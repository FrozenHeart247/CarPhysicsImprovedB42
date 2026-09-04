package dev.carphysicsimproved.v1.physics;

public final class LegacyPhysicsTest {
    private LegacyPhysicsTest() {
    }

    public static void main(String[] args) {
        LegacyPhysics.Settings settings = LegacyPhysics.Settings.defaults();
        LegacyPhysics.Spec sport = spec("Base.TestSport", 1_200.0, 320.0, 120.0, 3, 5);
        LegacyPhysics.Conditions road = new LegacyPhysics.Conditions(1.0, 1.0, 1.0, false);

        LegacyPhysics.State first = warmedState(1);
        LegacyPhysics.Output firstGear = LegacyPhysics.step(sport, road, settings,
                input(0.0, true, true, 1), first, 0.05);
        LegacyPhysics.State fifth = warmedState(5);
        LegacyPhysics.Output fifthGear = LegacyPhysics.step(sport, road, settings,
                input(0.0, true, true, 5), fifth, 0.05);
        check(firstGear.rawDriveForce() > fifthGear.rawDriveForce() * 2.0,
                "starting in fifth must be substantially weaker than first");

        LegacyPhysics.State automatic = warmedState(1);
        LegacyPhysics.Output highSpeed = LegacyPhysics.step(sport, road, settings,
                new LegacyPhysics.Input(25.0, true, true, false, false, false, 0.0, 1.0, false, 1),
                automatic, 0.05);
        check(highSpeed.gear() >= 3, "automatic transmission must upshift at road speed");

        LegacyPhysics.State coastState = warmedState(2);
        LegacyPhysics.Output coast = LegacyPhysics.step(sport, road, settings,
                new LegacyPhysics.Input(15.0, true, false, false, false, false, 0.0, 0.0, true, 2),
                coastState, 0.05);
        check(coast.engineForce() == 0.0 && coast.dragMagnitude() > 0.0,
                "released throttle must coast through resistance instead of propulsion");

        LegacyPhysics.State coastRpmState = new LegacyPhysics.State();
        coastRpmState.gear = 4;
        coastRpmState.engineRpm = 4_000.0;
        coastRpmState.throttle = 0.0;
        LegacyPhysics.Output coastRpm = null;
        for (int index = 0; index < 80; index++) {
            double decreasingSpeedMps = Math.max(0.0, 10.0 - index * 0.25);
            coastRpm = LegacyPhysics.step(sport, road, settings,
                    new LegacyPhysics.Input(decreasingSpeedMps, true, false, false, false, false,
                            0.0, 0.0, true, 4),
                    coastRpmState, 0.05);
            check(coastRpm.engineForce() == 0.0,
                    "RPM coupling while coasting must not add propulsion");
        }
        check(coastRpm != null && coastRpm.engineRpm() <= sport.idleRpm() + 1.0,
                "coasting to rest must return engine RPM to idle");

        LegacyPhysics.Spec powerful = spec("Base.TestPowerful", 900.0, 900.0, 120.0, 3, 5);
        LegacyPhysics.Conditions poorGrip = new LegacyPhysics.Conditions(0.7, 0.4, 0.55, false);
        LegacyPhysics.State burnoutState = warmedState(1);
        LegacyPhysics.Output burnout = null;
        for (int index = 0; index < 20; index++) {
            burnout = LegacyPhysics.step(powerful, poorGrip, settings, input(0.0, true, true, 1), burnoutState, 0.05);
        }
        check(burnout != null && burnout.burnoutSpeedKph() > 0.0,
                "powerful low-grip launch must exceed the traction cap");
        check(burnout.engineForce() <= burnout.rawDriveForce(), "traction cap may only reduce delivered force");

        LegacyPhysics.State goodTires = warmedState(1);
        LegacyPhysics.State badTires = warmedState(1);
        LegacyPhysics.Output good = LegacyPhysics.step(sport, road, settings, input(0.0, true, true, 1), goodTires, 0.05);
        LegacyPhysics.Output bad = LegacyPhysics.step(sport,
                new LegacyPhysics.Conditions(0.4, 0.1, 0.6, false), settings,
                input(0.0, true, true, 1), badTires, 0.05);
        check(bad.tireTraction() < good.tireTraction(), "tire condition and surface must reduce traction");

        LegacyPhysics.State reverseState = warmedState(-1);
        LegacyPhysics.Output reverseLimited = LegacyPhysics.step(sport, road, settings,
                new LegacyPhysics.Input(-15.0, true, true, false, false, false, 0.0, 1.0, true, -1),
                reverseState, 0.05);
        check(reverseLimited.engineForce() == 0.0, "reverse propulsion must stop above the configured limit");

        LegacyPhysics.State neutralDropState = new LegacyPhysics.State();
        neutralDropState.gear = 1;
        neutralDropState.lastStepGear = 0;
        neutralDropState.engineRpm = 4_200.0;
        neutralDropState.throttle = 1.0;
        LegacyPhysics.Output neutralDrop = LegacyPhysics.step(sport, road, settings,
                new LegacyPhysics.Input(0.0, true, true, false, false, false,
                        0.0, 1.0, true, 1, true),
                neutralDropState, 0.05);
        check(neutralDrop.clutchKickIntensity() > 0.0,
                "high-RPM neutral-to-first engagement must create a transient clutch kick");
        check(neutralDrop.burnoutSpeedKph() > 0.0,
                "a strong first-gear clutch dump must create measurable wheelspin");
        check(neutralDrop.engineForce() < neutralDrop.rawDriveForce(),
                "clutch-kick excess must become wheelspin instead of body acceleration");

        LegacyPhysics.State fifthGearDropState = new LegacyPhysics.State();
        fifthGearDropState.gear = 5;
        fifthGearDropState.lastStepGear = 0;
        fifthGearDropState.engineRpm = 4_200.0;
        fifthGearDropState.throttle = 1.0;
        LegacyPhysics.Output fifthGearDrop = LegacyPhysics.step(sport, road, settings,
                new LegacyPhysics.Input(0.0, true, true, false, false, false,
                        0.0, 1.0, true, 5, true),
                fifthGearDropState, 0.05);
        check(fifthGearDrop.burnoutSpeedKph() == 0.0,
                "a high-gear engagement must not receive first-gear clutch-dump wheelspin");
        check(fifthGearDrop.rawDriveForce() < neutralDrop.rawDriveForce() * 0.5,
                "neutral-drop torque must retain gear-ratio authority");

        LegacyPhysics.State disabledKickState = new LegacyPhysics.State();
        disabledKickState.gear = 1;
        disabledKickState.lastStepGear = 0;
        disabledKickState.engineRpm = 4_200.0;
        disabledKickState.throttle = 1.0;
        LegacyPhysics.Output disabledKick = LegacyPhysics.step(sport, road, settings,
                new LegacyPhysics.Input(0.0, true, true, false, false, false,
                        0.0, 1.0, true, 1, false),
                disabledKickState, 0.05);
        check(disabledKick.clutchKickIntensity() == 0.0,
                "disabled clutch-kick tuning must leave neutral engagement unchanged");

        LegacyPhysics.State steeringState = warmedState(1);
        LegacyPhysics.Output lowSteering = LegacyPhysics.step(sport, road, settings,
                new LegacyPhysics.Input(2.0, true, false, false, false, false, 1.0, 0.0, true, 1),
                steeringState, 0.05);
        LegacyPhysics.State highSteeringState = warmedState(1);
        LegacyPhysics.Output highSteering = LegacyPhysics.step(sport, road, settings,
                new LegacyPhysics.Input(25.0, true, false, false, false, false, 1.0, 0.0, true, 1),
                highSteeringState, 0.05);
        check(Math.abs(highSteering.steeringRadians()) < Math.abs(lowSteering.steeringRadians()),
                "high-speed steering must build more slowly");

        LegacyPhysics.State healthyTireSteeringState = warmedState(1);
        LegacyPhysics.State destroyedTireSteeringState = warmedState(1);
        LegacyPhysics.Output healthyTireSteering = null;
        LegacyPhysics.Output destroyedTireSteering = null;
        LegacyPhysics.Conditions destroyedSoftTires = new LegacyPhysics.Conditions(
                0.50, LegacyTireCondition.gripMultiplier(0.10, 1.4), 1.0, false);
        for (int index = 0; index < 30; index++) {
            LegacyPhysics.Input steeringInput = new LegacyPhysics.Input(
                    2.0, true, false, false, false, false, 1.0, 0.0, true, 1);
            healthyTireSteering = LegacyPhysics.step(sport, road, settings,
                    steeringInput, healthyTireSteeringState, 0.05);
            destroyedTireSteering = LegacyPhysics.step(sport, destroyedSoftTires, settings,
                    steeringInput, destroyedTireSteeringState, 0.05);
        }
        check(healthyTireSteering != null && destroyedTireSteering != null
                        && Math.abs(destroyedTireSteering.steeringRadians())
                                < Math.abs(healthyTireSteering.steeringRadians()) * 0.35,
                "destroyed half-inflated tires must have very weak steering authority");
        check(destroyedTireSteering.tireTraction() < healthyTireSteering.tireTraction() * 0.10,
                "pressure and deep wear must both reduce drivetrain traction");

        LegacyPhysics.Output dryRoadDefault = LegacyPhysics.step(sport, road, settings,
                input(15.0, true, false, 2), warmedState(2), 0.05);
        LegacyPhysics.Output dryRoadTerrainProfile = LegacyPhysics.step(sport,
                new LegacyPhysics.Conditions(1.0, 1.0, 1.0, false, 0.55), settings,
                input(15.0, true, false, 2), warmedState(2), 0.05);
        check(dryRoadDefault.dragMagnitude() == dryRoadTerrainProfile.dragMagnitude(),
                "terrain profile must not alter rolling resistance on dry asphalt");

        LegacyPhysics.Output ordinaryOffroad = LegacyPhysics.step(sport,
                new LegacyPhysics.Conditions(1.0, 1.0, 0.60, true, 1.0), settings,
                input(15.0, true, false, 2), warmedState(2), 0.05);
        LegacyPhysics.Output heavyOffroad = LegacyPhysics.step(sport,
                new LegacyPhysics.Conditions(1.0, 1.0, 0.84, true, 0.55), settings,
                input(15.0, true, false, 2), warmedState(2), 0.05);
        check(heavyOffroad.dragMagnitude() < ordinaryOffroad.dragMagnitude(),
                "terrain profile must reduce only the off-road rolling penalty");
        checkFinite(firstGear, fifthGear, highSpeed, coast, coastRpm, burnout, good, bad, reverseLimited,
                neutralDrop, fifthGearDrop, disabledKick,
                lowSteering, highSteering, dryRoadDefault, dryRoadTerrainProfile,
                ordinaryOffroad, heavyOffroad, healthyTireSteering, destroyedTireSteering);
        System.out.println("LegacyPhysicsTest: all legacy drivetrain invariants passed");
    }

