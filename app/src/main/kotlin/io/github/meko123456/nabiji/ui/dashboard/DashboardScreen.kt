package io.github.meko123456.nabiji.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.meko123456.heatmap.ContributionHeatmap
import io.github.meko123456.heatmap.GithubGreens
import io.github.meko123456.heatmap.GithubLightGreens
import io.github.meko123456.nabiji.data.GoalRepository
import io.github.meko123456.nabiji.data.HealthConnectSource
import io.github.meko123456.nabiji.domain.ActivitySummary
import io.github.meko123456.nabiji.domain.DayActivity
import io.github.meko123456.nabiji.domain.HealthAvailability
import io.github.meko123456.nabiji.domain.StepGoal
import java.time.LocalDate

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

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Nabiji", style = MaterialTheme.typography.titleLarge)
                    Text("ნაბიჯი", style = MaterialTheme.typography.labelMedium)
                }
            })
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
                    body = "Health Connect has nothing for the last year. Once your phone or watch records " +
                        "some steps they'll show up here.",
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

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** One honest explanation per non-ready state, with a way forward where one exists. */
@Composable
private fun Explain(title: String, body: String, action: Pair<String, () -> Unit>? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.let { (label, onClick) ->
                Button(onClick = onClick, modifier = Modifier.padding(top = 4.dp)) { Text(label) }
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
    val spoken = "Today: ${steps} steps, ${goal.percent(steps)} percent of your ${goal.steps} step goal" +
        if (state.streak > 0) ", ${state.streak} day streak" else ""
    Card(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("$steps", style = MaterialTheme.typography.displayMedium)
            Text(
                "of ${goal.steps} steps · ${goal.percent(steps)}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.LinearProgressIndicator(
                progress = { goal.progress(steps) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            val remaining = goal.remaining(steps)
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("This week", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
    val active = counts.size
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Last 26 weeks", style = MaterialTheme.typography.titleSmall)
            ContributionHeatmap(
                counts = counts,
                endDay = LocalDate.now().toEpochDay(),
                weeks = 26,
                // Absolute scale: the goal is the darkest level, so shading means the same
                // thing every week instead of drifting with the busiest day on screen.
                maxCount = goal.steps,
                levelColors = if (isSystemInDarkTheme()) GithubGreens else GithubLightGreens,
                contentDescription = "Activity heatmap: $active of the last 182 days with recorded steps",
            )
        }
    }
}

@Composable
private fun GoalCard(goal: StepGoal, onGoal: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Daily goal", style = MaterialTheme.typography.titleSmall)
            Text("${goal.steps} steps", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = goal.steps.toFloat(),
                onValueChange = { onGoal(it.toInt()) },
                valueRange = StepGoal.MIN.toFloat()..StepGoal.MAX.toFloat(),
                steps = 28,
                modifier = Modifier.semantics {
                    contentDescription = "Daily step goal"
                    stateDescription = "${goal.steps} steps"
                },
            )
        }
    }
}
