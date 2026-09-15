package com.example.pocketpath.util

object BitmapSampling {
    fun sampleSizeFor(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0) return 1

        val safeTargetWidth = targetWidth.coerceAtLeast(1)
        val safeTargetHeight = targetHeight.coerceAtLeast(1)

        var sampleSize = 1

        while (
            sourceHeight / (sampleSize * 2) >= safeTargetHeight &&
            sourceWidth / (sampleSize * 2) >= safeTargetWidth
        ) {
            sampleSize *= 2
        }

        return sampleSize
    }
}
