package zombie.roadcraft.physics;

/** Dependency-free golden-vector tests for the in-game balance adapter. */
public final class RoadcraftCalibrationTest {
    private static int assertions;

    private RoadcraftCalibrationTest() {
    }

    public static void main(String[] args) {
        fixedGearboxesMatchBalancedRatios();
        fallbackGearboxesAreSafeAndStrictlyDecreasing();
        speedToRpmCouplingMatchesTheBalancedFormula();
        vehicleProfilesPreservePowerAndMassDifferences();
        classThrottleResponseSmoothsKeyboardLaunches();
        coastAndSteeringProfilesAreSeparated();
        dynamicMassScalingPreservesRelativeMasses();
        authorityMatchesSinglePlayerAndMultiplayerOwnership();
        brakeMappingPreservesCoasting();
        tireSpeedBuildsSignedBurnoutAndRecovers();
        terrainAndWeatherTractionUseTheBalancedMinimum();
        steeringMatchesGoldenVectors();
        steeringSanitizesExtremeInputs();
        System.out.println("RoadcraftCalibrationTest: " + assertions + " assertions passed");
    }

    private static void fixedGearboxesMatchBalancedRatios() {
        arrayClose(new double[] {2.60, 1.60, 1.00}, RoadcraftCalibration.forwardGearRatios(3), "3-speed");
        arrayClose(new double[] {3.00, 1.80, 1.30, 1.00}, RoadcraftCalibration.forwardGearRatios(4), "4-speed");
        arrayClose(
                new double[] {3.20, 2.00, 1.50, 1.15, 0.90},
                RoadcraftCalibration.forwardGearRatios(5),
                "5-speed");
        close(3.20, RoadcraftCalibration.reverseGearRatio(5), 0.0, "reverse launch ratio");

        double[] first = RoadcraftCalibration.forwardGearRatios(5);
        double[] second = RoadcraftCalibration.forwardGearRatios(5);
        first[0] = 99.0;
        close(3.20, second[0], 0.0, "ratio arrays must not share mutable state");
    }

    private static void fallbackGearboxesAreSafeAndStrictlyDecreasing() {
        for (int requested = -10; requested <= 20; requested++) {
            double[] ratios = RoadcraftCalibration.forwardGearRatios(requested);
            int expectedLength = Math.max(1, Math.min(8, requested));
            check(ratios.length == expectedLength, "gear count must be clamped for " + requested);
            for (int index = 0; index < ratios.length; index++) {
                check(Double.isFinite(ratios[index]) && ratios[index] > 0.0,
                        "gear ratio must be finite and positive");
                if (index > 0) {
                    check(ratios[index] < ratios[index - 1], "gear ratios must strictly decrease");
                }
            }
        }
    }

    private static void speedToRpmCouplingMatchesTheBalancedFormula() {
        double redline = 4_500.0;
        double maximumSpeedKph = 120.0;
        double wheelRadius = 0.32;
        double finalDrive = RoadcraftCalibration.finalDriveRatio(redline, maximumSpeedKph, wheelRadius);
        double wheelRpmPerKph = 60.0 / (3.6 * 2.0 * Math.PI * wheelRadius);
        close(
                0.95 * redline / maximumSpeedKph,
                wheelRpmPerKph * finalDrive,
                1.0e-12,
                "base RPM-per-km/h coupling");

        double topGear = RoadcraftCalibration.forwardGearRatios(5)[4];
        close(
                redline * 0.855,
                maximumSpeedKph * wheelRpmPerKph * finalDrive * topGear,
                1.0e-9,
                "5-speed top gear deliberately reaches 85.5 percent of redline");
    }

