import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sound.sampled.*;

/**
 * Independent streamed music and a short Clip, using only the public sound API; then every
 * compressed fixture named as {@code path[=seconds]} (an .mp3, an .ogg) decoded the way the
 * C Engineer plugin does (issue #2) into a Clip that must play to completion with the duration
 * given, or, without one, the duration the decoded PCM implies.
 */
public final class TestAudioMusicAndEffect {
    private static final int RATE = 22050;
    private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);
    private static byte[] pcm(int frames, boolean audible, boolean effect) {
        byte[] data = new byte[frames * 2];
        if (audible) for (int i = 0; i < frames; i++) {
            int note = effect ? 1320 : new int[]{440, 660, 880}[Math.min(2, i / RATE)];
            int local = effect ? i : i % RATE;
            int duration = effect ? frames : RATE;
            double envelope = Math.min(1, Math.min(local, duration - local) / 220.0);
            short sample = (short) (1800 * envelope * Math.sin(2 * Math.PI * note * i / RATE));
            data[i * 2] = (byte) sample; data[i * 2 + 1] = (byte) (sample >> 8);
        }
        return data;
    }
    public static void main(String[] args) throws Exception {
        boolean audible = false;
        java.util.List<String> fixtures = new java.util.ArrayList<>();
        for (String arg : args) {
            if (arg.equals("audible")) audible = true;
            else fixtures.add(arg);
        }
        streamedAndClip(audible);
        for (String fixture : fixtures) compressed(fixture);
    }

    private static void streamedAndClip(boolean audible) throws Exception {
        SourceDataLine music = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, FORMAT, 8192));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread effect = new Thread(() -> {
            Clip clip = null;
            try {
                Thread.sleep(500);
                byte[] sound = pcm(RATE / 6, audible, true);
                clip = AudioSystem.getClip(); clip.open(FORMAT, sound, 0, sound.length); clip.start();
                long deadline = System.nanoTime() + 2_000_000_000L;
                while (clip.isRunning() && System.nanoTime() < deadline) Thread.sleep(5);
                if (clip.isRunning()) throw new AssertionError("Clip did not finish");
            } catch (Throwable error) { failure.set(error); }
            finally { if (clip != null) clip.close(); }
        }, "Audio-effect-contract");
        try {
            music.open(FORMAT, 8192); music.start(); effect.start();
            byte[] notes = pcm(RATE * 3, audible, false);
            if (music.write(notes, 0, notes.length) != notes.length) throw new AssertionError("Music write incomplete");
            long deadline = System.nanoTime() + 3_000_000_000L;
            while (music.getLongFramePosition() < RATE * 3 && System.nanoTime() < deadline) Thread.sleep(2);
            if (music.getLongFramePosition() != RATE * 3) throw new AssertionError("Music frame accounting changed");
            effect.join(2000);
            if (effect.isAlive() || failure.get() != null) throw new AssertionError("Effect failed", failure.get());
            System.out.println("PASS streamed music and overlapping Clip audible=" + audible + " frames=" + RATE * 3);
        } finally { music.close(); }
    }

    /** {@code path[=seconds]}: decode through AudioSystem as a hub plugin does, and play it out on a Clip. */
    private static void compressed(String spec) throws Exception {
        int eq = spec.lastIndexOf('=');
        String path = eq < 0 ? spec : spec.substring(0, eq);
        double expected = eq < 0 ? -1 : Double.parseDouble(spec.substring(eq + 1));
        String name = new File(path).getName().toLowerCase(java.util.Locale.ROOT);

        // A plain stream, not the file, so the decoder has to take the spooled-copy path.
        AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(new FileInputStream(path)));
        AudioFormat base = in.getFormat();
        String encoding = base.getEncoding().toString();
        if (name.endsWith(".mp3") && !encoding.startsWith("MPEG")) throw new AssertionError(name + " read as " + base);
        if (name.endsWith(".ogg") && !encoding.equals("VORBISENC") && !encoding.equals("OPUS")) {
            throw new AssertionError(name + " read as " + base);
        }
        if (base.getFrameSize() != AudioSystem.NOT_SPECIFIED) throw new AssertionError(name + ": encoded frame size " + base);
        AudioFormat decoded = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.getSampleRate(), 16,
            base.getChannels(), base.getChannels() * 2, base.getSampleRate(), false);
        AudioInputStream din = AudioSystem.getAudioInputStream(decoded, in);

        Clip clip = AudioSystem.getClip();
        CountDownLatch stopped = new CountDownLatch(1);
        clip.addLineListener(event -> { if (event.getType() == LineEvent.Type.STOP) stopped.countDown(); });
        try {
            clip.open(din);
            double seconds = clip.getMicrosecondLength() / 1e6;
            if (seconds <= 0) throw new AssertionError(name + ": decoded to no audio");
            if (expected > 0 && Math.abs(seconds - expected) > 0.1 + expected * 0.01) {
                throw new AssertionError(name + ": decoded " + seconds + " s, expected " + expected + " s");
            }
            long started = System.nanoTime();
            clip.start();
            if (!stopped.await((long) (seconds * 1000) + 2000, TimeUnit.MILLISECONDS)) {
                throw new AssertionError(name + ": did not play to completion in " + seconds + " s + 2 s");
            }
            double played = (System.nanoTime() - started) / 1e9;
            if (played < seconds * 0.9) throw new AssertionError(name + ": stopped after " + played + " s of " + seconds);
            if (clip.getFramePosition() != clip.getFrameLength()) {
                throw new AssertionError(name + ": stopped at frame " + clip.getFramePosition() + " of " + clip.getFrameLength());
            }

            // The same file by File and by encoding alone decodes to the same length.
            AudioInputStream again = AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED,
                AudioSystem.getAudioInputStream(new File(path)));
            long bytes = 0;
            byte[] buffer = new byte[16384];
            for (int n; (n = again.read(buffer)) >= 0; ) bytes += n;
            again.close();
            long frames = bytes / again.getFormat().getFrameSize();
            if (Math.abs(frames - clip.getFrameLength()) > 1) {
                throw new AssertionError(name + ": File decode gave " + frames + " frames, stream decode " + clip.getFrameLength());
            }
            System.out.println("PASS compressed " + name + " " + base + " -> " + seconds + " s, played " + played + " s");
        } finally {
            clip.close();
        }
    }
}
