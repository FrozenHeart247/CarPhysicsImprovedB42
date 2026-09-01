require "RoadcraftDynamics/RCD_Bridge"

local function reportServerStatus()
    RoadcraftDynamics.applySandboxOptions()
    local bridge = RoadcraftDynamics.getBridge()
    if bridge then
        print("[RoadcraftDynamics] Server runtime " .. tostring(bridge:status()) .. ": " .. tostring(bridge:statusDetail()))
    else
        print("[RoadcraftDynamics] ZombieBuddy did not load the Roadcraft JAR on the server. Confirm the dependency, approval policy, and console log.")
    end
end

RoadcraftDynamics.onRuntimeBridgeReady(reportServerStatus)
Events.OnServerStarted.Add(reportServerStatus)
