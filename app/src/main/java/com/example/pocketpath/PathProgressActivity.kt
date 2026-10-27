package com.example.pocketpath

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import com.example.pocketpath.data.firebase.FirebaseRepository
import com.example.pocketpath.data.firebase.FirebaseRepository.CategoryData
import com.example.pocketpath.data.firebase.FirebaseRepository.ExpenseData
import com.example.pocketpath.data.firebase.FirebaseRepository.GoalData
import com.example.pocketpath.util.Achievements
import com.example.pocketpath.util.Badge
import com.example.pocketpath.util.BadgeStatus
import com.example.pocketpath.util.BudgetSnapshot
import com.example.pocketpath.util.CategoryLimit
import com.example.pocketpath.util.Money
import com.example.pocketpath.util.NetworkStatus
import com.example.pocketpath.util.PathLevel
import com.example.pocketpath.util.PathScore
import com.example.pocketpath.util.PathScoreResult
import com.example.pocketpath.util.PillarOutcome
import com.example.pocketpath.util.ScorePart
import com.example.pocketpath.util.ScorePillar
import com.example.pocketpath.util.ScoreTip
import com.example.pocketpath.util.TrackedExpense
import com.example.pocketpath.view.ScoreRingView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// the path progress and achievements screen from the design doc
// it gives the user a score out of 100 for this month, explains what made
// the score, suggests how to raise it and shows the badges they have earned
// all of the rules live in PathScore and Achievements so they can be tested
class PathProgressActivity : AppCompatActivity() {

    // the three states of the screen
    private lateinit var loadingView: View
    private lateinit var errorView: View
    private lateinit var contentView: View

    private lateinit var tvErrorTitle: TextView
    private lateinit var tvErrorMessage: TextView

    // the score card
    private lateinit var tvScoreMonth: TextView
    private lateinit var scoreRing: ScoreRingView
    private lateinit var tvLevel: TextView
    private lateinit var tvNextLevel: TextView
    private lateinit var tvStreak: TextView

    // the breakdown, tips and badges
    private lateinit var scorePartsContainer: LinearLayout
    private lateinit var tipsContainer: LinearLayout
    private lateinit var tvBadgeCount: TextView
    private lateinit var progressBadges: LinearProgressIndicator
    private lateinit var badgesContainer: LinearLayout

    private var currentUserId: String? = null

    // watches firebase and gives up waiting if nothing arrives in time
    private var observeJob: Job? = null
    private var timeoutJob: Job? = null
    private var hasRendered = false

    // the last score drawn so the ring only animates when the score changes
    private var lastScore: Int? = null

    // badges already sent to firestore this visit so a slow or refused save
    // is not repeated every time the data changes
    private val badgeIdsSaved = mutableSetOf<String>()

