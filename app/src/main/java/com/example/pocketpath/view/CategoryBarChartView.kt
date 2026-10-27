package com.example.pocketpath.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.example.pocketpath.R
import com.example.pocketpath.util.CategorySpend
import com.example.pocketpath.util.Money

// draws how much was spent in each category during the chosen period
// bars run across the screen so long category names stay readable on a phone
// and the monthly goals are drawn over them as reference lines
class CategoryBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var rows: List<CategorySpend> = emptyList()
    private var minimumGoal = 0.0
    private var maximumGoal = 0.0

    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_label)
        textSize = sp(14f)
        isFakeBoldText = true
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_value)
        textSize = sp(13f)
        textAlign = Paint.Align.RIGHT
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_track)
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_bar)
    }

    private val emptyBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_bar_empty)
    }

    private val minimumGoalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_goal_minimum)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    }

    private val maximumGoalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_goal_maximum)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    }

    private val goalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        textAlign = Paint.Align.CENTER
    }

    private val barRect = RectF()

    private val rowHeight = dp(46f)
    private val barHeight = dp(14f)
    private val headerHeight = dp(18f)
    private val barCorner = dp(7f)

    // replaces the graph contents and redraws
    fun setData(rows: List<CategorySpend>, minimumGoal: Double, maximumGoal: Double) {
        this.rows = rows
        this.minimumGoal = minimumGoal
        this.maximumGoal = maximumGoal
        updateDescription()
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)

        val needed = if (rows.isEmpty()) {
            0f
        } else {
            headerHeight + rows.size * rowHeight + paddingTop + paddingBottom
        }

        setMeasuredDimension(width, resolveSize(needed.toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (rows.isEmpty()) return

        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val available = right - left
        if (available <= 0f) return

        // the scale has to fit the biggest bar and the maximum goal so the
        // goal lines stay inside the graph instead of running off the edge
        val largestBar = rows.maxOfOrNull { it.totalAmount } ?: 0.0
        val scale = maxOf(largestBar, maximumGoal)
        if (scale <= 0.0) return

        val linesTop = paddingTop + headerHeight
        val linesBottom = linesTop + rows.size * rowHeight

        // the goal lines are drawn first so the names and the amounts sit on
        // top of them instead of being cut through
        drawGoalLine(canvas, minimumGoal, scale, left, available, linesTop, linesBottom, minimumGoalPaint, "min")
        drawGoalLine(canvas, maximumGoal, scale, left, available, linesTop, linesBottom, maximumGoalPaint, "max")

        var top = linesTop

        for (row in rows) {
            val nameBaseline = top + namePaint.textSize
            canvas.drawText(row.categoryName, left, nameBaseline, namePaint)
            canvas.drawText(Money.format(row.totalAmount), right, nameBaseline, valuePaint)

            val barTop = nameBaseline + dp(8f)

            barRect.set(left, barTop, right, barTop + barHeight)
            canvas.drawRoundRect(barRect, barCorner, barCorner, trackPaint)

            // a category with nothing spent still gets a row so the user can
            // see it was budgeted for but not used
            if (row.totalAmount > 0.0) {
                val barWidth = (row.totalAmount / scale * available).toFloat()
                barRect.set(left, barTop, left + barWidth.coerceAtLeast(barCorner * 2), barTop + barHeight)
                canvas.drawRoundRect(barRect, barCorner, barCorner, barPaint)
            } else {
                barRect.set(left, barTop, left + barCorner * 2, barTop + barHeight)
                canvas.drawRoundRect(barRect, barCorner, barCorner, emptyBarPaint)
            }

            top += rowHeight
        }

    }

    // draws one dashed goal line with a small label above the graph
    private fun drawGoalLine(
        canvas: Canvas,
        goal: Double,
        scale: Double,
        left: Float,
        available: Float,
        top: Float,
        bottom: Float,
        linePaint: Paint,
        label: String
    ) {
        if (goal <= 0.0) return

        val x = left + (goal / scale * available).toFloat()
        canvas.drawLine(x, top, x, bottom, linePaint)

        goalLabelPaint.color = linePaint.color

        // keep the label inside the view when the line sits near an edge
        val half = goalLabelPaint.measureText(label) / 2f
        val labelX = x.coerceIn(left + half, left + available - half)

        canvas.drawText(label, labelX, top - dp(5f), goalLabelPaint)
    }

    // lets a screen reader describe the graph instead of announcing nothing
    private fun updateDescription() {
        if (rows.isEmpty()) {
            contentDescription = context.getString(R.string.chart_empty_description)
            return
        }

        val parts = rows.joinToString(separator = ", ") {
            "${it.categoryName} ${Money.format(it.totalAmount)}"
        }

        contentDescription = context.getString(R.string.chart_description, parts)
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    )

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics
    )

    init {
        // the goal labels sit above the bars so nothing is clipped
        setWillNotDraw(false)
        if (isInEditMode) {
            setBackgroundColor(Color.TRANSPARENT)
        }
    }
}
