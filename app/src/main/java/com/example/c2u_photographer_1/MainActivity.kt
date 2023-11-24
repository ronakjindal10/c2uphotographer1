package com.example.c2u_photographer_1
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.view.View
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
import java.io.FileInputStream
import java.util.Stack

// This is the main activity class of the app
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // This is the directory where the photos are getting added
    // Nikon on Redmi K20:
    // val photoDir = "/storage/emulated/0/Nikon downloads"
    // Bangalore photographer's Sony A7M3:
    val photoDir = "/storage/emulated/0/DCIM/Transfer & Tagging add-on"
    // val photoDir = "/storage/emulated/0/DCIM/Transfer & Tagging add-on/e522445b-bb7e-468b-9c1f-b5ffd19c2947/6c00f2fc-7bd3-48cc-a7e5-4a151aaed57b/b63df105-c1ae-4051-83dd-ab8a0bd3feef"
    // val photoDir = "/storage/emulated/0/DCIM/Transfer & Tagging add-on/e522445b-bb7e-468b-9c1f-b5ffd19c2947/2cfbb0a6-3d29-43e1-a59e-9f39124d15c4"
    // Hemant Royale Camera's Sony Camera below:
    // val photoDir = "/storage/emulated/0/DCIM/Transfer & Tagging add-on/e522445b-bb7e-468b-9c1f-b5ffd19c2947/a30304c5-5f08-4670-a8a9-5de328ce82d5/02d9ec51-f471-4afb-8476-782634a762f6"
    // val photoDir = "/storage/emulated/10/DCIM/Transfer & Tagging add-on/be86c1fd-3dec-4628-80de-5dd2b088f692/3a760bb9-876b-4836-95e7-cba9f2c6e2d3/e5528206-d021-4fdf-9ad6-b9efd87147d2"

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
//    var observer: FileObserver? = null
    private val allFileObservers = mutableListOf<FileObserver>()

    // This is the text view object that displays the logs to the user
    var logTextView: TextView? = null

    // We'll monitor the following events:
    val newFolderEvent = 1073742080
    val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.CREATE or newFolderEvent
    // val mask = FileObserver.CLOSE_WRITE
    // val mask = FileObserver.ALL_EVENTS

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
    }

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
                logMessage("Watermark image loaded successfully", Color.GREEN)
            } else {
                // Log an error message
                logMessage("Watermark file does not exist or is not readable", Color.YELLOW)
            }
        } catch (e: Exception) {
            // Log an exception message
            logMessage("Exception while loading watermark image: ${e.message}", Color.YELLOW)
        }
    }

    //This function checks if the file is ready to be uploaded, avoiding partially downloaded files
    fun isFileStable(file: File, delayMillis: Long = 2000): Boolean {
        try {
            val initialSize = file.length()
            logMessage("Checking file size, initial size: $initialSize")

            // Wait for a specified delay
            Thread.sleep(delayMillis)

            val finalSize = file.length()
            logMessage("Checking file size, final size: $finalSize")

            // File is considered stable if its size hasn't changed
            return initialSize == finalSize
        } catch (e: Exception) {
            logMessage("Exception while checking file stability: ${e.message}", Color.YELLOW)
            return false
        }
    }

