package dev.carphysicsimproved.v1.runtime;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;
import pzmod.carphysicsimproved.v1.CarPhysicsImprovedV1Mod;
import pzmod.carphysicsimproved.v1.Patch_BaseVehicleWheelGrip;

/** Uses installed ZB's real transformer/matcher; never starts or modifies the running game. */
public final class LegacyAxleDriftPatchTest {
    private static final String WHEEL_METHOD = "updateBulletStatsWheel";
    private static final String WHEEL_DESCRIPTOR = "(I[FLorg/joml/Vector3f;FIDD)V";
    private static Instrumentation instrumentation;

    private LegacyAxleDriftPatchTest() { }

    public static void premain(String ignored, Instrumentation value) {
        instrumentation = value;
    }

    public static void main(String[] args) throws Exception {
        check(instrumentation != null, "Run this test with its test-only javaagent");
        ClassLoader loader = LegacyAxleDriftPatchTest.class.getClassLoader();
        Field instrument = Class.forName("me.zed_0xff.zombie_buddy.Loader")
                .getDeclaredField("g_instrumentation");
        instrument.setAccessible(true);
        instrument.set(null, instrumentation);

        // Transform real installed game bytecode IN MEMORY. A 'patching' log alone
        // is not proof: count the actual call inserted into the seven-argument method.
        try (ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(loader)) {
            TypeDescription vehicle = TypePool.Default.of(locator)
                    .describe("zombie.vehicles.BaseVehicle").resolve();
            byte[] original = locator.locate(vehicle.getName()).resolve();
            check(hookCalls(original) == 0, "Installed BaseVehicle must be unpatched input");
            byte[] patched = weave(new ByteBuddy().redefine(vehicle, locator), vehicle).make().getBytes();
            check(hookCalls(patched) == 1, "ZB must insert exactly one afterWheel call into installed BaseVehicle");
        }

        // Execute that same ZB advice on a fixture with the game's exact argument
        // types. This proves float[] is passed by identity, not a scalar/copy.
        Class<?> vector = Class.forName("org.joml.Vector3f", false, loader);
        Class<?>[] parameters = { int.class, float[].class, vector, float.class,
                int.class, double.class, double.class };
        DynamicType.Builder<?> fixture = new ByteBuddy().subclass(Object.class)
                .name("dev.carphysicsimproved.v1.runtime.WovenWheelFixture")
                .defineMethod(WHEEL_METHOD, void.class, Visibility.PUBLIC)
                .withParameters(parameters)
                .intercept(MethodDelegation.to(WheelWriter.class));
        Class<?> woven = weave(fixture, fixture.toTypeDescription()).make()
                .load(loader, ClassLoadingStrategy.Default.WRAPPER).getLoaded();
        Object car = woven.getConstructor().newInstance();
        FakeAccess access = new FakeAccess(woven.getMethod(WHEEL_METHOD, parameters));
        Field nativeAccess = LegacyAxleDriftHooks.class.getDeclaredField("access");
        nativeAccess.setAccessible(true);
        nativeAccess.set(null, access);
        CarPhysicsImprovedV1Mod.setEnabled(true);
        CarPhysicsImprovedV1Mod.configureRearAxleDrift(true, .45);
        check(LegacyAxleDriftHooks.status(car).contains("waiting"), "No direct hook call before fixture");
        access.refresh(car);
        check(LegacyAxleDriftHooks.status(car).equals("idle"), "Woven advice must report a native wheel call");
        float[] baseline = access.values.clone();
        Object script = new Object();
        for (int tick = 0; tick < 4; tick++) {
            check(LegacyAxleDriftHooks.request(car, script, .45, .05), "Rear-axle request must activate");
            access.refresh(car);
        }
        for (int index = 0; index < baseline.length; index++) {
            float expected = index == 14 || index == 20 ? baseline[index] * .45f : baseline[index];
            check(Math.abs(access.values[index] - expected) < 1e-6f,
                    "Only fresh rear friction may change, index=" + index);
        }
        check(LegacyAxleDriftHooks.status(car).contains("writes=8"), "Four ticks must write both rear wheels");
        access.keep = false;
        LegacyAxleDriftHooks.cleanup();
        check(Arrays.equals(access.values, baseline), "Release must restore the fresh native array");
        check(LegacyAxleDriftHooks.status(car).equals("idle"), "Release must remove the session");
        System.out.println("LegacyAxleDriftPatchTest: real ZB weaving into installed BaseVehicle, "
                + "array identity, rear-only friction and release passed (no Bullet simulation)");
    }

    private static DynamicType.Builder<?> weave(DynamicType.Builder<?> builder, TypeDescription target)
            throws Exception {
        // Test-only access to the very callback installed by ZB's applyPatches.
        // Fail if its API changes, rather than replacing it with a copied matcher.
        List<Method> candidates = Arrays.stream(Class.forName("me.zed_0xff.zombie_buddy.PatchEngine")
                        .getDeclaredMethods())
                .filter(method -> method.isSynthetic() && method.getName().startsWith("lambda$applyPatches$")
                        && method.getParameterCount() == 8).toList();
        check(candidates.size() == 1, "Installed ZB transformer callback must be unambiguous");
        Method callback = candidates.getFirst();
        callback.setAccessible(true);
        return (DynamicType.Builder<?>) callback.invoke(null,
                Map.of(WHEEL_METHOD, List.of(Patch_BaseVehicleWheelGrip.class)),
                target.getName(), Map.of(), builder, target,
                LegacyAxleDriftPatchTest.class.getClassLoader(), null, null);
    }

    private static int hookCalls(byte[] bytes) {
        int[] calls = { 0 };
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int flags, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (!name.equals(WHEEL_METHOD) || !descriptor.equals(WHEEL_DESCRIPTOR)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String desc, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC && owner.equals(
                                "dev/carphysicsimproved/v1/runtime/LegacyAxleDriftHooks")
                                && method.equals("afterWheel") && desc.equals("(Ljava/lang/Object;I[F)V")) {
                            calls[0]++;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return calls[0];
    }

    public static final class WheelWriter {
        private WheelWriter() { }
        public static void write(@Argument(0) int wheel, @Argument(1) float[] values) {
            float[] fresh = { 1f, .9f, 1.8f, 2f, 3f, .01f };
            System.arraycopy(fresh, 0, values, wheel * 6, fresh.length);
        }
    }

    private static final class FakeAccess extends LegacyAxleDriftHooks.Access {
        private final Method method;
        private final float[] values = new float[24];
        private boolean keep = true;
        FakeAccess(Method method) throws ReflectiveOperationException {
            super();
            this.method = method;
        }
        @Override boolean[] rearWheels(Object script) { return new boolean[] { false, false, true, true }; }
        @Override boolean canKeep(Object vehicle) { return keep; }
        @Override void refresh(Object vehicle) throws ReflectiveOperationException {
            for (int wheel = 0; wheel < 4; wheel++) {
                method.invoke(vehicle, wheel, values, null, 1f, 0, 2.4, 1.0);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
