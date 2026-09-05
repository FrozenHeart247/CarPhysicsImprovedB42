package dev.carphysicsimproved.v1.physics;

/** Numerical reference-equation checks, not a Bullet driving simulation. */
public final class LegacyKeyDriftTest {
    private static final LegacyKeyDrift.Tuning TUNING = LegacyKeyDrift.Tuning.defaults();
    private LegacyKeyDriftTest() { }

    public static void main(String[] args) {
        activation();
        massAndTorque();
        steeringReference();
        frictionReference();
        driveIsolation();
        System.out.println("LegacyKeyDriftTest: reference gates, fixed own-body yaw calibration, steering, "
                + "grip, release and drivetrain isolation passed");
    }

    private static void activation() {
        check(!LegacyKeyDrift.active(false, 60, 1, TUNING), "Release/unsafe means off");
        check(!LegacyKeyDrift.active(true, 20, 1, TUNING), "Reference speed threshold is strict");
        check(!LegacyKeyDrift.active(true, 60, .25, TUNING), "Reference steering threshold is strict");
        check(LegacyKeyDrift.active(true, 20.01, .251, TUNING), "No entry delay");
        check(LegacyKeyDrift.active(true, 140, 1, TUNING), "No old 100 km/h cutoff");
        check(LegacyKeyDrift.active(true, -60, -1, TUNING), "Uses absolute native speed, no gear gate");
        check(!LegacyKeyDrift.active(true, Double.NaN, 1, TUNING), "Invalid speed cannot enable drift");
        check(!LegacyKeyDrift.active(true, 60, Double.NaN, TUNING), "Invalid steering cannot enable drift");
    }

    private static void massAndTorque() {
        for (double mass : new double[] { 500, 1041, 1200, 2500, 6000 }) {
            for (double steer : new double[] { -1, -.5, 0, .5, 1 }) {
                for (double intensity : new double[] { 0, .5, 1, 2 }) {
                    for (double rotation : new double[] { 0, 1000, 2000, 3000 }) {
                        var tuning = new LegacyKeyDrift.Tuning(rotation, .35, 1.5, 20);
                        double expected = steer * rotation * mass * .001 * 1.96 * intensity;
                        double command = LegacyKeyDrift.torque(true, steer, mass, intensity, tuning);
                        near(command, expected, "Fixed calibration with sandbox scaling");
                        near(command / mass, steer * rotation * .001 * 1.96 * intensity,
                                "Only own-body mass compensation; no world normalization");
                        near(LegacyKeyDrift.torque(true, -steer, mass, intensity, tuning), -command,
                                "Immediate countersteer, no sign calibration or delayed torque");
                        near(LegacyKeyDrift.torque(false, steer, mass, intensity, tuning), 0,
                                "Release has no tail");
                    }
                }
            }
        }
        // Captured accepted run: RaceCar58, rot=2000, refMassScale~.511,
        // yawCmd~4075. Rounded telemetry is a calibration target, not an exact oracle.
        double raceCarCommand = LegacyKeyDrift.torque(true, 1, 1041, 1, TUNING);
        check(Math.abs(raceCarCommand / 4075 - 1) < .002, "Accepted RaceCar command within 0.2 percent");
        for (double mass : new double[] { 0, -1, Double.NaN, Double.POSITIVE_INFINITY }) {
            near(LegacyKeyDrift.torque(true, 1, mass, 1, TUNING), 0, "Invalid own mass fails closed");
        }
    }

    private static void steeringReference() {
        LegacyPhysics.Settings settings = LegacyPhysics.Settings.defaults();
        for (double speed : new double[] { 0, 20, 50, 75, 120 }) {
            for (int fps : new int[] { 30, 60, 120 }) {
                double ours = 0, reference = 0;
                double dt = 1.0 / fps;
                for (int frame = 0; frame < fps * 3; frame++) {
                    double input = frame < fps ? 1 : frame < fps * 2 ? -1 : 0;
                    double speedRatio = Math.min(1, Math.abs(speed) / 75);
                    double gain = 1 + (.1 - 1) * speedRatio;
                    double center = gain;
                    double nativeInput = -input;
                    if (Math.abs(nativeInput) > .1) {
                        if ((nativeInput < 0) == (reference < 0)) gain *= 3;
                        reference -= (nativeInput + reference) * 3 * dt * gain * 1.5;
                    } else if (Math.abs(reference) <= .04) {
                        reference = 0;
                    } else if (reference > 0) {
                        reference = Math.max(0, reference - center * 4 * dt);
                    } else {
                        reference = Math.min(0, reference + center * 4 * dt);
                    }
                    reference = Math.max(-.55, Math.min(.55, reference));
                    ours = LegacyKeyDrift.steering(ours, input, speed, .55, 1, dt, settings, TUNING);
                    near(ours, reference, "Reference tunable steering sequence");
                }
            }
        }
        double damaged = LegacyKeyDrift.steering(0, 1, 60, .55, .10, .05, settings, TUNING);
        double healthy = LegacyKeyDrift.steering(0, 1, 60, .55, 1, .05, settings, TUNING);
        check(damaged < healthy, "Existing bad-tire steering penalty remains");
    }

    private static void frictionReference() {
        for (double base : new double[] { .5, 1.2, 1.8, 3, 5 }) {
            for (double grip : new double[] { .08, .3, .7, 1, 1.125, 1.8 }) {
                double expected = Math.min(1.8, base * grip) * .35;
                near(LegacyKeyDrift.friction(base, grip, .35), expected, "Cap before drift multiplier");
            }
        }
        near(LegacyKeyDrift.gripMultiplier(1, TUNING), .35, "Reference default");
        near(LegacyKeyDrift.gripMultiplier(0, TUNING), 1, "Zero intensity restores grip");
    }

    private static void driveIsolation() {
        LegacyPhysics.Spec car = new LegacyPhysics.Spec("Test.Sport", 1250, 250, 120, 700, 4500, 4350,
                3.2, LegacyPhysics.legacyRatios(5), 100, .9, 1, 3);
        for (boolean manual : new boolean[] { false, true }) {
            for (int gear : new int[] { -1, 0, 1, 3, 5 }) {
                for (boolean throttle : new boolean[] { false, true }) {
                    LegacyPhysics.Output drive = LegacyPhysics.step(car,
                            new LegacyPhysics.Conditions(1, 1, 1, false), LegacyPhysics.Settings.defaults(),
                            new LegacyPhysics.Input(18, true, throttle, false, false, false, 1, 1, manual, gear),
                            new LegacyPhysics.State(), .02);
                    LegacyPhysics.Output keyDrive = LegacyKeyDrift.withSteering(drive, .3);
                    check(LegacyKeyDrift.withSteering(keyDrive, drive.steeringRadians()).equals(drive),
                            "All drivetrain/braking/coast/burnout/RPM fields must be byte-for-byte values");
                }
            }
        }
        var off = LegacyKeyDrift.observation(false, 0, 2000, .35, 20, 4, .2);
        check(off.bulletYawTorque() == 0 && off.lateralForce() == 0 && off.wheelFrictionScale() == 1,
                "Release or straight input cannot keep any command active");
    }

    private static void near(double actual, double expected, String message) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) < 1e-8, message + ": " + actual + " / " + expected);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
