package zombie.core.physics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import org.joml.Vector2f;
import org.joml.Vector3f;
import se.krka.kahlua.j2se.KahluaTableImpl;
import zombie.GameTime;
import zombie.GameWindow;
import zombie.SandboxOptions;
import zombie.Lua.LuaManager;
import zombie.SandboxOptions.SandboxOption;
import zombie.audio.BaseSoundEmitter;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.Translator;
import zombie.core.math.PZMath;
import zombie.core.physics.CarController.BulletVariables;
import zombie.core.physics.CarController.ClientControls;
import zombie.core.physics.CarController.EngineInfoType;
import zombie.core.physics.CarController.GearInfo;
import zombie.core.physics.CarController.TorqueConverterType;
import zombie.core.physics.CarController.TransmissionType;
import zombie.core.physics.CarController.VehicleInfoType;
import zombie.core.random.Rand;
import zombie.debug.DebugLog;
import zombie.debug.DebugOptions;
import zombie.debug.LineDrawer;
import zombie.input.GameKeyboard;
import zombie.input.JoypadManager;
import zombie.inventory.InventoryItem;
import zombie.inventory.ItemContainer;
import zombie.iso.IsoCamera;
import zombie.iso.IsoChunk;
import zombie.iso.IsoObject;
import zombie.iso.IsoUtils;
import zombie.iso.IsoWorld;
import zombie.iso.Vector2;
import zombie.iso.weather.ClimateManager;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.network.ServerOptions;
import zombie.pathfind.VehiclePoly;
import zombie.scripting.ScriptManager;
import zombie.scripting.objects.CharacterTrait;
import zombie.scripting.objects.ItemTag;
import zombie.scripting.objects.MoodleType;
import zombie.scripting.objects.ResourceLocation;
import zombie.scripting.objects.VehicleScript;
import zombie.scripting.objects.VehicleScript.Wheel;
import zombie.ui.UIManager;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.TransmissionNumber;
import zombie.vehicles.VehiclePart;
import zombie.vehicles.BaseVehicle.Authorization;
import zombie.vehicles.BaseVehicle.engineStateTypes;

public final class CarController {
   public final BaseVehicle vehicleObject;
   public float clientForce = 0.0F;
   public float engineForce = 0.0F;
   public float brakingForce = 0.0F;
   private float VehicleSteering = 0.0F;
   boolean isGas = false;
   boolean isGasR = false;
   boolean isBreak = false;
   private float atRestTimer = -1.0F;
   private float regulatorTimer = 0.0F;
   public boolean isEnable = false;
   private final Transform tempXfrm = new Transform();
   private final Vector2 tempVec2 = new Vector2();
   private final Vector3f tempVec3f = new Vector3f();
   private final Vector3f tempVec3f2 = new Vector3f();
   private final Vector3f tempVec3f3 = new Vector3f();
   private static final Vector3f _UNIT_Y = new Vector3f(0.0F, 1.0F, 0.0F);
   public boolean acceleratorOn = false;
   public boolean brakeOn = false;
   public float speed = 0.0F;
   public static GearInfo[] gears = new GearInfo[3];
   public final ClientControls clientControls = new ClientControls();
   private boolean engineStartingFromKeyboard;
   private static final BulletVariables bulletVariables = new BulletVariables();
   float drunkDelayCommandTimer = 0.0F;
   boolean wasBreaking = false;
   boolean wasGas = false;
   boolean wasGasR = false;
   boolean wasSteering = false;
   private long soundStartup = -1L;
   private long soundStartupFailed = -1L;
   private float engineFade = 0.0F;
   private float analogThrottle = 0.0F;
   private float analogBrake = 0.0F;
   private boolean useAnalogThrottle = false;
   private boolean autoReverse = true;
   private boolean manualShift = false;
   private boolean useJoystickThrottle = false;
   private int shiftDown = 0;
   private int shiftUp = 0;
   private boolean customizableSteering = true;
   private int lastModOptionUpdate = 0;
   private boolean wasLastOffroadSkidding = false;
   private float throttleOvertime = 0.0F;
   private int lastGear = 1;
   float steeringFactorLowSpeed = 1.0F;
   float steeringFactorHighSpeed = 0.1F;
   float steeringCenteringLowSpeed = 1.0F;
   float steeringCenteringHighSpeed = 0.1F;
   float steeringSnapback = 3.0F;
   float steeringHighSpeed = 75.0F;
   float tirePressure = 1.0F;
   float tireCondition = 1.0F;
   float tireTraction = 1.0F;
   public static boolean onFirstUpdate = true;
   public static HashMap<String, VehicleInfoType> vehicleInfo = new HashMap<>();
   private static HashMap<String, EngineInfoType> engineInfo = new HashMap<>();
   private static HashMap<String, TransmissionType> transmissionInfo = new HashMap<>();
   private static HashMap<String, TorqueConverterType> torqueConverterInfo = new HashMap<>();
   private static HashMap<String, Float> InitialTractionValues = new HashMap<>();
   private static String driverCarType = "";
   private static ItemTag Tag_EngineBrakeBooster = null;
   private static ItemTag Tag_EnginePowerSteeringPump = null;
   private static ItemTag Tag_EngineFanBelt = null;
   private static ItemTag Tag_EngineTransmission = null;
   private static ItemTag Tag_EngineTorqueConverter = null;
   private static ItemTag Tag_EngineFlywheel = null;
   private float burnoutAmount = 0.0F;
   float tireSpeed = 0.0F;
   private float[] rearWheelPosition = new float[]{0.0F, 0.0F};
   private float[] rearWheelPositionLast = new float[]{0.0F, 0.0F};
   public float wheelImpulseCooldown = 0.0F;
   private float idleTargetRPM = 800.0F;
   private float idleTargetRPMUpdate = 0.0F;
   private boolean WasReverseLast = false;
   public BaseSoundEmitter engineEmitter = null;
   private long newEngineSound = 0L;
   private long newExhaustSound = 0L;

   private float getSandboxOption(String var1, float var2) {
      SandboxOption var3 = SandboxOptions.instance.getOptionByName(var1);
      if (var3 == null) {
         return var2;
      } else {
         float var4 = (float)Double.parseDouble(var3.asConfigOption().getValueAsString());
         if (var4 != var4) {
            DebugLog.log("Nan sandbox value detected");
            return var2;
         } else {
            return var4;
         }
      }
   }

   private boolean getSandboxOptionBoolean(String var1, boolean var2) {
      SandboxOption var3 = SandboxOptions.instance.getOptionByName(var1);
      return var3 == null ? var2 : (Boolean)var3.asConfigOption().getValueAsObject();
   }

   public CarController(BaseVehicle var1) {
      KahluaTableImpl var2 = (KahluaTableImpl)LuaManager.env.rawget("RealisticCarPhysicsMod");
      if (var2 != null) {
         var2.rawset("javaVersion", "3.2");
      } else {
         DebugLog.log("Realistic Car Physics mod Java side installed but mod not enabled. Sandbox options and mod options will be disabled");
      }

      this.loadLUATables();
      this.vehicleObject = var1;
      this.engineStartingFromKeyboard = false;
      VehicleScript var3 = var1.getScript();
      float var4 = var1.savedPhysicsZ;
      if (Float.isNaN(var4)) {
         float var5 = 0.0F;
         if (var3.getWheelCount() > 0) {
            Vector3f var6 = var3.getModelOffset();
            var5 += var6.y();
            var5 += var3.getWheel(0).getOffset().y() - var3.getWheel(0).radius;
         }

         float var8 = var3.getCenterOfMassOffset().y() - var3.getExtents().y() / 2.0F;
         var4 = PZMath.fastfloor(var1.getZ()) * 3 * 0.8164967F - Math.min(var5, var8);
         if (var3.getWheelCount() == 0) {
            var4 = PZMath.max(var4, PZMath.fastfloor(var1.getZ()) * 3 * 0.8164967F + 0.1F);
         }

         var1.jniTransform.origin.y = var4;
      }

      if (!GameServer.server) {
         Bullet.addVehicle(
            var1.vehicleId, var1.getX(), var1.getY(), var4, var1.savedRot.x, var1.savedRot.y, var1.savedRot.z, var1.savedRot.w, var3.getFullName()
         );
         var1.setPhysicsActive(!var1.isNetPlayerAuthorization(Authorization.Remote));
      }
   }

   public GearInfo findGear(float var1) {
      for (int var2 = 0; var2 < gears.length; var2++) {
         if (var1 >= gears[var2].minSpeed && var1 < gears[var2].maxSpeed) {
            return gears[var2];
         }
      }

      return null;
   }

   public void accelerator(boolean var1) {
      this.acceleratorOn = var1;
   }

   public void brake(boolean var1) {
      this.brakeOn = var1;
   }

   public ClientControls getClientControls() {
      return this.clientControls;
   }

