package com.shivam.tvlauncher

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RowKind { Pinned, All }

@Immutable
data class LauncherUi(
    val pinned: List<LaunchableApp>,
    val all: List<LaunchableApp>,
    val hidden: List<LaunchableApp>,
) {
    fun row(kind: RowKind): List<LaunchableApp> = if (kind == RowKind.Pinned) pinned else all
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val prefsStore = LauncherPrefsStore(application)
    private val installed = MutableStateFlow<List<LaunchableApp>?>(null)

    val ui: StateFlow<LauncherUi?> = combine(installed, prefsStore.prefs) { apps, prefs ->
        apps?.let { arrange(it, prefs) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var refreshJob: Job? = null

    /** Re-reads installed apps; cheap after the first run because card art is cached. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            installed.value = withContext(Dispatchers.IO) { loadLaunchableApps(getApplication()) }
        }
    }

    fun pin(app: LaunchableApp) = edit { it.copy(pinned = it.pinned - app.packageName + app.packageName) }

    fun unpin(app: LaunchableApp) = edit { it.copy(pinned = it.pinned - app.packageName) }

    fun hide(app: LaunchableApp) = edit {
        it.copy(hidden = it.hidden + app.packageName, pinned = it.pinned - app.packageName)
    }

    fun unhide(app: LaunchableApp) = edit { it.copy(hidden = it.hidden - app.packageName) }

    /** Swaps [app] with its neighbour in [row]; the row's visible order becomes the saved order. */
    fun move(app: LaunchableApp, row: RowKind, delta: Int) {
        val order = ui.value?.row(row)?.map { it.packageName }?.toMutableList() ?: return
        val from = order.indexOf(app.packageName)
        val to = from + delta
        if (from < 0 || to !in order.indices) return
        order[from] = order[to].also { order[to] = order[from] }
        edit { if (row == RowKind.Pinned) it.copy(pinned = order) else it.copy(allOrder = order) }
    }

    private fun edit(transform: (LauncherPrefs) -> LauncherPrefs) {
        viewModelScope.launch { prefsStore.update(transform) }
    }
}

private fun arrange(apps: List<LaunchableApp>, prefs: LauncherPrefs): LauncherUi {
    val byPackage = apps.associateBy { it.packageName }
    val (hidden, visible) = apps.partition { it.packageName in prefs.hidden }
    val rank = prefs.allOrder.withIndex().associate { (index, pkg) -> pkg to index }
    return LauncherUi(
        // Pins of uninstalled apps stay saved, so they come back if the app is reinstalled.
        pinned = prefs.pinned.mapNotNull { byPackage[it] }.filter { it.packageName !in prefs.hidden },
        // Stable sort: unranked apps (e.g. new installs) keep alphabetical order at the end.
        all = visible.sortedBy { rank[it.packageName] ?: Int.MAX_VALUE },
        hidden = hidden,
    )
}
