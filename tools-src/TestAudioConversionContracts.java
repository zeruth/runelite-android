// host-contract: args=build/compat-tests/awt/audio-conversion-reference.txt patched-args=shadow
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import javax.sound.sampled.*;

/**
 * Format conversion and the WAVE/AU/AIFF readers and writers, byte for byte against the JDK.
 *
 * <p>Every outcome (whether a conversion is offered, the CRC and length of what it produces, the
 * files the writers emit and what the readers make of them) is one line keyed by the case. The
 * plain-JDK run writes the lines to the file named by the first argument; the patched run, where
 * the ported {@code com.sun.media.sound} providers stand in for the JDK's, computes them again and
 * must reproduce every line. Both runs also hold the invariants a correct port cannot miss.
 */
public final class TestAudioConversionContracts {
    static final int FRAMES = 1102;
    static final AudioFormat.Encoding SIGNED = AudioFormat.Encoding.PCM_SIGNED;
    static final AudioFormat.Encoding UNSIGNED = AudioFormat.Encoding.PCM_UNSIGNED;
    static final AudioFormat.Encoding FLOAT = AudioFormat.Encoding.PCM_FLOAT;
    static final AudioFormat.Encoding ULAW = AudioFormat.Encoding.ULAW;
    static final AudioFormat.Encoding ALAW = AudioFormat.Encoding.ALAW;

