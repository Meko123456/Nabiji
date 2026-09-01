package io.github.meko123456.nabiji.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
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

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val goal = GoalRepository(context).goal.first()
        val source = HealthConnectSource(context)
        val today = LocalDate.now()
        val steps: Long? = if (source.availability() != HealthAvailability.AVAILABLE || !source.hasPermissions()) {
            null
        } else {
            source.dailyActivity(today, today).getOrNull()?.firstOrNull { it.date == today }?.steps ?: 0L
        }

        provideContent {
            GlanceTheme {
                WidgetBody(steps, goal)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun WidgetBody(steps: Long?, goal: StepGoal) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.Start,
        ) {
            if (steps == null) {
                // No permission or no Health Connect: say so instead of showing a fake zero.
                Text("Nabiji", style = TextStyle(fontWeight = FontWeight.Bold))
                Text("Tap to connect Health Connect", style = TextStyle(fontSize = 12.sp))
                return@Column
            }
            Text("$steps", style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold))
            Text("of ${goal.steps} steps", style = TextStyle(fontSize = 12.sp))
            LinearProgressIndicator(
                progress = goal.progress(steps),
                modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }

    companion object {
        /** Re-render every placed widget — called when the goal changes. */
        suspend fun refresh(context: Context) {
            runCatching { StepsWidget().updateAll(context) }
        }
    }
}

/** Receiver the launcher talks to. */
class StepsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StepsWidget()
}
