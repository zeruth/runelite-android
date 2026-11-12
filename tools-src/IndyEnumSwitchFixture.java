/** SwitchBootstraps.enumSwitch: an enum selector with pattern labels (--release 21). */
public class IndyEnumSwitchFixture {
    enum Level { LOW, MID, HIGH, MAX }

    static String rate(Level level) {
        return switch (level) {
            case null -> "none";
            case LOW -> "low";
            case MID -> "mid";
            case Level l when l.ordinal() > 2 -> "top " + l;
            case Level l -> "other " + l;
        };
    }

    public static void main(String[] args) {
        StringBuilder out = new StringBuilder();
        for (Level l : Level.values()) out.append(rate(l)).append("; ");
        out.append(rate(null));
        System.out.println(out);
        if (!rate(Level.MAX).equals("top MAX") || !rate(Level.HIGH).equals("other HIGH")) throw new AssertionError("enumSwitch dispatch");
        System.out.println("PASS: SwitchBootstraps.enumSwitch");
    }
}
