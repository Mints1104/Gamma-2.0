package com.mints.projectgammatwo.helpers

import android.os.Handler
import android.os.Looper
import android.util.Log
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

                    // Get screen & overlay dimensions
                    val dm = rootView.resources.displayMetrics
                    val maxX = dm.widthPixels - rootView.width
                    val maxY = dm.heightPixels - rootView.height

                    // Clamp to [0..max]
                    params.x = newX.coerceIn(0, maxX)
                    params.y = newY.coerceIn(0, maxY)

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
