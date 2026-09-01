package dev.carphysicsimproved.v2.physics;

import java.util.List;

/** Runtime wear/load state. Values outside their range are safely clamped. */
public record VehicleCondition(
        double engineCondition,
        double brakesCondition,
        double suspensionCondition,
        double payloadKg,
        List<TireCondition> tires) {

    public VehicleCondition {
        tires = tires == null ? List.of() : List.copyOf(tires);
    }

    public static VehicleCondition healthy(VehicleSpec specification) {
        return new VehicleCondition(
                1.0,
                1.0,
                1.0,
                0.0,
                specification.wheels().stream()
                        .map(wheel -> TireCondition.healthy(wheel.id()))
                        .toList());
    }

    public TireCondition tire(String wheelId) {
        if (wheelId != null) {
            for (TireCondition tire : tires) {
                if (tire != null && wheelId.equalsIgnoreCase(tire.wheelId())) {
                    return tire.sanitized();
                }
            }
        }
        return TireCondition.healthy(wheelId);
    }

    public double sanitizedEngineCondition() {
        return VehicleSpec.clamp(VehicleSpec.finite(engineCondition, 1.0), 0.0, 1.0);
    }

    public double sanitizedBrakesCondition() {
        return VehicleSpec.clamp(VehicleSpec.finite(brakesCondition, 1.0), 0.0, 1.0);
    }

    public double sanitizedSuspensionCondition() {
        return VehicleSpec.clamp(VehicleSpec.finite(suspensionCondition, 1.0), 0.0, 1.0);
    }

    public double sanitizedPayloadKg() {
        return VehicleSpec.clamp(VehicleSpec.finite(payloadKg, 0.0), 0.0, 8_000.0);
    }

    public record TireCondition(
            String wheelId,
            double condition,
            double pressure,
            boolean punctured,
            boolean installed,
            double compoundGripMultiplier) {

        public TireCondition(
                String wheelId,
                double condition,
                double pressure,
                boolean punctured,
                boolean installed) {
            this(wheelId, condition, pressure, punctured, installed, 1.0);
        }

        public static TireCondition healthy(String wheelId) {
            return new TireCondition(wheelId, 1.0, 1.0, false, true, 1.0);
        }

        TireCondition sanitized() {
            return new TireCondition(
                    wheelId,
                    VehicleSpec.clamp(VehicleSpec.finite(condition, 1.0), 0.0, 1.0),
                    VehicleSpec.clamp(VehicleSpec.finite(pressure, 1.0), 0.0, 1.35),
                    punctured,
                    installed,
                    VehicleSpec.clamp(VehicleSpec.finite(compoundGripMultiplier, 1.0), 0.45, 1.65));
        }

        public double gripMultiplier() {
            TireCondition value = sanitized();
            if (!value.installed()) {
                return 0.04;
            }
            double wear = 0.30 + 0.70 * Math.sqrt(value.condition());
            double pressureLoss = Math.abs(value.pressure() - 1.0);
            double pressureGrip = VehicleSpec.clamp(1.0 - pressureLoss * 0.62, 0.28, 1.0);
            double puncture = value.punctured() ? 0.18 : 1.0;
            return wear * pressureGrip * puncture * value.compoundGripMultiplier();
        }

        public double corneringMultiplier() {
            TireCondition value = sanitized();
            double pressureShape = VehicleSpec.clamp(0.20 + value.pressure() * 0.80, 0.20, 1.05);
            return gripMultiplier() * pressureShape;
        }

        public double rollingResistanceMultiplier() {
            TireCondition value = sanitized();
            if (!value.installed()) {
                return 9.0;
            }
            return 1.0
                    + (1.0 - value.pressure()) * 2.8
                    + (1.0 - value.condition()) * 0.45
                    + (value.punctured() ? 4.5 : 0.0);
        }
    }
}
