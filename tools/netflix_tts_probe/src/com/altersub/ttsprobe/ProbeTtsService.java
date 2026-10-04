package com.altersub.ttsprobe;

import android.media.AudioFormat;
import android.os.SystemClock;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Silent, standalone feasibility probe. It records only direct Netflix requests. */
public final class ProbeTtsService extends TextToSpeechService {
    private final byte[] silence = new byte[320];
    private volatile boolean stopped;

    @Override public void onCreate() {
        super.onCreate();
        DiagLog.d(this, "engine-created");
    }

    @Override protected String[] onGetLanguage() { return new String[] {"eng", "USA", ""}; }
    @Override protected int onIsLanguageAvailable(String language, String country, String variant) {
        if (country == null || country.isEmpty()) return TextToSpeech.LANG_AVAILABLE;
        if (variant == null || variant.isEmpty()) return TextToSpeech.LANG_COUNTRY_AVAILABLE;
        return TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
    }
    @Override protected int onLoadLanguage(String language, String country, String variant) {
        return onIsLanguageAvailable(language, country, variant);
    }
    @Override protected void onStop() { stopped = true; }

    @Override protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        stopped = false;
        int uid = request.getCallerUid();
        String[] packages = getPackageManager().getPackagesForUid(uid);
        boolean netflix = false;
        if (packages != null) {
            for (String name : packages) netflix |= "com.netflix.ninja".equals(name);
        }
        if (netflix) {
            try {
                String text = request.getCharSequenceText().toString();
                boolean truncated = text.length() > 8192;
                if (truncated) text = text.substring(0, 8192);
                JSONObject record = new JSONObject()
                        .put("elapsedMs", SystemClock.elapsedRealtime())
                        .put("callerUid", uid).put("package", "com.netflix.ninja")
                        .put("text", text).put("truncated", truncated)
                        .put("language", request.getLanguage()).put("voice", request.getVoiceName());
                File target = new File(getFilesDir(), "netflix-utterances.jsonl");
                if (target.length() < 262144) {
                    try (FileOutputStream output = new FileOutputStream(target, true)) {
                        output.write((record.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                    DiagLog.d(this, record.toString());
                }
            } catch (Exception e) {
                DiagLog.d(this, "record-error " + e.getClass().getSimpleName());
            }
        } else {
            // Validate that requests arrive without recording any other app's spoken strings.
            DiagLog.d(this, "ignored-caller uid=" + uid);
        }
        // Complete the ordinary synthesis protocol with 10 ms of silent PCM.
        if (callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1) == TextToSpeech.SUCCESS) {
            if (!stopped) callback.audioAvailable(silence, 0, silence.length);
            callback.done();
        } else {
            callback.error();
        }
    }
}
