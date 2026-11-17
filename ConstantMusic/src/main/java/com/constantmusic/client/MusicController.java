package com.constantmusic.client;

import com.constantmusic.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces vanilla's "play a song every 10-20 minutes" logic with "always have a song playing".
 * <p>
 * Vanilla's MusicManager keeps running but its songs are blocked in {@link ClientEvents}; this class
 * plays the music instead. What to play still comes from {@link Minecraft#getSituationalMusic()}, so
 * menu / creative / nether / the End / credits music (and other mods' music) all keep working.
 * <p>
 * Everything is counted in game ticks, so nothing advances while the game is paused.
 */
public final class MusicController {

    /** How long to wait before retrying when a song could not be started (sound engine busy, volume 0...). */
    private static final int RETRY_TICKS = 100;
    private static final int SITUATION_CHECK_INTERVAL_TICKS = 10;

    /** Every song that is currently audible, including ones that are fading out. */
    private static final List<ConstantMusicInstance> ACTIVE = new ArrayList<>();

    /** The newest song: the one that decides what happens next. Null while idle / pausing. */
    private static ConstantMusicInstance current;
    private static ResourceLocation lastSound;
    private static int waitTicks;
    private static int situationCooldown;
    private static boolean wasInWorld;

    private MusicController() {
    }

    public static void tick(Minecraft mc) {
        // Nothing moves while the game is paused (the sound engine pauses too) or still loading.
        if (mc.isPaused() || mc.getOverlay() != null) {
            return;
        }

        SoundManager sounds = mc.getSoundManager();

        // Forget songs that ended, or that the game stopped (world change, sound reload...).
        ACTIVE.removeIf(song -> song.isStopped() || !sounds.isActive(song));
        for (ConstantMusicInstance song : ACTIVE) {
            song.pollDuration();
        }

        if (current != null && !ACTIVE.contains(current)) {
            boolean natural = current.endedNaturally();
            current = null;
            // The configured pause only applies after a song that really finished, not after a cut-off.
            waitTicks = Math.max(waitTicks, natural && ClientConfig.pauseEnabled() ? ClientConfig.pauseTicks() : 0);
        }

        boolean inWorld = mc.level != null;
        boolean worldChanged = inWorld != wasInWorld;
        wasInWorld = inWorld;

        if (current != null) {
            tickCurrent(mc, worldChanged);
        } else {
            tickIdle(mc);
        }
    }

    /** Forget everything (used after an error so one bug can't spam the log every tick). */
    public static void reset() {
        ACTIVE.clear();
        current = null;
        waitTicks = RETRY_TICKS * 2;
    }

    // ---------------------------------------------------------------------------------------

    /** Nothing is playing as the "current" song: wait out any pause, then start the next one. */
    private static void tickIdle(Minecraft mc) {
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        current = startSong(mc, mc.getSituationalMusic(), fadeInTicks());
    }

    private static void tickCurrent(Minecraft mc, boolean worldChanged) {
        ConstantMusicInstance song = current;

        // 1) The situation wants different music right now (credits, the End, menu -> world, ...).
        if (!song.isFadingOut() && (worldChanged || --situationCooldown <= 0)) {
            situationCooldown = SITUATION_CHECK_INTERVAL_TICKS;
            Music wanted = mc.getSituationalMusic();
            boolean differs = !wanted.getEvent().value().getLocation().equals(song.getEventId());
            if (differs && (wanted.replaceCurrentMusic() || worldChanged)) {
                int fade = fadeInTicks();
                if (fade > 0) {
                    song.beginFadeOut(fade);
                } else {
                    song.finish();
                }
                current = startSong(mc, wanted, fade);
                return;
            }
        }

        // 2) The song is about to end: hand over to the next one.
        if (song.isFadingOut()) {
            return;
        }
        int total = song.getDurationTicks();
        if (total <= 0) {
            return; // length unknown: let it finish by itself, the next song then fades in on its own
        }

        boolean fade = ClientConfig.fadeEnabled();
        int lead = fade ? Math.min(ClientConfig.fadeTicks(), total / 2) : 0;
        int remaining = total - song.getAge();
        if (remaining > lead) {
            return;
        }

        if (ClientConfig.pauseEnabled()) {
            // Fade out so that we reach silence exactly at the end; tickIdle() starts the next song after the pause.
            if (fade) {
                song.beginFadeOut(Math.max(1, remaining));
            }
            return;
        }

        // Continuous mode: the next song starts now, overlapping the tail of this one.
        if (lead > 0) {
            song.beginFadeOut(Math.max(1, remaining));
        }
        current = startSong(mc, mc.getSituationalMusic(), lead);
    }

    private static int fadeInTicks() {
        return ClientConfig.fadeEnabled() ? ClientConfig.fadeTicks() : 0;
    }

    /**
     * Starts a song for the given music category.
     *
     * @return the new song, or null if it could not be started (a retry is then scheduled).
     */
    private static ConstantMusicInstance startSong(Minecraft mc, Music music, int fadeInTicks) {
        SoundManager sounds = mc.getSoundManager();
        SoundEvent event = music.getEvent().value();

        // Pick a file, trying a few times to avoid repeating the song that just played.
        ConstantMusicInstance song = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            ConstantMusicInstance candidate = new ConstantMusicInstance(event, fadeInTicks);
            candidate.resolve(sounds);
            Sound sound = candidate.getSound();
            if (sound == null || sound == SoundManager.EMPTY_SOUND) {
                break;
            }
            song = candidate;
            if (!sound.getLocation().equals(lastSound)) {
                break;
            }
        }

        if (song != null) {
            sounds.play(song);
        }
        if (song == null || !sounds.isActive(song)) {
            waitTicks = RETRY_TICKS;
            return null;
        }

        lastSound = song.getSoundId();
        song.setDurationFuture(SongLengths.lookup(mc.getResourceManager(), lastSound));
        ACTIVE.add(song);
        return song;
    }
}
