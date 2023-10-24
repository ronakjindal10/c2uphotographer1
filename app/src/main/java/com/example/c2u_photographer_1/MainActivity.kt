package com.example.c2u_photographer_1
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.FileObserver
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request.Builder
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Response
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.Exception
import java.io.IOException
import kotlin.math.min
//import androidx.databinding.DataBindingUtil
import com.example.c2u_photographer_1.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit
import android.widget.ScrollView

// This is the main activity class of the app
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // This is the directory where the photos are getting added
    val photoDir = "/storage/emulated/0/Nikon downloads"

    // This is the directory where the watermark is
    val watermarkDir = "/storage/emulated/0/Watermark/watermark.png"

    // This is the URL of the API to upload photos
    val apiUrl = "https://c2u-api.onrender.com/upload-photo"

    // This is the request code for selecting a photo from the gallery
//    val pickImage = 100

    // This is the bitmap object for the watermark image
    var watermarkBitmap: Bitmap? = null

    // This is the file observer object that monitors the photo directory for changes
    var fileObserver: FileObserver? = null

    // This is the text view object that displays the logs to the user
    var logTextView: TextView? = null

    // This is the method that is called when the activity is created
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize the log text view
        logTextView = binding.logTextView

        // Load the watermark image from the watermark directory
        loadWatermark()

        // Start the file observer to watch for new photos in the photo directory
        startFileObserver()

        // Set a click listener for the select photo button
