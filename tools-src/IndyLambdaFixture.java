import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/** LambdaMetafactory.metafactory in the shapes javac emits for hub plugins (--release 11). */
public class IndyLambdaFixture {
    private final String name;

    private IndyLambdaFixture(String name) {
        this.name = name;
    }

    // Private instance method: its reference is an H_INVOKESPECIAL handle.
    private String greet(String who) {
        return name + " greets " + who;
    }

    private static int twice(int x) {
        return 2 * x;
    }

    // Captures this: the body is a private instance lambda$ method.
    private Supplier<String> self() {
        return () -> name + "!";
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    public static void main(String[] args) {
        int base = 3;
        IntUnaryOperator capture = x -> x + base;
        Function<String, IndyLambdaFixture> ctor = IndyLambdaFixture::new;
        IndyLambdaFixture fixture = ctor.apply("fixture");
        Function<String, String> bound = fixture::greet;
        IntUnaryOperator statics = IndyLambdaFixture::twice;
        BiFunction<Integer, Integer, Integer> boxing = Integer::sum;
        List<String> list = new ArrayList<>(Arrays.asList("ccc", "a", "bb"));
        list.sort(Comparator.comparing(String::length));

        check(capture.applyAsInt(4) == 7, "capturing lambda");
        check(bound.apply("you").equals("fixture greets you"), "private method reference");
        check(fixture.self().get().equals("fixture!"), "this-capturing lambda");
        check(statics.applyAsInt(21) == 42, "static method reference");
        check(boxing.apply(2, 3) == 5, "boxing adaptation");
        System.out.println(capture.applyAsInt(4));
        System.out.println(bound.apply("you"));
        System.out.println(fixture.self().get());
        System.out.println(statics.applyAsInt(21));
        System.out.println(boxing.apply(2, 3));
        System.out.println(list);
        System.out.println("PASS: LambdaMetafactory.metafactory");
    }
}
