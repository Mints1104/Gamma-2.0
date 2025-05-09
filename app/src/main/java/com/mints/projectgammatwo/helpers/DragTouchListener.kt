package com.mints.projectgammatwo.helpers

import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class DragTouchListener(
    private val params: WindowManager.LayoutParams,
    private val windowManager: WindowManager,
    private val rootView: View
): View.OnTouchListener {

    private var initialX = 0
    private var initialY = 0
    private var downRawX = 0f
    private var downRawY = 0f

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d("DragTouch", "DOWN at (${event.rawX},${event.rawY})")
                initialX = params.x; initialY = params.y
                downRawX = event.rawX; downRawY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                Log.d("DragTouch", "MOVE to (${event.rawX},${event.rawY})")
                val deltaX = (event.rawX - downRawX).toInt()
                val deltaY = (event.rawY - downRawY).toInt()
                params.x = initialX + deltaX
                params.y = initialY + deltaY
                windowManager.updateViewLayout(rootView, params)
                return true
            }
        }
        return false
    }

}