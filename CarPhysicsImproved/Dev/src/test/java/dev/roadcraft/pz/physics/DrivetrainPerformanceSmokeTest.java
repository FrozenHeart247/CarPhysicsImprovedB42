package zombie.roadcraft.physics;

/** Simple repeatable load smoke test; it is not a substitute for the in-game profiler. */
public final class DrivetrainPerformanceSmokeTest {
    private static volatile double blackhole;

    private DrivetrainPerformanceSmokeTest() {
    }

    public static void main(String[] arguments) {
        runCase(1, 20_000, true);
        runCase(10, 2_000, true);
        for (int vehicles : new int[] {1, 10, 50}) {
            Measurement measurement = runCase(vehicles, 10_000, false);
            System.out.printf(
                    "DrivetrainPerformanceSmokeTest: vehicles=%d updates=%d ns/update=%.1f totalMs=%.2f%n",
                    vehicles,
                    measurement.updates(),
                    measurement.nanosecondsPerUpdate(),
                    measurement.totalNanoseconds() / 1_000_000.0);
            if (!Double.isFinite(measurement.nanosecondsPerUpdate()) || measurement.nanosecondsPerUpdate() <= 0.0) {
                throw new AssertionError("Invalid timing result");
            }
        }
    }

    private static Measurement runCase(int vehicleCount, int frames, boolean warmup) {
        PhysicsSettings settings = PhysicsSettings.standard();
        VehicleState[] states = new VehicleState[vehicleCount];
        for (int index = 0; index < states.length; index++) {
            states[index] = VehicleState.stopped(settings);
        }
        VehicleInput input = new VehicleInput(0.72, 0.0, 0.0, 0.18, true, 1, 0.83, 0.22, 0.0);

        double sink = 0.0;
        long start = System.nanoTime();
        for (int frame = 0; frame < frames; frame++) {
            for (int index = 0; index < states.length; index++) {
                VehicleOutput output = DrivetrainModel.step(settings, states[index], input, 1.0 / 60.0);
                states[index] = output.state();
                sink += output.appliedDriveForceN() * 1.0e-300;
            }
        }
        long elapsed = System.nanoTime() - start;
        blackhole = sink;
        long updates = (long) vehicleCount * frames;
        if (warmup) {
            return new Measurement(updates, elapsed, 0.0);
        }
        return new Measurement(updates, elapsed, elapsed / (double) updates);
    }

    private record Measurement(long updates, long totalNanoseconds, double nanosecondsPerUpdate) {
    }
}
