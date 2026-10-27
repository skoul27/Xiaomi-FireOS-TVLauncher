package com.shivam.tvlauncher

import android.app.Application
import android.text.format.DateFormat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
        // Wait for the first-run pins so the first frame already shows them.
        apps?.takeIf { prefs.seeded }?.let { arrange(it, prefs) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Settings the whole screen depends on: theme, animations, the remote shortcut. */
    val settings: StateFlow<LauncherPrefs> = prefsStore.prefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherPrefs())

    private val _shortcuts = MutableStateFlow<List<SystemShortcut>>(emptyList())

    /** Settings screens that open on this device right now; re-checked on every refresh. */
    val shortcuts: StateFlow<List<SystemShortcut>> = _shortcuts

    /** The TV's HDMI ports, kept current as devices announce themselves or go. */
    val ports: StateFlow<List<HdmiPort>> = hdmiPortsFlow(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var refreshJob: Job? = null

    /** Re-reads installed apps; cheap after the first run because art is cached. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { loadLaunchableApps(getApplication()) }
            installed.value = apps
            _shortcuts.value = withContext(Dispatchers.IO) { availableSystemShortcuts(getApplication()) }
            val packages = apps.mapTo(HashSet()) { it.packageName }
            prefsStore.update {
                if (it.seeded) it else it.copy(pinned = (it.pinned + defaultPinsFor(packages)).distinct(), seeded = true)
            }
            // Pinned apps first: they are what the first frame shows.
            val pinned = prefsStore.prefs.first().pinned.withIndex().associate { (i, pkg) -> pkg to i }
            AppArtCache.prefetch(getApplication(), apps.sortedBy { pinned[it.packageName] ?: Int.MAX_VALUE })
        }
    }

    fun pin(app: LaunchableApp) = edit { it.copy(pinned = it.pinned - app.packageName + app.packageName) }

    fun unpin(app: LaunchableApp) = edit { it.copy(pinned = it.pinned - app.packageName) }

    fun hide(app: LaunchableApp) = edit {
        it.copy(hidden = it.hidden + app.packageName, pinned = it.pinned - app.packageName)
    }

    fun unhide(app: LaunchableApp) = edit { it.copy(hidden = it.hidden - app.packageName) }

    fun setShortcutKey(key: ColorKey?) = edit { it.copy(shortcutKey = key) }

    fun setTheme(name: String) = edit { it.copy(theme = name) }

    fun setAnimations(enabled: Boolean) = edit { it.copy(animations = enabled) }

    fun setHomeRedirect(enabled: Boolean) = edit { it.copy(homeRedirect = enabled) }

    fun setWallpaper(wallpaper: Wallpaper) = edit { it.copy(wallpaper = wallpaper.name) }

    private val _photoVersion = MutableStateFlow(0)

    /** Bumped each time a new photo is saved, so the screen reloads it. */
    val photoVersion: StateFlow<Int> = _photoVersion

    private var uploadServer: PhotoUploadServer? = null
    private val _photoUpload = MutableStateFlow<PhotoUpload?>(null)

    /** The "Choose a photo" screen's state; null while it's closed. */
    val photoUpload: StateFlow<PhotoUpload?> = _photoUpload

    /** Starts the phone-upload page; each photo sent is copied in and becomes the background. */
    fun startPhotoUpload() {
        if (uploadServer != null) return
        val app = getApplication<Application>()
        val server = PhotoUploadServer(
            viewModelScope,
            app.cacheDir,
            onEvent = { status -> _photoUpload.update { (it as? PhotoUpload.Ready)?.copy(status = status) ?: it } },
        ) { file ->
            val saved = WallpaperPhoto.save(app, android.net.Uri.fromFile(file))
            if (saved) {
                _photoVersion.value++
                prefsStore.update { it.copy(wallpaper = Wallpaper.Photo.name) }
            }
            saved
        }
        uploadServer = server
        viewModelScope.launch {
            val address = withContext(Dispatchers.IO) { lanAddress(app) }
            val port = address?.let { withContext(Dispatchers.IO) { server.start() } }
            // Closed again while the port was opening.
            if (uploadServer !== server) return@launch server.stop()
            _photoUpload.value = if (port != null) PhotoUpload.Ready("http://$address:$port/${server.token}") else PhotoUpload.Unavailable
        }
    }

    fun stopPhotoUpload() {
        uploadServer?.stop()
        uploadServer = null
        _photoUpload.value = null
    }

    override fun onCleared() = stopPhotoUpload()

    /** Automatic with no name is the default, so it isn't stored; a name survives cycling past Other. */
    fun setConsoleTile(inputId: String, tile: PortTile) = edit {
        val clean = tile.copy(name = cleanTileName(tile.name))
        it.copy(
            consoleTiles = if (clean == PortTile(TileMode.Auto)) it.consoleTiles - inputId else it.consoleTiles + (inputId to clean),
        )
    }

    /** Swaps [app] with its neighbour in [row]; the row's visible order becomes the saved order. */
    fun move(app: LaunchableApp, row: RowKind, delta: Int) {
        val order = ui.value?.row(row)?.map { it.packageName }?.toMutableList() ?: return
        val from = order.indexOf(app.packageName)
        val to = from + delta
        if (from < 0 || to !in order.indices) return
        order[from] = order[to].also { order[to] = order[from] }
        edit { if (row == RowKind.Pinned) it.copy(pinned = order) else it.copy(allOrder = order) }
    }

    private val _backup = MutableStateFlow<BackupState?>(null)

    /** Result of the last export/import, or an import waiting for confirmation. */
    val backup: StateFlow<BackupState?> = _backup

    fun exportBackup() {
        viewModelScope.launch {
            val file = LauncherBackup.file(getApplication())
            val prefs = prefsStore.prefs.first()
            val saved = file != null && withContext(Dispatchers.IO) {
                runCatching {
                    file.parentFile?.mkdirs()
                    // Write aside, then swap in, so a failure never leaves half a backup behind.
                    val temp = File(file.parentFile, "${file.name}.tmp")
                    temp.writeText(LauncherBackup.encode(prefs, System.currentTimeMillis()))
                    if (!temp.renameTo(file)) {
                        file.delete()
                        check(temp.renameTo(file))
                    }
                }.isSuccess
            }
            _backup.value = BackupState.Message(
                if (saved) {
                    "Saved ${prefs.pinned.size} My Apps, ${prefs.hidden.size} hidden, console tiles, theme and settings to ${file!!.path}"
                } else {
                    "Couldn't save the backup: storage isn't writable right now."
                },
            )
        }
    }

    /** Reads and validates the file; a valid one waits for [confirmImport]. Nothing changes yet. */
    fun checkImport() {
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) { LauncherBackup.read(LauncherBackup.file(getApplication())) }
            _backup.value = when (read) {
                is LauncherBackup.Read.Invalid -> BackupState.Message("Can't import: ${read.reason}")
                is LauncherBackup.Read.Valid -> BackupState.ConfirmImport(read.contents, summarize(read.contents))
            }
        }
    }

    fun confirmImport() {
        val pending = _backup.value as? BackupState.ConfirmImport ?: return
        viewModelScope.launch {
            prefsStore.update { pending.contents.apply(it) }
            _backup.value = BackupState.Message("Backup imported.")
        }
    }

    fun dismissBackup() {
        _backup.value = null
    }

    private fun summarize(contents: LauncherBackup.Contents): String {
        val installedNow = installed.value.orEmpty().mapTo(HashSet()) { it.packageName }
        val missing = (contents.pinned + contents.hidden).toSet().count { it !in installedNow }
        val date = contents.exportedAt?.let {
            val locale = Locale.getDefault()
            SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "dMMMyyyyHHmm"), locale).format(Date(it))
        }
        return buildString {
            append(if (date != null) "Backup from $date: " else "Backup: ")
            append("${contents.pinned.size} My Apps, ${contents.hidden.size} hidden")
            if (missing > 0) append(" ($missing not installed on this TV; kept for if they come back)")
            // Older backups have no console tiles; importing one leaves the current tiles alone.
            contents.consoleTiles?.let { append(", ${it.size} console tile${if (it.size == 1) "" else "s"}") }
            append(". Theme ${contents.theme.label}, animations ${if (contents.animations) "on" else "off"}, ")
            append("remote shortcut ${contents.shortcutKey?.label ?: "off"}. ")
            append("This replaces your current setup.")
        }
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
