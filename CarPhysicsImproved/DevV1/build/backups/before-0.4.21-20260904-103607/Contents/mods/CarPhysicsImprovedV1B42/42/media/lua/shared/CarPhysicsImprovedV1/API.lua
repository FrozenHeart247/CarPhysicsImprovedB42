CarPhysicsImprovedV1 = CarPhysicsImprovedV1 or {}
CarPhysicsImprovedV1.vehicleSpecs = CarPhysicsImprovedV1.vehicleSpecs or {}

local function javaBridge()
    return CarPhysicsImprovedV1Mod
end

function CarPhysicsImprovedV1.registerVehicleSpec(fullType, horsePower, massKg, cargoKg)
    if not fullType then return false end
    local spec = {
        horsePower = tonumber(horsePower),
        massKg = tonumber(massKg),
        cargoKg = tonumber(cargoKg) or 0,
    }
    if not spec.horsePower or not spec.massKg then return false end
    CarPhysicsImprovedV1.vehicleSpecs[fullType] = spec
    local bridge = javaBridge()
    if bridge and bridge.registerVehicleSpec then
        local ok = pcall(bridge.registerVehicleSpec, fullType, spec.horsePower, spec.massKg, spec.cargoKg)
        return ok
    end
    return false
end

function CarPhysicsImprovedV1.flushVehicleSpecs()
    for fullType, spec in pairs(CarPhysicsImprovedV1.vehicleSpecs) do
        CarPhysicsImprovedV1.registerVehicleSpec(fullType, spec.horsePower, spec.massKg, spec.cargoKg)
    end
end

Events.OnGameBoot.Add(CarPhysicsImprovedV1.flushVehicleSpecs)
