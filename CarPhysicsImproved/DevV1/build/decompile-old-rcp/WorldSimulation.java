package zombie.core.physics;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import zombie.GameTime;
import zombie.SandboxOptions;
import zombie.SandboxOptions.SandboxOption;
import zombie.characters.IsoPlayer;
import zombie.core.physics.WorldSimulation.s_performance;
import zombie.core.profiling.AbstractPerformanceProfileProbe;
import zombie.core.textures.TextureDraw.GenericDrawer;
import zombie.debug.DebugLog;
import zombie.debug.LogSeverity;
import zombie.iso.IsoChunkMap;
import zombie.iso.IsoMetaGrid;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoWorld;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.network.PacketTypes.PacketType;
import zombie.network.packets.INetworkPacket;
import zombie.scripting.ScriptManager;
import zombie.scripting.objects.VehicleScript;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehicleManager;
import zombie.vehicles.BaseVehicle.Authorization;

public final class WorldSimulation {
   public static WorldSimulation instance = new WorldSimulation();
   public HashMap<Integer, IsoMovingObject> physicsObjectMap = new HashMap<>();
   public boolean created = false;
   public float offsetX = 0.0F;
   public float offsetY = 0.0F;
   public long time;
   private final ArrayList<BaseVehicle> collideVehicles = new ArrayList<>(4);
   private final Vector3f tempVector3f = new Vector3f();
   private final Vector3f tempVector3f2 = new Vector3f();
   private final Transform tempTransform = new Transform();
   private final Quaternionf javaxQuat4f = new Quaternionf();
   private final float[] ff = new float[8192];
   private final float[] wheelSteer = new float[4];
   private final float[] wheelRotation = new float[4];
   private final float[] wheelSkidInfo = new float[4];
   private final float[] wheelSuspensionLength = new float[4];
   private float localTime = 0.0F;
   public float periodSec = 0.0F;
   private int bulletFrameNo = -1;
   public float massScaler = 1.0F;
   private Field fieldImpulseFromHitZombie = null;
   private Field fieldImpulseFromSquishedZombie = null;
   private Method ImpulseClearMethod = null;
   private Method ImpulseSizeMethod = null;
   private Method ImpulseGetMethod = null;
   private Field impulseField = null;
   private Field impulseEnabledField = null;

   private float getSandboxOption(String var1, float var2) {
      SandboxOption var3 = SandboxOptions.instance.getOptionByName(var1);
      return var3 == null ? var2 : (float)Double.parseDouble(var3.asConfigOption().getValueAsString());
   }

   private void reflectionInit() {
      try {
         Class var1 = Class.forName("zombie.vehicles.BaseVehicle");
         this.fieldImpulseFromHitZombie = var1.getDeclaredField("impulsesFromHitObjects");
         this.fieldImpulseFromHitZombie.setAccessible(true);
         this.fieldImpulseFromSquishedZombie = var1.getDeclaredField("impulsesFromSquishedBodies");
         this.fieldImpulseFromSquishedZombie.setAccessible(true);
         Class var2 = Class.forName("java.util.ArrayList");
         Class[] var3 = new Class[0];
         this.ImpulseClearMethod = var2.getMethod("clear", var3);
         this.ImpulseSizeMethod = var2.getMethod("size", var3);
         Class[] var4 = new Class[]{int.class};
         this.ImpulseGetMethod = var2.getMethod("get", var4);
         Class var5 = Class.forName("zombie.vehicles.BaseVehicle$VehicleImpulse");
         this.impulseField = var5.getDeclaredField("impulse");
         this.impulseField.setAccessible(true);
         this.impulseEnabledField = var5.getDeclaredField("enable");
         this.impulseEnabledField.setAccessible(true);
      } catch (NoSuchFieldException var7) {
         throw new RuntimeException("no field");
      } catch (ClassNotFoundException var8) {
         throw new RuntimeException("no class");
      } catch (NoSuchMethodException var9) {
         throw new RuntimeException("NoSuchMethodException");
      }

      ArrayList var10 = ScriptManager.instance.getAllVehicleScripts();
      DebugLog.log("Realistic Car Physics: Updating " + var10.size() + " Vehicle Scripts");

      for (int var11 = 0; var11 < var10.size(); var11++) {
         VehicleScript var13 = (VehicleScript)var10.get(var11);

         try {
            var13.Load(var13.getName(), "{ stoppingMovementForce = 0.01, }");
         } catch (Exception var6) {
            DebugLog.log("Error setting vehicle script stopping force");
         }

         var13.toBullet();
      }

      for (BaseVehicle var15 : IsoWorld.instance.currentCell.getVehicles()) {
         var15.scriptReloaded();
         DebugLog.log("Refreshing Vehicle Script");
      }

      CarController.onFirstUpdate = true;
   }

