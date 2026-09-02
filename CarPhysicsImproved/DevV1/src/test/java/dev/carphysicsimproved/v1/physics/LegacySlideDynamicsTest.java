package dev.carphysicsimproved.v1.physics;

public final class LegacySlideDynamicsTest {
    private static final double DT = 0.05;
    private static final LegacySlideDynamics.Spec CAR =
            new LegacySlideDynamics.Spec(1_250.0, 2.5, 0.52, 0.55, 1.0);
    private static final LegacySlideDynamics.AxleGrip ROAD =
            new LegacySlideDynamics.AxleGrip(1.0, 1.0);
    private static final LegacySlideDynamics.Tuning TUNING = LegacySlideDynamics.Tuning.defaults();

    private LegacySlideDynamicsTest() {
    }

    public static void main(String[] args) {
        normalCornerHasNoOverlay();
        steeringTapDoesNotStartSlide();
        sustainedPowerCanStartDrift();
        handbrakeNeedsSpeedAndSteering();
        releasedDriftRetainsGripBriefly();
        targetSlipAngleReducesRotationCommand();
        naturalRearGripLossCanStartSlide();
        impactCanBecomeNaturalSlide();
        leftAndRightCommandsAreSymmetric();
        highSpeedStableTurnHasNoOverlay();
        releasedDriftRecoversToGrip();
        clutchKickCanInitiateButWeakLaunchCannot();
        System.out.println("LegacySlideDynamicsTest: all slide-controller invariants passed");
    }

    private static void normalCornerHasNoOverlay() {
        LegacySlideDynamics.State state = new LegacySlideDynamics.State();
        LegacySlideDynamics.Output output = null;
        for (int index = 0; index < 30; index++) {
            output = step(state, ROAD, input(12.0, 0.15, -0.38, 0.08,
                    0.35, 0.0, 0.0, 2, 0.30, 0.0, 0.0, false));
            check(output.mode() == LegacySlideDynamics.Mode.GRIP
                            || output.mode() == LegacySlideDynamics.Mode.LIMIT,
                    "ordinary corner must remain inside the grip states");
            check(output.lateralForce() == 0.0 && output.bulletYawTorque() == 0.0,
                    "ordinary corner must not receive overlay forces");
            check(!output.intentionalSlide() && output.wheelFrictionScale() == 1.0,
                    "ordinary corner must retain native wheel friction");
        }
        check(output != null && output.yawSignCalibrated(),
                "normal steering observations must calibrate the yaw sign");
    }

    private static void steeringTapDoesNotStartSlide() {
        LegacySlideDynamics.State state = calibratedState();
        LegacySlideDynamics.Output tap = step(state, ROAD, input(18.0, 0.0, -0.55, 0.20,
                1.0, 0.0, 0.0, 1, 1.25, 0.0, 0.0, false));
        LegacySlideDynamics.Output released = step(state, ROAD, input(18.0, 0.0, 0.0, 0.0,
                1.0, 0.0, 0.0, 1, 1.25, 0.0, 0.0, false));
        check(tap.mode() != LegacySlideDynamics.Mode.SLIDE
                        && tap.mode() != LegacySlideDynamics.Mode.CONTROLLED,
                "one steering tap must not arm a slide");
        check(released.bulletYawTorque() == 0.0,
                "released steering tap must not leave a yaw command");
    }

    private static void sustainedPowerCanStartDrift() {
        LegacySlideDynamics.State state = calibratedState();
        LegacySlideDynamics.Output output = null;
        for (int index = 0; index < 24; index++) {
            output = step(state, ROAD, input(10.0, 0.0, -0.72, 0.18,
                    1.0, 0.0, 0.0, 1, 1.18, 0.0, 0.0, false));
        }
        check(output != null && (output.mode() == LegacySlideDynamics.Mode.SLIDE
                        || output.mode() == LegacySlideDynamics.Mode.CONTROLLED),
                "sustained full-throttle cornering may initiate power oversteer");
        check(output.cause() == LegacySlideDynamics.Cause.POWER,
                "power-oversteer entry must retain its diagnostic cause");
        check(output.bulletYawTorque() != 0.0,
                "calibrated intentional drift must produce a bounded yaw command");
        check(Math.abs(output.bulletYawTorque()) < CAR.massKg() * 9.80665 * CAR.wheelbaseMeters() * 0.251,
                "intentional yaw command exceeded its safety envelope");
    }

