package dev.uint.qrserv.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object ManifestUtils {
    /**
     * Whether this build's own manifest declares MANAGE_EXTERNAL_STORAGE at all
     * -- see StripPermissionTask in app/build.gradle.kts, in which case Direct
     * Access Mode can never work here regardless of Android version or runtime grant state.
     */
    fun hasManageExternalStorageInManifest(context: Context): Boolean {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        return packageInfo.requestedPermissions
            ?.contains("android.permission.MANAGE_EXTERNAL_STORAGE") == true
    }

    /**
     * Whether this build is capable of Direct Access Mode at all: on Android 10 and earlier
     * it doesn't need MANAGE_EXTERNAL_STORAGE to begin with, so the manifest check -- the only
     * thing that can disqualify a build (see [hasManageExternalStorageInManifest]) -- is skipped
     * entirely there.
     */
    fun isDirectAccessModeEligible(context: Context): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q || hasManageExternalStorageInManifest(context)
}
