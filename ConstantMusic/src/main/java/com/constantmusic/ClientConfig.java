package com.constantmusic;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client config, written to config/constantmusic-client.toml.
 * Values are read live, so edits take effect without restarting the game.
 */
public final class ClientConfig {

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue FADE_ENABLED;
    private static final ForgeConfigSpec.DoubleValue FADE_SECONDS;
    private static final ForgeConfigSpec.BooleanValue PAUSE_ENABLED;
    private static final ForgeConfigSpec.DoubleValue PAUSE_SECONDS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        FADE_ENABLED = builder
                .comment(
                        "Fade songs in and out.",
                        "true  = the end of one song fades out while the start of the next fades in (they overlap).",
                        "false = songs start and end abruptly, one right after the other.")
                .define("fadeEnabled", true);

        FADE_SECONDS = builder
                .comment(
                        "How long the fade lasts, in seconds (also the length of the overlap).",
                        "Songs shorter than twice this value use half their length instead.")
                .defineInRange("fadeSeconds", (double) ConstantMusicMod.DEFAULT_FADE_SECONDS, 0.5D, 120.0D);

        PAUSE_ENABLED = builder
                .comment(
                        "Leave a silent gap between songs.",
                        "true  = a song fades out, there is silence for pauseSeconds, then the next song fades in",
                        "        (songs no longer overlap, because the gap is the point).",
                        "false = music is continuous.")
                .define("pauseBetweenSongs", false);

        PAUSE_SECONDS = builder
                .comment("Length of the silent gap between songs, in seconds (only used if pauseBetweenSongs = true).")
                .defineInRange("pauseSeconds", 10.0D, 0.0D, 3600.0D);

        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    public static boolean fadeEnabled() {
        return FADE_ENABLED.get();
    }

    public static int fadeTicks() {
        return (int) Math.round(FADE_SECONDS.get() * 20.0D);
    }

    public static boolean pauseEnabled() {
        return PAUSE_ENABLED.get();
    }

    public static int pauseTicks() {
        return (int) Math.round(PAUSE_SECONDS.get() * 20.0D);
    }
}
