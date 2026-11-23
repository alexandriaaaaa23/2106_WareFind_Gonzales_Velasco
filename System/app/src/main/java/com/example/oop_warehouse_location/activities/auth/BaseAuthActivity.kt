package com.example.oop_warehouse_location.activities.auth

import android.util.Patterns
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.oop_warehouse_location.R
import com.example.oop_warehouse_location.activities.base.BaseActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout


abstract class BaseAuthActivity : BaseActivity() {
    override fun setupWindowInsets() {
        val root = findViewById<ScrollView>(R.id.main) ?: run {
            super.setupWindowInsets()
            return
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))

            if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
                (v as ScrollView).post {
                    v.smoothScrollTo(0, v.getChildAt(0).bottom)
                }
            }
            insets
        }
    }

    protected fun validateEmail(emailLayout: TextInputLayout, emailInput: TextInputEditText): String? {
        val email = emailInput.text.toString().trim()
        return when {
            email.isEmpty() -> {
                emailLayout.error = "Email is required"
                emailInput.requestFocus()
                null
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                emailLayout.error = "Enter a valid email"
                emailInput.requestFocus()
                null
            }
            else -> {
                emailLayout.error = null
                email
            }
        }
    }

    protected fun validatePassword(
        passwordLayout: TextInputLayout,
        passwordInput: TextInputEditText,
        minLength: Int = 6
    ): String? {
        val password = passwordInput.text.toString().trim()
        return when {
            password.isEmpty() -> {
                passwordLayout.error = "Password is required"
                passwordInput.requestFocus()
                null
            }
            password.length < minLength -> {
                passwordLayout.error = "Password must be at least $minLength characters"
                passwordInput.requestFocus()
                null
            }
            else -> {
                passwordLayout.error = null
                password
            }
        }
    }
}
