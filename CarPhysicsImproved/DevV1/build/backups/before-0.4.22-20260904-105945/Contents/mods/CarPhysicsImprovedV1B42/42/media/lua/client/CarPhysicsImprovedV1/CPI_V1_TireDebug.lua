require "Vehicles/ISUI/ISVehicleMechanics"

CarPhysicsImprovedV1 = CarPhysicsImprovedV1 or {}

local CONDITION_PRESETS = { 100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0 }
local PRESSURE_PRESETS = { 100, 75, 50, 25, 0 }

local function debugToolsAllowed()
    if (getDebug and getDebug()) or (isDebugEnabled and isDebugEnabled()) then return true end
    if not isClient or not isClient() then return false end
    return (isAdmin and isAdmin()) or (getAccessLevel and getAccessLevel() == "moderator")
end

local function wheelIndex(part)
    if not part or not part.getWheelIndex then return -1 end
    local ok, index = pcall(function() return part:getWheelIndex() end)
    return ok and tonumber(index) or -1
end

local function isInstalledTire(part)
    if wheelIndex(part) < 0 or not part:getInventoryItem() then return false end
    return part:isContainer() and part:getContainerCapacity() > 0
end

local function installedTires(vehicle)
    local tires = {}
    if not vehicle then return tires end
    for index = 0, vehicle:getPartCount() - 1 do
        local part = vehicle:getPartByIndex(index)
        if isInstalledTire(part) then tires[#tires + 1] = part end
    end
    return tires
end

local function setCondition(playerObj, parts, condition)
    for _, part in ipairs(parts) do
        local vehicle = part:getVehicle()
        if vehicle then
            sendClientCommand(playerObj, "vehicle", "setPartCondition", {
                vehicle = vehicle:getId(),
                part = part:getId(),
                condition = condition,
            })
        end
    end
end

local function setPressure(playerObj, parts, percent)
    for _, part in ipairs(parts) do
        local vehicle = part:getVehicle()
        local capacity = tonumber(part:getContainerCapacity()) or 0
        if vehicle and capacity > 0 then
            sendClientCommand(playerObj, "vehicle", "setTirePressure", {
                vehicle = vehicle:getId(),
                part = part:getId(),
                psi = capacity * percent / 100,
            })
        end
    end
end

local function addValueMenu(parentContext, parentOption, values, playerObj, parts, callback)
    local valueMenu = ISContextMenu:getNew(parentContext)
    parentContext:addSubMenu(parentOption, valueMenu)
    for _, value in ipairs(values) do
        valueMenu:addOption(tostring(value) .. "%", playerObj, callback, parts, value)
    end
end

local function addTargetMenu(parentContext, label, playerObj, parts)
    local targetOption = parentContext:addOption(label)
    local targetMenu = ISContextMenu:getNew(parentContext)
    parentContext:addSubMenu(targetOption, targetMenu)

    local conditionOption = targetMenu:addOption(getText("ContextMenu_CPI_V1_TireCondition"))
    addValueMenu(targetMenu, conditionOption, CONDITION_PRESETS, playerObj, parts, setCondition)

    local pressureOption = targetMenu:addOption(getText("ContextMenu_CPI_V1_TirePressure"))
    addValueMenu(targetMenu, pressureOption, PRESSURE_PRESETS, playerObj, parts, setPressure)
end

local function addTireDebugMenu(mechanics, part)
    if not debugToolsAllowed() or not mechanics.context or not isInstalledTire(part) then return end

    local playerObj = getSpecificPlayer(mechanics.playerNum)
    if not playerObj then return end

    local rootOption = mechanics.context:addOption(getText("ContextMenu_CPI_V1_TireDebug"))
    local rootMenu = ISContextMenu:getNew(mechanics.context)
    mechanics.context:addSubMenu(rootOption, rootMenu)

    addTargetMenu(rootMenu, getText("ContextMenu_CPI_V1_SelectedTire"), playerObj, { part })

    local tires = installedTires(part:getVehicle())
    if #tires > 1 then
        addTargetMenu(rootMenu, getText("ContextMenu_CPI_V1_AllTires"), playerObj, tires)
    end

    mechanics.context:setVisible(true)
end

if not CarPhysicsImprovedV1.tireDebugMenuInstalled then
    CarPhysicsImprovedV1.tireDebugMenuInstalled = true
    local vanillaDoPartContextMenu = ISVehicleMechanics.doPartContextMenu
    function ISVehicleMechanics:doPartContextMenu(part, x, y)
        vanillaDoPartContextMenu(self, part, x, y)
        addTireDebugMenu(self, part)
    end
end
