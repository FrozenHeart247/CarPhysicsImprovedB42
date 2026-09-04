package dev.carphysicsimproved.v1.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Client-local tire-mark buffer rendered after the cached terrain layer and
 * before players, floor effects and moving vehicles in the B42 FBO pipeline.
 * Game classes are reached through a small reflection boundary
 * so a compatible B42 update does not require bundling replacement classes.
 */
public final class LegacyTireTrackRenderer {
    private static final int TEXTURE_COUNT = 16;
    private static final int MAX_MARKS = 900;
    private static final Object LOCK = new Object();
    private static final Object[] TEXTURES = new Object[TEXTURE_COUNT];
    private static final ArrayDeque<TrackMark> MARKS = new ArrayDeque<>();

    private static volatile boolean enabled = true;
    private static volatile double lifetimeSeconds = 25.0;
    private static volatile double opacity = 0.70;
    private static volatile Access access;
    private static volatile boolean renderFailed;
    private static volatile String status = "waiting for tire-mark textures";
    private static volatile long acceptedMarks;
    private static volatile long hookCalls;
    private static volatile long renderCalls;
    private static volatile long visiblePasses;
    private static volatile long submittedDraws;
    private static volatile int lastLayer = Integer.MIN_VALUE;
    private static volatile int lastMarkZ = Integer.MIN_VALUE;
    private static volatile String lastRenderMode = "unknown";

    private LegacyTireTrackRenderer() {
    }

    public static void configure(boolean enabledValue, double lifetimeValue, double opacityValue) {
        enabled = enabledValue;
        lifetimeSeconds = clamp(lifetimeValue, 1.0, 600.0);
        opacity = clamp(opacityValue, 0.0, 1.0);
        if (!enabledValue) {
            clearMarks();
        }
    }

    public static void registerTexture(int index, Object texture) {
        if (index < 0 || index >= TEXTURE_COUNT || texture == null) {
            return;
        }
        synchronized (LOCK) {
            TEXTURES[index] = texture;
        }
        renderFailed = false;
        status = "ready; floor-pass renderer has " + registeredTextureCount() + "/" + TEXTURE_COUNT
                + " textures";
    }

    public static void addMark(double x, double y, double z, int textureIndex, double alpha) {
        if (!enabled || textureIndex < 0 || textureIndex >= TEXTURE_COUNT
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(alpha)) {
            return;
        }
        synchronized (LOCK) {
            if (TEXTURES[textureIndex] == null) {
                return;
            }
            while (MARKS.size() >= MAX_MARKS) {
                MARKS.removeFirst();
            }
            MARKS.addLast(new TrackMark(
                    (float) x,
                    (float) y,
                    (int) Math.floor(z),
                    textureIndex,
                    (float) clamp(alpha, 0.0, 1.0),
                    System.nanoTime()));
            acceptedMarks++;
            lastMarkZ = (int) Math.floor(z);
            if (acceptedMarks == 1L) {
                System.out.println("[CarPhysicsImprovedV1] Tire-mark producer accepted its first mark at z="
                        + lastMarkZ + ", texture=" + textureIndex);
            }
        }
    }

    public static void clearMarks() {
        synchronized (LOCK) {
            MARKS.clear();
            acceptedMarks = 0L;
            hookCalls = 0L;
            renderCalls = 0L;
            visiblePasses = 0L;
            submittedDraws = 0L;
            lastLayer = Integer.MIN_VALUE;
            lastMarkZ = Integer.MIN_VALUE;
            lastRenderMode = "unknown";
        }
    }

    public static String status() {
        return status + "; buffered=" + markCount()
                + ", accepted=" + acceptedMarks
                + ", hookCalls=" + hookCalls
                + ", renderCalls=" + renderCalls
                + ", visiblePasses=" + visiblePasses
                + ", draws=" + submittedDraws
                + ", layer=" + printableLayer(lastLayer)
                + ", markZ=" + printableLayer(lastMarkZ)
                + ", mode=" + lastRenderMode;
    }

