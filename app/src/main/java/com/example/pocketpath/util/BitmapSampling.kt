package com.example.pocketpath.util

// works out how much to shrink a photo while loading it
object BitmapSampling {
    // returns the shrink factor as a power of two
    fun sampleSizeFor(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        // size is unknown so load the photo at full size
        if (sourceWidth <= 0 || sourceHeight <= 0) return 1

        val safeTargetWidth = targetWidth.coerceAtLeast(1)
        val safeTargetHeight = targetHeight.coerceAtLeast(1)

        var sampleSize = 1

        // keep doubling while the photo still covers the target
        while (
            sourceHeight / (sampleSize * 2) >= safeTargetHeight &&
            sourceWidth / (sampleSize * 2) >= safeTargetWidth
        ) {
            sampleSize *= 2
        }

        return sampleSize
    }
}
