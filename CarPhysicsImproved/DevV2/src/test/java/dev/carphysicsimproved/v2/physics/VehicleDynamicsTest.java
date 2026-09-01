package dev.carphysicsimproved.v2.physics;

import java.util.List;

/** Dependency-free deterministic contract tests for the V2 physics core. */
public final class VehicleDynamicsTest {
    private static final double DT = 1.0 / 60.0;

    private VehicleDynamicsTest() {
    }

    public static void main(String[] args) {
        VehicleSpec spec = ordinaryVehicle("Workshop.Author.ArbitraryVehicle");
        derivesSpecificationWithoutVehicleNameRules(spec);
        automaticAndManualShareOnePhysics(spec);
        automaticShiftsButManualHolds(spec);
        reverseAndRoadGradeHavePhysicalDirection(spec);
        neutralAndStationaryBrakeDoNotDrive(spec);
        manualGearHasARealRevLimit(spec);
        highGearLaunchBogs(spec);
        lowGearsBuildAccelerationProgressively(spec);
        sportsGearCurveBoostsLowRatiosAndSoftensTopRatios();
        reverseSpeedUsesDedicatedEnvelope();
        sandboxTuningScalesPhysicalInputs(spec);
        sandboxSteeringAndDriftDefaultsRemainExplicit(spec);
        scriptPerformanceSeparatesVanAndSportsAcceleration();
        engagedHandbrakeBlocksPropulsion(spec);
        healthyStandardVehicleCanBreakTraction(spec);
        burnoutFollowsGearAndSurface();
        tireStateChangesGripResistanceAndPull(spec);
        detectsPowerOversteerAndUndersteer(spec);
        steeringAppliesAndReturnsProgressively(spec);
        shortSteeringTapBuildsLateralForceProgressively(spec);
        lowSpeedSteeringCannotSpinParkedVehicle(spec);
        sanitizesNonFiniteRuntimeValues(spec);
        deterministicReplay(spec);
        System.out.println("VehicleDynamicsTest: all deterministic contracts passed");
    }

    private static VehicleSpec ordinaryVehicle(String name) {
        return vehicle(name, 1_380.0, 5_200.0, 175.0, 1.05);
    }

    private static VehicleSpec vehicle(
            String name,
            double massKg,
            double engineForce,
            double maximumSpeedKph,
            double wheelFriction) {
        return VehicleSpec.fromScript(new ScriptVehicleData(
                name,
                massKg,
                engineForce,
                850.0,
                maximumSpeedKph,
                wheelFriction,
                0.60,
                0.05,
                DriveLayout.REAR,
                new double[] {3.55, 2.18, 1.52, 1.13, 0.87},
                List.of(
                        new ScriptVehicleData.Wheel("FrontLeft", true, -0.78, 1.28, 0.33, 0.21),
                        new ScriptVehicleData.Wheel("FrontRight", true, 0.78, 1.28, 0.33, 0.21),
                        new ScriptVehicleData.Wheel("RearLeft", false, -0.78, -1.22, 0.33, 0.21),
                        new ScriptVehicleData.Wheel("RearRight", false, 0.78, -1.22, 0.33, 0.21))));
    }

    private static void derivesSpecificationWithoutVehicleNameRules(VehicleSpec spec) {
        check(spec.fullType().contains("ArbitraryVehicle"), "full type must remain diagnostic-only");
        check(spec.massKg() == 1_380.0, "valid modded mass must be preserved");
        check(spec.transmission().gearCount() == 5, "modded gear count must be retained");
        check(spec.chassis().wheelbaseMeters() > 2.4, "wheel geometry must derive wheelbase");
    }

    private static void automaticAndManualShareOnePhysics(VehicleSpec spec) {
        DynamicsState state = new DynamicsState(1, 2_000.0, 0.72, 0.05, 6.0, 0.2);
        VehicleMotion motion = new VehicleMotion(5.0, 0.15, 0.02);
        DriverInput automatic = new DriverInput(0.72, 0.0, 0.0, 0.15, TransmissionMode.AUTOMATIC, 1, 1.0);
        DriverInput manual = new DriverInput(0.72, 0.0, 0.0, 0.15, TransmissionMode.MANUAL, 1, 1.0);
        DynamicsOutput autoOutput = VehicleDynamics.step(spec, state, automatic, motion, DT);
        DynamicsOutput manualOutput = VehicleDynamics.step(spec, state, manual, motion, DT);
        near(autoOutput.longitudinalForceN(), manualOutput.longitudinalForceN(), 1.0e-9,
                "same selected gear must produce the same longitudinal physics");
        near(autoOutput.yawTorqueNm(), manualOutput.yawTorqueNm(), 1.0e-9,
                "transmission UI mode must not change tire physics");

        DriverInput autoCoast = new DriverInput(0.0, 0.0, 0.0, 0.0, TransmissionMode.AUTOMATIC, 1, 1.0);
        DriverInput manualCoast = new DriverInput(0.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, 1, 1.0);
        DynamicsOutput a = VehicleDynamics.step(spec, state, autoCoast, motion, DT);
        DynamicsOutput m = VehicleDynamics.step(spec, state, manualCoast, motion, DT);
        near(a.resistanceForceN(), m.resistanceForceN(), 1.0e-9,
                "automatic must not have a private coast-assist path");
    }

