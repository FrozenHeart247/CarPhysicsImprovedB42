-- UI/INI wiring tests. Does not simulate GameKeyboard or Bullet.
local clientFile = assert(arg[1], "client Lua path required")
local function runCase(lines, key)
    local calls, bindings, events, options = {}, {}, {}, nil
    require = function() end
    Keyboard = { KEY_UP = 200, KEY_DOWN = 208, KEY_SPACE = 57,
        KEY_LSHIFT = 42, KEY_RSHIFT = 54, KEY_LCONTROL = 29, KEY_RCONTROL = 157,
        KEY_LMENU = 56, KEY_RMENU = 184 }
    local pressed = {}
    Keyboard.isKeyDown = function(code) return pressed[code] == true end
    Events = setmetatable({}, { __index = function(t, name)
        local list = {}
        events[name] = list
        local event = { Add = function(fn) list[#list + 1] = fn end }
        rawset(t, name, event)
        return event
    end })
    CarPhysicsImprovedV1 = { flushVehicleSpecs = function() end }
    CarPhysicsImprovedV1Mod = setmetatable({}, { __index = function(_, name)
        return function(...) calls[name] = { ... } end
    end })
    getCore = function() return { addKeyBinding = function(_, ...) bindings[#bindings + 1] = { ... } end } end
    getFileReader = function()
        local index = 0
        return { readLine = function() index = index + 1; return lines[index] end, close = function() end }
    end
    PZAPI = { ModOptions = {} }
    function PZAPI.ModOptions:create()
        options = { dict = {} }
        function options:addTickBox(id, _, value) return self:addValue(id, value) end
        function options:addSlider(id, _, _, _, _, value) return self:addValue(id, value) end
        function options:addKeyBind(id, _, value) return self:addValue(id, value) end
        function options:addDescription() end
        function options:addValue(id, value)
            local entry = { value = value, getValue = function(self) return self.value end }
            self.dict[id] = entry
            return entry
        end
        return options
    end
    function PZAPI.ModOptions:load()
        if key then options.dict.DriftKey.value = key end
        options.dict.ShiftUp.value = 18
        options.dict.ShiftDown.value = 16
    end
    dofile(clientFile)
    events.OnGameBoot[1]()
    return calls, bindings, options, events, pressed
end

local calls, bindings, options = runCase({})
assert(calls.configureRearAxleDrift[1] == false, "Retired axle experiment must stay disabled")
assert(options.dict.RearAxleDrift == nil and options.dict.RearAxleGrip == nil,
    "Retired options must not remain visible")
assert(calls.configureKeyDrift[1] == 2000 and calls.configureKeyDrift[2] == .35
    and calls.configureKeyDrift[3] == 1.5 and calls.configureKeyDrift[4] == 20,
    "Key drift starts with reference defaults independent of legacy power settings")
options.dict.KeyDriftRotation.value = 1800
options.apply()
assert(calls.configureKeyDrift[1] == 1800, "Key drift options must reach Java")
assert(calls.configureDriftKey[1] == 42 and calls.configureDriftKey[2] == false,
    "Default must be standalone Left Shift even before opening options")
assert(options.dict.DriftKey.shift == false, "Standalone Shift must not have an extra modifier")
assert(bindings[1][2] == 0, "No global native action may shadow Space on foot/with V1 disabled")

calls, bindings, options = runCase({ "CPI V1 drift (hold)=key:57;shift:true" }, 57)
assert(calls.configureDriftKey[1] == 57 and calls.configureDriftKey[2] == true,
    "Changing the default must not overwrite a previously saved Shift+Space binding")

calls, bindings, options = runCase({ "CPI V1 drift (hold)=key:45;ctrl:true" }, 45)
assert(calls.configureDriftKey[1] == 45 and calls.configureDriftKey[2] == false
    and calls.configureDriftKey[3] == true and calls.configureDriftKey[4] == false,
    "Reload must restore Ctrl+X without the old default Shift")
assert(options.dict.DriftKey.shift == false and options.dict.DriftKey.ctrl == true,
    "Reopening options must preserve saved modifier metadata")

calls, bindings, options = runCase({ "CPI V1 drift (hold)=key:45" }, 45)
assert(calls.configureDriftKey[2] == false and calls.configureDriftKey[3] == false,
    "A saved plain key must not inherit Shift")

local events, pressed
calls, bindings, options, events, pressed = runCase({
    "Shift up=key:18;shift:true", "Shift down=key:16;shift:true"
})
assert(calls.configureShiftKeys[1] == 18 and calls.configureShiftKeys[2] == true
    and calls.configureShiftKeys[5] == 16 and calls.configureShiftKeys[6] == true,
    "Java must receive the exact gear bindings for priority over standalone drift Shift")
options.dict.ManualTransmission.value = true
options.dict.ShiftUp.value = 18
options.dict.ShiftDown.value = 16
getPlayer = function() return { getVehicle = function() return { getId = function() return 12 end } end } end
GameKeyboard = { eatKeyPress = function() end }
events.OnKeyStartPressed[1](18)
assert(calls.requestShiftFor == nil, "Plain E must not activate Shift+E")
pressed[42] = true
events.OnKeyStartPressed[1](18)
assert(calls.requestShiftFor[1] == 12 and calls.requestShiftFor[2] == 1, "Shift+E must still shift up")
events.OnKeyStartPressed[1](16)
assert(calls.requestShiftFor[2] == -1, "Shift+Q must still shift down")
print("drift_key_options_test: defaults, rebinding, reload and gear combinations passed")
