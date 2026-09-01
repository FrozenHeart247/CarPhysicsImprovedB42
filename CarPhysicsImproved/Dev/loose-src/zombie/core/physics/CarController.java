package zombie.core.physics;

import org.joml.Vector3f;
import zombie.roadcraft.runtime.RoadcraftHooks;
import zombie.vehicles.BaseVehicle;

/**
 * Clean-room controller shadow for the loose {@code zombie/} installation.
 *
 * <p>This class intentionally contains no Project Zomboid implementation. It
 * preserves the small public B42.20.4 binary surface used by other game
 * classes and delegates all game-facing work to Roadcraft's public runtime
 * bridge.</p>
 */
public final class CarController {
    public final BaseVehicle vehicleObject;
    public float clientForce;
    public float engineForce;
    public float brakingForce;
    public boolean isEnable;
    public boolean acceleratorOn;
    public boolean brakeOn;
    public float speed;
    public static GearInfo[] gears = {
            new GearInfo(0, 25, 0.0f),
            new GearInfo(25, 50, 0.5f),
            new GearInfo(50, 1_000, 0.5f)
    };
    public final ClientControls clientControls = new ClientControls();

    private float vehicleSteering;
    private boolean isGas;
    private boolean isGasR;
    private boolean isBreak;
    private float atRestTimer = -1.0f;

    public CarController(BaseVehicle vehicle) {
        this.vehicleObject = vehicle;
        RoadcraftHooks.initializeDirectVehicle(vehicle);
    }

    public GearInfo findGear(float currentSpeed) {
        GearInfo[] currentGears = gears;
        if (currentGears == null) {
            return null;
        }
        for (GearInfo gear : currentGears) {
            if (gear != null && currentSpeed >= gear.minSpeed && currentSpeed < gear.maxSpeed) {
                return gear;
            }
        }
        return null;
    }

    public void accelerator(boolean active) {
        acceleratorOn = active;
    }

    public void brake(boolean active) {
        brakeOn = active;
    }

    public ClientControls getClientControls() {
        return clientControls;
    }

    public void update() {
        updateControls();
        RoadcraftHooks.DirectControllerResult result =
                RoadcraftHooks.updateDirectController(this, false);
        apply(result);
        checkShouldBeActive();
        RoadcraftHooks.dispatchDirectController(this, result);
    }

    public void updateTrailer() {
        updateControls();
        RoadcraftHooks.DirectControllerResult result =
                RoadcraftHooks.updateDirectController(this, true);
        apply(result);
        checkShouldBeActive();
        RoadcraftHooks.dispatchDirectController(this, result);
    }

    public float getVehicleSteering() {
        return vehicleSteering;
    }

    public boolean isGas() {
        return isGas;
    }

    public boolean isGasR() {
        return isGasR;
    }

    public boolean isBreak() {
        return isBreak;
    }

    public void control_NoControl() {
        clientControls.reset();
        engineForce = 0.0f;
        brakingForce = 10.0f;
        vehicleSteering = 0.0f;
        isGas = false;
        isGasR = false;
        isBreak = false;
        acceleratorOn = false;
        brakeOn = false;
    }

    public void updateControls() {
        RoadcraftHooks.DirectControls controls =
                RoadcraftHooks.readDirectControls(vehicleObject, clientControls.forceBrake);
        if (controls == null || !controls.available()) {
            return;
        }
        clientControls.steering = finite(controls.steering());
        clientControls.forward = controls.forward();
        clientControls.backward = controls.backward();
        clientControls.brake = controls.brake();
        clientControls.shift = controls.shift();
        if (controls.wasUsingParkingBrakes()) {
            clientControls.wasUsingParkingBrakes = true;
        }
        clientControls.forceBrake = controls.forceBrake();
    }

    public void park() {
        clientControls.reset();
        RoadcraftHooks.DirectControllerResult result = RoadcraftHooks.parkDirectController(this);
        apply(result);
        RoadcraftHooks.dispatchDirectController(this, result);
    }

    protected boolean shouldBeActive() {
        return RoadcraftHooks.shouldDirectControllerBeActive(this);
    }

    public void checkShouldBeActive() {
        if (!RoadcraftHooks.shouldManageDirectControllerActivity()) {
            return;
        }
        if (shouldBeActive()) {
            if (!isEnable) {
                RoadcraftHooks.setDirectControllerActive(this, true);
            }
            atRestTimer = 1.0f;
        } else if (isEnable && RoadcraftHooks.isDirectControllerAtRest(this)) {
            if (atRestTimer > 0.0f) {
                atRestTimer -= RoadcraftHooks.directControllerTimeDelta();
            }
            if (atRestTimer <= 0.0f) {
                RoadcraftHooks.setDirectControllerActive(this, false);
            }
        }
    }

    public boolean isGasPedalPressed() {
        return acceleratorOn;
    }

    public boolean isBrakePedalPressed() {
        return brakeOn;
    }

    public void debug() {
        // Intentionally empty. The clean-room controller has no debug renderer.
    }

    public void drawRect(Vector3f position, float width, float height, float red, float green) {
        // Public B42 ABI compatibility only.
    }

    public void drawRect(
            Vector3f position,
            float width,
            float height,
            float red,
            float green,
            float blue,
            float alpha,
            float lineWidth) {
        // Public B42 ABI compatibility only.
    }

    public void drawCircle(float x, float y, float radius) {
        // Public B42 ABI compatibility only.
    }

    public void drawCircle(
            float x,
            float y,
            float radius,
            float red,
            float green,
            float blue,
            float alpha) {
        // Public B42 ABI compatibility only.
    }

    private void apply(RoadcraftHooks.DirectControllerResult result) {
        if (result == null) {
            return;
        }
        engineForce = finite(result.engineForce());
        brakingForce = finite(result.brakingForce());
        vehicleSteering = finite(result.vehicleSteering());
        speed = finite(result.speed());
        isGas = result.gas();
        isGasR = result.gasReverse();
        isBreak = result.breaking();
        acceleratorOn = result.acceleratorOn();
        brakeOn = result.brakeOn();
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    public static final class ClientControls {
        public float steering;
        public boolean forward;
        public boolean backward;
        public boolean brake;
        public boolean shift;
        public boolean wasUsingParkingBrakes;
        public long forceBrake;

        public ClientControls() {
        }

        public void reset() {
            steering = 0.0f;
            forward = false;
            backward = false;
            brake = false;
            shift = false;
            wasUsingParkingBrakes = false;
            forceBrake = 0L;
        }
    }

    public static final class GearInfo {
        private final int minSpeed;
        private final int maxSpeed;
        private final float minRpm;

        private GearInfo(int minSpeed, int maxSpeed, float minRpm) {
            this.minSpeed = minSpeed;
            this.maxSpeed = maxSpeed;
            this.minRpm = minRpm;
        }
    }

    /** Retained because the B42.20.4 class exposes this nested type publicly. */
    public static final class BulletVariables {
        public BulletVariables() {
        }
    }
}