    private static void vehicleProfilesPreservePowerAndMassDifferences() {
        close(0.40, RoadcraftCalibration.drivenWeightFraction(), 0.0,
                "standard driven-axle weight fraction");
        close(0.50, RoadcraftCalibration.drivenWeightFraction(2), 0.0,
                "heavy driven-axle weight fraction");
        close(0.43, RoadcraftCalibration.drivenWeightFraction(3), 0.0,
                "sport driven-axle weight fraction");

        close(60.0, RoadcraftCalibration.peakTorqueNm(4_000.0, 1, 1.0), 0.0,
                "standard B42 engineForce conversion");
        close(51.8, RoadcraftCalibration.peakTorqueNm(3_700.0, 2, 1.0), 1.0e-12,
                "heavy B42 engineForce conversion");
        close(105.45, RoadcraftCalibration.peakTorqueNm(5_700.0, 3, 1.0), 1.0e-12,
                "sport B42 engineForce conversion");
        check(RoadcraftCalibration.peakTorqueNm(7_000.0, 3, 1.0)
                        > RoadcraftCalibration.peakTorqueNm(5_700.0, 3, 1.0),
                "engineForce differences inside one class must remain visible");
        close(120.0, RoadcraftCalibration.peakTorqueNm(4_000.0, 1, 2.0), 0.0,
                "Sandbox torque remains a relative multiplier");

        close(0.40, RoadcraftCalibration.peakTorqueRpmFraction(2), 0.0,
                "heavy engine reaches peak torque early");
        close(0.58, RoadcraftCalibration.peakTorqueRpmFraction(3), 0.0,
                "sport engine carries torque higher");
        check(RoadcraftCalibration.idleTorqueFraction(2)
                        > RoadcraftCalibration.idleTorqueFraction(3),
                "heavy engine must have the fuller low-speed curve");
        check(RoadcraftCalibration.redlineTorqueFraction(3)
                        > RoadcraftCalibration.redlineTorqueFraction(2),
                "sport engine must retain more torque near redline");
        close(1.675, RoadcraftCalibration.torqueConverterMultiplier(2.5, 2), 1.0e-12,
                "heavy converter launch multiplication");
        close(2.50, RoadcraftCalibration.torqueConverterMultiplier(2.5, 1), 0.0,
                "standard converter launch multiplication");
        close(2.50, RoadcraftCalibration.torqueConverterMultiplier(2.5, 3), 0.0,
                "sport converter launch multiplication");
        check("Standard".equals(RoadcraftCalibration.powertrainCategory(1)),
                "mechanic type 1 profile");
        check("Heavy".equals(RoadcraftCalibration.powertrainCategory(2)),
                "mechanic type 2 profile");
        check("Sport".equals(RoadcraftCalibration.powertrainCategory(3)),
                "mechanic type 3 profile");
    }

    private static void classThrottleResponseSmoothsKeyboardLaunches() {
        double standard = rampThrottleForHalfSecond(1);
        double heavy = rampThrottleForHalfSecond(2);
        double sport = rampThrottleForHalfSecond(3);

        close(0.675, standard, 1.0e-12, "standard half-second throttle");
        close(0.425, heavy, 1.0e-12, "heavy half-second throttle");
        close(1.0, sport, 1.0e-12, "sport half-second throttle");
        check(heavy < standard && standard < sport,
                "accelerator response must visibly separate vehicle classes");
        close(0.50, RoadcraftCalibration.throttleStep(1.0, 0.0, 2, 0.10), 0.0,
                "throttle release remains quick for every class");
    }

    private static double rampThrottleForHalfSecond(int mechanicType) {
        double throttle = 0.0;
        for (int step = 0; step < 5; step++) {
            throttle = RoadcraftCalibration.throttleStep(
                    throttle,
                    1.0,
                    mechanicType,
                    0.10);
        }
        return throttle;
    }

    private static void coastAndSteeringProfilesAreSeparated() {
        close(0.25, RoadcraftCalibration.automaticCoastDragScale(3), 0.0,
                "sport retains a quarter of explicit coast drag");
        close(0.12, RoadcraftCalibration.automaticCoastDragScale(2), 0.0,
                "heavy automatic retains the least explicit coast drag");
        close(0.18, RoadcraftCalibration.automaticCoastDragScale(1), 0.0,
                "standard automatic coast drag");
        close(0.22, RoadcraftCalibration.automaticCoastAssistMps2(2), 0.0,
                "heavy Bullet-damping compensation");
        close(0.16, RoadcraftCalibration.automaticCoastAssistMps2(1), 0.0,
                "standard Bullet-damping compensation");
        close(0.12, RoadcraftCalibration.automaticCoastAssistMps2(3), 0.0,
                "sport Bullet-damping compensation");
        close(0.0, RoadcraftCalibration.automaticCoastSpeedBlend(0.35), 0.0,
                "coast assistance is absent only immediately before a stop");
        close(0.50, RoadcraftCalibration.automaticCoastSpeedBlend(1.175), 1.0e-12,
                "coast assistance fades smoothly");
        close(1.0, RoadcraftCalibration.automaticCoastSpeedBlend(2.0), 0.0,
                "coast assistance reaches full strength above seven km/h");
        close(0.415, RoadcraftCalibration.automaticCoastAssistStep(
                0.16, 1.0, 0.15, 1, 0.10), 1.0e-12,
                "adaptive coast correction learns excess native damping");
        close(0.055, RoadcraftCalibration.automaticCoastAssistStep(
                0.40, -1.0, 0.15, 1, 0.10), 1.0e-12,
                "adaptive coast correction backs off before causing acceleration");
        close(3.0, RoadcraftCalibration.automaticCoastAssistStep(
                2.90, 8.0, 0.0, 2, 0.10), 0.0,
                "heavy adaptive correction has a safety cap");

        close(0.90, RoadcraftCalibration.steeringClampMultiplier(3, 120.0, 120.0), 0.0,
                "sport high-speed command range is limited for control");
        close(0.70, RoadcraftCalibration.steeringClampMultiplier(2, 0.0, 65.0), 0.0,
                "heavy low-speed steering range");
        close(0.55, RoadcraftCalibration.steeringClampMultiplier(2, 65.0, 65.0), 0.0,
                "heavy high-speed steering range");
        close(1.50, RoadcraftCalibration.steeringResponseMultiplier(3, 120.0, 120.0), 0.0,
                "sport high-speed steering response stays progressive");
        close(0.45, RoadcraftCalibration.steeringResponseMultiplier(2, 65.0, 65.0), 0.0,
                "heavy high-speed steering response");
        close(1.0, RoadcraftCalibration.steeringResponseMultiplier(1, 90.0, 90.0), 0.0,
                "standard steering remains the baseline");
    }