    // sets the screen up when it opens
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_path_progress)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        currentUserId = FirebaseRepository.currentUserId

        // nobody is signed in so go back to the login screen
        if (currentUserId == null) {
            Log.w(TAG, "Path progress opened without a logged-in user")
            returnToLogin()
            return
        }

        connectViews()
        configureButtons()
        loadProgress()
    }

    // links the variables to the views in the layout
    private fun connectViews() {
        loadingView = findViewById(R.id.loadingView)
        errorView = findViewById(R.id.errorView)
        contentView = findViewById(R.id.contentView)
        tvErrorTitle = findViewById(R.id.tvErrorTitle)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        tvScoreMonth = findViewById(R.id.tvScoreMonth)
        scoreRing = findViewById(R.id.scoreRing)
        tvLevel = findViewById(R.id.tvLevel)
        tvNextLevel = findViewById(R.id.tvNextLevel)
        tvStreak = findViewById(R.id.tvStreak)
        scorePartsContainer = findViewById(R.id.scorePartsContainer)
        tipsContainer = findViewById(R.id.tipsContainer)
        tvBadgeCount = findViewById(R.id.tvBadgeCount)
        progressBadges = findViewById(R.id.progressBadges)
        badgesContainer = findViewById(R.id.badgesContainer)
    }

    private fun configureButtons() {
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnRetry).setOnClickListener {
            Log.d(TAG, "Retrying path progress load")
            loadProgress()
        }
    }

    // watches the expenses, categories, goal and saved badges together so the
    // score and badges update by themselves when anything changes
    private fun loadProgress() {
        val uid = currentUserId ?: return

        observeJob?.cancel()
        timeoutJob?.cancel()
        hasRendered = false

        // with no connection firestore would quietly hand back an empty cache
        // and show a score of zero, so say the phone is offline instead
        if (!NetworkStatus.isOnline(this)) {
            Log.w(TAG, "No internet connection, path progress not loaded")
            showError(R.string.path_progress_offline_title, R.string.path_progress_offline_message)
            return
        }

        showLoading()

        timeoutJob = lifecycleScope.launch {
            delay(LOAD_TIMEOUT_MILLIS)
            if (!hasRendered) {
                Log.w(TAG, "Path progress took longer than $LOAD_TIMEOUT_MILLIS ms to load")
                showError(R.string.path_progress_error_title, R.string.path_progress_slow_message)
            }
        }

        // the badges are a bonus so if they cannot be read the score still shows
        val savedBadges = FirebaseRepository.observeUnlockedBadges(uid)
            .catch { exception ->
                Log.e(TAG, "Unable to read saved badges, working them out from the data only", exception)
                emit(emptyMap())
            }

        observeJob = lifecycleScope.launch {
            try {
                combine(
                    FirebaseRepository.observeExpenses(uid),
                    FirebaseRepository.observeCategories(uid),
                    FirebaseRepository.observeMonthlyGoal(uid),
                    savedBadges
                ) { expenses, categories, goal, badges ->
                    ProgressData(expenses, categories, goal, badges)
                }.collectLatest { data ->
                    render(data)
                }
            } catch (cancellation: CancellationException) {
                // retrying or leaving the screen cancels the old watch which is normal
                throw cancellation
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to load path progress", exception)
                timeoutJob?.cancel()
                showError(R.string.path_progress_error_title, R.string.path_progress_error_message)
            }
        }
    }

    // works out the score and badges from one snapshot of the data and draws them
    private fun render(data: ProgressData) {
        val now = System.currentTimeMillis()
        val snapshot = buildSnapshot(data.expenses, data.categories, data.goal)

        val result = PathScore.calculate(snapshot, now)
        val badges = Achievements.evaluate(snapshot, result.score, now, data.savedBadges)

        Log.d(
            TAG,
            "Path score ${result.score} (${result.level}) from ${data.expenses.size} expenses, " +
                "parts ${result.parts.joinToString { "${it.pillar}=${it.points}" }}"
        )

        renderScore(result, now)
        renderParts(result, data.categories.isNotEmpty())
        renderTips(result)
        renderBadges(badges)
        saveNewBadges(badges)

        hasRendered = true
        timeoutJob?.cancel()
        showContent()
    }

    // the big number, the level and the streak
    private fun renderScore(result: PathScoreResult, now: Long) {
        tvScoreMonth.text = getString(R.string.path_score_month, MONTH_FORMAT.format(Date(now)))

        scoreRing.setScore(result.score, animate = lastScore != result.score)
        lastScore = result.score

        tvLevel.setText(levelName(result.level))

        val next = result.level.next
        val pointsToNext = result.pointsToNextLevel
        tvNextLevel.text = if (next == null || pointsToNext == null) {
            getString(R.string.path_top_level)
        } else {
            resources.getQuantityString(
                R.plurals.path_points_to_next_level, pointsToNext, pointsToNext, getString(levelName(next))
            )
        }

        tvStreak.text = if (result.currentStreak > 0) {
            getString(R.string.path_streak, result.currentStreak, result.longestStreak)
        } else {
            getString(R.string.path_no_streak)
        }
    }

    // one row for each of the four areas showing what helped and what hurt
    private fun renderParts(result: PathScoreResult, hasCategories: Boolean) {
        scorePartsContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        result.parts.forEachIndexed { index, part ->
            val row = inflater.inflate(R.layout.item_score_part, scorePartsContainer, false)

            val name = getString(pillarName(part.pillar))
            val reason = explain(part, hasCategories)

            // green for full marks, amber for some points and red for none
            val (statusText, statusColour) = when {
                part.isFullMarks -> R.string.pillar_status_full to R.color.goal_within
                part.points > 0 -> R.string.pillar_status_partial to R.color.path_partial
                else -> R.string.pillar_status_none to R.color.goal_above
            }
            val colour = ContextCompat.getColor(this, statusColour)

            row.findViewById<TextView>(R.id.tvPartStatus).apply {
                setText(statusText)
                backgroundTintList = ColorStateList.valueOf(colour)
            }
            row.findViewById<TextView>(R.id.tvPartName).text = name
            row.findViewById<TextView>(R.id.tvPartPoints).text =
                getString(R.string.pillar_points, part.points, part.maxPoints)
            row.findViewById<LinearProgressIndicator>(R.id.progressPart).apply {
                max = part.maxPoints
                setIndicatorColor(colour)
                setProgressCompat(part.points, true)
            }
            row.findViewById<TextView>(R.id.tvPartReason).text = reason

            row.contentDescription = getString(R.string.pillar_description, name, part.points, part.maxPoints, reason)

            scorePartsContainer.addView(row)

            // a thin line between rows like a list
            if (index < result.parts.lastIndex) scorePartsContainer.addView(divider())
        }
    }

    // the sentence that says why an area earned the points it did
    private fun explain(part: ScorePart, hasCategories: Boolean): String {
        val goal = part.goal

        return when (part.outcome) {
            PillarOutcome.GOAL_NOT_SET -> getString(R.string.why_goal_not_set)
            PillarOutcome.GOAL_WITHIN -> getString(
                R.string.why_goal_within,
                money(goal?.spent), money(goal?.minimum), money(goal?.maximum)
            )
            PillarOutcome.GOAL_BELOW_MINIMUM -> getString(
                R.string.why_goal_below, money(goal?.spent), money(goal?.minimum)
            )
            PillarOutcome.GOAL_SLIGHTLY_OVER -> getString(
                R.string.why_goal_slightly_over,
                money(goal?.spent), money(goal?.remainingToMaximum?.let { -it }), money(goal?.maximum)
            )
            PillarOutcome.GOAL_WELL_OVER -> getString(
                R.string.why_goal_well_over,
                money(goal?.spent), money(goal?.remainingToMaximum?.let { -it }), money(goal?.maximum)
            )
            PillarOutcome.NO_LIMITS_SET -> getString(
                if (hasCategories) R.string.why_no_limits else R.string.why_no_categories
            )
            PillarOutcome.NO_SPENDING_YET -> getString(R.string.why_no_spending)
            PillarOutcome.ALL_WITHIN_LIMITS -> resources.getQuantityString(
                R.plurals.why_all_within_limits, part.target, part.target
            )
            PillarOutcome.SOME_OVER_LIMITS -> getString(
                R.string.why_some_over_limits,
                part.achieved, part.target, part.overLimit.joinToString { it.name }
            )
            PillarOutcome.LOGGED_ENOUGH -> getString(
                R.string.why_logged_enough, part.achieved, PathScore.LOGGING_WINDOW_DAYS
            )
            PillarOutcome.LOGGED_SOME -> getString(
                R.string.why_logged_some, part.achieved, PathScore.LOGGING_WINDOW_DAYS, part.target
            )
            PillarOutcome.NOT_LOGGED -> getString(R.string.why_not_logged, PathScore.LOGGING_WINDOW_DAYS)
            PillarOutcome.RECEIPTS_ENOUGH -> getString(R.string.why_receipts_enough, part.achieved, part.target)
            PillarOutcome.RECEIPTS_SOME -> getString(R.string.why_receipts_some, part.achieved, part.target)
            PillarOutcome.NO_RECEIPTS -> resources.getQuantityString(
                R.plurals.why_no_receipts, part.target, part.target
            )
        }
    }

    // numbered suggestions with the points each one is worth
    private fun renderTips(result: PathScoreResult) {
        tipsContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        // a perfect score still gets general advice but leads with praise
        if (result.tips.none { it.pointsAvailable > 0 }) {
            tipsContainer.addView(tipRow(inflater, getString(R.string.tips_all_done), null, 0))
        }

        result.tips.forEachIndexed { index, tip ->
            tipsContainer.addView(tipRow(inflater, describe(tip), index + 1, tip.pointsAvailable))
        }
    }

    private fun tipRow(inflater: LayoutInflater, text: String, number: Int?, points: Int): View {
        val row = inflater.inflate(R.layout.item_score_tip, tipsContainer, false)

        row.findViewById<TextView>(R.id.tvTipNumber).text = number?.toString() ?: getString(R.string.pillar_status_full)
        row.findViewById<TextView>(R.id.tvTipText).text = text

        val pointsView = row.findViewById<TextView>(R.id.tvTipPoints)
        if (points > 0) {
            pointsView.text = getString(R.string.tip_points, points)
            pointsView.visibility = View.VISIBLE
        } else {
            pointsView.visibility = View.GONE
        }

        return row
    }

    // the full sentence for one tip with the user's own numbers filled in
    private fun describe(tip: ScoreTip): String = when (tip) {
        is ScoreTip.SetMonthlyGoal -> getString(R.string.tip_set_goal)
        is ScoreTip.CutBackToMaximum -> getString(R.string.tip_cut_back, Money.format(tip.overBy))
        is ScoreTip.CheckMinimum -> getString(R.string.tip_check_minimum, Money.format(tip.shortBy))
        is ScoreTip.AddCategoryLimits -> getString(
            if (tip.hasCategories) R.string.tip_add_limits else R.string.tip_create_categories
        )
        is ScoreTip.CategoryOverLimit -> getString(
            R.string.tip_category_over,
            tip.usage.name, Money.format(tip.usage.overBy), Money.format(tip.usage.limit)
        )
        is ScoreTip.StartLogging -> getString(R.string.tip_start_logging)
        is ScoreTip.LogMoreDays -> resources.getQuantityString(
            R.plurals.tip_log_more_days, tip.daysNeeded,
            tip.daysNeeded, PathScore.LOGGING_TARGET_DAYS, PathScore.LOGGING_WINDOW_DAYS
        )
        is ScoreTip.AddReceipts -> resources.getQuantityString(
            R.plurals.tip_add_receipts, tip.receiptsNeeded, tip.receiptsNeeded
        )
        is ScoreTip.CategoryNearLimit -> getString(
            R.string.tip_category_near,
            tip.usage.name, Money.format(tip.usage.remaining), Money.format(tip.usage.limit)
        )
        is ScoreTip.DailyAllowance -> resources.getQuantityString(
            R.plurals.tip_daily_allowance, tip.daysLeft, Money.format(tip.perDay), tip.daysLeft
        )
    }

    // the badges two to a row with the unlocked count above them
    private fun renderBadges(statuses: List<BadgeStatus>) {
        val unlocked = statuses.count { it.isUnlocked }

        tvBadgeCount.text = getString(R.string.achievements_count, unlocked, statuses.size)
        progressBadges.setProgressCompat(unlocked * 100 / statuses.size.coerceAtLeast(1), true)

        badgesContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        statuses.chunked(BADGES_PER_ROW).forEachIndexed { rowIndex, rowStatuses ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (rowIndex > 0) topMargin = dp(BADGE_GAP_DP) }
            }

            rowStatuses.forEachIndexed { index, status ->
                val card = inflater.inflate(R.layout.item_badge, row, false)
                if (index > 0) {
                    (card.layoutParams as LinearLayout.LayoutParams).marginStart = dp(BADGE_GAP_DP)
                }
                bindBadge(card, status)
                row.addView(card)
            }

            // an odd badge at the end keeps half the width rather than stretching
            if (rowStatuses.size < BADGES_PER_ROW) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f).apply {
                    marginStart = dp(BADGE_GAP_DP)
                })
            }

            badgesContainer.addView(row)
        }
    }

    private fun bindBadge(card: View, status: BadgeStatus) {
        val style = badgeStyle(status.badge)
        val name = getString(style.name)
        val how = getString(style.howToUnlock)
        val accent = ContextCompat.getColor(this, style.colour)

        val icon = card.findViewById<ImageView>(R.id.imgBadgeIcon)
        icon.setImageResource(style.icon)

        val progress = card.findViewById<LinearProgressIndicator>(R.id.progressBadge)
        val statusText = card.findViewById<TextView>(R.id.tvBadgeStatus)

        card.findViewById<TextView>(R.id.tvBadgeName).text = name
        card.findViewById<TextView>(R.id.tvBadgeHow).text = how

        if (status.isUnlocked) {
            icon.backgroundTintList = ColorStateList.valueOf(accent)
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white)))
            card.findViewById<View>(R.id.imgBadgeLock).visibility = View.GONE
            (card as MaterialCardView).strokeColor = accent

            progress.visibility = View.GONE
            statusText.setTextColor(accent)
            statusText.text = status.unlockedAtMillis
                ?.let { getString(R.string.badge_unlocked_on, DATE_FORMAT.format(Date(it))) }
                ?: getString(R.string.badge_unlocked)

            card.contentDescription = getString(R.string.badge_description_unlocked, name, statusText.text)
        } else {
            val lockedIcon = ContextCompat.getColor(this, R.color.badge_locked_icon)
            icon.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.badge_locked_background))
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(lockedIcon))
            card.findViewById<View>(R.id.imgBadgeLock).visibility = View.VISIBLE

            // a one step badge has nothing to count so it just says locked
            if (status.badge.target > 1) {
                progress.visibility = View.VISIBLE
                progress.max = status.badge.target
                progress.setIndicatorColor(accent)
                progress.setProgressCompat(status.progress, false)
                statusText.text = getString(R.string.badge_progress, status.progress, status.badge.target)
            } else {
                progress.visibility = View.GONE
                statusText.setText(R.string.badge_locked)
            }

            card.contentDescription = getString(R.string.badge_description_locked, name, how)
        }
    }

    // saves badges the data has just earned so they keep their unlock date
    // and stay unlocked even if the expense that earned them is deleted
    private fun saveNewBadges(statuses: List<BadgeStatus>) {
        val uid = currentUserId ?: return

        val newBadges = Achievements.newlyEarned(statuses).filterNot { it.id in badgeIdsSaved }
        if (newBadges.isEmpty()) return

        badgeIdsSaved += newBadges.map { it.id }
        val unlockedAt = System.currentTimeMillis()

        lifecycleScope.launch {
            try {
                FirebaseRepository.saveUnlockedBadges(uid, newBadges.associate { it.id to unlockedAt })
                Log.d(TAG, "Saved ${newBadges.size} new badges: ${newBadges.joinToString { it.id }}")

                val message = if (newBadges.size == 1) {
                    getString(R.string.badge_new_single, getString(badgeStyle(newBadges.first()).name))
                } else {
                    getString(R.string.badge_new_many, newBadges.size)
                }
                Toast.makeText(this@PathProgressActivity, message, Toast.LENGTH_LONG).show()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                // the badges still show as unlocked from the data, just without a date
                Log.e(TAG, "Unable to save unlocked badges", exception)
            }
        }
    }

    private fun showLoading() = showOnly(loadingView)

    private fun showContent() = showOnly(contentView)

    private fun showError(@StringRes title: Int, @StringRes message: Int) {
        tvErrorTitle.setText(title)
        tvErrorMessage.setText(message)
        showOnly(errorView)
    }

    private fun showOnly(visible: View) {
        listOf(loadingView, errorView, contentView).forEach {
            it.visibility = if (it == visible) View.VISIBLE else View.GONE
        }
    }

    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        setBackgroundColor(DIVIDER_COLOR)
    }

    private fun money(amount: Double?): String = Money.format(amount ?: 0.0)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // sends the user back to the login screen
    private fun returnToLogin() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    // everything read from firebase for one redraw
    private data class ProgressData(
        val expenses: List<ExpenseData>,
        val categories: List<CategoryData>,
        val goal: GoalData?,
        val savedBadges: Map<String, Long>
    )

    // how one badge looks on screen
    private data class BadgeStyle(
        @StringRes val name: Int,
        @StringRes val howToUnlock: Int,
        @DrawableRes val icon: Int,
        @ColorRes val colour: Int
    )

    companion object {
        private const val TAG = "PocketPathProgress"

        // how long to wait for firebase before offering to try again
        private const val LOAD_TIMEOUT_MILLIS = 15_000L

        private const val BADGES_PER_ROW = 2
        private const val BADGE_GAP_DP = 12
        private const val DIVIDER_COLOR = 0xFFE3ECEA.toInt()

        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        private val MONTH_FORMAT = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

        // turns the firebase data into the plain classes the score rules use
        // shared with the dashboard card so both always show the same score
        fun buildSnapshot(
            expenses: List<ExpenseData>,
            categories: List<CategoryData>,
            goal: GoalData?
        ) = BudgetSnapshot(
            expenses = expenses.map {
                TrackedExpense(
                    categoryId = it.categoryId,
                    amount = it.amount,
                    dateMillis = it.expenseDate,
                    hasReceipt = !it.photoBase64.isNullOrBlank()
                )
            },
            categories = categories.map { CategoryLimit(it.categoryId, it.name, it.monthlyLimit) },
            minimumGoal = goal?.minimumAmount ?: 0.0,
            maximumGoal = goal?.maximumAmount ?: 0.0
        )

        // the name shown for each level, shared with the dashboard card
        @StringRes
        fun levelName(level: PathLevel): Int = when (level) {
            PathLevel.STARTING_OUT -> R.string.level_starting_out
            PathLevel.FINDING_YOUR_PATH -> R.string.level_finding_your_path
            PathLevel.ON_THE_RIGHT_PATH -> R.string.level_on_the_right_path
            PathLevel.PATH_MASTER -> R.string.level_path_master
        }

        @StringRes
        private fun pillarName(pillar: ScorePillar): Int = when (pillar) {
            ScorePillar.MONTHLY_GOAL -> R.string.pillar_monthly_goal
            ScorePillar.CATEGORY_LIMITS -> R.string.pillar_category_limits
            ScorePillar.LOGGING_HABIT -> R.string.pillar_logging_habit
            ScorePillar.RECEIPTS -> R.string.pillar_receipts
        }

        private fun badgeStyle(badge: Badge): BadgeStyle = when (badge) {
            Badge.FIRST_STEP -> BadgeStyle(
                R.string.badge_first_step, R.string.badge_first_step_how,
                R.drawable.ic_badge_first_step, R.color.badge_first_step
            )
            Badge.ORGANISER -> BadgeStyle(
                R.string.badge_organiser, R.string.badge_organiser_how,
                R.drawable.ic_badge_organiser, R.color.badge_organiser
            )
            Badge.GOAL_SETTER -> BadgeStyle(
                R.string.badge_goal_setter, R.string.badge_goal_setter_how,
                R.drawable.ic_badge_goal_setter, R.color.badge_goal_setter
            )
            Badge.ON_A_ROLL -> BadgeStyle(
                R.string.badge_on_a_roll, R.string.badge_on_a_roll_how,
                R.drawable.ic_badge_on_a_roll, R.color.badge_on_a_roll
            )
            Badge.RECEIPT_KEEPER -> BadgeStyle(
                R.string.badge_receipt_keeper, R.string.badge_receipt_keeper_how,
                R.drawable.ic_badge_receipt_keeper, R.color.badge_receipt_keeper
            )
            Badge.MONTH_MASTERED -> BadgeStyle(
                R.string.badge_month_mastered, R.string.badge_month_mastered_how,
                R.drawable.ic_badge_month_mastered, R.color.badge_month_mastered
            )
            Badge.PATH_FINDER -> BadgeStyle(
                R.string.badge_path_finder, R.string.badge_path_finder_how,
                R.drawable.ic_badge_path_finder, R.color.badge_path_finder
            )
            Badge.DEDICATED_TRACKER -> BadgeStyle(
                R.string.badge_dedicated_tracker, R.string.badge_dedicated_tracker_how,
                R.drawable.ic_badge_dedicated_tracker, R.color.badge_dedicated_tracker
            )
        }
    }
}
