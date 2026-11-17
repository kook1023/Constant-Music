package com.constantmusic.client;

import com.constantmusic.ConstantMusicMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ConstantMusicMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {

    private static final StackWalker STACK = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            MusicController.tick(Minecraft.getInstance());
        } catch (RuntimeException e) {
            ConstantMusicMod.LOGGER.error("Constant Music hit an error and will retry shortly", e);
            MusicController.reset();
        }
    }

    /**
     * Silences songs started by vanilla's MusicManager (we play the music ourselves).
     * Only sounds that really come from MusicManager are blocked, so music discs, other mods'
     * own sounds and our own songs are untouched.
     */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound instanceof ConstantMusicInstance || sound.getSource() != SoundSource.MUSIC) {
            return;
        }
        boolean fromVanillaMusicManager = STACK.walk(
                frames -> frames.anyMatch(frame -> frame.getDeclaringClass() == MusicManager.class));
        if (fromVanillaMusicManager) {
            event.setSound(null);
        }
    }
}
