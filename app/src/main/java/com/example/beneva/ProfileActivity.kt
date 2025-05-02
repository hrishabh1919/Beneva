package com.example.beneva

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ProfileActivity : AppCompatActivity() {

    private lateinit var ageInput: EditText
    private lateinit var locationInput: EditText
    private lateinit var raceInput: EditText
    private lateinit var allergenInput: EditText
    private lateinit var medicalConditionsInput: EditText
    private lateinit var editButton: Button
    private lateinit var allergenGroup: RadioGroup
    private lateinit var allergenPeanuts: RadioButton
    private lateinit var allergenDairy: RadioButton
    private lateinit var allergenGluten: RadioButton
    private lateinit var allergenNone: RadioButton


    private var isEditing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.profile_activity)

        ageInput = findViewById(R.id.ageInput)
        locationInput = findViewById(R.id.locationInput)
        raceInput = findViewById(R.id.raceInput)
        //allergenInput = findViewById(R.id.allergenInput)
        medicalConditionsInput = findViewById(R.id.medicalConditionsInput)
        editButton = findViewById(R.id.editButton)
        allergenGroup = findViewById(R.id.allergenGroup)
        allergenPeanuts = findViewById(R.id.allergenPeanuts)
        allergenDairy = findViewById(R.id.allergenDairy)
        allergenGluten = findViewById(R.id.allergenGluten)
        allergenNone = findViewById(R.id.allergenNone)


        editButton.setOnClickListener {
            toggleEditMode()
        }
    }

    private fun toggleEditMode() {
        isEditing = !isEditing
        val editable = isEditing

        ageInput.isEnabled = editable
        locationInput.isEnabled = editable
        raceInput.isEnabled = editable
        allergenInput.isEnabled = editable
        medicalConditionsInput.isEnabled = editable
        allergenPeanuts.isEnabled = editable
        allergenDairy.isEnabled = editable
        allergenGluten.isEnabled = editable
        allergenNone.isEnabled = editable

        if (editable) {
            editButton.text = "Save"
        } else {
            val selectedAllergenId = allergenGroup.checkedRadioButtonId
            val selectedAllergen = findViewById<RadioButton>(selectedAllergenId)?.text?.toString() ?: "Not specified"

            // Example of saving other values:
            val age = ageInput.text.toString()
            val location = locationInput.text.toString()
            val race = raceInput.text.toString()
            val conditions = medicalConditionsInput.text.toString()

            // Save logic here (e.g., to Firestore or local storage)

            Toast.makeText(this, "Profile saved. Allergen: $selectedAllergen", Toast.LENGTH_SHORT).show()
            editButton.text = "Edit"
        }

    }
}
