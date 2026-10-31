import javax.sound.sampled.*;
import net.runelite.awt.impl.AndroidAudioBridge;

/** Deterministic native-mutation trace, excluding polling counts and scheduling-dependent reads. */
public final class TestAudioModernParity {
    private static void played(SourceDataLine line, int frames) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (line.getLongFramePosition() < frames && System.nanoTime() < deadline) Thread.sleep(2);
        if (line.getLongFramePosition() != frames) throw new AssertionError("audio stalled");
    }
    public static void main(String[] args) throws Exception {
        SourceDataLine line = AndroidAudioBridge.openSourceDataLine(new AudioFormat(22050, 16, 1, true, false), 8192);
        try {
            line.open(); line.start();
            if (android.os.Build.VERSION.SDK_INT <= 30) {
                AudioTrackTrace.rejectNextResize = true;
                try { line.write(new byte[512], 0, 512); throw new AssertionError("resize rejection was hidden"); }
                catch (IllegalStateException expected) { }
            } else {
                line.write(new byte[4096], 0, 4096); played(line, 2048);
                line.stop(); line.flush(); line.start();
                line.write(new byte[4096], 0, 4096); played(line, 4096);
            }
        } finally { line.close(); }
        if (android.os.Build.VERSION.SDK_INT <= 30) {
            SourceDataLine replacement = AndroidAudioBridge.openSourceDataLine(new AudioFormat(22050, 16, 1, true, false), 8192);
            try {
                replacement.open(); replacement.start();
                replacement.write(new byte[512], 0, 512); played(replacement, 256);
            } finally { replacement.close(); }
            System.out.println("PASS rejected track is not returned to the reusable pool");
            SourceDataLine throwing = AndroidAudioBridge.openSourceDataLine(new AudioFormat(22050, 16, 1, true, false), 8192);
            try {
                throwing.open(); throwing.start();
                AudioTrackTrace.throwNextResize = true;
                try { throwing.write(new byte[512], 0, 512); throw new AssertionError("native exception was hidden"); }
                catch (IllegalStateException expected) { }
            } finally { throwing.close(); }
            System.out.println("PASS thrown resize error still permits safe close");
        }
        Thread.sleep(2300);
        System.out.println("NATIVE_TRACE " + AudioTrackTrace.snapshot());
        if (android.os.Build.VERSION.SDK_INT >= 31 && AudioTrackTrace.legacyCalls() != 0)
            throw new AssertionError("legacy native calls reached modern Android");
        System.out.println("PASS native audio path sdk=" + android.os.Build.VERSION.SDK_INT);
    }
}
