package com.example.pocketpath.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.example.pocketpath.R
import com.example.pocketpath.util.PathScore

// draws the path score as a ring that fills up to the score with the number
// in the middle, used large on the path progress screen and small on the
// dashboard so every size is worked out from the view's own width
class ScoreRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var score = 0

    // the score currently drawn which moves towards the real score
    private var shownScore = 0f
    private var animator: ValueAnimator? = null

    // false until the score has been loaded so the ring shows a dash
    private var hasScore = false

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.score_ring_track)
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.path_gold)
    }

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = ContextCompat.getColor(context, R.color.white)
    }

    private val outOfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.score_ring_label)
    }

    private val arcBounds = RectF()

    // changes the colours so the ring can sit on a dark or a light card
    fun setColours(@ColorInt ring: Int, @ColorInt track: Int, @ColorInt number: Int, @ColorInt label: Int) {
        ringPaint.color = ring
        trackPaint.color = track
        numberPaint.color = number
        outOfPaint.color = label
        invalidate()
    }

    // shows a new score and sweeps the ring round to it
    fun setScore(newScore: Int, animate: Boolean) {
        val target = newScore.coerceIn(0, PathScore.MAX_SCORE)
        val wasEmpty = !hasScore

        score = target
        hasScore = true
        contentDescription = context.getString(R.string.path_score_description, target)

        animator?.cancel()

        if (!animate) {
            shownScore = target.toFloat()
            invalidate()
            return
        }

        animator = ValueAnimator.ofFloat(if (wasEmpty) 0f else shownScore, target.toFloat()).apply {
            duration = ANIMATION_MILLIS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                shownScore = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wanted = dp(DEFAULT_SIZE_DP).toInt()
        val width = resolveSize(wanted, widthMeasureSpec)
        val height = resolveSize(wanted, heightMeasureSpec)
        val size = minOf(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = minOf(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        if (size <= 0f) return

        val stroke = size * STROKE_SHARE
        trackPaint.strokeWidth = stroke
        ringPaint.strokeWidth = stroke

        val left = paddingLeft + (width - paddingLeft - paddingRight - size) / 2f
        val top = paddingTop + (height - paddingTop - paddingBottom - size) / 2f
        arcBounds.set(left + stroke / 2f, top + stroke / 2f, left + size - stroke / 2f, top + size - stroke / 2f)

        canvas.drawOval(arcBounds, trackPaint)

        val sweep = 360f * shownScore / PathScore.MAX_SCORE
        if (sweep > 0f) {
            canvas.drawArc(arcBounds, START_ANGLE, sweep, false, ringPaint)
        }

        val centreX = left + size / 2f
        val centreY = top + size / 2f

        // a small ring has no room for the out of 100 label
        val showOutOf = size >= dp(OUT_OF_MIN_SIZE_DP)

        numberPaint.textSize = size * if (showOutOf) NUMBER_SHARE else SMALL_NUMBER_SHARE
        outOfPaint.textSize = size * OUT_OF_SHARE

        val numberText = if (hasScore) shownScore.toInt().toString() else "–"

        // centres the number on its own height rather than its baseline
        val numberBaseline = if (showOutOf) {
            centreY + numberPaint.textSize * 0.25f
        } else {
            centreY - (numberPaint.descent() + numberPaint.ascent()) / 2f
        }

        canvas.drawText(numberText, centreX, numberBaseline, numberPaint)

        if (showOutOf) {
            canvas.drawText(
                context.getString(R.string.path_score_out_of),
                centreX,
                numberBaseline + outOfPaint.textSize * 1.6f,
                outOfPaint
            )
        }
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    )

    private companion object {
        const val DEFAULT_SIZE_DP = 168f
        const val OUT_OF_MIN_SIZE_DP = 100f
        const val ANIMATION_MILLIS = 900L

        // the ring starts at the top and goes clockwise
        const val START_ANGLE = -90f

        // sizes as a share of the ring's width
        const val STROKE_SHARE = 0.085f
        const val NUMBER_SHARE = 0.30f
        const val SMALL_NUMBER_SHARE = 0.34f
        const val OUT_OF_SHARE = 0.09f
    }
}