    private static void automaticShiftsButManualHolds(VehicleSpec spec) {
        DynamicsState state = new DynamicsState(1, 6_000.0, 1.0, 0.0, 20.0, 0.0);
        VehicleMotion fast = new VehicleMotion(20.0, 0.0, 0.0);
        DynamicsOutput automatic = VehicleDynamics.step(spec, state,
                new DriverInput(1.0, 0.0, 0.0, 0.0, TransmissionMode.AUTOMATIC, 1, 1.0), fast, DT);
        DynamicsOutput manual = VehicleDynamics.step(spec, state,
                new DriverInput(1.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, 1, 1.0), fast, DT);
        check(automatic.state().gear() == 2 && automatic.shifted(), "automatic strategy must upshift");
        check(manual.state().gear() == 1 && !manual.shifted(), "manual strategy must hold requested gear");
    }

    private static void highGearLaunchBogs(VehicleSpec spec) {
        DynamicsState settled = new DynamicsState(1, spec.engine().idleRpm(), 1.0, 0.0, 0.0, 0.0);
        DynamicsOutput first = VehicleDynamics.step(spec, settled,
                new DriverInput(1.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, 1, 1.0),
                VehicleMotion.stopped(), DT);
        DynamicsState fifthState = new DynamicsState(5, spec.engine().idleRpm(), 1.0, 0.0, 0.0, 0.0);
        DynamicsOutput fifth = VehicleDynamics.step(spec, fifthState,
                new DriverInput(1.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, 5, 1.0),
                VehicleMotion.stopped(), DT);
        check(fifth.rawDriveForceN() < first.rawDriveForceN() * 0.12,
                "fifth-gear standing start must bog instead of launching like first");
    }

    private static void lowGearsBuildAccelerationProgressively(VehicleSpec spec) {
        DriverInput firstGear = new DriverInput(
                1.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, 1, 1.0);
        DynamicsOutput standing = VehicleDynamics.step(
                spec,
                new DynamicsState(1, spec.engine().idleRpm(), 1.0, 0.0, 0.0, 0.0),
                firstGear,
                VehicleMotion.stopped(),
                DT);
        DynamicsOutput rolling = VehicleDynamics.step(
                spec,
                new DynamicsState(1, 3_000.0, 1.0, 0.0, 5.0, 0.0),
                firstGear,
                new VehicleMotion(5.0, 0.0, 0.0),
                DT);
        check(rolling.propulsionForceLimitN() > standing.propulsionForceLimitN() * 1.15,
                "first-gear body acceleration must build instead of using one flat vanilla-like cap");

        DynamicsOutput secondLow = VehicleDynamics.step(
                spec,
                new DynamicsState(2, 1_800.0, 1.0, 0.0, 7.0, 0.0),
                new DriverInput(1.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, 2, 1.0),
                new VehicleMotion(7.0, 0.0, 0.0),
                DT);
        DynamicsOutput secondHigh = VehicleDynamics.step(
                spec,
                new DynamicsState(2, 4_000.0, 1.0, 0.0, 13.0, 0.0),
                new DriverInput(1.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, 2, 1.0),
                new VehicleMotion(13.0, 0.0, 0.0),
                DT);
        check(secondHigh.propulsionForceLimitN() > secondLow.propulsionForceLimitN(),
                "second gear must recover force as RPM builds after the shift");
    }

