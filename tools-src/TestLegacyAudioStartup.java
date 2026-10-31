import android.media.AudioTrack;
import java.lang.reflect.Field;
import java.util.Arrays;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import net.runelite.awt.impl.AndroidAudioBridge;

/** Device contracts with silent PCM by default; "audible" plays a quiet three-note phrase. */
public final class TestLegacyAudioStartup {
    private static final AudioFormat FORMAT = new AudioFormat(22050, 16, 1, true, false);
    private static int passed;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Object slot(SourceDataLine line) throws Exception {
        Class<?> type = line.getClass();
        while (type != null) {
            try { type.getDeclaredField("slot"); break; }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        Field field = type.getDeclaredField("slot");
        field.setAccessible(true);
        return field.get(line);
    }

    private static AudioTrack track(SourceDataLine line) throws Exception {
        Object slot = slot(line);
        Field field = slot.getClass().getDeclaredField("track");
        field.setAccessible(true);
        return (AudioTrack) field.get(slot);
    }

    private static SourceDataLine open() throws Exception {
        SourceDataLine line = AndroidAudioBridge.openSourceDataLine(FORMAT, 8192);
        line.open();
        return line;
    }

    private static void played(SourceDataLine line, long expected) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (line.getLongFramePosition() < expected && System.nanoTime() < deadline) Thread.sleep(2);
        check(line.getLongFramePosition() == expected,
            "frame accounting expected=" + expected + " actual=" + line.getLongFramePosition());
    }

    private static void pass(String name) {
        passed++;
        System.out.println("PASS " + name);
    }

    private static void shortWrites() throws Exception {
        for (int frames : new int[]{128, 256, 1024, 2048}) {
            SourceDataLine line = open();
            try {
                int original = line.getBufferSize();
                line.start();
                check(line.write(new byte[frames * 2], 0, frames * 2) == frames * 2, "full short write");
                check(line.getBufferSize() == original, "logical buffer remains stable while priming");
                played(line, frames);
                check(track(line).getBufferSizeInFrames() * 2 == original, "native buffer restored after progress");
                check(line.available() == original, "empty line capacity is unchanged");
                System.out.println("SHORT frames=" + frames + " nativeBuffer=" + track(line).getBufferSizeInFrames());
            } finally { line.close(); }
            pass("short block " + frames);
        }
    }

    private static void flushAndUnderrun() throws Exception {
        SourceDataLine line = open();
        try {
            line.start();
            for (int round = 1; round <= 4; round++) {
                line.write(new byte[512], 0, 512);
                played(line, round * 256);
                Thread.sleep(120);
            }
            System.out.println("UNDERRUNS count=" + track(line).getUnderrunCount());
            line.flush();
            long position = line.getLongFramePosition();
            line.write(new byte[256], 0, 256);
            played(line, position + 128);
        } finally { line.close(); }
        pass("short writes recover after starvation and flush");
    }

    private static void pauseResume() throws Exception {
        SourceDataLine line = open();
        try {
            int frames = line.getBufferSize() / 2;
            line.write(new byte[frames * 2], 0, frames * 2);
            line.start();
            line.stop();
            // Allow any already-issued mixer work to settle before checking the paused head.
            Thread.sleep(40);
            long position = line.getLongFramePosition();
            Thread.sleep(80);
            check(line.getLongFramePosition() == position, "pause preserves queued audio");
            line.start();
            played(line, frames);
        } finally { line.close(); }
        pass("pause and resume preserve pending samples");
    }

    private static void reopenAndMix() throws Exception {
        SourceDataLine first = open();
        AudioTrack nativeTrack;
        try {
            nativeTrack = track(first);
            first.start();
            first.write(new byte[256], 0, 256);
        } finally { first.close(); }
        SourceDataLine reopened = open();
        SourceDataLine other = open();
        try {
            check(track(reopened) == nativeTrack, "rapid reopen uses the same native track");
            check(track(other) != nativeTrack, "active lines remain independent");
            reopened.start(); other.start();
            reopened.write(new byte[512], 0, 512);
            other.write(new byte[1024], 0, 1024);
            played(reopened, 256);
            reopened.close();
            played(other, 512);
        } finally { reopened.close(); other.close(); }
        pass("rapid reopen and overlapping lines preserve accounting");
    }

    private static void stereo() throws Exception {
        SourceDataLine line = AndroidAudioBridge.openSourceDataLine(new AudioFormat(44100, 16, 2, true, false), 16384);
        try {
            line.open();
            int capacity = line.getBufferSize();
            line.start();
            line.write(new byte[1024], 0, 1024);
            played(line, 256);
            check(track(line).getBufferSizeInFrames() * 4 == capacity, "stereo buffer restored in frames");
        } finally { line.close(); }
        pass("stereo short-write frame accounting");
    }

    private static void continuous(boolean audible, int blocks) throws Exception {
        SourceDataLine line = open();
        try {
            byte[] pcm = new byte[256 * 2];
            long cpu = android.os.Debug.threadCpuTimeNanos();
            long started = System.nanoTime();
            int underruns = track(line).getUnderrunCount();
            line.start();
            System.out.println("STREAM_BEGIN audible=" + audible);
            for (int block = 0; block < blocks; block++) {
                if (audible) {
                    double frequency = new double[]{440, 660, 880}[(block / 86) % 3];
                    for (int i = 0; i < 256; i++) {
                        short value = (short) (2000 * Math.sin(2 * Math.PI * frequency * (block * 256 + i) / 22050));
                        pcm[i * 2] = (byte) value;
                        pcm[i * 2 + 1] = (byte) (value >> 8);
                    }
                }
                check(line.write(pcm, 0, pcm.length) == pcm.length, "stream write accepted in full");
            }
            played(line, blocks * 256L);
            long wall = System.nanoTime() - started;
            long used = android.os.Debug.threadCpuTimeNanos() - cpu;
            System.out.println("METRICS sdk=" + android.os.Build.VERSION.SDK_INT + " frames=" + blocks * 256
                + " wallMs=" + wall / 1_000_000 + " cpuUs=" + used / 1000
                + " underruns=" + (track(line).getUnderrunCount() - underruns));
        } finally { line.close(); }
        pass("continuous PCM playback");
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("perf")) {
            continuous(false, 260);
            return;
        }
        if (Arrays.asList(args).contains("audible")) {
            continuous(true, 260);
            return;
        }
        if (Arrays.asList(args).contains("background")) {
            continuous(false, 2600);
            return;
        }
        shortWrites();
        stereo();
        flushAndUnderrun();
        pauseResume();
        reopenAndMix();
        continuous(false, 260);
        System.out.println("RESULT legacy audio passed=" + passed + " failed=0");
    }
}
