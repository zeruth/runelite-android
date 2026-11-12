// host-contract: runs=patched
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import net.runelite.awt.impl.CompressedAudio;
import net.runelite.awt.impl.CompressedAudioCodec;
import net.runelite.awt.impl.CompressedAudioFileReader;

/**
 * The compressed-audio reader and decoder's contract, away from MediaCodec: synthetic MP3, Ogg
 * (Vorbis, Opus), FLAC, ADTS and MP4 headers are recognised with the format desktop mp3spi and
 * vorbisspi report (codec encoding, frame size NOT_SPECIFIED); other data is refused with the
 * stream left where it was; and the plugin pattern {@code getAudioInputStream(pcm, encoded)} hands
 * the decoder the whole container (the file in place, or a spooled copy for a plain stream that
 * is deleted on close) and delivers its PCM in the requested layout. The Android half (real
 * MediaCodec decoding to a Clip) is TestAudioMusicAndEffect with .mp3/.ogg fixtures on a device.
 */
public final class TestCompressedAudioContracts {
    static final int NS = AudioSystem.NOT_SPECIFIED;
    static final CompressedAudioFileReader READER = new CompressedAudioFileReader();
    static final CompressedAudioCodec CODEC = new CompressedAudioCodec();

    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        byte[] mp3 = mpegFrames(0xFFFB9040, 417, 3);
        byte[] mp3Tagged = concat(id3(100), mp3);
        byte[] mp2Mono = mpegFrames(0xFFF380C0, 208, 3);
        byte[] vorbis = oggVorbis(2, 44100);
        byte[] opus = oggOpus(1);
        byte[] flac = flac(48000, 2, 16, 96000);
        byte[] adts = adts(4, 2, 3);
        byte[] m4a = concat(box("ftyp", ascii("M4A "), new byte[4], ascii("isom")), moov(2, 44100));
        byte[] m4aTrailing = concat(box("ftyp", ascii("M4A "), new byte[4]), u32(1 << 30), ascii("mdat"), new byte[64]);

        expect("mp3", mp3, "MP3", "MPEG1L3", 44100, 2, NS, 44100f / 1152);
        expect("mp3 after ID3v2", mp3Tagged, "MP3", "MPEG1L3", 44100, 2, NS, 44100f / 1152);
        expect("mpeg-2 layer III mono", mp2Mono, "MP3", "MPEG2L3", 22050, 1, NS, 22050f / 576);
        expect("ogg vorbis", vorbis, "OGG", "VORBISENC", 44100, 2, NS, NS);
        expect("ogg opus", opus, "OGG", "OPUS", 48000, 1, NS, NS);
        expect("flac", flac, "FLAC", "FLAC", 48000, 2, 16, NS);
        expect("adts", adts, "AAC", "AAC", 44100, 2, NS, 44100f / 1024);
        expect("m4a", m4a, "M4A", "AAC", 44100, 2, NS, NS);
        expect("m4a with trailing moov", m4aTrailing, "M4A", "AAC", NS, NS, NS, NS);
        AudioFileFormat flacFile = READER.getAudioFileFormat(new ByteArrayInputStream(flac));
        check(Long.valueOf(2000000L).equals(flacFile.getProperty("duration")), "FLAC duration from STREAMINFO: "
            + flacFile.getProperty("duration"));

        byte[] broken = mp3.clone();
        broken[417] = 0;
        refuse("mpeg sync without a second frame", broken);
        refuse("wave", concat(ascii("RIFF"), u32(36), ascii("WAVEfmt "), new byte[40]));
        refuse("random", random(4096));
        refuse("empty", new byte[0]);
        refuse("ID3v2 over noise", concat(id3(20), random(8192)));
        try {
            READER.getAudioInputStream(new FilterInputStream(new ByteArrayInputStream(mp3)) {
                @Override public boolean markSupported() { return false; }
            });
            throw new AssertionError("a stream without mark/reset was sniffed");
        } catch (IOException expected) {
            // the JDK's readers refuse such a stream the same way
        }

