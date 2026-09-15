package com.example.pocketpath.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapSamplingTest {
    @Test
    fun `a photograph already smaller than the target is not shrunk`() {
        val sampleSize = BitmapSampling.sampleSizeFor(
            sourceWidth = 400,
            sourceHeight = 300,
            targetWidth = 600,
            targetHeight = 400
        )

        assertEquals(1, sampleSize)
    }

    @Test
    fun `a photograph the same size as the target is not shrunk`() {
        val sampleSize = BitmapSampling.sampleSizeFor(
            sourceWidth = 600,
            sourceHeight = 400,
            targetWidth = 600,
            targetHeight = 400
        )

        assertEquals(1, sampleSize)
    }

    @Test
    fun `a photograph twice the target is halved`() {
        val sampleSize = BitmapSampling.sampleSizeFor(
            sourceWidth = 1200,
            sourceHeight = 800,
            targetWidth = 600,
            targetHeight = 400
        )

        assertEquals(2, sampleSize)
    }

    @Test
    fun `a full size camera photograph is shrunk by a large factor`() {
        val sampleSize = BitmapSampling.sampleSizeFor(
            sourceWidth = 4032,
            sourceHeight = 3024,
            targetWidth = 600,
            targetHeight = 400
        )

        assertEquals(4, sampleSize)
    }

    @Test
    fun `the result is always a power of two`() {
        val sampleSize = BitmapSampling.sampleSizeFor(
            sourceWidth = 4000,
            sourceHeight = 3000,
            targetWidth = 100,
            targetHeight = 100
        )

        assertEquals(0, sampleSize and (sampleSize - 1))
    }

    @Test
    fun `the shrunk photograph still covers the target`() {
        val sourceWidth = 4032
        val sourceHeight = 3024
        val targetWidth = 600
        val targetHeight = 400

        val sampleSize = BitmapSampling.sampleSizeFor(
            sourceWidth, sourceHeight, targetWidth, targetHeight
        )

        assertEquals(true, sourceWidth / sampleSize >= targetWidth)
        assertEquals(true, sourceHeight / sampleSize >= targetHeight)
    }

    @Test
    fun `an unreadable size falls back to decoding at full size`() {
        val sampleSize = BitmapSampling.sampleSizeFor(
            sourceWidth = 0,
            sourceHeight = 0,
            targetWidth = 600,
            targetHeight = 400
        )

        assertEquals(1, sampleSize)
    }

    @Test
    fun `a zero target does not cause a division by zero`() {
        val sampleSize = BitmapSampling.sampleSizeFor(
            sourceWidth = 4032,
            sourceHeight = 3024,
            targetWidth = 0,
            targetHeight = 0
        )

        assertEquals(true, sampleSize >= 1)
    }
}