//        binding.selectPhotoButton.setOnClickListener {
//            // Create an intent to pick a photo from the gallery
//            val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
//            startActivityForResult(gallery, pickImage)
//        }
    }

    // This is the method that is called when an activity returns a result
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (resultCode == RESULT_OK && requestCode == pickImage) {
//            // Get the image URI from the data intent
//            val imageUri = data?.data
//
//            // Check if the image URI is not null
//            if (imageUri != null) {
//                // Get the image file path from the image URI
//                val imagePath = getImagePathFromUri(imageUri)
//
//                // Check if the image file path is not null or empty
//                if (!imagePath.isNullOrEmpty()) {
//                    // Process and upload the image file
//                    processAndUploadImageFile(imagePath)
//                } else {
//                    // Log an error message
//                    logMessage("Could not get image file path from URI")
//                }
//            } else {
//                // Log an error message
//                logMessage("Could not get image URI from data intent")
//            }
//        }
//    }

    // This is the method that loads the watermark image from the watermark directory
    fun loadWatermark() {
        try {
            // Create a file object for the watermark image file
            val watermarkFile = File(watermarkDir)

            // Check if the watermark file exists and is readable
            if (watermarkFile.exists() && watermarkFile.canRead()) {
                // Decode the watermark file into a bitmap object
                watermarkBitmap = BitmapFactory.decodeFile(watermarkFile.absolutePath)

                // Log a success message
                logMessage("Watermark image loaded successfully")
            } else {
                // Log an error message
                logMessage("Watermark file does not exist or is not readable")
            }
        } catch (e: Exception) {
            // Log an exception message
            logMessage("Exception while loading watermark image: ${e.message}")
        }
    }

    // This is the method that starts the file observer to watch for new photos in the photo directory
    fun startFileObserver() {
        try {
            // Create a file observer object for the photo directory with a close write event mask
            fileObserver = object : FileObserver(photoDir, FileObserver.CLOSE_WRITE) {

                // This is the method that is called when an event occurs in the photo directory
                override fun onEvent(event: Int, path: String?) {

                    // Check if the event is a close write event and the path is not null or empty
                    if (event == FileObserver.CLOSE_WRITE && !path.isNullOrEmpty()) {

                        // Get the full file path of the new photo file by appending it to the photo directory path
                        val filePath = "$photoDir/$path"

                        // Process and upload the new photo file
                        processAndUploadImageFile(filePath)
                    }
                }
            }

            // Start watching for events in the photo directory
            fileObserver?.startWatching()

            // Log a success message
            logMessage("File observer started successfully")
        } catch (e: Exception) {
            // Log an exception message
            logMessage("Exception while starting file observer: ${e.message}")
        }
    }

    // This is the method that stops the file observer
    fun stopFileObserver() {
        try {
            // Stop watching for events in the photo directory
            fileObserver?.stopWatching()

            // Log a success message
            logMessage("File observer stopped successfully")
        } catch (e: Exception) {
            // Log an exception message
            logMessage("Exception while stopping file observer: ${e.message}")
        }
    }

    // This is the method that gets the image file path from the image URI
    fun getImagePathFromUri(uri: Uri): String? {
        var imagePath: String? = null
        try {
            // Create a cursor object to query the image URI
            val cursor = contentResolver.query(uri, null, null, null, null)

            // Move the cursor to the first row
            cursor?.moveToFirst()

            // Get the index of the data column
            val index = cursor?.getColumnIndex(MediaStore.Images.ImageColumns.DATA)

            // Check if the index is not null and is valid
            if (index != null && index >= 0) {
                // Get the image file path from the cursor
                imagePath = cursor.getString(index)
            }

            // Close the cursor
            cursor?.close()
        } catch (e: Exception) {
            // Log an exception message
            logMessage("Exception while getting image path from URI: ${e.message}")
        }
        return imagePath
    }

    // This is the method that processes and uploads the image file
    fun processAndUploadImageFile(filePath: String) {
        try {
            // Create a file object for the image file
            val imageFile = File(filePath)

            // Check if the image file exists and is readable
            if (imageFile.exists() && imageFile.canRead()) {

                // Log a message that the image file is being processed
                logMessage("Processing image file: ${imageFile.name}")

                // Decode the image file into a bitmap object
                val originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)

                // Resize and compress the bitmap object
                val resizedBitmap = resizeAndCompressBitmap(originalBitmap)

                // Add a watermark to the bitmap object
                val watermarkedBitmap = addWatermarkToBitmap(resizedBitmap)

                // Convert the bitmap object into a byte array
                val byteArray = bitmapToByteArray(watermarkedBitmap)

                // Upload the byte array to the API server
                uploadByteArrayToApi(byteArray, imageFile.name)
            } else {
                // Log an error message
                logMessage("Image file does not exist or is not readable")
            }
        } catch (e: Exception) {
            // Log an exception message
            logMessage("Exception while processing and uploading image file: ${e.message}")
        }
    }

    // This is the method that resizes and compresses the bitmap object
    fun resizeAndCompressBitmap(originalBitmap: Bitmap): Bitmap {

        // This is the maximum width and height of the resized bitmap in pixels
        val maxWidth = 2560
        val maxHeight = 1440

        // This is the quality of the compressed bitmap in percentage
        val quality = 80

        // Get the original width and height of the bitmap in pixels
        val originalWidth = originalBitmap.width
        val originalHeight = originalBitmap.height

        // Calculate the scale factor for resizing the bitmap
        val scaleFactor = min(maxWidth.toFloat() / originalWidth, maxHeight.toFloat() / originalHeight)

        // Create a matrix object for scaling the bitmap
        val matrix = Matrix()

        // Set the scale factor to the matrix
        matrix.postScale(scaleFactor, scaleFactor)

        // Create a new bitmap object by applying the matrix to the original bitmap
        val resizedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalWidth, originalHeight, matrix, true)

        // Create a byte output stream object to write the compressed bitmap data
        val byteOutputStream = ByteArrayOutputStream()

        // Compress the resized bitmap into a JPEG format with the given quality and write it to the byte output stream
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteOutputStream)

        // Convert the byte output stream into a byte array
        val byteArray = byteOutputStream.toByteArray()

        // Decode the byte array into a new bitmap object
        val compressedBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

        // Return the compressed bitmap object
        return compressedBitmap

    }

    // This is the method that adds a watermark to the bitmap object
    fun addWatermarkToBitmap(originalBitmap: Bitmap): Bitmap {

        // Check if the watermark bitmap is not null
        if (watermarkBitmap != null) {

            // Get the original width and height of the bitmap in pixels
            // Get the original width and height of the bitmap in pixels
            val originalWidth = originalBitmap.width
            val originalHeight = originalBitmap.height

// Get the watermark width and height in pixels
            val watermarkWidth = watermarkBitmap!!.width
            val watermarkHeight = watermarkBitmap!!.height

// Calculate the margin for placing the watermark at the bottom right corner of the bitmap
            val margin = 10

// Create a new bitmap object with the same dimensions as the original bitmap
            val watermarkedBitmap = Bitmap.createBitmap(originalWidth, originalHeight, originalBitmap.config)

// Create a canvas object to draw on the new bitmap
            val canvas = Canvas(watermarkedBitmap)

// Draw the original bitmap on the canvas
            canvas.drawBitmap(originalBitmap, 0f, 0f, null)

// Draw the watermark bitmap on the canvas at the bottom right corner with some margin
            canvas.drawBitmap(watermarkBitmap!!, (originalWidth - watermarkWidth - margin).toFloat(), (originalHeight - watermarkHeight - margin).toFloat(), null)

// Return the watermarked bitmap object
            return watermarkedBitmap

        } else {
// Log an error message
            logMessage("Watermark image is null")
        }

// Return the original bitmap object as a fallback
        return originalBitmap

    }

    // This is the method that converts the bitmap object into a byte array
    fun bitmapToByteArray(bitmap: Bitmap): ByteArray {

// Create a byte output stream object to write the bitmap data
        val byteOutputStream = ByteArrayOutputStream()

// Compress the bitmap into a JPEG format and write it to the byte output stream
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteOutputStream)

