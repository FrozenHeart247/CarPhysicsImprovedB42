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
    "Use the exact key and modifier combinations configured below to select R, N and forward gears.")
local telemetry = options:addTickBox("Telemetry", "Physics telemetry in console.txt", false,
    "Prints a V1 diagnostic line every two seconds while driving.")
local skidSound = options:addTickBox("SkidSound", "Burnout and skid sound", true,
    "Plays only after wheelspin or physical tire slip is confirmed; ordinary cornering stays silent.")
local skidMarks = options:addTickBox("SkidMarks", "Temporary tire marks", true,
    "Draws client-local rear-wheel marks during confirmed burnout, braking skid or slide. Does not modify the map or save.")
local skidMarkLifetime = options:addSlider("SkidMarkLifetime", "Tire mark lifetime (seconds)", 5, 60, 1, 25,
    "Visual lifetime only. Older marks fade out and are discarded.")
local skidMarkOpacity = options:addSlider("SkidMarkOpacity", "Tire mark opacity", 0.2, 1.0, 0.05, 0.70,
    "Local visual opacity; it does not change tire physics.")
local SHIFT_UP_BINDING = "Shift up"
local SHIFT_DOWN_BINDING = "Shift down"
local shiftUp = options:addKeyBind("ShiftUp", SHIFT_UP_BINDING, Keyboard.KEY_UP,
    "Used only with Manual transmission.")
local shiftDown = options:addKeyBind("ShiftDown", SHIFT_DOWN_BINDING, Keyboard.KEY_DOWN,
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
local keyModifiers = {}
local trackVehicles = {}
local trackPlayerVehicles = {}
local rearWheelCache = {}
local trackRenderingFailed = false
local TRACK_TEXTURE_COUNT = 16
local TRACK_SAMPLE_MS = 70
local TRACK_SPACING_TILES = 0.38
local TRACK_MAX_FILL_TILES = 2.5
local TRACK_MAX_STAMPS_PER_WHEEL = 8

local function emptyModifiers()
    return { shift = false, ctrl = false, alt = false }
end

local function refreshKeyModifiers()
    keyModifiers[SHIFT_UP_BINDING] = emptyModifiers()
    keyModifiers[SHIFT_DOWN_BINDING] = emptyModifiers()
    local found = {}
    local ok, reader = pcall(getFileReader, "keysB42.ini", true)
    if ok and reader then
        while true do
            local line = reader:readLine()
            if not line then break end
            local name, definition = line:match("^([^=]+)=(.+)$")
            if keyModifiers[name] then
                keyModifiers[name] = {
                    shift = definition:find("shift:true", 1, true) ~= nil,
                    ctrl = definition:find("ctrl:true", 1, true) ~= nil,
                    alt = definition:find("alt:true", 1, true) ~= nil,
                }
                found[name] = true
            end
        end
        reader:close()
    end

    -- While the options screen is open, its element is the freshest source.
    local function useElementWhenFileHasNoEntry(name, option)
        local element = option and option.element
        if not found[name] and element then
            keyModifiers[name] = {
                shift = element.shift == true,
                ctrl = element.ctrl == true,
                alt = element.alt == true,
            }
        end
    end
    useElementWhenFileHasNoEntry(SHIFT_UP_BINDING, shiftUp)
    useElementWhenFileHasNoEntry(SHIFT_DOWN_BINDING, shiftDown)
end

local function modifiersMatch(expected)
    expected = expected or emptyModifiers()
    local shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) or Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
    local ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) or Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)
    local alt = Keyboard.isKeyDown(Keyboard.KEY_LMENU) or Keyboard.isKeyDown(Keyboard.KEY_RMENU)
    return shift == expected.shift and ctrl == expected.ctrl and alt == expected.alt
end

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

local function clamp(value, minimum, maximum)
    return math.max(minimum, math.min(maximum, value))
end

local function resetTrackState()
    trackVehicles = {}
    trackPlayerVehicles = {}
    callJava("clearTireTrackMarks")
end

