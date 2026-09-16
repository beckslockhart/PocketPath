package com.example.pocketpath.util

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

// formats money amounts for the screen
object Money {
    // the rand symbol shown before every amount
    private const val CURRENCY_SYMBOL = "R"

    // formats an amount as rands
    fun format(amount: Double): String = buildFormat().format(amount)

    // formats an amount plus the percent it makes of the total
    fun formatWithShare(amount: Double, total: Double): String {
        // a zero total has no percent so only show the amount
        if (total <= 0.0) return format(amount)

        val share = (amount / total) * 100
        return "${format(amount)} (${percentFormat().format(share)}%)"
    }

    // builds a number format that uses the rand symbol
    private fun buildFormat(): DecimalFormat {
        val format = NumberFormat.getCurrencyInstance(Locale.UK) as DecimalFormat

        format.decimalFormatSymbols = format.decimalFormatSymbols.apply {
            currencySymbol = CURRENCY_SYMBOL
        }

        return format
    }

    // number format with one decimal place for percents
    private fun percentFormat(): NumberFormat =
        NumberFormat.getNumberInstance(Locale.UK).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
}
