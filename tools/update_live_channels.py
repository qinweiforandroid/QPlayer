#!/usr/bin/env python3
"""抓取直播源、自检可用性，然后写回 data/live/channels.json。

用法：
    python3 tools/update_live_channels.py              # 抓取 + 自检 + 写入
    python3 tools/update_live_channels.py --dry-run    # 只自检，打印结果，不写文件
    python3 tools/update_live_channels.py --check FILE # 只自检已有 JSON，用于 CI
    python3 tools/update_live_channels.py --no-fetch   # 只重新自检现有 JSON 里的线路

自检口径（一条线路算「可用」必须全部满足）：
    1. 主清单 HTTP 2xx，且 Content-Type/正文确实是 m3u8；
    2. 若主清单是 master playlist，至少有一个 variant 可取；
    3. 取到一条 media playlist 后，能列出分片且第一个分片 HTTP 2xx 且非空。
只校验到「出流」，不解码；因此能筛掉 404 / 超时 / 空壳清单，筛不掉编码异常。

写回时每个频道最多保留 --max-lines 条（默认 2）通过自检的线路；
一条都不过的频道会被剔除，并在报告里列出。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import socket
import ssl
import sys
import tempfile
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Iterable

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_DATA_FILE = os.path.join(PROJECT_ROOT, "data", "live", "channels.json")
ASSET_TARGETS = (
    os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "live", "channels.json"),
    os.path.join(PROJECT_ROOT, "app-tv", "src", "main", "assets", "live", "channels.json"),
)

CN_TZ = timezone(timedelta(hours=8))

# guovin/iptv-api 每日构建的输出。相比 iptv-org，它对中国大陆台的覆盖厚得多：
# 每个台通常有 5 条线路，且自带 tvg-name / tvg-logo / group-title。
# 站点上的 viewer.html 只是渲染这个 m3u，直接抓原始文件即可。
GUOVIN_M3U = "https://raw.githubusercontent.com/guovin/iptv-api/gd/output/result.m3u"
USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
)

# 频道映射：key 是在源里出现的 tvg-name / 标题（归一化后），
# value 是 (本地频道 id, 显示名, 分类)。
#
# 归一化规则见 normalize_name：去空格、去末尾 HD/4K/超清/频道后缀、全角转半角。
# 已收录的台按 CCTV → 卫视 → 地方排列，顺序即 JSON 里的展示顺序。
CHANNEL_MAP: dict[str, tuple[str, str, str]] = {
    # ---- 央视
    "CCTV-1": ("cctv1", "CCTV-1 综合", "CCTV"),
    "CCTV-2": ("cctv2", "CCTV-2 财经", "CCTV"),
    "CCTV-3": ("cctv3", "CCTV-3 综艺", "CCTV"),
    "CCTV-4": ("cctv4", "CCTV-4 中文国际", "CCTV"),
    "CCTV-5": ("cctv5", "CCTV-5 体育", "CCTV"),
    "CCTV-5+": ("cctv5p", "CCTV-5+ 体育赛事", "CCTV"),
    "CCTV-6": ("cctv6", "CCTV-6 电影", "CCTV"),
    "CCTV-7": ("cctv7", "CCTV-7 国防军事", "CCTV"),
    "CCTV-8": ("cctv8", "CCTV-8 电视剧", "CCTV"),
    "CCTV-9": ("cctv9", "CCTV-9 纪录", "CCTV"),
    "CCTV-10": ("cctv10", "CCTV-10 科教", "CCTV"),
    "CCTV-11": ("cctv11", "CCTV-11 戏曲", "CCTV"),
    "CCTV-12": ("cctv12", "CCTV-12 社会与法", "CCTV"),
    "CCTV-13": ("cctv13", "CCTV-13 新闻", "CCTV"),
    "CCTV-14": ("cctv14", "CCTV-14 少儿", "CCTV"),
    "CCTV-15": ("cctv15", "CCTV-15 音乐", "CCTV"),
    "CCTV-16": ("cctv16", "CCTV-16 奥林匹克", "CCTV"),
    "CCTV-17": ("cctv17", "CCTV-17 农业农村", "CCTV"),
    # ---- 卫视
    "北京卫视": ("bj", "北京卫视", "SATELLITE"),
    "东方卫视": ("dfl", "东方卫视", "SATELLITE"),
    "浙江卫视": ("zj", "浙江卫视", "SATELLITE"),
    "江苏卫视": ("js", "江苏卫视", "SATELLITE"),
    "湖南卫视": ("hn", "湖南卫视", "SATELLITE"),
    "安徽卫视": ("ah", "安徽卫视", "SATELLITE"),
    "深圳卫视": ("sz", "深圳卫视", "SATELLITE"),
    "广东卫视": ("gd", "广东卫视", "SATELLITE"),
    "东南卫视": ("dn", "东南卫视", "SATELLITE"),
    "山东卫视": ("sd", "山东卫视", "SATELLITE"),
    "黑龙江卫视": ("hlj", "黑龙江卫视", "SATELLITE"),
    "内蒙古卫视": ("nmg", "内蒙古卫视", "SATELLITE"),
    "山西卫视": ("sx", "山西卫视", "SATELLITE"),
    "江西卫视": ("jx", "江西卫视", "SATELLITE"),
    "西藏卫视": ("xz", "西藏卫视", "SATELLITE"),
    "辽宁卫视": ("ln", "辽宁卫视", "SATELLITE"),
    "甘肃卫视": ("gs", "甘肃卫视", "SATELLITE"),
    "新疆卫视": ("xj", "新疆卫视", "SATELLITE"),
    "河北卫视": ("heb", "河北卫视", "SATELLITE"),
    "河南卫视": ("hen", "河南卫视", "SATELLITE"),
    "湖北卫视": ("hub", "湖北卫视", "SATELLITE"),
    "四川卫视": ("sc", "四川卫视", "SATELLITE"),
    "贵州卫视": ("gz", "贵州卫视", "SATELLITE"),
    "云南卫视": ("yn", "云南卫视", "SATELLITE"),
    "广西卫视": ("gx", "广西卫视", "SATELLITE"),
    "陕西卫视": ("shx", "陕西卫视", "SATELLITE"),
    "吉林卫视": ("jl", "吉林卫视", "SATELLITE"),
    "重庆卫视": ("cq", "重庆卫视", "SATELLITE"),
    "天津卫视": ("tj", "天津卫视", "SATELLITE"),
    "海南卫视": ("ha", "海南卫视", "SATELLITE"),
    "宁夏卫视": ("nx", "宁夏卫视", "SATELLITE"),
    "青海卫视": ("qh", "青海卫视", "SATELLITE"),
    "三沙卫视": ("ss", "三沙卫视", "SATELLITE"),
    "延边卫视": ("yb", "延边卫视", "SATELLITE"),
    "安多卫视": ("ad", "安多卫视", "SATELLITE"),
    "香港卫视": ("hk", "香港卫视", "SATELLITE"),
    # ---- 浙江地方台
    "浙江新闻": ("zj_news", "浙江新闻", "LOCAL"),
    "浙江钱江": ("zj_qianjiang", "浙江钱江", "LOCAL"),
    "浙江经视": ("zj_jingshi", "浙江经视", "LOCAL"),
    "浙江教育": ("zj_edu", "浙江教育", "LOCAL"),
    "浙江少儿": ("zj_kids", "浙江少儿", "LOCAL"),
    "浙江公共新闻": ("zj_public", "浙江公共新闻", "LOCAL"),
    "之江纪录": ("zj_record", "之江纪录", "LOCAL"),
    "中国蓝新闻": ("zj_zgl", "中国蓝新闻", "LOCAL"),
    # ---- 其他省市
    "江苏城市": ("js_city", "江苏城市", "LOCAL"),
}

# 源里的分组名 → 是否属于要收录的组。只用它做一道预筛，
# 真正的归属仍以 CHANNEL_MAP 为准，避免把付费包 / 剧场并入正牌频道。
WANTED_GROUP_KEYWORDS = ("央视频道", "卫视频道", "浙江频道", "江苏频道")


# ---------------------------------------------------------------- 抓取


@dataclass
class Candidate:
    channel_id: str
    name: str
    category: str
    url: str
    source: str
    logo_url: str = ""


def fetch_text(url: str, timeout: float, attempts: int = 3) -> str:
    """下载文本并重试。

    guovin 的 result.m3u 有 ~400KB 且托管在 raw.githubusercontent，
    国内经常单次超时，所以抓取要比探测线路更宽容。
    """
    last: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return resp.read().decode("utf-8", errors="replace")
        except Exception as exc:  # noqa: BLE001
            last = exc
            if attempt < attempts:
                print(f"  拉取失败（第 {attempt} 次）: {exc}，重试…", file=sys.stderr)
                time.sleep(2 * attempt)
    raise last  # type: ignore[misc]


def parse_m3u(text: str) -> list[tuple[dict[str, str], str]]:
    """解析 M3U，返回 [(属性 dict, url)]。属性里的 tvg-id / tvg-logo 会被提取成键。"""
    entries: list[tuple[dict[str, str], str]] = []
    pending: dict[str, str] | None = None
    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("#EXTINF:"):
            attrs = dict(re.findall(r'([\w-]+)="([^"]*)"', line))
            title = line.rsplit(",", 1)[-1].strip() if "," in line else ""
            attrs["title"] = title
            pending = attrs
        elif line.startswith("#"):
            continue
        elif pending is not None:
            entries.append((pending, line))
            pending = None
    return entries


def normalize_name(raw: str) -> str:
    """把源里的各种写法收敛成一个可比对的键。

    guovin 的 tvg-name 相对规整（`CCTV-1`、`浙江卫视`），但标题里会带
    清晰度、画质修饰和全角符号，例如 `CCTV-1 高清`、`ＣＣＴＶ－1`、`浙江卫视 HD`。
    """
    text = unicodedata.normalize("NFKC", raw or "").strip()
    text = re.sub(r"\s+", "", text)
    text = re.sub(r"(高清|超清|蓝光|标清|HD|SD|FHD|UHD|4K|8K|1080P|720P|576P)$", "",
                  text, flags=re.I)
    text = re.sub(r"频道$", "", text)
    return text.upper() if re.fullmatch(r"[\w\W]{0,8}", text) and "CCTV" in text.upper() else text


def map_entry(attrs: dict[str, str]) -> tuple[str, str, str] | None:
    """把一条 M3U 条目映射到本地频道；映射不上就返回 None。

    优先用 tvg-name（guovin 的 `CCTV-1`、`浙江卫视` 都在这里），
    再用标题兜底，因为少数条目的 tvg-name 是空的。
    """
    for field in ("tvg-name", "title"):
        key = normalize_name(attrs.get(field, ""))
        if key in CHANNEL_MAP:
            return CHANNEL_MAP[key]
        # 映射表里存的是规范化后的形式，再做一次不区分大小写的比对兜底
        for mapped_key, value in CHANNEL_MAP.items():
            if normalize_name(mapped_key) == key:
                return value
    return None


def is_wanted_group(group_title: str) -> bool:
    return any(keyword in group_title for keyword in WANTED_GROUP_KEYWORDS)


def fetch_candidates(timeout: float) -> list[Candidate]:
    out: list[Candidate] = []
    try:
        # 抓取整份清单，超时按探测超时放大，且自带重试
        text = fetch_text(GUOVIN_M3U, max(timeout * 4, 30.0))
    except Exception as exc:  # noqa: BLE001 - 抓取失败不应中断自检流程
        print(f"[warn] 拉取 {GUOVIN_M3U} 失败: {exc}", file=sys.stderr)
        return out

    skipped_group = 0
    for attrs, url in parse_m3u(text):
        group = attrs.get("group-title", "")
        if not is_wanted_group(group):
            skipped_group += 1
            continue
        mapped = map_entry(attrs)
        if mapped is None:
            continue
        channel_id, name, category = mapped
        out.append(Candidate(
            channel_id=channel_id,
            name=name,
            category=category,
            url=url,
            source=GUOVIN_M3U,
            logo_url=attrs.get("tvg-logo", ""),
        ))
    if skipped_group:
        print(f"  按分组过滤掉 {skipped_group} 条（非目标分组）")
    return out


# ---------------------------------------------------------------- 自检


@dataclass
class ProbeResult:
    url: str
    ok: bool
    reason: str = ""
    latency_ms: int = 0
    detail: str = ""


def _open(url: str, timeout: float) -> tuple[bytes, str]:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    ctx = ssl.create_default_context()
    # 部分县域源的证书链确实有问题；这里只为自检放通，不影响客户端。
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
        return resp.read(256 * 1024), resp.headers.get("Content-Type", "")


def _absolute(base: str, ref: str) -> str:
    return urllib.parse.urljoin(base, ref)


def probe_stream(url: str, timeout: float = 8.0) -> ProbeResult:
    """三段落式自检：主清单 → variant → 首片。"""
    if not re.match(r"^https?://", url):
        return ProbeResult(url, False, "bad-scheme")

    started = time.monotonic()

    def elapsed() -> int:
        return int((time.monotonic() - started) * 1000)

    try:
        body, ctype = _open(url, timeout)
    except urllib.error.HTTPError as exc:
        return ProbeResult(url, False, f"http-{exc.code}", elapsed())
    except (socket.timeout, TimeoutError):
        return ProbeResult(url, False, "timeout", elapsed())
    except urllib.error.URLError as exc:
        return ProbeResult(url, False, f"unreachable: {exc.reason}", elapsed())
    except Exception as exc:  # noqa: BLE001
        return ProbeResult(url, False, f"error: {exc}", elapsed())

    text = body.decode("utf-8", errors="replace").lstrip("﻿")
    if "#EXTM3U" not in text[:4096] and "mpegurl" not in ctype.lower():
        return ProbeResult(url, False, "not-hls", elapsed())

    playlist_url = url
    lines = [ln.strip() for ln in text.splitlines() if ln.strip()]

    # master playlist：挑第一条 variant 继续校验
    variants = [ln for ln in lines if not ln.startswith("#")]
    if "#EXT-X-STREAM-INF" in text:
        if not variants:
            return ProbeResult(url, False, "empty-master", elapsed())
        playlist_url = _absolute(url, variants[0])
        try:
            variant_body, _ = _open(playlist_url, timeout)
        except Exception as exc:  # noqa: BLE001
            return ProbeResult(url, False, "variant-failed", elapsed(), str(exc))
        lines = [ln.strip() for ln in variant_body.decode("utf-8", errors="replace").splitlines()]
        lines = [ln for ln in lines if ln]

    segments = [ln for ln in lines if ln and not ln.startswith("#")]
    if not segments:
        # 空清单：有的源在开播前会这么返回，视作不可用
        return ProbeResult(url, False, "empty-playlist", elapsed())

    first = _absolute(playlist_url, segments[0])
    try:
        seg_body, _ = _open(first, timeout)
    except Exception as exc:  # noqa: BLE001
        return ProbeResult(url, False, "segment-failed", elapsed(), str(exc))
    if not seg_body:
        return ProbeResult(url, False, "empty-segment", elapsed())

    return ProbeResult(url, True, latency_ms=elapsed(), detail=f"{len(segments)} segs")


def probe_all(urls: Iterable[str], timeout: float, workers: int) -> dict[str, ProbeResult]:
    """先全量探测，再对失败的线路用放宽的超时重试一轮。

    单轮探测受并发与网络抖动影响很大，只失败一次就判死会误删可用线路；
    重试仍失败的才认为是真不可用。
    """
    unique = list(dict.fromkeys(urls))
    with ThreadPoolExecutor(max_workers=max(1, workers)) as pool:
        results = dict(pool.map(lambda u: (u, probe_stream(u, timeout)), unique))

    failed = [u for u, r in results.items() if not r.ok]
    if failed:
        print(f"  {len(failed)} 条首轮失败，用 {timeout * 2:.0f}s 超时重试…")
        with ThreadPoolExecutor(max_workers=max(1, workers // 2)) as pool:
            for url, result in pool.map(lambda u: (u, probe_stream(u, timeout * 2)), failed):
                if result.ok:
                    results[url] = result
    return {u: results[u] for u in unique}


# ---------------------------------------------------------------- JSON 读写


def load_manifest(path: str) -> dict:
    with open(path, encoding="utf-8") as fp:
        return json.load(fp)


def all_urls(manifest: dict) -> list[str]:
    return [line["url"] for ch in manifest.get("channels", []) for line in ch.get("lines", [])]


def validate_manifest(manifest: dict) -> list[str]:
    """结构自检：不联网，只查字段与约束。返回问题列表。"""
    problems: list[str] = []
    channels = manifest.get("channels")
    if not isinstance(channels, list) or not channels:
        return ["channels 为空或不是数组"]

    seen_ids: set[str] = set()
    seen_urls: set[str] = set()
    valid_categories = {"CCTV", "SATELLITE", "LOCAL"}

    for idx, ch in enumerate(channels):
        where = f"channels[{idx}]"
        cid = ch.get("id")
        if not isinstance(cid, str) or not cid.strip():
            problems.append(f"{where}.id 缺失")
            continue
        if cid in seen_ids:
            problems.append(f"{where}.id 重复: {cid}")
        seen_ids.add(cid)
        if not isinstance(ch.get("name"), str) or not ch["name"].strip():
            problems.append(f"{cid}.name 缺失")
        if ch.get("category") not in valid_categories:
            problems.append(f"{cid}.category 非法: {ch.get('category')!r}")
        lines = ch.get("lines")
        if not isinstance(lines, list) or not lines:
            problems.append(f"{cid} 没有线路")
            continue
        for li, line in enumerate(lines):
            url = line.get("url", "")
            if not re.match(r"^https?://", url):
                problems.append(f"{cid}.lines[{li}].url 不是 http(s): {url!r}")
            if url in seen_urls:
                problems.append(f"{cid}.lines[{li}].url 与其他频道重复: {url}")
            seen_urls.add(url)
            if not line.get("label"):
                problems.append(f"{cid}.lines[{li}].label 缺失")
    return problems


def write_json_atomic(path: str, manifest: dict) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=os.path.dirname(path), suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as fp:
            json.dump(manifest, fp, ensure_ascii=False, indent=2)
            fp.write("\n")
        os.replace(tmp, path)
    except BaseException:
        os.unlink(tmp)
        raise


# ---------------------------------------------------------------- 主流程


def build_manifest(
    existing: dict,
    candidates: list[Candidate],
    probes: dict[str, ProbeResult],
    max_lines: int,
) -> tuple[dict, list[str]]:
    channels: dict[str, dict] = {}
    order: list[str] = []
    warnings: list[str] = []

    def ensure(ch: dict) -> dict:
        cid = ch["id"]
        if cid not in channels:
            channels[cid] = {
                "id": cid,
                "name": ch.get("name", cid),
                "category": ch.get("category", "SATELLITE"),
                "logoUrl": ch.get("logoUrl", ""),
                "lines": [],
            }
            order.append(cid)
        return channels[cid]

    # 每个频道收集「所有候选线路」（旧 JSON 里的 + 新抓的），
    # 按实测延迟升序排序后只留最快的 max_lines 条，而不是「旧的优先」。
    # 这样源失效时旧线路会被自然淘汰，源变快时也能及时替换。
    pool: dict[str, list[tuple[str, str]]] = {}

    def add_to_pool(cid: str, url: str) -> None:
        pool.setdefault(cid, [])
        if not any(u == url for _, u in pool[cid]):
            pool[cid].append((cid, url))

    for ch in existing.get("channels", []):
        target = ensure(ch)
        target["name"] = ch.get("name", target["name"])
        target["category"] = ch.get("category", target["category"])
        target["logoUrl"] = ch.get("logoUrl", "")
        for line in ch.get("lines", []):
            add_to_pool(ch["id"], line["url"])

    for cand in candidates:
        target = ensure({
            "id": cand.channel_id,
            "name": cand.name,
            "category": cand.category,
            "logoUrl": cand.logo_url,
        })
        if cand.logo_url and not target["logoUrl"]:
            target["logoUrl"] = cand.logo_url
        add_to_pool(cand.channel_id, cand.url)

    for cid, candidate_urls in pool.items():
        ok_urls = [url for _, url in candidate_urls if probes.get(url) and probes[url].ok]
        ok_urls.sort(key=lambda u: probes[u].latency_ms)
        for _, url in candidate_urls:
            if url not in ok_urls:
                result = probes.get(url)
                warnings.append(f"丢弃 {cid} 线路 {url}: "
                                f"{result.reason if result else '未探测'}")
        channels[cid]["lines"] = [
            {"label": f"线路{i + 1}", "url": url}
            for i, url in enumerate(ok_urls[:max_lines])
        ]

    final_channels = []
    for cid in order:
        ch = channels[cid]
        if not ch["lines"]:
            warnings.append(f"剔除频道 {cid}（{ch['name']}）: 没有可用线路")
            continue
        final_channels.append(ch)

    manifest = {
        "schemaVersion": existing.get("schemaVersion", 1),
        "updatedAt": datetime.now(CN_TZ).isoformat(timespec="seconds"),
        "note": (f"直播频道源。由 tools/update_live_channels.py 抓取并自检后生成；"
                 f"每个频道最多保留 {max_lines} 条已验证可出流的线路。"),
        "channels": final_channels,
    }
    return manifest, warnings


def report(manifest: dict, probes: dict[str, ProbeResult]) -> None:
    total = sum(len(c["lines"]) for c in manifest["channels"])
    ok = sum(1 for r in probes.values() if r.ok)
    print(f"\n自检: {ok}/{len(probes)} 条线路可出流 "
          f"({ok * 100 // max(1, len(probes))}%)")
    print(f"结果: {len(manifest['channels'])} 个频道 / {total} 条线路")
    for c in manifest["channels"]:
        lat = [probes[l["url"]].latency_ms for l in c["lines"] if l["url"] in probes]
        lat_text = f"{min(lat)}-{max(lat)}ms" if lat else "-"
        print(f"  {c['id']:<14} {c['name']:<18} {len(c['lines'])} 条  {lat_text}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="更新直播频道 JSON 并自检")
    parser.add_argument("--data", default=DEFAULT_DATA_FILE, help="目标 JSON 路径")
    parser.add_argument("--check", metavar="FILE", help="只自检指定 JSON，不写回")
    parser.add_argument("--no-fetch", action="store_true", help="只重新自检现有 JSON，不抓新源")
    parser.add_argument("--dry-run", action="store_true", help="自检但不写文件")
    parser.add_argument("--max-lines", type=int, default=2, help="每个频道保留的线路数")
    parser.add_argument("--timeout", type=float, default=8.0, help="单条线路探测超时（秒）")
    parser.add_argument("--workers", type=int, default=16, help="并发探测数")
    args = parser.parse_args(argv)

    target = args.check or args.data
    existing = load_manifest(target)

    problems = validate_manifest(existing)
    if problems and args.check:
        for p in problems:
            print(f"[invalid] {p}")
        return 1
    for p in problems:
        print(f"[warn] 结构问题: {p}", file=sys.stderr)

    candidates: list[Candidate] = []
    if not args.no_fetch and not args.check:
        print("抓取候选线路…")
        candidates = fetch_candidates(args.timeout)
        print(f"抓到 {len(candidates)} 条候选 / {len({c.channel_id for c in candidates})} 个频道")

    urls = all_urls(existing) + [c.url for c in candidates]
    print(f"自检 {len(set(urls))} 条线路…")
    probes = probe_all(urls, args.timeout, args.workers)

    manifest, warnings = build_manifest(existing, candidates, probes, args.max_lines)
    for w in warnings:
        print(f"[warn] {w}", file=sys.stderr)

    report(manifest, probes)

    post_problems = validate_manifest(manifest)
    if post_problems:
        print("\n生成结果未通过结构自检，放弃写入：", file=sys.stderr)
        for p in post_problems:
            print(f"[invalid] {p}", file=sys.stderr)
        return 1

    alive = sum(len(c["lines"]) for c in manifest["channels"])
    if alive == 0:
        print("\n没有任何可用线路，保留原文件不动。", file=sys.stderr)
        return 1

    if args.check:
        print("\n结构自检通过。")
        return 0
    if args.dry_run:
        print("\n--dry-run：未写入文件。")
        return 0

    write_json_atomic(args.data, manifest)
    print(f"\n已写入 {os.path.relpath(args.data, PROJECT_ROOT)}")
    for dest in ASSET_TARGETS:
        write_json_atomic(dest, manifest)
        print(f"已同步 {os.path.relpath(dest, PROJECT_ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
