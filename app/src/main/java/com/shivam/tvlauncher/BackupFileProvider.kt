package com.shivam.tvlauncher

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Lets adb copy the backup file in and out:
 *
 *   adb shell content read  --uri content://com.shivam.tvlauncher.backup/launcher-backup.json > launcher-backup.json
 *   adb shell content write --uri content://com.shivam.tvlauncher.backup/launcher-backup.json < launcher-backup.json
 *
 * Fire OS blocks adb from Android/data, and reading a file adb put in Download would need a
 * storage permission. This serves exactly one file, and only to the adb shell (or root): every
 * other caller, including other apps, is refused. It never imports anything itself; the
 * launcher's Import button validates the file first.
 */
class BackupFileProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val uid = Binder.getCallingUid()
        if (uid != SHELL_UID && uid != ROOT_UID) throw SecurityException("Only adb may use this.")
        if (uri.pathSegments != listOf(LauncherBackup.FILE_NAME)) throw FileNotFoundException(uri.toString())
        val file = LauncherBackup.file(context ?: throw FileNotFoundException("No context"))
            ?: throw FileNotFoundException("Storage isn't available")
        return if (mode == "r") {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            file.parentFile?.mkdirs()
            ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
            )
        }
    }

    override fun getType(uri: Uri): String = "application/json"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    private companion object {
        // android.os.Process.SHELL_UID / ROOT_UID are hidden API; the values are fixed.
        const val SHELL_UID = 2000
        const val ROOT_UID = 0
    }
}
