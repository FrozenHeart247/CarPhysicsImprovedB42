RoadcraftDynamics = RoadcraftDynamics or {}

local bridge = nil
local bridgeReadyCallbacks = {}
local bridgeReadyNotified = false
local retryRegistered = false
local retryTicks = 0

local function notifyBridgeReady()
    if bridgeReadyNotified then return end
    bridgeReadyNotified = true
    for index = 1, #bridgeReadyCallbacks do
        local ok, errorMessage = pcall(bridgeReadyCallbacks[index])
        if not ok then
            print("[RoadcraftDynamics] Runtime-ready callback failed: " .. tostring(errorMessage))
        end
    end
end

local function bindBridge()
    if bridge then return bridge end
    if not RoadcraftMod then return nil end

    local statusMethod = RoadcraftMod.status
    if not statusMethod then return nil end
    local ok, errorMessage = pcall(statusMethod)
    if not ok then
        print("[RoadcraftDynamics] RoadcraftMod.status failed: " .. tostring(errorMessage))
        return nil
    end

    bridge = {}
    function bridge:setBoolean(key, value) RoadcraftMod.setBoolean(key, value) end
    function bridge:setNumber(key, value) RoadcraftMod.setNumber(key, value) end
    function bridge:requestShiftFor(vehicleId, direction) RoadcraftMod.requestShiftFor(vehicleId, direction) end
    function bridge:burnoutAmountFor(vehicleId) return RoadcraftMod.burnoutAmountFor(vehicleId) end
    function bridge:activateConfiguration() RoadcraftMod.activateConfiguration() end
    function bridge:status() return RoadcraftMod.status() end
    function bridge:statusDetail() return RoadcraftMod.statusDetail() end
    function bridge:runtimeVersion() return RoadcraftMod.runtimeVersion() end
    function bridge:targetGameVersion() return RoadcraftMod.targetGameVersion() end
    function bridge:knownTestedGameVersion() return RoadcraftMod.knownTestedGameVersion() end
    RoadcraftDynamics.bridge = bridge
    notifyBridgeReady()
    return bridge
end

function RoadcraftDynamics.onRuntimeBridgeReady(callback)
    if type(callback) ~= "function" then return end
    bridgeReadyCallbacks[#bridgeReadyCallbacks + 1] = callback
    if bridge then
        local ok, errorMessage = pcall(callback)
        if not ok then
            print("[RoadcraftDynamics] Runtime-ready callback failed: " .. tostring(errorMessage))
        end
    else
        bindBridge()
    end
end

local function setBoolean(target, key, value)
    if value ~= nil then target:setBoolean(key, value == true) end
end

local function setNumber(target, key, value)
    value = tonumber(value)
    if value then target:setNumber(key, value) end
end

function RoadcraftDynamics.applySandboxOptions(quiet)
    local target = bindBridge()
    if not target then
        if not quiet then
            print("[RoadcraftDynamics] ZombieBuddy Java API is unavailable. Confirm mod approval, restart the game, and inspect console.txt; Roadcraft configuration was not applied.")
        end
        return false
    end

    local sv = SandboxVars and SandboxVars.RoadcraftDynamics
    if not sv then return false end

    setBoolean(target, "manualAllowed", sv.AllowManualTransmission)
    setBoolean(target, "autoStart", sv.AutoStart)
    setBoolean(target, "easyTow", sv.EasyTow)
    setBoolean(target, "tractionEnabled", sv.TractionEnabled)
    setNumber(target, "redlineRpm", sv.RedlineRPM)
    setNumber(target, "reverseSpeedLimit", sv.ReverseSpeedLimit)
    setNumber(target, "torqueSport", sv.TorqueSport)
    setNumber(target, "torqueStandard", sv.TorqueStandard)
    setNumber(target, "torqueHeavy", sv.TorqueHeavy)
    setNumber(target, "torqueConverterLimit", sv.TorqueConverterLimit)
    setNumber(target, "aeroDragSport", sv.AeroDragSport)
    setNumber(target, "aeroDragStandard", sv.AeroDragStandard)
    setNumber(target, "aeroDragHeavy", sv.AeroDragHeavy)
    setNumber(target, "rollingResistance", sv.RollingResistance)
    setNumber(target, "rollingResistanceSpeed", sv.RollingResistanceSpeed)
    setNumber(target, "offroadRollingResistance", sv.OffroadRollingResistance)
    setNumber(target, "offroadRollingResistanceSpeed", sv.OffroadRollingResistanceSpeed)
    setNumber(target, "overallTraction", sv.OverallTraction)
    setNumber(target, "accelerationTraction", sv.AccelerationTraction)
    setNumber(target, "offroadTraction", sv.OffroadTraction)
    setNumber(target, "wetTraction", sv.WetTraction)
    setNumber(target, "snowTraction", sv.SnowTraction)
    setNumber(target, "steeringRateLow", sv.SteeringRateLowSpeed)
    setNumber(target, "steeringRateHigh", sv.SteeringRateHighSpeed)
    setNumber(target, "steeringCenterLow", sv.SteeringCenterLowSpeed)
    setNumber(target, "steeringCenterHigh", sv.SteeringCenterHighSpeed)
    setNumber(target, "steeringSnapback", sv.SteeringSnapback)
    setNumber(target, "steeringHighSpeed", sv.SteeringHighSpeedReference)
    setNumber(target, "plantImpulse", sv.PlantImpulse)
    setNumber(target, "zombieImpulse", sv.ZombieImpulse)
    setNumber(target, "corpseImpulse", sv.CorpseImpulse)
    setNumber(target, "dynamicMassReference", sv.DynamicMassReference)

    target:activateConfiguration()
    print("[RoadcraftDynamics] Sandbox configuration applied; runtime status=" .. tostring(target:status()))
    return true
end

function RoadcraftDynamics.getBridge()
    return bindBridge()
end

local function stopRuntimeRetry()
    if not retryRegistered then return end
    Events.OnTick.Remove(RoadcraftDynamics.retryRuntimeBridge)
    retryRegistered = false
end

function RoadcraftDynamics.retryRuntimeBridge()
    retryTicks = retryTicks + 1
    if retryTicks < 30 then return end
    retryTicks = 0
    if RoadcraftDynamics.applySandboxOptions(true) then
        stopRuntimeRetry()
    end
end

local function initializeRuntimeBridge()
    if RoadcraftDynamics.applySandboxOptions(false) then return end
    if not retryRegistered then
        retryRegistered = true
        Events.OnTick.Add(RoadcraftDynamics.retryRuntimeBridge)
    end
end

RoadcraftDynamics.onRuntimeBridgeReady(function()
    RoadcraftDynamics.applySandboxOptions(true)
end)

Events.OnInitGlobalModData.Add(initializeRuntimeBridge)
