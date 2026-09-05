package dev.carphysicsimproved.v1.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;
import pzmod.carphysicsimproved.v1.CarPhysicsImprovedV1Mod;
import pzmod.carphysicsimproved.v1.Patch_ClothingWetness;

/** Verify actual installed ZB weaving, not annotation presence or a startup log. */
public final class LegacyReleasePatchTest {
    private LegacyReleasePatchTest() { }

    public static void main(String[] args) throws Exception {
        Field agent = field(LegacyAxleDriftPatchTest.class, "instrumentation");
        check(agent.get(null) != null, "Test-only instrumentation required");
        field(Class.forName("me.zed_0xff.zombie_buddy.Loader"), "g_instrumentation").set(null, agent.get(null));
        ClassLoader loader = LegacyReleasePatchTest.class.getClassLoader();
        String[][] expected = {
            {"Patch_BaseVehicleImpulses$Plant", "plantImpulse"},
            {"Patch_BaseVehicleImpulses$Pedestrian", "begin", "end"},
            {"Patch_BaseVehicleImpulses$Corpse", "begin", "end"},
            {"Patch_BaseVehicleImpulses$ProneCharacter", "applyProneBump"},
            {"Patch_BaseVehicleImpulses$ObstacleSlowdown", "beginObstacleSlowdown", "endObstacleSlowdown"},
            {"Patch_BaseVehicle", "onVehicleCrash"}, {"Patch_Temperature", "adjustWindChill"},
            {"Patch_ClothingWetness", "adjustRainInputs"},
            {"Patch_WorldSimulationVisual", "afterVehicleReadback"},
            {"Patch_CarController$Controls", "afterControllerControls"},
            {"Patch_CarController$Update", "beforeControllerUpdate", "afterControllerUpdate"},
            {"Patch_WorldSimulation", "afterVehiclePhysics"},
            {"Patch_BaseVehicleAuthority", "onVehicleAuthorizationChanged"},
            {"Patch_BaseVehicleWheelGrip", "afterWheel"}
        };
        try (ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(loader)) {
            for (String[] entry : expected) {
                Class<?> patch = Class.forName("pzmod.carphysicsimproved.v1." + entry[0]);
                var annotation = patch.getAnnotation(me.zed_0xff.zombie_buddy.Patch.class);
                TypeDescription target = TypePool.Default.of(locator).describe(annotation.className()).resolve();
                byte[] bytes = weave(new ByteBuddy().redefine(target, locator), target, patch).make().getBytes();
                List<String> calls = calls(bytes, annotation.methodName());
                for (int i = 1; i < entry.length; i++) {
                    String hook = entry[i];
                    check(calls.stream().filter(hook::equals).count() == 1,
                            "Exactly one " + hook + " must be woven into " + entry[0] + ": " + calls);
                }
            }
            // The engine readback must remain inside updateInternal, before our exit advice.
            List<String> nativeCalls = calls(locator.locate("zombie.core.physics.WorldSimulation").resolve(), "updateInternal");
            check(nativeCalls.contains("getVehiclePhysics"), "Readback hook targets the real wheel readback method");
            List<String> controllerCalls = calls(locator.locate(LegacyHooks.class.getName()).resolve(),
                    "afterControllerUpdate");
            check(controllerCalls.stream().filter("withDelivery"::equals).count() == 1,
                    "Production controller applies the launch filter exactly once");
            check(controllerCalls.indexOf("step") < controllerCalls.indexOf("forceScale")
                    && controllerCalls.indexOf("forceScale") < controllerCalls.indexOf("withDelivery")
                    && controllerCalls.indexOf("withDelivery") < controllerCalls.indexOf("withSteering")
                    && controllerCalls.indexOf("withSteering") < controllerCalls.indexOf("apply"),
                    "Production call order: drivetrain -> launch delivery -> optional key steering -> native apply");
            clothingFixture(locator, loader);
        }
        System.out.println("LegacyReleasePatchTest: 14 installed-game patches matched; "
                + "single clothing update, mutable rain arguments and disabled passthrough executed");
    }