   private void loadLUATables() {
      if (onFirstUpdate) {
         Tag_EngineBrakeBooster = ItemTag.get(ResourceLocation.of("ProjectSummerCar:EngineBrakeBooster"));
         Tag_EnginePowerSteeringPump = ItemTag.get(ResourceLocation.of("ProjectSummerCar:EnginePowerSteeringPump"));
         Tag_EngineFanBelt = ItemTag.get(ResourceLocation.of("ProjectSummerCar:EngineFanBelt"));
         Tag_EngineTransmission = ItemTag.get(ResourceLocation.of("ProjectSummerCar:EngineTransmission"));
         Tag_EngineTorqueConverter = ItemTag.get(ResourceLocation.of("ProjectSummerCar:EngineTorqueConverter"));
         Tag_EngineFlywheel = ItemTag.get(ResourceLocation.of("ProjectSummerCar:EngineFlywheel"));
         onFirstUpdate = false;
         InitialTractionValues.clear();
         ArrayList var1 = ScriptManager.instance.getAllVehicleScripts();

         for (int var2 = 0; var2 < var1.size(); var2++) {
            VehicleScript var3 = (VehicleScript)var1.get(var2);
            InitialTractionValues.put(var3.getName(), var3.getWheelFriction());
         }

         vehicleInfo.clear();
         KahluaTableImpl var19 = (KahluaTableImpl)LuaManager.env.rawget("RCP_VehicleValues");
         if (var19 != null) {
            for (Entry var4 : var19.delegate.entrySet()) {
               String var5 = var4.getKey().toString();
               VehicleInfoType var6 = new VehicleInfoType();
               KahluaTableImpl var7 = (KahluaTableImpl)var4.getValue();

               for (Entry var9 : var7.delegate.entrySet()) {
                  String var10 = var9.getKey().toString();
                  if (var10.equals("engineSound")) {
                     var6.engineSound = var9.getValue().toString();
                  }

                  if (var10.equals("cargo")) {
                     var6.cargo = ((Double)var9.getValue()).floatValue();
                  }

                  if (var10.equals("horsePower")) {
                     var6.hasHPOverhaul = true;
                  }
               }

               vehicleInfo.put(var5, var6);
            }
         }

         engineInfo.clear();
         KahluaTableImpl var21 = (KahluaTableImpl)LuaManager.env.rawget("RCP_EngineValues");
         if (var21 != null) {
            for (Entry var25 : var21.delegate.entrySet()) {
               String var28 = var25.getKey().toString();
               EngineInfoType var31 = new EngineInfoType();
               KahluaTableImpl var34 = (KahluaTableImpl)var25.getValue();

               for (Entry var40 : var34.delegate.entrySet()) {
                  String var11 = var40.getKey().toString();
                  if (var11.equals("engineSound")) {
                     var31.engineSound = var40.getValue().toString();
                  }

                  if (var11.equals("engineSoundRPM")) {
                     var31.engineSoundRPM = ((Double)var40.getValue()).floatValue();
                  }

                  if (var11.equals("engineSoundBias")) {
                     var31.engineSoundBias = ((Double)var40.getValue()).floatValue();
                  }

                  if (var11.equals("engineSoundMultipler")) {
                     var31.engineSoundMultipler = ((Double)var40.getValue()).floatValue();
                  }

                  if (var11.equals("exhaustSound")) {
                     var31.exhaustSound = var40.getValue().toString();
                  }

                  if (var11.equals("exhaustSoundBias")) {
                     var31.exhaustSoundBias = ((Double)var40.getValue()).floatValue();
                  }

                  if (var11.equals("exhaustSoundMultipler")) {
                     var31.exhaustSoundMultipler = ((Double)var40.getValue()).floatValue();
                  }

                  if (var11.equals("crankSound")) {
                     var31.crankSound = var40.getValue().toString();
                  }

                  if (var11.equals("startSound")) {
                     var31.startSound = var40.getValue().toString();
                  }
               }

               engineInfo.put(var28, var31);
            }
         }

         transmissionInfo.clear();
         KahluaTableImpl var23 = (KahluaTableImpl)LuaManager.env.rawget("TransmissionTable");
         if (var23 != null) {
            for (Entry var29 : var23.delegate.entrySet()) {
               String var32 = var29.getKey().toString();
               TransmissionType var35 = new TransmissionType();
               String var38 = "";
               ArrayList var41 = new ArrayList();
               KahluaTableImpl var42 = (KahluaTableImpl)var29.getValue();

               for (Entry var13 : var42.delegate.entrySet()) {
                  String var14 = var13.getKey().toString();
                  if (var14.equals("name")) {
                     var38 = var13.getValue().toString();
                  }

                  if (var14.equals("ratios")) {
                     KahluaTableImpl var15 = (KahluaTableImpl)var13.getValue();

                     for (Entry var17 : var15.delegate.entrySet()) {
                        float var18 = ((Double)var17.getValue()).floatValue();
                        var41.add(var18);
                     }
                  }
               }

               var35.ratios = new float[var41.size()];

               for (int var44 = 0; var44 < var41.size(); var44++) {
                  var35.ratios[var44] = (Float)var41.get(var44);
               }

               var35.gearCount = var41.size() - 1;
               transmissionInfo.put(var38, var35);
            }

            torqueConverterInfo.clear();
            var23 = (KahluaTableImpl)LuaManager.env.rawget("TorqueConverterTable");

            for (Entry var30 : var23.delegate.entrySet()) {
               String var33 = var30.getKey().toString();
               TorqueConverterType var36 = new TorqueConverterType();
               String var39 = "";
               new ArrayList();
               KahluaTableImpl var43 = (KahluaTableImpl)var30.getValue();

               for (Entry var46 : var43.delegate.entrySet()) {
                  String var47 = var46.getKey().toString();
                  if (var47.equals("name")) {
                     var39 = var46.getValue().toString();
                  } else if (var47.equals("lockupRPM")) {
                     var36.lockupRPM = (float)((Double)var46.getValue()).doubleValue();
                  } else if (var47.equals("lockupRange")) {
                     var36.lockupRange = (float)((Double)var46.getValue()).doubleValue();
                  }
               }

               torqueConverterInfo.put(var39, var36);
            }
         }
      }
   }

   private void updateModOptions() {
      this.lastModOptionUpdate--;
      if (this.lastModOptionUpdate <= 0) {
         this.lastModOptionUpdate = 60;
         KahluaTableImpl var1 = (KahluaTableImpl)LuaManager.env.rawget("RealisticCarPhysicsMod");
         if (var1 != null) {
            this.manualShift = var1.rawgetBool("manualShift");
            this.autoReverse = var1.rawgetBool("autoReverse") || !this.manualShift;
            this.useAnalogThrottle = var1.rawgetBool("useAnalogThrottle") && this.vehicleObject.getJoypad() != -1;
            this.useJoystickThrottle = var1.rawgetBool("JoystickThrottle");
            this.customizableSteering = var1.rawgetBool("CustomizableSteering");
            this.steeringFactorLowSpeed = var1.rawgetFloat("SteeringFactorLowSpeed");
            this.steeringFactorHighSpeed = var1.rawgetFloat("SteeringFactorHighSpeed");
            this.steeringCenteringLowSpeed = var1.rawgetFloat("SteeringCenteringLowSpeed");
            this.steeringCenteringHighSpeed = var1.rawgetFloat("SteeringCenteringHighSpeed");
            this.steeringSnapback = var1.rawgetFloat("SteeringSnapback");
            this.steeringHighSpeed = var1.rawgetFloat("SteeringHighSpeed");
         }
      }
   }

   private void updateControlsCalculation(ItemContainer var1) {
      VehicleScript var2 = this.vehicleObject.getScript();
      this.speed = this.vehicleObject.getCurrentSpeedKmHour();
      boolean var3 = this.vehicleObject.getDriver() != null && this.vehicleObject.getDriver().getMoodles().getMoodleLevel(MoodleType.DRUNK) > 1;
      float var4 = 0.0F;
      Vector3f var5 = this.vehicleObject.getLinearVelocity(this.tempVec3f2);
      var5.y = 0.0F;
      if (var5.length() > 0.5) {
         var5.normalize();
         Vector3f var6 = this.tempVec3f;
         this.vehicleObject.getForwardVector(var6);
         var4 = var5.dot(var6);
      }

      float var15 = GameTime.getInstance().getRealworldSecondsSinceLastUpdate();
      float var7 = this.vehicleObject.getCurrentSpeedKmHour() * BaseVehicle.getFakeSpeedModifier();
      this.isGas = false;
      this.isGasR = false;
      this.isBreak = false;
      boolean var8 = this.autoReverse;
      if (this.clientControls.shift) {
         var8 = false;
      }

      if (var8 && this.useAnalogThrottle) {
         if (var4 >= 0.0F && this.analogThrottle - this.analogBrake > 0.0F) {
            this.isGas = true;
            this.WasReverseLast = false;
         }

         if (var4 <= 0.0F && this.analogThrottle - this.analogBrake < 0.0F) {
            this.isGasR = true;
            this.WasReverseLast = true;
         }

         if (this.WasReverseLast) {
            float var9 = this.analogThrottle;
            this.analogThrottle = this.analogBrake;
            this.analogBrake = var9;
         }
      }

      if (var8 && !this.useAnalogThrottle) {
         if (this.clientControls.backward) {
            if (var4 > 0.0F) {
               this.isBreak = true;
            }

            if (var4 <= 0.0F) {
               this.isGasR = true;
               this.WasReverseLast = true;
            }
         }

         if (this.clientControls.forward) {
            if (var4 < 0.0F) {
               this.isBreak = true;
            }

            if (var4 >= 0.0F) {
               this.isGas = true;
               this.WasReverseLast = false;
            }

            if (this.isGasR) {
               this.isGasR = false;
               this.isBreak = true;
            }
         }
      } else if (!var8 && !this.useAnalogThrottle) {
         this.WasReverseLast = false;
         if (this.clientControls.forward) {
            this.isGas = true;
         }

         if (this.clientControls.backward) {
            this.isBreak = true;
         }
      }

      if (this.clientControls.brake) {
         this.isBreak = true;
      }

      if (var3 && this.vehicleObject.engineState != engineStateTypes.Idle) {
         if (this.isBreak && !this.wasBreaking) {
            this.isBreak = this.delayCommandWhileDrunk(this.isBreak);
         }

         if (this.isGas && !this.wasGas) {
            this.isGas = this.delayCommandWhileDrunk(this.isGas);
         }

         if (this.isGasR && !this.wasGasR) {
            this.isGasR = this.delayCommandWhileDrunk(this.isGas);
         }

         if (this.clientControls.steering != 0.0F && !this.wasSteering) {
            this.clientControls.steering = this.delayCommandWhileDrunk(this.clientControls.steering);
         }
      }

      this.updateRegulator(var15);
      this.wasBreaking = this.isBreak;
      this.wasGas = this.isGas;
      this.wasGasR = this.isGasR;
      this.wasSteering = this.clientControls.steering != 0.0F;
      if (!this.isGasR && this.vehicleObject.isInvalidChunkAhead()) {
         this.isBreak = true;
         this.isGas = false;
         this.isGasR = false;
      } else if (!this.isGas && this.vehicleObject.isInvalidChunkBehind()) {
         this.isBreak = true;
         this.isGas = false;
         this.isGasR = false;
      }

      if (this.clientControls.shift) {
         this.isGas = false;
         this.isGasR = false;
         this.clientControls.wasUsingParkingBrakes = false;
         this.WasReverseLast = false;
      }

      float var16 = this.vehicleObject.throttle;
      if (this.vehicleObject.isRegulator() && !this.isGas && !this.isGasR) {
         float var10 = this.vehicleObject.getRegulatorSpeed() - var7;
         var16 = Math.min(Math.max(var10 * 0.5F, 0.0F), 1.0F);
         if (this.isBreak) {
            var16 = 0.0F;
         }
      }

      IsoGameCharacter var18 = this.vehicleObject.getDriver();
      boolean var11 = var18 != null && var18.hasTrait(CharacterTrait.SPEED_DEMON);
      boolean var12 = var18 != null && var18.hasTrait(CharacterTrait.SUNDAY_DRIVER);
      if (this.useAnalogThrottle) {
         if (!this.vehicleObject.isRegulator()) {
            var16 = this.analogThrottle;
            this.throttleOvertime = this.analogThrottle > 0.9 ? 1.0F : 0.0F;
         } else {
            this.throttleOvertime = 0.0F;
         }
      } else {
         if (!this.isGas && !this.isGasR) {
            if (var12) {
               var16 -= var15 * 6.0F;
            } else if (var11) {
               var16 -= var15 * 2.0F;
            } else {
               var16 -= var15 * 4.0F;
            }
         } else if (var12) {
            var16 += var15 * 2.0F;
         } else if (var11) {
            var16 += var15 * 6.0F;
         } else {
            var16 += var15 * 4.0F;
         }

         if (var16 >= 1.0F) {
            if (var12) {
               this.throttleOvertime += var15 * 0.3F;
            } else {
               this.throttleOvertime += var15 * 1.0F;
            }
         } else {
            this.throttleOvertime -= var15 * 1.0F;
         }
      }

      this.throttleOvertime = PZMath.clamp(this.throttleOvertime, 0.0F, 1.0F);
      var16 = PZMath.clamp(var16, 0.0F, 1.0F);
      this.engineForce = 0.0F;
      this.brakingForce = 0.0F;
      if (GameClient.client) {
         var16 = Math.min(var16, (float)(ServerOptions.instance.speedLimit.getValue() - this.vehicleObject.getCurrentSpeedKmHour() - 2.0) / 2.0F);
      }

      this.vehicleObject.throttle = var16;
      if (this.isGas || this.isGasR || this.isBreak) {
         UIManager.speedControls.SetCurrentGameSpeed(1);
      }

      if ((this.isGasR || this.isGas) && this.clientControls.wasUsingParkingBrakes) {
         this.clientControls.wasUsingParkingBrakes = false;
      }

      this.updateBackSignal();
      if (this.useAnalogThrottle) {
         this.isBreak = this.isBreak | this.analogBrake > 0.1;
      }

      if (this.isBreak) {
         this.brakingForce = this.vehicleObject.getBrakingForce();
         if (this.useAnalogThrottle && !this.clientControls.brake) {
            this.brakingForce = this.brakingForce * this.analogBrake;
         }

         if (this.clientControls.brake) {
            this.brakingForce *= 3.0F;
         }

         if (var1 != null) {
            InventoryItem var13 = var1.getFirstTag(Tag_EngineBrakeBooster);
            float var14 = 0.0F;
            if (var13 != null) {
               var14 = Math.min(1.0F, var13.getCondition() / 50.0F);
            }

            if (!this.vehicleObject.isEngineRunning()) {
               var14 = 0.0F;
            }

            this.brakingForce = (float)(this.brakingForce * Math.max(0.2, var14));
         }
      }

      this.updateBrakeLights();
      BaseVehicle var19 = this.vehicleObject.getVehicleTowedBy();
      if (var19 != null && var19.getDriver() == null && this.vehicleObject.getDriver() != null && !GameClient.client) {
         this.vehicleObject.addPointConstraint(null, var19, this.vehicleObject.getTowAttachmentSelf(), var19.getTowAttachmentSelf());
      }
   }

