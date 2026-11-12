import java.io.Serializable;
import java.util.function.Supplier;

/** LambdaMetafactory.altMetafactory: serializable, marker-interface and bridged lambdas. */
public class IndyAltLambdaFixture {
    interface Marker {}

    interface Source<T> {
        T get();
    }

    // The lambda implements get()String; Source.get()Object is the bridge javac asks for.
    interface StringSource extends Source<String> {
        @Override
        String get();
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    public static void main(String[] args) {
        String suffix = "!";
        // FLAG_SERIALIZABLE. Android cannot serialize a lambda (no SerializedLambda); calling
        // it is what a plugin does, so that is what is compared.
        Supplier<String> serializable = (Supplier<String> & Serializable) () -> "serializable" + suffix;
        Supplier<String> marked = (Supplier<String> & Marker) () -> "marker";
        StringSource bridged = () -> "bridged";
        Source<String> erased = bridged;

        check(serializable.get().equals("serializable!"), "serializable lambda");
        check(marked instanceof Marker, "marker interface");
        check(erased.get().equals("bridged"), "bridge method");
        System.out.println(serializable.get());
        System.out.println(marked.get() + " " + (marked instanceof Marker));
        System.out.println(erased.get());
        System.out.println("PASS: LambdaMetafactory.altMetafactory");
    }
}
