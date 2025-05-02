package com.example.beneva

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.annotation.OptIn
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.widget.Button
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.exceptions.ClearCredentialException


class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private val db = FirebaseFirestore.getInstance()
    private var lastScannedCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.cameraPreview)
        cameraExecutor = Executors.newSingleThreadExecutor()

        startCamera()

        val signOutButton = findViewById<Button>(R.id.signOutButton)
        signOutButton.setOnClickListener {
            signOut() // Call your function here
        }

        val profileButton: Button = findViewById(R.id.profileButton)
        profileButton.setOnClickListener {
            val intent = Intent(this@MainActivity, ProfileActivity::class.java)
            startActivity(intent)
        }

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_ALL_FORMATS
                )
                .build()
            val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(options)

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(
                        cameraExecutor,
                        BarcodeAnalyzer(barcodeScanner)
                    )
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, analyzer
                )
            } catch (e: Exception) {
                Log.e("MainActivity", "Use case binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @ExperimentalGetImage
    private fun processImageProxy(scanner: BarcodeScanner, imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue?.trim()
                        if (!rawValue.isNullOrEmpty() && rawValue != lastScannedCode) {
                            lastScannedCode = rawValue
                            Log.d("MainActivity", "Barcode detected: $rawValue")
                            fetchProductInfo(rawValue)
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("MainActivity", "Barcode scanning failed", it)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun fetchProductInfo(barcode: String) {
        val sanitizedBarcode = barcode.trim()
        db.collection("products")
            .whereEqualTo("barcode", sanitizedBarcode)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val product = documents.documents[0]
                    val productName = product.getString("name") ?: "Unknown"
                    val ingredients = product.get("ingredients")?.let {
                        when (it) {
                            is List<*> -> it.joinToString(", ")
                            else -> it.toString()
                        }
                    } ?: "Not specified"
                    Toast.makeText(
                        this,
                        "Product: $productName\nIngredients: $ingredients",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this, "Product not found in database.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("MainActivity", "Error fetching product info", exception)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private inner class BarcodeAnalyzer(
        private val scanner: BarcodeScanner
    ) : ImageAnalysis.Analyzer {
        @ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            processImageProxy(scanner, imageProxy)
        }
    }

    private fun signOut() {
        FirebaseAuth.getInstance().signOut()

        val credentialManager = CredentialManager.create(this)
        val clearRequest = ClearCredentialStateRequest()

        credentialManager.clearCredentialStateAsync(
            clearRequest,
            null,
            ContextCompat.getMainExecutor(this),
            object : CredentialManagerCallback<Void?, ClearCredentialException> {
                override fun onResult(result: Void?) {
                    Log.d("MainActivity", "Credential state cleared.")
                }

                override fun onError(e: ClearCredentialException) {
                    Log.e("MainActivity", "Failed to clear credential state.", e)
                }
            }
        )

        val intent = Intent(this@MainActivity, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

}
