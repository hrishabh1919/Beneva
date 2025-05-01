package com.example.beneva

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.FirebaseDatabase
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var barcodeScanner: BarcodeScanner
    private lateinit var cameraExecutor: ExecutorService
    private val CAMERA_PERMISSION_REQUEST = 1001
    private val database = FirebaseDatabase.getInstance().reference

    // UI Components
    private lateinit var productName: TextView
    private lateinit var ecoScore: TextView
    private lateinit var veganStatus: TextView
    private lateinit var recyclableStatus: TextView
    private lateinit var allergens: TextView
    private lateinit var packagingInfo: TextView
    private lateinit var brandSustainability: TextView
    private lateinit var suggestAlternativesButton: MaterialButton
    private lateinit var cameraPreview: PreviewView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        initializeViews()

        // Initialize barcode scanner
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_E
            )
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check and request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
        } else {
            startCamera()
        }
    }

    private fun initializeViews() {
        productName = findViewById(R.id.productName)
        ecoScore = findViewById(R.id.ecoScore)
        veganStatus = findViewById(R.id.veganStatus)
        recyclableStatus = findViewById(R.id.recyclableStatus)
        allergens = findViewById(R.id.allergens)
        packagingInfo = findViewById(R.id.packagingInfo)
        brandSustainability = findViewById(R.id.brandSustainability)
        suggestAlternativesButton = findViewById(R.id.suggestAlternativesButton)
        cameraPreview = findViewById(R.id.cameraPreview)

        suggestAlternativesButton.setOnClickListener {
            Toast.makeText(this, R.string.alternatives_coming_soon, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(cameraPreview.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            processBarcode(image)
                        }
                        imageProxy.close()
                    }
                }

            try {
                // Unbind any previous use cases
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Toast.makeText(this, R.string.camera_error, Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processBarcode(image: InputImage) {
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue
                    if (rawValue != null) {
                        // Stop scanning after finding a barcode
                        cameraExecutor.shutdown()
                        fetchProductInfo(rawValue)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, R.string.barcode_scan_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchProductInfo(barcode: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = database.child("products").child(barcode).get().await()
                if (snapshot.exists()) {
                    val product = snapshot.getValue(Product::class.java)
                    product?.let { displayProductInfo(it) }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, R.string.product_not_found, Toast.LENGTH_SHORT).show()
                        // Restart camera after product not found
                        startCamera()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, R.string.fetch_error, Toast.LENGTH_SHORT).show()
                    // Restart camera after error
                    startCamera()
                }
            }
        }
    }

    private fun displayProductInfo(product: Product) {
        runOnUiThread {
            // Update product name
            productName.text = product.name

            // Update EcoScore
            ecoScore.text = getString(R.string.eco_score, product.ecoScore)

            // Update vegan status
            if (product.isVegan) {
                veganStatus.visibility = View.VISIBLE
            } else {
                veganStatus.visibility = View.GONE
            }

            // Update recyclability status
            if (product.recyclability.isNotEmpty()) {
                recyclableStatus.visibility = View.VISIBLE
                recyclableStatus.text = getString(R.string.recyclable, product.recyclability)
            } else {
                recyclableStatus.visibility = View.GONE
            }

            // Update allergens
            if (product.allergens.isNotEmpty()) {
                allergens.text = getString(R.string.allergens, product.allergens.joinToString(", "))
            } else {
                allergens.text = getString(R.string.no_allergens)
            }

            // Update packaging info
            packagingInfo.text = getString(R.string.packaging, product.packagingMaterial)

            // Update brand sustainability
            brandSustainability.text = getString(R.string.brand_sustainability, product.brandSustainability)

            // Show suggest alternatives button
            suggestAlternativesButton.visibility = View.VISIBLE
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        barcodeScanner.close()
        cameraExecutor.shutdown()
    }
}

data class Product(
    val barcode: String = "",
    val name: String = "",
    val ecoScore: Int = 0,
    val allergens: List<String> = emptyList(),
    val isVegan: Boolean = false,
    val recyclability: String = "",
    val packagingMaterial: String = "",
    val brandSustainability: String = ""
) 