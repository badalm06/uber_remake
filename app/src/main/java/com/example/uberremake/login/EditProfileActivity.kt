package com.example.uberremake.login

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.uberremake.Common
import com.example.uberremake.DriverHomeActivity
import com.example.uberremake.R
import com.example.uberremake.databinding.ActivityEditProfileBinding
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.concurrent.TimeUnit

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var storageReference: StorageReference
    private var user: FirebaseUser? = null

    private val PICK_IMAGE_REQUEST = 1
    private lateinit var profileImageView: ImageView

    private var isEditing = false
    private var selectedImageUri: Uri? = null

    // Added for car fields
    private val carList = listOf(
        "Select Car Model", // Hint for Spinner
        "Maruti Suzuki Dzire", "Maruti Suzuki Wagon R", "Maruti Suzuki Celerio", "Maruti Suzuki Ertiga",
        "Maruti Suzuki Alto K10", "Maruti Suzuki Eeco", "Hyundai Aura", "Hyundai Grand i10 Nios", "Tata Tiago",
        "Tata Tigor", "Honda Amaze", "Toyota Innova Crysta", "Mahindra Marazzo", "Mahindra Scorpio",
        "Kia Carens"
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        user = firebaseAuth.currentUser

        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        database = FirebaseDatabase.getInstance().getReference("users")
        storageReference = FirebaseStorage.getInstance().getReference("profile_images/${user!!.uid}")
        profileImageView = binding.profileImage

        // Spinner setup: Place this in onCreate, after binding initialization
        val spinnerAdapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            carList
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as TextView).setTextColor(Color.BLACK) // Set selected item color to black
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as TextView).setTextColor(Color.BLACK) // Set dropdown items color to black
                return view
            }
        }
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.etSelectCar.adapter = spinnerAdapter


        loadUserProfile()

        binding.btnBack.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val car = binding.etSelectCar.selectedItem.toString()
            val carNumber = binding.etCarNumber.text.toString().trim()

            var isValid = true

            if (name.isEmpty()) {
                binding.etName.error = "Name is required"
                isValid = false
            }
            if (phone.isEmpty()) {
                binding.etPhone.error = "Phone number is required"
                isValid = false
            }
            if (email.isEmpty()) {
                binding.etEmail.error = "Email is required"
                isValid = false
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etEmail.error = "Enter a valid email"
                isValid = false
            }
            if (binding.etSelectCar.selectedItemPosition == 0) { // 0 is the hint position
                Toast.makeText(this, "Car selection is required", Toast.LENGTH_SHORT).show()
                isValid = false
            }
            if (carNumber.isEmpty()) {
                binding.etCarNumber.error = "Car number is required"
                isValid = false
            } else if (!isValidIndianCarNumber(carNumber)) {
                binding.etCarNumber.error = "Enter a valid car number (e.g. MH12AB1234)"
                isValid = false
            }


            if (isValid) {
                // Save profile before navigating
                saveUserProfileAndNavigateToHome()
            }
            // If not valid, errors will be shown and navigation will not happen
        }


        binding.driverActivityButton.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val car = binding.etSelectCar.selectedItem.toString()
            val carNumber = binding.etCarNumber.text.toString().trim()


            var isValid = true

            if (name.isEmpty()) {
                binding.etName.error = "Name is required"
                isValid = false
            }
            if (phone.isEmpty()) {
                binding.etPhone.error = "Phone number is required"
                isValid = false
            }
            if (email.isEmpty()) {
                binding.etEmail.error = "Email is required"
                isValid = false
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etEmail.error = "Enter a valid email"
                isValid = false
            }
            if (binding.etSelectCar.selectedItemPosition == 0) { // 0 is the hint position
                Toast.makeText(this, "Car selection is required", Toast.LENGTH_SHORT).show()
                isValid = false
            }
            if (carNumber.isEmpty()) {
                binding.etCarNumber.error = "Car number is required"
                isValid = false
            } else if (!isValidIndianCarNumber(carNumber)) {
                binding.etCarNumber.error = "Enter a valid car number (e.g. MH12AB1234)"
                isValid = false
            }


            if (isValid) {
                // All fields are filled and valid, proceed
                saveUserProfileAndNavigateToHome()
            }
            // If not valid, errors will be shown and navigation will not happen
        }


        setEditable(false)

        binding.editBtn.setOnClickListener {
            toggleEditMode()
        }

        profileImageView.setOnClickListener {
            if (isEditing) {
                selectProfileImage()
            }
        }

        binding.logoutBtn.setOnClickListener {
            firebaseAuth.signOut()
            startActivity(Intent(this, logInActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            Toast.makeText(this, "Logout Successfully", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveUserProfileAndNavigateToHome() {
        saveUserProfile {
            // This code runs only after the profile is successfully saved
            startActivity(Intent(this, DriverHomeActivity::class.java))
            finish()
        }
    }

    fun isValidIndianCarNumber(carNumber: String): Boolean {
        val regex = Regex("^[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{4}$")
        return regex.matches(carNumber.replace("\\s".toRegex(), "").uppercase())
    }



    private fun loadUserProfile() {
        user?.let { currentUser ->
            database.child(currentUser.uid).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.child("name").getValue(String::class.java) ?: ""
                    val phone = snapshot.child("phone").getValue(String::class.java) ?: ""
                    val email = snapshot.child("email").getValue(String::class.java) ?: ""
                    val car = snapshot.child("car").getValue(String::class.java) ?: ""
                    val carNumber = snapshot.child("carNumber").getValue(String::class.java) ?: ""

                    binding.etName.setText(name)
                    binding.etPhone.setText(phone)
                    binding.etEmail.setText(email)
                    val carPosition = carList.indexOf(car)
                    if (carPosition >= 0) {
                        binding.etSelectCar.setSelection(carPosition)
                    }
                    binding.etCarNumber.setText(carNumber)

                    // Only allow editing email if user email is empty
                    binding.etEmail.isEnabled = email.isEmpty()

                    val imageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this@EditProfileActivity).load(imageUrl).into(profileImageView)
                    } else {
                        profileImageView.setImageResource(R.drawable.person_24)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@EditProfileActivity, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun toggleEditMode() {
        isEditing = !isEditing
        setEditable(isEditing)
        binding.editBtn.text = if (isEditing) "Save" else "Edit"
        if (!isEditing) {
            saveUserProfile {
                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun setEditable(editable: Boolean) {
        binding.etName.isEnabled = editable
        binding.etPhone.isEnabled = editable
        binding.etEmail.isEnabled = editable && (user?.email.isNullOrEmpty())
        binding.etSelectCar.isEnabled = editable
        binding.etCarNumber.isEnabled = editable
    }

    private fun saveUserProfile(onSuccess: () -> Unit) {
        val name = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val car = binding.etSelectCar.selectedItem.toString()
        val carNumber = binding.etCarNumber.text.toString().trim()


        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Name and Phone cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (car.isEmpty()) { // Added for car fields
            Toast.makeText(this, "Car selection cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (carNumber.isEmpty()) { // Added for car fields
            Toast.makeText(this, "Car number cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        user?.let { currentUser ->
            database.child(currentUser.uid).child("phone").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val currentPhone = snapshot.getValue(String::class.java)
                    if (currentPhone != phone) {
                        // Phone changed, verify via OTP
                        sendOtpForVerification(phone, name, email, car, carNumber, onSuccess)
                    } else {
                        if (selectedImageUri != null) {
                            uploadImageAndSaveProfile(name, phone, email, car, carNumber, onSuccess)
                        } else {
                            updateProfile(name, phone, email, null, car, carNumber, onSuccess)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@EditProfileActivity, "Failed to check phone", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun uploadImageAndSaveProfile(name: String, phone: String, email: String, car: String, carNumber: String, onSuccess: () -> Unit) {
        if (selectedImageUri == null) {
            updateProfile(name, phone, email, null, car, carNumber, onSuccess)
            return
        }

        storageReference.putFile(selectedImageUri!!)
            .addOnSuccessListener {
                storageReference.downloadUrl.addOnSuccessListener { uri ->
                    val imageUrl = uri.toString()
                    Glide.with(this).load(uri).into(profileImageView)
                    updateProfile(name, phone, email, imageUrl, car, carNumber, onSuccess)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Image upload failed", Toast.LENGTH_SHORT).show()
                updateProfile(name, phone, email, null, car, carNumber, onSuccess)
            }
    }


    private fun sendOtpForVerification(phoneNumber: String, name: String, email: String, car: String, carNumber: String, onSuccess: () -> Unit) {
        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber("+91$phoneNumber")
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    verifyOtpAndSave(credential, name, phoneNumber, email, car, carNumber, onSuccess)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Toast.makeText(this@EditProfileActivity, "OTP failed: ${e.message}", Toast.LENGTH_LONG).show()
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    showOtpDialog(verificationId, name, phoneNumber, email, car, carNumber, onSuccess)
                }
            }).build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }


    private fun showOtpDialog(
        verificationId: String,
        name: String,
        phone: String,
        email: String,
        car: String,
        carNumber: String,
        onSuccess: () -> Unit
    ) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER

        AlertDialog.Builder(this)
            .setTitle("Enter OTP")
            .setView(input)
            .setPositiveButton("Verify") { _, _ ->
                val otp = input.text.toString()
                val credential = PhoneAuthProvider.getCredential(verificationId, otp)
                verifyOtpAndSave(credential, name, phone, email, car, carNumber, onSuccess)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun verifyOtpAndSave(
        credential: PhoneAuthCredential,
        name: String,
        phone: String,
        email: String,
        car: String,
        carNumber: String,
        onSuccess: () -> Unit
    ) {
        val currentUser = firebaseAuth.currentUser

        if (currentUser == null) {
            // Not logged in: phone login
            firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        user = firebaseAuth.currentUser
                        if (selectedImageUri != null) {
                            uploadImageAndSaveProfile(name, phone, email, car, carNumber, onSuccess)
                        } else {
                            updateProfile(name, phone, email, null, car, carNumber, onSuccess)
                        }
                    } else {
                        Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            // Already logged in: check if phone is already linked
            if (currentUser.phoneNumber == "+91$phone") {
                // Already linked, just update profile
                if (selectedImageUri != null) {
                    uploadImageAndSaveProfile(name, phone, email, car, carNumber, onSuccess)
                } else {
                    updateProfile(name, phone, email, null, car, carNumber, onSuccess)
                }
            } else if (currentUser.providerData.any { it.providerId == "phone" }) {
                // User already has a phone linked, so update it
                currentUser.updatePhoneNumber(credential)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            if (selectedImageUri != null) {
                                uploadImageAndSaveProfile(name, phone, email, car, carNumber, onSuccess)
                            } else {
                                updateProfile(name, phone, email, null, car, carNumber, onSuccess)
                            }
                        } else {
                            Toast.makeText(this, "Could not update phone: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                // No phone linked yet, link it
                currentUser.linkWithCredential(credential)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            if (selectedImageUri != null) {
                                uploadImageAndSaveProfile(name, phone, email, car, carNumber, onSuccess)
                            } else {
                                updateProfile(name, phone, email, null, car, carNumber, onSuccess)
                            }
                        } else {
                            Toast.makeText(this, "Invalid OTP or phone already linked", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }



    private fun updateProfile(
        name: String,
        phone: String,
        email: String,
        imageUrl: String?,
        car: String,
        carNumber: String,
        onSuccess: () -> Unit) {
        val userData = mutableMapOf<String, Any>(
            "name" to name,
            "phone" to phone,
            "email" to email,
            "car" to car,
            "carNumber" to carNumber
        )
        if (imageUrl != null) {
            userData["profileImageUrl"] = imageUrl
        }

        user?.let { currentUser ->
            database.child(currentUser.uid).updateChildren(userData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                    Common.currentUser = User().apply {
                        this.name = name
                        this.number = phone
                        this.email = email
                        this.car = car
                        this.carNumber = carNumber
                        if (imageUrl != null) {
                            this.profileImageUrl = imageUrl
                        }
                    }
                    onSuccess() // Navigation or next step only after successful save
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
                }
        }
    }


    private fun selectProfileImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.data
            profileImageView.setImageURI(selectedImageUri)
        }
    }
}