   private void updateSteeringVanilla(VehicleScript var1) {
      float var2 = GameTime.getInstance().getMultiplier() / 0.8F;
      if (Math.abs(this.clientControls.steering) > 0.1F) {
         float var3 = 1.0F - this.speed / this.vehicleObject.getMaxSpeed();
         if (var3 < 0.1F) {
            var3 = 0.1F;
         }

         this.VehicleSteering = this.VehicleSteering - (this.clientControls.steering + this.VehicleSteering) * 0.06F * var2 * var3;
      } else if (Math.abs(this.VehicleSteering) <= 0.04) {
         this.VehicleSteering = 0.0F;
      } else if (this.VehicleSteering > 0.0F) {
         this.VehicleSteering -= 0.04F * var2;
         this.VehicleSteering = Math.max(this.VehicleSteering, 0.0F);
      } else {
         this.VehicleSteering += 0.04F * var2;
         this.VehicleSteering = Math.min(this.VehicleSteering, 0.0F);
      }

      float var4 = var1.getSteeringClamp(this.speed);
      this.VehicleSteering = PZMath.clamp(this.VehicleSteering, -var4, var4);
   }

   private void updateTireStats() {
      VehicleScript var1 = this.vehicleObject.getScript();
      int var2 = var1.getWheelCount();
      float var3 = 0.0F;
      float var4 = 0.0F;

      for (int var5 = 0; var5 < var2; var5++) {
         Wheel var6 = var1.getWheel(var5);
         if (var6 != null) {
            VehiclePart var7 = this.vehicleObject.getPartById("Tire" + var6.getId());
            if (var7 != null && var7.getInventoryItem() != null) {
               var3 += var7.getContainerContentAmount() / var7.getContainerCapacity();
               var4 += var7.getCondition() * var7.getInventoryItem().getWheelFriction();
               this.vehicleObject.setTireInflation(var5, 1.0F);
            }
         }
      }

      if (var2 > 0) {
         var3 /= var2;
         var4 = var4 * 0.01F / var2;
      }

      this.tirePressure = var3;
      this.tireCondition = var4;
      float var9 = this.tireCondition * 0.5F + 0.5F;
      var9 *= this.getSandboxOption("RealisticCarPhysics.OverallTraction", 1.0F);
      float var12 = 1.0F;
      if (ClimateManager.getInstance().getSnowStrength() > 0.5F) {
         float var13 = 1.0F - this.getSandboxOption("RealisticCarPhysics.TractionSnow", 0.4F);
         var13 = (float)(var13 * (Math.min(Math.max(ClimateManager.getInstance().getSnowStrength(), 0.0F), 5.0F) * 0.2));
         var12 = (1.0F - var13) * var1.getOffroadEfficiency();
      }

      float var15 = 1.0F;
      if (this.vehicleObject.isDoingOffroad()) {
         float var8 = this.getSandboxOption("RealisticCarPhysics.TractionOffroad", 0.6F);
         var8 = 1.0F - var8;
         var8 *= 0.5F + this.tirePressure / 2.0F;
         var15 = (1.0F - var8) * var1.getOffroadEfficiency();
      }

      if (ClimateManager.getInstance().isRaining()) {
         var15 *= this.getSandboxOption("RealisticCarPhysics.TractionRaining", 0.7F);
      }

      var9 *= Math.min(1.0F, Math.min(var12, var15));
      this.tireTraction = var9;
   }

   private void applyFriction() {
      VehicleScript var1 = this.vehicleObject.getScript();
      Float var2 = InitialTractionValues.get(var1.getName());
      if (var2 == null) {
         DebugLog.log("Could not find initial friction value");
      } else {
         float var3 = var2 * this.tireTraction;
         var3 = Math.min(1.8F, var3);
         boolean var4 = this.getSandboxOptionBoolean("RealisticCarPhysics.TractionModification", true);
         if (this.vehicleObject.getDriver() != null) {
            driverCarType = var1.getName();
         } else if (driverCarType == var1.getName()) {
            var4 = false;
         }

         if (var4) {
            try {
               var1.Load(var1.getName(), "{ wheelFriction = " + var3 + ", }");
               var1.toBullet();
            } catch (Exception var6) {
               DebugLog.log("Error setting vehicle script wheel friction");
            }
         }
      }
   }

   private void applyDrag(float var1) {
      VehicleScript var2 = this.vehicleObject.getScript();
      this.applyFriction();
      float var3 = Math.abs(this.vehicleObject.getCurrentSpeedKmHour() * BaseVehicle.getFakeSpeedModifier());
      float var4 = var3 * var3 * 0.05F;
      if (var2.getMechanicType() == 3) {
         var4 *= this.getSandboxOption("RealisticCarPhysics.AerodynamicDragSport", 0.7F);
      } else if (var2.getMechanicType() == 2) {
         var4 *= this.getSandboxOption("RealisticCarPhysics.AerodynamicDragHeavyDuty", 1.5F);
      } else {
         var4 *= this.getSandboxOption("RealisticCarPhysics.AerodynamicDragStandard", 1.0F);
      }

      if (this.vehicleObject.isDoingOffroad()) {
         float var5 = var2.getOffroadEfficiency();
         if (this.vehicleObject.isInForest()) {
            var5 = (float)(var5 - 0.2);
         }

         float var6 = this.getSandboxOption("RealisticCarPhysics.OffroadRollingResistance", 0.2F);
         var6 += 0.01F * var3 * this.getSandboxOption("RealisticCarPhysics.OffroadRollingResistanceSpeed", 1.0F);
         var6 *= this.vehicleObject.getMass() * (1.0F + this.tirePressure * 0.6F) / var5;
         var4 += var6;
      } else {
         float var9 = this.getSandboxOption("RealisticCarPhysics.RollingResistance", 0.05F);
         var9 += 0.01F * var3 * this.getSandboxOption("RealisticCarPhysics.RollingResistanceSpeed", 0.1F);
         var9 *= this.vehicleObject.getMass() * (2.0F - this.tirePressure);
         var4 += var9;
      }

      Vector3f var12 = new Vector3f(this.vehicleObject.jniLinearVelocity);
      if (var12.lengthSquared() > 0.1) {
         var12.normalize();
      }

      var12.mul(var4 * WorldSimulation.instance.massScaler * var1 * -200.0F);
      Bullet.applyCentralForceToVehicle(this.vehicleObject.vehicleId, var12.x, var12.y, var12.z);
   }

