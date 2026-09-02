require "PZAPI/ModOptions"
require "CarPhysicsImprovedV1/API"

CarPhysicsImprovedV1 = CarPhysicsImprovedV1 or {}
local ModJava = CarPhysicsImprovedV1Mod
CarPhysicsImprovedV1.javaReady = ModJava ~= nil

local options = PZAPI.ModOptions:create("CarPhysicsImprovedV1B42", "Car Physics Improved V1 (Legacy)")
if not CarPhysicsImprovedV1.javaReady then
    options:addDescription("Java runtime is unavailable. Approve the V1 JAR in ZombieBuddy and fully restart the game.")
end

local enabled = options:addTickBox("Enabled", "Enable V1 legacy physics", true,
    "Disables only V1; the vanilla controller remains available.")
local manual = options:addTickBox("ManualTransmission", "Manual transmission", false,
    "Use Shift Up/Down to select R, N and forward gears.")
local telemetry = options:addTickBox("Telemetry", "Physics telemetry in console.txt", false,
    "Prints a V1 diagnostic line every two seconds while driving.")
local skidSound = options:addTickBox("SkidSound", "Burnout and skid sound", true,
    "Uses the new CPI tire loop; no audio from the reference mod is included.")
local shiftUp = options:addKeyBind("ShiftUp", "Shift up", Keyboard.KEY_UP,
    "Used only with Manual transmission.")
local shiftDown = options:addKeyBind("ShiftDown", "Shift down", Keyboard.KEY_DOWN,
    "Used only with Manual transmission.")

local steeringFactorLow = options:addSlider("SteeringFactorLow", "Steering input: low speed", 0, 3, 0.05, 1.0,
    "Legacy default: 1.0")
local steeringFactorHigh = options:addSlider("SteeringFactorHigh", "Steering input: high speed", 0, 2, 0.05, 0.1,
    "Legacy default: 0.1")
local centeringLow = options:addSlider("CenteringLow", "Steering return: low speed", 0, 3, 0.05, 1.0,
    "Legacy default: 1.0")
local centeringHigh = options:addSlider("CenteringHigh", "Steering return: high speed", 0, 2, 0.05, 0.1,
    "Legacy default: 0.1")
local snapback = options:addSlider("Snapback", "Opposite-direction snapback", 0, 6, 0.1, 3.0,
    "Speeds up steering when changing direction. Legacy default: 3.0")
local steeringHighSpeed = options:addSlider("SteeringHighSpeed", "High-speed reference (km/h)", 10, 120, 1, 75,
    "Speed at which the high-speed steering factors are fully applied.")

local activeSounds = {}

local function callJava(name, ...)
    if not CarPhysicsImprovedV1.javaReady then return false end
    local fn = ModJava[name]
    if not fn then
        CarPhysicsImprovedV1.javaReady = false
        print("[CarPhysicsImprovedV1] Missing Java method: " .. tostring(name))
        return false
    end
    local ok, result = pcall(fn, ...)
    if not ok then
        CarPhysicsImprovedV1.javaReady = false
        print("[CarPhysicsImprovedV1] Java call failed: " .. tostring(name) .. ": " .. tostring(result))
        return false
    end
    return true, result
end

local function sandboxValue(name, fallback)
    local root = SandboxVars and SandboxVars.CarPhysicsImprovedV1B42
    local value = root and tonumber(root[name]) or fallback
    return value or fallback
end

local function sandboxBool(name, fallback)
    local root = SandboxVars and SandboxVars.CarPhysicsImprovedV1B42
    if not root or root[name] == nil then return fallback end
    return root[name] == true
end

