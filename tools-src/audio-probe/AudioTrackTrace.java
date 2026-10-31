import android.media.AudioTrack;
import java.util.ArrayList;
import java.util.List;

/** Test bytecode redirects native calls here; this class never ships in the app. */
public final class AudioTrackTrace {
    private static final List<String> calls = new ArrayList<>();
    private static int legacyCalls;
    public static boolean rejectNextResize;
    public static boolean throwNextResize;
    private static synchronized void record(String call) { calls.add(call); }
    public static synchronized String snapshot() { return calls.toString(); }
    public static synchronized int legacyCalls() { return legacyCalls; }
    public static int getPlaybackHeadPosition(AudioTrack track) { return track.getPlaybackHeadPosition(); }
    public static int getBufferSizeInFrames(AudioTrack track) {
        int result = track.getBufferSizeInFrames(); record("buffer:" + result); return result;
    }
    public static int getUnderrunCount(AudioTrack track) {
        synchronized (AudioTrackTrace.class) { legacyCalls++; }
        return track.getUnderrunCount();
    }
    public static int setStartThresholdInFrames(AudioTrack track, int frames) {
        int result = track.setStartThresholdInFrames(frames); record("threshold:" + frames + ":" + result); return result;
    }
    public static int setBufferSizeInFrames(AudioTrack track, int frames) {
        synchronized (AudioTrackTrace.class) { legacyCalls++; }
        if (throwNextResize) { throwNextResize = false; record("throw:" + frames); throw new IllegalStateException("Synthetic native resize failure"); }
        if (rejectNextResize) { rejectNextResize = false; record("reject:" + frames); return AudioTrack.ERROR_BAD_VALUE; }
        int result = track.setBufferSizeInFrames(frames); record("resize:" + frames + ":" + result); return result;
    }
    public static int write(AudioTrack track, byte[] bytes, int offset, int length, int mode) {
        int result = track.write(bytes, offset, length, mode);
        if (result > 0) record("write:" + length + ":" + result);
        return result;
    }
    public static void play(AudioTrack track) { record("play"); track.play(); }
    public static void pause(AudioTrack track) { record("pause"); track.pause(); }
    public static void flush(AudioTrack track) { record("flush"); track.flush(); }
    public static void stop(AudioTrack track) { record("stop"); track.stop(); }
    public static void release(AudioTrack track) { record("release"); track.release(); }
}
