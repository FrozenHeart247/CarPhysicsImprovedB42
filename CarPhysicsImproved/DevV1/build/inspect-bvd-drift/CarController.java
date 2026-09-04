package zombie.core.physics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import zombie.core.math.PZMath;
import zombie.core.physics.CarController.BulletVariables;
import zombie.core.physics.CarController.ClientControls;
import zombie.core.physics.CarController.ConverterSpec;
import zombie.core.physics.CarController.EngineSpec;
import zombie.core.physics.CarController.GearInfo;
import zombie.core.physics.CarController.GearboxSpec;
import zombie.core.physics.CarController.VehicleSpec;
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
import zombie.iso.IsoFloorBloodSplat;
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
   private float steerAngle = 0.0F;
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
   private static final TransmissionNumber[] GEAR_LABELS = new TransmissionNumber[]{
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
   private static final GearboxSpec DEFAULT_GEARBOX_3 = makeGearbox(new float[]{2.6F, 2.6F, 1.6F, 1.0F}, 3);
   private static final GearboxSpec DEFAULT_GEARBOX_4 = makeGearbox(new float[]{3.0F, 3.0F, 1.8F, 1.3F, 1.0F}, 4);
   private static final GearboxSpec DEFAULT_GEARBOX_5 = makeGearbox(new float[]{3.2F, 3.2F, 2.0F, 1.5F, 1.15F, 0.9F}, 5);
   private static final ConverterSpec DEFAULT_CONVERTER = makeConverter(2000.0F, 800.0F);
   private final Vector3f dragDir = new Vector3f();
   private final float[] skidSpinDelta = new float[2];
   float drunkDelayCommandTimer = 0.0F;
   boolean wasBreaking = false;
   boolean wasGas = false;
   boolean wasGasR = false;
   boolean wasSteering = false;
   private boolean manualGearbox = false;
   private boolean autoReverseEnabled = true;
   private boolean analogPedals = false;
   private boolean joystickPedalAxis = false;
   private boolean tunableSteering = true;
   private float steerGainLow = 1.0F;
   private float steerGainHigh = 0.1F;
   private float steerReturnLow = 1.0F;
   private float steerReturnHigh = 0.1F;
   private float steerSnap = 3.0F;
   private float steerHighSpeedRef = 75.0F;
   private int configRefreshCountdown = 0;
   private float pedalGas = 0.0F;
   private float pedalBrake = 0.0F;
   private int shiftDownEdge = 0;
   private int shiftUpEdge = 0;
   private float overrev = 0.0F;
   private int heldGear = 1;
   private boolean reversing = false;
   private float drivenWheelSpeed = 0.0F;
   private float burnout = 0.0F;
   private float[] rearSpinAngle = new float[]{0.0F, 0.0F};
   private float[] rearSpinAngleLast = new float[]{0.0F, 0.0F};
   private float idleRpmTarget = 800.0F;
   private float idleRpmRetimer = 0.0F;
   private boolean offroadSkidLast = false;
   private float tireFill = 1.0F;
   private float tireWear = 1.0F;
   private float gripFactor = 1.0F;
   private long crankSoundId = -1L;
   private long startSoundId = -1L;
   private float startupBlend = 0.0F;
   public BaseSoundEmitter loopEmitter = null;
   private long loopEngineId = 0L;
   private long loopExhaustId = 0L;
   public static HashMap<String, VehicleSpec> vehicleSpecs = new HashMap<>();
   private static HashMap<String, EngineSpec> engineSpecs = new HashMap<>();
   private static HashMap<String, GearboxSpec> gearboxSpecs = new HashMap<>();
   private static HashMap<String, ConverterSpec> converterSpecs = new HashMap<>();
   private static HashMap<String, Float> baseWheelFriction = new HashMap<>();
   private static final HashMap<String, Float> lastAppliedFriction = new HashMap<>();
   private static final float FRICTION_EPSILON = 1.0E-4F;
   private static String lastDrivenScript = "";
   private static int loadedSpecGen = -1;
   private static boolean defaultsLoaded = false;
   private static ItemTag tagBrakeBooster = null;
   private static ItemTag tagPowerSteering = null;
   private static ItemTag tagFanBelt = null;
   private static ItemTag tagGearbox = null;
   private static ItemTag tagConverter = null;
   private static ItemTag tagFlywheel = null;
   private static final int BVD_BRIDGE_PROTOCOL = 1;
   private KahluaTableImpl computedOut = null;
   private final HashMap<String, String> tireFamilyCache = new HashMap<>();
   private float gripRoadOut = 1.0F;
   private float gripWetOut = 1.0F;
   private float gripSnowOut = 1.0F;
   private float gripOffroadOut = 1.0F;
   private String tireFamilyOut = "";
   private float ladenRatioOut = 1.0F;
   private float loadPenaltyOut = 0.0F;
   private long bvdSkidPruneMs = 0L;
   private int bvdSinkTicks = 0;
   private int bvdGuardCooldown = 0;
   private final HashMap<String, SandboxOption> sandboxHandleCache = new HashMap<>();
   private final HashSet<String> sandboxHandleMisses = new HashSet<>();

   private static GearboxSpec makeGearbox(float[] var0, int var1) {
      GearboxSpec var2 = new GearboxSpec();
      var2.ratios = var0;
      var2.gearCount = var1;
      return var2;
   }

   private static ConverterSpec makeConverter(float var0, float var1) {
      ConverterSpec var2 = new ConverterSpec();
      var2.lockupRpm = var0;
      var2.lockupRange = var1;
      return var2;
   }

   private SandboxOption resolveSandboxOption(String var1) {
      SandboxOption var2 = this.sandboxHandleCache.get(var1);
      if (var2 != null) {
         return var2;
      } else if (this.sandboxHandleMisses.contains(var1)) {
         return null;
      } else {
         SandboxOption var3 = SandboxOptions.instance.getOptionByName(var1);
         if (var3 == null) {
            this.sandboxHandleMisses.add(var1);
            return null;
         } else {
            this.sandboxHandleCache.put(var1, var3);
            return var3;
         }
      }
   }

   private float getSandboxOption(String var1, float var2) {
      SandboxOption var3 = this.resolveSandboxOption(var1);
      if (var3 == null) {
         return var2;
      }

      String var4 = var3.asConfigOption().getValueAsString();

      float var5;
      try {
         var5 = (float)Double.parseDouble(var4);
      } catch (NumberFormatException var7) {
         DebugLog.log("[BetterVehicleDynamics] sandbox key " + var1 + " not numeric ('" + var4 + "'); using " + var2);
         return var2;
      }

      if (Float.isNaN(var5)) {
         DebugLog.log("[BetterVehicleDynamics] sandbox key " + var1 + " was NaN; using " + var2);
         return var2;
      } else {
         return var5;
      }
   }

   private boolean getSandboxOptionBoolean(String var1, boolean var2) {
      SandboxOption var3 = this.resolveSandboxOption(var1);
      return var3 == null ? var2 : (Boolean)var3.asConfigOption().getValueAsObject();
   }

   private KahluaTableImpl bvdBridge() {
      return (KahluaTableImpl)LuaManager.env.rawget("BetterVehicleDynamicsMod");
   }

   private static float bridgeFieldOr(KahluaTableImpl var0, String var1, float var2) {
      if (var0 == null) {
         return var2;
      } else {
         Object var3 = var0.rawget(var1);
         if (var3 instanceof Double var5) {
            return var5.floatValue();
         } else {
            return var3 instanceof Float var4 ? var4 : var2;
         }
      }
   }

   private static String tireFamilyKey(String var0) {
      if (var0 == null) {
         return "";
      }

      int var1 = var0.length();

      while (var1 > 0 && var0.charAt(var1 - 1) >= '0' && var0.charAt(var1 - 1) <= '9') {
         var1--;
      }

      return var1 == var0.length() ? var0 : var0.substring(0, var1);
   }

   private String tireFamilyKeyCached(String var1) {
      if (var1 == null) {
         return "";
      }

      String var2 = this.tireFamilyCache.get(var1);
      if (var2 != null) {
         return var2;
      }

      String var3 = tireFamilyKey(var1);
      this.tireFamilyCache.put(var1, var3);
      return var3;
   }

   private void bvdPruneSkidMarks() {
      long var1 = System.currentTimeMillis();
      if (var1 - this.bvdSkidPruneMs >= 1500L) {
         this.bvdSkidPruneMs = var1;
         if (this.vehicleObject != null && IsoWorld.instance != null && IsoWorld.instance.currentCell != null) {
            float var3 = (float)GameTime.getInstance().getWorldAgeHours();
            float var4 = 0.16666667F;
            int var5 = PZMath.coorddivision(this.vehicleObject.getXi(), 8);
            int var6 = PZMath.coorddivision(this.vehicleObject.getYi(), 8);

            for (int var7 = -1; var7 <= 1; var7++) {
               for (int var8 = -1; var8 <= 1; var8++) {
                  IsoChunk var9 = IsoWorld.instance.currentCell.getChunk(var5 + var8, var6 + var7);
                  if (var9 != null) {
                     ArrayList var10 = null;
                     int var11 = 0;
                     int var12 = var9.floorBloodSplats.size();

                     for (int var13 = 0; var13 < var12; var13++) {
                        IsoFloorBloodSplat var14 = (IsoFloorBloodSplat)var9.floorBloodSplats.get(var13);
                        boolean var15 = var14.type >= 21 && var14.type <= 24 && var3 - var14.worldAge >= var4;
                        if (!var15) {
                           if (var10 != null) {
                              var10.add(var14);
                           }
                        } else {
                           if (var10 == null) {
                              var10 = new ArrayList();

                              for (int var16 = 0; var16 < var13; var16++) {
                                 var10.add((IsoFloorBloodSplat)var9.floorBloodSplats.get(var16));
                              }
                           }

                           var11++;
                           var9.floorBloodSplatsFade.remove(var14);
                        }
                     }

                     if (var11 > 0) {
                        var9.floorBloodSplats.clear();

                        for (int var17 = 0; var17 < var10.size(); var17++) {
                           var9.floorBloodSplats.add((IsoFloorBloodSplat)var10.get(var17));
                        }

                        var9.invalidateRenderChunkLevels(1L);
                     }
                  }
               }
            }
         }
      }
   }

   private void bvdStabilityGuard() {
      if (!GameServer.server) {
         if (this.vehicleObject != null && !this.vehicleObject.isNetPlayerAuthorization(Authorization.Remote)) {
            KahluaTableImpl var1 = this.bvdBridge();
            Object var2 = var1 == null ? null : var1.rawget("stabilityGuard");
            KahluaTableImpl var3 = var2 instanceof KahluaTableImpl ? (KahluaTableImpl)var2 : null;
            if (var3 != null && !(bridgeFieldOr(var3, "enabled", 0.0F) < 0.5F)) {
               float var4 = bridgeFieldOr(var3, "sinkDepth", 1.0F);
               int var5 = (int)bridgeFieldOr(var3, "dwellTicks", 5.0F);
               int var6 = (int)bridgeFieldOr(var3, "cooldownTicks", 60.0F);
               if (this.bvdGuardCooldown > 0) {
                  this.bvdGuardCooldown--;
               }

               int var7 = PZMath.fastfloor(this.vehicleObject.getZ());
               float var8 = var7 * 2.44949F;
               float var9 = this.vehicleObject.jniTransform.origin.y;
               boolean var10 = var9 < var8 - var4;
               if (!var10) {
                  this.bvdSinkTicks = 0;
               } else if (this.bvdGuardCooldown > 0) {
                  this.bvdSinkTicks = 0;
               } else {
                  this.bvdSinkTicks++;
                  if (this.bvdSinkTicks >= var5) {
                     this.vehicleObject.jniTransform.origin.y = var8 + 0.1F;
                     this.vehicleObject.setWorldTransform(this.vehicleObject.jniTransform);
                     this.bvdSinkTicks = 0;
                     this.bvdGuardCooldown = var6;
                  }
               }
            } else {
               this.bvdSinkTicks = 0;
               if (this.bvdGuardCooldown > 0) {
                  this.bvdGuardCooldown--;
               }
            }
         }
      }
   }

   private void publishComputedState() {
      IsoPlayer var1 = IsoPlayer.getInstance();
      if (var1 != null && var1.getVehicle() == this.vehicleObject) {
         KahluaTableImpl var2 = this.bvdBridge();
         if (var2 != null) {
            if (this.computedOut == null) {
               this.computedOut = new KahluaTableImpl(new LinkedHashMap());
            }

            this.computedOut.rawset("tireFamily", this.tireFamilyOut);
            this.computedOut.rawset("gripRoad", (double)this.gripRoadOut);
            this.computedOut.rawset("gripWet", (double)this.gripWetOut);
            this.computedOut.rawset("gripSnow", (double)this.gripSnowOut);
            this.computedOut.rawset("gripOffroad", (double)this.gripOffroadOut);
            this.computedOut.rawset("ladenRatio", (double)this.ladenRatioOut);
            this.computedOut.rawset("loadPenalty", (double)this.loadPenaltyOut);
            var2.rawset("computed", this.computedOut);
         }
      }
   }

   public CarController(BaseVehicle var1) {
      KahluaTableImpl var2 = this.bvdBridge();
      if (var2 != null) {
         var2.rawset("javaVersion", "3.4");
         var2.rawset("protocolVersion", 1.0);
      } else {
         DebugLog.log("Better Vehicle Dynamics Java side present but the Workshop mod is not enabled; sandbox options will not load.");
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
      KahluaTableImpl var1 = this.bvdBridge();
      int var2 = -1;
      boolean var3 = false;
      if (var1 != null) {
         var2 = var1.rawgetInt("specGen");
         var3 = var2 >= 0;
      }

      if (var3) {
         if (var2 == loadedSpecGen) {
            return;
         }
      } else if (defaultsLoaded) {
         return;
      }

      tagBrakeBooster = ItemTag.get(ResourceLocation.of("ProjectSummerCar:EngineBrakeBooster"));
      tagPowerSteering = ItemTag.get(ResourceLocation.of("ProjectSummerCar:EnginePowerSteeringPump"));
      tagFanBelt = ItemTag.get(ResourceLocation.of("ProjectSummerCar:EngineFanBelt"));
      tagGearbox = ItemTag.get(ResourceLocation.of("ProjectSummerCar:EngineTransmission"));
      tagConverter = ItemTag.get(ResourceLocation.of("ProjectSummerCar:EngineTorqueConverter"));
      tagFlywheel = ItemTag.get(ResourceLocation.of("ProjectSummerCar:EngineFlywheel"));
      baseWheelFriction.clear();
      ArrayList var4 = ScriptManager.instance.getAllVehicleScripts();

      for (int var5 = 0; var5 < var4.size(); var5++) {
         VehicleScript var6 = (VehicleScript)var4.get(var5);
         baseWheelFriction.put(var6.getName(), var6.getWheelFriction());
      }

      vehicleSpecs.clear();
      engineSpecs.clear();
      gearboxSpecs.clear();
      converterSpecs.clear();
      if (var1 == null) {
         defaultsLoaded = true;
      } else {
         if (var1.rawget("vehicleData") instanceof KahluaTableImpl var19) {
            for (Entry var8 : var19.delegate.entrySet()) {
               String var9 = var8.getKey().toString();
               VehicleSpec var10 = new VehicleSpec();
               if (var8.getValue() instanceof KahluaTableImpl var12) {
                  for (Entry var14 : var12.delegate.entrySet()) {
                     String var15 = var14.getKey().toString();
                     if (var15.equals("engineSound")) {
                        var10.engineSound = var14.getValue().toString();
                     } else if (var15.equals("cargo")) {
                        if (var14.getValue() instanceof Double var16) {
                           var10.cargo = (float)var16.doubleValue();
                        }
                     } else if (var15.equals("hp")) {
                        var10.realismApplied = true;
                     }
                  }
               }

               vehicleSpecs.put(var9, var10);
            }
         }

         this.absorbEngineTable(var1.rawget("engineData"));
         this.absorbGearboxTable(var1.rawget("gearboxData"));
         this.absorbConverterTable(var1.rawget("converterData"));
         if (var3) {
            loadedSpecGen = var2;
         } else {
            defaultsLoaded = true;
         }
      }
   }

   private void absorbEngineTable(Object var1) {
      if (var1 instanceof KahluaTableImpl) {
         for (Entry var3 : ((KahluaTableImpl)var1).delegate.entrySet()) {
            String var4 = var3.getKey().toString();
            EngineSpec var5 = new EngineSpec();
            Object var6 = var3.getValue();
            if (var6 instanceof KahluaTableImpl) {
               for (Entry var8 : ((KahluaTableImpl)var6).delegate.entrySet()) {
                  String var9 = var8.getKey().toString();
                  Object var10 = var8.getValue();
                  if (var9.equals("engineSound")) {
                     var5.engineSound = var10.toString();
                  } else if (var9.equals("engineSoundRPM")) {
                     if (var10 instanceof Double var11) {
                        var5.engineSoundRpm = (float)var11.doubleValue();
                     }
                  } else if (var9.equals("exhaustSound")) {
                     var5.exhaustSound = var10.toString();
                  } else if (var9.equals("crankSound")) {
                     var5.crankSound = var10.toString();
                  } else if (var9.equals("startSound")) {
                     var5.startSound = var10.toString();
                  }
               }
            }

            engineSpecs.put(var4, var5);
         }
      }
   }

   private void absorbGearboxTable(Object var1) {
      if (var1 instanceof KahluaTableImpl) {
         for (Entry var3 : ((KahluaTableImpl)var1).delegate.entrySet()) {
            GearboxSpec var4 = new GearboxSpec();
            String var5 = "";
            ArrayList var6 = new ArrayList();
            Object var7 = var3.getValue();
            if (var7 instanceof KahluaTableImpl) {
               for (Entry var9 : ((KahluaTableImpl)var7).delegate.entrySet()) {
                  String var10 = var9.getKey().toString();
                  if (var10.equals("name")) {
                     var5 = var9.getValue().toString();
                  } else if (var10.equals("ratios") && var9.getValue() instanceof KahluaTableImpl) {
                     for (Entry var12 : ((KahluaTableImpl)var9.getValue()).delegate.entrySet()) {
                        if (var12.getValue() instanceof Double var13) {
                           var6.add((float)var13.doubleValue());
                        }
                     }
                  }
               }
            }

            if (!var6.isEmpty()) {
               var4.ratios = new float[var6.size()];

               for (int var15 = 0; var15 < var6.size(); var15++) {
                  var4.ratios[var15] = (Float)var6.get(var15);
               }

               var4.gearCount = var6.size() - 1;
               gearboxSpecs.put(var5, var4);
            }
         }
      }
   }

   private void absorbConverterTable(Object var1) {
      if (var1 instanceof KahluaTableImpl) {
         for (Entry var3 : ((KahluaTableImpl)var1).delegate.entrySet()) {
            ConverterSpec var4 = new ConverterSpec();
            String var5 = "";
            Object var6 = var3.getValue();
            if (var6 instanceof KahluaTableImpl) {
               for (Entry var8 : ((KahluaTableImpl)var6).delegate.entrySet()) {
                  String var9 = var8.getKey().toString();
                  if (var9.equals("name")) {
                     var5 = var8.getValue().toString();
                  } else if (var9.equals("lockupRPM")) {
                     if (var8.getValue() instanceof Double var10) {
                        var4.lockupRpm = (float)var10.doubleValue();
                     }
                  } else if (var9.equals("lockupRange") && var8.getValue() instanceof Double var12) {
                     var4.lockupRange = (float)var12.doubleValue();
                  }
               }
            }

            converterSpecs.put(var5, var4);
         }
      }
   }

   private void updateModOptions() {
      if (--this.configRefreshCountdown <= 0) {
         this.configRefreshCountdown = 60;
         this.sandboxHandleCache.clear();
         this.sandboxHandleMisses.clear();
         KahluaTableImpl var1 = this.bvdBridge();
         if (var1 != null) {
            this.manualGearbox = var1.rawgetBool("manualShift");
            this.autoReverseEnabled = var1.rawgetBool("autoReverse") || !this.manualGearbox;
            this.analogPedals = var1.rawgetBool("useAnalogThrottle") && this.vehicleObject.getJoypad() != -1;
            this.joystickPedalAxis = var1.rawgetBool("JoystickThrottle");
            this.tunableSteering = var1.rawgetBool("CustomizableSteering");
            this.steerGainLow = var1.rawgetFloat("SteeringFactorLowSpeed");
            this.steerGainHigh = var1.rawgetFloat("SteeringFactorHighSpeed");
            this.steerReturnLow = var1.rawgetFloat("SteeringCenteringLowSpeed");
            this.steerReturnHigh = var1.rawgetFloat("SteeringCenteringHighSpeed");
            this.steerSnap = var1.rawgetFloat("SteeringSnapback");
            this.steerHighSpeedRef = var1.rawgetFloat("SteeringHighSpeed");
         }
      }
   }

   private void updateControlsCalculation(ItemContainer var1) {
      this.speed = this.vehicleObject.getCurrentSpeedKmHour();
      boolean var2 = this.vehicleObject.getDriver() != null && this.vehicleObject.getDriver().getMoodles().getMoodleLevel(MoodleType.DRUNK) > 1;
      float var3 = 0.0F;
      Vector3f var4 = this.vehicleObject.getLinearVelocity(this.tempVec3f2);
      var4.y = 0.0F;
      if (var4.length() > 0.5) {
         var4.normalize();
         Vector3f var5 = this.tempVec3f;
         this.vehicleObject.getForwardVector(var5);
         var3 = var4.dot(var5);
      }

      float var14 = GameTime.getInstance().getRealworldSecondsSinceLastUpdate();
      float var6 = this.vehicleObject.getCurrentSpeedKmHour() * BaseVehicle.getFakeSpeedModifier();
      this.isGas = false;
      this.isGasR = false;
      this.isBreak = false;
      boolean var7 = this.autoReverseEnabled;
      if (this.clientControls.shift) {
         var7 = false;
      }

      if (var7 && this.analogPedals) {
         if (var3 >= 0.0F && this.pedalGas - this.pedalBrake > 0.0F) {
            this.isGas = true;
            this.reversing = false;
         }

         if (var3 <= 0.0F && this.pedalGas - this.pedalBrake < 0.0F) {
            this.isGasR = true;
            this.reversing = true;
         }

         if (this.reversing) {
            float var8 = this.pedalGas;
            this.pedalGas = this.pedalBrake;
            this.pedalBrake = var8;
         }
      }

      if (var7 && !this.analogPedals) {
         if (this.clientControls.backward) {
            if (var3 > 0.0F) {
               this.isBreak = true;
            }

            if (var3 <= 0.0F) {
               this.isGasR = true;
               this.reversing = true;
            }
         }

         if (this.clientControls.forward) {
            if (var3 < 0.0F) {
               this.isBreak = true;
            }

            if (var3 >= 0.0F) {
               this.isGas = true;
               this.reversing = false;
            }

            if (this.isGasR) {
               this.isGasR = false;
               this.isBreak = true;
            }
         }
      } else if (!var7 && !this.analogPedals) {
         this.reversing = false;
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

      if (var2 && this.vehicleObject.getEngineState() != engineStateTypes.Idle) {
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

      this.updateRegulator(var14);
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
         this.reversing = false;
      }

      float var15 = this.vehicleObject.throttle;
      if (this.vehicleObject.isRegulator() && !this.isGas && !this.isGasR) {
         float var9 = this.vehicleObject.getRegulatorSpeed() - var6;
         var15 = Math.min(Math.max(var9 * 0.5F, 0.0F), 1.0F);
         if (this.isBreak) {
            var15 = 0.0F;
         }
      }

      IsoGameCharacter var17 = this.vehicleObject.getDriver();
      boolean var10 = var17 != null && var17.hasTrait(CharacterTrait.SPEED_DEMON);
      boolean var11 = var17 != null && var17.hasTrait(CharacterTrait.SUNDAY_DRIVER);
      if (this.analogPedals) {
         if (!this.vehicleObject.isRegulator()) {
            var15 = this.pedalGas;
            this.overrev = this.pedalGas > 0.9 ? 1.0F : 0.0F;
         } else {
            this.overrev = 0.0F;
         }
      } else {
         if (!this.isGas && !this.isGasR) {
            if (var11) {
               var15 -= var14 * 6.0F;
            } else if (var10) {
               var15 -= var14 * 2.0F;
            } else {
               var15 -= var14 * 4.0F;
            }
         } else if (var11) {
            var15 += var14 * 2.0F;
         } else if (var10) {
            var15 += var14 * 6.0F;
         } else {
            var15 += var14 * 4.0F;
         }

         if (var15 >= 1.0F) {
            if (var11) {
               this.overrev += var14 * 0.3F;
            } else {
               this.overrev += var14 * 1.0F;
            }
         } else {
            this.overrev -= var14 * 1.0F;
         }
      }

      this.overrev = PZMath.clamp(this.overrev, 0.0F, 1.0F);
      var15 = PZMath.clamp(var15, 0.0F, 1.0F);
      this.engineForce = 0.0F;
      this.brakingForce = 0.0F;
      if (GameClient.client) {
         var15 = Math.min(var15, (float)(ServerOptions.instance.speedLimit.getValue() - this.vehicleObject.getCurrentSpeedKmHour() - 2.0) / 2.0F);
      }

      this.vehicleObject.throttle = var15;
      if (this.isGas || this.isGasR || this.isBreak) {
         UIManager.speedControls.SetCurrentGameSpeed(1);
      }

      if ((this.isGasR || this.isGas) && this.clientControls.wasUsingParkingBrakes) {
         this.clientControls.wasUsingParkingBrakes = false;
      }

      this.updateBackSignal();
      if (this.analogPedals) {
         this.isBreak = this.isBreak | this.pedalBrake > 0.1;
      }

      if (this.isBreak) {
         this.brakingForce = this.vehicleObject.getBrakingForce();
         if (this.analogPedals && !this.clientControls.brake) {
            this.brakingForce = this.brakingForce * this.pedalBrake;
         }

         if (this.clientControls.brake) {
            this.brakingForce *= 3.0F;
         }

         if (var1 != null) {
            InventoryItem var12 = var1.getFirstTag(tagBrakeBooster);
            float var13 = 0.0F;
            if (var12 != null) {
               var13 = Math.min(1.0F, var12.getCondition() / 50.0F);
            }

            if (!this.vehicleObject.isEngineRunning()) {
               var13 = 0.0F;
            }

            this.brakingForce = (float)(this.brakingForce * Math.max(0.2, var13));
         }
      }

      this.updateBrakeLights();
      BaseVehicle var18 = this.vehicleObject.getVehicleTowedBy();
      if (var18 != null && var18.getDriver() == null && this.vehicleObject.getDriver() != null && !GameClient.client) {
         this.vehicleObject.addPointConstraint(null, var18, this.vehicleObject.getTowAttachmentSelf(), var18.getTowAttachmentSelf());
      }
   }

   private float getDriftSteeringBoost() {
      if (!this.getSandboxOptionBoolean("BetterVehicleDynamics.Drift", false)) {
         return 1.0F;
      }

      KahluaTableImpl var1 = this.bvdBridge();
      return var1 != null && var1.rawgetBool("driftActive") ? this.getSandboxOption("BetterVehicleDynamics.DriftSteer", 1.5F) : 1.0F;
   }

   private void updateSteeringStock(VehicleScript var1) {
      float var2 = GameTime.getInstance().getMultiplier() / 0.8F;
      float var3 = this.getDriftSteeringBoost();
      if (Math.abs(this.clientControls.steering) > 0.1F) {
         float var4 = 1.0F - this.speed / this.vehicleObject.getMaxSpeed();
         if (var4 < 0.1F) {
            var4 = 0.1F;
         }

         this.steerAngle = this.steerAngle - (this.clientControls.steering + this.steerAngle) * 0.06F * var2 * var4 * var3;
      } else if (Math.abs(this.steerAngle) <= 0.04) {
         this.steerAngle = 0.0F;
      } else if (this.steerAngle > 0.0F) {
         this.steerAngle -= 0.04F * var2;
         this.steerAngle = Math.max(this.steerAngle, 0.0F);
      } else {
         this.steerAngle += 0.04F * var2;
         this.steerAngle = Math.min(this.steerAngle, 0.0F);
      }

      float var5 = var1.getSteeringClamp(this.speed);
      this.steerAngle = PZMath.clamp(this.steerAngle, -var5, var5);
   }

   private void updateSteeringTunable(ItemContainer var1, VehicleScript var2, float var3) {
      float var4 = Math.min(1.0F, Math.abs(this.speed) / this.steerHighSpeedRef);
      float var5 = this.steerGainLow + (this.steerGainHigh - this.steerGainLow) * var4;
      float var6 = this.steerReturnLow + (this.steerReturnHigh - this.steerReturnLow) * var4;
      if (var1 != null) {
         InventoryItem var7 = var1.getFirstTag(tagPowerSteering);
         InventoryItem var8 = var1.getFirstTag(tagFanBelt);
         float var9 = 0.0F;
         float var10 = 0.0F;
         if (var7 != null) {
            var9 = var7.getCondition();
            var9 *= Math.min(1.0F, var7.getFluidContainer().getFilledRatio() * 2.0F);
         }

         if (var8 != null) {
            var10 = var8.getCondition();
         }

         float var11 = var9 / 50.0F * Math.max(1.0F, var10 / 30.0F) * (float)this.vehicleObject.getEngineSpeed() / 2000.0F;
         var5 = Math.min(var5, var11 * this.steerGainLow);
      }

      boolean var12 = false;
      if (Math.abs(this.clientControls.steering) > 0.1F) {
         if (this.clientControls.steering < 0.0F == this.steerAngle < 0.0F) {
            var5 *= this.steerSnap;
         }

         this.steerAngle = this.steerAngle - (this.clientControls.steering + this.steerAngle) * 3.0F * var3 * var5 * this.getDriftSteeringBoost();
         var12 = true;
      } else if (Math.abs(this.steerAngle) <= 0.04) {
         this.steerAngle = 0.0F;
      }

      if (!var12) {
         if (this.steerAngle > 0.0F) {
            this.steerAngle -= var6 * 4.0F * var3;
            this.steerAngle = Math.max(this.steerAngle, 0.0F);
         } else {
            this.steerAngle += var6 * 4.0F * var3;
            this.steerAngle = Math.min(this.steerAngle, 0.0F);
         }
      }

      float var13 = var2.getSteeringClamp(this.speed);
      this.steerAngle = PZMath.clamp(this.steerAngle, -var13, var13);
   }

   private void updateTireStats() {
      VehicleScript var1 = this.vehicleObject.getScript();
      int var2 = var1.getWheelCount();
      float var3 = 0.0F;
      float var4 = 0.0F;
      KahluaTableImpl var5 = null;
      KahluaTableImpl var6 = this.bvdBridge();
      if (var6 != null) {
         Object var7 = var6.rawget("tireProfiles");
         if (var7 instanceof KahluaTableImpl) {
            var5 = (KahluaTableImpl)var7;
         }
      }

      float var20 = 0.0F;
      float var8 = 0.0F;
      float var9 = 0.0F;
      float var10 = 0.0F;
      String var11 = "";

      for (int var12 = 0; var12 < var2; var12++) {
         Wheel var13 = var1.getWheel(var12);
         String var14 = "";
         if (var13 != null) {
            VehiclePart var15 = this.vehicleObject.getPartById("Tire" + var13.getId());
            if (var15 != null && var15.getInventoryItem() != null) {
               var3 += var15.getContainerContentAmount() / var15.getContainerCapacity();
               var4 += var15.getCondition() * var15.getInventoryItem().getWheelFriction();
               this.vehicleObject.setTireInflation(var12, 1.0F);
               var14 = this.tireFamilyKeyCached(var15.getInventoryItem().getType());
               if (var11.isEmpty()) {
                  var11 = var14;
               }
            }
         }

         KahluaTableImpl var24 = null;
         if (var5 != null && !var14.isEmpty()) {
            Object var16 = var5.rawget(var14);
            if (var16 instanceof KahluaTableImpl) {
               var24 = (KahluaTableImpl)var16;
            }
         }

         if (var5 != null) {
            var20 += bridgeFieldOr(var24, "road", 1.0F);
            var8 += bridgeFieldOr(var24, "wet", 1.0F);
            var9 += bridgeFieldOr(var24, "snow", 1.0F);
            var10 += bridgeFieldOr(var24, "offroad", 1.0F);
         }
      }

      float var21 = 1.0F;
      float var22 = 1.0F;
      float var23 = 1.0F;
      float var25 = 1.0F;
      if (var2 > 0) {
         var3 /= var2;
         var4 = var4 * 0.01F / var2;
         if (var5 != null) {
            var21 = var20 / var2;
            var22 = var8 / var2;
            var23 = var9 / var2;
            var25 = var10 / var2;
         }
      }

      this.tireFill = var3;
      this.tireWear = var4;
      float var26 = this.tireWear * 0.5F + 0.5F;
      var26 *= this.getSandboxOption("BetterVehicleDynamics.GripLevel", 1.0F);
      if (var5 != null) {
         var26 *= var21;
      }

      float var17 = 1.0F;
      if (ClimateManager.getInstance().getSnowStrength() > 0.5F) {
         float var18 = 1.0F - this.getSandboxOption("BetterVehicleDynamics.SnowGrip", 0.45F);
         var18 = (float)(var18 * (Math.min(Math.max(ClimateManager.getInstance().getSnowStrength(), 0.0F), 5.0F) * 0.2));
         var17 = (1.0F - var18) * var1.getOffroadEfficiency();
         if (var5 != null) {
            var17 *= var23;
         }
      }

      float var30 = 1.0F;
      if (this.vehicleObject.isDoingOffroad()) {
         float var19 = this.getSandboxOption("BetterVehicleDynamics.OffroadGrip", 0.85F);
         var19 = 1.0F - var19;
         var19 *= 0.5F + this.tireFill / 2.0F;
         var30 = (1.0F - var19) * var1.getOffroadEfficiency();
         if (var5 != null) {
            var30 *= var25;
         }

         if (var30 < 0.05F) {
            var30 = 0.05F;
         }
      }

      if (ClimateManager.getInstance().isRaining()) {
         var30 *= this.getSandboxOption("BetterVehicleDynamics.WetGrip", 0.7F);
         if (var5 != null) {
            var30 *= var22;
         }
      }

      float var33 = var26;
      var26 *= Math.min(1.0F, Math.min(var17, var30));
      this.gripFactor = var26;
      this.tireFamilyOut = var11;
      this.gripRoadOut = var33;
      this.gripWetOut = ClimateManager.getInstance().isRaining()
         ? var33 * this.getSandboxOption("BetterVehicleDynamics.WetGrip", 0.7F) * (var5 != null ? var22 : 1.0F)
         : var33;
      this.gripSnowOut = ClimateManager.getInstance().getSnowStrength() > 0.5F ? var33 * Math.min(1.0F, var17) : var33;
      this.gripOffroadOut = this.vehicleObject.isDoingOffroad() ? var33 * Math.min(1.0F, var30) : var33;
   }

   private void applyFriction() {
      VehicleScript var1 = this.vehicleObject.getScript();
      Float var2 = baseWheelFriction.get(var1.getName());
      if (var2 == null) {
         DebugLog.log("[BetterVehicleDynamics] no recorded base wheel friction for " + var1.getName());
      } else {
         float var3 = var2 * this.gripFactor;
         var3 = Math.min(1.8F, var3);
         if (this.getSandboxOptionBoolean("BetterVehicleDynamics.Drift", false)) {
            KahluaTableImpl var4 = this.bvdBridge();
            if (var4 != null && var4.rawgetBool("driftActive")) {
               float var5 = this.getSandboxOption("BetterVehicleDynamics.DriftMinSpeed", 20.0F);
               float var6 = this.getSandboxOption("BetterVehicleDynamics.DriftGrip", 0.35F);
               float var7 = Math.abs(this.vehicleObject.getCurrentSpeedKmHour());
               if (var7 > var5 && Math.abs(this.clientControls.steering) > 0.25F) {
                  var3 *= var6;
                  float var8 = this.getSandboxOption("BetterVehicleDynamics.DriftRotation", 2000.0F);
                  float var9 = -this.clientControls.steering * var8 * (this.vehicleObject.getMass() * 0.001F);
                  Bullet.applyTorqueToVehicle(this.vehicleObject.vehicleId, 0.0F, var9, 0.0F);
               }
            }
         }

         boolean var12 = true;
         if (this.vehicleObject.getDriver() != null) {
            lastDrivenScript = var1.getName();
         } else if (lastDrivenScript.equals(var1.getName())) {
            var12 = false;
         }

         if (var12) {
            Float var13 = lastAppliedFriction.get(var1.getName());
            if (var13 != null && Math.abs(var13 - var3) < 1.0E-4F) {
               return;
            }

            try {
               var1.Load(var1.getName(), "{ wheelFriction = " + var3 + ", }");
               var1.toBullet();
               lastAppliedFriction.put(var1.getName(), var3);
            } catch (Exception var10) {
               DebugLog.log("[BetterVehicleDynamics] failed to push wheelFriction to script");
            }
         }
      }
   }

   private void applyDrag(float var1) {
      VehicleScript var2 = this.vehicleObject.getScript();
      this.applyFriction();
      float var3 = Math.abs(this.vehicleObject.getCurrentSpeedKmHour() * BaseVehicle.getFakeSpeedModifier());
      float var4 = var3 * var3 * 0.05F;
      var4 *= this.getSandboxOption("BetterVehicleDynamics.Drag", 1.0F);
      if (var2.getMechanicType() == 3) {
         var4 *= 0.7F;
      } else if (var2.getMechanicType() == 2) {
         var4 *= 1.5F;
      }

      if (this.vehicleObject.isDoingOffroad()) {
         float var5 = var2.getOffroadEfficiency();
         if (this.vehicleObject.isInForest()) {
            var5 = (float)(var5 - 0.2);
         }

         float var6 = this.getSandboxOption("BetterVehicleDynamics.RollResistanceOffroad", 1.8F) * 0.2F;
         var6 += 0.005F * var3;
         var6 *= this.vehicleObject.getMass() * (1.0F + this.tireFill * 0.6F) / var5;
         var4 += var6;
      } else {
         float var9 = this.getSandboxOption("BetterVehicleDynamics.RollResistance", 1.0F) * 0.05F;
         var9 += 0.001F * var3;
         var9 *= this.vehicleObject.getMass() * (2.0F - this.tireFill);
         var4 += var9;
      }

      Vector3f var12 = this.dragDir.set(this.vehicleObject.jniLinearVelocity);
      if (var12.lengthSquared() > 0.1) {
         var12.normalize();
      }

      var12.mul(var4 * WorldSimulation.instance.massScaler * var1 * -200.0F);
      Bullet.applyCentralForceToVehicle(this.vehicleObject.vehicleId, var12.x, var12.y, var12.z);
   }

   private void updateIdle(float var1, VehiclePart var2) {
      this.idleRpmRetimer -= var1;
      if (this.idleRpmRetimer <= 0.0F) {
         this.idleRpmRetimer = 0.1F;
         if (var2 != null) {
            this.idleRpmTarget = Rand.Next(650, 700 + (200 - var2.getCondition() * 2));
         }
      }

      float var3 = (float)this.vehicleObject.getEngineSpeed();
      if (var3 < this.idleRpmTarget) {
         float var4 = Math.min(Math.max((this.idleRpmTarget - var3) * 0.01F, 0.0F), 0.3F);
         this.vehicleObject.throttle = Math.max(var4, this.vehicleObject.throttle);
      }
   }

   private void updateAudio(float var1, VehicleSpec var2) {
      if (!GameServer.server) {
         if (var2 != null && var2.engineSound != null && !var2.engineSound.isEmpty()) {
            EngineSpec var3 = engineSpecs.get(var2.engineSound);
            if (var3 != null) {
               BaseSoundEmitter var4 = this.vehicleObject.getEmitter();
               if (this.vehicleObject.getEngineState() == engineStateTypes.Starting && !var4.isPlaying(this.crankSoundId)) {
                  this.crankSoundId = var4.playSoundImpl(var3.crankSound, (IsoObject)null);
               }

               if (this.vehicleObject.getEngineState() != engineStateTypes.Starting && var4.isPlaying(this.crankSoundId)) {
                  var4.stopSound(this.crankSoundId);
               }

               if (this.vehicleObject.getEngineState() == engineStateTypes.StartingSuccess && !var4.isPlaying(this.startSoundId)) {
                  this.startSoundId = var4.playSoundImpl(var3.startSound, (IsoObject)null);
                  this.startupBlend = 0.0F;
               }

               float var5 = (float)this.vehicleObject.getEngineSpeed() / var3.engineSoundRpm;
               var5 = (var5 + 0.4F) / 1.4F;
               BaseSoundEmitter var6 = this.loopEmitter;
               if (this.loopEngineId == 0L && this.vehicleObject.isEngineRunning()) {
                  if (var6 != null) {
                     var6.stopAll();
                  }

                  this.loopEmitter = IsoWorld.instance.getFreeEmitter(this.vehicleObject.getX(), this.vehicleObject.getY(), (int)this.vehicleObject.getZ());
                  var6 = this.loopEmitter;
                  this.loopEngineId = var6.playSoundImpl(var3.engineSound, (IsoObject)null);
                  if (!var3.exhaustSound.isEmpty()) {
                     this.loopExhaustId = var6.playSoundImpl(var3.exhaustSound, (IsoObject)null);
                  }
               }

               if (this.loopEngineId != 0L && !this.vehicleObject.isEngineRunning() && var6 != null) {
                  var6.stopSound(this.loopEngineId);
                  this.loopEngineId = 0L;
                  var6.stopSound(this.loopExhaustId);
                  this.loopExhaustId = 0L;
               }

               float var7 = Core.getInstance().getOptionVehicleEngineVolume() / 10.0F;
               if (this.vehicleObject.isEngineRunning()) {
                  this.startupBlend = Math.min(1.0F, this.startupBlend + var1 * 1.0F);
               }

               KahluaTableImpl var8 = this.bvdBridge();
               float var9 = 0.4F;
               if (var8 != null) {
                  var9 = var8.rawgetFloat("EngineOverhaulVolume");
               }

               if (this.startSoundId != 0L && var4.isPlaying(this.startSoundId)) {
                  var4.setVolume(this.startSoundId, (1.0F - this.startupBlend) * var7 * var9);
               }

               if (this.crankSoundId != 0L && var4.isPlaying(this.crankSoundId)) {
                  var4.setVolume(this.crankSoundId, var7 * var9);
               }

               if (this.loopEngineId != 0L) {
                  this.loopEmitter.setPos(this.vehicleObject.getX(), this.vehicleObject.getY(), this.vehicleObject.getZ());
                  var6.setPitch(this.loopEngineId, var5);
                  var6.setPitch(this.loopExhaustId, var5);
                  var6.setVolume(this.loopEngineId, (this.vehicleObject.throttle * 0.2F + 0.5F) * var7 * this.startupBlend * var9);
                  var6.setVolume(this.loopExhaustId, (this.vehicleObject.throttle * 0.8F + 0.2F) * var7 * this.startupBlend * var9);
               }
            }
         }
      }
   }

   public void update() {
      if (this.vehicleObject.getEngine() == null) {
         this.vehicleObject.transmissionNumber = TransmissionNumber.N;
         this.engineForce = 0.0F;
         this.brakingForce = 10.0F;
         if (!GameServer.server && this.vehicleObject.getScript().getWheelCount() > 0) {
            Bullet.controlVehicle(this.vehicleObject.vehicleId, 0.0F, this.brakingForce, this.steerAngle);
         }
      } else {
         this.loadLUATables();
         this.updateModOptions();
         float var1 = GameTime.getInstance().getRealworldSecondsSinceLastUpdate();
         VehicleSpec var2 = vehicleSpecs.get(this.vehicleObject.getScript().getFullType());
         this.updateAudio(var1, var2);
         if (this.vehicleObject.getVehicleTowedBy() == null) {
            VehiclePart var3 = this.vehicleObject.getPartById("Engine");
            ItemContainer var4 = null;
            if (var3 != null) {
               var4 = var3.getItemContainer();
            }

            KahluaTableImpl var5 = this.bvdBridge();
            VehicleScript var6 = this.vehicleObject.getScript();
            this.updateControlsCalculation(var4);
            float var7 = Math.min(120.0F, this.vehicleObject.getMaxSpeed());
            float var8 = 4500.0F;
            float var9 = 4350.0F;
            if (var6.getEngineRPMType().equals("firebird")) {
               var8 = 6000.0F;
               var9 = 5800.0F;
            }

            var7 /= var8;
            float var10 = 0.95F / var7;
            float var11 = Math.abs(this.vehicleObject.getCurrentSpeedKmHour() * BaseVehicle.getFakeSpeedModifier()) * var10;
            GearboxSpec var12 = DEFAULT_GEARBOX_4;
            int var13 = var6.getGearRatioCount();
            if (var13 == 3) {
               var12 = DEFAULT_GEARBOX_3;
            } else if (var13 == 5) {
               var12 = DEFAULT_GEARBOX_5;
            }

            InventoryItem var14 = null;
            InventoryItem var15 = null;
            if (var4 != null) {
               var14 = var4.getFirstTag(tagGearbox);
               var15 = var4.getFirstTag(tagConverter);
            }

            if (var14 != null) {
               String var16 = var14.getFullType();
               var16 = var16.substring(0, var16.length() - 2);
               GearboxSpec var17 = gearboxSpecs.get(var16);
               if (var17 != null) {
                  var12 = var17;
               }
            }

            ConverterSpec var48 = DEFAULT_CONVERTER;
            if (var15 != null) {
               String var49 = var15.getFullType();
               var49 = var49.substring(0, var49.length() - 2);
               ConverterSpec var18 = converterSpecs.get(var49);
               if (var18 != null) {
                  var48 = var18;
               }
            }

            int var51;
            for (var51 = 1; var51 < var12.gearCount; var51++) {
               float var52 = this.heldGear > var51 ? 500.0F : 0.0F;
               if (var11 * var12.ratios[var51] < var9 - var52) {
                  break;
               }
            }

            this.heldGear = var51;
            if (var51 >= 1 && var51 < var12.gearCount && var11 * var12.ratios[var51 + 1] > var9 * 0.5 && this.overrev < 0.5F) {
               var51++;
            }

            if (this.manualGearbox && var5 != null) {
               var51 = var5.rawgetInt("forceGear");
               if (this.shiftDownEdge == 1) {
                  this.shiftDownEdge = 2;
                  var51--;
               }

               if (this.shiftUpEdge == 1) {
                  this.shiftUpEdge = 2;
                  var51++;
               }

               if (this.autoReverseEnabled) {
                  if (var51 < 1) {
                     var51 = 1;
                  }
               } else if (var51 < 0) {
                  var51 = 0;
               }

               if (var51 > var12.gearCount) {
                  var51 = var12.gearCount;
               }

               var5.rawset("forceGear", Integer.valueOf(var51).doubleValue());
               if (this.reversing) {
                  var5.rawset("forceGear", Integer.valueOf(1).doubleValue());
               }
            }

            if (this.reversing) {
               var51 = 0;
            }

            var11 *= var12.ratios[var51];
            this.vehicleObject.transmissionNumber = GEAR_LABELS[var51];
            float var53 = this.vehicleObject.getEnginePower() / 10.0F;
            boolean var19 = this.getSandboxOptionBoolean("BetterVehicleDynamics.RealismHPWeight", false);
            if (var2 != null && var2.realismApplied && var19) {
               var53 *= 4.0F;
            }

            float var20 = (float)this.vehicleObject.getEngineSpeed();
            float var21 = 0.0F;
            float var22 = this.getSandboxOption("BetterVehicleDynamics.EnginePower", 1.0F);
            if (var22 > 5.0F) {
               var22 = 5.0F;
            }

            float var23 = var53 * var22 * 4500.0F / var8;
            float var24 = var23 * 1.0E-4F;
            if (var4 != null) {
               InventoryItem var25 = var4.getFirstTag(tagFlywheel);
               if (var25 != null) {
                  float var26 = var25.getScriptItem().getMaxItemSize();
                  if (var26 > 0.0F) {
                     var24 /= var26;
                  }
               }
            }

            var24 = Math.max(0.001F, var24);
            if (this.vehicleObject.isEngineRunning()) {
               if (var20 < 400.0F) {
                  var20 = 400.0F;
                  this.vehicleObject.setEngineSpeed(var20);
               }

               this.updateIdle(var1, var3);
               float var57 = this.vehicleObject.getCurrentSpeedKmHour() * BaseVehicle.getFakeSpeedModifier();
               float var63 = this.getSandboxOption("BetterVehicleDynamics.ReverseTopSpeed", 25.0F);
               if (this.reversing && var57 < -var63) {
                  this.vehicleObject.throttle = Math.min(this.vehicleObject.throttle, (var57 + var63 + 5.0F) / 5.0F);
               }

               float var27 = Math.min(Math.max(1.0F - (var20 - var8) / 1000.0F, 0.0F), 1.0F);
               var27 = (float)(var27 * Math.min(Math.max(var20 / var8 * 2.0F, 0.2), 1.0));
               var21 = this.vehicleObject.throttle * var27 * var23;
               var21 = (float)(var21 - var23 * 0.35F * this.vehicleObject.getEngineSpeed() / var8);
            }

            float var58 = Math.max(0.0F, Math.min(3.0F, (var20 - (var48.lockupRpm - var48.lockupRange)) / var48.lockupRange));
            float var64 = this.drivenWheelSpeed * var12.ratios[var51] * var10;
            if (var51 == 0) {
               var64 = -var64;
            }

            var64 = Math.max(0.0F, var64);
            float var67 = var64 / Math.max(var20, 1.0F);
            var67 = Math.min(var67, 1.0F);
            var58 -= var67;
            var58 *= Math.max(0.0F, Math.min(1.0F, (1.0F - var67) * 5.0F));
            float var28 = 1.2F;
            if (var4 != null) {
               var28 = 0.0F;
               if (var14 != null) {
                  var28 = Math.min(1.2F, var14.getCondition() / 50.0F);
                  var28 = (float)(var28 * Math.min(1.0, var14.getFluidContainer().getFilledRatio() * 2.0));
               }

               if (var15 != null) {
                  var28 *= Math.min(1.0F, var15.getCondition() / 50.0F);
               } else {
                  var28 = 0.0F;
               }
            }

            var58 *= var28;
            var58 = Math.min(Math.max(var58, 0.0F), 1.2F);
            float var29 = Math.min(Math.max(var67 * 1.1F, 0.0F), 1.0F);
            var29 = 1.0F - var29;
            float var30 = this.getSandboxOption("BetterVehicleDynamics.LowSpeedGrunt", 2.5F);
            var29 = var29 * (var30 - 1.0F) + 1.0F;
            float var31 = var23 * var58 * var29 * var12.ratios[var51] * var10 * 0.05F;
            float var32 = var6.getMass();
            KahluaTableImpl var33 = this.bvdBridge();
            if (var33 != null) {
               Object var34 = var33.rawget("vehicleData");
               if (var34 instanceof KahluaTableImpl) {
                  Object var35 = ((KahluaTableImpl)var34).rawget(var6.getFullType());
                  if (var35 instanceof KahluaTableImpl) {
                     float var36 = bridgeFieldOr((KahluaTableImpl)var35, "mass_kg", -1.0F);
                     if (var36 > 0.0F) {
                        var32 = var36;
                     }
                  }
               }
            }

            float var74 = this.vehicleObject.getMass() / Math.max(1.0F, var32);
            var74 = Math.min(Math.max(var74, 1.0F), 3.0F);
            this.ladenRatioOut = var74;
            float var76 = 0.0F;
            Object var79 = var33 == null ? null : var33.rawget("loadResponse");
            KahluaTableImpl var37 = var79 instanceof KahluaTableImpl ? (KahluaTableImpl)var79 : null;
            if (var37 != null && var37.rawgetBool("enabled")) {
               float var38 = bridgeFieldOr(var37, "threshold", 1.1F);
               if (var74 > var38) {
                  float var39 = bridgeFieldOr(var37, "fullAt", 1.2F);
                  float var40 = var39 - var38;
                  float var41 = var40 <= 0.0F ? 1.0F : Math.min(Math.max((var74 - var38) / var40, 0.0F), 1.0F);
                  var76 = bridgeFieldOr(var37, "maxPenalty", 0.0F) * var41;
                  float var42 = bridgeFieldOr(var37, "easeBySpeedKmh", 0.0F);
                  float var43 = Math.abs(this.vehicleObject.getCurrentSpeedKmHour());
                  float var44 = var42 <= 0.0F ? 0.0F : Math.min(Math.max(1.0F - var43 / var42, 0.0F), 1.0F);
                  var76 *= var44;
                  var76 = Math.max(0.0F, Math.min(1.0F, var76));
               }
            }

            this.loadPenaltyOut = var76;
            float var80 = this.vehicleObject.getMass() * 2.0F * this.gripFactor * this.getSandboxOption("BetterVehicleDynamics.GripLevel", 1.0F);
            float var81 = 1.0F;
            float var82 = 50.0F;
            var31 -= this.brakingForce * var82;
            this.burnout = 0.0F;
            float var83 = this.vehicleObject.getCurrentSpeedKmHour() * BaseVehicle.getFakeSpeedModifier();
            if (var51 == 0) {
               this.drivenWheelSpeed -= (var31 - var80) * var1 * 0.02F;
               this.drivenWheelSpeed = Math.min(this.drivenWheelSpeed, var83);
               this.burnout = Math.max(var83 - this.drivenWheelSpeed - 3.0F, 0.0F);
            } else {
               this.drivenWheelSpeed += (var31 - var80) * var1 * 0.02F;
               this.drivenWheelSpeed = Math.max(this.drivenWheelSpeed, var83);
               this.burnout = Math.max(this.drivenWheelSpeed - var83 - 3.0F, 0.0F);
            }

            if (var31 > var80) {
               var81 = var80 / Math.max(1.0F, var31);
               var31 = var80;
            }

            if (var51 == 0) {
               this.burnout = -this.burnout;
            }

            if (GameClient.client && this.vehicleObject.getCurrentSpeedKmHour() >= ServerOptions.instance.speedLimit.getValue()) {
               var31 = 0.0F;
            }

            this.updateTireStats();
            this.publishComputedState();
            this.bvdPruneSkidMarks();
            this.bvdStabilityGuard();
            this.applyDrag(var1);
            var21 -= var23 * var58 * var81;
            if (var31 < 0.0F) {
               this.brakingForce = -var31 / var82;
               var31 = 0.0F;
            }

            this.engineForce = var31;
            this.engineForce *= 1.0F - var76;
            this.vehicleObject.addEngineSpeed(var21 / var24 * var1);
            this.vehicleObject.setEngineSpeed(Math.max(200.0, this.vehicleObject.getEngineSpeed()));
            if (this.vehicleObject.getEngineSpeed() != this.vehicleObject.getEngineSpeed()) {
               this.vehicleObject.setEngineSpeed(200.0);
            }

            if (this.engineForce != this.engineForce) {
               this.engineForce = 0.0F;
            }

            if (var51 == 0) {
               this.engineForce = -this.engineForce;
            }

            this.updateSkidding(false);
            if (this.tunableSteering) {
               this.updateSteeringTunable(var4, var6, var1);
            } else {
               this.updateSteeringStock(var6);
            }

            BulletVariables var84 = bulletVariables.set(this.vehicleObject, this.engineForce, this.brakingForce, this.steerAngle);
            this.checkTire(var84);
            this.engineForce = var84.engineForce;
            this.brakingForce = var84.brakingForce;
            this.steerAngle = var84.vehicleSteering;
            this.vehicleObject.setCurrentSteering(this.steerAngle);
            this.vehicleObject.setBraking(this.isBreak);
            if (!GameServer.server) {
               this.checkShouldBeActive();
               Bullet.controlVehicle(this.vehicleObject.vehicleId, this.engineForce * WorldSimulation.instance.massScaler, this.brakingForce, this.steerAngle);
               boolean var85 = this.getSandboxOptionBoolean("BetterVehicleDynamics.ThrottleStart", true);
               if ((this.isGas || this.isGasR) && this.vehicleObject.getEngineState() == engineStateTypes.Idle && !this.engineStartingFromKeyboard && var85) {
                  this.engineStartingFromKeyboard = true;
                  if (GameClient.client) {
                     Boolean var87 = this.vehicleObject.getDriver().getInventory().haveThisKeyId(this.vehicleObject.getKeyId()) != null
                        ? Boolean.TRUE
                        : Boolean.FALSE;
                     GameClient.instance
                        .sendClientCommandV((IsoPlayer)this.vehicleObject.getDriver(), "vehicle", "startEngine", new Object[]{"haveKey", var87});
                  } else if (!GameClient.client && !GameServer.server) {
                     Boolean var86 = this.vehicleObject.getDriver().getInventory().haveThisKeyId(this.vehicleObject.getKeyId()) != null
                        ? Boolean.TRUE
                        : Boolean.FALSE;
                     this.vehicleObject.tryStartEngine(var86);
                  } else {
                     this.vehicleObject.tryStartEngine();
                  }
               }

               if (this.engineStartingFromKeyboard && !this.isGas && !this.isGasR) {
                  this.engineStartingFromKeyboard = false;
               }
            }

            if (this.vehicleObject.getEngineState() != engineStateTypes.Running) {
               this.acceleratorOn = false;
               if (!GameServer.server && this.vehicleObject.getCurrentSpeedKmHour() > 5.0F && var6.getWheelCount() > 0) {
                  Bullet.controlVehicle(this.vehicleObject.vehicleId, 0.0F, this.brakingForce, this.steerAngle);
               } else {
                  this.park();
               }
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
               this.steerAngle = 0.0F;
               boolean var2 = this.vehicleObject.getScriptName().contains("Trailer");
               float var3 = GameTime.getInstance().getRealworldSecondsSinceLastUpdate();
               this.updateTireStats();
               this.applyDrag(var3);
               if (!var2) {
                  this.steerAngle = 0.0F;
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
                  this.steerAngle = PZMath.clamp(var6 * 0.3F, -var8, var8);
                  if (this.vehicleObject.getCurrentSpeedKmHour() < 1.0F) {
                     this.steerAngle = 0.0F;
                  }

                  boolean var9 = this.getSandboxOptionBoolean("BetterVehicleDynamics.KeylessTow", true);
                  if (!(this.vehicleObject.isKeysInIgnition() | this.vehicleObject.isHotwired() | var9)) {
                     this.brakingForce = 500.0F;
                  }

                  this.updateSkidding(true);
               }

               Bullet.controlVehicle(this.vehicleObject.vehicleId, this.engineForce, this.brakingForce, this.steerAngle);
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
      boolean var2 = false;
      float var3 = 0.5F;
      KahluaTableImpl var4 = this.bvdBridge();
      if (var4 != null) {
         var2 = var4.rawgetBool("towSkidding");
         var3 = var4.rawgetFloat("skidVolume");
      }

      var3 *= Core.getInstance().getOptionSoundVolume() / 10.0F;
      var3 = Math.max(0.0F, Math.min(1.0F, var3));
      if ((!var1 || var2) && this.vehicleObject.getScript().getMechanicType() != 4) {
         float var5 = GameTime.getInstance().getRealworldSecondsSinceLastUpdate();
         float var6 = 0.0F;

         for (int var7 = 2; var7 < 4; var7++) {
            var6 = (float)(var6 + Math.min(Math.max(1.0F - this.vehicleObject.wheelInfo[var7].skidInfo - 0.3, 0.0), 0.5));
         }

         var6 += Math.abs(this.burnout * 0.1F);
         var6 = Math.max(Math.min(var6, 1.0F), 0.0F);
         float[] var17 = this.skidSpinDelta;
         var17[0] = this.vehicleObject.wheelInfo[2].rotation - this.rearSpinAngleLast[0];
         var17[1] = this.vehicleObject.wheelInfo[3].rotation - this.rearSpinAngleLast[1];
         this.rearSpinAngleLast[0] = this.vehicleObject.wheelInfo[2].rotation;
         this.rearSpinAngleLast[1] = this.vehicleObject.wheelInfo[3].rotation;
         boolean var8 = this.clientControls.brake;
         BaseVehicle var9 = this.vehicleObject.getVehicleTowedBy();
         if (var9 != null) {
            var8 = this.brakingForce > 1.0F;
         }

         if (var6 > 0.2 && var8) {
            var17[0] = 0.0F;
            var17[1] = 0.0F;
         } else {
            var17[0] += this.burnout * var5 * 1.0F;
            var17[1] += this.burnout * var5 * 1.0F;
         }

         this.rearSpinAngle[0] = this.rearSpinAngle[0] + var17[0];
         this.rearSpinAngle[1] = this.rearSpinAngle[1] + var17[1];

         for (int var10 = 2; var10 < 4; var10++) {
            this.vehicleObject.wheelInfo[var10].rotation = this.rearSpinAngle[var10 - 2];
            if (this.rearSpinAngle[var10 - 2] > 1000.0F) {
               this.rearSpinAngle[var10 - 2] = 0.0F;
            }

            if (this.rearSpinAngle[var10 - 2] < -1000.0F) {
               this.rearSpinAngle[var10 - 2] = 0.0F;
            }
         }

         if (this.vehicleObject.isEngineRunning() || var9 != null && var6 > 0.2) {
            boolean var18 = this.vehicleObject.isDoingOffroad();
            var18 |= ClimateManager.getInstance().getSnowStrength() > 0.5F;
            if (this.offroadSkidLast != var18) {
               this.offroadSkidLast = var18;
               if (this.vehicleObject.ramSound != 0L) {
                  this.vehicleObject.stopSound(this.vehicleObject.ramSound);
                  this.vehicleObject.ramSound = 0L;
               }
            }

            if (this.vehicleObject.ramSound == 0L) {
               boolean var11 = this.getSandboxOptionBoolean("BetterVehicleDynamics.SkidSound", true);
               String var12 = var11 ? "BVD_SkidMP" : "VehicleSkid";
               this.vehicleObject.ramSound = this.vehicleObject.playSoundImpl(var12, (IsoObject)null);
            }

            if (var1) {
               var3 = (float)(var3 * 0.8);
            }

            float var20 = Math.min(1.0F, (float)Math.sqrt(Math.min(1.0F, var6)) * var3 * 2.5F);
            this.vehicleObject.getEmitter().setVolume(this.vehicleObject.ramSound, var20);
         } else if (this.vehicleObject.ramSound != 0L) {
            this.vehicleObject.stopSound(this.vehicleObject.ramSound);
            this.vehicleObject.ramSound = 0L;
         }
      } else if (this.vehicleObject.ramSound != 0L) {
         this.vehicleObject.stopSound(this.vehicleObject.ramSound);
         this.vehicleObject.ramSound = 0L;
      }
   }

   public float getVehicleSteering() {
      return this.steerAngle;
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

   public void control_NoControl() {
      if (this.vehicleObject.getEngine() == null) {
         this.vehicleObject.transmissionNumber = TransmissionNumber.N;
         this.engineForce = 0.0F;
         this.brakingForce = 10.0F;
      } else {
         float var1 = GameTime.getInstance().getMultiplier() / 0.8F;
         if (!this.vehicleObject.isEngineRunning()) {
            if (this.vehicleObject.getEngineSpeed() > 0.0) {
               this.vehicleObject.setEngineSpeed(Math.max(this.vehicleObject.getEngineSpeed() - 50.0F * var1, 0.0));
            }
         } else if (this.vehicleObject.getEngineSpeed() > this.vehicleObject.getScript().getEngineIdleSpeed()) {
            if (!this.vehicleObject.isRegulator()) {
               this.vehicleObject.addEngineSpeed(-20.0F * var1);
            }
         } else {
            this.vehicleObject.addEngineSpeed(20.0F * var1);
         }

         if (!this.vehicleObject.isRegulator()) {
            this.vehicleObject.transmissionNumber = TransmissionNumber.N;
         }

         this.engineForce = 0.0F;
         if (this.vehicleObject.getEngineSpeed() > 1000.0) {
            this.brakingForce = 15.0F;
         } else {
            this.brakingForce = 10.0F;
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

         this.pedalGas = 0.0F;
         this.pedalBrake = 0.0F;
         int var7 = this.vehicleObject.getJoypad();
         if (var7 != -1) {
            boolean var8 = JoypadManager.instance.isRTPressed(var7);
            boolean var10 = JoypadManager.instance.isLTPressed(var7);
            boolean var11 = JoypadManager.instance.isBPressed(var7);
            this.clientControls.steering = JoypadManager.instance.getMovementAxisX(var7);
            this.clientControls.forward = var8;
            this.clientControls.backward = var10;
            this.clientControls.brake = var11;
            if (this.shiftDownEdge == 0) {
               this.shiftDownEdge = JoypadManager.instance.isLBPressed(var7) ? 1 : 0;
            } else if (this.shiftDownEdge == 2) {
               this.shiftDownEdge = JoypadManager.instance.isLBPressed(var7) ? 2 : 0;
            }

            if (this.shiftUpEdge == 0) {
               this.shiftUpEdge = JoypadManager.instance.isRBPressed(var7) ? 1 : 0;
            } else if (this.shiftUpEdge == 2) {
               this.shiftUpEdge = JoypadManager.instance.isRBPressed(var7) ? 2 : 0;
            }

            if (this.joystickPedalAxis) {
               if (JoypadManager.instance.getMovementAxisY(var7) < 0.0F) {
                  this.pedalGas = -JoypadManager.instance.getMovementAxisY(var7);
               } else {
                  this.pedalBrake = JoypadManager.instance.getMovementAxisY(var7);
               }
            } else {
               byte var12 = 5;
               if (GameWindow.GameInput.getAxisCount(var7) > var12) {
                  this.pedalGas = GameWindow.GameInput.getAxisValue(var7, var12) / 2.0F + 0.5F;
               }

               byte var13 = 4;
               if (GameWindow.GameInput.getAxisCount(var7) > var13) {
                  this.pedalBrake = GameWindow.GameInput.getAxisValue(var7, var13) / 2.0F + 0.5F;
               }
            }
         }

         if (this.clientControls.forceBrake != 0L) {
            long var9 = System.currentTimeMillis() - this.clientControls.forceBrake;
            if (var9 > 0L && var9 < 1000L) {
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
         if (this.vehicleObject.getEngine() != null) {
            this.vehicleObject.addEngineSpeed((this.vehicleObject.throttle - 0.1F) * var1 * 5000.0F);
         }
      }

      VehicleSpec var3 = vehicleSpecs.get(this.vehicleObject.getScript().getFullType());
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
         this.vehicleObject.getWorldTransform(this.tempXfrm);
         VehiclePoly var4 = this.vehicleObject.getPoly();
         LineDrawer.addLine(var4.x1, var4.y1, var2, var4.x2, var4.y2, var2, 1.0F, 1.0F, 1.0F, null, true);
         LineDrawer.addLine(var4.x2, var4.y2, var2, var4.x3, var4.y3, var2, 1.0F, 1.0F, 1.0F, null, true);
         LineDrawer.addLine(var4.x3, var4.y3, var2, var4.x4, var4.y4, var2, 1.0F, 1.0F, 1.0F, null, true);
         LineDrawer.addLine(var4.x4, var4.y4, var2, var4.x1, var4.y1, var2, 1.0F, 1.0F, 1.0F, null, true);
         Vector2f var5 = BaseVehicle.allocVector2f();
         float var6 = IsoCamera.frameState.camCharacterX;
         float var7 = IsoCamera.frameState.camCharacterY;
         this.vehicleObject.getClosestPointOnPoly(var6, var7, var5);
         if (this.vehicleObject.isPointLeftOfCenter(var5.x, var5.y)) {
            this.drawCircle(var5.x, var5.y, 0.05F, 0.0F, 1.0F, 0.0F, 1.0F);
         } else {
            this.drawCircle(var5.x, var5.y, 0.05F, 0.0F, 0.0F, 1.0F, 1.0F);
         }

         BaseVehicle.releaseVector2f(var5);
         _UNIT_Y.set(0.0F, 1.0F, 0.0F);

         for (int var8 = 0; var8 < this.vehicleObject.getScript().getWheelCount(); var8++) {
            Wheel var9 = var1.getWheel(var8);
            this.tempVec3f.set(var9.getOffset());
            if (var1.getModel() != null) {
               this.tempVec3f.add(var1.getModelOffset());
            }

            this.vehicleObject.getWorldPos(this.tempVec3f, this.tempVec3f);
            float var10 = this.tempVec3f.x;
            float var11 = this.tempVec3f.y;
            this.vehicleObject.getWheelForwardVector(var8, this.tempVec3f);
            LineDrawer.addLine(var10, var11, var2, var10 + this.tempVec3f.x, var11 + this.tempVec3f.z, var2, 1.0F, 1.0F, 1.0F, null, true);
            this.drawRect(this.tempVec3f, var10 - WorldSimulation.instance.offsetX, var11 - WorldSimulation.instance.offsetY, var9.width, var9.radius);
         }

         if (this.vehicleObject.collideX != -1.0F) {
            this.vehicleObject.getForwardVector(var3);
            this.drawCircle(this.vehicleObject.collideX, this.vehicleObject.collideY, 0.3F);
            this.vehicleObject.collideX = -1.0F;
            this.vehicleObject.collideY = -1.0F;
         }

         int var12 = this.vehicleObject.getJoypad();
         if (var12 != -1) {
            Vector2 var13 = JoypadManager.instance.getMovementAxis(var12, this.tempVec2);
            if (var13.getLengthSquared() > 1.0E-4F) {
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

         float var14 = this.vehicleObject.getX();
         float var15 = this.vehicleObject.getY();
         float var16 = this.vehicleObject.getZ();
         LineDrawer.addLine(var14 - 0.5F, var15, var16, var14 + 0.5F, var15, var16, 1.0F, 1.0F, 1.0F, null, true);
         LineDrawer.addLine(var14, var15 - 0.5F, var16, var14, var15 + 0.5F, var16, 1.0F, 1.0F, 1.0F, null, true);
         this.renderClosestPointToOtherVehicle();
      }
   }

   private void renderClosestPointToOtherVehicle() {
      BaseVehicle var1 = null;
      float var2 = Float.MAX_VALUE;

      for (BaseVehicle var4 : IsoWorld.instance.currentCell.getVehicles()) {
         if (var4 != this.vehicleObject) {
            float var5 = IsoUtils.DistanceToSquared(this.vehicleObject.getX(), this.vehicleObject.getY(), var4.getX(), var4.getY());
            if (var5 < var2) {
               var2 = var5;
               var1 = var4;
            }
         }
      }

      if (var1 != null && !(var2 > 100.0F)) {
         Vector2f var7 = BaseVehicle.allocVector2f();
         Vector2f var8 = BaseVehicle.allocVector2f();
         var2 = this.vehicleObject.getClosestPointOnPoly(var1, var7, var8);
         if (var2 == 0.0F) {
            LineDrawer.addRect(var7.x, var7.y, this.vehicleObject.getZ(), 0.05F, 0.05F, 0.0F, 1.0F, 1.0F);
         } else {
            LineDrawer.addLine(var7.x, var7.y, this.vehicleObject.getZ(), var8.x, var8.y, var1.getZ(), 0.0F, 1.0F, 1.0F, 1.0F);
         }

         BaseVehicle.releaseVector2f(var7);
         BaseVehicle.releaseVector2f(var8);
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

   static {
      gears[0] = new GearInfo(0, 25, 0.0F);
      gears[1] = new GearInfo(25, 50, 0.5F);
      gears[2] = new GearInfo(50, 1000, 0.5F);
   }
}