   private void updateSteering(ItemContainer var1, VehicleScript var2, float var3) {
      float var4 = Math.min(1.0F, Math.abs(this.speed) / this.steeringHighSpeed);
      float var5 = this.steeringFactorLowSpeed + (this.steeringFactorHighSpeed - this.steeringFactorLowSpeed) * var4;
      float var6 = this.steeringCenteringLowSpeed + (this.steeringCenteringHighSpeed - this.steeringCenteringLowSpeed) * var4;
      float var7 = 1.0F;
      if (var1 != null) {
         InventoryItem var8 = var1.getFirstTag(Tag_EnginePowerSteeringPump);
         InventoryItem var9 = var1.getFirstTag(Tag_EngineFanBelt);
         float var10 = 0.0F;
         float var11 = 0.0F;
         if (var8 != null) {
            var11 = var8.getCondition();
            var11 *= Math.min(1.0F, var8.getFluidContainer().getFilledRatio() * 2.0F);
         }

         if (var9 != null) {
            var10 = var9.getCondition();
         }

         float var12 = var11 / 50.0F * Math.max(1.0F, var10 / 30.0F) * (float)this.vehicleObject.engineSpeed / 2000.0F;
         var7 = Math.min(Math.max(var12, 0.0F), 1.0F);
         if (var7 < 0.2F) {
            var7 = 0.2F;
         }

         var5 = Math.min(var5, var12 * this.steeringFactorLowSpeed);
      }

      boolean var15 = false;
      if (Math.abs(this.clientControls.steering) > 0.1F) {
         if (this.clientControls.steering < 0.0F == this.VehicleSteering < 0.0F) {
            var5 *= this.steeringSnapback;
         }

         this.VehicleSteering = this.VehicleSteering - (this.clientControls.steering + this.VehicleSteering) * 3.0F * var3 * var5;
         var15 = true;
      } else if (Math.abs(this.VehicleSteering) <= 0.04) {
         this.VehicleSteering = 0.0F;
      }

      if (!var15) {
         if (this.VehicleSteering > 0.0F) {
            this.VehicleSteering -= var6 * 4.0F * var3;
            this.VehicleSteering = Math.max(this.VehicleSteering, 0.0F);
         } else {
            this.VehicleSteering += var6 * 4.0F * var3;
            this.VehicleSteering = Math.min(this.VehicleSteering, 0.0F);
         }
      }

      float var16 = var2.getSteeringClamp(this.speed);
      this.VehicleSteering = PZMath.clamp(this.VehicleSteering, -var16, var16);
   }

   private void updateIdle(float var1, VehiclePart var2) {
      this.idleTargetRPMUpdate -= var1;
      if (this.idleTargetRPMUpdate <= 0.0F) {
         this.idleTargetRPMUpdate = 0.1F;
         if (var2 != null) {
            this.idleTargetRPM = Rand.Next(650, 700 + (200 - var2.getCondition() * 2));
         }
      }

      float var3 = (float)this.vehicleObject.engineSpeed;
      if (var3 < this.idleTargetRPM) {
         float var4 = Math.min(Math.max((this.idleTargetRPM - var3) * 0.01F, 0.0F), 0.3F);
         this.vehicleObject.throttle = Math.max(var4, this.vehicleObject.throttle);
      }
   }

   private void updateAudio(float var1, VehicleInfoType var2) {
      if (!GameServer.server) {
         boolean var3 = this.getSandboxOptionBoolean("RealisticCarPhysics.SoundOverhaulBeta", false);
         if (var2 != null && var3 && var2.engineSound != null && !var2.engineSound.isEmpty()) {
            EngineInfoType var4 = engineInfo.get(var2.engineSound);
            if (var4 != null) {
               BaseSoundEmitter var5 = this.vehicleObject.getEmitter();
               if (this.vehicleObject.engineState == engineStateTypes.Starting && !this.vehicleObject.getEmitter().isPlaying(this.soundStartupFailed)) {
                  this.soundStartupFailed = var5.playSoundImpl(var4.crankSound, (IsoObject)null);
               }

               if (this.vehicleObject.engineState != engineStateTypes.Starting && var5.isPlaying(this.soundStartupFailed)) {
                  var5.stopSound(this.soundStartupFailed);
               }

               if (this.vehicleObject.engineState == engineStateTypes.StartingSuccess && !var5.isPlaying(this.soundStartup)) {
                  this.soundStartup = var5.playSoundImpl(var4.startSound, (IsoObject)null);
                  this.engineFade = 0.0F;
               }

               float var6 = (float)this.vehicleObject.engineSpeed / var4.engineSoundRPM;
               var6 = (var6 + 0.4F) / 1.4F;
               BaseSoundEmitter var7 = this.engineEmitter;
               if (this.newEngineSound == 0L && this.vehicleObject.isEngineRunning()) {
                  if (var7 != null) {
                     var7.stopAll();
                  }

                  this.engineEmitter = IsoWorld.instance.getFreeEmitter(this.vehicleObject.getX(), this.vehicleObject.getY(), (int)this.vehicleObject.getZ());
                  var7 = this.engineEmitter;
                  this.newEngineSound = var7.playSoundImpl(var4.engineSound, (IsoObject)null);
                  if (!var4.exhaustSound.isEmpty()) {
                     this.newExhaustSound = var7.playSoundImpl(var4.exhaustSound, (IsoObject)null);
                  }
               }

               if (this.newEngineSound != 0L && !this.vehicleObject.isEngineRunning() && var7 != null) {
                  var7.stopSound(this.newEngineSound);
                  this.newEngineSound = 0L;
                  var7.stopSound(this.newExhaustSound);
                  this.newExhaustSound = 0L;
               }

               float var8 = Core.getInstance().getOptionVehicleEngineVolume() / 10.0F;
               if (this.vehicleObject.isEngineRunning()) {
                  this.engineFade = Math.min(1.0F, this.engineFade + var1 * 1.0F);
               }

               KahluaTableImpl var9 = (KahluaTableImpl)LuaManager.env.rawget("RealisticCarPhysicsMod");
               float var10 = 0.4F;
               if (var9 != null) {
                  var10 = var9.rawgetFloat("EngineOverhaulVolume");
               }

               if (this.soundStartup != 0L && var5.isPlaying(this.soundStartup)) {
                  var5.setVolume(this.soundStartup, (1.0F - this.engineFade) * var8 * var10);
               }

               if (this.soundStartupFailed != 0L && var5.isPlaying(this.soundStartupFailed)) {
                  var5.setVolume(this.soundStartupFailed, var8 * var10);
               }

               if (this.newEngineSound != 0L) {
                  this.engineEmitter.setPos(this.vehicleObject.getX(), this.vehicleObject.getY(), this.vehicleObject.getZ());
                  var7.setPitch(this.newEngineSound, var6);
                  var7.setPitch(this.newExhaustSound, var6);
                  var7.setVolume(this.newEngineSound, (this.vehicleObject.throttle * 0.2F + 0.5F) * var8 * this.engineFade * var10);
                  var7.setVolume(this.newExhaustSound, (this.vehicleObject.throttle * 0.8F + 0.2F) * var8 * this.engineFade * var10);
               }
            }
         }
      }
   }

