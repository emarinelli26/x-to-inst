package com.emarinelli.xtoinsta

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Hands the finished video to Instagram. Personal accounts can't be published to by third-party
 * apps, so Instagram opens with the video loaded and the user taps "Share".
 *
 * The dedicated Story/Reels intents require a Meta App ID (META_APP_ID in gradle.properties).
 * Without one we use Instagram's generic share sheet, where the user picks Reel or Story.
 */
object InstagramSharer {
    private const val INSTAGRAM = "com.instagram.android"
    private const val MIME = "video/mp4"

    enum class Target { REEL, STORY }

    fun share(activity: Activity, video: Uri, target: Target) {
        val appId = BuildConfig.META_APP_ID
        if (appId.isNotBlank()) {
            val intent = when (target) {
                Target.STORY -> Intent("com.instagram.share.ADD_TO_STORY")
                    .putExtra("source_application", appId)
                Target.REEL -> Intent("com.instagram.share.ADD_TO_REEL")
                    .putExtra("com.facebook.platform.extra.APPLICATION_ID", appId)
            }.apply {
                setDataAndType(video, MIME)
                setPackage(INSTAGRAM)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.grantUriPermission(INSTAGRAM, video, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(intent)
                return
            }
        }
        shareGeneric(activity, video)
    }

    private fun shareGeneric(activity: Activity, video: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, video)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            activity.startActivity(Intent(send).setPackage(INSTAGRAM))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, "Instagram no está instalado", Toast.LENGTH_SHORT).show()
            activity.startActivity(Intent.createChooser(send, null))
        }
    }
}