    private static void dynamicMassScalingPreservesRelativeMasses() {
        close(1.0, RoadcraftCalibration.dynamicMassScale(750.0, 500.0), 0.0,
                "vehicles below the reference keep their native mass");
        close(0.50, RoadcraftCalibration.dynamicMassScale(750.0, 1_500.0), 0.0,
                "the heaviest vehicle is reduced to the reference mass");
        close(0.25, RoadcraftCalibration.dynamicMassScale(750.0, 3_000.0), 0.0,
                "one shared factor preserves mass ratios");
        close(0.08, RoadcraftCalibration.dynamicMassScale(750.0, 100_000.0), 0.0,
                "pathological vehicle masses honor the safety floor");
        close(0.50, RoadcraftCalibration.dynamicMassScale(Double.NaN, 1_500.0), 0.0,
                "invalid reference mass uses the balanced default");
    }

    private static void authorityMatchesSinglePlayerAndMultiplayerOwnership() {
        check(RoadcraftCalibration.hasControlAuthority(false, false, false),
                "single-player must own vehicle physics without a Local authorization enum");
        check(RoadcraftCalibration.hasControlAuthority(true, false, true),
                "multiplayer client must accept locally simulated vehicles");
        check(!RoadcraftCalibration.hasControlAuthority(true, false, false),
                "multiplayer client must reject remote vehicles");
        check(RoadcraftCalibration.hasControlAuthority(false, true, true),
                "dedicated server must respect an explicitly local simulation");
        check(!RoadcraftCalibration.hasControlAuthority(false, true, false),
                "dedicated server must reject non-local simulation ownership");
    }

    private static void brakeMappingPreservesCoasting() {
        close(0.0, RoadcraftCalibration.serviceBrakeDemand(false, false, 25.0), 0.0,
                "released controls must coast");
        close(0.0, RoadcraftCalibration.serviceBrakeDemand(true, false, 25.0), 0.0,
                "forward input while moving forward is not a brake");
        close(1.0, RoadcraftCalibration.serviceBrakeDemand(false, true, 25.0), 0.0,
                "reverse input while moving forward brakes");
        close(1.0, RoadcraftCalibration.serviceBrakeDemand(true, false, -25.0), 0.0,
                "forward input while reversing brakes");
        close(0.0, RoadcraftCalibration.serviceBrakeDemand(false, true, 0.50), 0.0,
                "low-speed direction change is not held by the service brake");
        close(1.0, RoadcraftCalibration.serviceBrakeDemand(true, true, 25.0), 0.0,
                "conflicting controls request a deterministic full brake");
    }

