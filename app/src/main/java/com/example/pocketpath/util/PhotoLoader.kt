package com.example.pocketpath.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log

// loads expense photos at a smaller size
object PhotoLoader {
    // tag used for log messages
    private const val TAG = "PocketPathPhotoLoader"

    // reads the photo scaled down to fit the target size
    fun loadScaled(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        return try {
            // first pass reads only the size of the photo
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

            context.contentResolver.openInputStream(uri).use { stream ->
                if (stream == null) return null
                BitmapFactory.decodeStream(stream, null, bounds)
            }

            // the size could not be read so give up
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // work out how much to shrink the photo
            val options = BitmapFactory.Options().apply {
                inSampleSize = BitmapSampling.sampleSizeFor(
                    bounds.outWidth,
                    bounds.outHeight,
                    targetWidth.coerceAtLeast(1),
                    targetHeight.coerceAtLeast(1)
                )
            }

            // second pass loads the shrunk photo
            context.contentResolver.openInputStream(uri).use { stream ->
                if (stream == null) return null
                BitmapFactory.decodeStream(stream, null, options)
            }
        // the photo file is missing or cannot be read
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to read photograph at $uri", exception)
            null
        }
    }
}
