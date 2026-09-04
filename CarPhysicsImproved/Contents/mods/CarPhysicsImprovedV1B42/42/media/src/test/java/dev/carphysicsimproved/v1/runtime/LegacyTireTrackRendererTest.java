package dev.carphysicsimproved.v1.runtime;

public final class LegacyTireTrackRendererTest {
    private LegacyTireTrackRendererTest() {
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        assertSetting(60.0, "lifetimeSeconds", "default lifetime before Lua configuration");
        assertSetting(0.25, "opacity", "default opacity before Lua configuration");
        LegacyTireTrackRenderer.clearMarks();
        LegacyTireTrackRenderer.configure(true, 25.0, 0.70);
        assertSetting(25.0, "lifetimeSeconds", "custom lifetime remains configurable");
        assertSetting(0.70, "opacity", "custom opacity remains configurable");
        LegacyTireTrackRenderer.addMark(10.0, 20.0, 0.0, 0, 0.8);
        assertEquals(0, LegacyTireTrackRenderer.markCount(), "unregistered texture must reject a mark");

        LegacyTireTrackRenderer.registerTexture(0, new Object());
        assertEquals(1, LegacyTireTrackRenderer.registeredTextureCount(), "texture registration");
        LegacyTireTrackRenderer.addMark(Double.NaN, 20.0, 0.0, 0, 0.8);
        assertEquals(0, LegacyTireTrackRenderer.markCount(), "non-finite coordinates must be rejected");

        for (int index = 0; index < 910; index++) {
            LegacyTireTrackRenderer.addMark(index, 20.0, 0.0, 0, 0.8);
        }
        assertEquals(900, LegacyTireTrackRenderer.markCount(), "mark buffer must remain bounded");

        LegacyTireTrackRenderer.configure(false, 25.0, 0.70);
        assertEquals(0, LegacyTireTrackRenderer.markCount(), "disabling marks must clear the buffer");
        LegacyTireTrackRenderer.addMark(10.0, 20.0, 0.0, 0, 0.8);
        assertEquals(0, LegacyTireTrackRenderer.markCount(), "disabled renderer must reject new marks");
        System.out.println("LegacyTireTrackRendererTest: all checks passed");
    }

    private static void assertSetting(double expected, String name, String label)
            throws ReflectiveOperationException {
        var field = LegacyTireTrackRenderer.class.getDeclaredField(name);
        field.setAccessible(true);
        double actual = field.getDouble(null);
        if (Double.compare(expected, actual) != 0) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
