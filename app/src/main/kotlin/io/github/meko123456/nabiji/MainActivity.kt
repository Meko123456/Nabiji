package io.github.meko123456.nabiji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.meko123456.nabiji.ui.theme.NabijiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NabijiTheme { Placeholder() } }
    }
}

/** Skeleton screen; replaced by the activity dashboard in #4. */
@Composable
private fun Placeholder() {
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text("Nabiji", style = MaterialTheme.typography.displaySmall)
            Text("ნაბიჯი", style = MaterialTheme.typography.titleMedium)
        }
    }
}
