package zombie.roadcraft.runtime;

import me.zed_0xff.zombie_buddy.Exposer;
import me.zed_0xff.zombie_buddy.Patch;

import java.nio.file.Path;
import java.util.jar.JarFile;

/** Static contract check for the ZB package and the tested B42.20.4 ABI. */
public final class ZombieBuddyPackageSmokeTest {
    private static int assertions;

    private ZombieBuddyPackageSmokeTest() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected the built ZombieBuddy JAR path");
        }
        Path jarPath = Path.of(arguments[0]).toAbsolutePath().normalize();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            requiredEntry(jar, "pzmod/roadcraft/Main.class");
            requiredEntry(jar, "pzmod/roadcraft/RoadcraftMod.class");
            requiredEntry(jar, "pzmod/roadcraft/Patch_CarController$Update.class");
            requiredEntry(jar, "pzmod/roadcraft/Patch_BaseVehicle$HitPlant.class");
            check(jar.getEntry("zombie/core/physics/CarController.class") == null,
                    "full CarController replacement must not ship");
        }

        patch("pzmod.roadcraft.Patch_CarController$Update",
                "zombie.core.physics.CarController", "update");
        patch("pzmod.roadcraft.Patch_CarController$UpdateTrailer",
                "zombie.core.physics.CarController", "updateTrailer");
        patch("pzmod.roadcraft.Patch_BaseVehicle$AutoStart",
                "zombie.vehicles.BaseVehicle", "tryStartEngine");
        patch("pzmod.roadcraft.Patch_BaseVehicle$HitPlant",
                "zombie.vehicles.BaseVehicle", "applyImpulseFromHitPlant");
        patch("pzmod.roadcraft.Patch_BaseVehicle$HitPedestrian",
                "zombie.vehicles.BaseVehicle", "applyImpulseFromHitPedestrian");
        patch("pzmod.roadcraft.Patch_BaseVehicle$HitCorpse",
                "zombie.vehicles.BaseVehicle", "applyImpulseFromHitCorpse");
        patch("pzmod.roadcraft.Patch_WorldSimulation",
                "zombie.core.physics.WorldSimulation", "updateVehiclePhysics");

        Class<?> facade = Class.forName("pzmod.roadcraft.RoadcraftMod", false,
                Thread.currentThread().getContextClassLoader());
        Exposer.LuaClass luaClass = facade.getAnnotation(Exposer.LuaClass.class);
        check(luaClass != null && luaClass.name().isEmpty(),
                "Lua facade uses ZombieBuddy's reliable simple-name exposure");
        facade.getDeclaredMethod("burnoutAmountFor", int.class);

        Class<?> controller = Class.forName("zombie.core.physics.CarController", false,
                Thread.currentThread().getContextClassLoader());
        controller.getDeclaredMethod("update");
        controller.getDeclaredMethod("updateTrailer");
        Class<?> baseVehicle = Class.forName("zombie.vehicles.BaseVehicle", false,
                Thread.currentThread().getContextClassLoader());
        baseVehicle.getDeclaredField("impulsesFromHitObjects");
        baseVehicle.getDeclaredMethod("applyImpulseFromHitPlant",
                Class.forName("zombie.iso.IsoObject"), float.class);
        baseVehicle.getDeclaredMethod("applyImpulseFromHitPedestrian",
                Class.forName("zombie.characters.IsoGameCharacter"));
        baseVehicle.getDeclaredMethod("applyImpulseFromHitCorpse",
                Class.forName("zombie.iso.objects.IsoDeadBody"));
        Class<?> worldSimulation = Class.forName("zombie.core.physics.WorldSimulation", false,
                Thread.currentThread().getContextClassLoader());
        worldSimulation.getDeclaredMethod("updateVehiclePhysics");

        new PzAccess();
        System.out.println("ZombieBuddyPackageSmokeTest: " + assertions
                + " package and B42.20.4 contract checks passed");
    }

    private static void patch(String className, String targetClass, String targetMethod)
            throws Exception {
        Class<?> type = Class.forName(className, false,
                Thread.currentThread().getContextClassLoader());
        Patch patch = type.getAnnotation(Patch.class);
        check(patch != null, className + " has @Patch");
        check(targetClass.equals(patch.className()), className + " target class");
        check(targetMethod.equals(patch.methodName()), className + " target method");
    }

    private static void requiredEntry(JarFile jar, String name) {
        check(jar.getEntry(name) != null, "JAR entry " + name);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
