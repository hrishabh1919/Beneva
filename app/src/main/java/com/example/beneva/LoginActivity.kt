package com.example.beneva

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.SignInButton
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

private const val TAG = "LoginActivity"
private const val TYPE_GOOGLE_ID_TOKEN_CREDENTIAL = "com.google.android.libraries.identity.googleid.GOOGLE_ID_TOKEN_CREDENTIAL"

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_activity)

        // Initialize Firebase Auth
        auth = Firebase.auth

        // Initialize Credential Manager
        credentialManager = CredentialManager.create(this)

        // Google Sign-In Button
        findViewById<SignInButton>(R.id.googleSignInButton).setOnClickListener {
            signInWithGoogle()
        }

        // Email/Password Login Button
        findViewById<Button>(R.id.animatedButton).setOnClickListener {
            performEmailPasswordLogin()
        }
    }

    // Email/Password login logic
    private fun performEmailPasswordLogin() {
        val email = findViewById<EditText>(R.id.emailEditText).text.toString().trim()
        val password = findViewById<EditText>(R.id.passwordEditText).text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    checkUserExistsOrPrompt(user)
                } else {
                    Toast.makeText(
                        this,
                        "Login failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    // Google Sign-In logic
    private fun signInWithGoogle() {
        lifecycleScope.launch {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(true)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    context = this@LoginActivity,
                    request = request
                )
                handleSignIn(result.credential)
            } catch (e: Exception) {
                Log.e(TAG, "Sign-in failed", e)
                Toast.makeText(this@LoginActivity, "Google sign-in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSignIn(credential: Credential) {
        if (credential is CustomCredential &&
            credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
            } catch (e: GoogleIdTokenParsingException) {
                Log.e(TAG, "Error parsing Google ID token", e)
                Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w(TAG, "Unexpected credential type")
            Toast.makeText(this, "Unexpected credential type", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                checkUserExistsOrPrompt(user)
            } else {
                Toast.makeText(this, "Authentication Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkUserExistsOrPrompt(user: FirebaseUser?) {
        user ?: return
        val usersRef = FirebaseDatabase.getInstance().getReference("users")

        usersRef.child(user.uid).get().addOnSuccessListener { dataSnapshot ->
            if (dataSnapshot.exists()) {
                // User exists
                goToHome()
            } else {
                // New user - ask to create
                AlertDialog.Builder(this)
                    .setTitle("New Account")
                    .setMessage("This email is not registered. Would you like to create a new account?")
                    .setPositiveButton("Continue") { _, _ ->
                        val userData = mapOf("name" to user.displayName, "email" to user.email)
                        usersRef.child(user.uid).setValue(userData).addOnSuccessListener {
                            goToHome()
                        }
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        auth.signOut()
                    }
                    .show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Database error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun goToHome() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
