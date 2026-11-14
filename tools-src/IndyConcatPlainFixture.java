/** StringConcatFactory.makeConcat: compiled with -XDstringConcat=indy, no recipe or constants. */
public class IndyConcatPlainFixture {
    public static void main(String[] args) {
        int i = -3;
        long l = Long.MIN_VALUE;
        char c = 'q';
        float f = 1.25f;
        Object nothing = null;
        String s = "x";
        String joined = s + i + l + c + f + nothing + s;
        if (!joined.equals("x-3-9223372036854775808q1.25nullx")) throw new AssertionError(joined);
        System.out.println(joined);
        System.out.println("PASS: StringConcatFactory.makeConcat");
    }
}
