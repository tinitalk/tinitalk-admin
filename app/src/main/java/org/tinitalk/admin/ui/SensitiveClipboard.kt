package org.tinitalk.admin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.annotation.MainThread
import java.util.UUID

internal object SensitiveClipboard {
    private const val CLEAR_DELAY_MILLIS = 60_000L
    private const val EXTRA_COPY_ID = "org.tinitalk.admin.extra.COPY_ID"
    private val handler = Handler(Looper.getMainLooper())
    private var pendingClear: Runnable? = null

    @MainThread
    fun copyToken(context: Context, token: String) {
        copy(context, ClipData.newPlainText("TiniTalk token", token))
    }

    @MainThread
    fun copy(context: Context, clip: ClipData) {
        val clipboard = context.applicationContext.getSystemService(ClipboardManager::class.java)
        val copyId = UUID.randomUUID().toString()
        clip.description.extras = (clip.description.extras ?: PersistableBundle()).apply {
            // The documented EXTRA_IS_SENSITIVE key also applies before API 33.
            putBoolean("android.content.extra.IS_SENSITIVE", true)
            putString(EXTRA_COPY_ID, copyId)
        }
        clipboard.setPrimaryClip(clip)

        pendingClear?.let(handler::removeCallbacks)
        pendingClear = null
        // Android 13+ provides its own clipboard expiration.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return

        val copiedAt = try {
            clipboard.primaryClipDescription
                ?.takeIf { it.extras?.getString(EXTRA_COPY_ID) == copyId }
                ?.timestamp ?: return
        } catch (_: SecurityException) {
            return
        }

        // Keep only metadata, not the token or Activity, while the timer is pending.
        val clear = Runnable {
            pendingClear = null
            try {
                val current = clipboard.primaryClipDescription
                if (current?.extras?.getString(EXTRA_COPY_ID) == copyId &&
                    current.timestamp == copiedAt
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            } catch (_: SecurityException) {
                // Best effort: Android can deny clipboard access while we are in the background.
            }
        }
        pendingClear = clear
        handler.postDelayed(clear, CLEAR_DELAY_MILLIS)
    }
}
