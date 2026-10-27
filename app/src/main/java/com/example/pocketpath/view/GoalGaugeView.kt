package com.example.pocketpath.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.example.pocketpath.R
import com.example.pocketpath.util.GoalBand
import com.example.pocketpath.util.GoalProgress
import com.example.pocketpath.util.Money

// shows how the past month of spending sat against the monthly goals
// the track runs from nothing spent up to the maximum goal with the stretch
// below the minimum shaded separately so the three outcomes are easy to tell
// apart at a glance
class GoalGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress: GoalProgress? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_track)
    }

    private val belowZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(ContextCompat.getColor(context, R.color.goal_below), 38)
    }

    private val withinZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(ContextCompat.getColor(context, R.color.goal_within), 38)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.goal_marker)
        strokeWidth = dp(2f)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_value)
        textSize = sp(11f)
    }

    private val rect = RectF()

    private val trackHeight = dp(22f)
    private val labelGap = dp(6f)
    private val corner = dp(11f)

    // replaces what the summary shows and redraws
    fun setProgress(progress: GoalProgress) {
        this.progress = progress
        updateDescription()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val needed = paddingTop + trackHeight + labelGap + labelPaint.textSize + paddingBottom
        setMeasuredDimension(width, resolveSize(needed.toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val current = progress ?: return

        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val available = right - left
        if (available <= 0f) return

        val top = paddingTop.toFloat()
        val bottom = top + trackHeight

        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, corner, corner, trackPaint)

        // goals that were never set leave an empty track with no zones
        if (current.band == GoalBand.NOT_SET) {
            drawLabel(canvas, context.getString(R.string.goals_not_set), left, bottom)
            return
        }

        val minimumX = left + (current.minimumFraction * available).toFloat()

        rect.set(left, top, minimumX, bottom)
        canvas.drawRoundRect(rect, corner, corner, belowZonePaint)

        rect.set(minimumX, top, right, bottom)
        canvas.drawRoundRect(rect, corner, corner, withinZonePaint)

        fillPaint.color = ContextCompat.getColor(context, bandColour(current.band))

        val fillWidth = (current.fractionOfMaximum * available).toFloat()
        if (fillWidth > 0f) {
            rect.set(left, top, left + fillWidth.coerceAtLeast(corner * 2), bottom)
            canvas.drawRoundRect(rect, corner, corner, fillPaint)
        }

        // the marker sits where the spending reached so going over the
        // maximum still reads as pinned to the end of the track
        val markerX = (left + fillWidth).coerceIn(left, right)
        canvas.drawLine(markerX, top - dp(3f), markerX, bottom + dp(3f), markerPaint)

        drawLabel(canvas, Money.format(current.minimum), minimumX, bottom)

        val maximumLabel = Money.format(current.maximum)
        val maximumX = right - labelPaint.measureText(maximumLabel)
        drawLabel(canvas, maximumLabel, maximumX, bottom)
    }

    // draws one value under the track and keeps it inside the view
    private fun drawLabel(canvas: Canvas, text: String, x: Float, trackBottom: Float) {
        val maxX = width - paddingRight - labelPaint.measureText(text)
        val clamped = x.coerceIn(paddingLeft.toFloat(), maxX.coerceAtLeast(paddingLeft.toFloat()))
        canvas.drawText(text, clamped, trackBottom + labelGap + labelPaint.textSize, labelPaint)
    }

    private fun bandColour(band: GoalBand): Int = when (band) {
        GoalBand.BELOW_MINIMUM -> R.color.goal_below
        GoalBand.WITHIN_GOALS -> R.color.goal_within
        GoalBand.ABOVE_MAXIMUM -> R.color.goal_above
        GoalBand.NOT_SET -> R.color.chart_track
    }

    // lets a screen reader read the outcome instead of announcing nothing
    private fun updateDescription() {
        val current = progress ?: return

        contentDescription = when (current.band) {
            GoalBand.NOT_SET -> context.getString(R.string.goals_not_set)
            GoalBand.BELOW_MINIMUM -> context.getString(
                R.string.goal_summary_below, Money.format(current.spent), Money.format(current.minimum)
            )
            GoalBand.WITHIN_GOALS -> context.getString(
                R.string.goal_summary_within, Money.format(current.spent)
            )
            GoalBand.ABOVE_MAXIMUM -> context.getString(
                R.string.goal_summary_above, Money.format(current.spent), Money.format(current.maximum)
            )
        }
    }

    private fun withAlpha(colour: Int, alpha: Int): Int =
        (alpha shl 24) or (colour and 0x00FFFFFF)

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    )

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics
    )
}
