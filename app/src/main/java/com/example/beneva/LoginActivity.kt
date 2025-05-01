package com.example.beneva

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var enterButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_activity)

        // Initialize views
        emailField = findViewById(R.id.email_field)
        passwordField = findViewById(R.id.password_field)
        enterButton = findViewById(R.id.enter_button)

        // Set click listener for enter button
        enterButton.setOnClickListener {
            val email = emailField.text.toString()
            val password = passwordField.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // For demo purposes, we'll just navigate to MainActivity
            // In a real app, you would validate credentials here
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}

