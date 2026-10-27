package org.fossify.messages.extensions

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.messages.R
import java.time.Instant
import java.time.Period
import java.time.ZoneId

private const val MILLIS_PER_SECOND = 1000L
private const val NOW_MAX_SECONDS = 10L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR
private const val SECONDS_PER_DAY = SECONDS_PER_HOUR * HOURS_PER_DAY
private const val HOURS_BUCKET_END_SECONDS = (HOURS_PER_DAY + 1) * SECONDS_PER_HOUR
private const val RELATIVE_DATE_REFRESH_INTERVAL_MS = 10_000L

fun Long.formatMessageDate(
    context: Context,
    hideTimeOnOtherDays: Boolean,
    showCurrentYear: Boolean,
): String {
    if (context.config.useRelativeDateTime) {
        toRelativeTimeAgo(context)?.let { return it }
    }
    return formatDateOrTime(
        context = context,
        hideTimeOnOtherDays = hideTimeOnOtherDays,
        showCurrentYear = showCurrentYear,
    )
}

fun Long.formatRelativeOr(context: Context, absolute: () -> String): String {
    if (context.config.useRelativeDateTime) {
        toRelativeTimeAgo(context)?.let { return it }
    }
    return absolute()
}

fun Long.toRelativeTimeAgo(context: Context, nowMillis: Long = System.currentTimeMillis()): String? {
    val age = relativeAge(timestampMillis = this, nowMillis = nowMillis) ?: return null
    return age.toLabel(context)
}

private fun relativeAge(timestampMillis: Long, nowMillis: Long): RelativeAge? {
    val elapsedMillis = nowMillis - timestampMillis
    if (elapsedMillis < 0L) {
        return null
    }

    val elapsedSeconds = elapsedMillis / MILLIS_PER_SECOND
    return when {
        elapsedSeconds <= NOW_MAX_SECONDS -> RelativeAge.Now
        elapsedSeconds < SECONDS_PER_MINUTE -> RelativeAge.FewSeconds
        elapsedSeconds < SECONDS_PER_HOUR -> {
            RelativeAge.Minutes((elapsedSeconds / SECONDS_PER_MINUTE).toInt())
        }
        elapsedSeconds < HOURS_BUCKET_END_SECONDS -> {
            RelativeAge.Hours((elapsedSeconds / SECONDS_PER_HOUR).toInt())
        }
        else -> calendarAge(timestampMillis, nowMillis, elapsedSeconds)
    }
}

private fun calendarAge(timestampMillis: Long, nowMillis: Long, elapsedSeconds: Long): RelativeAge {
    val zone = ZoneId.systemDefault()
    val startDate = Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDate()
    val endDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val period = Period.between(startDate, endDate)
    val days = (elapsedSeconds / SECONDS_PER_DAY).toInt().coerceAtLeast(1)
    return when {
        period.years >= 1 -> RelativeAge.Years(period.years)
        period.months >= 1 -> RelativeAge.Months(period.months)
        else -> RelativeAge.Days(days)
    }
}

private fun RelativeAge.toLabel(context: Context): String {
    return when (this) {
        RelativeAge.Now -> context.getString(R.string.relative_now)
        RelativeAge.FewSeconds -> context.getString(R.string.relative_few_seconds_ago)
        is RelativeAge.Minutes -> context.resources.getQuantityString(
            R.plurals.relative_minutes_ago,
            count,
            count,
        )
        is RelativeAge.Hours -> context.resources.getQuantityString(
            R.plurals.relative_hours_ago,
            count,
            count,
        )
        is RelativeAge.Days -> context.resources.getQuantityString(
            R.plurals.relative_days_ago,
            count,
            count,
        )
        is RelativeAge.Months -> context.resources.getQuantityString(
            R.plurals.relative_months_ago,
            count,
            count,
        )
        is RelativeAge.Years -> context.resources.getQuantityString(
            R.plurals.relative_years_ago,
            count,
            count,
        )
    }
}

private sealed class RelativeAge {
    data object Now : RelativeAge()
    data object FewSeconds : RelativeAge()
    data class Minutes(val count: Int) : RelativeAge()
    data class Hours(val count: Int) : RelativeAge()
    data class Days(val count: Int) : RelativeAge()
    data class Months(val count: Int) : RelativeAge()
    data class Years(val count: Int) : RelativeAge()
}

class RelativeDateTicker(
    private val isRelativeDateTime: () -> Boolean,
    private val refreshDates: (RecyclerView) -> Unit,
) {
    private var recyclerView: RecyclerView? = null
    private var wasRelativeDateTime: Boolean? = null
    private val tick = Runnable { onTick() }

    fun attach(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        recyclerView.removeCallbacks(tick)
        recyclerView.postDelayed(tick, RELATIVE_DATE_REFRESH_INTERVAL_MS)
    }

    fun detach(recyclerView: RecyclerView) {
        recyclerView.removeCallbacks(tick)
        if (this.recyclerView === recyclerView) {
            this.recyclerView = null
        }
    }

    fun refreshNow() {
        recyclerView?.let(refreshDates)
    }

    private fun onTick() {
        val view = recyclerView ?: return
        val relative = isRelativeDateTime()
        val modeChanged = wasRelativeDateTime != null && wasRelativeDateTime != relative
        if (relative || modeChanged) {
            refreshDates(view)
        }
        wasRelativeDateTime = relative
        view.postDelayed(tick, RELATIVE_DATE_REFRESH_INTERVAL_MS)
    }
}

inline fun RecyclerView.forEachVisiblePosition(block: (child: View, position: Int) -> Unit) {
    for (index in 0 until childCount) {
        val child = getChildAt(index) ?: continue
        val position = getChildAdapterPosition(child)
        if (position != RecyclerView.NO_POSITION) {
            block(child, position)
        }
    }
}
