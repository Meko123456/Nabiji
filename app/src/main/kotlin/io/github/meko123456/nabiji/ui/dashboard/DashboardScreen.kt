package io.github.meko123456.nabiji.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.meko123456.heatmap.ContributionHeatmap
import io.github.meko123456.heatmap.GithubGreens
import io.github.meko123456.heatmap.GithubLightGreens
import io.github.meko123456.heatmap.HeatmapLayout
import io.github.meko123456.nabiji.data.GoalRepository
import io.github.meko123456.nabiji.data.HealthConnectSource
import io.github.meko123456.nabiji.domain.ActivitySummary
import io.github.meko123456.nabiji.domain.DayActivity
import io.github.meko123456.nabiji.domain.HealthAvailability
import io.github.meko123456.nabiji.domain.StepGoal
import io.github.meko123456.nabiji.ui.theme.isDark
import java.time.LocalDate

/** Weeks the heatmap draws. Everything said *about* the heatmap has to agree with this. */
private const val HEATMAP_WEEKS = 26

/**
 * The smallest a thing you touch is allowed to be.
 *
 * Material is inconsistent about this. Slider quietly expands its own touch target past the
 * 44dp it draws, so it needs nothing; Button does not, and stops at the 40dp it draws. Only
 * the ones Material leaves short are given this.
 */
private val MinTouchTarget = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val vm: DashboardViewModel = viewModel {
        DashboardViewModel(HealthConnectSource(context), GoalRepository(context))
    }
    val state by vm.state.collectAsStateWithLifecycle()

    // Health Connect grants its reads in its own UI, not with a normal runtime dialog.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { vm.refresh() }

    // The bar holds two stacked lines of text and nothing else, so its height is the height of
    // that text. Left at Material's fixed 64dp, a reader at 200 % font size had the Georgian
    // line pushed out of the bar and up under the status-bar clock.
    val fontScale = LocalDensity.current.fontScale
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Nabiji", style = MaterialTheme.typography.titleLarge)
                        Text("ნაბიჯი", style = MaterialTheme.typography.labelMedium)
                    }
                },
                expandedHeight = 64.dp * fontScale.coerceIn(1f, 2f),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                DashboardState.Loading -> Loading()
                is DashboardState.Unavailable -> Explain(
                    title = when (s.availability) {
                        HealthAvailability.UPDATE_REQUIRED -> "Health Connect needs updating"
                        else -> "Health Connect isn't available"
                    },
                    body = when (s.availability) {
                        HealthAvailability.UPDATE_REQUIRED ->
                            "Update Health Connect from the Play Store, then come back."
                        else ->
                            "Nabiji reads the steps your phone and watch already record through Health Connect. " +
                                "It is built into Android 14 and newer; on older versions install it from the Play Store."
                    },
                )
                DashboardState.NeedsPermission -> Explain(
                    title = "Let Nabiji read your steps",
                    body = "Nabiji never counts steps itself — it reads what Health Connect already holds. " +
                        "Nothing leaves your device.",
                    action = "Grant in Health Connect" to {
                        permissionLauncher.launch(HealthConnectSource.PERMISSIONS)
                    },
                )
                is DashboardState.NoData -> Explain(
                    title = "No activity recorded yet",
                    body = "Health Connect has no steps for the last year. That usually means the app that " +
                        "tracks your steps — Samsung Health, Fitbit, Google Fit — isn't set to share them " +
                        "with Health Connect yet. Open that app's Health Connect settings and allow it to " +
                        "write steps.\n\nOn Android 14 and newer, Health Connect can also count steps on its " +
                        "own now that Nabiji has permission, so data may start appearing as you walk.",
                    action = "Check again" to vm::refresh,
                )
                is DashboardState.Failed -> Explain(
                    title = "Couldn't read your activity",
                    body = s.message,
                    action = "Try again" to vm::refresh,
                )
                is DashboardState.Ready -> Ready(s, vm::setGoal)
            }
        }
    }
}

/** A card's title, announced as a heading so a screen reader can jump between the cards. */
@Composable
private fun SectionTitle(
    text: String,
    style: TextStyle = MaterialTheme.typography.titleSmall,
    color: Color = Color.Unspecified,
) {
    Text(text, style = style, color = color, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        // A bare spinner is silent to a screen reader: it reports a busy indicator with nothing
        // to say what is being waited on.
        CircularProgressIndicator(
            Modifier.semantics { contentDescription = "Reading your activity from Health Connect" },
        )
    }
}