    private static void tireSpeedBuildsSignedBurnoutAndRecovers() {
        double forward = RoadcraftCalibration.updateTireSpeedKph(
                0.0, 0.0, 5_000.0, 2_000.0, 1, 0.10);
        close(6.0, forward, 1.0e-12, "forward tire-speed buildup");
        close(3.0, RoadcraftCalibration.burnoutAmountKph(forward, 0.0, 1), 1.0e-12,
                "forward burnout gap");

        double reverse = RoadcraftCalibration.updateTireSpeedKph(
                0.0, 0.0, -5_000.0, 2_000.0, -1, 0.10);
        close(-6.0, reverse, 1.0e-12, "reverse tire-speed buildup");
        close(-3.0, RoadcraftCalibration.burnoutAmountKph(reverse, 0.0, -1), 1.0e-12,
                "reverse burnout sign");
        close(0.0, RoadcraftCalibration.burnoutAmountKph(3.0, 0.0, 1), 0.0,
                "three-km/h wheel-speed tolerance");

        double recovered = RoadcraftCalibration.updateTireSpeedKph(
                10.0, 4.0, 1_000.0, 2_000.0, 1, 0.10);
        close(8.0, recovered, 1.0e-12, "wheelspin recovery below the traction cap");
        close(12.0, RoadcraftCalibration.updateTireSpeedKph(
                99.0, 12.0, 0.0, 2_000.0, 0, 0.10), 0.0,
                "neutral follows actual road speed");
    }

    private static void terrainAndWeatherTractionUseTheBalancedMinimum() {
        close(1.0, environment(0.0, 0.5, 1.0, false), 0.0, "dry road");
        close(0.48, environment(0.0, 0.0, 1.0, true), 1.0e-12, "inflated tire offroad");
        close(0.64, environment(0.0, 0.0, 0.0, true), 1.0e-12, "deflated tire offroad");
        close(0.336, environment(0.2, 0.0, 1.0, true), 1.0e-12, "wet offroad");
        close(0.704, environment(0.0, 1.0, 1.0, false), 1.0e-12, "snow-covered road");
        close(0.48, environment(0.0, 1.0, 1.0, true), 1.0e-12,
                "snow and terrain select the lower factor");
    }

    private static double environment(double rain, double snow, double pressure, boolean offroad) {
        return RoadcraftCalibration.tractionEnvironmentMultiplier(
                rain,
                snow,
                pressure,
                offroad,
                0.80,
                0.70,
                0.40,
                0.60);
    }

    private static void steeringMatchesGoldenVectors() {
        close(0.06666666666666667, steer(0.0, -1.0, 0.0, 0.9, 1.0 / 60.0), 1.0e-12,
                "low-speed steering sign and response");
        close(0.021081851067789197, steer(0.0, -1.0, 80.0, 0.3, 1.0 / 60.0), 1.0e-12,
                "high-speed steering response");
        close(0.25242704252547565, steering(0.4, 1.0, 0.0, 0.9, 1.0, 0.10, 1.0, 0.10, 2.5, 75.0, 1.0 / 60.0),
                1.0e-12, "snapback response");
        close(0.43333333333333335, steer(0.5, 0.0, 0.0, 0.9, 1.0 / 60.0), 1.0e-12,
                "low-speed centering");
        close(0.49333333333333335, steer(0.5, 0.0, 80.0, 0.9, 1.0 / 60.0), 1.0e-12,
                "high-speed centering");
        close(0.43333333333333335, steer(0.5, 0.10, 0.0, 0.9, 1.0 / 60.0), 1.0e-12,
                "deadzone includes exactly 0.1");
        close(0.30, steer(0.29, -1.0, 80.0, 0.30, 0.10), 0.0, "script steering clamp");
    }

    private static double steer(double current, double raw, double speed, double limit, double delta) {
        return steering(current, raw, speed, limit, 1.0, 0.10, 1.0, 0.10, 3.0, 75.0, delta);
    }

    private static double steering(
            double current,
            double raw,
            double speed,
            double limit,
            double lowTurn,
            double highTurn,
            double lowCenter,
            double highCenter,
            double snapback,
            double reference,
            double delta) {
        return RoadcraftCalibration.steeringStep(
                current,
                raw,
                speed,
                limit,
                lowTurn,
                highTurn,
                lowCenter,
                highCenter,
                snapback,
                reference,
                delta);
    }

    private static void steeringSanitizesExtremeInputs() {
        double result = steering(
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE);
        check(Double.isFinite(result), "extreme steering input must stay finite");
        check(Math.abs(result) <= Math.PI / 2.0, "steering must stay inside the sanitized script clamp");

        double boundedResponse = steering(0.4, 1.0, 0.0, 0.9, 10.0, 10.0, 10.0, 10.0, 10.0, 75.0, 0.1);
        close(-0.9, boundedResponse, 0.0, "extreme active response must not overshoot the target");
    }

    private static void arrayClose(double[] expected, double[] actual, String message) {
        check(expected.length == actual.length, message + " length");
        for (int index = 0; index < expected.length; index++) {
            close(expected[index], actual[index], 0.0, message + " ratio " + index);
        }
    }

    private static void close(double expected, double actual, double tolerance, String message) {
        assertions++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