   public void create() {
      if (!this.created) {
         this.reflectionInit();
         IsoMetaGrid var1 = IsoWorld.instance.metaGrid;
         this.offsetX = var1.getMinX() * 256;
         this.offsetY = var1.getMinY() * 256;
         this.time = GameTime.getServerTimeMills();
         IsoChunkMap var2 = IsoWorld.instance.currentCell.chunkMap[0];
         Bullet.initWorld(
            var1.getMinX(),
            var1.getMinY(),
            var1.getMaxX(),
            var1.getMaxY(),
            (int)this.offsetX,
            (int)this.offsetY,
            var2.getWorldXMin(),
            var2.getWorldYMin(),
            IsoChunkMap.chunkGridWidth
         );

         for (int var3 = 0; var3 < 4; var3++) {
            this.wheelSteer[var3] = 0.0F;
            this.wheelRotation[var3] = 0.0F;
            this.wheelSkidInfo[var3] = 0.0F;
            this.wheelSuspensionLength[var3] = 0.0F;
         }

         this.created = true;
      }
   }

   public void destroy() {
      Bullet.destroyWorld();
   }

   private void updatePhysic() {
      ArrayList var1 = IsoWorld.instance.currentCell.getVehicles();
      float var2 = 10.0F;

      for (int var3 = 0; var3 < var1.size(); var3++) {
         BaseVehicle var4 = (BaseVehicle)var1.get(var3);
         float var5 = var4.getMass();
         if (var5 > var2) {
            var2 = var5;
         }
      }

      float var20 = 750.0F;
      this.massScaler = Math.min(1.0F, var20 / var2);
      if (this.massScaler < 0.02) {
         DebugLog.log("Dynamic Mass Scale extremely low " + this.massScaler);
      }

      int var21 = 0;
      this.localTime = this.localTime + GameTime.instance.getPhysicsSecondsSinceLastUpdate();
      if (this.localTime >= 0.01F) {
         var21 = (int)(this.localTime / 0.01F);
         this.localTime -= var21 * 0.01F;

         for (int var22 = 0; var22 < var21; var22++) {
            ArrayList var6 = IsoWorld.instance.currentCell.getVehicles();

            for (int var7 = 0; var7 < var6.size(); var7++) {
               BaseVehicle var8 = (BaseVehicle)var6.get(var7);
               Bullet.setVehicleMass(var8.vehicleId, var8.getMass() * this.massScaler);
               float var9 = Math.min(1.5F, 1000.0F / var8.getFudgedMass());

               try {
                  Object var10 = this.fieldImpulseFromHitZombie.get(var8);
                  int var11 = (Integer)this.ImpulseSizeMethod.invoke(var10);

                  for (int var12 = 0; var12 < var11; var12++) {
                     Object var13 = this.ImpulseGetMethod.invoke(var10, var12);
                     Vector3f var14 = (Vector3f)this.impulseField.get(var13);
                     Vector3f var15 = new Vector3f();
                     var8.getLinearVelocity(var15);
                     var15.mul(-0.025F * var8.getFudgedMass());
                     boolean var16 = var15.sub(var14).lengthSquared() < 0.001F;
                     var8.getLinearVelocity(var15);
                     var15.mul(-0.1F * var8.getFudgedMass());
                     var16 = var16 || var15.sub(var14).lengthSquared() < 0.001F;
                     if (var16) {
                        var14.mul(var9 * this.massScaler * this.getSandboxOption("RealisticCarPhysics.PlantImpulse", 1.0F));
                     } else {
                        var14.mul(var9 * this.massScaler * this.getSandboxOption("RealisticCarPhysics.ZombieImpulse", 1.0F));
                     }
                  }

                  Object var24 = this.fieldImpulseFromSquishedZombie.get(var8);
                  var11 = Array.getLength(var24);
                  CarController var25 = var8.getController();
                  var25.wheelImpulseCooldown = Math.max(0.0F, var25.wheelImpulseCooldown - 0.01F);

                  for (int var26 = 0; var26 < var11; var26++) {
                     Object var27 = Array.get(var24, var26);
                     if (var27 != null) {
                        boolean var29 = this.impulseEnabledField.getBoolean(var27);
                        if (var29) {
                           Vector3f var17 = (Vector3f)this.impulseField.get(var27);
                           if (var25.wheelImpulseCooldown < 0.01F) {
                              var17.mul(var9 * 2.0F * this.massScaler * this.getSandboxOption("RealisticCarPhysics.CorpseImpulse", 1.0F));
                              var25.wheelImpulseCooldown = 0.08F;
                           } else {
                              var17.mul(0.0F);
                           }
                        }
                     }
                  }
               } catch (IllegalAccessException var18) {
                  throw new RuntimeException("access exception");
               } catch (InvocationTargetException var19) {
                  throw new RuntimeException("InvocationTargetException");
               }

               var8.applyAccumulatedImpulsesFromHitObjectsToPhysics();
               var8.applyAllImpulsesFromProneCharacters();
            }

            Bullet.stepSimulation(0.01F, 0, 0.0F);
         }

         this.periodSec = var21 * 0.01F;
         this.bulletFrameNo++;
      }

      if (Math.abs(this.time - GameTime.getServerTimeMills()) > 100L) {
         this.time = GameTime.getServerTimeMills();
      } else {
         this.time += (long)(10.0F * var21);
      }
   }