        decoding(mp3, vorbis);
        System.out.println("PASS TestCompressedAudioContracts");
    }

    static void expect(String name, byte[] data, String type, String encoding, float rate, int channels, int bits,
                       float frameRate) throws Exception {
        MarkedStream in = new MarkedStream(data);
        AudioFileFormat file = READER.getAudioFileFormat(in);
        check(in.position() == 0, name + ": sniffing moved the stream to " + in.position());
        AudioFormat f = file.getFormat();
        check(file.getType().toString().equals(type), name + ": type " + file.getType());
        check(f.getEncoding().toString().equals(encoding), name + ": encoding " + f.getEncoding());
        check(f.getSampleRate() == rate, name + ": rate " + f.getSampleRate());
        check(f.getChannels() == channels, name + ": channels " + f.getChannels());
        check(f.getSampleSizeInBits() == bits, name + ": bits " + f.getSampleSizeInBits());
        check(f.getFrameSize() == NS, name + ": frame size must be NOT_SPECIFIED, was " + f.getFrameSize());
        check(Math.abs(f.getFrameRate() - frameRate) < 1e-3, name + ": frame rate " + f.getFrameRate());
        AudioInputStream encoded = READER.getAudioInputStream(new ByteArrayInputStream(data));
        check(encoded.getFrameLength() == NS, name + ": encoded length must be NOT_SPECIFIED");
        check(Arrays.equals(readAll(encoded), data), name + ": the encoded stream must be the whole file");
    }

    static void refuse(String name, byte[] data) throws Exception {
        MarkedStream in = new MarkedStream(data);
        try {
            READER.getAudioInputStream(in);
            throw new AssertionError(name + ": accepted as " + READER.getAudioFileFormat(new ByteArrayInputStream(data)));
        } catch (UnsupportedAudioFileException expected) {
            check(in.position() == 0, name + ": a refused stream must be reset, at " + in.position());
        }
    }

    /** A decoder standing in for MediaCodec: checks what it is handed and returns a known PCM ramp. */
    static final class FakeDecoder implements CompressedAudio.Decoder {
        static final int FRAMES = 1000;
        File file;
        boolean temporary;
        boolean floats;
        byte[] seen;

        @Override
        public AudioInputStream decode(File source, boolean owned, AudioFormat encoded) throws IOException {
            this.file = source;
            this.temporary = owned;
            this.seen = Files.readAllBytes(source.toPath());
            AudioFormat pcm = floats
                ? new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 44100, 32, 2, 8, 44100, false)
                : new AudioFormat(44100, 16, 2, true, false);
            byte[] data = floats ? toFloat(pcm()) : pcm();
            return new AudioInputStream(new ByteArrayInputStream(data), pcm, NS) {
                @Override
                public void close() throws IOException {
                    super.close();
                    if (owned) source.delete();
                }
            };
        }

        static byte[] toFloat(byte[] pcm16) {
            byte[] out = new byte[pcm16.length * 2];
            for (int i = 0, o = 0; i < pcm16.length; i += 2, o += 4) {
                short s = (short) ((pcm16[i] & 0xFF) | (pcm16[i + 1] << 8));
                int bits = Float.floatToIntBits(s / 32768f);
                out[o] = (byte) bits; out[o + 1] = (byte) (bits >> 8);
                out[o + 2] = (byte) (bits >> 16); out[o + 3] = (byte) (bits >> 24);
            }
            return out;
        }

        static byte[] pcm() {
            byte[] out = new byte[FRAMES * 4];
            for (int i = 0; i < FRAMES; i++) {
                short left = (short) (i * 7), right = (short) (-i * 3);
                out[i * 4] = (byte) left; out[i * 4 + 1] = (byte) (left >> 8);
                out[i * 4 + 2] = (byte) right; out[i * 4 + 3] = (byte) (right >> 8);
            }
            return out;
        }
    }

    static void decoding(byte[] mp3, byte[] vorbis) throws Exception {
        CompressedAudio.setDecoder(null);
        AudioFormat mp3Format = READER.getAudioFileFormat(new ByteArrayInputStream(mp3)).getFormat();
        check(CODEC.getTargetEncodings(mp3Format).length == 0, "nothing is offered without a platform decoder");

        FakeDecoder fake = new FakeDecoder();
        CompressedAudio.setDecoder(fake);
        try {
            check(Arrays.asList(CODEC.getTargetEncodings(mp3Format)).contains(AudioFormat.Encoding.PCM_SIGNED),
                "MPEG1L3 decodes to PCM_SIGNED");
            check(CODEC.getTargetEncodings(new AudioFormat(44100, 16, 2, true, false)).length == 0, "PCM is not decoded");

            // The C Engineer pattern: PCM at the base format's rate and channels, from a plain stream.
            AudioInputStream in = READER.getAudioInputStream(new ByteArrayInputStream(mp3));
            AudioFormat base = in.getFormat();
            AudioFormat decoded = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.getSampleRate(), 16,
                base.getChannels(), base.getChannels() * 2, base.getSampleRate(), false);
            check(CODEC.isConversionSupported(decoded, base), "the plugin's PCM target is offered");
            AudioInputStream din = CODEC.getAudioInputStream(decoded, in);
            check(din.getFormat().matches(decoded), "decoded format " + din.getFormat());
            check(Arrays.equals(readAll(din), FakeDecoder.pcm()), "the decoder's PCM is delivered unchanged");
            check(fake.temporary && Arrays.equals(fake.seen, mp3), "a plain stream is spooled whole to a temporary file");
            din.close();
            check(!fake.file.exists(), "the spooled copy is deleted on close");

            // A File (and a file: URL) is decoded in place.
            File file = File.createTempFile("compressed-contract", ".ogg");
            try {
                Files.write(file.toPath(), vorbis);
                AudioInputStream fromFile = READER.getAudioInputStream(file);
                check(fromFile.getFormat().getEncoding().toString().equals("VORBISENC"), "file encoding");
                readAll(CODEC.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, fromFile));
                check(!fake.temporary && fake.file.equals(file), "a File is decoded in place, not copied");
                AudioInputStream fromUrl = READER.getAudioInputStream(file.toURI().toURL());
                AudioInputStream byEncoding = CODEC.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, fromUrl);
                check(byEncoding.getFormat().getSampleSizeInBits() == 16 && !byEncoding.getFormat().isBigEndian(),
                    "decoding by encoding gives 16-bit little-endian: " + byEncoding.getFormat());
                byEncoding.close();
                check(!fake.temporary && fake.file.getCanonicalFile().equals(file.getCanonicalFile()),
                    "a file: URL is decoded in place");
            } finally {
                file.delete();
            }

            // The requested layout wins over the decoder's: byte order exactly, channels through the mixer.
            AudioFormat big = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, true);
            byte[] swapped = readAll(CODEC.getAudioInputStream(big, READER.getAudioInputStream(new ByteArrayInputStream(mp3))));
            byte[] little = FakeDecoder.pcm();
            check(swapped.length == little.length, "big-endian length " + swapped.length);
            for (int i = 0; i < little.length; i += 2) {
                check(swapped[i] == little[i + 1] && swapped[i + 1] == little[i], "big-endian sample at byte " + i);
            }
            // A decoder that emits float samples (MediaCodec may) still yields the 16-bit PCM asked for.
            fake.floats = true;
            AudioInputStream fromFloat = CODEC.getAudioInputStream(decoded, READER.getAudioInputStream(new ByteArrayInputStream(mp3)));
            check(fromFloat.getFormat().matches(decoded), "float output converted to " + fromFloat.getFormat());
            byte[] converted = readAll(fromFloat);
            check(converted.length == little.length, "float output keeps every frame: " + converted.length);
            for (int i = 0; i < little.length; i += 2) {
                int want = (short) ((little[i] & 0xFF) | (little[i + 1] << 8));
                int got = (short) ((converted[i] & 0xFF) | (converted[i + 1] << 8));
                check(Math.abs(want - got) <= 1, "float-to-16 sample at byte " + i + ": " + got + " for " + want);
            }
            fake.floats = false;

            // As with mp3spi, only the source's own rate and channel count are offered.
            AudioFormat mono = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, false);
            check(!CODEC.isConversionSupported(mono, base), "a channel change is not offered by the decoder");

            // Only the reader's own stream carries the container the decoder needs.
            try {
                CODEC.getAudioInputStream(decoded, new AudioInputStream(new ByteArrayInputStream(mp3), base, NS));
                throw new AssertionError("decoded a stream that has no container");
            } catch (IllegalArgumentException expected) {
                // as documented by FormatConversionProvider
            }
        } finally {
            CompressedAudio.setDecoder(null);
        }
    }

    /** Counts what was consumed, so a test can see where a refused or sniffed stream was left. */
    static final class MarkedStream extends ByteArrayInputStream {
        MarkedStream(byte[] data) { super(data); }
        int position() { return pos; }
    }

    static byte[] mpegFrames(int header, int length, int count) {
        byte[] out = new byte[length * count];
        for (int i = 0; i < count; i++) {
            byte[] h = u32(header);
            System.arraycopy(h, 0, out, i * length, 4);
        }
        return out;
    }

    static byte[] id3(int size) {
        byte[] tag = new byte[10 + size];
        tag[0] = 'I'; tag[1] = 'D'; tag[2] = '3'; tag[3] = 4;
        tag[6] = (byte) ((size >> 21) & 0x7F); tag[7] = (byte) ((size >> 14) & 0x7F);
        tag[8] = (byte) ((size >> 7) & 0x7F); tag[9] = (byte) (size & 0x7F);
        return tag;
    }

    static byte[] oggPage(byte[] packet) {
        byte[] head = new byte[27];
        System.arraycopy(ascii("OggS"), 0, head, 0, 4);
        head[5] = 2; // beginning of stream
        head[26] = 1;
        return concat(head, new byte[]{(byte) packet.length}, packet);
    }

    static byte[] oggVorbis(int channels, int rate) {
        byte[] id = new byte[30];
        id[0] = 1;
        System.arraycopy(ascii("vorbis"), 0, id, 1, 6);
        id[11] = (byte) channels;
        id[12] = (byte) rate; id[13] = (byte) (rate >> 8); id[14] = (byte) (rate >> 16); id[15] = (byte) (rate >> 24);
        id[28] = (byte) 0xB8; id[29] = 1;
        return oggPage(id);
    }

    static byte[] oggOpus(int channels) {
        byte[] id = new byte[19];
        System.arraycopy(ascii("OpusHead"), 0, id, 0, 8);
        id[8] = 1;
        id[9] = (byte) channels;
        id[12] = (byte) 0x44; id[13] = (byte) 0xAC; // 44100 input rate: Opus still decodes at 48 kHz
        return oggPage(id);
    }

    static byte[] flac(int rate, int channels, int bits, long total) {
        byte[] info = new byte[34];
        info[0] = 0x10; info[2] = 0x10; // 4096-sample blocks
        info[10] = (byte) (rate >> 12);
        info[11] = (byte) (rate >> 4);
        info[12] = (byte) (((rate & 15) << 4) | ((channels - 1) << 1) | ((bits - 1) >> 4));
        info[13] = (byte) ((((bits - 1) & 15) << 4) | (int) ((total >> 32) & 15));
        System.arraycopy(u32((int) total), 0, info, 14, 4);
        return concat(ascii("fLaC"), new byte[]{(byte) 0x80, 0, 0, 34}, info);
    }

    static byte[] adts(int rateIndex, int channels, int count) {
        int length = 20;
        byte[] frame = new byte[length];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xF1;
        frame[2] = (byte) ((1 << 6) | (rateIndex << 2) | ((channels >> 2) & 1));
        frame[3] = (byte) (((channels & 3) << 6) | ((length >> 11) & 3));
        frame[4] = (byte) (length >> 3);
        frame[5] = (byte) (((length & 7) << 5) | 0x1F);
        frame[6] = (byte) 0xFC;
        byte[] out = new byte[0];
        for (int i = 0; i < count; i++) out = concat(out, frame);
        return out;
    }

    static byte[] moov(int channels, int rate) {
        byte[] entry = concat(u32(36), ascii("mp4a"), new byte[6], new byte[]{0, 1}, new byte[8],
            new byte[]{0, (byte) channels, 0, 16}, new byte[4], u32(rate << 16));
        byte[] stsd = box("stsd", new byte[4], u32(1), entry);
        byte[] video = box("trak", box("mdia", box("minf", box("stbl",
            box("stsd", new byte[4], u32(1), concat(u32(36), ascii("avc1"), new byte[28]))))));
        return box("moov", video, box("trak", box("mdia", box("minf", box("stbl", stsd)))));
    }

    static byte[] box(String type, byte[]... content) {
        byte[] body = concat(content);
        return concat(u32(8 + body.length), ascii(type), body);
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.write(part, 0, part.length);
        return out.toByteArray();
    }

    static byte[] u32(int value) {
        return new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }

    static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] random(int length) {
        byte[] out = new byte[length];
        new java.util.Random(84).nextBytes(out);
        out[0] = 0x12; // not a sync byte or a container magic
        return out;
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int n; (n = in.read(buffer)) >= 0; ) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}
