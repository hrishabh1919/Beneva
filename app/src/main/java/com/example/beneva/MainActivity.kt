package com.example.beneva

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.annotation.OptIn
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.exceptions.ClearCredentialException
import android.hardware.camera2.CameraManager
import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private var lastScannedCode: String? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var barcodeScanner: BarcodeScanner? = null
    private var preview: Preview? = null
    private var isCameraInitialized = false
    private var productsData: JSONArray? = null

    // UI elements
    private lateinit var productNameText: TextView
    private lateinit var ecoScoreText: TextView
    private lateinit var veganStatusText: TextView
    private lateinit var recyclableStatusText: TextView
    private lateinit var allergensText: TextView
    private lateinit var packagingInfoText: TextView
    private lateinit var brandSustainabilityText: TextView
    private lateinit var rescanButton: MaterialButton
    private lateinit var scanStatusText: TextView
    private lateinit var suggestAlternativesButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        previewView = findViewById(R.id.cameraPreview)
        productNameText = findViewById(R.id.productName)
        ecoScoreText = findViewById(R.id.ecoScore)
        veganStatusText = findViewById(R.id.veganStatus)
        recyclableStatusText = findViewById(R.id.recyclableStatus)
        allergensText = findViewById(R.id.allergens)
        packagingInfoText = findViewById(R.id.packagingInfo)
        brandSustainabilityText = findViewById(R.id.brandSustainability)
        rescanButton = findViewById(R.id.rescanButton)
        scanStatusText = findViewById(R.id.scanStatusText)
        suggestAlternativesButton = findViewById(R.id.suggestAlternativesButton)

        // Load JSON database
        loadProductsDatabase()

        // Initialize camera
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Set up rescan button
        rescanButton.setOnClickListener {
            resetProductInfo()
            lastScannedCode = null
            startCamera()
        }

        // Set up sign out button
        findViewById<MaterialButton>(R.id.signOutButton).setOnClickListener {
            signOut()
        }

        // Check camera availability before starting
        if (isCameraAvailable()) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera is not available", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun loadProductsDatabase() {
        try {
            val inputStream = resources.openRawResource(R.raw.database)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.readText()
            reader.close()

            val jsonObject = JSONObject(jsonString)
            productsData = jsonObject.optJSONArray("products")

            Log.d("MainActivity", "Loaded ${productsData?.length() ?: 0} products from database")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading products database", e)
            Toast.makeText(this, "Error loading products database", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetProductInfo() {
        productNameText.text = ""
        ecoScoreText.text = getString(R.string.eco_score)
        veganStatusText.visibility = View.GONE
        recyclableStatusText.visibility = View.GONE
        allergensText.text = ""
        packagingInfoText.text = ""
        brandSustainabilityText.text = ""
        rescanButton.visibility = View.GONE
        scanStatusText.visibility = View.GONE
        suggestAlternativesButton.visibility = View.GONE
    }

    private fun isCameraAvailable(): Boolean {
        return try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager.cameraIdList.isNotEmpty()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking camera availability", e)
            false
        }
    }

    private fun startCamera() {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    setupCamera()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error setting up camera", e)
                    Toast.makeText(this, "Error setting up camera. Please restart the app.", Toast.LENGTH_LONG).show()
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting camera", e)
            Toast.makeText(this, R.string.error_loading_products_database, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupCamera() {
        if (isCameraInitialized) {
            return
        }

        try {
            preview = Preview.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            barcodeScanner = BarcodeScanning.getClient(options)

            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(previewView.display.rotation)
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(
                        cameraExecutor,
                        BarcodeAnalyzer(barcodeScanner!!)
                    )
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
                isCameraInitialized = true
            } catch (e: Exception) {
                Log.e("MainActivity", "Use case binding failed", e)
                Toast.makeText(this, "Error binding camera. Please restart the app.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in setupCamera", e)
            Toast.makeText(this, "Error setting up camera. Please restart the app.", Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(ExperimentalGetImage::class)
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
        if (productsData == null) {
            Toast.makeText(this, "Product database not loaded", Toast.LENGTH_SHORT).show()
            return
        }

        val sanitizedBarcode = barcode.trim()
        var productFound = false

        // Search the JSON array for the product with matching barcode
        for (i in 0 until productsData!!.length()) {
            try {
                val product = productsData!!.getJSONObject(i)
                if (product.getString("barcode") == sanitizedBarcode) {
                    displayProductInfo(product)
                    productFound = true
                    break
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error parsing product", e)
            }
        }

        if (!productFound) {
            runOnUiThread {
                scanStatusText.text = getString(R.string.product_not_found)
                scanStatusText.visibility = View.VISIBLE
                rescanButton.visibility = View.VISIBLE
            }
        }
    }

    private fun displayProductInfo(product: JSONObject) {
        runOnUiThread {
            try {
                // Stop the camera as we've found a product
                imageAnalysis?.clearAnalyzer()

                // Display product information
                productNameText.text = product.getString("name")

                // EcoScore
                val ecoScore = product.optString("ecoscore", "?")
                ecoScoreText.text = getString(R.string.eco_score, "?")

                // Vegan status
                if (product.optBoolean("vegan", false)) {
                    veganStatusText.visibility = View.VISIBLE
                } else {
                    veganStatusText.visibility = View.GONE
                }

                // Recyclable status
                if (product.optBoolean("recyclable", false)) {
                    recyclableStatusText.visibility = View.VISIBLE
                } else {
                    recyclableStatusText.visibility = View.GONE
                }

                // Allergens
                val allergens = product.optString("allergens", "")
                if (allergens.isNotEmpty()) {
                    allergensText.text = getString(R.string.allergens,allergens)
                } else {
                    allergensText.text = getString(R.string.allergens_none)
                }

                // Packaging info
                val packaging = product.optString("packaging", "")
                if (packaging.isNotEmpty()) {
                    packagingInfoText.text = getString(R.string.packaging,packaging)
                } else {
                    packagingInfoText.text = getString(R.string.packaging_not_available)
                }

                // Brand sustainability
                val sustainability = product.optString("brand_sustainability", "")
                if (sustainability.isNotEmpty()) {
                    brandSustainabilityText.text = getString(R.string.brand_sustainability,sustainability)
                } else {
                    brandSustainabilityText.text = getString(R.string.brand_sustainability_not_available)
                }

                // Show rescan button and alternatives button
                rescanButton.visibility = View.VISIBLE
                suggestAlternativesButton.visibility = View.VISIBLE

            } catch (e: Exception) {
                Log.e("MainActivity", "Error displaying product info", e)
                Toast.makeText(this, getString(R.string.error_displaying_product_info), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
            isCameraInitialized = false
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onPause", e)
        }
    }

    override fun onResume() {
        super.onResume()
        if (lastScannedCode == null && isCameraAvailable()) {
            setupCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            isCameraInitialized = false
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onDestroy", e)
        }
    }

    private inner class BarcodeAnalyzer(
        private val scanner: BarcodeScanner
    ) : ImageAnalysis.Analyzer {
        @OptIn(ExperimentalGetImage::class)
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
