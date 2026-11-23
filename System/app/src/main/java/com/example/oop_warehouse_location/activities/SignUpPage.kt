package com.example.oop_warehouse_location.activities

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.example.oop_warehouse_location.R
import com.example.oop_warehouse_location.activities.auth.BaseAuthActivity
import com.example.oop_warehouse_location.models.user.User
import com.example.oop_warehouse_location.models.user.UserFactory
import com.example.oop_warehouse_location.models.Warehouse
import com.example.oop_warehouse_location.repository.FirebaseRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.UserProfileChangeRequest
import java.util.UUID

/**
 * INHERITANCE — extends [BaseAuthActivity] which provides:
 *   • FirebaseAuth initialisation (from BaseActivity)
 *   • Edge-to-edge + window-insets with auto-scroll (from BaseAuthActivity)
 *   • [validateEmail] / [validatePassword] helpers (from BaseAuthActivity)
 *   • [setLoading] toggle (from BaseActivity)
 */
class SignUpPage : BaseAuthActivity() {

    private val repository = FirebaseRepository()

    override fun getLayoutResId(): Int = R.layout.activity_sign_up_page

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // UI references
        val accountTypeLayout = findViewById<TextInputLayout>(R.id.textInputLayout6)
        val accountTypeInput = findViewById<MaterialAutoCompleteTextView>(R.id.statusInput)
        val firstNameLayout = findViewById<TextInputLayout>(R.id.textInputLayout)
        val firstNameInput = findViewById<TextInputEditText>(R.id.FirstNameInput)
        val lastNameLayout = findViewById<TextInputLayout>(R.id.textInputLayout2)
        val lastNameInput = findViewById<TextInputEditText>(R.id.LastnameInput)
        val emailLayout = findViewById<TextInputLayout>(R.id.textInputLayout3)
        val emailInput = findViewById<TextInputEditText>(R.id.EmailAddressInput)
        val passwordLayout = findViewById<TextInputLayout>(R.id.textInputLayout4)
        val passwordInput = findViewById<TextInputEditText>(R.id.PasswordInput)
        val confirmPasswordLayout = findViewById<TextInputLayout>(R.id.textInputLayout5)
        val confirmPasswordInput = findViewById<TextInputEditText>(R.id.confirmPasswordInput)
        val btnSignUp = findViewById<MaterialButton>(R.id.btnLogIn)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        // Navigate to Sign In
        findViewById<TextView>(R.id.tvSignIn).setOnClickListener {
            startActivity(Intent(this, SignInPage::class.java))
            finish()
        }