    private static void reverseSpeedUsesDedicatedEnvelope() {
        VehicleSpec sports = vehicle("Test.SportsReverse", 800.0, 7_000.0, 120.0, 1.4);
        VehicleSpec utility = vehicle("Test.UtilityReverse", 1_200.0, 3_700.0, 70.0, 1.4);
        near(VehicleDynamics.reverseSpeedLimitKph(sports), 34.0, 1.0e-9,
                "sports reverse speed must use the generic upper bound");
        near(VehicleDynamics.reverseSpeedLimitKph(utility), 22.0, 1.0e-9,
                "low-speed utility reverse must use the generic lower bound");

        double limitMps = VehicleDynamics.reverseSpeedLimitKph(sports) / 3.6;
        DynamicsOutput below = VehicleDynamics.step(
                sports,
                new DynamicsState(-1, 3_000.0, 1.0, 0.0, -limitMps * 0.50, 0.0),
                new DriverInput(1.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, -1, 1.0),
                new VehicleMotion(-limitMps * 0.50, 0.0, 0.0),
                DT);
        DynamicsOutput atLimit = VehicleDynamics.step(
                sports,
                new DynamicsState(-1, 5_000.0, 1.0, 0.0, -limitMps, 0.0),
                new DriverInput(1.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, -1, 1.0),
                new VehicleMotion(-limitMps, 0.0, 0.0),
                DT);
        check(below.propulsionForceLimitN() > sports.massKg() * 1.5,
                "reverse must retain useful acceleration below its speed envelope");
        near(atLimit.propulsionForceLimitN(), 0.0, 1.0e-9,
                "reverse propulsion must fade out at the dedicated limit");
    }

    private static void sportsGearCurveBoostsLowRatiosAndSoftensTopRatios() {
        VehicleSpec sports = vehicle("Test.SportsCurve", 800.0, 7_000.0, 190.0, 1.8);
        DriverInput first = new DriverInput(
                1.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, 1, 1.0);
        DynamicsOutput standing = VehicleDynamics.step(
                sports,
                new DynamicsState(1, sports.engine().idleRpm(), 1.0, 0.0, 0.0, 0.0),
                first,
                VehicleMotion.stopped(),
                DT);
        DynamicsOutput rolling = VehicleDynamics.step(
                sports,
                new DynamicsState(1, 3_200.0, 1.0, 0.0, 5.5, 0.0),
                first,
                new VehicleMotion(5.5, 0.0, 0.0),
                DT);
        check(standing.propulsionForceLimitN() > rolling.propulsionForceLimitN() * 0.72,
                "sports first gear must retain the slightly stronger standing launch authority");

        near(VehicleDynamics.highGearDriveScale(1.0, 3, 5), 1.0, 1.0e-9,
                "middle sports ratios must remain unchanged");
        near(VehicleDynamics.highGearDriveScale(1.0, 4, 5), 0.96, 1.0e-9,
                "penultimate sports ratio must be softened slightly");
        near(VehicleDynamics.highGearDriveScale(1.0, 5, 5), 0.90, 1.0e-9,
                "top sports ratio must receive the full high-gear reduction");
        near(VehicleDynamics.highGearDriveScale(0.0, 5, 5), 1.0, 1.0e-9,
                "non-performance scripts must not inherit the sports top-gear penalty");
    }

    private static void sandboxTuningScalesPhysicalInputs(VehicleSpec spec) {
        DriverInput drive = new DriverInput(
                0.75, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, 3, 1.0);
        DynamicsState driveState = new DynamicsState(3, 3_000.0, 0.75, 0.0, 15.0, 0.0);
        VehicleMotion motion = new VehicleMotion(15.0, 0.0, 0.0);
        DynamicsOutput normal = VehicleDynamics.step(
                spec, driveState, drive, motion, VehicleCondition.healthy(spec),
                PhysicsTuning.defaults(), DT);
        DynamicsOutput powered = VehicleDynamics.step(
                spec, driveState, drive, motion, VehicleCondition.healthy(spec),
                new PhysicsTuning(1.20, 1.0), DT);
        near(powered.rawDriveForceN(), normal.rawDriveForceN() * 1.20, 1.0e-6,
                "engine-power sandbox tuning must scale wheel torque before traction");

        DriverInput coast = new DriverInput(
                0.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, 3, 1.0);
        DynamicsState coastState = new DynamicsState(3, 3_000.0, 0.0, 0.0, 15.0, 0.0);
        DynamicsOutput lowResistance = VehicleDynamics.step(
                spec, coastState, coast, motion, VehicleCondition.healthy(spec),
                new PhysicsTuning(1.0, 0.50), DT);
        DynamicsOutput highResistance = VehicleDynamics.step(
                spec, coastState, coast, motion, VehicleCondition.healthy(spec),
                new PhysicsTuning(1.0, 1.50), DT);
        near(Math.abs(highResistance.resistanceForceN()),
                Math.abs(lowResistance.resistanceForceN()) * 3.0,
                1.0e-6,
                "road-resistance sandbox tuning must scale the complete coast force");
    }