    private static void handbrakeNeedsSpeedAndSteering() {
        LegacySlideDynamics.State straight = calibratedState();
        LegacySlideDynamics.Output straightOutput = null;
        for (int index = 0; index < 10; index++) {
            straightOutput = step(straight, ROAD, input(12.0, 0.0, 0.0, 0.0,
                    0.2, 0.0, 1.0, 2, 0.2, 0.0, 0.3, false));
        }
        check(straightOutput != null && straightOutput.mode() != LegacySlideDynamics.Mode.SLIDE,
                "handbrake on a straight road must not inject rotation");

        LegacySlideDynamics.State turning = calibratedState();
        LegacySlideDynamics.Output turningOutput = null;
        for (int index = 0; index < 5; index++) {
            turningOutput = step(turning, ROAD, input(12.0, 0.2, -0.45, 0.12,
                    0.2, 0.0, 1.0, 2, 0.2, 0.0, 0.3, false));
        }
        check(turningOutput != null && (turningOutput.mode() == LegacySlideDynamics.Mode.SLIDE
                        || turningOutput.mode() == LegacySlideDynamics.Mode.CONTROLLED),
                "held handbrake plus steering must be able to initiate a slide");
        check(turningOutput.cause() == LegacySlideDynamics.Cause.HANDBRAKE,
                "handbrake entry must retain its diagnostic cause");
        check(turningOutput.bulletYawTorque() != 0.0,
                "held handbrake plus steering must produce a physical rotation command");
        check(turningOutput.bulletYawTorque()
                        * turningOutput.expectedYawRateRadiansPerSecond() < 0.0,
                "Bullet yaw command must reinforce the requested turn using its measured inverse sign");
        check(turningOutput.intentionalSlide()
                        && turningOutput.wheelFrictionScale() >= 0.25
                        && turningOutput.wheelFrictionScale() < 0.50,
                "handbrake drift must temporarily reduce the native wheel-friction budget");
    }

    private static void releasedDriftRetainsGripBriefly() {
        LegacySlideDynamics.State state = calibratedState();
        LegacySlideDynamics.Output output = null;
        for (int index = 0; index < 5; index++) {
            output = step(state, ROAD, input(12.0, 0.2, -0.45, 0.12,
                    0.2, 0.0, 1.0, 2, 0.2, 0.0, 0.3, false));
        }
        check(output != null && output.driftGripActive(),
                "an initiated drift must enter its reduced-grip phase");

        LegacySlideDynamics.Output firstReleased = step(state, ROAD, input(12.0, 0.2, -0.45, 0.0,
                0.0, 0.0, 0.0, 2, 0.0, 0.0, 0.2, false));
        check(!firstReleased.intentionalSlide() && firstReleased.driftGripActive()
                        && firstReleased.wheelFrictionScale() < 1.0,
                "releasing the drift input must not restore full wheel grip in one physics frame");

        LegacySlideDynamics.Output settled = firstReleased;
        for (int index = 0; index < 10; index++) {
            settled = step(state, ROAD, input(12.0, 0.0, -0.10, 0.0,
                    0.0, 0.0, 0.0, 2, 0.0, 0.0, 0.0, false));
        }
        check(!settled.driftGripActive() && settled.wheelFrictionScale() == 1.0,
                "the short drift-grip hold must expire and restore native wheel friction");
    }

    private static void targetSlipAngleReducesRotationCommand() {
        LegacySlideDynamics.State shallowState = calibratedState();
        LegacySlideDynamics.State targetState = calibratedState();
        LegacySlideDynamics.Output shallow = null;
        LegacySlideDynamics.Output nearTarget = null;
        double targetLateralSpeed = Math.tan(Math.toRadians(15.5)) * 12.0;
        for (int index = 0; index < 7; index++) {
            shallow = step(shallowState, ROAD, input(12.0, 0.2, -0.45, 0.12,
                    0.2, 0.0, 1.0, 2, 0.2, 0.0, 0.3, false));
            nearTarget = step(targetState, ROAD, input(12.0, targetLateralSpeed, -0.45, 0.12,
                    0.2, 0.0, 1.0, 2, 0.2, 0.0, 0.3, false));
        }
        check(shallow != null && nearTarget != null
                        && Math.abs(nearTarget.bulletYawTorque()) < Math.abs(shallow.bulletYawTorque()),
                "rotation assistance must taper as the car approaches its target slip angle");
    }

