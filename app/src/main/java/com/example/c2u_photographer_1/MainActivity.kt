package com.example.c2u_photographer_1
import PhotoProcessor
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
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
import android.view.ViewGroup
import android.widget.EditText
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
    private lateinit var emailEditText: EditText
    private lateinit var sharedPreferences: SharedPreferences

    // This is the directory where the photos are getting added
    var photoDir = "/storage/emulated/0/DCIM/Transfer & Tagging add-on/"
    val defaultPhotoDir = "/storage/emulated/0/DCIM/Transfer & Tagging add-on/"

    // This is the file observer object that monitors the photo directory for changes
    var fileObserver: FileObserver? = null
    private val allFileObservers = mutableListOf<FileObserver>()

    // This is the text view object that displays the logs to the user
    var logTextView: TextView? = null

    // We'll monitor the following events:
    val newFolderEvent = 1073742080
    val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.CREATE or newFolderEvent

    // Define a queue for file uploads
    val photoQueue: Queue<String> = LinkedList<String>()
    val newPhotosQueue: Queue<String> = LinkedList<String>()
    val failedUploadsQueue: Queue<String> = LinkedList<String>()

    // Handle uploads sequentially but offload them from the main thread using a single-threaded executor
    private val uploadExecutor = Executors.newSingleThreadExecutor()
    val isRunning = AtomicBoolean(true)

    // Add a constant for the watermark URI key
    companion object {
        private const val PREFS_NAME = "PhotoAppPrefs"
        private const val KEY_WATERMARK_URI = "watermarkUri"
        private const val REQUEST_CODE_PICK_WATERMARK = 2
        private const val EMAIL = ""
    }

    // This is the method that is called when the activity is created
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize PhotoProcessor with the context
        PhotoProcessor.initialize(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        logTextView = binding.logTextView
        PhotoProcessor.logTextView = findViewById(R.id.photoProcessorLogTextView)

        // Initialize sharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Load photoDir from SharedPreferences
        photoDir = sharedPreferences.getString("photoDir", defaultPhotoDir) ?: defaultPhotoDir
        val watermarkUriString = sharedPreferences.getString(KEY_WATERMARK_URI, null)
        watermarkUriString?.let {
            val watermarkUri = Uri.parse(it)
            PhotoProcessor.instance.setWatermarkUri(watermarkUri)
        }

        emailEditText = findViewById(R.id.emailEditText)
        emailEditText.setText(sharedPreferences.getString("email", ""))

        val photoDirEditText: EditText = findViewById(R.id.photoDirEditText)
        photoDirEditText.setText(photoDir)

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

    override fun onResume() {
        super.onResume()
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                updateUploadInfo()
                handler.postDelayed(this, 1000) // Update every second
            }
        }
        handler.post(runnable)
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
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    // Start watching this directory
                    startWatchingDirectory(file)
                }
            }
            // Create and start a file observer for this directory
            fileObserver = object : FileObserver(directory.path, mask) {
                override fun onEvent(event: Int, path: String?) {
                    if ((event == FileObserver.CLOSE_WRITE || event == FileObserver.MOVED_TO || event == newFolderEvent) && path != null && isFileStable(
                            File("${directory.path}/$path")
                        )){
                        val filePath = "${directory.path}/$path"
                        // Check if it's a directory or a file
                        if (File(filePath).isDirectory) {
                            // If it's a directory, start watching it
                            logMessage(
                                "Detected new folder: ${
                                    File(   
                                        filePath
                                    ).path
                                }", Color.GRAY
                            )
                            startWatchingDirectory(File(filePath))
                        } else {
                            // If it's a file, process it
                            logMessage(
                                "Detected new file: ${
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
    fun isFileStable(file: File, delayMillis: Long = 500): Boolean {
        try {
            val initialSize = file.length()
            // Wait for a specified delay
            Thread.sleep(delayMillis)

            val finalSize = file.length()
            // return true if the final file size is greater than 0 and it's equal to initial size
            if (finalSize > 0) {
                return initialSize == finalSize
            }
            else {
                return false
            }
        } catch (e: Exception) {
            logMessage("Exception while checking file stability: ${e.message}", Color.YELLOW)
            return false
        }
    }

     private fun updateUploadInfo() {
         val info = PhotoProcessor.instance.getUploadInfo()
         // Assuming you have a TextView with id upload_info in your layout
         findViewById<TextView>(R.id.upload_info).text = info
     }

    // This is the method that stops the file observer
    fun stopFileObserver() {
        try {
            // Stop watching for events in the photo directory
            allFileObservers.forEach { it.stopWatching() }
            allFileObservers.clear()

            // Log a success message
            logMessage("File observer stopped successfully")
        } catch (e: Exception) {
            // Log an exception message
            logMessage("Exception while stopping file observer: ${e.message}")
        }
    }

    fun onSetFolderClicked(view: View) {
        val photoDirEditText: EditText = findViewById(R.id.photoDirEditText)
        val directoryPathErrorTextView = findViewById<TextView>(R.id.directoryPathErrorTextView)
        photoDir = photoDirEditText.text.toString()
        // Save the email when the set folder button is clicked
        val email = emailEditText.text.toString()

        if (isValidDirectory(photoDir)) {
            // Save to SharedPreferences
            val sharedPrefs = getSharedPreferences("PhotoAppPrefs", MODE_PRIVATE)
            with(sharedPrefs.edit()) {
                putString("email", email)
                putString("photoDir", photoDir)
                apply()
            }
            restartFileObserver()
            directoryPathErrorTextView.visibility = View.GONE
            // Set the email in PhotoProcessor
            PhotoProcessor.instance.setEmail(email)
        } else {
            directoryPathErrorTextView.text = "Invalid directory path"
            directoryPathErrorTextView.visibility = View.VISIBLE
        }
    }

    fun isValidDirectory(path: String): Boolean {
        val directory = File(path)
        return directory.exists() && directory.isDirectory
    }

    private fun restartFileObserver() {
        // Stop the current file observer if it exists
        stopFileObserver()

        // Start a new file observer with the updated photoDir
        startFileObserver()
    }

    // Method to pick a watermark image
    fun onAddWatermarkClicked(view: View) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_CODE_PICK_WATERMARK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_WATERMARK && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                PhotoProcessor.instance.setWatermarkUri(uri)
                sharedPreferences.edit().putString(KEY_WATERMARK_URI, uri.toString()).apply()
            }
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