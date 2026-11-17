package com.constantmusic.client;

import com.constantmusic.ConstantMusicMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Looks up (and caches) song lengths on a background thread so the game never stutters. */
public final class SongLengths {

    private static final Map<ResourceLocation, Double> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ConstantMusic-LengthReader");
        thread.setDaemon(true);
        return thread;
    });

    private SongLengths() {
    }

    /**
     * @param soundId the sound's id as used in sounds.json, e.g. minecraft:music/game/calm1
     * @return a future holding the length in seconds, or -1 if unknown. Never completes exceptionally.
     */
    public static CompletableFuture<Double> lookup(ResourceManager resources, ResourceLocation soundId) {
        Double cached = CACHE.get(soundId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            ResourceLocation file = new ResourceLocation(soundId.getNamespace(), "sounds/" + soundId.getPath() + ".ogg");
            double seconds = -1.0D;
            try (InputStream in = resources.getResource(file).orElseThrow().open()) {
                seconds = OggDuration.readSeconds(in);
            } catch (Exception e) {
                ConstantMusicMod.LOGGER.warn("Could not read length of {}; it will not be crossfaded", file, e);
            }
            CACHE.put(soundId, seconds);
            return seconds;
        }, POOL);
    }

    /** Called on resource reload (a different resource pack may ship different music). */
    public static void clear() {
        CACHE.clear();
    }
}
