/** SwitchBootstraps.typeSwitch with class, String, Integer and enum-constant labels (--release 21). */
public class IndyTypeSwitchFixture {
    sealed interface Shape permits Circle, Square {}

    record Circle(int r) implements Shape {}

    record Square(int side) implements Shape {}

    enum Color { RED, GREEN, BLUE }

    static String describe(Object o) {
        return switch (o) {
            case null -> "null";
            case String s when s.isEmpty() -> "empty string";
            case String s -> "string " + s;
            case Integer i when i > 10 -> "big " + i;
            case Integer i -> "int " + i;
            case Color c when c == Color.GREEN -> "green";
            case Color c -> "color " + c;
            case Circle(int r) when r > 5 -> "large circle";
            case Circle c -> "circle " + c.r();
            default -> "other " + o.getClass().getSimpleName();
        };
    }

    // Qualified enum constants as labels of an Object switch: EnumDesc dynamic constants.
    static String tint(Object o) {
        return switch (o) {
            case Color.RED -> "red";
            case Color.BLUE -> "blue";
            case Color c -> "other color " + c.name();
            default -> "none";
        };
    }

    static String number(Integer n) {
        return switch (n) {
            case 1 -> "one";
            case 2 -> "two";
            case Integer i when i < 0 -> "negative";
            case Integer i -> "n" + i;
        };
    }

    static String word(String s) {
        return switch (s) {
            case "a" -> "letter a";
            case String t when t.length() > 3 -> "long " + t;
            case String t -> "short " + t;
        };
    }

    // Exhaustive over a sealed hierarchy: no default.
    static int area(Shape shape) {
        return switch (shape) {
            case Circle c -> 3 * c.r() * c.r();
            case Square q -> q.side() * q.side();
        };
    }

    public static void main(String[] args) {
        Object[] inputs = {null, "", "abc", 42, 3, Color.GREEN, Color.RED, new Circle(9), new Circle(2), new Square(4), 1.5};
        StringBuilder out = new StringBuilder();
        for (Object o : inputs) out.append(describe(o)).append("; ");
        System.out.println(out);
        System.out.println(tint(Color.RED) + ", " + tint(Color.BLUE) + ", " + tint(Color.GREEN) + ", " + tint("x"));
        System.out.println(number(1) + ", " + number(2) + ", " + number(-4) + ", " + number(9));
        System.out.println(word("a") + ", " + word("abcd") + ", " + word("ab"));
        System.out.println(area(new Circle(2)) + " " + area(new Square(5)));
        if (!describe(new Circle(9)).equals("large circle") || !tint(Color.BLUE).equals("blue") || !number(-4).equals("negative")) {
            throw new AssertionError("typeSwitch dispatch");
        }
        System.out.println("PASS: SwitchBootstraps.typeSwitch");
    }
}
