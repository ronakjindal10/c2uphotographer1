package com.example.c2u_photographer_1
import PhotoProcessor
import android.Manifest
import android.app.Activity
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
import androidx.documentfile.provider.DocumentFile
import java.io.FileInputStream
import java.util.LinkedList
import java.util.Queue
import java.util.Stack
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import android.widget.Toast
import de.lolhens.resticui.util.PermissionManager

// This is the main activity class of the app
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // This is the directory where the photos are getting added
    // Nikon on Redmi K20:
    // val photoDir = "/storage/emulated/0/Nikon downloads"
    // Bangalore photographer's Sony A7M3:
    // val photoDir = "/storage/emulated/10/Pictures/Canon EOS R10"
    // val photoDir = "/storage/emulated/0/DCIM/Transfer & Tagging add-on/e522445b-bb7e-468b-9c1f-b5ffd19c2947/6c00f2fc-7bd3-48cc-a7e5-4a151aaed57b/b63df105-c1ae-4051-83dd-ab8a0bd3feef"
    // val photoDir = "/storage/emulated/0/DCIM/Transfer & Tagging add-on/e522445b-bb7e-468b-9c1f-b5ffd19c2947/2cfbb0a6-3d29-43e1-a59e-9f39124d15c4"
    // Hemant Royale Camera's Sony Camera below:
    // val photoDir = "/storage/emulated/0/DCIM/Transfer & Tagging add-on/e522445b-bb7e-468b-9c1f-b5ffd19c2947/a30304c5-5f08-4670-a8a9-5de328ce82d5/02d9ec51-f471-4afb-8476-782634a762f6"
    // val photoDir = "/storage/emulated/10/DCIM/Transfer & Tagging add-on/be86c1fd-3dec-4628-80de-5dd2b088f692/3a760bb9-876b-4836-95e7-cba9f2c6e2d3/e5528206-d021-4fdf-9ad6-b9efd87147d2"
    val photoDir = "/storage/emulated/0/Pictures/Canon EOS RP"

    // This is the directory where the watermark is
    val watermarkDir = "/storage/emulated/10/Watermark/watermark.png"

    // This is the URL of the API to upload photos
    val apiUrl = "https://c2u-api.onrender.com/upload-photo"

    // This is the request code for selecting a photo from the gallery
//    val pickImage = 100



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

    // Define a queue for file uploads
    val photoQueue: Queue<String> = LinkedList<String>()
    val newPhotosQueue: Queue<String> = LinkedList<String>()
    val failedUploadsQueue: Queue<String> = LinkedList<String>()

    // Handle uploads sequentially but offload them from the main thread using a single-threaded executor
    private val uploadExecutor = Executors.newSingleThreadExecutor()
    val isRunning = AtomicBoolean(true)

//    private lateinit var photoProcessor: PhotoProcessor

    // This is the method that is called when the activity is created
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        logTextView = binding.logTextView

        if (PermissionManager.instance.hasStoragePermission(applicationContext, write = true)) {
            startFileObserver()
            startPhotoService()
        } else {
            PermissionManager.instance.requestStoragePermission(this, write = true)
                .thenApply { granted ->
                    if (granted) {
                        startFileObserver()
                        startPhotoService()
                    } else {
                        Toast.makeText(this, "Allow permission for storage access!", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionManager.instance.onRequestPermissionsResult(requestCode)
    }

    // When you want to start the service
    fun startPhotoService() {
        val serviceIntent = Intent(this, PhotoProcessingService::class.java)
        startForegroundService(serviceIntent)  // Start the service
    }

    // When you want to stop the service
    fun stopPhotoService() {
        val serviceIntent = Intent(this, PhotoProcessingService::class.java)
        stopService(serviceIntent)  // Stop the service
    }


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
                                "Inside onEvent, detected new file, adding it to uploadQueue: ${
                                    File(
                                        filePath
                                    ).path
                                }", Color.GRAY
                            )
                            PhotoProcessor.instance.addPhotoToQueue(filePath)
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

    // This is the method that logs a message to the log text view and scrolls it to the bottom
    fun logMessage(message: String, color: Int = Color.GRAY) {
        runOnUiThread {
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
        stopPhotoService()
    }
}