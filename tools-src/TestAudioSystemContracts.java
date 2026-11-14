// host-contract
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import javax.sound.sampled.*;

/** AudioSystem's mixer/line/control surface and the pure-Java data classes, as the JDK reports them. */
public final class TestAudioSystemContracts {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static final AudioFormat CD = new AudioFormat(44100f, 16, 2, true, false);

    public static void main(String[] args) throws Exception {
        constants();
        matching();
        mixers();
        lines();
        files();
        System.out.println("TestAudioSystemContracts OK");
    }

    static void constants() {
        check(AudioSystem.NOT_SPECIFIED == -1, "NOT_SPECIFIED");
        check("PCM_SIGNED".equals(AudioFormat.Encoding.PCM_SIGNED.toString()), "PCM_SIGNED name");
        check("PCM_UNSIGNED".equals(AudioFormat.Encoding.PCM_UNSIGNED.toString()), "PCM_UNSIGNED name");
        check("PCM_FLOAT".equals(AudioFormat.Encoding.PCM_FLOAT.toString()), "PCM_FLOAT name");
        check("ULAW".equals(AudioFormat.Encoding.ULAW.toString()), "ULAW name");
        check("ALAW".equals(AudioFormat.Encoding.ALAW.toString()), "ALAW name");
        check(new AudioFormat.Encoding("PCM_SIGNED").equals(AudioFormat.Encoding.PCM_SIGNED), "encoding equality by name");
        check("WAVE".equals(AudioFileFormat.Type.WAVE.toString()) && "wav".equals(AudioFileFormat.Type.WAVE.getExtension()), "WAVE type");
        check("AU".equals(AudioFileFormat.Type.AU.toString()) && "au".equals(AudioFileFormat.Type.AU.getExtension()), "AU type");
        check("AIFF".equals(AudioFileFormat.Type.AIFF.toString()) && "aif".equals(AudioFileFormat.Type.AIFF.getExtension()), "AIFF type");
        check("AIFF-C".equals(AudioFileFormat.Type.AIFC.toString()) && "aifc".equals(AudioFileFormat.Type.AIFC.getExtension()), "AIFF-C type");
        check("SND".equals(AudioFileFormat.Type.SND.toString()) && "snd".equals(AudioFileFormat.Type.SND.getExtension()), "SND type");
        check("Master Gain".equals(FloatControl.Type.MASTER_GAIN.toString()), "MASTER_GAIN name");
        check("Pan".equals(FloatControl.Type.PAN.toString()), "PAN name");
        check("Balance".equals(FloatControl.Type.BALANCE.toString()), "BALANCE name");
        check("Volume".equals(FloatControl.Type.VOLUME.toString()), "VOLUME name");
        check("Mute".equals(BooleanControl.Type.MUTE.toString()), "MUTE name");
        check("Open".equals(LineEvent.Type.OPEN.toString()) && "Close".equals(LineEvent.Type.CLOSE.toString())
            && "Start".equals(LineEvent.Type.START.toString()) && "Stop".equals(LineEvent.Type.STOP.toString()), "LineEvent type names");
        check(!Port.Info.SPEAKER.isSource() && Port.Info.MICROPHONE.isSource(), "port directions");
        check(Port.Info.SPEAKER.getLineClass() == Port.class, "port line class");
    }

    static void matching() {
        AudioFormat anyRate = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, AudioSystem.NOT_SPECIFIED, 16, 2, 4,
            AudioSystem.NOT_SPECIFIED, false);
        check(CD.matches(anyRate), "concrete format matches a wildcard rate");
        check(!anyRate.matches(CD), "wildcards in the receiver do not match concrete values");
        check(!CD.matches(new AudioFormat(44100f, 16, 2, true, true)), "byte order matters for 16-bit samples");
        check(new AudioFormat(8000f, 8, 1, true, false).matches(new AudioFormat(8000f, 8, 1, true, true)), "byte order ignored for 8-bit");

