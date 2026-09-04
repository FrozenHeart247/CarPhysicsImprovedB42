package dev.carphysicsimproved.v1.runtime;

/** Immutable bindings; gear chords take priority over a held drift modifier. */
public record LegacyDrivingKeys(LegacyDriftKey drift, LegacyDriftKey shiftUp, LegacyDriftKey shiftDown) {
    @FunctionalInterface
    public interface KeyState {
        boolean isDown(int key) throws ReflectiveOperationException;
    }

    public boolean driftHeld(boolean manual, KeyState keys) throws ReflectiveOperationException {
        if (drift.key() == 0 || !keys.isDown(drift.key())) return false;
        boolean shift = keys.isDown(42) || keys.isDown(54);
        boolean ctrl = keys.isDown(29) || keys.isDown(157);
        boolean alt = keys.isDown(56) || keys.isDown(184);
        if (!drift.matches(true, shift, ctrl, alt)) return false;
        if (!manual) return true;
        return !held(shiftUp, keys, shift, ctrl, alt) && !held(shiftDown, keys, shift, ctrl, alt);
    }

    private static boolean held(LegacyDriftKey key, KeyState keys,
            boolean shift, boolean ctrl, boolean alt) throws ReflectiveOperationException {
        return key.key() != 0 && keys.isDown(key.key()) && key.matches(true, shift, ctrl, alt);
    }

    public static LegacyDrivingKeys defaults() {
        return new LegacyDrivingKeys(new LegacyDriftKey(42, false, false, false),
                new LegacyDriftKey(200, false, false, false), new LegacyDriftKey(208, false, false, false));
    }
}
