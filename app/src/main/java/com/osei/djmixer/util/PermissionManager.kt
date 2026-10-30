package com.osei.djmixer.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Utility to manage and evaluate Android runtime permissions cleanly across OS versions.
 *
 * Separates media storage access (audio/video/external storage) from notification permissions,
 * ensuring that POST_NOTIFICATIONS denial never blocks local media library access.
 */
object PermissionManager {

    data class MediaPermissionStatus(
        val hasAudioAccess: Boolean,
        val hasVideoAccess: Boolean,
        val hasAnyMediaAccess: Boolean,
        val isFullyGranted: Boolean
    )

    /**
     * Returns the required media permissions for local storage access based on API level.
     * Android 13+ (API 33, Tiramisu): READ_MEDIA_AUDIO and READ_MEDIA_VIDEO.
     * Android 12 and below: READ_EXTERNAL_STORAGE.
     */
    fun getRequiredMediaPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> {
        return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Evaluates a permission grant map to determine audio, video, partial, and full media access.
     * Completely decouples media access decisions from POST_NOTIFICATIONS status.
     */
    fun evaluateMediaPermissions(
        grantMap: Map<String, Boolean>,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): MediaPermissionStatus {
        return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            val audio = grantMap[Manifest.permission.READ_MEDIA_AUDIO] == true
            val video = grantMap[Manifest.permission.READ_MEDIA_VIDEO] == true
            MediaPermissionStatus(
                hasAudioAccess = audio,
                hasVideoAccess = video,
                hasAnyMediaAccess = audio || video,
                isFullyGranted = audio && video
            )
        } else {
            val storage = grantMap[Manifest.permission.READ_EXTERNAL_STORAGE] == true
            MediaPermissionStatus(
                hasAudioAccess = storage,
                hasVideoAccess = storage,
                hasAnyMediaAccess = storage,
                isFullyGranted = storage
            )
        }
    }

    /**
     * Determines whether media scanning should proceed given a permission result map.
     * Returns true if any accessible media permission is granted, regardless of notification status.
     */
    fun shouldScanMedia(
        grantMap: Map<String, Boolean>,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        return evaluateMediaPermissions(grantMap, sdkInt).hasAnyMediaAccess
    }

    /**
     * Checks current media permissions status using the given Android Context.
     */
    fun checkMediaPermissionStatus(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): MediaPermissionStatus {
        val permissions = getRequiredMediaPermissions(sdkInt)
        val grantMap = permissions.associateWith { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        return evaluateMediaPermissions(grantMap, sdkInt)
    }

    /**
     * Determines whether POST_NOTIFICATIONS is required and ungranted on this device.
     * Only applies to Android 13+ (API 33+).
     */
    fun isNotificationPermissionNeeded(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }
}
