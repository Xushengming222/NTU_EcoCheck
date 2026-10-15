package com.ntu.electricity.payment

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import kotlinx.coroutines.suspendCancellableCoroutine

/** PixelCopy includes the hardware-rendered WebView content; no screen-capture permission. */
internal object PaymentCapture {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    suspend fun capture(view: View): Bitmap? = suspendCancellableCoroutine { pending ->
        val activity = view.context.activity()
        val decor = activity?.window?.decorView
        if (activity == null || decor == null || !view.isAttachedToWindow || !view.isShown) {
            pending.resume(null) {}
            return@suspendCancellableCoroutine
        }
        // Wait for the next frame after the status text changed, so layout is up to date.
        view.postOnAnimation {
            if (!pending.isActive) return@postOnAnimation
            val position = IntArray(2)
            view.getLocationInWindow(position)
            val area = Rect(position[0], position[1], position[0] + view.width, position[1] + view.height)
            if (!area.intersect(0, 0, decor.width, decor.height) || area.isEmpty) {
                pending.resume(null) {}
                return@postOnAnimation
            }
            val scale = minOf(1f, 1920f / area.width(), 3200f / area.height())
            val bitmap = Bitmap.createBitmap((area.width() * scale).toInt().coerceAtLeast(1),
                (area.height() * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(activity.window, area, bitmap, { result ->
                    if (!pending.isActive || result != PixelCopy.SUCCESS) {
                        bitmap.recycle()
                        if (pending.isActive) pending.resume(null) {}
                    } else {
                        pending.resume(bitmap) { bitmap.recycle() }
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (_: IllegalArgumentException) {
                bitmap.recycle()
                if (pending.isActive) pending.resume(null) {}
            }
        }
    }

    private fun Context.activity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> if (baseContext !== this) baseContext.activity() else null
        else -> null
    }
}
