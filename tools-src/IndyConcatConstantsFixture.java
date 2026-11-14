/** StringConcatFactory.makeConcatWithConstants, with and without bootstrap constants (--release 11). */
public class IndyConcatConstantsFixture {
    private static String visible(String s) {
        return s.replace("\001", "<1>").replace("\002", "<2>");
    }

    public static void main(String[] args) {
        int i = 7;
        long l = 1L << 40;
        char c = 'z';
        double d = 0.5;
        boolean b = true;
        Object nothing = null;
        String plain = "i=" + i + " l=" + l + " c=" + c + " d=" + d + " b=" + b + " null=" + nothing;
        // A literal holding the recipe's tag characters cannot sit in the recipe, so javac
        // passes it as a bootstrap constant.
        String constants = "tag\001" + i + "\002end" + c;
        if (!plain.equals("i=7 l=1099511627776 c=z d=0.5 b=true null=null")) throw new AssertionError(plain);
        if (!visible(constants).equals("tag<1>7<2>endz")) throw new AssertionError(visible(constants));
        System.out.println(plain);
        System.out.println(visible(constants));
        System.out.println("PASS: StringConcatFactory.makeConcatWithConstants");
    }
}
