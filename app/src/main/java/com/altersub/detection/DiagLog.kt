package com.altersub.detection

import android.util.Log
import com.altersub.BuildConfig

/**
 * Detection diagnostics for device testing: which apps publish media sessions and what metadata they carry,
 * and what text the accessibility scraper saw. Debug builds only. This includes other apps' screen text, so
 * release builds must not write it to logcat; R8 removes these calls there entirely.
 *
 * Filter with: adb logcat -s AlterSubDiag
 */
object DiagLog {
    const val TAG = "AlterSubDiag"

    inline fun d(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }
}
