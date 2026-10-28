import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.StringValue;
import java.io.ByteArrayOutputStream;

/** Run with the release APK on dalvikvm's classpath, without protobuf bundled in this test. */
public class TestReleaseProtobuf {
    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream output = CodedOutputStream.newInstance(bytes);
        output.writeString(1, "plugin-runtime");
        output.flush();
        CodedInputStream input = CodedInputStream.newInstance(bytes.toByteArray());
        if (input.readTag() != 10 || !"plugin-runtime".equals(input.readString()) || !input.isAtEnd()) {
            throw new AssertionError("protobuf wire round trip failed");
        }
        StringValue message = StringValue.newBuilder()
            .setValue("plugin-runtime").build();
        if (!message.equals(StringValue.parseFrom(message.toByteArray()))) {
            throw new AssertionError("generated message round trip failed");
        }
        // Plugin injection inspects method signatures before invoking plugin code.
        CodedOutputStream.class.getDeclaredMethods();
        StringValue.class.getDeclaredMethods();
        System.out.println("PASS: release protobuf linkage, reflection and serialization");
    }
}
