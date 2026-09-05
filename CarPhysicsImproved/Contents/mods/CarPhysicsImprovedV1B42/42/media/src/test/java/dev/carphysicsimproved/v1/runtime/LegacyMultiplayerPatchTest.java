package dev.carphysicsimproved.v1.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import pzmod.carphysicsimproved.v1.Patch_BaseVehicleAuthority;
import pzmod.carphysicsimproved.v1.Patch_CarController;
import pzmod.carphysicsimproved.v1.Patch_WorldSimulation;

/** Actual installed ZB matcher/advice on game bytecode plus executable ownership fixture. */
public final class LegacyMultiplayerPatchTest {
    private LegacyMultiplayerPatchTest() { }

    public static void main(String[] args) throws Exception {
        Field agent = LegacyAxleDriftPatchTest.class.getDeclaredField("instrumentation");
        agent.setAccessible(true);
        check(agent.get(null) != null, "Test-only agent must be attached");
        Field instrumentation = Class.forName("me.zed_0xff.zombie_buddy.Loader").getDeclaredField("g_instrumentation");
        instrumentation.setAccessible(true);
        instrumentation.set(null, agent.get(null));
        ClassLoader loader = LegacyMultiplayerPatchTest.class.getClassLoader();
        try (ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(loader)) {
            verifyNative(locator, "zombie.vehicles.BaseVehicle", Patch_BaseVehicleAuthority.class,
                    "setNetPlayerAuthorization", "onVehicleAuthorizationChanged", 1);
            verifyNative(locator, "zombie.core.physics.CarController", Patch_CarController.Update.class,
                    "update", "beforeControllerUpdate", 1);
            verifyNative(locator, "zombie.core.physics.CarController", Patch_CarController.Update.class,
                    "update", "afterControllerUpdate", 1); // Shared normal/Throwable exit advice.
            verifyNative(locator, "zombie.core.physics.WorldSimulation", Patch_WorldSimulation.class,
                    "updateVehiclePhysics", "afterVehiclePhysics", 1);
            verifyFixedStepOrder(locator);

            TypeDescription fixtureType = TypePool.Default.of(locator).describe(AuthorityFixture.class.getName()).resolve();
            Class<?> woven = weave(new ByteBuddy().redefine(fixtureType, locator)
                            .name("dev.carphysicsimproved.v1.runtime.WovenOwnershipFixture"),
                    fixtureType, Patch_BaseVehicleAuthority.class).make()
                    .load(loader, ClassLoadingStrategy.Default.WRAPPER).getLoaded();
            var car = (LegacyMultiplayerRuntimeTest.Car)woven.getConstructor().newInstance();
            PzLegacyAccess access = LegacyMultiplayerRuntimeTest.fixtureAccess();
            LegacyMultiplayerRuntimeTest.field(LegacyHooks.class, "access").set(null, access);
            LegacyMultiplayerRuntimeTest.Flags.client = true;
            LegacyMultiplayerRuntimeTest.Flags.server = false;
            LegacyHooks.RuntimeState first = LegacyHooks.prepareSession(access, car);
            access.applyWheelFrictionScale(car, .2);
            Method change = woven.getMethod("setNetPlayerAuthorization", Object.class, int.class);
            change.invoke(car, "Remote", 2);
            check(Math.abs(car.script.wheelFriction - 1.8) < 1e-6,
                    "Woven callback must see NEW authority and restore old grip");
            change.invoke(car, "Local", 1);
            check(LegacyHooks.prepareSession(access, car) != first,
                    "Woven loss/reacquire between ticks drops old session");
            check(!(Boolean)LegacyMultiplayerRuntimeTest.field(LegacyHooks.class, "failed").get(null),
                    "No swallowed callback failure");
            LegacyHooks.releaseVehicleSessions();
        }
        System.out.println("LegacyMultiplayerPatchTest: real ZB weaving into installed ownership/controller/fixed-step "
                + "methods and executable post-authorization cleanup passed; no game world/network started");
    }

    private static void verifyFixedStepOrder(ClassFileLocator locator) throws Exception {
        int[] steps = {0};
        new ClassReader(locator.locate("zombie.core.physics.WorldSimulation").resolve())
                .accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int flags, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (!name.equals("updatePhysic")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    private String previousCall;
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String desc, boolean isInterface) {
                        if (owner.equals("zombie/core/physics/Bullet") && method.equals("stepSimulation")) {
                            check("zombie/core/physics/WorldSimulation.updateVehiclePhysics".equals(previousCall),
                                    "Fixed-step hook must run immediately before Bullet, not after the render frame");
                            steps[0]++;
                        }
                        previousCall = owner + "." + method;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        check(steps[0] == 1, "Expected one Bullet step call inside the native substep loop");
        int[] frameForces = {0};
        new ClassReader(locator.locate("dev.carphysicsimproved.v1.runtime.LegacyHooks").resolve())
                .accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int flags, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (!name.equals("afterControllerUpdate")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String desc, boolean isInterface) {
                        if (!owner.equals("dev/carphysicsimproved/v1/runtime/PzLegacyAccess")) return;
                        check(!method.equals("applyKeyDriftTorque"), "No duplicate controller-time key torque");
                        if (method.equals("applySlideForces")) frameForces[0]++;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        check(frameForces[0] == 0, "Neither key nor ordinary slide forces are submitted at frame cadence");
    }

    private static void verifyNative(ClassFileLocator locator, String name, Class<?> patch,
            String method, String hook, int expected) throws Exception {
        TypeDescription target = TypePool.Default.of(locator).describe(name).resolve();
        check(calls(locator.locate(name).resolve(), method, hook) == 0, "Native input unmodified");
        byte[] bytes = weave(new ByteBuddy().redefine(target, locator), target, patch).make().getBytes();
        int actual = calls(bytes, method, hook);
        check(actual == expected, name + "." + method + ": " + hook + " calls=" + actual);
    }

    private static DynamicType.Builder<?> weave(DynamicType.Builder<?> builder, TypeDescription target,
            Class<?> patch) throws Exception {
        List<Method> candidates = Arrays.stream(Class.forName("me.zed_0xff.zombie_buddy.PatchEngine").getDeclaredMethods())
                .filter(m -> m.isSynthetic() && m.getName().startsWith("lambda$applyPatches$")
                        && m.getParameterCount() == 8).toList();
        check(candidates.size() == 1, "ZB transformer callback must be unambiguous");
        Method transformer = candidates.getFirst();
        transformer.setAccessible(true);
        String methodName = patch.getAnnotation(me.zed_0xff.zombie_buddy.Patch.class).methodName();
        return (DynamicType.Builder<?>)transformer.invoke(null,
                Map.of(methodName, List.of(patch)), target.getName(), Map.of(), builder, target,
                LegacyMultiplayerPatchTest.class.getClassLoader(), null, null);
    }

    private static int calls(byte[] bytes, String target, String hook) {
        int[] count = {0};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int flags, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (!name.equals(target)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String desc, boolean isInterface) {
                        if (owner.equals("dev/carphysicsimproved/v1/runtime/LegacyHooks") && method.equals(hook)) count[0]++;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return count[0];
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static class AuthorityFixture extends LegacyMultiplayerRuntimeTest.Car {
        public void setNetPlayerAuthorization(Object value, int id) {
            authorization = value;
            ownerId = (short)id;
            local = "Local".equals(value);
        }
    }
}
