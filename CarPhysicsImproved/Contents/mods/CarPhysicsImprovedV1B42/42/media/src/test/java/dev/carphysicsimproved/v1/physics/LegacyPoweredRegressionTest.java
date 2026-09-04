package dev.carphysicsimproved.v1.physics;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Exact positive-power output/state baseline captured from the shipped 0.4.22 JAR. */
public final class LegacyPoweredRegressionTest {
    private static final String EXPECTED = "372328e1ff8b6383b799bae31c4935e2a1662753aee25af6865a71c5a263586a";

    private LegacyPoweredRegressionTest() {
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance("SHA-256");
        var buffer = ByteBuffer.allocate(8);
        int steps = 0;
        for (int type = 1; type <= 3; type++) {
            for (int gears : new int[]{3, 4, 5}) {
                for (boolean manual : new boolean[]{false, true}) {
                    for (double dt : new double[]{1.0 / 30, 1.0 / 60, 1.0 / 120}) {
                        for (boolean offroad : new boolean[]{false, true}) {
                            for (var driver : new LegacyDriverTraits.Modifiers[]{LegacyDriverTraits.normal(),
                                    LegacyDriverTraits.modifiers(true, false), LegacyDriverTraits.modifiers(false, true)}) {
                                var spec = new LegacyPhysics.Spec("Base.Baseline" + type, 900 + type * 400,
                                        160 + type * 120, 120, 700, 4500, 4350, 3,
                                        LegacyPhysics.legacyRatios(gears), 20, .55, 1, type);
                                var conditions = new LegacyPhysics.Conditions(offroad ? .5 : 1,
                                        offroad ? .4 : 1, offroad ? .6 : 1, offroad, offroad ? .55 : 1);
                                var state = new LegacyPhysics.State();
                                for (int tick = 0; tick < 240; tick++) {
                                    int phase = tick / 30;
                                    double speed = phase == 7 ? -2 : phase == 0 ? 0 : phase == 2 ? 28 : 5;
                                    int gear = phase == 3 ? 0 : phase == 7 ? -1 : phase == 2 ? gears : 1;
                                    boolean throttle = phase < 5 || phase == 7;
                                    var input = new LegacyPhysics.Input(speed, tick < 225 || tick >= 235,
                                            throttle && (manual || gear >= 0), throttle && !manual && gear < 0,
                                            phase == 6, tick >= 195 && tick < 210,
                                            tick % 60 < 30 ? .8 : -.8, throttle ? 1 : 0, manual, gear, true, driver);
                                    var out = LegacyPhysics.step(spec, conditions, LegacyPhysics.Settings.defaults(),
                                            input, state, dt);
                                    hash(digest, buffer, out.gear(), out.engineForce(), out.brakingForce(),
                                            out.steeringRadians(), out.dragMagnitude(), out.tireTraction(),
                                            out.burnoutSpeedKph(), out.engineRpm(), out.throttle(), out.rawDriveForce(),
                                            out.clutchKickIntensity(), state.gear, state.engineRpm, state.throttle,
                                            state.fullThrottleSeconds, state.steering, state.burnout,
                                            state.lastStepGear, state.clutchKick);
                                    steps++;
                                }
                            }
                        }
                    }
                }
            }
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        System.out.println("LegacyPoweredRegressionTest: " + steps + " steps; SHA-256 " + actual);
        if (!(args.length == 1 && "--capture".equals(args[0])) && !EXPECTED.equals(actual)) {
            throw new AssertionError("Positive-power behavior differs from the 0.4.22 baseline");
        }
    }

    private static void hash(MessageDigest digest, ByteBuffer buffer, double... values) {
        for (double value : values) {
            buffer.clear();
            buffer.putLong(Double.doubleToLongBits(value));
            digest.update(buffer.array());
        }
    }
}
