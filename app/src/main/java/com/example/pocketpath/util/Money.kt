package com.example.pocketpath.util

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

object Money {
    private const val CURRENCY_SYMBOL = "R"

    fun format(amount: Double): String = buildFormat().format(amount)

    fun formatWithShare(amount: Double, total: Double): String {
        if (total <= 0.0) return format(amount)

        val share = (amount / total) * 100
        return "${format(amount)} (${percentFormat().format(share)}%)"
    }

    private fun buildFormat(): DecimalFormat {
        val format = NumberFormat.getCurrencyInstance(Locale.UK) as DecimalFormat

        format.decimalFormatSymbols = format.decimalFormatSymbols.apply {
            currencySymbol = CURRENCY_SYMBOL
        }

        return format
    }

    private fun percentFormat(): NumberFormat =
        NumberFormat.getNumberInstance(Locale.UK).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
}
