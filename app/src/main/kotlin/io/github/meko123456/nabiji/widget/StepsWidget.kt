package io.github.meko123456.nabiji.widget

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.semantics.semantics
import androidx.glance.semantics.contentDescription
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.action.actionStartActivity
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.meko123456.nabiji.MainActivity
import io.github.meko123456.nabiji.data.GoalRepository
import io.github.meko123456.nabiji.data.HealthConnectSource
import io.github.meko123456.nabiji.domain.HealthAvailability
import io.github.meko123456.nabiji.domain.StepGoal
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * Home-screen widget: today's steps against the goal.
 *
 * It reads Health Connect directly at update time rather than caching, because the widget is
 * refreshed rarely — this is a dashboard, not a live tracker, and polling for steps would cost
 * battery for numbers nobody is watching.
 */
class StepsWidget : GlanceAppWidget() {

    // Exact, so LocalSize is the widget's real size and a short widget can lay itself out to fit.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = Reading.load(context)
        provideContent {
            // Read here, again for every version refresh writes. While a widget's session is open
            // (about 45 s after each update), update() recomposes this content instead of calling
            // provideGlance again, so numbers read above provideContent would not change.
            val version = currentState(VERSION) ?: 0
            val reading by produceState(first, version) { value = Reading.load(context) }
            GlanceTheme {
                WidgetBody(reading.steps, reading.goal)
            }
        }
    }

    /** Today's steps and the goal. */
    private data class Reading(val steps: Steps, val goal: StepGoal) {
        companion object {
            suspend fun load(context: Context): Reading {
                val goal = GoalRepository(context).goal.first()
                val source = HealthConnectSource(context)
                val today = LocalDate.now()
                val steps = if (source.availability() != HealthAvailability.AVAILABLE || !source.hasPermissions()) {
                    Steps.NotConnected
                } else {
                    source.dailyActivity(today, today).fold(
                        onSuccess = { days -> Steps.Count(days.firstOrNull { it.date == today }?.steps ?: 0L) },
                        // Not 0: Health Connect refused, and the steps are unknown. It refuses a
                        // widget update the app makes from the background unless the app holds
                        // the background permission, which drew a false "0 steps".
                        onFailure = { Steps.Unread },
                    )
                }
                return Reading(steps, goal)
            }
        }
    }

    private sealed interface Steps {
        /** No Health Connect, or no permission to read steps. */
        data object NotConnected : Steps

        /** Connected, but this read was refused or failed. */
        data object Unread : Steps

        data class Count(val value: Long) : Steps
    }

    @androidx.compose.runtime.Composable
    private fun WidgetBody(reading: Steps, goal: StepGoal) {
        // A description on the container is what a screen reader reads *instead of* the children,
        // so it has to carry the numbers as well as the fact that the widget is a button. The
        // first attempt at labelling the tap target said only "Nabiji: open the app", which
        // bought the label at the cost of the step count it was wrapped around.
        val spoken = when (reading) {
            Steps.NotConnected -> "Nabiji: not connected to Health Connect yet. Opens the app."
            Steps.Unread -> "Nabiji: today's steps could not be read here. Opens the app to update them."
            is Steps.Count ->
                "Nabiji: ${reading.value} steps of ${goal.steps} today, ${goal.percent(reading.value)} percent. Opens the app."
        }
        // Glance's default text colour is a flat black and its progress indicator a flat purple —
        // neither asks the theme anything, so on a dark widget background the numbers went to
        // black on near-black. Every colour here is named against the theme instead.
        val ink = GlanceTheme.colors.onSurface
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(actionStartActivity<MainActivity>())
                .semantics { contentDescription = spoken },
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.Start,
        ) {
            val steps = when (reading) {
                is Steps.Count -> reading.value
                // No permission, no Health Connect, or a refused read: say so instead of showing
                // a fake zero.
                Steps.NotConnected -> {
                    Text("Nabiji", style = TextStyle(color = ink, fontWeight = FontWeight.Bold))
                    Text("Tap to connect Health Connect", style = TextStyle(color = ink, fontSize = 12.sp))
                    return@Column
                }
                Steps.Unread -> {
                    Text("Nabiji", style = TextStyle(color = ink, fontWeight = FontWeight.Bold))
                    Text("Tap to update", style = TextStyle(color = ink, fontSize = 12.sp))
                    return@Column
                }
            }
            // A one-row widget (its default size) is about 67dp tall on a Pixel launcher, and the
            // number above the goal above the bar needs about 85dp: the goal and the bar were cut
            // off. Short widgets put the number and the goal on one line instead.
            if (LocalSize.current.height < COMPACT_BELOW) {
                // One line, at sizes that keep "12345 / 30000" inside a two-cell width (about
                // 100dp after padding); "of" made it wrap.
                Row(verticalAlignment = Alignment.Vertical.Bottom) {
                    Text(
                        "$steps",
                        style = TextStyle(color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    Text(
                        " / ${goal.steps}",
                        style = TextStyle(color = ink, fontSize = 11.sp),
                        maxLines = 1,
                        modifier = GlanceModifier.padding(bottom = 3.dp),
                    )
                }
            } else {
                Text("$steps", style = TextStyle(color = ink, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                Text("of ${goal.steps} steps", style = TextStyle(color = ink, fontSize = 12.sp))
            }
            LinearProgressIndicator(
                progress = goal.progress(steps),
                modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.secondaryContainer,
            )
        }
    }

    companion object {
        val VERSION = intPreferencesKey("version")

        /** Below this height the steps and the goal share a line. */
        private val COMPACT_BELOW = 90.dp

        /** Re-render every placed widget from fresh numbers: the dashboard calls it once a new goal is stored. */
        suspend fun refresh(context: Context) {
            runCatching {
                val widget = StepsWidget()
                GlanceAppWidgetManager(context).getGlanceIds(StepsWidget::class.java).forEach { id ->
                    updateAppWidgetState(context, id) { it[VERSION] = (it[VERSION] ?: 0) + 1 }
                    widget.update(context, id)
                }
            }
        }
    }
}

/** Receiver the launcher talks to. */
class StepsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StepsWidget()
}
