import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * The client's lambda shapes for DesugarLambdasTask (--release 8, so only metafactory sites):
 * private instance method references are H_INVOKESPECIAL handles, the regression's shape.
 */
public class ClientLambdaFixture {
    private final int seed;

    private ClientLambdaFixture(int seed) {
        this.seed = seed;
    }

    private int scaled(int x) {
        return seed * x;
    }

    private static String tag(Object o) {
        return "tag:" + o;
    }

    private Supplier<String> capturingThis() {
        return () -> "seed=" + seed;
    }

    public static void main(String[] args) {
        IntFunction<ClientLambdaFixture> ctor = ClientLambdaFixture::new;
        ClientLambdaFixture fixture = ctor.apply(3);
        IntUnaryOperator scale = fixture::scaled;
        Function<Object, String> tag = ClientLambdaFixture::tag;
        Supplier<String> self = fixture.capturingThis();
        Runnable effect = () -> System.out.println("runnable " + fixture.seed);
        if (scale.applyAsInt(5) != 15 || !self.get().equals("seed=3")) throw new AssertionError("private impl dispatch");
        System.out.println(scale.applyAsInt(5));
        System.out.println(tag.apply("x"));
        System.out.println(self.get());
        effect.run();
        System.out.println("PASS: client lambda lowering");
    }
}
