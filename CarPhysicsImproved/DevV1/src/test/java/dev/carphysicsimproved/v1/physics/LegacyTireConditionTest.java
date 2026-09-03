package dev.carphysicsimproved.v1.physics;

public final class LegacyTireConditionTest {
    private LegacyTireConditionTest() {
    }

    public static void main(String[] args) {
        double fullNormal = LegacyTireCondition.gripMultiplier(1.0, 1.4);
        double seventyNormal = LegacyTireCondition.gripMultiplier(0.70, 1.4);
        double halfNormal = LegacyTireCondition.gripMultiplier(0.50, 1.4);
        double fullModern = LegacyTireCondition.gripMultiplier(1.0, 1.8);
        double seventyModern = LegacyTireCondition.gripMultiplier(0.70, 1.8);

        checkClose(1.125, fullNormal, "full-condition compatibility");
        check(seventyNormal / fullNormal > 0.73 && seventyNormal / fullNormal < 0.77,
                "70 percent durability must retain about 75 percent of healthy grip");
        check(halfNormal < seventyNormal,
                "wear curve must continue reducing grip below 70 percent");
        checkClose(fullNormal, fullModern,
                "the former high-quality tire cap must remain unchanged at full condition");
        check(seventyModern / fullModern > 0.73 && seventyModern / fullModern < 0.77,
                "tire quality must not hide 70 percent wear");
        System.out.println("LegacyTireConditionTest: nonlinear 70-percent wear threshold passed");
    }

    private static void checkClose(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < 1.0E-9,
                message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
