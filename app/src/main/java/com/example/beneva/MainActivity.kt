package com.example.beneva

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

        // Initialize Firebase database with local data
        initializeDatabase()

        startCamera()
    }

    private fun initializeDatabase() {
        val productsRef = db.collection("products")
        
        // First, clear the existing collection to ensure clean state
        productsRef.get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    Log.d("MainActivity", "Clearing existing database...")
                    val batch = db.batch()
                    documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit()
                        .addOnSuccessListener {
                            Log.d("MainActivity", "Database cleared successfully")
                            populateDatabase()
                        }
                        .addOnFailureListener { e ->
                            Log.e("MainActivity", "Error clearing database", e)
                        }
                } else {
                    populateDatabase()
                }
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error checking database", e)
            }
    }

    private fun populateDatabase() {
        val productsRef = db.collection("products")
        val jsonString = resources.openRawResource(R.raw.database)
            .bufferedReader()
            .use { it.readText() }
        
        val jsonArray = JSONArray(jsonString)
        Log.d("MainActivity", "Loading ${jsonArray.length()} products from local database")
        
        // Use a batch write for better performance and atomicity
        val batch = db.batch()
        
        for (i in 0 until jsonArray.length()) {
            val product = jsonArray.getJSONObject(i)
            val barcode = product.getString("barcode")
            val name = product.getString("name")
            
            // Create a document reference with the barcode as the document ID
            val docRef = productsRef.document(barcode)
            batch.set(docRef, product.toMap())
            
            Log.d("MainActivity", "Preparing to add product: $name (Barcode: $barcode)")
        }
        
        batch.commit()
            .addOnSuccessListener {
                Log.d("MainActivity", "All products added successfully")
                verifyDatabase()
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error adding products", e)
            }
    }

    private fun verifyDatabase() {
        val productsRef = db.collection("products")
        productsRef.get()
            .addOnSuccessListener { documents ->
                Log.d("MainActivity", "Database verification: Found ${documents.size()} products")
                documents.forEach { doc ->
                    val barcode = doc.getString("barcode")
                    val name = doc.getString("name")
                    Log.d("MainActivity", "Verified product: $name (Barcode: $barcode)")
                }
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error verifying database", e)
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
                            Toast.makeText(this, "Scanning barcode: $rawValue", Toast.LENGTH_SHORT).show()
                            fetchProductInfo(rawValue)
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("MainActivity", "Barcode scanning failed", it)
                    Toast.makeText(this, "Failed to scan barcode. Please try again.", Toast.LENGTH_SHORT).show()
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
        Log.d("MainActivity", "Searching for barcode: '$sanitizedBarcode'")
        
        // Query using document ID (barcode) instead of whereEqualTo
        db.collection("products")
            .document(sanitizedBarcode)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val productName = document.getString("name") ?: "Unknown"
                    val ingredients = document.get("ingredients")?.let {
                        when (it) {
                            is List<*> -> it.joinToString(", ")
                            else -> it.toString()
                        }
                    } ?: "Not specified"
                    val ecoScore = document.getLong("eco_score") ?: 0
                    
                    val message = """
                        Product: $productName
                        Ingredients: $ingredients
                        EcoScore: $ecoScore
                    """.trimIndent()
                    
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    Log.d("MainActivity", "Product found: $productName")
                } else {
                    Log.d("MainActivity", "No product found for barcode: $sanitizedBarcode")
                    Toast.makeText(this, "Product not found in database.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("MainActivity", "Error fetching product info", exception)
                Toast.makeText(this, "Error fetching product info. Please try again.", Toast.LENGTH_SHORT).show()
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

    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = this.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = this.get(key)
            map[key] = when (value) {
                is JSONArray -> value.toList()
                is JSONObject -> value.toMap()
                else -> value
            }
        }
        return map
    }

    private fun JSONArray.toList(): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until this.length()) {
            val value = this.get(i)
            list.add(when (value) {
                is JSONArray -> value.toList()
                is JSONObject -> value.toMap()
                else -> value
            })
        }
        return list
    }
}