    private static LegacyPhysics.Spec spec(String name, double mass, double enginePower,
            double maximumSpeed, int mechanicType, int gears) {
        return new LegacyPhysics.Spec(name, mass, enginePower, maximumSpeed, 700.0, 4_500.0, 4_350.0,
                3.2, LegacyPhysics.legacyRatios(gears), 20.0, 0.55, 1.0, mechanicType);
    }

    private static LegacyPhysics.State warmedState(int gear) {
        LegacyPhysics.State state = new LegacyPhysics.State();
        state.gear = gear;
        state.engineRpm = 2_500.0;
        state.throttle = 1.0;
        return state;
    }

    private static LegacyPhysics.Input input(double speed, boolean running, boolean throttle, int gear) {
        return new LegacyPhysics.Input(speed, running, throttle, false, false, false, 0.0,
                throttle ? 1.0 : 0.0, true, gear);
    }

    private static void checkFinite(LegacyPhysics.Output... values) {
        for (LegacyPhysics.Output value : values) {
            check(Double.isFinite(value.engineForce()) && Double.isFinite(value.brakingForce())
                    && Double.isFinite(value.steeringRadians()) && Double.isFinite(value.dragMagnitude())
                    && Double.isFinite(value.engineRpm()), "physics output contains a non-finite value");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