   public void update() {
      AbstractPerformanceProfileProbe var1 = s_performance.worldSimulationUpdate.profile();

      try {
         this.updateInternal();
      } catch (Throwable var5) {
         if (var1 != null) {
            try {
               var1.close();
            } catch (Throwable var4) {
               var5.addSuppressed(var4);
            }
         }

         throw var5;
      }

      if (var1 != null) {
         var1.close();
      }
   }

   private void updateInternal() {
      if (this.created) {
         this.updatePhysic();
         if (GameClient.client) {
            try {
               VehicleManager.instance.clientUpdate();
            } catch (Exception var23) {
               DebugLog.Vehicle.printException(var23, "VehicleManager.clientUpdate was failed", LogSeverity.Error);
            }
         }

         this.collideVehicles.clear();
         int var1 = Bullet.getVehicleCount();
         int var2 = 0;

         while (var2 < var1) {
            int var3 = Bullet.getVehiclePhysics(var2, this.ff);
            if (var3 <= 0) {
               break;
            }

            var2 += var3;
            int var4 = 0;

            for (int var5 = 0; var5 < var3; var5++) {
               int var6 = (int)this.ff[var4++];
               float var7 = this.ff[var4++];
               float var8 = this.ff[var4++];
               float var9 = this.ff[var4++];
               this.tempTransform.origin.set(var7, var8, var9);
               float var10 = this.ff[var4++];
               float var11 = this.ff[var4++];
               float var12 = this.ff[var4++];
               float var13 = this.ff[var4++];
               this.javaxQuat4f.set(var10, var11, var12, var13);
               this.tempTransform.setRotation(this.javaxQuat4f);
               float var14 = this.ff[var4++];
               float var15 = this.ff[var4++];
               float var16 = this.ff[var4++];
               this.tempVector3f.set(var14, var15, var16);
               float var17 = this.ff[var4++];
               float var18 = this.ff[var4++];
               int var19 = (int)this.ff[var4++];

               for (int var20 = 0; var20 < var19; var20++) {
                  this.wheelSteer[var20] = this.ff[var4++];
                  this.wheelRotation[var20] = this.ff[var4++];
                  this.wheelSkidInfo[var20] = this.ff[var4++];
                  this.wheelSuspensionLength[var20] = this.ff[var4++];
               }

               int var57 = (int)(var7 * 100.0F + var8 * 100.0F + var9 * 100.0F + var10 * 100.0F + var11 * 100.0F + var12 * 100.0F + var13 * 100.0F);
               BaseVehicle var21 = VehicleManager.instance.getVehicleByID((short)var6);
               if (var21 != null
                  && (
                     !GameClient.client
                        || var21 == null
                        || !(var21.timeSinceLastAuth <= 0.0F)
                        || !var21.isNetPlayerAuthorization(Authorization.Remote) && !var21.isNetPlayerAuthorization(Authorization.RemoteCollide)
                  )) {
                  if (var21.vehicleId == var6 && var18 > 0.5F) {
                     this.collideVehicles.add(var21);
                     var21.authSimulationHash = var57;
                  }

                  if (GameClient.client && var21.isNetPlayerAuthorization(Authorization.LocalCollide)) {
                     if (var21.authSimulationHash != var57) {
                        var21.authSimulationTime = System.currentTimeMillis();
                        var21.authSimulationHash = var57;
                     }

                     if (System.currentTimeMillis() - var21.authSimulationTime > 1000L) {
                        INetworkPacket.send(PacketType.VehicleCollide, new Object[]{var21, var21.getDriver(), false});
                        var21.authSimulationTime = 0L;
                     }
                  }

                  if (!var21.isNetPlayerAuthorization(Authorization.Remote) || !var21.isNetPlayerAuthorization(Authorization.RemoteCollide)) {
                     if (GameClient.client && var21.isNetPlayerAuthorization(Authorization.Server)) {
                        var21.setSpeedKmHour(0.0F);
                     } else {
                        var21.setSpeedKmHour(var17);
                     }
                  }

                  if (!GameClient.client
                     || var21 == null
                     || !(var21.timeSinceLastAuth <= 0.0F)
                     || !var21.isNetPlayerAuthorization(Authorization.Server)
                        && !var21.isNetPlayerAuthorization(Authorization.Remote)
                        && !var21.isNetPlayerAuthorization(Authorization.RemoteCollide)) {
                     if (this.compareTransform(this.tempTransform, var21.getPoly().t)) {
                        var21.polyDirty = true;
                     }

                     var21.jniTransform.set(this.tempTransform);
                     var21.jniLinearVelocity.set(this.tempVector3f);
                     var21.jniIsCollide = var18 > 0.5F;

                     for (int var22 = 0; var22 < var19; var22++) {
                        var21.wheelInfo[var22].steering = this.wheelSteer[var22];
                        var21.wheelInfo[var22].rotation = this.wheelRotation[var22];
                        var21.wheelInfo[var22].skidInfo = this.wheelSkidInfo[var22];
                        var21.wheelInfo[var22].suspensionLength = this.wheelSuspensionLength[var22];
                     }
                  }
               }
            }
         }

         if (GameClient.client) {
            IsoPlayer var24 = IsoPlayer.players[IsoPlayer.getPlayerIndex()];
            if (var24 != null) {
               BaseVehicle var42 = var24.getVehicle();
               if (var42 != null && var42.isNetPlayerId(var24.getOnlineID()) && this.collideVehicles.contains(var42)) {
                  for (BaseVehicle var49 : this.collideVehicles) {
                     if (var49.DistTo(var42) < 16.0F && var49.isNetPlayerAuthorization(Authorization.Server)) {
                        INetworkPacket.send(PacketType.VehicleCollide, new Object[]{var49, var24, true});
                        var49.authorizationClientCollide(var24);
                     }
                  }
               }
            }
         }

         int var25 = Bullet.getObjectPhysics(this.ff);
         int var43 = 0;

         for (int var48 = 0; var48 < var25; var48++) {
            int var50 = (int)this.ff[var43++];
            float var51 = this.ff[var43++];
            float var53 = this.ff[var43++];
            float var54 = this.ff[var43++];
            var51 += this.offsetX;
            var54 += this.offsetY;
            IsoMovingObject var56 = this.physicsObjectMap.get(var50);
            if (var56 != null) {
               var56.removeFromSquare();
               var56.setX(var51 + 0.18F);
               var56.setY(var54);
               var56.setZ(Math.max(0.0F, var53 / 3.0F / 0.8164967F));
               var56.setCurrentSquareFromPosition();
            }
         }
      }
   }

