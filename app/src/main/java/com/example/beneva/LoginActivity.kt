package com.example.beneva

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.util.concurrent.Executor
import java.util.concurrent.Executors


class LoginActivity : AppCompatActivity() {

    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var enterButton: Button
    private lateinit var googleButton: Button
    private lateinit var auth: FirebaseAuth
    private val executor: Executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_activity)

        auth = FirebaseAuth.getInstance()

        // Navigate to main if user already signed in
        auth.currentUser?.let {
            navigateToMain()
        }

        // UI references
        emailField = findViewById(R.id.email_field)
        passwordField = findViewById(R.id.password_field)
        enterButton = findViewById(R.id.enter_button)
        googleButton = findViewById(R.id.google_button)

        // Email/password sign-in
        enterButton.setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        navigateToMain()
                    } else {
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // Google Sign-In button
        googleButton.setOnClickListener {
            startGoogleSignIn()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun startGoogleSignIn() {
        val credentialManager = CredentialManager.create(this)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val callback = object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
            override fun onResult(result: GetCredentialResponse) {
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    firebaseAuthWithGoogle(googleCredential.idToken)
                } else {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Invalid Google credential", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onError(e: GetCredentialException) {
                runOnUiThread {
                    Log.e("GoogleSignIn", "Sign-in failed: ${e.message}")
                    Toast.makeText(this@LoginActivity, "Sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        credentialManager.getCredentialAsync(
            context = this,
            request = request,
            executor = ContextCompat.getMainExecutor(this),
            callback = callback
        )
    }



    private fun firebaseAuthWithGoogle(idToken: String) {
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(firebaseCredential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    navigateToMain()
                } else {
                    Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

fun CredentialManager.getCredentialAsync(
    context: Context,
    request: GetCredentialRequest,
    executor: Executor,
    callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>
) {
    this.getCredentialAsync(context, request, null, executor, callback)
}


