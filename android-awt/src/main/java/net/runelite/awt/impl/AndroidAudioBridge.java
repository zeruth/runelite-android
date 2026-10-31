package net.runelite.awt.impl;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/** Creates independent PCM lines with safe native-track reuse during rapid reopen. */
public final class AndroidAudioBridge {
    private AndroidAudioBridge() {
    }

    public static SourceDataLine openSourceDataLine(AudioFormat format, int bufferSize) throws LineUnavailableException {
        if (format == null) {
            throw new LineUnavailableException("AudioFormat required");
        }
        if (android.os.Build.VERSION.SDK_INT >= 26 && android.os.Build.VERSION.SDK_INT <= 30) {
            return new LegacySourceDataLine(format, bufferSize);
        }
        return new PooledSourceDataLine(format, bufferSize);
    }
}