   private boolean compareTransform(Transform var1, Transform var2) {
      if (!(Math.abs(var1.origin.x - var2.origin.x) > 0.01F) && !(Math.abs(var1.origin.z - var2.origin.z) > 0.01F) && (int)var1.origin.y == (int)var2.origin.y) {
         byte var3 = 2;
         var1.basis.getColumn(2, this.tempVector3f2);
         float var4 = this.tempVector3f2.x;
         float var5 = this.tempVector3f2.z;
         var2.basis.getColumn(2, this.tempVector3f2);
         float var6 = this.tempVector3f2.x;
         float var7 = this.tempVector3f2.z;
         return Math.abs(var4 - var6) > 0.001F || Math.abs(var5 - var7) > 0.001F;
      } else {
         return true;
      }
   }

   public void activateChunkMap(int var1) {
      this.create();
      IsoChunkMap var2 = IsoWorld.instance.currentCell.chunkMap[var1];
      if (!GameServer.server) {
         Bullet.activateChunkMap(var1, var2.getWorldXMin(), var2.getWorldYMin(), IsoChunkMap.chunkGridWidth);
      }
   }

   public void deactivateChunkMap(int var1) {
      if (this.created) {
         Bullet.deactivateChunkMap(var1);
      }
   }

   public void scrollGroundLeft(int var1) {
      if (this.created) {
         Bullet.scrollChunkMapLeft(var1);
      }
   }

   public void scrollGroundRight(int var1) {
      if (this.created) {
         Bullet.scrollChunkMapRight(var1);
      }
   }

   public void scrollGroundUp(int var1) {
      if (this.created) {
         Bullet.scrollChunkMapUp(var1);
      }
   }

   public void scrollGroundDown(int var1) {
      if (this.created) {
         Bullet.scrollChunkMapDown(var1);
      }
   }

   public static GenericDrawer getDrawer(int var0) {
      PhysicsDebugRenderer var1 = PhysicsDebugRenderer.alloc();
      var1.init(IsoPlayer.players[var0]);
      IsoPlayer.players[var0].physicsDebugRenderer = var1;
      return var1;
   }

   public int getBulletFrameNo() {
      return this.bulletFrameNo;
   }
}
