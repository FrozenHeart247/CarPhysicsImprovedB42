# Car Physics Improved: Modding API Guide

Applies to CPI **0.4.23-dev**, targeting Build 42 and tested against 42.20.4. This documents the current implementation, not a guarantee that every Java bridge method is a stable public API.

## Workshop description: short version

CPI includes a small Lua compatibility API for vehicle authors. Patches can register horsepower and calculation-mass overrides for individual vehicle script types without modifying CPI or rebuilding its JAR. Most vehicles do not need a patch: CPI reads their existing parameters automatically. These are fixed overrides, not per-vehicle upgrade or damage multipliers, so integration with dynamic engine or transmission mods requires additional care.

## What the API does

The main entry point is:

```lua
CarPhysicsImprovedV1.registerVehicleSpec(fullType, horsePower, massKg, cargoKg)
```

| Argument | Meaning in the current version |
| --- | --- |
| `fullType` | Exact, case-sensitive vehicle script name, including its module, such as `MyVehicles.Roadster`. This is not the display name, Mod ID, or Workshop ID. |
| `horsePower` | Horsepower input for CPI's power calibration. Internally converted to `horsePower * 4`, not passed directly to vanilla `engineForce`. |
| `massKg` | Fixed mass used by CPI calculations that consume its vehicle specification. It does **not** replace the native physics body's mass. |
| `cargoKg` | Retained argument for an older capacity feature. Cargo-capacity overrides are disabled in the current configuration; pass `0`. It is not the weight of the vehicle's current cargo. |

Both horsepower and mass are required; this helper cannot override only one of them. Use finite, positive numbers. The Java registration clamps horsepower to 1–5,000 and mass to 100–20,000 kg. CPI's internal power also caps at 5,000, so horsepower above 1,250 does not increase that input further.

Each registration affects **all vehicles of the matching script type** in that runtime. It is not a setting for one spawned vehicle. Re-registering the same name replaces the previous values; there is no priority or ownership system between patches.

## Minimal compatibility patch

Use a separate patch mod that depends on CPI and the vehicle pack it targets. CPI's current dependency ID is:

```ini
require=\CarPhysicsImprovedV1B42
```

Merge this dependency into your patch's existing `require` list; do not discard its vehicle-pack dependencies. ZombieBuddy must also be correctly installed and enabled for CPI.

Place a Lua file in your patch's `42/media/lua/shared/` directory, for example `MyPack_CPI.lua`:

```lua
require "CarPhysicsImprovedV1/API"

-- Fictional names and example values: replace with your actual vehicle scripts.
CarPhysicsImprovedV1.registerVehicleSpec("MyVehicles.Roadster", 180, 1380, 0)
CarPhysicsImprovedV1.registerVehicleSpec("MyVehicles.DeliveryVan", 145, 2200, 0)
```

No additional Java code or per-frame registration loop is needed. The Lua helper stores entries immediately and attempts to forward them to Java. CPI retries stored entries at game boot and when applying its options, including at game start.

The helper's return value only indicates whether the immediate Java call completed without a Lua error. `false` can mean the bridge is not ready even though the entry was queued. `true` does not verify that the named vehicle script exists or prove an in-game effect.

To find the exact script name, run this in an in-game Lua debug console while seated in the target vehicle:

```lua
local player = getPlayer()
local vehicle = player and player:getVehicle()
if vehicle then
    print(vehicle:getScript():getFullName())
end
```

## Important limitations

- **Automatic support is usually preferable.** Without an override, CPI reads the vehicle's current `getEnginePower()` and `getMass()` during controller updates.
- **Fixed power replaces the live power input.** A registered horsepower value can bypass power reductions applied through `setEngineFeature(...)` by repair, engine, or transmission mods, including a reduction to zero. It does not force a stopped engine to run, but it is not suitable as a general damage or upgrades API.
- **Fixed calculation mass is not a native mass edit.** CPI calculations using the override stop following changes in `getMass()`. Other native physics behavior can still use the actual vehicle mass. Keep values consistent with the vehicle's definition.
- **No zero-power shutdown through registration.** Passing zero horsepower is clamped to one. Do not use this helper to simulate a broken gearbox or a stalled engine.
- **This is not a complete vehicle specification editor.** The helper does not expose custom gear ratios, redline, top speed, tire grip, vehicle category, off-road profile, or per-vehicle transmission mode. CPI continues obtaining other inputs from its normal script/runtime paths.
- **No automatic network synchronization.** The registration table is process-local. Distribute the same patch and values to the server and clients; do not rely on server-only registration to configure a driving client. Multiplayer behavior still needs in-game testing.

## Removing an override

For a released patch, remove its registration and fully restart the game. Merely deleting the source line while the game is running does not clear an already registered entry.

For a live development session, clear both stores:

```lua
require "CarPhysicsImprovedV1/API"

local fullType = "MyVehicles.Roadster"
CarPhysicsImprovedV1.vehicleSpecs[fullType] = nil

local bridge = CarPhysicsImprovedV1Mod
if bridge and bridge.unregisterVehicleSpec then
    bridge.unregisterVehicleSpec(fullType)
end
```

There is currently no Lua `CarPhysicsImprovedV1.unregisterVehicleSpec` helper. Clearing only Java allows a later Lua flush to re-register the entry. Removal also does not restore another patch's earlier override.

## Diagnostics and effects

The exposed Java bridge offers simple read-only queries:

```lua
local bridge = CarPhysicsImprovedV1Mod
if bridge then
    print("CPI runtime: " .. tostring(bridge.runtimeVersion()))
    print("CPI status: " .. tostring(bridge.status()))

    local player = getPlayer()
    local vehicle = player and player:getVehicle()
    if vehicle then
        local id = vehicle:getId()
        print("Burnout: " .. tostring(bridge.burnoutAmountFor(id)))
        print("Skid: " .. tostring(bridge.skidAmountFor(id)))
    end
end
```

Effect queries return CPI's last reported non-negative effect amounts, or zero if no entry exists. They are not native wheel-friction measurements or a synchronized remote-vehicle feed.

Other exposed methods, such as `configurePhysics`, `configureSteering`, and `setManualTransmission`, control runtime-wide settings rather than individual vehicle types. CPI reapplies these from Mod Options and Sandbox settings. Vehicle patches should not use them to silently change the player's global configuration.

## Testing your patch

Restart the game, verify the script name and CPI runtime status, then compare the same vehicle with and without your patch. Keep Sandbox settings, driver traits, tire condition/pressure, cargo, and road/weather conditions consistent. Test both transmission modes and any repair or upgrade mods you intend to support. CPI's optional physics telemetry writes diagnostics to `console.txt`.

The examples have been syntax-checked and exercised with the shipped Lua helper and a mocked bridge. This is not an in-game or multiplayer compatibility certification.

## Source reference

Paths below are relative to CPI's `42/media/` directory:

- Lua helper and queued registrations: `lua/shared/CarPhysicsImprovedV1/API.lua`
- Java bridge and registration limits: `src/main/java/pzmod/carphysicsimproved/v1/CarPhysicsImprovedV1Mod.java`
- Runtime parameter selection: `src/main/java/dev/carphysicsimproved/v1/runtime/PzLegacyAccess.java`, method `snapshot`
- Settings application and retry: `lua/client/CarPhysicsImprovedV1/CPI_V1_Client.lua`, function `applyOptions`
