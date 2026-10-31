import java.util.concurrent.atomic.AtomicReference;
import javax.sound.sampled.*;

/** Independent streamed music and a short Clip, using only the public sound API. */
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
        boolean audible = args.length > 0 && args[0].equals("audible");
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
}
