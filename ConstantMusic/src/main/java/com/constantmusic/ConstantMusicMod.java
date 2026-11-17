package com.constantmusic;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Constant Music - client-side mod that keeps Minecraft's music playing without gaps,
 * crossfading the end of each song into the start of the next one.
 */
@Mod(ConstantMusicMod.MOD_ID)
public class ConstantMusicMod {

    public static final String MOD_ID = "constantmusic";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * ============================================================
     *  THE ONE VARIABLE: how many seconds the songs overlap.
     * ============================================================
     * The last N seconds of a song fade out while the first N seconds of the next song
     * fade in. Change this number and rebuild to change the default.
     *
     * NOTE: this is only the DEFAULT used when the config file is first generated.
     * Players can override it any time in config/constantmusic-client.toml (fadeSeconds).
     * If you change this after the config file already exists, delete that file (or edit
     * fadeSeconds in it) to see the new value. Allowed range: 0.5 - 120.
     */
    public static final int DEFAULT_FADE_SECONDS = 10;

    public ConstantMusicMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }
}