    private static void naturalRearGripLossCanStartSlide() {
        LegacySlideDynamics.State state = calibratedState();
        LegacySlideDynamics.AxleGrip damagedRear = new LegacySlideDynamics.AxleGrip(1.0, 0.48);
        LegacySlideDynamics.Output output = null;
        for (int index = 0; index < 8; index++) {
            output = step(state, damagedRear, input(14.0, 1.1, -0.50, 0.13,
                    0.0, 0.25, 0.0, 2, 0.15, 0.0, 0.30, false));
        }
        check(output != null && (output.mode() == LegacySlideDynamics.Mode.SLIDE
                        || output.mode() == LegacySlideDynamics.Mode.CONTROLLED),
                "measured rear-grip loss must start a natural slide without drift intent");
        check(output.cause() == LegacySlideDynamics.Cause.BRAKING
                        || output.cause() == LegacySlideDynamics.Cause.TIRE_OR_SURFACE,
                "natural slide must report a physical cause");
        check(output.wheelFrictionScale() == 1.0,
                "natural slide detection must not rewrite native wheel friction");
    }

    private static void impactCanBecomeNaturalSlide() {
        LegacySlideDynamics.State state = calibratedState();
        LegacySlideDynamics.Output first = step(state, ROAD, input(11.0, 2.0, 0.55, 0.0,
                0.0, 0.0, 0.0, 2, 0.0, 0.0, 0.0, true));
        check(first.mode() == LegacySlideDynamics.Mode.SLIDE
                        && first.cause() == LegacySlideDynamics.Cause.IMPACT,
                "post-impact sideslip must be recognized without player intent");
        check(first.lateralForce() == 0.0 && first.bulletYawTorque() == 0.0,
                "a natural slide must get a short development window before recovery assistance");
        LegacySlideDynamics.Output output = first;
        for (int index = 0; index < 16; index++) {
            output = step(state, ROAD, input(11.0, 2.0, 0.55, 0.0,
                    0.0, 0.0, 0.0, 2, 0.0, 0.0, 0.0, true));
        }
        check(output.lateralForce() != 0.0 || output.bulletYawTorque() != 0.0,
                "a developed uncontrolled slide must receive bounded recovery assistance");
    }

    private static void leftAndRightCommandsAreSymmetric() {
        LegacySlideDynamics.State left = calibratedState();
        LegacySlideDynamics.State right = calibratedState();
        LegacySlideDynamics.Output leftOutput = null;
        LegacySlideDynamics.Output rightOutput = null;
        for (int index = 0; index < 24; index++) {
            leftOutput = step(left, ROAD, input(10.0, 0.0, -0.72, 0.18,
                    1.0, 0.0, 0.0, 1, 1.18, 0.0, 0.0, false));
            rightOutput = step(right, ROAD, input(10.0, 0.0, 0.72, -0.18,
                    1.0, 0.0, 0.0, 1, 1.18, 0.0, 0.0, false));
        }
        check(leftOutput != null && rightOutput != null,
                "symmetric slide outputs were not produced");
        check(Math.signum(leftOutput.bulletYawTorque()) == -Math.signum(rightOutput.bulletYawTorque()),
                "left/right yaw commands must use opposite signs");
        check(Math.abs(Math.abs(leftOutput.bulletYawTorque()) - Math.abs(rightOutput.bulletYawTorque())) < 0.001,
                "left/right yaw command magnitudes must match");
    }

