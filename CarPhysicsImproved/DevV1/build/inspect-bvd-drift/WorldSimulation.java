package zombie.core.physics;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import zombie.debug.DebugType;
import zombie.debug.LogSeverity;
import zombie.iso.IsoChunk;
import zombie.iso.IsoChunkMap;
import zombie.iso.IsoFloorBloodSplat;
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
   public boolean created;
   public float offsetX;
   public float offsetY;
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
   private float localTime;
   public float periodSec;
   private int bulletFrameNo = -1;
   public float massScaler = 1.0F;
   private Field bvdHitImpulseList;
   private Field bvdSquishImpulseArray;
   private Method bvdListSize;
   private Method bvdListGet;
   private Field bvdImpulseVector;
   private Field bvdImpulseEnabled;
   private boolean bvdReflectionReady;
   private final Vector3f bvdImpulseProbe = new Vector3f();
   private final HashMap<Integer, Float> bvdCorpseCooldown = new HashMap<>();
   private static long bvdSkidPruneMs = 0L;

   private float getSandboxOption(String var1, float var2) {
      SandboxOption var3 = SandboxOptions.instance.getOptionByName(var1);
      if (var3 == null) {
         return var2;
      }

      try {
         return (float)Double.parseDouble(var3.asConfigOption().getValueAsString());
      } catch (NumberFormatException var5) {
         return var2;
      }
   }

   private void reflectionInit() {
      try {
         Class var1 = Class.forName("zombie.vehicles.BaseVehicle");
         this.bvdHitImpulseList = var1.getDeclaredField("impulsesFromHitObjects");
         this.bvdHitImpulseList.setAccessible(true);
         this.bvdSquishImpulseArray = var1.getDeclaredField("impulsesFromSquishedBodies");
         this.bvdSquishImpulseArray.setAccessible(true);
         Class var2 = Class.forName("java.util.ArrayList");
         this.bvdListSize = var2.getMethod("size");
         this.bvdListGet = var2.getMethod("get", int.class);
         Class var3 = Class.forName("zombie.vehicles.BaseVehicle$VehicleImpulse");
         this.bvdImpulseVector = var3.getDeclaredField("impulse");
         this.bvdImpulseVector.setAccessible(true);
         this.bvdImpulseEnabled = var3.getDeclaredField("enable");
         this.bvdImpulseEnabled.setAccessible(true);
         this.bvdReflectionReady = true;
      } catch (ClassNotFoundException var6) {
         DebugLog.log("BetterVehicleDynamics: reflection init failed (" + var6 + "); impulse scaling disabled, vanilla physics retained");
         this.bvdReflectionReady = false;
         return;
      } catch (NoSuchFieldException var7) {
         DebugLog.log("BetterVehicleDynamics: reflection init failed (" + var7 + "); impulse scaling disabled, vanilla physics retained");
         this.bvdReflectionReady = false;
         return;
      } catch (NoSuchMethodException var8) {
         DebugLog.log("BetterVehicleDynamics: reflection init failed (" + var8 + "); impulse scaling disabled, vanilla physics retained");
         this.bvdReflectionReady = false;
         return;
      }

      ArrayList var9 = ScriptManager.instance.getAllVehicleScripts();
      DebugLog.log("BetterVehicleDynamics: retuning " + var9.size() + " vehicle script(s)");

      for (int var10 = 0; var10 < var9.size(); var10++) {
         VehicleScript var11 = (VehicleScript)var9.get(var10);

         try {
            var11.Load(var11.getName(), "{ stoppingMovementForce = 0.01, }");
         } catch (Exception var5) {
            DebugLog.log("BetterVehicleDynamics: could not retune script " + var11.getName());
         }

         var11.toBullet();
      }
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
      float var1 = 10.0F;
      HashSet var2 = new HashSet();

      for (BaseVehicle var4 : IsoWorld.instance.currentCell.getVehicles()) {
         var2.add(Integer.valueOf(var4.vehicleId));
         float var5 = var4.getMass();
         if (var5 > var1) {
            var1 = var5;
         }
      }

      this.massScaler = Math.min(1.0F, 750.0F / var1);
      if (this.massScaler < 0.02F) {
         DebugLog.log("BetterVehicleDynamics: mass scaler unusually small " + this.massScaler);
      }

      int var8 = 0;
      this.localTime = this.localTime + GameTime.instance.getPhysicsSecondsSinceLastUpdate();
      if (this.localTime >= 0.01F) {
         var8 = (int)(this.localTime / 0.01F);
         this.localTime -= var8 * 0.01F;

         for (int var9 = 0; var9 < var8; var9++) {
            for (BaseVehicle var6 : IsoWorld.instance.currentCell.getVehicles()) {
               Bullet.setVehicleMass(var6.vehicleId, var6.getMass() * this.massScaler);
               float var7 = Math.min(1.5F, 1000.0F / var6.getFudgedMass());
               this.applyImpulseScaling(var6, var7);
               var6.applyAccumulatedImpulsesFromHitObjectsToPhysics();
               var6.applyAllImpulsesFromProneCharacters();
            }

            Bullet.stepSimulation(0.01F, 0, 0.0F);
         }

         this.periodSec = var8 * 0.01F;
         this.bulletFrameNo++;
      }

      this.bvdCorpseCooldown.keySet().retainAll(var2);
      if (Math.abs(this.time - GameTime.getServerTimeMills()) > 100L) {
         this.time = GameTime.getServerTimeMills();
      } else {
         this.time += (long)(10.0F * var8);
      }
   }

   private void applyImpulseScaling(BaseVehicle var1, float var2) {
      if (this.bvdReflectionReady) {
         try {
            Object var3 = this.bvdHitImpulseList.get(var1);
            int var4 = (Integer)this.bvdListSize.invoke(var3);

            for (int var5 = 0; var5 < var4; var5++) {
               Object var6 = this.bvdListGet.invoke(var3, var5);
               Vector3f var7 = (Vector3f)this.bvdImpulseVector.get(var6);
               Vector3f var8 = this.bvdImpulseProbe;
               var1.getLinearVelocity(var8);
               var8.mul(-0.025F * var1.getFudgedMass());
               boolean var9 = var8.sub(var7).lengthSquared() < 0.001F;
               var1.getLinearVelocity(var8);
               var8.mul(-0.1F * var1.getFudgedMass());
               var9 = var9 || var8.sub(var7).lengthSquared() < 0.001F;
               if (var9) {
                  var7.mul(var2 * this.massScaler * this.getSandboxOption("BetterVehicleDynamics.ShoveFoliage", 1.0F));
               } else {
                  var7.mul(var2 * this.massScaler * this.getSandboxOption("BetterVehicleDynamics.ShoveZombies", 1.0F));
               }
            }

            Object var13 = this.bvdSquishImpulseArray.get(var1);
            int var14 = Array.getLength(var13);
            float var15 = this.bvdCorpseCooldown.getOrDefault(var1.vehicleId, 0.0F);
            var15 = Math.max(0.0F, var15 - 0.01F);

            for (int var17 = 0; var17 < var14; var17++) {
               Object var19 = Array.get(var13, var17);
               if (var19 != null && this.bvdImpulseEnabled.getBoolean(var19)) {
                  Vector3f var10 = (Vector3f)this.bvdImpulseVector.get(var19);
                  if (var15 < 0.01F) {
                     var10.mul(var2 * 2.0F * this.massScaler * this.getSandboxOption("BetterVehicleDynamics.ShoveCorpses", 1.0F));
                     var15 = 0.08F;
                  } else {
                     var10.mul(0.0F);
                  }
               }
            }

            this.bvdCorpseCooldown.put(Integer.valueOf(var1.vehicleId), var15);
         } catch (IllegalAccessException var11) {
            throw new RuntimeException("BetterVehicleDynamics: impulse field access denied", var11);
         } catch (InvocationTargetException var12) {
            throw new RuntimeException("BetterVehicleDynamics: impulse list invoke failed", var12);
         }
      }
   }

   private void bvdPruneSkidMarksWorld() {
      long var1 = System.currentTimeMillis();
      if (var1 - bvdSkidPruneMs >= 2000L) {
         bvdSkidPruneMs = var1;
         if (IsoWorld.instance != null && IsoWorld.instance.currentCell != null) {
            IsoChunkMap var3 = IsoWorld.instance.currentCell.chunkMap[0];
            if (var3 != null) {
               float var4 = (float)GameTime.getInstance().getWorldAgeHours();
               float var5 = 0.16666667F;
               int var6 = IsoChunkMap.chunkGridWidth;

               for (int var7 = 0; var7 < var6; var7++) {
                  for (int var8 = 0; var8 < var6; var8++) {
                     IsoChunk var9 = var3.getChunk(var7, var8);
                     if (var9 != null) {
                        int var10 = var9.floorBloodSplats.size();
                        if (var10 != 0) {
                           ArrayList var11 = null;
                           int var12 = 0;

                           for (int var13 = 0; var13 < var10; var13++) {
                              IsoFloorBloodSplat var14 = (IsoFloorBloodSplat)var9.floorBloodSplats.get(var13);
                              boolean var15 = var14.type >= 21 && var14.type <= 24 && var4 - var14.worldAge >= var5;
                              if (!var15) {
                                 if (var11 != null) {
                                    var11.add(var14);
                                 }
                              } else {
                                 if (var11 == null) {
                                    var11 = new ArrayList();

                                    for (int var16 = 0; var16 < var13; var16++) {
                                       var11.add((IsoFloorBloodSplat)var9.floorBloodSplats.get(var16));
                                    }
                                 }

                                 var12++;
                                 var9.floorBloodSplatsFade.remove(var14);
                              }
                           }

                           if (var12 > 0) {
                              var9.floorBloodSplats.clear();

                              for (int var17 = 0; var17 < var11.size(); var17++) {
                                 var9.floorBloodSplats.add((IsoFloorBloodSplat)var11.get(var17));
                              }

                              var9.invalidateRenderChunkLevels(1L);
                           }
                        }
                     }
                  }
               }
            }
         }
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
         this.bvdPruneSkidMarksWorld();
         this.updatePhysic();
         if (GameClient.client) {
            try {
               VehicleManager.instance.clientUpdate();
            } catch (Exception var23) {
               DebugType.Vehicle.printException(var23, "VehicleManager.clientUpdate was failed", LogSeverity.Error);
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

                     if (var21.isAttachingTrailer() || var21.getVehicleTowedBy() != null && var21.getVehicleTowedBy().isAttachingTrailer()) {
                        var21.authSimulationTime = System.currentTimeMillis();
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
                     || !(var21.timeSinceLastAuth <= 0.0F)
                     || var21.constraintChangedTime != -1L
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
