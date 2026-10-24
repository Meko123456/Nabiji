package io.github.meko123456.nabiji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import io.github.meko123456.nabiji.ui.dashboard.DashboardScreen
import io.github.meko123456.nabiji.ui.theme.NabijiTheme
import io.github.meko123456.nabiji.widget.WidgetPreviews
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch { WidgetPreviews.publish(applicationContext) }
        setContent { NabijiTheme { DashboardScreen() } }
    }
}
