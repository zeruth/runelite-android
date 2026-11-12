/** ObjectMethods.bootstrap: a record's equals, hashCode and toString (--release 21). */
public class IndyRecordFixture {
    record Point(int x, long y, String label) {}

    record Empty() {}

    public static void main(String[] args) {
        Point a = new Point(1, 2L, "a");
        Point same = new Point(1, 2L, "a");
        Point other = new Point(1, 2L, null);
        if (!a.equals(same) || a.equals(other) || other.equals(a)) throw new AssertionError("equals");
        if (a.hashCode() != same.hashCode()) throw new AssertionError("hashCode");
        // The hash itself is unspecified, so only its consistency is compared.
        System.out.println(a + " " + other + " " + new Empty());
        System.out.println(a.equals(same) + " " + a.equals(other) + " " + (a.hashCode() == same.hashCode()));
        System.out.println("PASS: ObjectMethods.bootstrap");
    }
}
