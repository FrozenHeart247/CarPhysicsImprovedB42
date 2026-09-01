package zombie.roadcraft.runtime;

import zombie.roadcraft.GameCompatibility;

import java.lang.invoke.MethodHandle;

/** Dependency-free contract test for the Lua/ZombieBuddy bridge state machine. */
public final class RoadcraftBridgeContractTest {
    private static int assertions;

    private RoadcraftBridgeContractTest() {
    }

    public static void main(String[] arguments) throws Exception {
        check(RoadcraftBridge.status().equals("NOT_INSTALLED"), "initial bridge state");
        check(RoadcraftBridge.protocolVersion() == 1, "protocol version");
        check(RoadcraftBridge.targetGameVersion().equals("42"), "common Build 42 target");
        check(RoadcraftBridge.knownTestedGameVersion().equals(GameCompatibility.KNOWN_TESTED_VERSION),
                "known-tested game version");
        check(RoadcraftBridge.bool("missingBoolean", true), "missing boolean uses true fallback");
        check(!RoadcraftBridge.bool("missingBoolean", false), "missing boolean uses false fallback");
        check(RoadcraftBridge.number("missingNumber", 12.5) == 12.5, "missing number uses fallback");
        check(PzAccess.class.getDeclaredField("fallbackControlVehicle").getType() == MethodHandle.class,
                "native control fallback is a cached exact MethodHandle");

        RoadcraftBridge.bootstrapZombieBuddyRuntime("fixture");
        check(RoadcraftBridge.status().equals("ACTIVE"), "ZombieBuddy package activates runtime defaults");
        check(RoadcraftBridge.runtimeVersion().equals("fixture"), "runtime version round trip");
        RoadcraftBridge.activateConfiguration();
        check(RoadcraftBridge.status().equals("ACTIVE"), "Lua configuration retains active state");

        RoadcraftBridge.setBoolean("fixtureBoolean", false);
        RoadcraftBridge.setNumber("fixtureNumber", 42.25);
        check(!RoadcraftBridge.bool("fixtureBoolean", true), "boolean round trip");
        check(RoadcraftBridge.number("fixtureNumber", 0.0) == 42.25, "number round trip");
        RoadcraftBridge.setNumber("fixtureNumber", Double.NaN);
        check(RoadcraftBridge.number("fixtureNumber", 0.0) == 42.25, "non-finite value is rejected");
        RoadcraftBridge.updateBurnoutAmount(404, 27.5);
        check(RoadcraftBridge.burnoutAmountFor(404) == 27.5, "burnout telemetry round trip");
        RoadcraftBridge.updateBurnoutAmount(404, 0.0);
        check(RoadcraftBridge.burnoutAmountFor(404) == 0.0, "burnout telemetry clears at rest");

        for (int index = 0; index < 20; index++) {
            RoadcraftBridge.requestShift(1);
        }
        check(RoadcraftBridge.consumeShiftRequest(1001) == 4, "legacy shift requests are bounded");
        check(RoadcraftBridge.consumeShiftRequest(1001) == 0, "legacy shift requests are consumed once");
        for (int index = 0; index < 20; index++) {
            RoadcraftBridge.requestShiftFor(2002, -1);
        }
        check(RoadcraftBridge.consumeShiftRequest(2002) == -4, "per-vehicle shift requests are bounded");
        check(RoadcraftBridge.consumeShiftRequest(3003) == 0, "shift requests do not leak between vehicles");

        RuntimeException failure = new RuntimeException("fixture");
        RoadcraftBridge.markRuntimeFailure("fixture", failure);
        RoadcraftBridge.markRuntimeFailure("fixture", failure);
        check(RoadcraftBridge.status().equals("ACTIVE"), "two runtime failures retain active state");
        RoadcraftBridge.markRuntimeFailure("fixture", failure);
        check(RoadcraftBridge.status().equals("INCOMPATIBLE"), "three runtime failures disable hooks");
        check(!RoadcraftBridge.runtimeEnabled(), "disabled bridge rejects runtime hooks");

        System.out.println("RoadcraftBridgeContractTest: " + assertions + " assertions passed");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