    private static void sandboxSteeringAndDriftDefaultsRemainExplicit(VehicleSpec spec) {
        DriverInput turn = new DriverInput(
                0.0, 0.0, 0.0, 1.0, TransmissionMode.MANUAL, 2, 1.0);
        VehicleMotion motion = new VehicleMotion(10.0, 0.0, 0.0);
        DynamicsState normalState = new DynamicsState(2, 2_500.0, 0.0, 0.0, 10.0, 0.0);
        DynamicsState softState = normalState;
        DynamicsOutput normal = null;
        DynamicsOutput soft = null;
        PhysicsTuning softSteering = new PhysicsTuning(1.0, 1.0, 0.50, 1.50);
        for (int frame = 0; frame < 120; frame++) {
            normal = VehicleDynamics.step(
                    spec, normalState, turn, motion, VehicleCondition.healthy(spec),
                    PhysicsTuning.defaults(), DT);
            soft = VehicleDynamics.step(
                    spec, softState, turn, motion, VehicleCondition.healthy(spec),
                    softSteering, DT);
            normalState = normal.state();
            softState = soft.state();
        }
        if (normal == null || soft == null) {
            throw new AssertionError("steering tuning replay must produce outputs");
        }
        check(Math.abs(normal.state().steeringAngleRadians())
                        > Math.abs(soft.state().steeringAngleRadians()) * 1.90,
                "steering sandbox scale must alter target wheel angle without changing its default");

        double defaultActivation = VehicleDynamics.powerDriftActivation(
                1.75, PhysicsTuning.defaults());
        double delayedActivation = VehicleDynamics.powerDriftActivation(
                1.75, new PhysicsTuning(1.0, 1.0, 1.0, 2.50));
        check(defaultActivation > 0.0 && delayedActivation == 0.0,
                "drift-entry sandbox delay must move the power-drift activation threshold");
    }

    private static void scriptPerformanceSeparatesVanAndSportsAcceleration() {
        VehicleSpec sports = vehicle("Test.Sports", 800.0, 7_000.0, 120.0, 1.8);
        VehicleSpec van = vehicle("Test.StepVan", 1_160.0, 3_700.0, 70.0, 1.8);
        DriverInput launch = new DriverInput(
                1.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, 1, 1.0);
        DynamicsOutput sportsOutput = VehicleDynamics.step(sports,
                new DynamicsState(1, sports.engine().idleRpm(), 1.0, 0.0, 0.0, 0.0),
                launch, VehicleMotion.stopped(), DT);
        DynamicsOutput vanOutput = VehicleDynamics.step(van,
                new DynamicsState(1, van.engine().idleRpm(), 1.0, 0.0, 0.0, 0.0),
                launch, VehicleMotion.stopped(), DT);
        double sportsAcceleration = sportsOutput.longitudinalForceN() / sports.massKg();
        double vanAcceleration = vanOutput.longitudinalForceN() / van.massKg();
        check(sportsAcceleration > vanAcceleration * 1.10,
                "utility launch assist must not erase the sports car's performance advantage");

        LaunchResult sportsSecond = simulateAutomaticLaunch(sports, 120);
        LaunchResult vanSecond = simulateAutomaticLaunch(van, 120);
        check(sportsSecond.speedMps() > vanSecond.speedMps() * 1.10,
                "the acceleration difference must persist through an integrated two-second launch");
        check(vanSecond.gear() <= 2,
                "a step van must not rush through three gears during its first two seconds");

        DynamicsOutput vanLaunch = repeatedLaunch(van, 8);
        DynamicsOutput sportsLaunch = repeatedLaunch(sports, 8);
        check(!vanLaunch.burnout(),
                "a healthy low-performance van must put launch torque into motion instead of permanent wheelspin");
        check(sportsLaunch.burnout(),
                "a sufficiently powerful sports car must retain launch wheelspin");
    }

    private static DynamicsOutput repeatedLaunch(VehicleSpec spec, int frames) {
        DynamicsState state = new DynamicsState(1, spec.engine().idleRpm(), 1.0, 0.0, 0.0, 0.0);
        DynamicsOutput output = null;
        for (int frame = 0; frame < frames; frame++) {
            output = VehicleDynamics.step(spec, state,
                    new DriverInput(1.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, 1, 1.0),
                    VehicleMotion.stopped(), DT);
            state = output.state();
        }
        if (output == null) {
            throw new AssertionError("launch simulation must produce output");
        }
        return output;
    }

