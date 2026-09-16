package com.example.pocketpath

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.pocketpath.util.PhotoLoader
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// shows one expense photo full screen
class PhotoViewerActivity : AppCompatActivity() {
    // the views on this screen
    private lateinit var imgFullPhoto: ImageView
    private lateinit var tvPhotoError: TextView

    // sets the screen up when it opens
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_viewer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        imgFullPhoto = findViewById(R.id.imgFullPhoto)
        tvPhotoError = findViewById(R.id.tvPhotoError)

        // the close button goes back to the history list
        findViewById<MaterialButton>(R.id.btnClosePhoto).setOnClickListener { finish() }

        showCaption()
        showPhoto()
    }

    // shows the description and date above the photo
    private fun showCaption() {
        val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
        val expenseDate = intent.getLongExtra(EXTRA_EXPENSE_DATE, 0L)

        val caption = if (expenseDate > 0L) {
            val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(Date(expenseDate))
            listOf(description, formattedDate)
                .filter { it.isNotBlank() }
                .joinToString(separator = "  ·  ")
        } else {
            description
        }

        findViewById<TextView>(R.id.tvPhotoCaption).text = caption
    }

    // loads the photo scaled to fit the screen
    private fun showPhoto() {
        val photoUri = intent.getStringExtra(EXTRA_PHOTO_URI)

        // there is no photo to show
        if (photoUri.isNullOrBlank()) {
            showUnavailable()
            return
        }

        val metrics = resources.displayMetrics

        // load the photo off the main thread
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                PhotoLoader.loadScaled(
                    context = this@PhotoViewerActivity,
                    uri = photoUri.toUri(),
                    targetWidth = metrics.widthPixels,
                    targetHeight = metrics.heightPixels
                )
            }

            // the photo could not be loaded
            if (bitmap == null) {
                showUnavailable()
            } else {
                imgFullPhoto.setImageBitmap(bitmap)
            }
        }
    }

    // shows a message instead of an empty black screen
    private fun showUnavailable() {
        imgFullPhoto.visibility = View.GONE
        tvPhotoError.visibility = View.VISIBLE
    }

    // names of the values passed in from the history screen
    companion object {
        const val EXTRA_PHOTO_URI = "extra_photo_uri"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_EXPENSE_DATE = "extra_expense_date"
    }
}
