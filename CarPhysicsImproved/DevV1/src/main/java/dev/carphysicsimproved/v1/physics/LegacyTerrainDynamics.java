package dev.carphysicsimproved.v1.physics;

/**
 * Computes environmental grip independently from drivetrain and steering.
 * Dry asphalt is an exact identity path for every vehicle profile.
 */
public final class LegacyTerrainDynamics {
    private static final double TERRAIN_EFFICIENCY_THRESHOLD = 1.05;

    private LegacyTerrainDynamics() {
    }

    public static Output evaluate(Input rawInput, Tuning rawTuning) {
        Input input = rawInput == null ? Input.defaults() : rawInput;
        Tuning tuning = rawTuning == null ? Tuning.defaults() : rawTuning;
        boolean terrainCapable = input.mechanicType() == 2
                || input.offroadEfficiency() >= TERRAIN_EFFICIENCY_THRESHOLD;

        double offroadFactor = 1.0;
        if (input.offroad()) {
            double pressureSeverity = 0.5 + clamp(input.tirePressure(), 0.0, 1.0) * 0.5;
            double roadFactor = 1.0 - (1.0 - tuning.offroadTraction()) * pressureSeverity;
            offroadFactor = recoverLostGrip(roadFactor,
                    terrainCapable ? tuning.heavyOffroadAdvantage() : 0.0);
        }

        double forestFactor = input.forest()
                ? recoverLostGrip(0.80, terrainCapable ? tuning.heavyOffroadAdvantage() : 0.0)
                : 1.0;
        double rainFactor = recoverLostGrip(
                lerp(1.0, tuning.rainTraction(), clamp(input.rainIntensity(), 0.0, 1.0)),
                terrainCapable ? tuning.heavyRainAdvantage() : 0.0);

        // Preserve the existing B42 snow activation threshold while removing
        // the old double application of script offRoadEfficiency.
        double snow = clamp(input.snowIntensity(), 0.0, 1.0);
        double snowBase = snow > 0.5
                ? 1.0 - (1.0 - tuning.snowTraction()) * snow
                : 1.0;
        double snowFactor = recoverLostGrip(snowBase,
                terrainCapable ? tuning.heavySnowAdvantage() : 0.0);

        double surfaceGrip = clamp(offroadFactor * forestFactor * rainFactor * snowFactor, 0.10, 1.0);
        double resistanceScale = input.offroad() && terrainCapable
                ? tuning.heavyOffroadResistanceScale()
                : 1.0;
        double nativeWheelFrictionScale = clamp(lerp(
                1.0, surfaceGrip, tuning.nativeFrictionInfluence()), 0.25, 1.0);
        return new Output(
                terrainCapable ? Profile.TERRAIN : Profile.ROAD,
                surfaceGrip,
                resistanceScale,
                nativeWheelFrictionScale,
                clamp(input.rainIntensity(), 0.0, 1.0),
                snow,
                input.offroad(),
                input.forest());
    }

    private static double recoverLostGrip(double base, double advantage) {
        double safeBase = clamp(base, 0.0, 1.0);
        return safeBase + (1.0 - safeBase) * clamp(advantage, 0.0, 1.0);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    public enum Profile {
        ROAD,
        TERRAIN
    }

    public record Input(int mechanicType, double offroadEfficiency, double tirePressure,
            double rainIntensity, double snowIntensity, boolean offroad, boolean forest) {
        public Input {
            mechanicType = Math.max(1, Math.min(3, mechanicType));
            offroadEfficiency = clamp(offroadEfficiency, 0.15, 4.0);
        }

        public static Input defaults() {
            return new Input(1, 1.0, 1.0, 0.0, 0.0, false, false);
        }
    }

    public record Tuning(double offroadTraction, double rainTraction, double snowTraction,
            double heavyOffroadAdvantage, double heavyRainAdvantage, double heavySnowAdvantage,
            double heavyOffroadResistanceScale, double nativeFrictionInfluence) {
        public Tuning {
            offroadTraction = clamp(offroadTraction, 0.0, 1.0);
            rainTraction = clamp(rainTraction, 0.0, 1.0);
            snowTraction = clamp(snowTraction, 0.0, 1.0);
            heavyOffroadAdvantage = clamp(heavyOffroadAdvantage, 0.0, 1.0);
            heavyRainAdvantage = clamp(heavyRainAdvantage, 0.0, 1.0);
            heavySnowAdvantage = clamp(heavySnowAdvantage, 0.0, 1.0);
            heavyOffroadResistanceScale = clamp(heavyOffroadResistanceScale, 0.10, 1.0);
            nativeFrictionInfluence = clamp(nativeFrictionInfluence, 0.0, 1.0);
        }

        public static Tuning defaults() {
            return new Tuning(0.60, 0.70, 0.40, 0.60, 0.45, 0.60, 0.55, 0.45);
        }
    }

    public record Output(Profile profile, double surfaceGrip, double offroadResistanceScale,
            double nativeWheelFrictionScale,
            double rainIntensity, double snowIntensity, boolean offroad, boolean forest) {
    }
}