    private static LaunchResult simulateAutomaticLaunch(VehicleSpec spec, int frames) {
        DynamicsState state = DynamicsState.stopped(spec);
        double speed = 0.0;
        for (int frame = 0; frame < frames; frame++) {
            DynamicsOutput output = VehicleDynamics.step(spec, state,
                    new DriverInput(1.0, 0.0, 0.0, 0.0, TransmissionMode.AUTOMATIC, 1, 1.0),
                    new VehicleMotion(speed, 0.0, 0.0), DT);
            speed = Math.max(0.0, speed + output.longitudinalForceN() / spec.massKg() * DT);
            state = output.state();
        }
        return new LaunchResult(speed, state.gear());
    }

    private static void engagedHandbrakeBlocksPropulsion(VehicleSpec spec) {
        DynamicsOutput output = VehicleDynamics.step(spec,
                new DynamicsState(2, 2_500.0, 1.0, 0.0, 0.0, 0.0),
                new DriverInput(1.0, 0.0, 1.0, 0.0, TransmissionMode.MANUAL, 2, 1.0),
                VehicleMotion.stopped(), DT);
        near(output.rawDriveForceN(), 0.0, 1.0e-9,
                "a fully engaged handbrake must disconnect propulsion in every gear");
        near(output.longitudinalForceN(), 0.0, 1.0e-9,
                "a fully engaged handbrake must hold a stationary vehicle");
    }

    private static void neutralAndStationaryBrakeDoNotDrive(VehicleSpec spec) {
        DynamicsOutput neutral = VehicleDynamics.step(spec,
                new DynamicsState(0, spec.engine().idleRpm(), 1.0, 0.0, 0.0, 0.0),
                new DriverInput(1.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, 0, 1.0),
                VehicleMotion.stopped(), DT);
        near(neutral.rawDriveForceN(), 0.0, 1.0e-9,
                "neutral may free-rev but must not transmit wheel force");

        DynamicsOutput braking = VehicleDynamics.step(spec, DynamicsState.stopped(spec),
                new DriverInput(0.0, 1.0, 0.0, 0.0, TransmissionMode.MANUAL, 1, 1.0),
                VehicleMotion.stopped(), DT);
        near(braking.longitudinalForceN(), 0.0, 1.0e-9,
                "a stationary brake must not act as reverse propulsion");
    }

    private static void manualGearHasARealRevLimit(VehicleSpec spec) {
        VehicleMotion fast = new VehicleMotion(20.0, 0.0, 0.0);
        DynamicsOutput first = VehicleDynamics.step(spec,
                new DynamicsState(1, spec.engine().redlineRpm(), 1.0, 0.0, 20.0, 0.0),
                new DriverInput(1.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, 1, 1.0), fast, DT);
        DynamicsOutput third = VehicleDynamics.step(spec,
                new DynamicsState(3, 3_500.0, 1.0, 0.0, 20.0, 0.0),
                new DriverInput(1.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, 3, 1.0), fast, DT);
        check(Math.abs(first.rawDriveForceN()) < 1.0,
                "held first gear must cut drive force above redline");
        check(third.rawDriveForceN() > first.rawDriveForceN() + 500.0,
                "a valid higher gear must accelerate after first reaches its rev limit");
    }

    private static void reverseAndRoadGradeHavePhysicalDirection(VehicleSpec spec) {
        DynamicsState reverseState = new DynamicsState(-1, spec.engine().idleRpm(), 1.0, 0.0, 0.0, 0.0);
        DynamicsOutput reverse = VehicleDynamics.step(spec, reverseState,
                new DriverInput(1.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, -1, 1.0),
                VehicleMotion.stopped(), DT);
        check(reverse.rawDriveForceN() < 0.0 && reverse.longitudinalForceN() < 0.0,
                "reverse gear must apply force in the reverse body direction");

        DynamicsState coastState = new DynamicsState(2, 2_000.0, 0.0, 0.0, 8.0, 0.0);
        DriverInput coast = DriverInput.idle(TransmissionMode.MANUAL, 2);
        DynamicsOutput flat = VehicleDynamics.step(spec, coastState, coast,
                new VehicleMotion(8.0, 0.0, 0.0, 0.0), DT);
        DynamicsOutput uphill = VehicleDynamics.step(spec, coastState, coast,
                new VehicleMotion(8.0, 0.0, 0.0, 0.12), DT);
        check(uphill.longitudinalForceN() < flat.longitudinalForceN() - 1_000.0,
                "an uphill grade must oppose forward coasting instead of being feedback-cancelled");
    }