        DataLine.Info supported = new DataLine.Info(SourceDataLine.class, new AudioFormat[]{anyRate},
            AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED);
        DataLine.Info request = new DataLine.Info(SourceDataLine.class, CD);
        check(request.matches(supported), "request matches the supported line");
        check(!new DataLine.Info(Clip.class, CD).matches(supported), "a Clip request is not met by a SourceDataLine");
        check(new Line.Info(DataLine.class).matches(supported), "a DataLine request is met by a SourceDataLine");
        check(!request.matches(new Line.Info(SourceDataLine.class)), "a DataLine.Info request needs a DataLine.Info");
        check(!new DataLine.Info(SourceDataLine.class, CD, 8192).matches(
            new DataLine.Info(SourceDataLine.class, new AudioFormat[]{anyRate}, 0, 4096)), "buffer above the supported maximum");
        check(supported.isFormatSupported(CD) && !supported.isFormatSupported(new AudioFormat(44100f, 8, 2, true, false)),
            "isFormatSupported");
    }

    static void mixers() throws Exception {
        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        check(infos.length > 0, "at least one mixer");
        boolean source = false;
        for (Mixer.Info info : infos) {
            check(info.getName() != null && info.getVendor() != null, "mixer info fields");
            Mixer mixer = AudioSystem.getMixer(info);
            check(mixer.getMixerInfo().equals(info), "getMixer returns the named mixer");
            if (mixer.isLineSupported(new DataLine.Info(SourceDataLine.class, CD))) source = true;
        }
        check(source, "a mixer offers a CD-format SourceDataLine");
        check(AudioSystem.getSourceLineInfo(new Line.Info(SourceDataLine.class)).length > 0, "source line infos");
    }

    static void lines() throws Exception {
        check(AudioSystem.isLineSupported(new DataLine.Info(SourceDataLine.class, CD)), "SourceDataLine supported");
        check(AudioSystem.isLineSupported(new DataLine.Info(Clip.class, CD)), "Clip supported");
        check(AudioSystem.isLineSupported(new Line.Info(SourceDataLine.class)), "plain SourceDataLine supported");
        AudioFormat bogus = new AudioFormat(new AudioFormat.Encoding("NO_SUCH_ENCODING"), 44100f, 16, 2, 4, 44100f, false);
        check(!AudioSystem.isLineSupported(new DataLine.Info(SourceDataLine.class, bogus)), "unknown encoding unsupported");
        boolean thrown = false;
        try {
            AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, bogus));
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        check(thrown, "an unsupported line request is an IllegalArgumentException");

        SourceDataLine line = AudioSystem.getSourceDataLine(CD);
        check(line != null && !line.isOpen(), "getSourceDataLine returns a closed line");
        line.open(CD, 8820);
        try {
            check(line.isOpen(), "line opens");
            check(line.getFormat().matches(CD), "line format");
            check(line.isControlSupported(FloatControl.Type.MASTER_GAIN), "MASTER_GAIN on a line");
            check(line.isControlSupported(BooleanControl.Type.MUTE), "MUTE on a line");
            check(line.isControlSupported(FloatControl.Type.BALANCE) || line.isControlSupported(FloatControl.Type.PAN),
                "BALANCE or PAN on a stereo line");
            FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            check(gain.getMinimum() < 0 && gain.getMaximum() >= 0 && "dB".equals(gain.getUnits()), "gain range in dB");
            gain.setValue(-6f);
            check(Math.abs(gain.getValue() + 6f) < 0.5f, "gain keeps its value");
            BooleanControl mute = (BooleanControl) line.getControl(BooleanControl.Type.MUTE);
            mute.setValue(true);
            check(mute.getValue(), "mute keeps its value");
            mute.setValue(false);
            check(Arrays.asList(line.getControls()).contains(gain), "getControls lists the gain");
            // Started, and less than the buffer, so the JDK's blocking write returns.
            line.start();
            byte[] silence = new byte[1000 * 4];
            check(line.write(silence, 0, silence.length) == silence.length, "write accepts whole frames");
        } finally {
            line.close();
        }
        check(!line.isOpen(), "line closes");

        Clip clip = AudioSystem.getClip();
        byte[] tone = new byte[4410 * 4];
        clip.open(CD, tone, 0, tone.length);
        try {
            check(clip.isOpen() && clip.getFrameLength() == 4410, "clip opens with its frames");
            check(clip.isControlSupported(FloatControl.Type.MASTER_GAIN), "MASTER_GAIN on a clip");
            check(clip.isControlSupported(BooleanControl.Type.MUTE), "MUTE on a clip");
        } finally {
            clip.close();
        }
    }

    static void files() throws Exception {
        check(AudioSystem.isFileTypeSupported(AudioFileFormat.Type.WAVE), "WAVE writable");
        check(Arrays.asList(AudioSystem.getAudioFileTypes()).contains(AudioFileFormat.Type.WAVE), "WAVE listed");
        check(AudioSystem.isConversionSupported(AudioFormat.Encoding.ULAW, CD), "PCM to ULAW");
        check(Arrays.asList(AudioSystem.getTargetEncodings(AudioFormat.Encoding.PCM_SIGNED)).contains(AudioFormat.Encoding.PCM_UNSIGNED),
            "PCM_SIGNED converts to PCM_UNSIGNED");

        byte[] pcm = new byte[1000 * 4];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (byte) i;
        AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(pcm), CD, 1000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int written = AudioSystem.write(stream, AudioFileFormat.Type.WAVE, out);
        check(written == out.size() && written >= pcm.length + 44, "write reports the file length");
        AudioFileFormat format = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(out.toByteArray()));
        check(AudioFileFormat.Type.WAVE.equals(format.getType()), "written file reads back as WAVE");
        check(format.getFrameLength() == 1000 && format.getFormat().matches(CD), "written file keeps frames and format");
        AudioInputStream back = AudioSystem.getAudioInputStream(new ByteArrayInputStream(out.toByteArray()));
        byte[] read = new byte[pcm.length];
        int total = 0;
        for (int n; total < read.length && (n = back.read(read, total, read.length - total)) > 0; ) total += n;
        check(total == pcm.length && Arrays.equals(read, pcm), "samples round-trip through WAVE");
    }
}
