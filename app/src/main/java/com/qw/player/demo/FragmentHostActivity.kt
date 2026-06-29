package com.qw.player.demo

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class FragmentHostActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_FRAGMENT_CLASS_NAME = "extra_fragment_class_name"
        const val EXTRA_TITLE = "extra_title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_host)
        title = intent.getStringExtra(EXTRA_TITLE) ?: title
        if (savedInstanceState != null) {
            return
        }
        val fragmentClassName = intent.getStringExtra(EXTRA_FRAGMENT_CLASS_NAME) ?: return
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            fragmentClassName
        )
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