    private static void healthyStandardVehicleCanBreakTraction(VehicleSpec spec) {
        VehicleSpec standard = vehicle("Test.CarNormal", 800.0, 4_000.0, 90.0, 1.4);
        DynamicsOutput output = repeatedLaunch(standard, 8);
        check(output.burnout(), "ordinary dry-asphalt RWD vehicle must be able to break traction");
        check(output.wheelSlipMps() > 0.65, "burnout must include measurable driven-wheel slip");
        check(output.rawDriveForceN() > output.rearGripLimitN(), "burnout must come from force exceeding grip");
    }

    private static void tireStateChangesGripResistanceAndPull(VehicleSpec spec) {
        VehicleCondition healthy = VehicleCondition.healthy(spec);
        VehicleCondition damaged = new VehicleCondition(
                1.0, 1.0, 0.85, 250.0,
                List.of(
                        VehicleCondition.TireCondition.healthy("FrontRight"),
                        new VehicleCondition.TireCondition("FrontLeft", 0.25, 0.30, true, true),
                        VehicleCondition.TireCondition.healthy("RearLeft"),
                        VehicleCondition.TireCondition.healthy("RearRight")));
        DynamicsState state = new DynamicsState(1, 2_000.0, 0.8, 0.0, 8.0, 0.0);
        DriverInput input = new DriverInput(0.8, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, 1, 1.0);
        VehicleMotion motion = new VehicleMotion(8.0, 0.0, 0.0);
        DynamicsOutput good = VehicleDynamics.step(spec, state, input, motion, healthy, DT);
        DynamicsOutput bad = VehicleDynamics.step(spec, state, input, motion, damaged, DT);
        check(bad.frontTireGripMultiplier() < good.frontTireGripMultiplier() * 0.75,
                "puncture must reduce its axle grip");
        check(bad.rollingResistanceMultiplier() > good.rollingResistanceMultiplier() * 1.5,
                "low-pressure puncture must increase rolling resistance");
        check(Math.abs(bad.yawTorqueNm()) > Math.abs(good.yawTorqueNm()) + 1.0,
                "left/right tire mismatch must create vehicle pull");
    }

    private static void detectsPowerOversteerAndUndersteer(VehicleSpec spec) {
        VehicleCondition weakRear = new VehicleCondition(1.0, 1.0, 0.9, 0.0, List.of(
                VehicleCondition.TireCondition.healthy("FrontLeft"),
                VehicleCondition.TireCondition.healthy("FrontRight"),
                new VehicleCondition.TireCondition("RearLeft", 0.2, 0.45, true, true),
                new VehicleCondition.TireCondition("RearRight", 0.2, 0.45, true, true)));
        DriverInput fullThrottleTurn = new DriverInput(
                1.0, 0.0, 0.0, 0.45, TransmissionMode.MANUAL, 2, 1.0);
        VehicleMotion slidingTurn = new VehicleMotion(15.0, 2.0, 0.28);
        DynamicsState driftState = new DynamicsState(2, 4_000.0, 1.0, 0.18, 15.0, 0.0);
        DynamicsOutput shortTurn = VehicleDynamics.step(
                spec, driftState, fullThrottleTurn, slidingTurn, weakRear, DT);
        check(!shortTurn.drifting(),
                "a short steering input must not immediately become a power drift");
        DynamicsOutput sustainedTurn = shortTurn;
        driftState = shortTurn.state();
        for (int frame = 1; frame < 90; frame++) {
            sustainedTurn = VehicleDynamics.step(
                    spec, driftState, fullThrottleTurn, slidingTurn, weakRear, DT);
            driftState = sustainedTurn.state();
        }
        check(!sustainedTurn.drifting(),
                "power drift must not arm before 1.5 seconds of continuous full-throttle steering");
        for (int frame = 90; frame < 132; frame++) {
            sustainedTurn = VehicleDynamics.step(
                    spec, driftState, fullThrottleTurn, slidingTurn, weakRear, DT);
            driftState = sustainedTurn.state();
        }
        check(sustainedTurn.drifting(),
                "sustained full-throttle steering with rear slip must register power drift");

        DynamicsState partialState = new DynamicsState(2, 4_000.0, 0.8, 0.18, 15.0, 0.0);
        DynamicsOutput partialTurn = null;
        DriverInput partialThrottleTurn = new DriverInput(
                0.80, 0.0, 0.0, 0.45, TransmissionMode.MANUAL, 2, 1.0);
        for (int frame = 0; frame < 120; frame++) {
            partialTurn = VehicleDynamics.step(
                    spec, partialState, partialThrottleTurn, slidingTurn, weakRear, DT);
            partialState = partialTurn.state();
        }
        check(partialTurn != null && !partialTurn.drifting(),
                "partial throttle alone must not arm power drift");

        DynamicsOutput handbrakeTurn = VehicleDynamics.step(spec,
                new DynamicsState(2, 3_000.0, 0.0, 0.18, 15.0, 0.0),
                new DriverInput(0.0, 0.0, 1.0, 0.45, TransmissionMode.MANUAL, 2, 1.0),
                slidingTurn, weakRear, DT);
        check(handbrakeTurn.drifting(),
                "handbrake plus steering and rear slip must initiate a drift without throttle dwell");

        VehicleCondition weakFront = new VehicleCondition(1.0, 1.0, 0.9, 0.0, List.of(
                new VehicleCondition.TireCondition("FrontLeft", 0.1, 0.4, true, true),
                new VehicleCondition.TireCondition("FrontRight", 0.1, 0.4, true, true),
                VehicleCondition.TireCondition.healthy("RearLeft"),
                VehicleCondition.TireCondition.healthy("RearRight")));
        DynamicsOutput understeer = VehicleDynamics.step(spec,
                new DynamicsState(3, 3_000.0, 0.2, 0.35, 15.0, 0.0),
                new DriverInput(0.2, 0.0, 0.0, 0.8, TransmissionMode.MANUAL, 3, 1.0),
                new VehicleMotion(15.0, 0.0, 0.0), weakFront, DT);
        check(understeer.understeering(), "front saturation must register understeer");
    }

