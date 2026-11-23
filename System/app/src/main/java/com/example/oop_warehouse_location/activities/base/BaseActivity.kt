package com.example.oop_warehouse_location.activities.base

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.oop_warehouse_location.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth


abstract class BaseActivity : AppCompatActivity() {

    protected lateinit var auth: FirebaseAuth


    abstract fun getLayoutResId(): Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(getLayoutResId())
        auth = FirebaseAuth.getInstance()
        setupWindowInsets()
    }

    protected open fun setupWindowInsets() {
        val root = findViewById<View>(R.id.main) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    protected fun setLoading(progressBar: ProgressBar, button: MaterialButton, loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        button.isEnabled = !loading
    }
}
