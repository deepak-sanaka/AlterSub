package com.altersub.ttsprobe;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;

public final class SelfTestActivity extends Activity {
    private TextToSpeech tts;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        new Handler().postDelayed(this::finish, 90000);
        tts = new TextToSpeech(this, status -> runOnUiThread(() -> {
            DiagLog.d(this, "self-test-init status=" + status);
            if (status != TextToSpeech.SUCCESS) { finish(); return; }
            int languageStatus = tts.setLanguage(Locale.US);
            DiagLog.d(this, "self-test-language status=" + languageStatus + " voice=" + tts.getVoice());
            if (languageStatus < 0) { finish(); return; }
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { DiagLog.d(SelfTestActivity.this, "self-test-start"); }
                @Override public void onDone(String id) {
                    DiagLog.d(SelfTestActivity.this, "self-test-done");
                    runOnUiThread(() -> finish());
                }
                @Override public void onError(String id) {
                    DiagLog.d(SelfTestActivity.this, "self-test-error");
                    runOnUiThread(() -> finish());
                }
            });
            tts.speak("AlterSub engine self test", TextToSpeech.QUEUE_FLUSH, null, "self-test");
        }), getIntent().getStringExtra("engine") == null ? "com.altersub.ttsprobe" : getIntent().getStringExtra("engine"));
    }
    @Override protected void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