    public static void renderCurrentCameraLayer() {
        hookCalls++;
        if (!enabled || renderFailed) {
            return;
        }
        try {
            Access current = access;
            if (current == null) {
                current = new Access();
                access = current;
            }
            Object frameState = current.cameraFrameState.get(null);
            renderLayer((int) Math.floor(current.cameraCharacterZ.getFloat(frameState)));
        } catch (Throwable throwable) {
            disableAfterRenderError(throwable);
        }
    }

    public static void renderLayer(int layer) {
        if (!enabled || renderFailed) {
            return;
        }
        renderCalls++;
        lastLayer = layer;
        try {
            Access current = access;
            if (current == null) {
                current = new Access();
                access = current;
            }
            long now = System.nanoTime();
            long lifetimeNanos = Math.max(1L, (long) (lifetimeSeconds * 1_000_000_000.0));
            List<TrackMark> visible = snapshot(layer, now, lifetimeNanos);
            if (visible.isEmpty()) {
                return;
            }
            visiblePasses++;
            int drawCount = current.render(visible, now, lifetimeNanos, (float) opacity);
            submittedDraws += drawCount;
            status = "active floor-pass renderer";
            if (submittedDraws == drawCount && drawCount > 0) {
                System.out.println("[CarPhysicsImprovedV1] Tire-mark renderer submitted its first "
                        + drawCount + " draw call(s); mode=" + lastRenderMode + ", layer=" + layer);
            }
        } catch (Throwable throwable) {
            disableAfterRenderError(throwable);
        }
    }

    private static void disableAfterRenderError(Throwable throwable) {
        renderFailed = true;
        Throwable cause = throwable instanceof InvocationTargetException
                && throwable.getCause() != null ? throwable.getCause() : throwable;
        status = "disabled after render error: " + cause.getClass().getSimpleName()
                + ": " + String.valueOf(cause.getMessage());
        System.err.println("[CarPhysicsImprovedV1] Tire-mark floor renderer disabled: " + cause);
    }

    public static void validateRuntimeAbi() throws ReflectiveOperationException {
        new Access();
    }

    static int markCount() {
        synchronized (LOCK) {
            return MARKS.size();
        }
    }

    static int registeredTextureCount() {
        synchronized (LOCK) {
            int count = 0;
            for (Object texture : TEXTURES) {
                if (texture != null) {
                    count++;
                }
            }
            return count;
        }
    }

