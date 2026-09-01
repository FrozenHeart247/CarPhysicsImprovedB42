package zombie.roadcraft.physics;

/**
 * Driver and environment inputs for one simulation step.
 *
 * <p>Pedal values, tire condition, wetness and off-road fraction use the range
 * {@code [0, 1]}; steering uses {@code [-1, 1]}. The model sanitizes values at
 * its boundary. In automatic mode {@code requestedGear} is a selector:
 * negative is reverse, zero is neutral and positive is drive. In manual mode
 * it is the exact gear number ({@code -1, 0, 1..N}).</p>
 */
public record VehicleInput(
        double throttle,
        double serviceBrake,
        double parkingBrake,
        double steering,
        boolean automaticTransmission,
        int requestedGear,
        double tireCondition,
        double wetness,
        double offroadFraction) {

    public static VehicleInput idle(boolean automaticTransmission) {
        return new VehicleInput(
                0.0,
                0.0,
                0.0,
                0.0,
                automaticTransmission,
                0,
                1.0,
                0.0,
                0.0);
    }
}
