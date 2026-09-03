package dev.carphysicsimproved.v1.runtime;

public final class LegacyTireTrackRendererTest {
    private LegacyTireTrackRendererTest() {
    }

    public static void main(String[] args) {
        LegacyTireTrackRenderer.clearMarks();
        LegacyTireTrackRenderer.configure(true, 25.0, 0.70);
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

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
