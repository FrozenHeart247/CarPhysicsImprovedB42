package zombie.roadcraft;

/** Build family and ABI snapshot used by the clean-room ZombieBuddy patches. */
public final class GameCompatibility {
    public static final String GAME_FAMILY = "42";
    public static final String KNOWN_TESTED_VERSION = "42.20.4";
    public static final String KNOWN_TESTED_BUILD = "b0bbce05d5";
    public static final String KNOWN_TESTED_GAME_JAR_SHA256 =
            "80E405A4BFC42F6072E75B3735F458A6514143DA011D3226007DED305A442F44";
    public static final String KNOWN_TESTED_CAR_CONTROLLER_SHA256 =
            "5B660138BA8F2A575502D641318371ECF9E9B3555DC0815241DEE45C5F30410D";

    private GameCompatibility() {
    }
}
