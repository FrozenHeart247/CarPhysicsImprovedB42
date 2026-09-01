package pzmod.carphysicsimproved;

import dev.carphysicsimproved.v2.BuildInfo;
import dev.carphysicsimproved.v2.physics.DriveLayout;
import me.zed_0xff.zombie_buddy.Exposer;

import java.util.concurrent.ConcurrentHashMap;

/** Minimal Lua-facing configuration facade exposed by ZombieBuddy. */
@Exposer.LuaClass
public final class CarPhysicsImprovedMod {
    private static final ConcurrentHashMap<Integer, Integer> SHIFT_REQUESTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Double> BURNOUT_AMOUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Double> SKID_AMOUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, DriveLayout> DRIVE_LAYOUTS = new ConcurrentHashMap<>();
    private static volatile boolean enabled = true;
    private static volatile boolean manualMode;
    private static volatile boolean telemetry;
    private static volatile String status = "loaded; waiting for a locally controlled vehicle";

    public CarPhysicsImprovedMod() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void setManualMode(boolean value) {
        manualMode = value;
    }

    public static void setTelemetry(boolean value) {
        telemetry = value;
    }

    public static void requestShiftFor(int vehicleId, int direction) {
        int sanitized = Math.max(-1, Math.min(1, direction));
        if (vehicleId >= 0 && sanitized != 0) {
            SHIFT_REQUESTS.merge(vehicleId, sanitized, Integer::sum);
        }
    }

    /** Optional compatibility hook for VehicleScripts that do not expose their driven axle. */
    public static void setDriveLayout(String fullType, String layout) {
        if (fullType == null || fullType.isBlank() || layout == null) {
            return;
        }
        String normalized = layout.trim().toUpperCase();
        DriveLayout value = switch (normalized) {
            case "FWD", "FRONT" -> DriveLayout.FRONT;
            case "AWD", "4WD", "ALL" -> DriveLayout.ALL;
            case "RWD", "REAR" -> DriveLayout.REAR;
            default -> null;
        };
        if (value != null) {
            DRIVE_LAYOUTS.put(fullType, value);
        }
    }

    public static DriveLayout driveLayoutFor(String fullType) {
        return fullType == null ? DriveLayout.REAR : DRIVE_LAYOUTS.getOrDefault(fullType, DriveLayout.REAR);
    }

    public static boolean enabled() {
        return enabled;
    }

    public static boolean manualMode() {
        return manualMode;
    }

    public static boolean telemetry() {
        return telemetry;
    }

    public static int consumeShiftRequest(int vehicleId) {
        Integer request = SHIFT_REQUESTS.remove(vehicleId);
        return request == null ? 0 : Math.max(-1, Math.min(1, request));
    }

    public static void updateBurnoutAmount(int vehicleId, double wheelSlipMps) {
        BURNOUT_AMOUNTS.put(vehicleId,
                Double.isFinite(wheelSlipMps) ? Math.max(0.0, wheelSlipMps) : 0.0);
    }

    public static double burnoutAmountFor(int vehicleId) {
        return BURNOUT_AMOUNTS.getOrDefault(vehicleId, 0.0);
    }

    public static void updateSkidAmount(int vehicleId, double skidSpeedMps) {
        SKID_AMOUNTS.put(vehicleId,
                Double.isFinite(skidSpeedMps) ? Math.max(0.0, skidSpeedMps) : 0.0);
    }

    public static double skidAmountFor(int vehicleId) {
        return SKID_AMOUNTS.getOrDefault(vehicleId, 0.0);
    }

    public static void updateStatus(String value) {
        status = value == null ? "unknown" : value;
    }

    public static String status() {
        return status;
    }

    public static String runtimeVersion() {
        return BuildInfo.VERSION;
    }

    public static String testedGameVersion() {
        return BuildInfo.TESTED_GAME;
    }
}
