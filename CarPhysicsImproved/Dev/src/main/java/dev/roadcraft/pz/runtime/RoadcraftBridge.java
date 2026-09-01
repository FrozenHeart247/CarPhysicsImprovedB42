package zombie.roadcraft.runtime;

import zombie.roadcraft.GameCompatibility;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/** Stable, dependency-free bridge used by Lua and the ZombieBuddy runtime hooks. */
public final class RoadcraftBridge {
    public static final int PROTOCOL_VERSION = 1;

    private static final Map<String, Double> NUMBERS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> BOOLEANS = new ConcurrentHashMap<>();
    private static final Map<Integer, AtomicInteger> SHIFT_REQUESTS = new ConcurrentHashMap<>();
    private static final Map<Integer, Double> BURNOUT_AMOUNTS = new ConcurrentHashMap<>();
    private static final AtomicInteger LEGACY_SHIFT_REQUEST = new AtomicInteger();
    private static final AtomicLong CONFIGURATION_REVISION = new AtomicLong();
    private static final AtomicInteger RUNTIME_FAILURES = new AtomicInteger();
    private static final AtomicBoolean RUNTIME_STARTED = new AtomicBoolean();
    private static final AtomicBoolean CONFIGURATION_READY = new AtomicBoolean();
    private static final AtomicBoolean RUNTIME_DISABLED = new AtomicBoolean();

    private static volatile String runtimeVersion = "not-loaded";
    private static volatile String statusDetail = "ZombieBuddy runtime has not started";

    private RoadcraftBridge() {
    }

    /** Activates the clean-room ZombieBuddy patch package. */
    public static void bootstrapZombieBuddyRuntime(String version) {
        if (!RUNTIME_STARTED.compareAndSet(false, true)) {
            return;
        }
        runtimeVersion = version;
        CONFIGURATION_READY.set(true);
        statusDetail = "ZombieBuddy patch package loaded; runtime defaults active";
        System.out.println("[RoadcraftDynamics] ZombieBuddy runtime " + version + " loaded.");
    }

    public static void markFatal(String message) {
        RUNTIME_DISABLED.set(true);
        statusDetail = message;
        System.err.println("[RoadcraftDynamics] " + message
                + ". Roadcraft hooks are disabled; vanilla methods remain active until restart.");
    }

    public static void markRuntimeFailure(String area, Throwable error) {
        int failures = RUNTIME_FAILURES.incrementAndGet();
        if (failures <= 3) {
            System.err.println("[RoadcraftDynamics] Runtime hook failure in " + area + " (" + failures + "/3): "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        if (failures >= 3) {
            RUNTIME_DISABLED.set(true);
            statusDetail = "Runtime disabled after repeated hook failures; vanilla methods remain active";
        }
    }

    public static String status() {
        if (!RUNTIME_STARTED.get()) {
            return "NOT_INSTALLED";
        }
        if (RUNTIME_DISABLED.get()) {
            return "INCOMPATIBLE";
        }
        if (!CONFIGURATION_READY.get()) {
            return "WAITING_CONFIG";
        }
        return "ACTIVE";
    }

    public static String statusDetail() {
        return statusDetail;
    }

    public static String runtimeVersion() {
        return runtimeVersion;
    }

    public static String targetGameVersion() {
        return GameCompatibility.GAME_FAMILY;
    }

    public static String knownTestedGameVersion() {
        return GameCompatibility.KNOWN_TESTED_VERSION;
    }

    public static int protocolVersion() {
        return PROTOCOL_VERSION;
    }

    public static boolean runtimeEnabled() {
        return !RUNTIME_DISABLED.get() && RUNTIME_STARTED.get()
                && CONFIGURATION_READY.get();
    }

    public static void activateConfiguration() {
        CONFIGURATION_READY.set(true);
        statusDetail = "Lua configuration accepted; ZombieBuddy patch package active";
    }

    public static void setBoolean(String key, boolean value) {
        if (key != null) {
            Boolean previous = BOOLEANS.put(key, value);
            if (previous == null || previous != value) {
                CONFIGURATION_REVISION.incrementAndGet();
            }
        }
    }

    public static void setNumber(String key, double value) {
        if (key != null && Double.isFinite(value)) {
            Double previous = NUMBERS.put(key, value);
            if (previous == null || Double.compare(previous, value) != 0) {
                CONFIGURATION_REVISION.incrementAndGet();
            }
        }
    }

    public static boolean bool(String key, boolean fallback) {
        Boolean value = BOOLEANS.get(key);
        return value == null ? fallback : value;
    }

    public static double number(String key, double fallback) {
        Double stored = NUMBERS.get(key);
        if (stored == null) {
            return fallback;
        }
        double value = stored;
        return Double.isFinite(value) ? value : fallback;
    }

    public static void requestShift(int direction) {
        LEGACY_SHIFT_REQUEST.updateAndGet(value -> boundedShift(value, direction));
    }

    public static void requestShiftFor(int vehicleId, int direction) {
        SHIFT_REQUESTS.computeIfAbsent(vehicleId, ignored -> new AtomicInteger())
                .updateAndGet(value -> boundedShift(value, direction));
    }

    /** Local visual/audio wheelspin telemetry; it does not affect authority. */
    public static double burnoutAmountFor(int vehicleId) {
        Double amount = BURNOUT_AMOUNTS.get(vehicleId);
        return amount == null || !Double.isFinite(amount) ? 0.0 : amount;
    }

    static void updateBurnoutAmount(int vehicleId, double amountKph) {
        double amount = Double.isFinite(amountKph) ? Math.abs(amountKph) : 0.0;
        if (amount < 0.05) {
            BURNOUT_AMOUNTS.remove(vehicleId);
        } else {
            BURNOUT_AMOUNTS.put(vehicleId, Math.min(amount, 80.0));
        }
    }

    static int consumeShiftRequest(int vehicleId) {
        AtomicInteger request = SHIFT_REQUESTS.get(vehicleId);
        int value = request == null ? 0 : request.getAndSet(0);
        if (request != null && request.get() == 0) {
            SHIFT_REQUESTS.remove(vehicleId, request);
        }
        if (value == 0) {
            value = LEGACY_SHIFT_REQUEST.getAndSet(0);
        }
        return value;
    }

    static long configurationRevision() {
        return CONFIGURATION_REVISION.get();
    }

    private static int boundedShift(int current, int direction) {
        return Math.max(-4, Math.min(4, current + Integer.signum(direction)));
    }

}
