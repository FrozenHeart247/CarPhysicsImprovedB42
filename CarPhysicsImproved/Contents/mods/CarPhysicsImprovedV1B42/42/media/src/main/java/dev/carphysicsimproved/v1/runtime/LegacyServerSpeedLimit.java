package dev.carphysicsimproved.v1.runtime;

/** Final MP drive-force gate. Never clamps velocity or adds a braking force. */
final class LegacyServerSpeedLimit {
    private LegacyServerSpeedLimit() { }

    static double limitForce(double force, double signedSpeedKph, double limitKph) {
        if (!Double.isFinite(limitKph) || limitKph <= 0.0 || !Double.isFinite(signedSpeedKph)) {
            return force;
        }
        // Retain opposing force: it slows the vehicle instead of accelerating
        // beyond the limit. Apply the same cap to forward and reverse driving.
        return Math.abs(signedSpeedKph) >= limitKph && force * signedSpeedKph > 0.0 ? 0.0 : force;
    }
}