    private static void burnoutFollowsGearAndSurface() {
        VehicleSpec sports = vehicle("Test.Sports", 800.0, 7_000.0, 120.0, 1.8);
        DynamicsOutput dryThird = repeatedGearLoad(sports, 3, 10.5, 1.0, 30);
        check(!dryThird.burnout(),
                "dry-asphalt burnout must not persist into third gear merely because the engine is powerful");

        DynamicsOutput looseThird = repeatedGearLoad(sports, 3, 10.5, 0.45, 30);
        check(looseThird.burnout(),
                "third-gear wheelspin must remain physically possible on a sufficiently low-grip surface");
    }

    private static DynamicsOutput repeatedGearLoad(
            VehicleSpec spec, int gear, double speedMps, double surfaceGrip, int frames) {
        DynamicsState state = new DynamicsState(
                gear, 3_000.0, 1.0, 0.0, speedMps, 0.0);
        DynamicsOutput output = null;
        for (int frame = 0; frame < frames; frame++) {
            output = VehicleDynamics.step(spec, state,
                    new DriverInput(1.0, 0.0, 0.0, 0.0, TransmissionMode.MANUAL, gear, surfaceGrip),
                    new VehicleMotion(speedMps, 0.0, 0.0), DT);
            state = output.state();
        }
        if (output == null) {
            throw new AssertionError("gear-load simulation must produce output");
        }
        return output;
    }

    private static void steeringAppliesAndReturnsProgressively(VehicleSpec spec) {
        DynamicsState state = DynamicsState.stopped(spec);
        DriverInput turn = new DriverInput(0.0, 0.0, 0.0, 1.0, TransmissionMode.MANUAL, 1, 1.0);
        DynamicsOutput first = VehicleDynamics.step(spec, state, turn, VehicleMotion.stopped(), DT);
        check(first.state().steeringAngleRadians() > 0.0, "steering input must move the wheels");
        check(first.state().steeringAngleRadians() < spec.steering().maximumAngleRadians(),
                "steering input must be rate-limited");
        DynamicsOutput returned = VehicleDynamics.step(spec, first.state(),
                DriverInput.idle(TransmissionMode.MANUAL, 1), VehicleMotion.stopped(), DT);
        check(returned.state().steeringAngleRadians() < first.state().steeringAngleRadians(),
                "released steering must progressively return toward center");

        DynamicsState highSpeedState = new DynamicsState(4, 3_500.0, 0.0, 0.30, 30.0, 0.0);
        DynamicsOutput highSpeed = null;
        for (int frame = 0; frame < 60; frame++) {
            highSpeed = VehicleDynamics.step(spec, highSpeedState,
                    new DriverInput(0.0, 0.0, 0.0, 1.0, TransmissionMode.MANUAL, 4, 1.0),
                    new VehicleMotion(30.0, 0.0, 0.0), DT);
            highSpeedState = highSpeed.state();
        }
        check(highSpeed != null && highSpeed.state().steeringAngleRadians() < 0.14,
                "high-speed steering angle must be bounded for keyboard control stability");
    }

