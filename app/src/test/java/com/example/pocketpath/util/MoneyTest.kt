package com.example.pocketpath.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test
    fun `formats a plain amount with two decimals`() {
        assertEquals("R450.00", Money.format(450.0))
    }

    @Test
    fun `formats zero`() {
        assertEquals("R0.00", Money.format(0.0))
    }

    @Test
    fun `groups thousands`() {
        assertEquals("R1,250.00", Money.format(1250.0))
    }

    @Test
    fun `groups millions`() {
        assertEquals("R1,234,567.89", Money.format(1234567.89))
    }

    @Test
    fun `rounds to the nearest cent`() {
        assertEquals("R1,234.57", Money.format(1234.567))
    }

    @Test
    fun `pads a single decimal place`() {
        assertEquals("R12.50", Money.format(12.5))
    }

    @Test
    fun `shows the share a category makes up of the period total`() {
        assertEquals("R500.00 (25.0%)", Money.formatWithShare(amount = 500.0, total = 2000.0))
    }

    @Test
    fun `a category that is the whole period total shows one hundred percent`() {
        assertEquals("R800.00 (100.0%)", Money.formatWithShare(amount = 800.0, total = 800.0))
    }

    @Test
    fun `share is rounded to one decimal place`() {
        assertEquals("R333.33 (33.3%)", Money.formatWithShare(amount = 333.33, total = 1000.0))
    }

    @Test
    fun `a zero period total shows the amount without a share`() {
        assertEquals("R0.00", Money.formatWithShare(amount = 0.0, total = 0.0))
    }
}
