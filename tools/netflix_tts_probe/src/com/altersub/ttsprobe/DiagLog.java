package com.altersub.ttsprobe;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

/** This standalone diagnostic APK has no release variant and never ships in AlterSub. */
final class DiagLog {
    static void d(Context context, String message) {
        if ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.d("AlterSubDiag", "TTS_PROBE " + message);
        }
    }
}
