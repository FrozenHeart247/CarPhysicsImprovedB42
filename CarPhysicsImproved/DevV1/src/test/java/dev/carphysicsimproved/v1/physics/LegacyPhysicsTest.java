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
        checkFinite(firstGear, fifthGear, highSpeed, coast, burnout, good, bad, reverseLimited,
                lowSteering, highSteering);
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