    static final Map<String, String> results = new LinkedHashMap<>();

    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path reference = Paths.get(args[0]);
        boolean shadow = args.length > 1 && "shadow".equals(args[1]);
        List<AudioFormat> formats = formats();
        encodings(formats);
        matrix(formats);
        files(formats);
        invariants();
        if (!shadow) {
            Files.createDirectories(reference.toAbsolutePath().getParent());
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, String> e : results.entrySet()) lines.add(e.getKey() + " = " + e.getValue());
            Files.write(reference, lines, StandardCharsets.UTF_8);
            System.out.println("TestAudioConversionContracts recorded " + lines.size() + " JDK outcomes in " + reference);
        } else {
            check(Files.isRegularFile(reference), "no JDK reference at " + reference + " (the plain-JDK run writes it)");
            Map<String, String> expected = new LinkedHashMap<>();
            for (String line : Files.readAllLines(reference, StandardCharsets.UTF_8)) {
                int at = line.indexOf(" = ");
                expected.put(line.substring(0, at), line.substring(at + 3));
            }
            List<String> differences = new ArrayList<>();
            for (Map.Entry<String, String> e : expected.entrySet()) {
                String actual = results.get(e.getKey());
                if (!e.getValue().equals(actual)) differences.add(e.getKey() + ": JDK " + e.getValue() + ", shadow " + actual);
            }
            for (String key : results.keySet()) {
                if (!expected.containsKey(key)) differences.add(key + ": only in the shadow run");
            }
            for (String d : differences.subList(0, Math.min(40, differences.size()))) System.out.println("DIFF " + d);
            check(differences.isEmpty(), differences.size() + " of " + expected.size() + " outcomes differ from the JDK");
            System.out.println("TestAudioConversionContracts matched " + expected.size() + " JDK outcomes");
        }
    }

    // Fixtures.

    static AudioFormat pcm(AudioFormat.Encoding encoding, float rate, int bits, int channels, boolean bigEndian) {
        return new AudioFormat(encoding, rate, bits, channels, (bits + 7) / 8 * channels, rate, bigEndian);
    }

    static List<AudioFormat> formats() {
        List<AudioFormat> list = new ArrayList<>();
        for (AudioFormat.Encoding encoding : new AudioFormat.Encoding[]{SIGNED, UNSIGNED}) {
            for (int bits : new int[]{8, 16, 24, 32}) {
                for (int channels = 1; channels <= 2; channels++) {
                    list.add(pcm(encoding, 22050f, bits, channels, false));
                    if (bits > 8) list.add(pcm(encoding, 22050f, bits, channels, true));
                }
            }
        }
        for (int channels = 1; channels <= 2; channels++) {
            list.add(pcm(FLOAT, 22050f, 32, channels, false));
            list.add(pcm(FLOAT, 22050f, 32, channels, true));
            list.add(pcm(ULAW, 22050f, 8, channels, false));
            list.add(pcm(ALAW, 22050f, 8, channels, false));
            // sample-rate conversion: the same layout at other rates
            list.add(pcm(SIGNED, 44100f, 16, channels, false));
            list.add(pcm(SIGNED, 11025f, 16, channels, false));
            list.add(pcm(SIGNED, 48000f, 16, channels, true));
        }
        return list;
    }

    /** A two-tone signal with a little seeded noise, so every sample size and sign carries real content. */
    static byte[] clip(AudioFormat format) {
        int bits = format.getSampleSizeInBits();
        int bytes = bits / 8;
        int channels = format.getChannels();
        byte[] data = new byte[FRAMES * format.getFrameSize()];
        long seed = 0x5DEECE66DL ^ (bits * 31 + channels);
        int at = 0;
        for (int frame = 0; frame < FRAMES; frame++) {
            for (int c = 0; c < channels; c++) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                double noise = ((seed >>> 40) / (double) (1L << 24) - 0.5) * 0.05;
                double v = 0.6 * Math.sin(2 * Math.PI * 440 * (c + 1) * frame / format.getSampleRate())
                    + 0.3 * Math.sin(2 * Math.PI * 97 * frame / format.getSampleRate()) + noise;
                v = Math.max(-1, Math.min(0.999, v));
                AudioFormat.Encoding encoding = format.getEncoding();
                if (encoding.equals(ULAW) || encoding.equals(ALAW)) {
                    data[at++] = (byte) (seed >>> 56);
                    continue;
                }
                long sample;
                if (encoding.equals(FLOAT)) {
                    sample = Float.floatToIntBits((float) v) & 0xFFFFFFFFL;
                } else {
                    sample = (long) (v * ((1L << (bits - 1)) - 1));
                    if (encoding.equals(UNSIGNED)) sample += 1L << (bits - 1);
                }
                for (int b = 0; b < bytes; b++) {
                    int shift = format.isBigEndian() ? (bytes - 1 - b) * 8 : b * 8;
                    data[at++] = (byte) (sample >>> shift);
                }
            }
        }
        return data;
    }

    static AudioInputStream stream(AudioFormat format) {
        return new AudioInputStream(new ByteArrayInputStream(clip(format)), format, FRAMES);
    }

    static byte[] readAll(AudioInputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4800]; // a multiple of every frame size in the matrix
        for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    static String key(AudioFormat f) {
        return f.getEncoding() + "/" + f.getSampleRate() + "/" + f.getSampleSizeInBits() + "/" + f.getChannels()
            + "/" + f.getFrameSize() + "/" + f.getFrameRate() + "/" + (f.isBigEndian() ? "be" : "le");
    }

    static String digest(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length);
        return data.length + ":" + Long.toHexString(crc.getValue());
    }

    static void record(String key, String value) {
        check(results.put(key, value) == null, "duplicate case " + key);
    }

    // Cases.

    static void encodings(List<AudioFormat> formats) {
        for (AudioFormat.Encoding encoding : new AudioFormat.Encoding[]{SIGNED, UNSIGNED, FLOAT, ULAW, ALAW}) {
            record("targetEncodings(" + encoding + ")", sorted(AudioSystem.getTargetEncodings(encoding)));
        }
        for (AudioFormat source : formats) {
            record("targetEncodings(" + key(source) + ")", sorted(AudioSystem.getTargetEncodings(source)));
            for (AudioFormat.Encoding encoding : new AudioFormat.Encoding[]{SIGNED, UNSIGNED, FLOAT, ULAW, ALAW}) {
                List<String> targets = new ArrayList<>();
                for (AudioFormat f : AudioSystem.getTargetFormats(encoding, source)) targets.add(key(f));
                Collections.sort(targets);
                record("targetFormats(" + encoding + ", " + key(source) + ")", targets.toString());
            }
        }
    }

    static String sorted(Object[] values) {
        List<String> names = new ArrayList<>();
        for (Object value : values) names.add(String.valueOf(value));
        Collections.sort(names);
        return names.toString();
    }

    static void matrix(List<AudioFormat> formats) throws Exception {
        for (AudioFormat source : formats) {
            for (AudioFormat target : formats) {
                String name = "convert(" + key(source) + " -> " + key(target) + ")";
                if (!AudioSystem.isConversionSupported(target, source)) {
                    record(name, "unsupported");
                    continue;
                }
                AudioInputStream converted = AudioSystem.getAudioInputStream(target, stream(source));
                record(name, key(converted.getFormat()) + " " + digest(readAll(converted))
                    + " frames " + converted.getFrameLength());
            }
            for (AudioFormat.Encoding encoding : new AudioFormat.Encoding[]{SIGNED, UNSIGNED, FLOAT, ULAW, ALAW}) {
                String name = "convert(" + key(source) + " -> " + encoding + ")";
                if (!AudioSystem.isConversionSupported(encoding, source)) {
                    record(name, "unsupported");
                    continue;
                }
                AudioInputStream converted = AudioSystem.getAudioInputStream(encoding, stream(source));
                record(name, key(converted.getFormat()) + " " + digest(readAll(converted)));
            }
        }
    }

    static void files(List<AudioFormat> formats) throws Exception {
        AudioFileFormat.Type[] types = {AudioFileFormat.Type.WAVE, AudioFileFormat.Type.AU, AudioFileFormat.Type.AIFF};
        record("fileTypes", sorted(AudioSystem.getAudioFileTypes()));
        File temp = File.createTempFile("audio-conversion", ".bin");
        temp.deleteOnExit();
        for (AudioFormat source : formats) {
            record("fileTypes(" + key(source) + ")", sorted(AudioSystem.getAudioFileTypes(stream(source))));
            for (AudioFileFormat.Type type : types) {
                String name = type + "(" + key(source) + ")";
                if (!AudioSystem.isFileTypeSupported(type, stream(source))) {
                    record(name, "unsupported");
                    continue;
                }
                ByteArrayOutputStream file = new ByteArrayOutputStream();
                int written = AudioSystem.write(stream(source), type, file);
                byte[] bytes = file.toByteArray();
                check(written == bytes.length, name + " write returned " + written + " for " + bytes.length + " bytes");
                AudioFileFormat header = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(bytes));
                AudioInputStream back = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes));
                byte[] samples = readAll(back);
                check(header.getType().equals(type), name + " reads back as " + header.getType());
                if (back.getFormat().matches(source)) {
                    check(Arrays.equals(samples, clip(source)), name + " round trip changed the samples");
                }
                record(name, digest(bytes) + " header " + header.getType() + " " + key(header.getFormat())
                    + " frames " + header.getFrameLength() + " bytes " + header.getByteLength()
                    + " read " + key(back.getFormat()) + " " + back.getFrameLength() + " " + digest(samples));

                int toFile = AudioSystem.write(stream(source), type, temp);
                AudioFileFormat fileHeader = AudioSystem.getAudioFileFormat(temp);
                check(fileHeader.getType().equals(type), name + " File header type " + fileHeader.getType());
                AudioInputStream fromFile = AudioSystem.getAudioInputStream(temp);
                try {
                    record(name + " File", toFile + " " + digest(Files.readAllBytes(temp.toPath()))
                        + " frames " + fileHeader.getFrameLength() + " read " + digest(readAll(fromFile)));
                } finally {
                    fromFile.close();
                }
            }
        }
        temp.delete();
    }

    /** What any faithful port holds, checked in both runs before the byte comparison. */
    static void invariants() throws Exception {
        AudioFormat le16 = pcm(SIGNED, 22050f, 16, 2, false);
        AudioFormat be16 = pcm(SIGNED, 22050f, 16, 2, true);
        check(AudioSystem.isConversionSupported(be16, le16), "16-bit byte swap");
        byte[] swapped = readAll(AudioSystem.getAudioInputStream(be16, stream(le16)));
        byte[] original = clip(le16);
        for (int i = 0; i < original.length; i += 2) {
            check(swapped[i] == original[i + 1] && swapped[i + 1] == original[i], "byte swap at " + i);
        }
        check(AudioSystem.isConversionSupported(pcm(SIGNED, 44100f, 16, 2, false), le16), "resample 22050 -> 44100");
        check(AudioSystem.isConversionSupported(SIGNED, pcm(ULAW, 8000f, 8, 1, false)), "ULAW decode");
        check(AudioSystem.isConversionSupported(ALAW, pcm(SIGNED, 8000f, 16, 1, false)), "ALAW encode");
        check(AudioSystem.isConversionSupported(pcm(SIGNED, 22050f, 16, 1, false), le16), "stereo -> mono");
        check(!AudioSystem.isConversionSupported(new AudioFormat.Encoding("MPEG1L3"), le16), "no MP3 encoder");
        try {
            AudioSystem.getAudioInputStream(new ByteArrayInputStream(new byte[64]));
            throw new AssertionError("zeros read as an audio file");
        } catch (UnsupportedAudioFileException expected) {
            // no container claims it
        }
        try {
            AudioSystem.getAudioInputStream(new AudioFormat.Encoding("MPEG1L3"), stream(le16));
            throw new AssertionError("unsupported conversion returned a stream");
        } catch (IllegalArgumentException expected) {
            // as the JDK reports it
        }
    }
}
