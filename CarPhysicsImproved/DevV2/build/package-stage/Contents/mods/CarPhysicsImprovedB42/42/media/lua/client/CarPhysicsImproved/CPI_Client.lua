require "PZAPI/ModOptions"

CarPhysicsImproved = CarPhysicsImproved or {}
local ModJava = CarPhysicsImprovedMod
CarPhysicsImproved.javaReady = ModJava ~= nil

local options = PZAPI.ModOptions:create("CarPhysicsImprovedB42", "Car Physics Improved V2")
if not CarPhysicsImproved.javaReady then
    options:addDescription("Java runtime is unavailable. Approve the JAR in ZombieBuddy and fully restart the game.")
end

local enabled = options:addTickBox(
    "Enabled",
    "Enable V2 physics",
    true,
    "Disables only Car Physics Improved; vanilla control remains available.")
local manual = options:addTickBox(
    "ManualTransmission",
    "Manual transmission",
    false,
    "Automatic and manual use identical physics. This option changes only gear selection.")
local telemetry = options:addTickBox(
    "Telemetry",
    "Physics telemetry in console.txt",
    false,
    "Prints one compact diagnostic line every two seconds while driving.")
local burnoutSound = options:addTickBox(
    "BurnoutSound",
    "Tire skid sound",
    true,
    "Plays tire sound during burnout and lateral skids.")
local shiftUp = options:addKeyBind(
    "ShiftUp",
    "Shift up",
    Keyboard.KEY_UP,
    "Used only while Manual transmission is enabled.")
local shiftDown = options:addKeyBind(
    "ShiftDown",
    "Shift down",
    Keyboard.KEY_DOWN,
    "Used only while Manual transmission is enabled.")
local skidSounds = {}

local function callJava(name, ...)
    if not CarPhysicsImproved.javaReady then return false end
    local fn = ModJava[name]
    if not fn then
        CarPhysicsImproved.javaReady = false
        print("[CarPhysicsImproved] Missing Java method: " .. tostring(name))
        return false
    end
    local ok, result = pcall(fn, ...)
    if not ok then
        CarPhysicsImproved.javaReady = false
        print("[CarPhysicsImproved] Java call failed: " .. tostring(name) .. ": " .. tostring(result))
        return false
    end
    return true, result
end

local function sandboxPercent(name, fallback)
    local root = SandboxVars and SandboxVars.CarPhysicsImprovedB42
    local value = root and tonumber(root[name]) or fallback
    if not value then value = fallback end
    return value / 100
end

local function applySandboxOptions()
    local root = SandboxVars and SandboxVars.CarPhysicsImprovedB42
    local vanillaCollisions = not root or root.VanillaCollisionResponse ~= false
    callJava(
        "setPhysicsTuning",
        sandboxPercent("EnginePowerPercent", 100),
        sandboxPercent("RoadResistancePercent", 100),
        sandboxPercent("TireGripPercent", 100),
        sandboxPercent("RecoveryStrengthPercent", 100),
        sandboxPercent("SteeringSensitivityPercent", 100),
        (root and tonumber(root.DriftEntryDelay)) or 1.5,
        vanillaCollisions)
end

local function applyOptions()
    callJava("setEnabled", enabled:getValue())
    callJava("setManualMode", manual:getValue())
    callJava("setTelemetry", telemetry:getValue())
    applySandboxOptions()
end

local function loadAndApply()
    PZAPI.ModOptions:load()
    applyOptions()
end

local function onKeyPressed(key)
    if not manual:getValue() or not CarPhysicsImproved.javaReady then return end
    local direction = key == shiftUp:getValue() and 1
        or key == shiftDown:getValue() and -1
        or 0
    if direction == 0 then return end

    local player = getPlayer()
    local vehicle = player and player:getVehicle()
    if vehicle then
        callJava("requestShiftFor", vehicle:getId(), direction)
        print("[CarPhysicsImproved] Shift " .. (direction > 0 and "up" or "down")
            .. " requested: vehicle=" .. tostring(vehicle:getId()))
    end
end

local function reportStatus()
    applyOptions()
    local ok, status = callJava("status")
    if ok then
        print("[CarPhysicsImproved] " .. tostring(status)
            .. "; burnoutSound=" .. tostring(burnoutSound:getValue()))
    else
        print("[CarPhysicsImproved] ZombieBuddy did not expose CarPhysicsImprovedMod.")
    end
end

local function stopSkidSound(player)
    local active = skidSounds[player]
    if not active then return end
    if active.vehicle and active.sound then
        active.vehicle:stopSound(active.sound)
    end
    skidSounds[player] = nil
end

local function updateSkidSound(player)
    if not player then return end
    if not burnoutSound:getValue() or not CarPhysicsImproved.javaReady then
        stopSkidSound(player)
        return
    end

    local vehicle = player:getVehicle()
    if not vehicle then
        stopSkidSound(player)
        return
    end

    local ok, amount = callJava("skidAmountFor", vehicle:getId())
    local wheelspin = ok and math.abs(tonumber(amount) or 0) or 0
    local active = skidSounds[player]
    if active and active.vehicle ~= vehicle then
        stopSkidSound(player)
        active = nil
    end
    local threshold = active and 0.45 or 0.80
    if wheelspin <= threshold then
        stopSkidSound(player)
        return
    end

    if active then
        return
    end
    -- Keep tire audio on the vehicle's ordinary world emitter. Playing this
    -- loop through the character emitter can compete with or outlive the
    -- vehicle engine event after repeated burnout transitions.
    skidSounds[player] = {
        vehicle = vehicle,
        sound = vehicle:playSoundImpl("CPI_TireSkid", nil)
    }
end

options.apply = applyOptions
Events.OnGameBoot.Add(loadAndApply)
Events.OnKeyStartPressed.Add(onKeyPressed)
Events.OnGameStart.Add(reportStatus)
Events.OnPlayerUpdate.Add(updateSkidSound)

CarPhysicsImproved.options = options
