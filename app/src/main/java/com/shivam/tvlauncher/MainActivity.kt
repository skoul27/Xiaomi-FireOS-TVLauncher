package com.shivam.tvlauncher

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LauncherScreen(onLaunch = ::launch)
                }
            }
        }
    }

    private fun launch(app: LaunchableApp) {
        try {
            startActivity(app.launchIntent())
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun LauncherScreen(onLaunch: (LaunchableApp) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }

    // Reload on every resume so installs/uninstalls show up when we come back.
    LifecycleResumeEffect(Unit) {
        val job = scope.launch {
            apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
        }
        onPauseOrDispose { job.cancel() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
        Text(
            text = "Apps",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        when (val list = apps) {
            null -> Text("Loading…")
            else -> AppGrid(list, onLaunch)
        }
    }
}

@Composable
private fun AppGrid(apps: List<LaunchableApp>, onLaunch: (LaunchableApp) -> Unit) {
    val firstItem = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 180.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(apps, key = { _, app -> app.component.flattenToString() }) { index, app ->
            AppCard(
                app = app,
                onClick = { onLaunch(app) },
                focusModifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
            )
        }
    }

    // Give the D-pad somewhere to start; only on first load so a resume keeps the user's place.
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty() && !focusedOnce) {
            focusedOnce = runCatching { firstItem.requestFocus() }.isSuccess
        }
    }
}

@Composable
private fun AppCard(app: LaunchableApp, onClick: () -> Unit, focusModifier: Modifier = Modifier) {
    Column {
        Card(
            onClick = onClick,
            modifier = focusModifier.fillMaxWidth().aspectRatio(16f / 9f),
        ) {
            if (app.banner != null) {
                Image(
                    bitmap = app.banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().background(Color(0xFF2A3442)),
                ) {
                    Image(
                        bitmap = app.icon,
                        contentDescription = app.label,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
