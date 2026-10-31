package net.runelite.mp.audioprobe;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Visible only for the emulator background/resume contract. */
public final class AudioProbeActivity extends Activity {
    static volatile int resumes, pauses;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setText("Audio background/resume test");
        setContentView(text);
    }
    @Override public void onResume() { super.onResume(); resumes++; }
    @Override public void onPause() { pauses++; super.onPause(); }
}
