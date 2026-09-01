# Provenance

- All shipped Java and Lua sources under `DevV1/src` and `Contents/mods/CarPhysicsImprovedV1B42` were written for this project.
- The supplied Realistic Car Physics binary and Lua files were inspected only to identify observable mechanics, constants, public data shapes, and old integration points.
- The supplied RCP KI5 Patch was inspected only to understand its merge-based vehicle-data interface. Its vehicle table is not copied or bundled.
- No `zombie.*` class and no reference-mod source or engine audio is present in the V1 JAR or release package.
- The tire loop is the existing Car Physics Improved project asset, renamed at the event/file boundary for V1.
- ZombieBuddy is a runtime/build dependency and is not redistributed.
- Vineflower 1.12.0 is kept only under `DevV1/tools` for local bytecode analysis and is never included in the Workshop package. Downloaded from the official Vineflower GitHub release; SHA-256 `1DFCFE974395734FA467CE620661C7623D05BA83670DE0529B1FBD63FF548B9D`.