   public void update() {
      this.loadLUATables();
      this.updateModOptions();
      float var1 = GameTime.getInstance().getRealworldSecondsSinceLastUpdate();
      float var2 = this.vehicleObject.getMass();
      VehicleInfoType var3 = vehicleInfo.get(this.vehicleObject.getScript().getFullType());
      this.updateAudio(var1, var3);
      if (this.vehicleObject.getVehicleTowedBy() == null) {
         VehiclePart var4 = this.vehicleObject.getPartById("Engine");
         ItemContainer var5 = null;
         if (var4 != null) {
            var5 = var4.getItemContainer();
         }

         KahluaTableImpl var6 = (KahluaTableImpl)LuaManager.env.rawget("RealisticCarPhysicsMod");
         VehicleScript var7 = this.vehicleObject.getScript();
         this.updateControlsCalculation(var5);
         float var8 = Math.min(120.0F, this.vehicleObject.getMaxSpeed());
         SandboxOption var9 = SandboxOptions.instance.getOptionByName("RealisticCarPhysics.SpeedOverride");
         String var10 = var7.getCarModelName();
         if (var10 == null) {
            var10 = var7.getName();
         }

         var10 = Translator.getText("IGUI_VehicleName" + var10);
         if (var9 != null) {
            String var11 = var9.asConfigOption().getValueAsString();
            String[] var12 = var11.split("/");

            for (String var16 : var12) {
               String[] var17 = var16.split(":");
               if (var17.length == 2 && var10.contains(var17[0])) {
                  float var18 = (float)Double.parseDouble(var17[1]);
                  if (!Float.isNaN(var18)) {
                     var8 = Math.min(120.0F, var18);
                  }
               }
            }
         }

         float var47 = 4500.0F;
         float var48 = 4350.0F;
         if (var7.getEngineRPMType().equals("firebird")) {
            var47 = 6000.0F;
            var48 = 5800.0F;
         }

         var8 /= var47;
         float var49 = 0.95F / var8;
         float var50 = Math.abs(this.vehicleObject.getCurrentSpeedKmHour() * BaseVehicle.getFakeSpeedModifier()) * var49;
         TransmissionType var52 = new TransmissionType();
         var52.ratios = new float[]{3.0F, 3.0F, 1.8F, 1.3F, 1.0F};
         var52.gearCount = 4;
         int var53 = var7.getGearRatioCount();
         if (var53 == 3) {
            var52.ratios = new float[]{2.6F, 2.6F, 1.6F, 1.0F};
            var52.gearCount = 3;
         } else if (var53 == 5) {
            var52.ratios = new float[]{3.2F, 3.2F, 2.0F, 1.5F, 1.15F, 0.9F};
            var52.gearCount = 5;
         }

         InventoryItem var54 = null;
         InventoryItem var55 = null;
         if (var5 != null) {
            var54 = var5.getFirstTag(Tag_EngineTransmission);
            var55 = var5.getFirstTag(Tag_EngineTorqueConverter);
         }

         if (var54 != null) {
            String var19 = var54.getFullType();
            var19 = var19.substring(0, var19.length() - 2);
            TransmissionType var20 = transmissionInfo.get(var19);
            if (var20 != null) {
               var52 = var20;
            }
         }

         TorqueConverterType var57 = new TorqueConverterType();
         var57.lockupRPM = 2000.0F;
         var57.lockupRange = 800.0F;
         if (var55 != null) {
            String var58 = var55.getFullType();
            var58 = var58.substring(0, var58.length() - 2);
            TorqueConverterType var21 = torqueConverterInfo.get(var58);
            if (var21 != null) {
               var57 = var21;
            }
         }

         TransmissionNumber[] var60 = new TransmissionNumber[]{
            TransmissionNumber.R,
            TransmissionNumber.Speed1,
            TransmissionNumber.Speed2,
            TransmissionNumber.Speed3,
            TransmissionNumber.Speed4,
            TransmissionNumber.Speed5,
            TransmissionNumber.Speed6,
            TransmissionNumber.Speed7,
            TransmissionNumber.Speed8
         };

         int var61;
         for (var61 = 1; var61 < var52.gearCount; var61++) {
            float var22 = this.lastGear > var61 ? 500.0F : 0.0F;
            if (var50 * var52.ratios[var61] < var48 - var22) {
               break;
            }
         }

         this.lastGear = var61;
         if (var61 >= 1 && var61 < var52.gearCount && var50 * var52.ratios[var61 + 1] > var48 * 0.5 && this.throttleOvertime < 0.5F) {
            var61++;
         }

         if (this.manualShift && var6 != null) {
            var61 = var6.rawgetInt("forceGear");
            if (this.shiftDown == 1) {
               this.shiftDown = 2;
               var61--;
            }

            if (this.shiftUp == 1) {
               this.shiftUp = 2;
               var61++;
            }

            if (this.autoReverse) {
               if (var61 < 1) {
                  var61 = 1;
               }
            } else if (var61 < 0) {
               var61 = 0;
            }

            if (var61 > var52.gearCount) {
               var61 = var52.gearCount;
            }

            var6.rawset("forceGear", Integer.valueOf(var61).doubleValue());
            if (this.WasReverseLast) {
               var6.rawset("forceGear", Integer.valueOf(1).doubleValue());
            }
         }

         if (this.WasReverseLast) {
            var61 = 0;
         }

         var50 *= var52.ratios[var61];
         this.vehicleObject.transmissionNumber = var60[var61];
         float var62 = this.vehicleObject.getEnginePower() / 10.0F;
         boolean var23 = this.getSandboxOptionBoolean("RealisticCarPhysics.HPWeightOverhaulBeta", false);
         if (var3 != null && var3.hasHPOverhaul && var23) {
            var62 *= 4.0F;
         }

         float var24 = (float)this.vehicleObject.engineSpeed;
         float var25 = 0.0F;
         float var26 = 1.0F;
         if (var7.getMechanicType() == 3) {
            var26 = this.getSandboxOption("RealisticCarPhysics.TorqueModifierSport", 1.0F);
         } else if (var7.getMechanicType() == 2) {
            var26 = this.getSandboxOption("RealisticCarPhysics.TorqueModifierHeavyDuty", 1.0F);
         } else {
            var26 = this.getSandboxOption("RealisticCarPhysics.TorqueModifierStandard", 1.0F);
         }

         SandboxOption var27 = SandboxOptions.instance.getOptionByName("RealisticCarPhysics.TorqueModifierIndivual");
         if (var27 != null) {
            String var28 = var27.asConfigOption().getValueAsString();
            String[] var29 = var28.split("/");

            for (String var33 : var29) {
               String[] var34 = var33.split(":");
               if (var34.length == 2 && var10.contains(var34[0])) {
                  float var35 = (float)Double.parseDouble(var34[1]);
                  if (!Float.isNaN(var35)) {
                     var26 *= var35;
                  }
               }
            }
         }

         if (var26 > 5.0F) {
            var26 = 5.0F;
         }

         float var66 = var62 * var26 * 4500.0F / var47;
         float var67 = var66 * 1.0E-4F;
         if (var5 != null) {
            InventoryItem var69 = var5.getFirstTag(Tag_EngineFlywheel);
            if (var69 != null) {
               float var76 = var69.getScriptItem().getMaxItemSize();
               if (var76 > 0.0F) {
                  var67 /= var76;
               }
            }
         }

         var67 = Math.max(0.001F, var67);
         if (this.vehicleObject.isEngineRunning()) {
            if (var24 < 400.0F) {
               var24 = 400.0F;
               this.vehicleObject.engineSpeed = var24;
            }

            this.updateIdle(var1, var4);
            float var70 = this.vehicleObject.getCurrentSpeedKmHour() * BaseVehicle.getFakeSpeedModifier();
            float var77 = this.getSandboxOption("RealisticCarPhysics.ReverseSpeedMax", 40.0F);
            if (this.WasReverseLast && var70 < -var77) {
               this.vehicleObject.throttle = Math.min(this.vehicleObject.throttle, (var70 + var77 + 5.0F) / 5.0F);
            }

            float var80 = Math.min(Math.max(1.0F - (var24 - var47) / 1000.0F, 0.0F), 1.0F);
            var80 = (float)(var80 * Math.min(Math.max(var24 / var47 * 2.0F, 0.2), 1.0));
            var25 = this.vehicleObject.throttle * var80 * var66;
            var25 = (float)(var25 - var66 * 0.35F * this.vehicleObject.engineSpeed / var47);
         }

         float var71 = Math.max(0.0F, Math.min(3.0F, (var24 - (var57.lockupRPM - var57.lockupRange)) / var57.lockupRange));
         float var78 = this.tireSpeed * var52.ratios[var61] * var49;
         if (var61 == 0) {
            var78 = -var78;
         }

         var78 = Math.max(0.0F, var78);
         float var82 = var78 / Math.max(var24, 1.0F);
         var82 = Math.min(var82, 1.0F);
         var71 -= var82;
         var71 *= Math.max(0.0F, Math.min(1.0F, (1.0F - var82) * 5.0F));
         float var84 = 1.2F;
         if (var5 != null) {
            var84 = 0.0F;
            if (var54 != null) {
               var84 = Math.min(1.2F, var54.getCondition() / 50.0F);
               var84 = (float)(var84 * Math.min(1.0, var54.getFluidContainer().getFilledRatio() * 2.0));
            }

            if (var55 != null) {
               var84 *= Math.min(1.0F, var55.getCondition() / 50.0F);
            } else {
               var84 = 0.0F;
            }
         }

         var71 *= var84;
         var71 = Math.min(Math.max(var71, 0.0F), 1.2F);
         float var87 = 1.1F;
         float var88 = Math.min(Math.max(var82 * var87, 0.0F), 1.0F);
         var88 = 1.0F - var88;
         float var36 = this.getSandboxOption("RealisticCarPhysics.TorqueMultiplierLimit", 2.0F);
         var88 = var88 * (var36 - 1.0F) + 1.0F;
         float var37 = var66 * var71 * var88 * var52.ratios[var61] * var49 * 0.05F;
         float var38 = this.vehicleObject.getMass() * 2.0F * this.tireTraction * this.getSandboxOption("RealisticCarPhysics.AccelerationTraction", 1.0F);
         float var39 = 1.0F;
         float var40 = 50.0F;
         var37 -= this.brakingForce * var40;
         this.burnoutAmount = 0.0F;
         float var41 = this.vehicleObject.getCurrentSpeedKmHour() * BaseVehicle.getFakeSpeedModifier();
         if (var61 == 0) {
            this.tireSpeed -= (var37 - var38) * var1 * 0.02F;
            this.tireSpeed = Math.min(this.tireSpeed, var41);
            this.burnoutAmount = Math.max(var41 - this.tireSpeed - 3.0F, 0.0F);
         } else {
            this.tireSpeed += (var37 - var38) * var1 * 0.02F;
            this.tireSpeed = Math.max(this.tireSpeed, var41);
            this.burnoutAmount = Math.max(this.tireSpeed - var41 - 3.0F, 0.0F);
         }

         if (var37 > var38) {
            var39 = var38 / Math.max(1.0F, var37);
            var37 = var38;
         }

         if (var61 == 0) {
            this.burnoutAmount = -this.burnoutAmount;
         }

         if (GameClient.client && this.vehicleObject.getCurrentSpeedKmHour() >= ServerOptions.instance.speedLimit.getValue()) {
            var37 = 0.0F;
         }

         this.updateTireStats();
         this.applyDrag(var1);
         var25 -= var66 * var71 * var39;
         if (var37 < 0.0F) {
            this.brakingForce = -var37 / var40;
            var37 = 0.0F;
         }

         this.engineForce = var37;
         this.vehicleObject.engineSpeed += var25 / var67 * var1;
         this.vehicleObject.engineSpeed = Math.max(200.0, this.vehicleObject.engineSpeed);
         if (this.vehicleObject.engineSpeed != this.vehicleObject.engineSpeed) {
            this.vehicleObject.engineSpeed = 200.0;
         }

         if (this.engineForce != this.engineForce) {
            this.engineForce = 0.0F;
         }

         if (var61 == 0) {
            this.engineForce = -this.engineForce;
         }

         this.updateSkidding(false);
         if (this.customizableSteering) {
            this.updateSteering(var5, var7, var1);
         } else {
            this.updateSteeringVanilla(var7);
         }

         BulletVariables var42 = bulletVariables.set(this.vehicleObject, this.engineForce, this.brakingForce, this.VehicleSteering);
         this.checkTire(var42);
         this.engineForce = var42.engineForce;
         this.brakingForce = var42.brakingForce;
         this.VehicleSteering = var42.vehicleSteering;
         this.vehicleObject.setCurrentSteering(this.VehicleSteering);
         this.vehicleObject.setBraking(this.isBreak);
         if (!GameServer.server) {
            this.checkShouldBeActive();
            Bullet.controlVehicle(this.vehicleObject.vehicleId, this.engineForce * WorldSimulation.instance.massScaler, this.brakingForce, this.VehicleSteering);
            boolean var43 = this.getSandboxOptionBoolean("RealisticCarPhysics.AutoStart", true);
            if ((this.isGas || this.isGasR) && this.vehicleObject.engineState == engineStateTypes.Idle && !this.engineStartingFromKeyboard && var43) {
               this.engineStartingFromKeyboard = true;
               if (GameClient.client) {
                  Boolean var44 = this.vehicleObject.getDriver().getInventory().haveThisKeyId(this.vehicleObject.getKeyId()) != null
                     ? Boolean.TRUE
                     : Boolean.FALSE;
                  GameClient.instance.sendClientCommandV((IsoPlayer)this.vehicleObject.getDriver(), "vehicle", "startEngine", new Object[]{"haveKey", var44});
               } else if (!GameClient.client && !GameServer.server) {
                  Boolean var92 = this.vehicleObject.getDriver().getInventory().haveThisKeyId(this.vehicleObject.getKeyId()) != null
                     ? Boolean.TRUE
                     : Boolean.FALSE;
                  this.vehicleObject.tryStartEngine(var92);
               } else {
                  this.vehicleObject.tryStartEngine();
               }
            }

            if (this.engineStartingFromKeyboard && !this.isGas && !this.isGasR) {
               this.engineStartingFromKeyboard = false;
            }
         }

         if (this.vehicleObject.engineState != engineStateTypes.Running) {
            this.acceleratorOn = false;
            if (!GameServer.server && this.vehicleObject.getCurrentSpeedKmHour() > 5.0F && var7.getWheelCount() > 0) {
               Bullet.controlVehicle(this.vehicleObject.vehicleId, 0.0F, this.brakingForce, this.VehicleSteering);
            } else {
               this.park();
            }
         }
      }
   }

