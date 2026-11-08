package com.example.btproject2.ui.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import android.media.ExifInterface
import com.example.btproject2.R
import com.example.btproject2.utils.DocumentHelper
import com.example.btproject2.utils.KinTraceAlert
import com.example.btproject2.views.KinTraceCropImageView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.io.InputStream
import java.util.Locale

class PhotoCropperActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ENTITY_ID = "EXTRA_ENTITY_ID"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_CURRENT_BASE64 = "EXTRA_CURRENT_BASE64"
        const val EXTRA_ALLOW_REMOVE = "EXTRA_ALLOW_REMOVE"

        const val RESULT_PHOTO_URI = "RESULT_PHOTO_URI"
        const val RESULT_PHOTO_BASE64 = "RESULT_PHOTO_BASE64"
        const val RESULT_PHOTO_REMOVED = "RESULT_PHOTO_REMOVED"

        fun createIntent(
            context: Context,
            entityId: String,
            title: String = "Frame & Crop Picture",
            currentPhotoBase64: String? = null,
            allowRemove: Boolean = true
        ): Intent {
            return Intent(context, PhotoCropperActivity::class.java).apply {
                putExtra(EXTRA_ENTITY_ID, entityId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CURRENT_BASE64, currentPhotoBase64)
                putExtra(EXTRA_ALLOW_REMOVE, allowRemove)
            }
        }
    }

    private lateinit var cropImageView: KinTraceCropImageView
    private lateinit var seekCropperZoom: SeekBar
    private lateinit var tvCropperZoomValue: TextView
    private lateinit var tvCropperHeaderTitle: TextView
    private lateinit var btnCropperCancel: TextView
    private lateinit var btnCropperSaveTop: TextView
    private lateinit var btnCropperRotate: Button
    private lateinit var btnCropperChangeSource: Button
    private lateinit var btnCropperReset: Button
    private lateinit var btnCropperApply: Button

    private var entityId: String = "entity"
    private var screenTitle: String = "Frame & Crop Picture"
    private var currentPhotoBase64: String? = null
    private var allowRemove: Boolean = true

    private var tempCameraFile: File? = null
    private var tempCameraUri: Uri? = null
    private var isSourcePickerShown = false

    // Camera Launcher
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraFile != null && tempCameraFile!!.exists()) {
            loadBitmapFromFile(tempCameraFile!!)
        } else {
            if (cropImageView.cropResultBitmap() == null) {
                // If user cancelled camera and no existing image, prompt picker again
                showSourcePickerSheet()
            }
        }
    }

    // Gallery Launcher
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            loadBitmapFromUri(uri)
        } else {
            if (cropImageView.cropResultBitmap() == null) {
                showSourcePickerSheet()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_cropper)

        entityId = intent.getStringExtra(EXTRA_ENTITY_ID) ?: "entity_${System.currentTimeMillis()}"
        screenTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Frame & Crop Picture"
        currentPhotoBase64 = intent.getStringExtra(EXTRA_CURRENT_BASE64)
        allowRemove = intent.getBooleanExtra(EXTRA_ALLOW_REMOVE, true)

        initViews()

        // If existing base64 is present, load it initially
        if (!currentPhotoBase64.isNullOrBlank()) {
            val existingBitmap = DocumentHelper.decodeBase64Bitmap(currentPhotoBase64!!, 600)
            if (existingBitmap != null) {
                cropImageView.setBitmap(existingBitmap)
            } else {
                showSourcePickerSheet()
            }
        } else {
            showSourcePickerSheet()
        }
    }

    private fun initViews() {
        cropImageView = findViewById(R.id.cropImageView)
        seekCropperZoom = findViewById(R.id.seekCropperZoom)
        tvCropperZoomValue = findViewById(R.id.tvCropperZoomValue)
        tvCropperHeaderTitle = findViewById(R.id.tvCropperHeaderTitle)
        btnCropperCancel = findViewById(R.id.btnCropperCancel)
        btnCropperSaveTop = findViewById(R.id.btnCropperSaveTop)
        btnCropperRotate = findViewById(R.id.btnCropperRotate)
        btnCropperChangeSource = findViewById(R.id.btnCropperChangeSource)
        btnCropperReset = findViewById(R.id.btnCropperReset)
        btnCropperApply = findViewById(R.id.btnCropperApply)

        tvCropperHeaderTitle.text = screenTitle

        btnCropperCancel.setOnClickListener { finish() }
        btnCropperSaveTop.setOnClickListener { performSaveCrop() }
        btnCropperApply.setOnClickListener { performSaveCrop() }

        btnCropperRotate.setOnClickListener { cropImageView.rotate90() }
        btnCropperReset.setOnClickListener { cropImageView.resetTransform() }
        btnCropperChangeSource.setOnClickListener { showSourcePickerSheet() }
        cropImageView.onEmptyStateClickedListener = { showSourcePickerSheet() }

        seekCropperZoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val zoom = 1.0f + (progress / 100f)
                    cropImageView.setZoom(zoom)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        cropImageView.onZoomChangedListener = { zoom ->
            val progress = ((zoom - 1.0f) * 100f).toInt().coerceIn(0, 250)
            if (seekCropperZoom.progress != progress) {
                seekCropperZoom.progress = progress
            }
            tvCropperZoomValue.text = "${String.format(Locale.US, "%.1f", zoom)}x"
        }
    }

    private fun showSourcePickerSheet() {
        if (isFinishing || isDestroyed) return

        val dialog = BottomSheetDialog(this)
        val sheetView = LayoutInflater.from(this).inflate(R.layout.sheet_photo_source_selector, null)
        dialog.setContentView(sheetView)

        val btnCamera = sheetView.findViewById<View>(R.id.btnOptionCamera)
        val btnGallery = sheetView.findViewById<View>(R.id.btnOptionGallery)
        val btnRemove = sheetView.findViewById<View>(R.id.btnOptionRemovePhoto)
        val btnCancel = sheetView.findViewById<View>(R.id.btnSourceCancel)

        if (!allowRemove) {
            btnRemove.visibility = View.GONE
        }

        btnCamera.setOnClickListener {
            dialog.dismiss()
            launchCameraCapture()
        }

        btnGallery.setOnClickListener {
            dialog.dismiss()
            galleryLauncher.launch("image/*")
        }

        btnRemove.setOnClickListener {
            dialog.dismiss()
            val resultIntent = Intent().apply {
                putExtra(RESULT_PHOTO_REMOVED, true)
                putExtra(RESULT_PHOTO_URI, "")
                putExtra(RESULT_PHOTO_BASE64, "")
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnCancelListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun launchCameraCapture() {
        try {
            val cacheDir = File(cacheDir, "camera_captures").apply { if (!exists()) mkdirs() }
            tempCameraFile = File(cacheDir, "cap_${System.currentTimeMillis()}.jpg")
            tempCameraUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                tempCameraFile!!
            )
            cameraLauncher.launch(tempCameraUri!!)
        } catch (e: Exception) {
            e.printStackTrace()
            KinTraceAlert.show(this, "Could not launch camera: ${e.message}")
        }
    }

    private fun loadBitmapFromFile(file: File) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            var sampleSize = 1
            val maxDimension = 1600
            while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return

            // Fix orientation from camera EXIF
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }

            val orientedBmp = if (!matrix.isIdentity) {
                Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
            } else {
                rawBmp
            }

            cropImageView.setBitmap(orientedBmp)
        } catch (e: Exception) {
            e.printStackTrace()
            KinTraceAlert.show(this, "Failed to load captured photo.")
        }
    }

    private fun loadBitmapFromUri(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, bounds)
            inputStream?.close()

            var sampleSize = 1
            val maxDimension = 1600
            while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeStream = contentResolver.openInputStream(uri)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBmp = BitmapFactory.decodeStream(decodeStream, null, decodeOpts)
            decodeStream?.close()

            if (rawBmp == null) {
                KinTraceAlert.show(this, "Could not open selected image.")
                return
            }

            // Attempt EXIF read from stream or file descriptor if possible
            var orientedBmp = rawBmp
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    val matrix = Matrix()
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    }
                    if (!matrix.isIdentity) {
                        orientedBmp = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
                    }
                }
            } catch (_: Exception) {}

            cropImageView.setBitmap(orientedBmp)
        } catch (e: Exception) {
            e.printStackTrace()
            KinTraceAlert.show(this, "Failed to process photo.")
        }
    }

    private fun performSaveCrop() {
        val croppedBitmap = cropImageView.cropResultBitmap(outputSize = 256)
        if (croppedBitmap == null) {
            KinTraceAlert.show(this, "Please select or capture a photo first.")
            showSourcePickerSheet()
            return
        }

        val result = DocumentHelper.saveCroppedBitmapToInternal(this, croppedBitmap, entityId)
        if (result == null) {
            KinTraceAlert.show(this, "Failed to save cropped photo.")
            return
        }

        val (savedPath, base64) = result
        val resultIntent = Intent().apply {
            putExtra(RESULT_PHOTO_URI, savedPath)
            putExtra(RESULT_PHOTO_BASE64, base64)
            putExtra(RESULT_PHOTO_REMOVED, false)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
