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

class PhotoViewerActivity : AppCompatActivity() {
    private lateinit var imgFullPhoto: ImageView
    private lateinit var tvPhotoError: TextView

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

        findViewById<MaterialButton>(R.id.btnClosePhoto).setOnClickListener { finish() }

        showCaption()
        showPhoto()
    }

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

    private fun showPhoto() {
        val photoUri = intent.getStringExtra(EXTRA_PHOTO_URI)

        if (photoUri.isNullOrBlank()) {
            showUnavailable()
            return
        }

        val metrics = resources.displayMetrics

        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                PhotoLoader.loadScaled(
                    context = this@PhotoViewerActivity,
                    uri = photoUri.toUri(),
                    targetWidth = metrics.widthPixels,
                    targetHeight = metrics.heightPixels
                )
            }

            if (bitmap == null) {
                showUnavailable()
            } else {
                imgFullPhoto.setImageBitmap(bitmap)
            }
        }
    }

    private fun showUnavailable() {
        imgFullPhoto.visibility = View.GONE
        tvPhotoError.visibility = View.VISIBLE
    }

    companion object {
        const val EXTRA_PHOTO_URI = "extra_photo_uri"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_EXPENSE_DATE = "extra_expense_date"
    }
}
