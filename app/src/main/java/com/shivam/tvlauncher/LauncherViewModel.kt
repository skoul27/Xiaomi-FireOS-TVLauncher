package com.shivam.tvlauncher

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class LauncherUi(
    val pinned: List<LaunchableApp>,
    val all: List<LaunchableApp>,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val _ui = MutableStateFlow<LauncherUi?>(null)
    val ui: StateFlow<LauncherUi?> = _ui.asStateFlow()

    private var refreshJob: Job? = null

    /** Re-reads installed apps; cheap after the first run because card art is cached. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { loadLaunchableApps(getApplication()) }
            // Provisional until pinning lands: the first few TV apps fill the Pinned row.
            _ui.value = LauncherUi(pinned = apps.filter { it.isTvApp }.take(5), all = apps)
        }
    }
}
