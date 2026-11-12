// host-contract: args=build/compat-tests/awt/midi-reference.txt patched-args=shadow jvm-args=--add-exports=java.desktop/com.sun.media.sound=ALL-UNNAMED
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import javax.sound.midi.*;
import javax.sound.sampled.*;

/**
 * javax.sound.midi against the JDK: the standard MIDI file reader and writer, the real-time
 * sequencer and the Gervill software synthesizer.
 *
 * <p>A hand-assembled type 1 fixture (conductor track with tempo, time signature and name; a
 * piano track with running status, note-off as velocity 0, pitch bend and a GM-on sysex) is
 * parsed, dumped event by event and written back in every file type the writer offers. Each
 * outcome is one line; the plain-JDK run writes the lines to the file named by the first argument
 * and the patched run must reproduce every line. Both runs then play the fixture through the
 * default sequencer into Gervill opened on a capturing {@code SourceDataLine} and require audible
 * output, and render it offline through {@code AudioSystem}'s MIDI reader. The capturing line is
 * handed to Gervill through {@code com.sun.media.sound.AudioSynthesizer}, the JDK-internal interface
 * {@code SoftSynthesizer} implements; the header exports its package for that reflective call.
 */
public final class TestMidiContracts {
    static final Map<String, String> results = new LinkedHashMap<>();

    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path reference = Paths.get(args[0]);
        boolean shadow = args.length > 1 && "shadow".equals(args[1]);
        byte[] fixture = fixture();
        Sequence sequence = files(fixture);
        messages();
        devices();
        play(sequence);
        render(fixture);
        if (!shadow) {
            Files.createDirectories(reference.toAbsolutePath().getParent());
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, String> e : results.entrySet()) lines.add(e.getKey() + " = " + e.getValue());
            Files.write(reference, lines, StandardCharsets.UTF_8);
            System.out.println("TestMidiContracts recorded " + lines.size() + " JDK outcomes in " + reference);
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
            System.out.println("TestMidiContracts matched " + expected.size() + " JDK outcomes");
        }
    }

    // The fixture: 96 ticks per quarter at 120 bpm, a C major chord held for one second.

    static byte[] fixture() {
        int[] conductor = {
            0x00, 0xFF, 0x51, 0x03, 0x07, 0xA1, 0x20,             // tempo 500000 us per quarter
            0x00, 0xFF, 0x58, 0x04, 0x04, 0x02, 0x18, 0x08,       // 4/4
            0x00, 0xFF, 0x03, 0x07, 'F', 'i', 'x', 't', 'u', 'r', 'e',
            0x00, 0xFF, 0x2F, 0x00,
        };
        int[] piano = {
            0x00, 0xF0, 0x05, 0x7E, 0x7F, 0x09, 0x01, 0xF7,       // GM system on
            0x00, 0xC0, 0x00,                                     // acoustic grand
            0x00, 0xB0, 0x07, 0x64,                               // volume 100
            0x00, 0x90, 0x3C, 0x64,                               // C4 on
            0x00, 0x40, 0x64,                                     // E4 on, running status
            0x00, 0x43, 0x64,                                     // G4 on
            0x81, 0x40, 0x3C, 0x00,                               // 192 ticks later: velocity 0 offs
            0x00, 0x40, 0x00,
            0x00, 0x43, 0x00,
            0x00, 0xE0, 0x00, 0x40,                               // pitch bend centre
            0x18, 0x80, 0x3C, 0x40,                               // explicit note off
            0x00, 0xFF, 0x2F, 0x00,
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        put(out, 'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 1, 0, 2, 0, 96);
        chunk(out, conductor);
        chunk(out, piano);
        return out.toByteArray();
    }

    static void chunk(ByteArrayOutputStream out, int[] data) {
        put(out, 'M', 'T', 'r', 'k', data.length >>> 24, (data.length >>> 16) & 0xFF, (data.length >>> 8) & 0xFF, data.length & 0xFF);
        put(out, data);
    }

    static void put(ByteArrayOutputStream out, int... bytes) {
        for (int b : bytes) out.write(b);
    }

    // The file reader and writer.

    static Sequence files(byte[] fixture) throws Exception {
        MidiFileFormat format = MidiSystem.getMidiFileFormat(new ByteArrayInputStream(fixture));
        results.put("format", describe(format));
        check(format.getType() == 1 && format.getResolution() == 96 && format.getDivisionType() == Sequence.PPQ,
            "fixture header: " + describe(format));

        Sequence sequence = MidiSystem.getSequence(new ByteArrayInputStream(fixture));
        String dump = dump(sequence);
        results.put("sequence", dump);
        check(sequence.getTracks().length == 2, "fixture has two tracks, read " + sequence.getTracks().length);
        check(sequence.getTracks()[0].size() == 4 && sequence.getTracks()[1].size() == 12,
            "fixture event counts 4/12, read " + sequence.getTracks()[0].size() + "/" + sequence.getTracks()[1].size());
        check(sequence.getTickLength() == 216, "tick length " + sequence.getTickLength());
        check(sequence.getMicrosecondLength() == 1125000, "microsecond length " + sequence.getMicrosecondLength());

        int[] types = MidiSystem.getMidiFileTypes(sequence);
        results.put("types", Arrays.toString(types));
        results.put("types.all", Arrays.toString(MidiSystem.getMidiFileTypes()));
        check(MidiSystem.isFileTypeSupported(1, sequence) && !MidiSystem.isFileTypeSupported(0, sequence),
            "a two-track sequence writes as type 1 only");
        for (int type : types) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int written = MidiSystem.write(sequence, type, out);
            byte[] bytes = out.toByteArray();
            check(written == bytes.length, "write(type " + type + ") returned " + written + " for " + bytes.length + " bytes");
            results.put("write." + type, bytes.length + " crc " + crc(bytes));
            Sequence again = MidiSystem.getSequence(new ByteArrayInputStream(bytes));
            check(dump(again).equals(dump), "type " + type + " write does not read back to the same events");
            ByteArrayOutputStream twice = new ByteArrayOutputStream();
            MidiSystem.write(again, type, twice);
            check(Arrays.equals(bytes, twice.toByteArray()), "type " + type + " write is not stable across a round trip");

            File file = File.createTempFile("midi-contract", ".mid");
            try {
                check(MidiSystem.write(sequence, type, file) == bytes.length, "file write length");
                check(Arrays.equals(Files.readAllBytes(file.toPath()), bytes), "file and stream writes differ");
                check(dump(MidiSystem.getSequence(file)).equals(dump), "file read differs from stream read");
                results.put("file." + type, describe(MidiSystem.getMidiFileFormat(file)));
            } finally {
                file.delete();
            }
        }

        // A single-track sequence built through the API offers type 0 as well.
        Sequence single = new Sequence(Sequence.PPQ, 480);
        Track track = single.createTrack();
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 9, 36, 127), 0));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 9, 36, 0), 480));
        results.put("single.types", Arrays.toString(MidiSystem.getMidiFileTypes(single)));
        check(MidiSystem.isFileTypeSupported(0, single), "a one-track sequence writes as type 0");
        ByteArrayOutputStream single0 = new ByteArrayOutputStream();
        MidiSystem.write(single, 0, single0);
        results.put("single.write.0", single0.size() + " crc " + crc(single0.toByteArray()));

        try {
            MidiSystem.getSequence(new ByteArrayInputStream("RIFF not a midi file".getBytes(StandardCharsets.US_ASCII)));
            throw new AssertionError("non-MIDI data parsed");
        } catch (InvalidMidiDataException expected) {
        }
        return sequence;
    }

    static String describe(MidiFileFormat format) {
        return "type " + format.getType() + " division " + format.getDivisionType() + " resolution " + format.getResolution()
            + " bytes " + format.getByteLength() + " us " + format.getMicrosecondLength();
    }

    static String dump(Sequence sequence) {
        StringBuilder text = new StringBuilder();
        text.append(sequence.getDivisionType()).append('/').append(sequence.getResolution())
            .append(" ticks ").append(sequence.getTickLength()).append(" us ").append(sequence.getMicrosecondLength());
        for (Track track : sequence.getTracks()) {
            text.append(" | track ").append(track.size()).append(':');
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                text.append(' ').append(event.getTick()).append('@').append(hex(event.getMessage().getMessage()));
            }
        }
        return text.toString();
    }

    static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte b : bytes) text.append(String.format("%02x", b & 0xFF));
        return text.toString();
    }

    static String crc(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, bytes.length);
        return Long.toHexString(crc.getValue());
    }

    // Message classes.

    static void messages() throws Exception {
        ShortMessage note = new ShortMessage(ShortMessage.NOTE_ON, 3, 60, 100);
        check(note.getCommand() == 0x90 && note.getChannel() == 3 && note.getData1() == 60 && note.getData2() == 100,
            "ShortMessage fields");
        results.put("short", hex(note.getMessage()) + " " + note.getLength());
        ShortMessage program = new ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, 5, 0);
        results.put("short.program", hex(program.getMessage()) + " " + program.getLength());
        try {
            new ShortMessage(0x90, 16, 0, 0);
            throw new AssertionError("channel 16 accepted");
        } catch (InvalidMidiDataException expected) {
        }
        MetaMessage tempo = new MetaMessage(0x51, new byte[]{0x07, (byte) 0xA1, 0x20}, 3);
        results.put("meta", hex(tempo.getMessage()) + " type " + tempo.getType() + " data " + hex(tempo.getData()));
        SysexMessage sysex = new SysexMessage(new byte[]{(byte) 0xF0, 0x7E, 0x7F, 0x09, 0x01, (byte) 0xF7}, 6);
        results.put("sysex", hex(sysex.getMessage()) + " data " + hex(sysex.getData()));
        Object cloned = note.clone();
        check(cloned instanceof ShortMessage && Arrays.equals(((ShortMessage) cloned).getMessage(), note.getMessage()), "clone");
    }

    // Defaults: the software sequencer and synthesizer.

    static void devices() throws Exception {
        Sequencer sequencer = MidiSystem.getSequencer(false);
        Synthesizer synthesizer = MidiSystem.getSynthesizer();
        results.put("sequencer", describe(sequencer.getDeviceInfo()));
        results.put("synthesizer", describe(synthesizer.getDeviceInfo()) + " channels " + synthesizer.getChannels().length
            + " polyphony " + synthesizer.getMaxPolyphony() + " latency " + synthesizer.getLatency());
        check("Real Time Sequencer".equals(sequencer.getDeviceInfo().getName()), "default sequencer " + sequencer.getDeviceInfo());
        check("Gervill".equals(synthesizer.getDeviceInfo().getName()), "default synthesizer " + synthesizer.getDeviceInfo());
        check(!sequencer.isOpen() && !synthesizer.isOpen(), "defaults come closed");
        results.put("sequencer.sync", Arrays.toString(sequencer.getMasterSyncModes()) + " " + Arrays.toString(sequencer.getSlaveSyncModes()));

        // The fallback Android always takes (it has no gm.dls or system soundfont): the generated
        // bank must match the JDK's instrument for instrument.
        Soundbank emergency = (Soundbank) Class.forName("com.sun.media.sound.EmergencySoundbank")
            .getMethod("createSoundbank").invoke(null);
        Instrument[] instruments = emergency.getInstruments();
        check(instruments.length > 0, "the emergency soundbank has no instruments");
        StringBuilder names = new StringBuilder();
        for (Instrument instrument : instruments) {
            names.append(instrument.getPatch().getBank()).append(':').append(instrument.getPatch().getProgram())
                .append(' ').append(instrument.getName()).append(';');
        }
        results.put("emergency.soundbank", emergency.getName() + " / " + emergency.getVendor() + " instruments "
            + instruments.length + " resources " + emergency.getResources().length);
        results.put("emergency.instruments", names.toString());
        // The size of the soundbank-emg.sf2 cache SoftSynthesizer writes on first use (MidiProviders documents it).
        ByteArrayOutputStream saved = new ByteArrayOutputStream();
        emergency.getClass().getMethod("save", java.io.OutputStream.class).invoke(emergency, saved);
        System.out.println("emergency soundbank: " + instruments.length + " instruments, " + saved.size() + " bytes as SF2");
        results.put("emergency.sf2.bytes", Integer.toString(saved.size()));
        Soundbank defaults = synthesizer.getDefaultSoundbank();
        check(defaults != null && defaults.getInstruments().length > 0, "the synthesizer has no default soundbank");
    }

    static String describe(MidiDevice.Info info) {
        return info.getName() + " / " + info.getVendor() + " / " + info.getDescription() + " / " + info.getVersion();
    }

    // Playback through the sequencer into a capturing line.

    static void play(Sequence sequence) throws Exception {
        Sequencer sequencer = MidiSystem.getSequencer(false);
        Synthesizer synthesizer = MidiSystem.getSynthesizer();
        CaptureLine line = new CaptureLine(new AudioFormat(44100f, 16, 2, true, false));
        Class<?> audioSynthesizer = Class.forName("com.sun.media.sound.AudioSynthesizer");
        check(audioSynthesizer.isInstance(synthesizer), "the default synthesizer is " + synthesizer.getClass().getName());
        Method open = audioSynthesizer.getMethod("open", SourceDataLine.class, Map.class);
        open.invoke(synthesizer, line, null);
        try {
            check(line.isOpen() && line.isRunning(), "the synthesizer opens and starts the line it is given");
            sequencer.open();
            sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
            final boolean[] ended = new boolean[1];
            sequencer.addMetaEventListener(new MetaEventListener() {
                @Override
                public void meta(MetaMessage message) {
                    if (message.getType() == 0x2F) {
                        synchronized (ended) {
                            ended[0] = true;
                        }
                    }
                }
            });
            sequencer.setSequence(sequence);
            check(sequencer.getTickLength() == 216, "sequencer tick length " + sequencer.getTickLength());
            sequencer.start();
            long deadline = System.currentTimeMillis() + 15000;
            while (sequencer.isRunning() && System.currentTimeMillis() < deadline) Thread.sleep(20);
            check(!sequencer.isRunning(), "the sequencer did not reach the end of the fixture in 15 s");
            Thread.sleep(300);
            synchronized (ended) {
                check(ended[0], "no end-of-track meta event reached the listener");
            }
        } finally {
            sequencer.close();
            synthesizer.close();
        }
        System.out.println("captured " + line.frames + " frames, peak " + line.peak + ", non-zero samples " + line.nonZero);
        check(line.frames > 44100 / 2, "the synthesizer pushed only " + line.frames + " frames");
        check(line.peak > 300 && line.nonZero > 4410, "the capture is silent: peak " + line.peak + ", non-zero " + line.nonZero);
        check(!line.isOpen(), "closing the synthesizer closes the line it opened");
    }

    /** A line that keeps the peak of what it is given and paces the writer to real time. */
    static final class CaptureLine implements SourceDataLine {
        final AudioFormat format;
        final long started = System.nanoTime();
        int bufferSize = 4410 * 4;
        boolean open;
        boolean running;
        volatile long frames;
        volatile int peak;
        volatile long nonZero;

        CaptureLine(AudioFormat format) {
            this.format = format;
        }

        @Override public void open(AudioFormat format, int bufferSize) {
            check(format.matches(this.format), "opened with " + format);
            if (bufferSize > 0) this.bufferSize = bufferSize - bufferSize % format.getFrameSize();
            open = true;
        }
        @Override public void open(AudioFormat format) { open(format, 0); }
        @Override public void open() { open(format, 0); }

        // Only Gervill's pusher thread writes; the test reads the totals after closing.
        @Override public int write(byte[] b, int off, int len) {
            if (len % 4 != 0) throw new IllegalArgumentException("partial frame " + len);
            for (int i = off; i + 1 < off + len; i += 2) {
                int sample = Math.abs((short) ((b[i] & 0xFF) | (b[i + 1] << 8)));
                if (sample > peak) peak = sample;
                if (sample != 0) nonZero++;
            }
            frames += len / 4;
            // Never run more than half a second ahead of the wall clock, as a device line would not.
            long aheadMillis = frames * 1000 / 44100 - (System.nanoTime() - started) / 1000000 - 500;
            if (aheadMillis > 0) {
                try {
                    Thread.sleep(aheadMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return len;
        }

        @Override public void drain() { }
        @Override public void flush() { }
        @Override public void start() { running = true; }
        @Override public void stop() { running = false; }
        @Override public boolean isRunning() { return running; }
        @Override public boolean isActive() { return running; }
        @Override public AudioFormat getFormat() { return format; }
        @Override public int getBufferSize() { return bufferSize; }
        @Override public int available() { return bufferSize; }
        @Override public int getFramePosition() { return (int) frames; }
        @Override public long getLongFramePosition() { return frames; }
        @Override public long getMicrosecondPosition() { return getLongFramePosition() * 1000000L / 44100; }
        @Override public float getLevel() { return AudioSystem.NOT_SPECIFIED; }
        @Override public Line.Info getLineInfo() { return new DataLine.Info(SourceDataLine.class, format); }
        @Override public void close() { open = false; running = false; }
        @Override public boolean isOpen() { return open; }
        @Override public Control[] getControls() { return new Control[0]; }
        @Override public boolean isControlSupported(Control.Type control) { return false; }
        @Override public Control getControl(Control.Type control) { throw new IllegalArgumentException("no control " + control); }
        @Override public void addLineListener(LineListener listener) { }
        @Override public void removeLineListener(LineListener listener) { }
    }

    // Offline rendering through AudioSystem's MIDI file reader.

    static void render(byte[] fixture) throws Exception {
        AudioInputStream stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(fixture));
        try {
            AudioFormat format = stream.getFormat();
            results.put("render", format.getEncoding() + " " + format.getSampleRate() + " Hz " + format.getSampleSizeInBits()
                + " bit " + format.getChannels() + " ch " + format.getFrameSize() + " B/frame big-endian " + format.isBigEndian()
                + " frames " + stream.getFrameLength());
            check(format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED && format.getSampleSizeInBits() == 16,
                "rendered format " + format);
            byte[] pcm = readAll(stream);
            int frameSize = format.getFrameSize();
            check(pcm.length % frameSize == 0 && pcm.length / frameSize >= 44100, "rendered " + pcm.length + " bytes");
            int peak = 0;
            boolean big = format.isBigEndian();
            for (int i = 0; i + 1 < pcm.length; i += 2) {
                int sample = big ? (short) ((pcm[i] << 8) | (pcm[i + 1] & 0xFF)) : (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
                peak = Math.max(peak, Math.abs(sample));
            }
            System.out.println("rendered " + pcm.length / frameSize + " frames offline, peak " + peak);
            check(peak > 300, "the offline render is silent: peak " + peak);
        } finally {
            stream.close();
        }
    }

    static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}
