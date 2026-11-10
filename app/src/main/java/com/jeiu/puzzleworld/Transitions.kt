package com.jeiu.puzzleworld

import android.app.Activity
import android.app.ActivityOptions

/**
 * Standard fade-in / fade-out activity transitions for consistent UI flow.
 */
object Transitions {
    fun applyFade(activity: Activity) {
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