local function applyOptions()
    callJava("setEnabled", enabled:getValue())
    callJava("setManualTransmission", manual:getValue())
    callJava("setTelemetry", telemetry:getValue())
    callJava("configurePhysics",
        sandboxValue("TorqueModifierSport", 1.0),
        sandboxValue("TorqueModifierStandard", 1.0),
        sandboxValue("TorqueModifierHeavy", 1.0),
        sandboxValue("TorqueMultiplierLimit", 2.5),
        sandboxValue("ReverseSpeedMax", 40.0),
        sandboxValue("AerodynamicDragSport", 0.70),
        sandboxValue("AerodynamicDragStandard", 1.0),
        sandboxValue("AerodynamicDragHeavy", 1.5),
        sandboxValue("RollingResistance", 0.05),
        sandboxValue("RollingResistanceSpeed", 0.1),
        sandboxValue("OffroadRollingResistance", 0.2),
        sandboxValue("OffroadRollingResistanceSpeed", 1.0),
        sandboxValue("OverallTraction", 1.0),
        sandboxValue("AccelerationTraction", 1.0),
        sandboxValue("TractionOffroad", 0.6),
        sandboxValue("TractionRain", 0.7),
        sandboxValue("TractionSnow", 0.4))
    callJava("configureSlide",
        sandboxBool("SlideMechanics", true),
        sandboxValue("DriftIntensity", 1.0),
        sandboxValue("StabilityAssist", 1.0),
        sandboxValue("PowerDriftDelay", 0.8),
        sandboxBool("ClutchKick", true))
    callJava("configureSteering",
        steeringFactorLow:getValue(), steeringFactorHigh:getValue(),
        centeringLow:getValue(), centeringHigh:getValue(),
        snapback:getValue(), steeringHighSpeed:getValue())
    callJava("configureImpulses",
        sandboxValue("PlantImpulse", 0.3),
        sandboxValue("ZombieImpulse", 0.5),
        sandboxValue("CorpseImpulse", 1.0))
    callJava("configureTrunk",
        sandboxBool("TrunkOverhaul", false),
        sandboxValue("TrunkMultiplier", 1.0),
        sandboxValue("TrunkAdder", 0.0),
        sandboxValue("OtherTrunkMultiplier", 1.0),
        sandboxValue("OtherTrunkAdder", 0.0))
    CarPhysicsImprovedV1.flushVehicleSpecs()
end

local function loadAndApply()
    PZAPI.ModOptions:load()
    applyOptions()
end

local function onKeyPressed(key)
    if not manual:getValue() or not CarPhysicsImprovedV1.javaReady then return end
    local direction = key == shiftUp:getValue() and 1 or key == shiftDown:getValue() and -1 or 0
    if direction == 0 then return end
    local player = getPlayer()
    local vehicle = player and player:getVehicle()
    if vehicle then callJava("requestShiftFor", vehicle:getId(), direction) end
end

local function stopSound(player)
    local active = activeSounds[player]
    if active and active.vehicle and active.sound then active.vehicle:stopSound(active.sound) end
    activeSounds[player] = nil
end

local function updateSound(player)
    if not player or not skidSound:getValue() or not CarPhysicsImprovedV1.javaReady then
        if player then stopSound(player) end
        return
    end
    local vehicle = player:getVehicle()
    if not vehicle then stopSound(player) return end
    local ok, amount = callJava("skidAmountFor", vehicle:getId())
    local value = ok and tonumber(amount) or 0
    local active = activeSounds[player]
    if active and active.vehicle ~= vehicle then stopSound(player) active = nil end
    local threshold = active and 0.35 or 0.65
    if not value or value <= threshold then stopSound(player) return end
    if not active then
        activeSounds[player] = { vehicle = vehicle, sound = vehicle:playSoundImpl("CPI_V1_TireSkid", nil) }
    end
end

local function reportStatus()
    applyOptions()
    local ok, value = callJava("status")
    print("[CarPhysicsImprovedV1] " .. tostring(ok and value or "ZombieBuddy did not expose V1 Java runtime"))
end

options.apply = applyOptions
Events.OnGameBoot.Add(loadAndApply)
Events.OnKeyStartPressed.Add(onKeyPressed)
Events.OnGameStart.Add(reportStatus)
Events.OnPlayerUpdate.Add(updateSound)

CarPhysicsImprovedV1.options = options