//     This is the method that starts the file observer to watch for new photos in the photo directory
//    fun startFileObserver() {
//        try {
//            // Create a file observer object for the photo directory with a close write event mask
//            fileObserver = object : FileObserver(photoDir, FileObserver.ALL_EVENTS) {
//
//                // This is the method that is called when an event occurs in the photo directory
//                override fun onEvent(event: Int, path: String?) {
//                    // LogMessage with details of the file observer, event emitted and current time
//                    logMessage("File observer event: $event, path: $path, current time: ${System.currentTimeMillis()}", Color.WHITE)
//
//                    // Check if the event is a close write event and the path is not null or empty
//                    if (event == FileObserver.CLOSE_WRITE && !path.isNullOrEmpty()) {
//
//                        // Get the full file path of the new photo file by appending it to the photo directory path
//                        val filePath = "$photoDir/$path"
//                        processAndUploadImageFile(filePath)
//                    }
//                }
//            }
//
//            // Start watching for events in the photo directory
//            fileObserver?.startWatching()
//
//            // Log a success message
//            logMessage("File observer started successfully", Color.GREEN)
//        } catch (e: Exception) {
//            // Log an exception message
//            logMessage("Exception while starting file observer: ${e.message}", Color.RED)
//        }
//    }

    // Recursive function to start file observers on all subdirectories
    fun startWatchingDirectory(directory: File) {
        try {
            logMessage(
                "Called startWatchingDirectory with directory: ${directory.path}",
                Color.GRAY
            )
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    // Start watching this directory
                    startWatchingDirectory(file)
                }
            }
            // Create and start a file observer for this directory
            fileObserver = object : FileObserver(directory.path, mask) {
                override fun onEvent(event: Int, path: String?) {
                    logMessage(
                        "File observer event: $event, path: $path, current time: ${System.currentTimeMillis()}",
                        Color.GRAY
                    )
                    // logMessage("Event is equal to close write or moved to? ${event == FileObserver.CLOSE_WRITE || event == FileObserver.MOVED_TO}", Color.GRAY)
                    if ((event == FileObserver.CLOSE_WRITE || event == FileObserver.MOVED_TO || event == newFolderEvent) && path != null && isFileStable(
                            File("${directory.path}/$path")
                        )){
                        // if (event == FileObserver.CLOSE_WRITE && path != null){
                        val filePath = "${directory.path}/$path"
                        // Check if it's a directory or a file
                        if (File(filePath).isDirectory) {
                            // If it's a directory, start watching it
                            logMessage(
                                "Inside onEvent, detected new folder, calling startWatchingDirectory next for: ${
                                    File(   
                                        filePath
                                    ).path
                                }", Color.GRAY
                            )
                            startWatchingDirectory(File(filePath))
                        } else {
                            // If it's a file, process it
                            logMessage(
                                "Inside onEvent, detected new file, calling processAndUploadImageFile next for: ${
                                    File(
                                        filePath
                                    ).path
                                }", Color.GRAY
                            )
                            processAndUploadImageFile(filePath)
                        }
                    }
                }
            }
            (fileObserver as FileObserver).startWatching()
            allFileObservers.add(fileObserver as FileObserver)
        } catch (e: Exception) {
            // Log an exception message
            logMessage("Exception while starting file observer: ${e.message}", Color.RED)
        }
    }

    // Function to start the recursive file observer
    fun startFileObserver() {
        try {
            // Get the photo directory as a File object
            val photoDirectory = File(photoDir)

            // Start watching the photo directory and its subdirectories
            startWatchingDirectory(photoDirectory)

            // Log a success message
            logMessage("File observer started successfully", Color.GREEN)
        } catch (e: Exception) {
            // Log an exception message
            logMessage("Exception while starting file observer: ${e.message}", Color.RED)
        }
    }

    // This is the method that stops the file observer
    fun stopFileObserver() {
        try {
            // Stop watching for events in the photo directory
            // fileObserver?.stopWatching()
            allFileObservers.forEach { it.stopWatching() }
            allFileObservers.clear()

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
                val resizedBitmap = resizeAndCompressBitmap(originalBitmap, filePath)

                // Add a watermark to the bitmap object
                val watermarkedBitmap = addWatermarkToBitmap(resizedBitmap)

                // Convert the bitmap object into a byte array
                val byteArray = bitmapToByteArray(watermarkedBitmap)

                // Upload the byte array to the API server
                uploadByteArrayToApi(byteArray, imageFile.name)
            } else {
                // Log an error message
                logMessage("Image file does not exist or is not readable", Color.RED)
            }
        } catch (e: Exception) {
            // Log an exception message
            logMessage("Exception while processing and uploading image file: ${e.message}", Color.YELLOW)
        }
    }

    // This is the method that resizes and compresses the bitmap object