/** One honest explanation per non-ready state, with a way forward where one exists. */
@Composable
private fun Explain(title: String, body: String, action: Pair<String, () -> Unit>? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.let { (label, onClick) ->
                Button(
                    onClick = onClick,
                    modifier = Modifier.padding(top = 4.dp).heightIn(min = MinTouchTarget),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun Ready(state: DashboardState.Ready, onGoal: (Int) -> Unit) {
    TodayCard(state)
    StatsCard(state)
    HeatmapCard(state.days, state.goal)
    GoalCard(state.goal, onGoal)
}

@Composable
private fun TodayCard(state: DashboardState.Ready) {
    val steps = state.today.steps
    val goal = state.goal
    val remaining = goal.remaining(steps)
    // The card is read out as one sentence, so the sentence has to carry everything the card
    // shows. It used to stop after the streak, which quietly hid the distance, the calories and
    // how far there was left to go from anyone listening rather than looking.
    val spoken = buildList {
        add("Today: $steps steps, ${goal.percent(steps)} percent of your ${goal.steps} step goal")
        add(if (remaining == 0L) "goal met" else "$remaining steps to go")
        if (state.streak > 0) add("${state.streak} day streak")
        state.today.distanceMeters?.let { add("%.2f kilometres".format(it / 1000)) }
        state.today.activeKilocalories?.let { add("${it.toInt()} kilocalories") }
    }.joinToString(", ")

    Card(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle(
                "Today",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("$steps", style = MaterialTheme.typography.displayMedium)
            Text(
                "of ${goal.steps} steps · ${goal.percent(steps)}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { goal.progress(steps) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Text(
                if (remaining == 0L) "Goal met 🎉" else "$remaining to go",
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.streak > 0) {
                Text("🔥 ${state.streak}-day streak", style = MaterialTheme.typography.titleSmall)
            }
            state.today.distanceMeters?.let {
                Text("%.2f km".format(it / 1000), style = MaterialTheme.typography.bodySmall)
            }
            state.today.activeKilocalories?.let {
                Text("${it.toInt()} kcal", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatsCard(state: DashboardState.Ready) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("This week")
            // Four figures pinned into one Row fitted at the default text size and nothing
            // larger: at 200 % they were squeezed together until "Goal days" broke across two
            // lines and left its own number behind on the line above. Letting them flow onto a
            // second line keeps every figure next to its label however big the text gets.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Stat("Total", "${state.weekTotal}")
                Stat("Average", "${state.weekAverage}")
                Stat("Best", "${state.bestDay?.steps ?: 0}")
                Stat("Goal days", "${state.daysMeetingGoal}/7")
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A year of steps, drawn with my published heatmap library. */
@Composable
private fun HeatmapCard(days: List<DayActivity>, goal: StepGoal) {
    val counts = ActivitySummary.heatmapCounts(days)
    val endDay = LocalDate.now().toEpochDay()
    // The dashboard loads a year but the grid only draws 26 weeks of it, so everything said
    // about the grid has to be counted over the window it actually draws. Counting the whole
    // year against a 182-day window is how the old description came to claim "295 of the last
    // 182 days" — a sentence that cannot be true, read out to the people who cannot see the
    // grid and check.
    val firstDay = HeatmapLayout.firstDay(endDay, HEATMAP_WEEKS)
    val shownDays = HeatmapLayout.daysShown(endDay, HEATMAP_WEEKS)
    val active = HeatmapLayout.daysWithin(counts.keys, endDay, HEATMAP_WEEKS)
    val met = days.count { it.date.toEpochDay() >= firstDay && goal.isMet(it.steps) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Last 26 weeks")
            ContributionHeatmap(
                counts = counts,
                endDay = endDay,
                weeks = HEATMAP_WEEKS,
                // Absolute scale: the goal is the darkest level, so shading means the same
                // thing every week instead of drifting with the busiest day on screen.
                maxCount = goal.steps,
                // Which greens suit the card depends on the theme actually in use, which is
                // not necessarily the system setting.
                levelColors = if (MaterialTheme.colorScheme.isDark) GithubGreens else GithubLightGreens,
                contentDescription = "Activity heatmap: $active of the last $shownDays days have " +
                    "recorded steps, and $met of them met the ${goal.steps} step goal",
            )
            // Depth of green is the only thing the grid says, and depth of green is the one
            // thing a red-green colour-blind reader cannot get back out of it. Say it in words
            // as well, so the grid is a nicety rather than the only copy of the answer.
            Text(
                "Each square is a day; the darker it is, the closer that day came to the goal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$active of the last $shownDays days have steps · $met met the ${goal.steps} goal",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun GoalCard(goal: StepGoal, onGoal: (Int) -> Unit) {
    // Where the thumb is right now, which is not the same thing as the goal that has been agreed.
    // Driving the slider straight off the committed goal meant every position a finger passed
    // through was committed: a single drag across the track raised ~29 separate goal changes.
    // Keyed on the goal so a change from anywhere else still moves the thumb.
    var position by remember(goal) { mutableFloatStateOf(goal.steps.toFloat()) }
    val shown = StepGoal.clamped(position.toInt())

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("Daily goal")
            // The label follows the finger, so the drag still reads as live even though only the
            // value it lands on is committed.
            Text("${shown.steps} steps", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = position,
                onValueChange = { position = it },
                onValueChangeFinished = { onGoal(position.toInt()) },
                valueRange = StepGoal.MIN.toFloat()..StepGoal.MAX.toFloat(),
                steps = 28,
                modifier = Modifier.semantics {
                    contentDescription = "Daily step goal"
                    stateDescription = "${shown.steps} steps"
                },
            )
        }
    }
}
