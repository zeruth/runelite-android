import javax.sound.sampled.*;

/** Uses only the stable Java Sound API in a normal release APK, with no implementation overlay. */
public final class TestPackagedAudio {
    private static final AudioFormat FORMAT = new AudioFormat(22050, 16, 1, true, false);
    private static SourceDataLine open() throws Exception {
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, FORMAT, 8192));
        line.open(FORMAT, 8192);
        System.out.println("RELEASE line=" + line.getClass().getName());
        return line;
    }
    private static void played(SourceDataLine line, long frames) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (line.getLongFramePosition() < frames && System.nanoTime() < deadline) Thread.sleep(2);
        if (line.getLongFramePosition() != frames) throw new AssertionError("Expected " + frames + ", got " + line.getLongFramePosition());
    }
    public static void main(String[] args) throws Exception {
        for (int frames : new int[]{128, 256, 2048}) {
            SourceDataLine line = open();
            try {
                int capacity = line.getBufferSize();
                line.start();
                line.write(new byte[frames * 2], 0, frames * 2);
                played(line, frames);
                if (capacity != line.getBufferSize()) throw new AssertionError("Buffer contract changed");
            } finally { line.close(); }
            System.out.println("PASS release short write " + frames);
        }
        SourceDataLine line = open();
        try {
            int frames = line.getBufferSize() / 2;
            line.write(new byte[frames * 2], 0, frames * 2);
            line.start(); line.stop(); Thread.sleep(60);
            line.start(); played(line, frames);
            line.flush();
            long position = line.getLongFramePosition();
            line.write(new byte[256], 0, 256);
            played(line, position + 128);
        } finally { line.close(); }
        System.out.println("PASS release pause/resume/flush");
        System.out.println("RESULT packaged audio passed=4 failed=0");
    }
}