   public void updateTrailer() {
      this.loadLUATables();
      BaseVehicle var1 = this.vehicleObject.getVehicleTowedBy();
      if (var1 != null) {
         if (GameServer.server) {
            if (var1.getDriver() == null && this.vehicleObject.getDriver() != null) {
               this.vehicleObject.addPointConstraint(null, var1, this.vehicleObject.getTowAttachmentSelf(), var1.getTowAttachmentSelf());
            }
         } else {
            this.speed = this.vehicleObject.getCurrentSpeedKmHour();
            this.isGas = false;
            this.isGasR = false;
            this.isBreak = false;
            this.wasGas = false;
            this.wasGasR = false;
            this.wasBreaking = false;
            this.vehicleObject.throttle = 0.0F;
            if (var1.getDriver() == null && this.vehicleObject.getDriver() != null && !GameClient.client) {
               this.vehicleObject.addPointConstraint(null, var1, this.vehicleObject.getTowAttachmentSelf(), var1.getTowAttachmentSelf());
            } else {
               this.checkShouldBeActive();
               this.engineForce = 0.0F;
               this.brakingForce = 0.0F;
               this.VehicleSteering = 0.0F;
               boolean var2 = this.vehicleObject.getScriptName().contains("Trailer");
               float var3 = GameTime.getInstance().getRealworldSecondsSinceLastUpdate();
               this.updateTireStats();
               this.applyDrag(var3);
               if (!var2) {
                  this.VehicleSteering = 0.0F;
                  Vector2f var4 = new Vector2f();
                  var4.x = var1.getX() - this.vehicleObject.getX();
                  var4.y = var1.getY() - this.vehicleObject.getY();
                  Vector3f var5 = new Vector3f();
                  this.vehicleObject.getForwardVector(var5);
                  float var6 = (float)Math.atan2(var4.x, var4.y) - (float)Math.atan2(var5.x, var5.z);
                  if (var6 > Math.PI) {
                     var6 = (float)(var6 - (Math.PI * 2));
                  }

                  if (var6 < -Math.PI) {
                     var6 = (float)(var6 + (Math.PI * 2));
                  }

                  VehicleScript var7 = this.vehicleObject.getScript();
                  float var8 = var7.getSteeringClamp(this.speed);
                  this.VehicleSteering = PZMath.clamp(var6 * 0.3F, -var8, var8);
                  if (this.vehicleObject.getCurrentSpeedKmHour() < 1.0F) {
                     this.VehicleSteering = 0.0F;
                  }

                  boolean var9 = this.getSandboxOptionBoolean("RealisticCarPhysics.EasyTow", true);
                  if (!(this.vehicleObject.isKeysInIgnition() | this.vehicleObject.isHotwired() | var9)) {
                     this.brakingForce = 500.0F;
                  }

                  this.updateSkidding(true);
               }

               Bullet.controlVehicle(this.vehicleObject.vehicleId, this.engineForce, this.brakingForce, this.VehicleSteering);
            }
         }
      }
   }

   private void updateRegulator(float var1) {
      if (this.regulatorTimer > 0.0F) {
         this.regulatorTimer -= var1;
      }

      if (this.clientControls.shift) {
         if (this.clientControls.forward && this.regulatorTimer <= 0.0F) {
            if (this.vehicleObject.getRegulatorSpeed() < this.vehicleObject.getMaxSpeed() + 20.0F
               && (!this.vehicleObject.isRegulator() && this.vehicleObject.getRegulatorSpeed() == 0.0F || this.vehicleObject.isRegulator())) {
               if (this.vehicleObject.getRegulatorSpeed() == 0.0F && this.vehicleObject.getCurrentSpeedForRegulator() != this.vehicleObject.getRegulatorSpeed()
                  )
                {
                  this.vehicleObject.setRegulatorSpeed(this.vehicleObject.getCurrentSpeedForRegulator());
               } else {
                  this.vehicleObject.setRegulatorSpeed(this.vehicleObject.getRegulatorSpeed() + 1.0F);
               }
            }

            if (!this.vehicleObject.isRegulator()) {
               this.vehicleObject.setRegulatorSpeed((float)Math.floor(this.vehicleObject.getCurrentSpeedKmHour() * BaseVehicle.getFakeSpeedModifier()));
            }

            this.vehicleObject.setRegulator(true);
            if (this.vehicleObject.getRegulatorSpeed() <= 0.0F) {
               this.vehicleObject.setRegulatorSpeed(0.0F);
               this.vehicleObject.setRegulator(false);
            }

            this.regulatorTimer += 0.1F;
         } else if (this.clientControls.backward) {
            this.regulatorTimer = 0.0F;
            this.vehicleObject.setRegulatorSpeed((float)Math.ceil(this.vehicleObject.getCurrentSpeedKmHour() * BaseVehicle.getFakeSpeedModifier()));
            this.vehicleObject.setRegulator(true);
            if (this.vehicleObject.getRegulatorSpeed() <= 0.0F) {
               this.vehicleObject.setRegulatorSpeed(0.0F);
               this.vehicleObject.setRegulator(false);
            }
         }
      } else if (this.isGasR || this.isBreak) {
         this.vehicleObject.setRegulator(false);
      }
   }

   private void updateSkidding(boolean var1) {
      boolean var2 = this.getSandboxOptionBoolean("RealisticCarPhysics.TowedSkid", false);
      float var3 = 0.5F;
      KahluaTableImpl var4 = (KahluaTableImpl)LuaManager.env.rawget("RealisticCarPhysicsMod");
      if (var4 != null) {
         var2 = var4.rawgetBool("TowSkidding");
         var3 = var4.rawgetFloat("skidVolume");
      }

      var3 *= Core.getInstance().getOptionSoundVolume() / 10.0F;
      if ((!var1 || var2) && this.vehicleObject.getScript().getMechanicType() != 4) {
         float var5 = GameTime.getInstance().getRealworldSecondsSinceLastUpdate();
         float var6 = 0.0F;

         for (int var7 = 2; var7 < 4; var7++) {
            var6 = (float)(var6 + Math.min(Math.max(1.0F - this.vehicleObject.wheelInfo[var7].skidInfo - 0.3, 0.0), 0.5));
         }

         var6 += Math.abs(this.burnoutAmount * 0.1F);
         var6 = Math.max(Math.min(var6, 1.0F), 0.0F);
         float[] var14 = new float[]{
            this.vehicleObject.wheelInfo[2].rotation - this.rearWheelPositionLast[0], this.vehicleObject.wheelInfo[3].rotation - this.rearWheelPositionLast[1]
         };
         this.rearWheelPositionLast[0] = this.vehicleObject.wheelInfo[2].rotation;
         this.rearWheelPositionLast[1] = this.vehicleObject.wheelInfo[3].rotation;
         boolean var8 = this.clientControls.brake;
         BaseVehicle var9 = this.vehicleObject.getVehicleTowedBy();
         if (var9 != null) {
            var8 = this.brakingForce > 1.0F;
         }

         if (var6 > 0.2 && var8) {
            var14[0] = 0.0F;
            var14[1] = 0.0F;
         } else {
            var14[0] += this.burnoutAmount * var5 * 1.0F;
            var14[1] += this.burnoutAmount * var5 * 1.0F;
         }

         this.rearWheelPosition[0] = this.rearWheelPosition[0] + var14[0];
         this.rearWheelPosition[1] = this.rearWheelPosition[1] + var14[1];

         for (int var10 = 2; var10 < 4; var10++) {
            this.vehicleObject.wheelInfo[var10].rotation = this.rearWheelPosition[var10 - 2];
            if (this.rearWheelPosition[var10 - 2] > 1000.0F) {
               this.rearWheelPosition[var10 - 2] = 0.0F;
            }

            if (this.rearWheelPosition[var10 - 2] < -1000.0F) {
               this.rearWheelPosition[var10 - 2] = 0.0F;
            }
         }

         if (this.vehicleObject.isEngineRunning() || var9 != null && var6 > 0.2) {
            boolean var15 = this.vehicleObject.isDoingOffroad();
            var15 |= ClimateManager.getInstance().getSnowStrength() > 0.5F;
            if (this.wasLastOffroadSkidding != var15) {
               this.wasLastOffroadSkidding = var15;
               if (this.vehicleObject.ramSound != 0L) {
                  this.vehicleObject.stopSound(this.vehicleObject.ramSound);
                  this.vehicleObject.ramSound = 0L;
               }
            }

            if (this.vehicleObject.ramSound == 0L) {
               if (var15) {
                  this.vehicleObject.ramSound = this.vehicleObject.playSoundImpl("CarSkiddingLoudOffroad", (IsoObject)null);
               } else {
                  this.vehicleObject.ramSound = this.vehicleObject.playSoundImpl("CarSkiddingLoud", (IsoObject)null);
               }
            }

            if (var1) {
               var3 = (float)(var3 * 0.8);
            }

            this.vehicleObject.getEmitter().setVolume(this.vehicleObject.ramSound, Math.min(1.0F, var6) * var3);
         } else if (this.vehicleObject.ramSound != 0L) {
            this.vehicleObject.stopSound(this.vehicleObject.ramSound);
            this.vehicleObject.ramSound = 0L;
         }
      } else {
         if (this.vehicleObject.ramSound != 0L) {
            this.vehicleObject.stopSound(this.vehicleObject.ramSound);
            this.vehicleObject.ramSound = 0L;
         }
      }
   }

   private void updateBackSignal() {
      if (this.isGasR && this.vehicleObject.isEngineRunning() && this.vehicleObject.hasBackSignal() && !this.vehicleObject.isBackSignalEmitting()) {
         if (GameClient.client) {
            GameClient.instance.sendClientCommandV((IsoPlayer)this.vehicleObject.getDriver(), "vehicle", "onBackSignal", new Object[]{"state", "start"});
         } else {
            this.vehicleObject.onBackMoveSignalStart();
         }
      }

      if (!this.isGasR && this.vehicleObject.isBackSignalEmitting()) {
         if (GameClient.client) {
            GameClient.instance.sendClientCommandV((IsoPlayer)this.vehicleObject.getDriver(), "vehicle", "onBackSignal", new Object[]{"state", "stop"});
         } else {
            this.vehicleObject.onBackMoveSignalStop();
         }
      }
   }

