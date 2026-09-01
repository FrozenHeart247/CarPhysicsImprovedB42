package zombie.roadcraft.physics;

import java.util.Objects;

/** State carried between deterministic simulation steps. */
public record VehicleState(double speedMps, double engineRpm, int gear, double wheelSlip) {

    public static VehicleState stopped(PhysicsSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return new VehicleState(0.0, settings.engine().idleRpm(), 0, 0.0);
    }
}
