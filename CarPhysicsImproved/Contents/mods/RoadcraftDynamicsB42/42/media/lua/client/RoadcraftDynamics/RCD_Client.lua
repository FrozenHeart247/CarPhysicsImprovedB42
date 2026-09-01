require "PZAPI/ModOptions"
require "RoadcraftDynamics/RCD_Bridge"

local options = PZAPI.ModOptions:create("RoadcraftDynamics", getText("IGUI_RCD_ModOptionsName"))
local manual = options:addTickBox("ManualTransmission", getText("IGUI_RCD_ManualTransmission"), false, getText("IGUI_RCD_ManualTransmission_Tooltip"))
local automaticReverse = options:addTickBox("AutomaticReverse", getText("IGUI_RCD_AutomaticReverse"), true, getText("IGUI_RCD_AutomaticReverse_Tooltip"))
local analogThrottle = options:addTickBox("AnalogThrottle", getText("IGUI_RCD_AnalogThrottle"), true, getText("IGUI_RCD_AnalogThrottle_Tooltip"))
local burnoutSound = options:addTickBox("BurnoutSound", getText("IGUI_RCD_BurnoutSound"), true, getText("IGUI_RCD_BurnoutSound_Tooltip"))
local shiftUp = options:addKeyBind("ShiftUp", getText("IGUI_RCD_ShiftUp"), Keyboard.KEY_UP, getText("IGUI_RCD_ShiftUp_Tooltip"))
local shiftDown = options:addKeyBind("ShiftDown", getText("IGUI_RCD_ShiftDown"), Keyboard.KEY_DOWN, getText("IGUI_RCD_ShiftDown_Tooltip"))
local skidSounds = {}

local function applyClientOptions()
    local bridge = RoadcraftDynamics.getBridge()
    if not bridge then return end
    bridge:setBoolean("manualMode", manual:getValue())
    bridge:setBoolean("automaticReverse", automaticReverse:getValue())
    bridge:setBoolean("useAnalogThrottle", analogThrottle:getValue())
end

RoadcraftDynamics.onRuntimeBridgeReady(applyClientOptions)

local function loadAndApplyClientOptions()
    -- Mod Options does not guarantee that persisted values are loaded before
    -- the Java controller starts. Load them explicitly on every game boot.
    PZAPI.ModOptions:load()
    applyClientOptions()
end

local function onKeyPressed(key)
    if not manual:getValue() then return end
    local bridge = RoadcraftDynamics.getBridge()
    if not bridge then return end

    if key == shiftUp:getValue() then
        local player = getPlayer()
        local vehicle = player and player:getVehicle()
        if vehicle then
            bridge:requestShiftFor(vehicle:getId(), 1)
            print("[RoadcraftDynamics] Shift-up requested: vehicle=" .. tostring(vehicle:getId()))
        end
    elseif key == shiftDown:getValue() then
        local player = getPlayer()
        local vehicle = player and player:getVehicle()
        if vehicle then
            bridge:requestShiftFor(vehicle:getId(), -1)
            print("[RoadcraftDynamics] Shift-down requested: vehicle=" .. tostring(vehicle:getId()))
        end
    end
end

local function reportStatus()
    RoadcraftDynamics.applySandboxOptions()
    applyClientOptions()
    local bridge = RoadcraftDynamics.getBridge()
    if not bridge then
        print("[RoadcraftDynamics] ZombieBuddy did not expose the Roadcraft Java API. Confirm approval, fully restart the game, and inspect console.txt.")
        return
    end
    print("[RoadcraftDynamics] " .. tostring(bridge:status()) .. ": " .. tostring(bridge:statusDetail()))
    print("[RoadcraftDynamics] Client options: manual=" .. tostring(manual:getValue())
        .. " automaticReverse=" .. tostring(automaticReverse:getValue())
        .. " analogThrottle=" .. tostring(analogThrottle:getValue())
        .. " burnoutSound=" .. tostring(burnoutSound:getValue())
        .. " shiftUp=" .. tostring(shiftUp:getValue())
        .. " shiftDown=" .. tostring(shiftDown:getValue()))
end

local function stopSkidSound(player)
    local active = skidSounds[player]
    if not active then return end
    if active.emitter and active.sound and active.emitter:isPlaying(active.sound) then
        active.emitter:stopSound(active.sound)
    end
    skidSounds[player] = nil
end

local function updateSkidSound(player)
    if not player then return end
    if not burnoutSound:getValue() then
        stopSkidSound(player)
        return
    end
    local bridge = RoadcraftDynamics.getBridge()
    local vehicle = player:getVehicle()
    if not bridge or not vehicle then
        stopSkidSound(player)
        return
    end

    local wheelspin = math.abs(tonumber(bridge:burnoutAmountFor(vehicle:getId())) or 0)
    local active = skidSounds[player]
    local stopThreshold = active and 0.5 or 1.5
    if wheelspin <= stopThreshold then
        stopSkidSound(player)
        return
    end

    if active and active.emitter and active.sound and active.emitter:isPlaying(active.sound) then
        return
    end
    local emitter = player:getEmitter()
    -- B42 file-backed clips need playSoundLocal; invoking playSound directly on
    -- the emitter can return a handle while the custom WAV remains silent.
    skidSounds[player] = {
        emitter = emitter,
        sound = player:playSoundLocal("RCD_TireSkid")
    }
end

options.apply = applyClientOptions
Events.OnGameBoot.Add(loadAndApplyClientOptions)
Events.OnKeyStartPressed.Add(onKeyPressed)
Events.OnGameStart.Add(reportStatus)
Events.OnPlayerUpdate.Add(updateSkidSound)
