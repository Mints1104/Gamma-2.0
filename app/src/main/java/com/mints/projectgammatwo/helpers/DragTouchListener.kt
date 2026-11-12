package com.mints.projectgammatwo.helpers

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class DragTouchListener(
    private val params: WindowManager.LayoutParams,
    private val windowManager: WindowManager,
    private val rootView: View,
    private val onLongPress: (() -> Unit)? = null
) : View.OnTouchListener {

    private var initialX = 0
    private var initialY = 0
    private var downRawX = 0f
    private var downRawY = 0f
    private var hasMoved = false

    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (!hasMoved) {
            Log.d("DragTouch", "Long press detected!")
            onLongPress?.invoke()
        }
    }

    companion object {
        private const val LONG_PRESS_TIMEOUT = 500L // 500ms for long press
        private const val MOVE_THRESHOLD = 10 // pixels
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d("DragTouch", "DOWN at (${event.rawX}, ${event.rawY})")
                initialX = params.x
                initialY = params.y
                downRawX = event.rawX
                downRawY = event.rawY
                hasMoved = false

                // Start long press detection
                longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = (event.rawX - downRawX).toInt()
                val deltaY = (event.rawY - downRawY).toInt()

                // Check if user has moved beyond threshold
                if (Math.abs(deltaX) > MOVE_THRESHOLD || Math.abs(deltaY) > MOVE_THRESHOLD) {
                    hasMoved = true
                    longPressHandler.removeCallbacks(longPressRunnable)

                    Log.d("DragTouch", "MOVE to (${event.rawX}, ${event.rawY})")
                    // Calculate desired new position
                    val newX = initialX + deltaX
                    val newY = initialY + deltaY

                    // Keep the window on screen. x/y are offsets from wherever the window's
                    // gravity anchors it: the main overlay's top-left corner, but the centre for
                    // the favorites and filter panels, which a [0, screen - size] clamp let slide
                    // half off-screen. coerceIn also threw when the window was larger than the
                    // screen (an empty range); onScreenOffsetRange never returns one.
                    val dm = rootView.resources.displayMetrics
                    val gravity = params.gravity
                    params.x = newX.coerceIn(
                        onScreenOffsetRange(
                            dm.widthPixels, rootView.width,
                            centered = (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL
                        )
                    )
                    params.y = newY.coerceIn(
                        onScreenOffsetRange(
                            dm.heightPixels, rootView.height,
                            centered = (gravity and Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL
                        )
                    )

                    // Apply update
                    windowManager.updateViewLayout(rootView, params)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Cancel long press if finger is lifted
                longPressHandler.removeCallbacks(longPressRunnable)
                return true
            }
        }
        return false
    }

    /** Expose current overlay X (left) */
    fun getCurrentParamsX(): Int = params.x

    /** Expose current overlay Y (top) */
    fun getCurrentParamsY(): Int = params.y

}

/**
 * The window offsets along one axis that keep a window of size [view] on a screen of size
 * [screen]: measured from the start edge, or from the centre when the window is [centered].
 * A window larger than the screen gets the single offset that pins it to the start edge (or
 * centres it), never an empty range.
 */
fun onScreenOffsetRange(screen: Int, view: Int, centered: Boolean): IntRange {
    val slack = (screen - view).coerceAtLeast(0)
    return if (centered) -(slack / 2)..(slack / 2) else 0..slack
}
