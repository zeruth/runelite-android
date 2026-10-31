import java.nio.file.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.*;

/** Instrument only compiled diagnostic artifacts, never repository source or a production APK. */
public final class TraceAudioBytecode {
    public static void main(String[] args) throws Exception {
        Set<String> methods = new HashSet<>(Arrays.asList("getPlaybackHeadPosition", "getBufferSizeInFrames",
            "getUnderrunCount", "setStartThresholdInFrames", "setBufferSizeInFrames", "write", "play", "pause", "flush", "stop", "release"));
        try (java.util.stream.Stream<Path> paths = Files.walk(Paths.get(args[0]))) {
            for (Path path : (Iterable<Path>) paths.filter(p -> (p.getFileName().toString().startsWith("PooledSourceDataLine") || p.getFileName().toString().startsWith("LegacySourceDataLine")) && p.toString().endsWith(".class"))::iterator) {
                ClassReader reader = new ClassReader(Files.readAllBytes(path));
                ClassWriter writer = new ClassWriter(0);
                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
                            @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                                if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("android/media/AudioTrack") && methods.contains(name)) {
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "AudioTrackTrace", name,
                                        "(Landroid/media/AudioTrack;" + descriptor.substring(1), false);
                                } else super.visitMethodInsn(opcode, owner, name, descriptor, itf);
                            }
                        };
                    }
                }, 0);
                Files.write(path, writer.toByteArray());
            }
        }
    }
}
