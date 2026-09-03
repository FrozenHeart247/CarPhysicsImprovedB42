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
        double tenNormal = LegacyTireCondition.gripMultiplier(0.10, 1.4);
        double halfPressure = LegacyTireCondition.pressureMultiplier(0.50);
        double destroyedHardware = LegacyTireCondition.hardwareGrip(0.50, tenNormal);

        checkClose(1.125, fullNormal, "full-condition compatibility");
        check(seventyNormal / fullNormal > 0.74 && seventyNormal / fullNormal < 0.76,
                "70 percent durability must retain about 75 percent of healthy grip");
        check(halfNormal < seventyNormal,
                "wear curve must continue reducing grip below 70 percent");
        checkClose(fullNormal, fullModern,
                "the former high-quality tire cap must remain unchanged at full condition");
        check(seventyModern / fullModern > 0.74 && seventyModern / fullModern < 0.76,
                "tire quality must not hide 70 percent wear");
        check(tenNormal / fullNormal > 0.09 && tenNormal / fullNormal < 0.12,
                "10 percent durability must behave like a nearly destroyed tire");
        check(halfPressure > 0.61 && halfPressure < 0.63,
                "half pressure must materially weaken the contact patch");
        check(destroyedHardware < 0.08,
                "10 percent durability plus half pressure must have almost no hardware grip");
        check(LegacyTireCondition.steeringAuthority(destroyedHardware) < 0.27,
                "destroyed soft tires must make steering very heavy");
        checkClose(1.0, LegacyTireCondition.nativeFrictionScale(fullNormal),
                "healthy tires must preserve native friction");
        checkClose(0.12, LegacyTireCondition.nativeFrictionScale(destroyedHardware),
                "destroyed tires must retain only the safe minimum native friction");
        System.out.println("LegacyTireConditionTest: deep-wear handling and pressure checks passed");
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