    private static void shortSteeringTapBuildsLateralForceProgressively(VehicleSpec spec) {
        DynamicsState state = new DynamicsState(4, 3_200.0, 0.0, 0.0, 20.0, 0.0);
        DriverInput tap = new DriverInput(
                0.0, 0.0, 0.0, 1.0, TransmissionMode.MANUAL, 4, 1.0);
        VehicleMotion motion = new VehicleMotion(20.0, 0.0, 0.0);
        double normal = spec.massKg() * 9.80665;
        DynamicsOutput first = VehicleDynamics.step(spec, state, tap, motion, DT);
        check(Math.abs(first.lateralForceN()) < normal * 0.04,
                "one steering frame must not request full lateral grip");
        check(Math.abs(first.yawTorqueNm())
                        < normal * spec.chassis().wheelbaseMeters() * 0.04,
                "one steering frame must not inject a full yaw impulse");

        DynamicsOutput shortTap = first;
        state = first.state();
        for (int frame = 1; frame < 6; frame++) {
            shortTap = VehicleDynamics.step(spec, state, tap, motion, DT);
            state = shortTap.state();
        }
        check(Math.abs(shortTap.lateralForceN()) < normal * 0.20,
                "a 100 ms keyboard tap must retain progressive lateral response");
        check(Math.abs(shortTap.yawTorqueNm())
                        < normal * spec.chassis().wheelbaseMeters() * 0.17,
                "a 100 ms keyboard tap must retain progressive yaw response");
    }

    private static void sanitizesNonFiniteRuntimeValues(VehicleSpec spec) {
        DynamicsOutput output = VehicleDynamics.step(spec, null,
                new DriverInput(Double.NaN, Double.POSITIVE_INFINITY, -5.0, Double.NaN,
                        null, 99, Double.NaN),
                new VehicleMotion(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY),
                new VehicleCondition(Double.NaN, 5.0, -2.0, Double.POSITIVE_INFINITY, null),
                Double.NaN);
        check(Double.isFinite(output.longitudinalForceN()), "longitudinal force must remain finite");
        check(Double.isFinite(output.lateralForceN()), "lateral force must remain finite");
        check(Double.isFinite(output.yawTorqueNm()), "yaw torque must remain finite");
    }

    private static void lowSpeedSteeringCannotSpinParkedVehicle(VehicleSpec spec) {
        DynamicsOutput output = VehicleDynamics.step(spec,
                new DynamicsState(1, spec.engine().idleRpm(), 0.0, 0.4, 0.3, 0.0),
                new DriverInput(0.0, 0.0, 0.0, 1.0, TransmissionMode.MANUAL, 1, 1.0),
                new VehicleMotion(0.30, 0.0, 0.0), DT);
        check(Math.abs(output.yawTorqueNm()) < 150.0,
                "near-standstill steering must not generate a spin impulse");
    }

    private static void deterministicReplay(VehicleSpec spec) {
        DynamicsState a = DynamicsState.stopped(spec);
        DynamicsState b = DynamicsState.stopped(spec);
        DynamicsOutput outA = null;
        DynamicsOutput outB = null;
        for (int frame = 0; frame < 240; frame++) {
            double steer = frame < 100 ? 0.35 : -0.15;
            DriverInput input = new DriverInput(0.82, 0.0, 0.0, steer, TransmissionMode.MANUAL, 2, 0.92);
            VehicleMotion motion = new VehicleMotion(12.0 + frame * 0.01, 0.4, 0.04);
            outA = VehicleDynamics.step(spec, a, input, motion, DT);
            outB = VehicleDynamics.step(spec, b, input, motion, DT);
            a = outA.state();
            b = outB.state();
        }
        check(outA != null && outB != null, "replay must produce output");
        check(outA.equals(outB), "identical replay inputs must produce byte-for-byte equal records");
    }

    private static void near(double actual, double expected, double tolerance, String message) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record LaunchResult(double speedMps, int gear) {
    }
}