   private void updateBrakeLights() {
      if (this.isBreak) {
         if (this.vehicleObject.getStoplightsOn()) {
            return;
         }

         if (GameClient.client) {
            GameClient.instance.sendClientCommandV((IsoPlayer)this.vehicleObject.getDriver(), "vehicle", "setStoplightsOn", new Object[]{"on", Boolean.TRUE});
         }

         if (!GameServer.server) {
            this.vehicleObject.setStoplightsOn(true);
         }
      } else {
         if (!this.vehicleObject.getStoplightsOn()) {
            return;
         }

         if (GameClient.client) {
            GameClient.instance.sendClientCommandV((IsoPlayer)this.vehicleObject.getDriver(), "vehicle", "setStoplightsOn", new Object[]{"on", Boolean.FALSE});
         }

         if (!GameServer.server) {
            this.vehicleObject.setStoplightsOn(false);
         }
      }
   }

   private boolean delayCommandWhileDrunk(boolean var1) {
      this.drunkDelayCommandTimer = this.drunkDelayCommandTimer + GameTime.getInstance().getMultiplier();
      if (Rand.AdjustForFramerate(4 * this.vehicleObject.getDriver().getMoodles().getMoodleLevel(MoodleType.DRUNK)) < this.drunkDelayCommandTimer) {
         this.drunkDelayCommandTimer = 0.0F;
         return true;
      } else {
         return false;
      }
   }

   private float delayCommandWhileDrunk(float var1) {
      this.drunkDelayCommandTimer = this.drunkDelayCommandTimer + GameTime.getInstance().getMultiplier();
      if (Rand.AdjustForFramerate(4 * this.vehicleObject.getDriver().getMoodles().getMoodleLevel(MoodleType.DRUNK)) < this.drunkDelayCommandTimer) {
         this.drunkDelayCommandTimer = 0.0F;
         return var1;
      } else {
         return 0.0F;
      }
   }

   private void checkTire(BulletVariables var1) {
      if (this.vehicleObject.getPartById("TireFrontLeft") == null || this.vehicleObject.getPartById("TireFrontLeft").getInventoryItem() == null) {
         var1.brakingForce = (float)(var1.brakingForce / 1.2);
         var1.engineForce = (float)(var1.engineForce / 1.2);
      }

      if (this.vehicleObject.getPartById("TireFrontRight") == null || this.vehicleObject.getPartById("TireFrontRight").getInventoryItem() == null) {
         var1.brakingForce = (float)(var1.brakingForce / 1.2);
         var1.engineForce = (float)(var1.engineForce / 1.2);
      }

      if (this.vehicleObject.getPartById("TireRearLeft") == null || this.vehicleObject.getPartById("TireRearLeft").getInventoryItem() == null) {
         var1.brakingForce = (float)(var1.brakingForce / 1.3);
         var1.engineForce = (float)(var1.engineForce / 1.3);
      }

      if (this.vehicleObject.getPartById("TireRearRight") == null || this.vehicleObject.getPartById("TireRearRight").getInventoryItem() == null) {
         var1.brakingForce = (float)(var1.brakingForce / 1.3);
         var1.engineForce = (float)(var1.engineForce / 1.3);
      }
   }

   public void updateControls() {
      if (!GameServer.server) {
         if (this.vehicleObject.isKeyboardControlled()) {
            boolean var1 = GameKeyboard.isKeyDown("Left");
            boolean var2 = GameKeyboard.isKeyDown("Right");
            boolean var3 = GameKeyboard.isKeyDown("Forward");
            boolean var4 = GameKeyboard.isKeyDown("Backward");
            boolean var5 = GameKeyboard.isKeyDown("Brake");
            boolean var6 = GameKeyboard.isKeyDown("CruiseControl");
            this.clientControls.steering = 0.0F;
            if (var1) {
               this.clientControls.steering--;
            }

            if (var2) {
               this.clientControls.steering++;
            }

            this.clientControls.forward = var3;
            this.clientControls.backward = var4;
            this.clientControls.brake = var5;
            this.clientControls.shift = var6;
            if (this.clientControls.brake) {
               this.clientControls.wasUsingParkingBrakes = true;
            }
         }

         this.analogThrottle = 0.0F;
         this.analogBrake = 0.0F;
         int var10 = this.vehicleObject.getJoypad();
         if (var10 != -1) {
            boolean var11 = JoypadManager.instance.isLeftPressed(var10);
            boolean var13 = JoypadManager.instance.isRightPressed(var10);
            boolean var14 = JoypadManager.instance.isRTPressed(var10);
            boolean var15 = JoypadManager.instance.isLTPressed(var10);
            boolean var16 = JoypadManager.instance.isBPressed(var10);
            float var7 = JoypadManager.instance.getMovementAxisX(var10);
            this.clientControls.steering = var7;
            this.clientControls.forward = var14;
            this.clientControls.backward = var15;
            this.clientControls.brake = var16;
            if (this.shiftDown == 0) {
               this.shiftDown = JoypadManager.instance.isLBPressed(var10) ? 1 : 0;
            } else if (this.shiftDown == 2) {
               this.shiftDown = JoypadManager.instance.isLBPressed(var10) ? 2 : 0;
            }

            if (this.shiftUp == 0) {
               this.shiftUp = JoypadManager.instance.isRBPressed(var10) ? 1 : 0;
            } else if (this.shiftUp == 2) {
               this.shiftUp = JoypadManager.instance.isRBPressed(var10) ? 2 : 0;
            }

            if (this.useJoystickThrottle) {
               if (JoypadManager.instance.getMovementAxisY(var10) < 0.0F) {
                  this.analogThrottle = -JoypadManager.instance.getMovementAxisY(var10);
               } else {
                  this.analogBrake = JoypadManager.instance.getMovementAxisY(var10);
               }
            } else {
               byte var8 = 5;
               if (GameWindow.GameInput.getAxisCount(var10) > var8) {
                  this.analogThrottle = GameWindow.GameInput.getAxisValue(var10, var8) / 2.0F + 0.5F;
               }

               byte var9 = 4;
               if (GameWindow.GameInput.getAxisCount(var10) > var9) {
                  this.analogBrake = GameWindow.GameInput.getAxisValue(var10, var9) / 2.0F + 0.5F;
               }
            }
         }

         if (this.clientControls.forceBrake != 0L) {
            long var12 = System.currentTimeMillis() - this.clientControls.forceBrake;
            if (var12 > 0L && var12 < 1000L) {
               this.clientControls.brake = true;
               this.clientControls.shift = false;
            }
         }
      }
   }

   public void park() {
      if (this.vehicleObject.isEngineRunning()) {
         float var1 = GameTime.getInstance().getRealworldSecondsSinceLastUpdate();
         VehiclePart var2 = this.vehicleObject.getPartById("Engine");
         this.vehicleObject.throttle = 0.0F;
         this.updateIdle(var1, var2);
         this.vehicleObject.engineSpeed = this.vehicleObject.engineSpeed + (this.vehicleObject.throttle - 0.1F) * var1 * 5000.0F;
      }

      VehicleInfoType var3 = vehicleInfo.get(this.vehicleObject.getScript().getFullType());
      float var4 = GameTime.getInstance().getRealworldSecondsSinceLastUpdate();
      this.updateAudio(var4, var3);
      if (this.vehicleObject.ramSound != 0L) {
         this.vehicleObject.stopSound(this.vehicleObject.ramSound);
         this.vehicleObject.ramSound = 0L;
      }

      if (!GameServer.server && this.vehicleObject.getScript().getWheelCount() > 0) {
         Bullet.controlVehicle(this.vehicleObject.vehicleId, 0.0F, Math.max(2.5F, this.vehicleObject.getBrakingForce()), 0.0F);
      }

      this.isGas = this.wasGas = false;
      this.isGasR = this.wasGasR = false;
      this.clientControls.reset();
      this.vehicleObject.transmissionNumber = TransmissionNumber.N;
      if (this.vehicleObject.getVehicleTowing() != null) {
         this.vehicleObject.getVehicleTowing().getController().park();
      }
   }

   protected boolean shouldBeActive() {
      if (this.vehicleObject.physicActiveCheck != -1L) {
         return true;
      } else if (!this.vehicleObject.isAtRest()) {
         return true;
      } else if (this.isPlayerDrivenVehicleNearby()) {
         return true;
      } else {
         BaseVehicle var1 = this.vehicleObject.getVehicleTowedBy();
         if (var1 == null) {
            float var2 = this.vehicleObject.isEngineRunning() ? this.engineForce : 0.0F;
            return Math.abs(var2) > 0.01F;
         } else {
            return var1.getController() == null ? false : var1.getController().shouldBeActive();
         }
      }
   }

   public void checkShouldBeActive() {
      if (this.shouldBeActive()) {
         if (!this.isEnable) {
            this.vehicleObject.setPhysicsActive(true);
         }

         this.atRestTimer = 1.0F;
      } else if (this.isEnable && this.vehicleObject.isAtRest()) {
         if (this.atRestTimer > 0.0F) {
            this.atRestTimer = this.atRestTimer - GameTime.getInstance().getTimeDelta();
         }

         if (this.atRestTimer <= 0.0F) {
            this.vehicleObject.setPhysicsActive(false);
         }
      }
   }

   public float getVehicleSteering() {
      return this.VehicleSteering;
   }

   public boolean isGas() {
      return this.isGas;
   }

   public boolean isGasR() {
      return this.isGasR;
   }

   public boolean isBreak() {
      return this.isBreak;
   }

   public boolean isGasPedalPressed() {
      return this.isGas || this.isGasR;
   }

   public boolean isBrakePedalPressed() {
      return this.isBreak;
   }

   private BaseVehicle getPlayerDrivenVehicleNearby() {
      int var1 = PZMath.coorddivision(this.vehicleObject.getXi(), 8);
      int var2 = PZMath.coorddivision(this.vehicleObject.getYi(), 8);
      Vector2f var3 = BaseVehicle.allocVector2f();
      Vector2f var4 = BaseVehicle.allocVector2f();
      BaseVehicle var5 = null;
      float var6 = Float.MAX_VALUE;

      for (int var7 = -1; var7 <= 1; var7++) {
         for (int var8 = -1; var8 <= 1; var8++) {
            IsoChunk var9 = IsoWorld.instance.currentCell.getChunk(var1 + var8, var2 + var7);
            if (var9 != null) {
               for (BaseVehicle var11 : var9.vehicles) {
                  if (var11 != this.vehicleObject && var11.getDriver() != null) {
                     float var12 = this.vehicleObject.getClosestPointOnPoly(var11, var3, var4);
                     if (var12 < 9.0F && var12 < var6) {
                        var5 = var11;
                        var6 = var12;
                     }
                  }
               }
            }
         }
      }

      BaseVehicle.releaseVector2f(var3);
      BaseVehicle.releaseVector2f(var4);
      return var5;
   }

