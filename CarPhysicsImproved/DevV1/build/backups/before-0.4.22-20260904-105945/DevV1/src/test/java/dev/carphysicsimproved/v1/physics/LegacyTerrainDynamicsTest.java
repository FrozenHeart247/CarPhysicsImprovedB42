package dev.carphysicsimproved.v1.physics;

public final class LegacyTerrainDynamicsTest {
    private static final LegacyTerrainDynamics.Tuning TUNING = LegacyTerrainDynamics.Tuning.defaults();

    private LegacyTerrainDynamicsTest() {
    }

    public static void main(String[] args) {
        dryAsphaltIsExactIdentityForEveryClass();
        sportAndStandardShareEnvironmentalResponse();
        heavyDutyRecoversConfiguredGripLoss();
        explicitOffroadEfficiencyEnablesModCompatibility();
        combinedConditionsDoNotExceedDryGrip();
        extremeScriptEfficiencyCannotAmplifyGrip();
        System.out.println("LegacyTerrainDynamicsTest: all terrain-profile invariants passed");
    }

    private static void dryAsphaltIsExactIdentityForEveryClass() {
        for (int mechanicType = 1; mechanicType <= 3; mechanicType++) {
            LegacyTerrainDynamics.Output output = evaluate(mechanicType, 1.0,
                    1.0, 0.0, 0.0, false, false);
            check(output.surfaceGrip() == 1.0 && output.offroadResistanceScale() == 1.0
                            && output.nativeWheelFrictionScale() == 1.0,
                    "dry asphalt must not modify grip or resistance for any class");
        }
    }

    private static void sportAndStandardShareEnvironmentalResponse() {
        LegacyTerrainDynamics.Output standard = evaluate(1, 1.0,
                1.0, 1.0, 1.0, true, true);
        LegacyTerrainDynamics.Output sport = evaluate(3, 0.80,
                1.0, 1.0, 1.0, true, true);
        check(standard.profile() == LegacyTerrainDynamics.Profile.ROAD
                        && sport.profile() == LegacyTerrainDynamics.Profile.ROAD,
                "ordinary standard and sport cars must share the road profile");
        check(Math.abs(standard.surfaceGrip() - sport.surfaceGrip()) < 0.000001
                        && standard.offroadResistanceScale() == sport.offroadResistanceScale()
                        && standard.nativeWheelFrictionScale() == sport.nativeWheelFrictionScale(),
                "standard and sport environmental response must be identical");
    }

    private static void heavyDutyRecoversConfiguredGripLoss() {
        LegacyTerrainDynamics.Output roadOffroad = evaluate(1, 1.0,
                1.0, 0.0, 0.0, true, false);
        LegacyTerrainDynamics.Output heavyOffroad = evaluate(2, 1.0,
                1.0, 0.0, 0.0, true, false);
        LegacyTerrainDynamics.Output heavyRain = evaluate(2, 1.0,
                1.0, 1.0, 0.0, false, false);
        LegacyTerrainDynamics.Output heavySnow = evaluate(2, 1.0,
                1.0, 0.0, 1.0, false, false);
        check(Math.abs(heavyOffroad.surfaceGrip() - 0.84) < 0.000001,
                "heavy off-road grip must recover 60 percent of the baseline loss");
        check(Math.abs(heavyRain.surfaceGrip() - 0.835) < 0.000001,
                "heavy wet grip must recover 45 percent of the baseline loss");
        check(Math.abs(heavySnow.surfaceGrip() - 0.76) < 0.000001,
                "heavy snow grip must recover 60 percent of the baseline loss");
        check(Math.abs(heavyOffroad.offroadResistanceScale() - 0.55) < 0.000001,
                "heavy off-road resistance must use its isolated scale");
        check(Math.abs(roadOffroad.nativeWheelFrictionScale() - 0.82) < 0.000001
                        && Math.abs(heavyOffroad.nativeWheelFrictionScale() - 0.928) < 0.000001
                        && heavyOffroad.nativeWheelFrictionScale() > roadOffroad.nativeWheelFrictionScale(),
                "terrain profile must retain more native wheel friction than the road profile");
    }

    private static void explicitOffroadEfficiencyEnablesModCompatibility() {
        LegacyTerrainDynamics.Output vanillaOffroad = evaluate(1, 1.30,
                1.0, 0.0, 0.0, true, false);
        check(vanillaOffroad.profile() == LegacyTerrainDynamics.Profile.TERRAIN
                        && Math.abs(vanillaOffroad.surfaceGrip() - 0.84) < 0.000001,
                "a standard-class script with explicit off-road ability must get the terrain profile");
    }

    private static void combinedConditionsDoNotExceedDryGrip() {
        LegacyTerrainDynamics.Output road = evaluate(1, 1.0,
                1.0, 1.0, 1.0, true, true);
        LegacyTerrainDynamics.Output terrain = evaluate(2, 1.0,
                1.0, 1.0, 1.0, true, true);
        check(terrain.surfaceGrip() > road.surfaceGrip(),
                "terrain-capable vehicles must retain an advantage under combined bad conditions");
        check(terrain.surfaceGrip() <= 1.0 && road.surfaceGrip() >= 0.10,
                "combined environmental factors must remain inside the grip envelope");
    }

    private static void extremeScriptEfficiencyCannotAmplifyGrip() {
        LegacyTerrainDynamics.Output output = evaluate(3, 4.0,
                1.0, 0.0, 0.0, true, false);
        check(output.profile() == LegacyTerrainDynamics.Profile.TERRAIN
                        && Math.abs(output.surfaceGrip() - 0.84) < 0.000001,
                "extreme mod values may classify capability but must not multiply grip");
    }

    private static LegacyTerrainDynamics.Output evaluate(int mechanicType, double efficiency,
            double pressure, double rain, double snow, boolean offroad, boolean forest) {
        LegacyTerrainDynamics.Output output = LegacyTerrainDynamics.evaluate(
                new LegacyTerrainDynamics.Input(mechanicType, efficiency, pressure,
                        rain, snow, offroad, forest),
                TUNING);
        check(Double.isFinite(output.surfaceGrip())
                        && Double.isFinite(output.offroadResistanceScale())
                        && Double.isFinite(output.nativeWheelFrictionScale()),
                "terrain output contains a non-finite value");
        return output;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