        // Sign Up button click
        btnSignUp.setOnClickListener {
            val accountType = accountTypeInput.text.toString().trim()
            val firstName = firstNameInput.text.toString().trim()
            val lastName = lastNameInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            // Clear previous errors
            accountTypeLayout.error = null
            firstNameLayout.error = null
            lastNameLayout.error = null
            emailLayout.error = null
            passwordLayout.error = null
            confirmPasswordLayout.error = null

            // Validate account type
            if (accountType.isEmpty()) {
                accountTypeLayout.error = "Select an account type"
                accountTypeInput.requestFocus()
                return@setOnClickListener
            }

            // Validate first name
            if (firstName.isEmpty()) {
                firstNameLayout.error = "First name is required"
                firstNameInput.requestFocus()
                return@setOnClickListener
            }

            // Validate last name
            if (lastName.isEmpty()) {
                lastNameLayout.error = "Last name is required"
                lastNameInput.requestFocus()
                return@setOnClickListener
            }

            // Use inherited validation helpers (POLYMORPHISM via BaseAuthActivity)
            val email = validateEmail(emailLayout, emailInput) ?: return@setOnClickListener
            val password = validatePassword(passwordLayout, passwordInput) ?: return@setOnClickListener

            // Validate confirm password
            if (confirmPassword.isEmpty()) {
                confirmPasswordLayout.error = "Confirm your password"
                confirmPasswordInput.requestFocus()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                confirmPasswordLayout.error = "Passwords do not match"
                confirmPasswordInput.requestFocus()
                return@setOnClickListener
            }

            // Show loading state — uses inherited helper
            setLoading(progressBar, btnSignUp, true)

            // Firebase create user
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val firebaseUser = auth.currentUser
                        val uid = firebaseUser?.uid ?: ""

                        // Set display name
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName("$firstName $lastName")
                            .build()

                        firebaseUser?.updateProfile(profileUpdates)

                        // Persist Firestore profile even if displayName update fails
                        setupUserProfile(
                            uid = uid,
                            firstName = firstName,
                            lastName = lastName,
                            email = email,
                            accountType = accountType,
                            progressBar = progressBar,
                            btnSignUp = btnSignUp
                        )
                    } else {
                        setLoading(progressBar, btnSignUp, false)
                        val errorMessage = task.exception?.message ?: "Registration failed"
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun setupUserProfile(
        uid: String,
        firstName: String,
        lastName: String,
        email: String,
        accountType: String,
        progressBar: ProgressBar,
        btnSignUp: MaterialButton
    ) {
        val user = UserFactory.create(
            accountType = accountType,
            uid = uid,
            firstName = firstName,
            lastName = lastName,
            email = email,
            warehouseId = "",  // will be populated for Manager or upon joining for Employee
            createdAt = System.currentTimeMillis()
        )

        if (user.canManageWarehouse()) {
            repository.generateUniqueInviteCode()
                .addOnSuccessListener { inviteCode ->
                    createManagerWarehouse(
                        user = user,
                        inviteCode = inviteCode,
                        email = email,
                        progressBar = progressBar,
                        btnSignUp = btnSignUp
                    )
                }
                .addOnFailureListener { e ->
                    setLoading(progressBar, btnSignUp, false)
                    Toast.makeText(this, "Failed to generate invite code: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        } else {
            // Employee — save user without warehouseId, then require email verification before joining
            repository.saveUser(user)
                .addOnSuccessListener {
                    handlePostRegistrationVerification(
                        email = email,
                        successNotice = "Employee account created!",
                        progressBar = progressBar,
                        btnSignUp = btnSignUp
                    )
                }
                .addOnFailureListener { e ->
                    setLoading(progressBar, btnSignUp, false)
                    Toast.makeText(this, "Failed to save profile: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun createManagerWarehouse(
        user: User,
        inviteCode: String,
        email: String,
        progressBar: ProgressBar,
        btnSignUp: MaterialButton
    ) {
        val warehouseId = UUID.randomUUID().toString()
        user.warehouseId = warehouseId

        val warehouse = Warehouse(
            warehouseId = warehouseId,
            name = "${user.firstName}'s Warehouse",
            totalCapacity = 500,
            ownerId = user.uid,
            inviteCode = inviteCode,
            members = mutableListOf(),
            createdAt = System.currentTimeMillis()
        )

        repository.createWarehouse(warehouse)
            .addOnSuccessListener {
                repository.saveUser(user)
                    .addOnSuccessListener {
                        handlePostRegistrationVerification(
                            email = email,
                            successNotice = "Manager account created! Your warehouse invite code is: $inviteCode",
                            progressBar = progressBar,
                            btnSignUp = btnSignUp
                        )
                    }
                    .addOnFailureListener { e ->
                        setLoading(progressBar, btnSignUp, false)
                        Toast.makeText(this, "Failed to save user: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                setLoading(progressBar, btnSignUp, false)
                Toast.makeText(this, "Failed to create warehouse: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Sends Firebase email verification to the registered address, signs the user out,
     * and prompts them with a dialog before redirecting to SignInPage.
     */
    private fun handlePostRegistrationVerification(
        email: String,
        successNotice: String = "",
        progressBar: ProgressBar,
        btnSignUp: MaterialButton
    ) {
        val firebaseUser = auth.currentUser
        firebaseUser?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                setLoading(progressBar, btnSignUp, false)
                auth.signOut()

                val message = buildString {
                    if (successNotice.isNotBlank()) {
                        append(successNotice)
                        append("\n\n")
                    }
                    if (task.isSuccessful) {
                        append("A verification link has been sent to $email.\n\nPlease check your inbox (and spam folder) and click the link to verify your email before logging in.")
                    } else {
                        append("Account created, but could not send verification email: ${task.exception?.localizedMessage}.\n\nYou can request a verification email on the Sign In page.")
                    }
                }

                AlertDialog.Builder(this)
                    .setTitle("Verify Your Email")
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("Go to Sign In") { _, _ ->
                        startActivity(Intent(this, SignInPage::class.java))
                        finish()
                    }
                    .show()
            }
    }
}
