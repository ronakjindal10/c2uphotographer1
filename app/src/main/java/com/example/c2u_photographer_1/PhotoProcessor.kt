import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Log
import android.widget.TextView
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.min

class PhotoProcessor private constructor() {

    private val newPhotosQueue: Queue<String> = LinkedList<String>()
    private val failedUploadsQueue: Queue<String> = LinkedList<String>()

    // Executor for processing photos
    private val uploadExecutor = Executors.newSingleThreadExecutor()

    // Lock object for synchronization
    private val lock = Any()

    // Atomic boolean to control the processing thread
    private val isRunning = AtomicBoolean(false)

    // This is the directory where the watermark is
    val watermarkDir = "/storage/emulated/10/Watermark/watermark.png"

    // This is the bitmap object for the watermark image
    var watermarkBitmap: Bitmap? = null

    // This is the URL of the API to upload photos
    val apiUrl = "https://c2u-api.onrender.com/upload-photo"

    // Start processing photos in a background thread
    fun startProcessing() {
        // Load the watermark image from the watermark directory
        loadWatermark()
        isRunning.set(true)
        Thread {
            while (isRunning.get()) {
                var photoPath: String? = null
                synchronized(lock) {
                    photoPath = getNextPhotoToUpload() // Get the next photo to upload
                }

                if (photoPath != null) {
                    try {
                        logMessage("Processing: $photoPath", Log.INFO)
                        logMessage("New photos queue: ${newPhotosQueue}", Log.INFO)
                        logMessage("Failed uploads queue: ${failedUploadsQueue}", Log.INFO)
                        val success = processAndUploadImageFile(photoPath!!)

                        // Only modify queues after processing is complete
                        synchronized(lock) {
                            if (success) {
                                newPhotosQueue.remove(photoPath)
                                failedUploadsQueue.remove(photoPath)
                                logMessage("Successfully uploaded $photoPath", Log.INFO)
                            } else {
                                newPhotosQueue.remove(photoPath)
                                failedUploadsQueue.remove(photoPath)
                                failedUploadsQueue.add(photoPath)
                                logMessage("Failed to upload $photoPath, moved to failed uploads queue", Log.WARN)
                            }
                        }
                    } catch (e: Exception) {
                        logMessage("Error processing $photoPath: ${e.localizedMessage}", Log.ERROR)
                    }
                } else {
                    logMessage("No photos to process at the moment", Log.INFO)
                }

                try {
                    Thread.sleep(5000) // Sleep between checks. Adjust as needed.
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    logMessage("Photo processing thread interrupted", Log.ERROR)
                }
            }
        }.start()
    }

    fun stopProcessing() {
        isRunning.set(false)
        uploadExecutor.shutdownNow()
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
        } catch (e: java.lang.Exception) {
            // Log an exception message
            logMessage("Exception while loading watermark image: ${e.message}", Color.YELLOW)
        }
    }

    private fun getNextPhotoToUpload(): String? {
        synchronized(lock) {
            return when {
                newPhotosQueue.isNotEmpty() -> newPhotosQueue.peek()
                failedUploadsQueue.isNotEmpty() -> failedUploadsQueue.peek()
                else -> null
            }
        }
    }

    private fun processAndUploadImageFile(filePath: String): Boolean {
        try {
            // Create a file object for the image file
            val imageFile = File(filePath)

            // Check if the image file exists and is readable
            if (imageFile.exists() && imageFile.canRead()) {

                // Log a message that the image file is being processed
                logMessage("Processing image file: ${imageFile.name}", Log.INFO)

                // Decode the image file into a bitmap object
                val originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)

                // Resize and compress the bitmap object
                val resizedBitmap = resizeAndCompressBitmap(originalBitmap, filePath)

                // Add a watermark to the bitmap object
                val watermarkedBitmap = addWatermarkToBitmap(resizedBitmap)

                // Convert the bitmap object into a byte array
                val byteArray = bitmapToByteArray(watermarkedBitmap)

                // Upload the byte array to the API server
                return uploadByteArrayToApi(byteArray, imageFile.name)
            } else {
                // Log an error message
                logMessage("Image file does not exist or is not readable", Color.RED)
                return false
            }
        } catch (e: java.lang.Exception) {
            // Log an exception message
            logMessage("Exception while processing and uploading image file: ${e.message}", Color.YELLOW)
            return false
        }
    }

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

    fun uploadByteArrayToApi(byteArray: ByteArray, fileName: String): Boolean {
        // Construct the request body and the request
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("photo", fileName, byteArray.toRequestBody("image/jpeg".toMediaType()))
            .build()

        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody)
            .build()

        // Create an OK HTTP client object to execute the request
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return try {
            // Execute the request and get the response
            val response = client.newCall(request).execute()

            // Check if the response is successful and log accordingly
            if (response.isSuccessful) {
                // Log success and return true
                val responseBody = response.body?.string()
                responseBody?.let {
                    val jsonObject = JSONObject(it)
                    val message = jsonObject.getString("message")
                    logMessage("Upload successful of $fileName: $message", Color.GREEN)
                }
                true
            } else {
                // Log failure and return false
                logMessage("Upload failed for file $fileName: ${response.code} ${response.message}", Color.RED)
                false
            }
        } catch (e: java.lang.Exception) {
            // Log exception and return false
            logMessage("Exception while uploading file $fileName: ${e.message}", Color.RED)
            false
        }
    }

    private fun logMessage(message: String, type: Int) {
        // You can replace this with any logging mechanism you're using
        when (type) {
            Log.INFO -> Log.i("PhotoProcessor", message)
            Log.WARN -> Log.w("PhotoProcessor", message)
            Log.ERROR -> Log.e("PhotoProcessor", message)
            else -> Log.d("PhotoProcessor", message)
        }
    }

    fun addPhotoToQueue(newPhoto: String) {
        synchronized(lock) {
            newPhotosQueue.add(newPhoto)
            logMessage("New photo added to queue: $newPhoto", Log.INFO)
        }
    }

    companion object {
        // The single instance of PhotoProcessor
        val instance: PhotoProcessor by lazy { PhotoProcessor() }
    }

    // Implement other necessary methods and cleanup based on your app's requirements...
}