    private static void clothingFixture(ClassFileLocator locator, ClassLoader loader) throws Exception {
        TypeDescription fixture = TypePool.Default.of(locator).describe(ClothingFixture.class.getName()).resolve();
        Class<?> woven = weave(new ByteBuddy().redefine(fixture, locator)
                .name("dev.carphysicsimproved.v1.runtime.WovenClothingFixture"), fixture, Patch_ClothingWetness.class)
                .make().load(loader, ClassLoadingStrategy.Default.WRAPPER).getLoaded();
        Object clothing = woven.getConstructor().newInstance();
        Class<?> accessClass = Class.forName(LegacyCabinExposureHooks.class.getName() + "$Access");
        var constructor = accessClass.getDeclaredConstructor(); constructor.setAccessible(true);
        Object access = constructor.newInstance();
        field(accessClass, "clothingCharacter").set(access, woven.getField("character"));
        field(accessClass, "characterVehicle").set(access, Cabin.class.getMethod("getVehicle"));
        field(accessClass, "vehiclePartById").set(access, Cabin.class.getMethod("getPartById", String.class));
        field(accessClass, "vehicleSpeed").set(access, Cabin.class.getMethod("getCurrentSpeedKmHour"));
        field(accessClass, "climateInstance").set(access, Cabin.class.getMethod("getInstance"));
        field(accessClass, "climateRaining").set(access, Cabin.class.getMethod("isRaining"));
        field(accessClass, "climateRainIntensity").set(access, Cabin.class.getMethod("getRainIntensity"));
        field(LegacyCabinExposureHooks.class, "access").set(null, access);
        Method update = woven.getMethod("updateWetness", float.class, float.class);
        CarPhysicsImprovedV1Mod.setEnabled(true);
        update.invoke(clothing, 0f, .1f);
        check(woven.getField("updates").getInt(clothing) == 1, "Sweat/drying body runs once, not twice");
        check(woven.getField("rain").getFloat(clothing) > .5f, "Woven mutable argument adds windshield rain");
        check(woven.getField("dry").getFloat(clothing) == 0, "No simultaneous extra drying");
        CarPhysicsImprovedV1Mod.setEnabled(false);
        update.invoke(clothing, .2f, .3f);
        check(woven.getField("updates").getInt(clothing) == 2, "Disabled body still executes once");
        check(woven.getField("rain").getFloat(clothing) == .2f
                && woven.getField("dry").getFloat(clothing) == .3f, "Disabled arguments exactly preserved");
        CarPhysicsImprovedV1Mod.setEnabled(true);
        field(LegacyCabinExposureHooks.class, "access").set(null, null);
    }

    public static class ClothingFixture {
        public Object character = new Cabin();
        public int updates;
        public float rain, dry;
        public void updateWetness(float increase, float decrease) { updates++; rain = increase; dry = decrease; }
    }
    public static final class Cabin {
        public Object getVehicle() { return this; }
        public Object getPartById(String ignored) { return null; }
        public float getCurrentSpeedKmHour() { return 60; }
        public static Cabin getInstance() { return new Cabin(); }
        public boolean isRaining() { return true; }
        public float getRainIntensity() { return 1; }
    }

    private static DynamicType.Builder<?> weave(DynamicType.Builder<?> builder, TypeDescription target,
            Class<?> patch) throws Exception {
        Method method = LegacyMultiplayerPatchTest.class.getDeclaredMethod("weave",
                DynamicType.Builder.class, TypeDescription.class, Class.class);
        method.setAccessible(true);
        return (DynamicType.Builder<?>) method.invoke(null, builder, target, patch);
    }
    private static List<String> calls(byte[] bytes, String target) {
        List<String> result = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int flags, String name, String desc, String signature, String[] exceptions) {
                if (!name.equals(target)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) {
                        result.add(method);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return result;
    }
    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