local function preloadTrackTextures()
    trackRenderingFailed = false
    if not getTexture then
        trackRenderingFailed = true
        print("[CarPhysicsImprovedV1] Tire marks unavailable: getTexture is missing")
        return
    end
    for index = 0, TRACK_TEXTURE_COUNT - 1 do
        local path = string.format("media/textures/CPI_V1_TireTrack_%02d.png", index)
        local texture = getTexture(path)
        if not texture then
            trackRenderingFailed = true
            print("[CarPhysicsImprovedV1] Tire mark texture is missing: " .. path)
            return
        end
        local ok = callJava("registerTireTrackTexture", index, texture)
        if not ok then
            trackRenderingFailed = true
            return
        end
    end
    local ok, rendererStatus = callJava("tireTrackRendererStatus")
    if ok then print("[CarPhysicsImprovedV1] Tire marks: " .. tostring(rendererStatus)) end
end

local function rearWheelOffsets(vehicle)
    local script = vehicle and vehicle:getScript()
    if not script then return nil end
    local key = script:getFullName()
    local cached = rearWheelCache[key]
    if cached then return cached.valid and cached.offsets or nil end

    local ok, offsets = pcall(function()
        local all = {}
        local count = script:getWheelCount()
        for index = 0, count - 1 do
            local wheel = script:getWheel(index)
            local offset = wheel and wheel:getOffset()
            if offset then
                all[#all + 1] = { x = offset:x(), z = offset:z(), index = index }
            end
        end
        table.sort(all, function(left, right) return left.z < right.z end)
        if #all > 2 then
            return { all[1], all[2] }
        end
        return all
    end)
    local valid = ok and offsets and #offsets > 0
    rearWheelCache[key] = { valid = valid, offsets = valid and offsets or nil }
    return valid and offsets or nil
end

local function vehicleForward(vehicle, state)
    if Vector3f and vehicle.getForwardVector then
        local ok, x, y = pcall(function()
            local forward = Vector3f.new(0, 0, 0)
            vehicle:getForwardVector(forward)
            return forward:x(), forward:z()
        end)
        x, y = tonumber(x), tonumber(y)
        local length = ok and x and y and math.sqrt(x * x + y * y) or 0
        if length > 0.0001 then return x / length, y / length end
    end
    local x, y = vehicle:getX(), vehicle:getY()
    if state.centerX and state.centerY then
        local dx, dy = x - state.centerX, y - state.centerY
        local length = math.sqrt(dx * dx + dy * dy)
        if length > 0.001 then return dx / length, dy / length end
    end
    return nil
end

local function wheelWorldPositions(vehicle, state)
    local offsets = rearWheelOffsets(vehicle)
    local forwardX, forwardY = vehicleForward(vehicle, state)
    if not offsets or not forwardX then return nil, nil, nil end
    local vehicleX, vehicleY = vehicle:getX(), vehicle:getY()
    local result = {}
    for _, offset in ipairs(offsets) do
        result[#result + 1] = {
            x = vehicleX + offset.z * forwardX - offset.x * forwardY,
            y = vehicleY + offset.z * forwardY + offset.x * forwardX,
            index = offset.index,
        }
    end
    state.centerX, state.centerY = vehicleX, vehicleY
    return result, forwardX, forwardY
end

local function textureIndexForDirection(dx, dy)
    local screenX = dx - dy
    local screenY = (dx + dy) * 0.5
    if screenX * screenX + screenY * screenY < 0.000001 then return 8 end
    local angle = math.atan2(screenY, screenX)
    if angle < 0 then angle = angle + math.pi end
    if angle >= math.pi then angle = angle - math.pi end
    return math.floor(angle / math.pi * TRACK_TEXTURE_COUNT + 0.5) % TRACK_TEXTURE_COUNT
end

local function isAsphaltAt(x, y, z)
    local cell = getCell and getCell()
    local square = cell and cell:getGridSquare(math.floor(x), math.floor(y), math.floor(z))
    local floor = square and square:getFloor()
    local sprite = floor and floor:getSprite()
    local name = sprite and sprite:getName()
    if not name then return false end

    -- This follows BaseVehicle.isDoingOffroad() in B42.20.4, narrowed to paved
    -- street floors. The 48-55 group is the vanilla shovelable gravel set.
    if name:find("floors_exterior_street", 1, true) then return true end
    if not name:find("blends_street", 1, true) then return false end
    if name:find("blends_street_01_", 1, true) then
        local tile = tonumber(name:match(".*_([0-9]+)$"))
        if tile and tile >= 48 and tile <= 55 then return false end
    end
    return true
end

local function addTrackMark(x, y, z, dx, dy, intensity, now)
    if not isAsphaltAt(x, y, z) then return end
    local ok = callJava("addTireTrackMark",
        x, y, math.floor(z), textureIndexForDirection(dx, dy),
        clamp(0.28 + intensity * 0.55, 0.28, 0.83))
    if not ok then
        trackRenderingFailed = true
    end
end

local function clearVehicleStreak(state)
    state.wheels = nil
    state.lastStationaryStampMs = nil
end

local function updateTrackMarks(player)
    if not player then return end
    if player:isDead() or trackRenderingFailed
            or not skidMarks:getValue() or not CarPhysicsImprovedV1.javaReady then
        local previousId = trackPlayerVehicles[player]
        if previousId and trackVehicles[previousId] then clearVehicleStreak(trackVehicles[previousId]) end
        trackPlayerVehicles[player] = nil
        return
    end
    local vehicle = player:getVehicle()
    if not vehicle or vehicle:getDriver() ~= player then
        local previousId = trackPlayerVehicles[player]
        if previousId and trackVehicles[previousId] then clearVehicleStreak(trackVehicles[previousId]) end
        trackPlayerVehicles[player] = nil
        return
    end
    local id = vehicle:getId()
    local previousId = trackPlayerVehicles[player]
    if previousId and previousId ~= id and trackVehicles[previousId] then
        clearVehicleStreak(trackVehicles[previousId])
    end
    trackPlayerVehicles[player] = id
    local state = trackVehicles[id]
    if not state then
        state = { wheels = nil, lastSampleMs = 0 }
        trackVehicles[id] = state
    end
    local now = getTimestampMs()
    if now - state.lastSampleMs < TRACK_SAMPLE_MS then return end
    state.lastSampleMs = now

    local ok, amount = callJava("skidAmountFor", id)
    local intensity = ok and tonumber(amount) or 0
    if not intensity or intensity < 0.14 then
        clearVehicleStreak(state)
        state.centerX, state.centerY = vehicle:getX(), vehicle:getY()
        return
    end

    local wheels, forwardX, forwardY = wheelWorldPositions(vehicle, state)
    if not wheels then
        clearVehicleStreak(state)
        return
    end
    local previousWheels = state.wheels
    local currentWheels = {}
    local z = vehicle:getZ()
    local burnoutOk, burnoutAmount = callJava("burnoutAmountFor", id)
    local burnout = burnoutOk and tonumber(burnoutAmount) or 0
    for _, wheel in ipairs(wheels) do
        local previous = previousWheels and previousWheels[wheel.index]
        if previous and previous.z == math.floor(z) then
            local dx, dy = wheel.x - previous.x, wheel.y - previous.y
            local distance = math.sqrt(dx * dx + dy * dy)
            if distance >= 0.02 and distance <= TRACK_MAX_FILL_TILES then
                local steps = math.min(TRACK_MAX_STAMPS_PER_WHEEL,
                    math.max(1, math.ceil(distance / TRACK_SPACING_TILES)))
                for step = 1, steps do
                    local fraction = step / steps
                    addTrackMark(previous.x + dx * fraction, previous.y + dy * fraction,
                        z, dx, dy, intensity, now)
                end
            elseif distance < 0.02 and burnout and burnout >= 4.0 then
                local stampKey = tostring(wheel.index)
                state.lastStationaryStampMs = state.lastStationaryStampMs or {}
                local lastStamp = state.lastStationaryStampMs[stampKey] or 0
                if now - lastStamp >= 350 then
                    addTrackMark(wheel.x, wheel.y, z, forwardX, forwardY, intensity, now)
                    state.lastStationaryStampMs[stampKey] = now
                end
            end
        end
        currentWheels[wheel.index] = { x = wheel.x, y = wheel.y, z = math.floor(z) }
    end
    state.wheels = currentWheels
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
    refreshKeyModifiers()
    callJava("setEnabled", enabled:getValue())
    callJava("setManualTransmission", manual:getValue())
    callJava("setTelemetry", telemetry:getValue())
    callJava("configureTireTracks",
        skidMarks:getValue(), skidMarkLifetime:getValue(), skidMarkOpacity:getValue())
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
    callJava("configureTerrain",
        sandboxValue("HeavyDutyOffroadAdvantagePercent", 60.0) / 100.0,
        sandboxValue("HeavyDutyRainAdvantagePercent", 45.0) / 100.0,
        sandboxValue("HeavyDutySnowAdvantagePercent", 60.0) / 100.0,
        sandboxValue("HeavyDutyOffroadResistancePercent", 55.0) / 100.0,
        sandboxValue("TerrainHandlingInfluencePercent", 45.0) / 100.0)
    callJava("configureSlide",
        sandboxBool("SlideMechanics", true),
        sandboxValue("DriftIntensity", 1.0),
        sandboxValue("StabilityAssist", 1.0),
        sandboxValue("PowerDriftDelay", 0.8),
        sandboxBool("ClutchKick", true),
        sandboxValue("PowerDriftRotation", 2000.0),
        sandboxValue("HandbrakeDriftRotation", 2000.0),
        sandboxValue("PowerDriftGripPercent", 35.0) / 100.0,
        sandboxValue("HandbrakeDriftGripPercent", 35.0) / 100.0,
        sandboxValue("DriftSteeringPercent", 150.0) / 100.0)
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
    local direction = 0
    if key == shiftUp:getValue() and modifiersMatch(keyModifiers[SHIFT_UP_BINDING]) then
        direction = 1
    elseif key == shiftDown:getValue() and modifiersMatch(keyModifiers[SHIFT_DOWN_BINDING]) then
        direction = -1
    end
    if direction == 0 then return end
    local player = getPlayer()
    local vehicle = player and player:getVehicle()
    if vehicle then
        if GameKeyboard and GameKeyboard.eatKeyPress then GameKeyboard.eatKeyPress(key) end
        callJava("requestShiftFor", vehicle:getId(), direction)
    end
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
    local threshold = active and 0.06 or 0.16
    if not value or value <= threshold then stopSound(player) return end
    if not active then
        activeSounds[player] = { vehicle = vehicle, sound = vehicle:playSoundImpl("CPI_V1_TireSkid", nil) }
    end
end

local function reportStatus()
    applyOptions()
    local ok, value = callJava("status")
    print("[CarPhysicsImprovedV1] " .. tostring(ok and value or "ZombieBuddy did not expose V1 Java runtime"))
    local rendererOk, rendererStatus = callJava("tireTrackRendererStatus")
    if rendererOk then print("[CarPhysicsImprovedV1] Tire marks: " .. tostring(rendererStatus)) end
end

options.apply = applyOptions
Events.OnGameBoot.Add(loadAndApply)
Events.OnGameBoot.Add(preloadTrackTextures)
Events.OnKeyStartPressed.Add(onKeyPressed)
Events.OnGameStart.Add(resetTrackState)
Events.OnGameStart.Add(reportStatus)
Events.OnPlayerUpdate.Add(updateSound)
Events.OnPlayerUpdate.Add(updateTrackMarks)

CarPhysicsImprovedV1.options = options
CarPhysicsImprovedV1.skidMarks = skidMarks
CarPhysicsImprovedV1.skidMarkLifetime = skidMarkLifetime
CarPhysicsImprovedV1.skidMarkOpacity = skidMarkOpacity