   private boolean isPlayerDrivenVehicleNearby() {
      return this.getPlayerDrivenVehicleNearby() != null;
   }

   public void debug() {
      if (Core.debug && DebugOptions.instance.vehicleRenderOutline.getValue()) {
         VehicleScript var1 = this.vehicleObject.getScript();
         int var2 = PZMath.fastfloor(this.vehicleObject.getZ());
         Vector3f var3 = this.tempVec3f;
         this.vehicleObject.getForwardVector(var3);
         Transform var4 = this.tempXfrm;
         this.vehicleObject.getWorldTransform(var4);
         VehiclePoly var5 = this.vehicleObject.getPoly();
         LineDrawer.addLine(var5.x1, var5.y1, var2, var5.x2, var5.y2, var2, 1.0F, 1.0F, 1.0F, null, true);
         LineDrawer.addLine(var5.x2, var5.y2, var2, var5.x3, var5.y3, var2, 1.0F, 1.0F, 1.0F, null, true);
         LineDrawer.addLine(var5.x3, var5.y3, var2, var5.x4, var5.y4, var2, 1.0F, 1.0F, 1.0F, null, true);
         LineDrawer.addLine(var5.x4, var5.y4, var2, var5.x1, var5.y1, var2, 1.0F, 1.0F, 1.0F, null, true);
         Vector2f var6 = BaseVehicle.allocVector2f();
         float var7 = IsoCamera.frameState.camCharacterX;
         float var8 = IsoCamera.frameState.camCharacterY;
         this.vehicleObject.getClosestPointOnPoly(var7, var8, var6);
         if (this.vehicleObject.isPointLeftOfCenter(var6.x, var6.y)) {
            this.drawCircle(var6.x, var6.y, 0.05F, 0.0F, 1.0F, 0.0F, 1.0F);
         } else {
            this.drawCircle(var6.x, var6.y, 0.05F, 0.0F, 0.0F, 1.0F, 1.0F);
         }

         BaseVehicle.releaseVector2f(var6);
         _UNIT_Y.set(0.0F, 1.0F, 0.0F);

         for (int var9 = 0; var9 < this.vehicleObject.getScript().getWheelCount(); var9++) {
            Wheel var10 = var1.getWheel(var9);
            this.tempVec3f.set(var10.getOffset());
            if (var1.getModel() != null) {
               this.tempVec3f.add(var1.getModelOffset());
            }

            this.vehicleObject.getWorldPos(this.tempVec3f, this.tempVec3f);
            float var11 = this.tempVec3f.x;
            float var12 = this.tempVec3f.y;
            this.vehicleObject.getWheelForwardVector(var9, this.tempVec3f);
            LineDrawer.addLine(var11, var12, var2, var11 + this.tempVec3f.x, var12 + this.tempVec3f.z, var2, 1.0F, 1.0F, 1.0F, null, true);
            this.drawRect(this.tempVec3f, var11 - WorldSimulation.instance.offsetX, var12 - WorldSimulation.instance.offsetY, var10.width, var10.radius);
         }

         if (this.vehicleObject.collideX != -1.0F) {
            this.vehicleObject.getForwardVector(var3);
            this.drawCircle(this.vehicleObject.collideX, this.vehicleObject.collideY, 0.3F);
            this.vehicleObject.collideX = -1.0F;
            this.vehicleObject.collideY = -1.0F;
         }

         int var14 = this.vehicleObject.getJoypad();
         if (var14 != -1) {
            float var15 = JoypadManager.instance.getMovementAxisX(var14);
            float var17 = JoypadManager.instance.getMovementAxisY(var14);
            float var19 = JoypadManager.instance.getDeadZone(var14, 0);
            if (Math.abs(var17) > var19 || Math.abs(var15) > var19) {
               Vector2 var13 = this.tempVec2.set(var15, var17);
               var13.setLength(4.0F);
               var13.rotate((float) (-Math.PI / 4));
               LineDrawer.addLine(
                  this.vehicleObject.getX(),
                  this.vehicleObject.getY(),
                  this.vehicleObject.getZ(),
                  this.vehicleObject.getX() + var13.x,
                  this.vehicleObject.getY() + var13.y,
                  this.vehicleObject.getZ(),
                  1.0F,
                  1.0F,
                  1.0F,
                  null,
                  true
               );
            }
         }

         float var16 = this.vehicleObject.getX();
         float var18 = this.vehicleObject.getY();
         float var20 = this.vehicleObject.getZ();
         LineDrawer.addLine(var16 - 0.5F, var18, var20, var16 + 0.5F, var18, var20, 1.0F, 1.0F, 1.0F, null, true);
         LineDrawer.addLine(var16, var18 - 0.5F, var20, var16, var18 + 0.5F, var20, 1.0F, 1.0F, 1.0F, null, true);
         this.renderClosestPointToOtherVehicle();
      }
   }

   private void renderClosestPointToOtherVehicle() {
      ArrayList var1 = IsoWorld.instance.currentCell.getVehicles();
      BaseVehicle var2 = null;
      float var3 = Float.MAX_VALUE;

      for (int var4 = 0; var4 < var1.size(); var4++) {
         BaseVehicle var5 = (BaseVehicle)var1.get(var4);
         if (var5 != this.vehicleObject) {
            float var6 = IsoUtils.DistanceToSquared(this.vehicleObject.getX(), this.vehicleObject.getY(), var5.getX(), var5.getY());
            if (var6 < var3) {
               var3 = var6;
               var2 = var5;
            }
         }
      }

      if (var2 != null && !(var3 > 100.0F)) {
         Vector2f var8 = BaseVehicle.allocVector2f();
         Vector2f var9 = BaseVehicle.allocVector2f();
         var3 = this.vehicleObject.getClosestPointOnPoly(var2, var8, var9);
         if (var3 == 0.0F) {
            LineDrawer.addRect(var8.x, var8.y, this.vehicleObject.getZ(), 0.05F, 0.05F, 0.0F, 1.0F, 1.0F);
         } else {
            LineDrawer.addLine(var8.x, var8.y, this.vehicleObject.getZ(), var9.x, var9.y, var2.getZ(), 0.0F, 1.0F, 1.0F, 1.0F);
         }

         BaseVehicle.releaseVector2f(var8);
         BaseVehicle.releaseVector2f(var9);
      }
   }

   public void drawRect(Vector3f var1, float var2, float var3, float var4, float var5) {
      this.drawRect(var1, var2, var3, var4, var5, 1.0F, 1.0F, 1.0F);
   }

   public void drawRect(Vector3f var1, float var2, float var3, float var4, float var5, float var6, float var7, float var8) {
      float var9 = var1.x;
      float var10 = var1.y;
      float var11 = var1.z;
      Vector3f var12 = this.tempVec3f3;
      var1.cross(_UNIT_Y, var12);
      float var13 = 1.0F;
      var1.x *= 1.0F * var5;
      var1.z *= 1.0F * var5;
      var12.x *= 1.0F * var4;
      var12.z *= 1.0F * var4;
      float var14 = var2 + var1.x;
      float var15 = var3 + var1.z;
      float var16 = var2 - var1.x;
      float var17 = var3 - var1.z;
      float var18 = var14 - var12.x / 2.0F;
      float var19 = var14 + var12.x / 2.0F;
      float var20 = var16 - var12.x / 2.0F;
      float var21 = var16 + var12.x / 2.0F;
      float var22 = var17 - var12.z / 2.0F;
      float var23 = var17 + var12.z / 2.0F;
      float var24 = var15 - var12.z / 2.0F;
      float var25 = var15 + var12.z / 2.0F;
      var18 += WorldSimulation.instance.offsetX;
      var24 += WorldSimulation.instance.offsetY;
      var19 += WorldSimulation.instance.offsetX;
      var25 += WorldSimulation.instance.offsetY;
      var20 += WorldSimulation.instance.offsetX;
      var22 += WorldSimulation.instance.offsetY;
      var21 += WorldSimulation.instance.offsetX;
      var23 += WorldSimulation.instance.offsetY;
      int var26 = PZMath.fastfloor(this.vehicleObject.getZ());
      float var27 = this.vehicleObject.getAlpha(IsoPlayer.getPlayerIndex());
      LineDrawer.addLine(var18, var24, var26, var19, var25, var26, var6, var7, var8, var27);
      LineDrawer.addLine(var18, var24, var26, var20, var22, var26, var6, var7, var8, var27);
      LineDrawer.addLine(var19, var25, var26, var21, var23, var26, var6, var7, var8, var27);
      LineDrawer.addLine(var20, var22, var26, var21, var23, var26, var6, var7, var8, var27);
      var1.set(var9, var10, var11);
   }

   public void drawCircle(float var1, float var2, float var3) {
      this.drawCircle(var1, var2, var3, 1.0F, 1.0F, 1.0F, 1.0F);
   }

   public void drawCircle(float var1, float var2, float var3, float var4, float var5, float var6, float var7) {
      LineDrawer.DrawIsoCircle(var1, var2, this.vehicleObject.getZ(), var3, 16, var4, var5, var6, var7);
   }

   public void control_NoControl() {
      float var1 = GameTime.getInstance().getMultiplier() / 0.8F;
      if (!this.vehicleObject.isEngineRunning()) {
         if (this.vehicleObject.engineSpeed > 0.0) {
            this.vehicleObject.engineSpeed = Math.max(this.vehicleObject.engineSpeed - 50.0F * var1, 0.0);
         }
      } else if (this.vehicleObject.engineSpeed > this.vehicleObject.getScript().getEngineIdleSpeed()) {
         if (!this.vehicleObject.isRegulator()) {
            this.vehicleObject.engineSpeed -= 20.0F * var1;
         }
      } else {
         this.vehicleObject.engineSpeed += 20.0F * var1;
      }

      if (!this.vehicleObject.isRegulator()) {
         this.vehicleObject.transmissionNumber = TransmissionNumber.N;
      }

      this.engineForce = 0.0F;
      if (this.vehicleObject.engineSpeed > 1000.0) {
         this.brakingForce = 15.0F;
      } else {
         this.brakingForce = 10.0F;
      }
   }

   static {
      gears[0] = new GearInfo(0, 25, 0.0F);
      gears[1] = new GearInfo(25, 50, 0.5F);
      gears[2] = new GearInfo(50, 1000, 0.5F);
   }
}