//    fun resizeAndCompressBitmap(originalBitmap: Bitmap): Bitmap {
//
//        // This is the maximum width and height of the resized bitmap in pixels
//        val maxWidth = 2560
//        val maxHeight = 1440
//
//        // This is the quality of the compressed bitmap in percentage
//        val quality = 75
//
//        // Get the original width and height of the bitmap in pixels
//        val originalWidth = originalBitmap.width
//        val originalHeight = originalBitmap.height
//
//        // Calculate the scale factor for resizing the bitmap
//        val scaleFactor = min(maxWidth.toFloat() / originalWidth, maxHeight.toFloat() / originalHeight)
//
//        // Create a matrix object for scaling the bitmap
//        val matrix = Matrix()
//
//        // Set the scale factor to the matrix
//        matrix.postScale(scaleFactor, scaleFactor)
//
//        // Create a new bitmap object by applying the matrix to the original bitmap
//        val resizedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalWidth, originalHeight, matrix, true)
//
//        // Create a byte output stream object to write the compressed bitmap data
//        val byteOutputStream = ByteArrayOutputStream()
//
//        // Compress the resized bitmap into a JPEG format with the given quality and write it to the byte output stream
//        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteOutputStream)
//
//        // Convert the byte output stream into a byte array
//        val byteArray = byteOutputStream.toByteArray()
//
//        // Decode the byte array into a new bitmap object
//        val compressedBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
//
//        // Return the compressed bitmap object
//        return compressedBitmap
//
//    }
    fun resizeAndCompressBitmap(originalBitmap: Bitmap, filePath: String): Bitmap {
        val maxWidth = 2560
        val maxHeight = 1440
        val quality = 75

        val originalWidth = originalBitmap.width
        val originalHeight = originalBitmap.height

        val exif = ExifInterface(filePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()

        // Apply rotation based on orientation
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }

        // Calculate the scale factor for resizing the bitmap
        val scaleFactor = min(maxWidth.toFloat() / originalWidth, maxHeight.toFloat() / originalHeight)

        // Apply scaling to the matrix
        matrix.postScale(scaleFactor, scaleFactor)

        // Create a new bitmap by applying the matrix to the original bitmap
        val resizedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalWidth, originalHeight, matrix, true)

        // Create a byte output stream for compressed bitmap data
        val byteOutputStream = ByteArrayOutputStream()

        // Compress the resized bitmap into JPEG format with the given quality and write to the byte output stream
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteOutputStream)

        // Convert the byte output stream to a byte array
        val byteArray = byteOutputStream.toByteArray()

        // Decode the byte array into a new bitmap
        val compressedBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

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
            logMessage("Watermark image is null", Color.YELLOW)
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
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
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
                    logMessage("Upload failed for file ${fileName} (${e.message}), retrying ($retries)", Color.YELLOW)

                    // Enqueue the request again with the same callback function
                    call.clone().enqueue(this)
                } else {
                    // Log an error message with the exception message and retry count in red colour
                    logMessage("Upload failed for file ${fileName} (${e.message}), giving up after $retries retries", Color.RED)
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
                            val apiFileName = jsonObject.getString("fileName")

                            // Log a success message with the message and fileName fields
                            logMessage("Upload successful of $fileName: $message, $apiFileName", Color.GREEN)
                        } else {
                            // Check if we have reached the maximum number of retries
                            if (retries < maxRetries) {
                                // Increment the number of retries
                                retries++

                                // Log an error message with the retry count
                                logMessage("Upload failed for file ${fileName}: Response body is null or empty, retrying ($retries)", Color.YELLOW)

                                // Enqueue the request again with the same callback function
                                call.clone().enqueue(this)
                            } else {
                                // Log an error message with the retry count
                                logMessage("Upload failed for file ${fileName}: Response body is null or empty, giving up after $retries retries", Color.RED)
                            }
                        }
                    } catch (e: Exception) {
                        // Check if we have reached the maximum number of retries
                        if (retries < maxRetries) {
                            // Increment the number of retries
                            retries++

                            // Log an exception message with the retry count
                            logMessage("Exception while parsing response for file ${fileName} (${e.message}), retrying ($retries)", Color.YELLOW)

                            // Enqueue the request again with the same callback function
                            call.clone().enqueue(this)
                        } else {
                            // Log an exception message with the retry count
                            logMessage("Exception while parsing response for file ${fileName} (${e.message}), giving up after $retries retries", Color.RED)
                        }
                    }
                } else {
                    // Check if we have reached the maximum number of retries
                    if (retries < maxRetries) {
                        // Increment the number of retries
                        retries++

                        // Log an error message with the response code, message, and retry count
                        logMessage("Upload failed for file ${fileName}: ${response.code} ${response.message}, retrying ($retries)", Color.YELLOW)

                        // Enqueue the request again with the same callback function
                        call.clone().enqueue(this)
                    } else {
                        // Log an error message with the response code, message, and retry count
                        logMessage("Upload failed for file ${fileName}: ${response.code} ${response.message}, giving up after $retries retries", Color.RED)
                    }
                }
            }
        })
    }

    // This is the method that logs a message to the log text view and scrolls it to the bottom
    fun logMessage(message: String, color: Int = Color.GRAY) {
        runOnUiThread {
            // Save current color
            // val originalColor = logTextView?.currentTextColor

            // Save current length of text
            val originalLength = logTextView?.length()

            // Append a new line character to the message
            logTextView?.append("$message\n")

            // Get text as SpannableString
            val spannableText = SpannableString(logTextView?.text)

            // Set color on only the new span
            if (originalLength != null) {
                logTextView?.length()?.let {
                    spannableText.setSpan(ForegroundColorSpan(color), originalLength,
                        it, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            // Set spannable text on logTextView
            logTextView?.text = spannableText

            // Set color for just the new text
            // logTextView?.setTextColor(color, originalLength, logTextView?.length())

            // Restore original color
            // if (originalColor != null) {
            //     logTextView?.setTextColor(originalColor)
            // }

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