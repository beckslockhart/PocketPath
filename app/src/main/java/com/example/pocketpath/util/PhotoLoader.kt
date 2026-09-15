package com.example.pocketpath.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log

object PhotoLoader {
    private const val TAG = "PocketPathPhotoLoader"

    fun loadScaled(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

            context.contentResolver.openInputStream(uri).use { stream ->
                if (stream == null) return null
                BitmapFactory.decodeStream(stream, null, bounds)
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = BitmapSampling.sampleSizeFor(
                    bounds.outWidth,
                    bounds.outHeight,
                    targetWidth.coerceAtLeast(1),
                    targetHeight.coerceAtLeast(1)
                )
            }

            context.contentResolver.openInputStream(uri).use { stream ->
                if (stream == null) return null
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to read photograph at $uri", exception)
            null
        }
    }
}
