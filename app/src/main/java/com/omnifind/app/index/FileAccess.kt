package com.omnifind.app.index

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Reading the files someone chooses. Up to Android 10 that's READ_EXTERNAL_STORAGE (on the Mi A1's Android 9 it covers
 * all shared storage and SD cards). From Android 11 that permission no longer reaches documents and PDFs, so OmniFind
 * asks for "All files access" in Settings instead; it still only reads the folders chosen in the Index tab.
 */
object FileAccess {
    val needsSettings: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun granted(context: Context): Boolean =
        if (needsSettings) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    /** The Settings page where Android 11+ grants "All files access" to OmniFind. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            .takeIf { it.resolveActivity(context.packageManager) != null }
            ?: Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
}