    private static List<TrackMark> snapshot(int layer, long now, long lifetimeNanos) {
        ArrayList<TrackMark> result = new ArrayList<>();
        synchronized (LOCK) {
            Iterator<TrackMark> iterator = MARKS.iterator();
            while (iterator.hasNext()) {
                TrackMark mark = iterator.next();
                if (now - mark.bornNanos() > lifetimeNanos) {
                    iterator.remove();
                } else if (mark.z() == layer) {
                    result.add(mark);
                }
            }
        }
        return result;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String printableLayer(int value) {
        return value == Integer.MIN_VALUE ? "none" : Integer.toString(value);
    }

    private record TrackMark(float x, float y, int z, int textureIndex, float alpha, long bornNanos) {
    }

    /** Cached B42 rendering ABI derived from the installed game classes. */
    private static final class Access {
        private final Method xToScreen;
        private final Method yToScreen;
        private final Field globalOffsetX;
        private final Field globalOffsetY;
        private final Field cameraFrameState;
        private final Field cameraOffX;
        private final Field cameraOffY;
        private final Field cameraCharacterZ;
        private final Field cameraWidth;
        private final Field cameraHeight;
        private final Field cameraPlayerIndex;
        private final Field fboManagerInstance;
        private final Method fboIsCaching;
        private final Method fboXOffset;
        private final Method fboYOffset;
        private final Field fboRenderChunk;
        private final Field renderChunkChunk;
        private final Field chunkWorldX;
        private final Field chunkWorldY;
        private final Field chunkSize;
        private final Field tileScale;
        private final Field spriteRendererInstance;
        private final Method startShader;
        private final Field defaultShaderId;
        private final Method disableDepthTest;
        private final Method blendFunction;
        private final Method textureWidth;
        private final Method textureHeight;
        private final Method textureRender;
        private final Field depthModifierInstance;

        private Access() throws ReflectiveOperationException {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> isoUtilsClass = Class.forName("zombie.iso.IsoUtils", false, loader);
            Class<?> isoSpriteClass = Class.forName("zombie.iso.sprite.IsoSprite", false, loader);
            Class<?> isoCameraClass = Class.forName("zombie.iso.IsoCamera", false, loader);
            Class<?> frameStateClass = Class.forName("zombie.iso.IsoCamera$FrameState", false, loader);
            Class<?> fboManagerClass = Class.forName(
                    "zombie.iso.fboRenderChunk.FBORenderChunkManager", false, loader);
            Class<?> renderChunkClass = Class.forName(
                    "zombie.iso.fboRenderChunk.FBORenderChunk", false, loader);
            Class<?> isoChunkClass = Class.forName("zombie.iso.IsoChunk", false, loader);
            Class<?> isoChunkMapClass = Class.forName("zombie.iso.IsoChunkMap", false, loader);
            Class<?> coreClass = Class.forName("zombie.core.Core", false, loader);
            Class<?> spriteRendererClass = Class.forName("zombie.core.SpriteRenderer", false, loader);
            Class<?> sceneShaderClass = Class.forName("zombie.core.SceneShaderStore", false, loader);
            Class<?> indieGlClass = Class.forName("zombie.IndieGL", false, loader);
            Class<?> textureClass = Class.forName("zombie.core.textures.Texture", false, loader);
            Class<?> depthModifierClass = Class.forName(
                    "zombie.tileDepth.TileDepthModifier", false, loader);

            xToScreen = method(isoUtilsClass, "XToScreen",
                    float.class, float.class, float.class, int.class);
            yToScreen = method(isoUtilsClass, "YToScreen",
                    float.class, float.class, float.class, int.class);
            globalOffsetX = field(isoSpriteClass, "globalOffsetX");
            globalOffsetY = field(isoSpriteClass, "globalOffsetY");
            cameraFrameState = field(isoCameraClass, "frameState");
            cameraOffX = field(frameStateClass, "offX");
            cameraOffY = field(frameStateClass, "offY");
            cameraCharacterZ = field(frameStateClass, "camCharacterZ");
            cameraWidth = field(frameStateClass, "offscreenWidth");
            cameraHeight = field(frameStateClass, "offscreenHeight");
            cameraPlayerIndex = field(frameStateClass, "playerIndex");
            fboManagerInstance = field(fboManagerClass, "instance");
            fboIsCaching = method(fboManagerClass, "isCaching");
            fboXOffset = method(fboManagerClass, "getXOffset");
            fboYOffset = method(fboManagerClass, "getYOffset");
            fboRenderChunk = field(fboManagerClass, "renderChunk");
            renderChunkChunk = field(renderChunkClass, "chunk");
            chunkWorldX = field(isoChunkClass, "wx");
            chunkWorldY = field(isoChunkClass, "wy");
            chunkSize = field(isoChunkMapClass, "CHUNK_SIZE_IN_SQUARES");
            tileScale = field(coreClass, "tileScale");
            spriteRendererInstance = field(spriteRendererClass, "instance");
            startShader = method(spriteRendererClass, "StartShader", int.class, int.class);
            defaultShaderId = field(sceneShaderClass, "defaultShaderId");
            disableDepthTest = method(indieGlClass, "disableDepthTest");
            blendFunction = method(indieGlClass, "glBlendFuncSeparate",
                    int.class, int.class, int.class, int.class);
            textureWidth = method(textureClass, "getWidth");
            textureHeight = method(textureClass, "getHeight");
            textureRender = method(textureClass, "render",
                    float.class, float.class, float.class, float.class,
                    float.class, float.class, float.class, float.class, Consumer.class);
            depthModifierInstance = field(depthModifierClass, "instance");
        }

        private int render(List<TrackMark> marks, long now, long lifetimeNanos, float opacityValue)
                throws ReflectiveOperationException {
            Object frameState = cameraFrameState.get(null);
            Object fboManager = fboManagerInstance.get(null);
            boolean caching = (Boolean) invoke(fboIsCaching, fboManager);
            int currentChunkX = 0;
            int currentChunkY = 0;
            int chunkWidth = chunkSize.getInt(null);
            float offsetX;
            float offsetY;
            if (caching) {
                lastRenderMode = "chunk-cache";
                offsetX = ((Number) invoke(fboXOffset, fboManager)).floatValue();
                offsetY = ((Number) invoke(fboYOffset, fboManager)).floatValue();
                Object rendered = fboRenderChunk.get(fboManager);
                Object chunk = renderChunkChunk.get(rendered);
                currentChunkX = chunkWorldX.getInt(chunk);
                currentChunkY = chunkWorldY.getInt(chunk);
            } else {
                lastRenderMode = "screen";
                offsetX = -cameraOffX.getFloat(frameState);
                offsetY = -cameraOffY.getFloat(frameState);
                globalOffsetX.setFloat(null, offsetX);
                globalOffsetY.setFloat(null, offsetY);
            }

            int scale = tileScale.getInt(null);
            int screenWidth = cameraWidth.getInt(frameState);
            int screenHeight = cameraHeight.getInt(frameState);
            Object spriteRenderer = spriteRendererInstance.get(null);
            invoke(startShader, spriteRenderer,
                    defaultShaderId.getInt(null), cameraPlayerIndex.getInt(frameState));
            invoke(disableDepthTest, null);
            invoke(blendFunction, null, 770, 771, 773, 1);
            Object depthModifier = caching ? depthModifierInstance.get(null) : null;
            int drawCount = 0;

            for (TrackMark mark : marks) {
                if (caching && (Math.floorDiv((int) Math.floor(mark.x()), chunkWidth) != currentChunkX
                        || Math.floorDiv((int) Math.floor(mark.y()), chunkWidth) != currentChunkY)) {
                    continue;
                }
                Object texture;
                synchronized (LOCK) {
                    texture = TEXTURES[mark.textureIndex()];
                }
                if (texture == null) {
                    continue;
                }
                float x = mark.x();
                float y = mark.y();
                if (caching) {
                    x -= currentChunkX * (float) chunkWidth;
                    y -= currentChunkY * (float) chunkWidth;
                }
                float width = ((Number) invoke(textureWidth, texture)).floatValue() * scale;
                float height = ((Number) invoke(textureHeight, texture)).floatValue() * scale;
                float screenX = ((Number) invoke(xToScreen, null, x, y, (float) mark.z(), 0)).floatValue()
                        - width * 0.5f + offsetX;
                float screenY = ((Number) invoke(yToScreen, null, x, y, (float) mark.z(), 0)).floatValue()
                        - height * 0.5f + offsetY;
                if (!caching && (screenX >= screenWidth || screenX + width <= 0.0f
                        || screenY >= screenHeight || screenY + height <= 0.0f)) {
                    continue;
                }
                float age = Math.max(0.0f, (float) (now - mark.bornNanos()) / lifetimeNanos);
                float fade = age <= 0.70f ? 1.0f : Math.max(0.0f, (1.0f - age) / 0.30f);
                float alpha = mark.alpha() * opacityValue * fade;
                invoke(textureRender, texture,
                        screenX, screenY, width, height,
                        1.0f, 1.0f, 1.0f, alpha, depthModifier);
                drawCount++;
            }
            return drawCount;
        }

        private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
            Field result = type.getDeclaredField(name);
            result.setAccessible(true);
            return result;
        }

        private static Method method(Class<?> type, String name, Class<?>... parameterTypes)
                throws ReflectiveOperationException {
            Method result = type.getDeclaredMethod(name, parameterTypes);
            result.setAccessible(true);
            return result;
        }

        private static Object invoke(Method method, Object target, Object... arguments)
                throws ReflectiveOperationException {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof ReflectiveOperationException reflective) {
                    throw reflective;
                }
                throw exception;
            }
        }
    }
}
