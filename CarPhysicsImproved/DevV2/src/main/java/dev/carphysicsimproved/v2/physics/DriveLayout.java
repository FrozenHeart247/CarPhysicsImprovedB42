package dev.carphysicsimproved.v2.physics;

/** Driven-axle layout used by the two-axle tire model. */
public enum DriveLayout {
    FRONT(1.0, 0.0),
    REAR(0.0, 1.0),
    ALL(0.5, 0.5);

    private final double frontShare;
    private final double rearShare;

    DriveLayout(double frontShare, double rearShare) {
        this.frontShare = frontShare;
        this.rearShare = rearShare;
    }

    public double frontShare() {
        return frontShare;
    }

    public double rearShare() {
        return rearShare;
    }
}
