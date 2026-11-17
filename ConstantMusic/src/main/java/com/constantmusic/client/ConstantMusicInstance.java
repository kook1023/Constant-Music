package com.constantmusic.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.concurrent.CompletableFuture;

/**
 * One song. Behaves like vanilla's music instance, but its volume is driven by a fade-in and
 * (on request) a fade-out. The sound engine ticks it every game tick and re-reads {@link #getVolume()}.
 * <p>
 * Fades use an equal-power curve (sine in, cosine out) so that two overlapping songs keep a
 * constant perceived loudness instead of dipping in the middle of the crossfade.
 */
public final class ConstantMusicInstance extends AbstractTickableSoundInstance {

    private static final double HALF_PI = Math.PI / 2.0D;
    /** Must be non-zero: the sound engine refuses to start a sound whose volume is exactly 0. */
    private static final float START_VOLUME = 0.0001F;
    /** If a song stops this close to its end (or later) we count it as having finished naturally. */
    private static final int NATURAL_END_SLACK_TICKS = 100;

    private final ResourceLocation eventId;
    private final int fadeInTicks;

    private Sound pinnedSound;
    private int age;
    private int fadeOutStartAge = -1;
    private int fadeOutTicks = 1;

    /** -1 = still being read, 0 = unknown/failed, >0 = known length. */
    private int durationTicks = -1;
    private CompletableFuture<Double> durationFuture;

    public ConstantMusicInstance(SoundEvent event, int fadeInTicks) {
        super(event, SoundSource.MUSIC, RandomSource.create());
        this.eventId = event.getLocation();
        this.fadeInTicks = Math.max(0, fadeInTicks);

        this.looping = false;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = true;
        this.volume = this.fadeInTicks > 0 ? START_VOLUME : 1.0F;
    }

    /**
     * Vanilla re-rolls which file to play every time resolve() is called, and the engine calls it
     * again when playback starts. We need to know the file up front (to look up its length and to
     * avoid repeating the previous song), so the first pick is pinned.
     */
    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        if (this.pinnedSound == null) {
            WeighedSoundEvents events = super.resolve(manager);
            this.pinnedSound = this.sound;
            return events;
        }
        this.sound = this.pinnedSound;
        return manager.getSoundEvent(this.getLocation());
    }

    @Override
    public void tick() {
        this.age++;

        double gain = 1.0D;

        if (this.fadeInTicks > 0 && this.age < this.fadeInTicks) {
            gain *= Math.sin(HALF_PI * this.age / this.fadeInTicks);
        }

        if (this.fadeOutStartAge >= 0) {
            double progress = (double) (this.age - this.fadeOutStartAge) / this.fadeOutTicks;
            if (progress >= 1.0D) {
                this.volume = 0.0F;
                this.stop();
                return;
            }
            gain *= Math.cos(HALF_PI * progress);
        }

        this.volume = (float) gain;
    }

    // ---- controls used by MusicController -------------------------------------------------

    /** Start fading out; the song is stopped once the fade completes. */
    public void beginFadeOut(int ticks) {
        if (this.fadeOutStartAge >= 0) {
            return;
        }
        this.fadeOutTicks = Math.max(1, ticks);
        this.fadeOutStartAge = this.age;
    }

    /** Stop immediately. */
    public void finish() {
        this.stop();
    }

    public boolean isFadingOut() {
        return this.fadeOutStartAge >= 0;
    }

    public int getAge() {
        return this.age;
    }

    public ResourceLocation getEventId() {
        return this.eventId;
    }

    /** The id of the actual song file, e.g. minecraft:music/game/calm1. */
    public ResourceLocation getSoundId() {
        return this.pinnedSound != null ? this.pinnedSound.getLocation() : this.getLocation();
    }

    public void setDurationFuture(CompletableFuture<Double> future) {
        this.durationFuture = future;
    }

    /** Picks up the length once the background lookup has finished. */
    public void pollDuration() {
        CompletableFuture<Double> future = this.durationFuture;
        if (future == null || !future.isDone()) {
            return;
        }
        this.durationFuture = null;
        try {
            double seconds = future.getNow(-1.0D);
            this.durationTicks = seconds > 0.0D ? (int) Math.round(seconds * 20.0D) : 0;
        } catch (RuntimeException e) {
            this.durationTicks = 0;
        }
    }

    /** @return song length in ticks, or a value <= 0 if not (yet) known. */
    public int getDurationTicks() {
        return this.durationTicks;
    }

    /** Whether this song played to (nearly) its end, as opposed to being cut off by the game. */
    public boolean endedNaturally() {
        if (this.durationTicks <= 0) {
            return this.age > 600;
        }
        return this.age >= this.durationTicks - NATURAL_END_SLACK_TICKS;
    }
}
