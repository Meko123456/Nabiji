package io.github.meko123456.nabiji.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.meko123456.nabiji.domain.StepGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nabiji")

/**
 * The user's daily step goal. Reads are clamped through [StepGoal.clamped], so a corrupt or
 * out-of-range stored value can never crash the dashboard or produce a nonsense ring.
 */
class GoalRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.dataStore)

    val goal: Flow<StepGoal> = store.data.map { prefs ->
        prefs[KEY]?.let { StepGoal.clamped(it) } ?: StepGoal.DEFAULT
    }

    suspend fun setGoal(goal: StepGoal) {
        store.edit { it[KEY] = goal.steps }
    }

    private companion object {
        val KEY = intPreferencesKey("daily_step_goal")
    }
}