// Convert the byte output stream into a byte array
        val byteArray = byteOutputStream.toByteArray()

// Return the byte array
        return byteArray

    }

    fun uploadByteArrayToApi(byteArray: ByteArray, fileName: String) {
        // Log a message that the byte array is being uploaded
        logMessage("Uploading byte array: $fileName")

        // Create a multipart request body object with the photo field and the byte array as the value
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("photo", fileName, byteArray.toRequestBody("image/jpeg".toMediaType()))
            .build()

        // Create a request object with the API URL and the request body
        val request = Builder()
            .url(apiUrl)
            .post(requestBody)
            .build()

        // Create an OK HTTP client object to execute the request with a 15 second timeout
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        // Define a variable to keep track of the number of retries
        var retries = 0
        // Define a variable to store the maximum number of retries allowed
        val maxRetries = 3

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                // Check if we have reached the maximum number of retries
                if (retries < maxRetries) {
                    // Increment the number of retries
                    retries++

                    // Log an error message with the exception message and retry count
                    logMessage("Upload failed (${e.message}), retrying ($retries)")

                    // Enqueue the request again with the same callback function
                    call.clone().enqueue(this)
                } else {
                    // Log an error message with the exception message and retry count
                    logMessage("Upload failed (${e.message}), giving up after $retries retries")
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                // Check if the response is successful
                if (response.isSuccessful) {
                    try {
                        // Get the response body as a string
                        val responseBody = response.body?.string()

                        // Check if the response body is not null or empty
                        if (!responseBody.isNullOrEmpty()) {
                            // Parse the response body as a JSON object
                            val jsonObject = JSONObject(responseBody)

                            // Get the message and fileName fields from the JSON object
                            val message = jsonObject.getString("message")
                            val fileName = jsonObject.getString("fileName")

                            // Log a success message with the message and fileName fields
                            logMessage("Upload successful: $message, $fileName")
                        } else {
                            // Check if we have reached the maximum number of retries
                            if (retries < maxRetries) {
                                // Increment the number of retries
                                retries++

                                // Log an error message with the retry count
                                logMessage("Upload failed: Response body is null or empty, retrying ($retries)")

                                // Enqueue the request again with the same callback function
                                call.clone().enqueue(this)
                            } else {
                                // Log an error message with the retry count
                                logMessage("Upload failed: Response body is null or empty, giving up after $retries retries")
                            }
                        }
                    } catch (e: Exception) {
                        // Check if we have reached the maximum number of retries
                        if (retries < maxRetries) {
                            // Increment the number of retries
                            retries++

                            // Log an exception message with the retry count
                            logMessage("Exception while parsing response (${e.message}), retrying ($retries)")

                            // Enqueue the request again with the same callback function
                            call.clone().enqueue(this)
                        } else {
                            // Log an exception message with the retry count
                            logMessage("Exception while parsing response (${e.message}), giving up after $retries retries")
                        }
                    }
                } else {
                    // Check if we have reached the maximum number of retries
                    if (retries < maxRetries) {
                        // Increment the number of retries
                        retries++

                        // Log an error message with the response code, message, and retry count
                        logMessage("Upload failed: ${response.code} ${response.message}, retrying ($retries)")

                        // Enqueue the request again with the same callback function
                        call.clone().enqueue(this)
                    } else {
                        // Log an error message with the response code, message, and retry count
                        logMessage("Upload failed: ${response.code} ${response.message}, giving up after $retries retries")
                    }
                }
            }
        })
    }

    // This is the method that logs a message to the log text view and scrolls it to the bottom
    fun logMessage(message: String) {
        runOnUiThread {
// Append a new line character to the message
            val newLineMessage = "$message\n"

// Append the new line message to the log text view text
            logTextView?.append(newLineMessage)

// Get the layout of the log text view
            val layout = logTextView?.layout

// Check if the layout is not null
            if (layout != null) {
// Get the scroll amount to scroll to the bottom of the text view
                val scrollAmount = layout.getLineTop(logTextView!!.lineCount) - logTextView!!.height

// Check if the scroll amount is positive or zero
                if (scrollAmount >= 0) {
// Scroll to the bottom of the text view by using smooth scroll by amount method
                    logTextView?.scrollTo(0, scrollAmount)
                } else {
// Scroll to the top of the text view
                    logTextView?.scrollTo(0, 0)
                }
            }
        }
    }

    // This is the method that is called when the activity is destroyed
    override fun onDestroy() {
        super.onDestroy()

// Stop the file observer
        stopFileObserver()
    }
}