    private static void highSpeedStableTurnHasNoOverlay() {
        LegacySlideDynamics.State state = calibratedState();
        LegacySlideDynamics.Output output = null;
        for (int index = 0; index < 30; index++) {
            output = step(state, ROAD, input(28.0, 0.0, -0.18, 0.035,
                    0.45, 0.0, 0.0, 4, 0.40, 0.0, 0.0, false));
        }
        check(output != null && output.mode() != LegacySlideDynamics.Mode.SLIDE
                        && output.mode() != LegacySlideDynamics.Mode.CONTROLLED,
                "stable high-speed cornering must not become an assisted drift");
        check(output.lateralForce() == 0.0 && output.bulletYawTorque() == 0.0,
                "stable high-speed cornering must preserve native V1 handling");
    }

    private static void releasedDriftRecoversToGrip() {
        LegacySlideDynamics.State state = calibratedState();
        for (int index = 0; index < 24; index++) {
            step(state, ROAD, input(10.0, 0.0, -0.72, 0.18,
                    1.0, 0.0, 0.0, 1, 1.18, 0.0, 0.0, false));
        }
        LegacySlideDynamics.Output output = null;
        for (int index = 0; index < 80; index++) {
            double fade = Math.max(0.0, 1.0 - index / 20.0);
            output = step(state, ROAD, input(9.0, 0.5 * fade, -0.12 * fade, 0.0,
                    0.0, 0.0, 0.0, 2, 0.0, 0.0, 0.0, false));
        }
        check(output != null && output.mode() == LegacySlideDynamics.Mode.GRIP,
                "released drift must recover to the normal grip state");
        check(output.lateralForce() == 0.0 && output.bulletYawTorque() == 0.0,
                "completed recovery must leave no residual body-force command");
    }

    private static void clutchKickCanInitiateButWeakLaunchCannot() {
        LegacySlideDynamics.State kickState = calibratedState();
        LegacySlideDynamics.Output kick = step(kickState, ROAD, input(9.0, 0.2, -0.35, 0.12,
                0.8, 0.0, 0.0, 1, 0.95, 0.70, 0.0, false));
        check(kick.mode() == LegacySlideDynamics.Mode.SLIDE
                        && kick.cause() == LegacySlideDynamics.Cause.CLUTCH_KICK,
                "a strong clutch kick while turning must be able to initiate rear slip");

        LegacySlideDynamics.State weakState = calibratedState();
        LegacySlideDynamics.Output weak = step(weakState, ROAD, input(9.0, 0.0, -0.30, 0.12,
                0.55, 0.0, 0.0, 3, 0.45, 0.08, 0.0, false));
        check(weak.mode() != LegacySlideDynamics.Mode.SLIDE,
                "a weak high-gear engagement must not be promoted to clutch-kick drift");
    }

    private static LegacySlideDynamics.State calibratedState() {
        LegacySlideDynamics.State state = new LegacySlideDynamics.State();
        for (int index = 0; index < 10; index++) {
            step(state, ROAD, input(10.0, 0.0, -0.25, 0.07,
                    0.25, 0.0, 0.0, 2, 0.20, 0.0, 0.0, false));
        }
        return state;
    }

    private static LegacySlideDynamics.Output step(LegacySlideDynamics.State state,
            LegacySlideDynamics.AxleGrip grip, LegacySlideDynamics.Input input) {
        LegacySlideDynamics.Output output = LegacySlideDynamics.step(CAR, grip, TUNING, input, state, DT);
        check(Double.isFinite(output.sideSlipAngleRadians())
                        && Double.isFinite(output.expectedYawRateRadiansPerSecond())
                        && Double.isFinite(output.frontGripUse())
                        && Double.isFinite(output.rearGripUse())
                        && Double.isFinite(output.lateralForce())
                        && Double.isFinite(output.bulletYawTorque()),
                "slide output contains a non-finite value");
        return output;
    }

    private static LegacySlideDynamics.Input input(double speed, double side, double yaw, double steering,
            double throttle, double serviceBrake, double handbrake, int gear, double driveUse,
            double clutchKick, double rearNativeSkid, boolean impact) {
        double tractionLimit = CAR.massKg() * 2.0;
        return new LegacySlideDynamics.Input(speed, side, yaw, steering, throttle,
                serviceBrake, handbrake, gear, driveUse * tractionLimit, tractionLimit,
                clutchKick, rearNativeSkid, impact);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
