package me.libraryaddict.disguise.utilities.packets.packetlisteners;

import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import me.libraryaddict.disguise.LibsDisguises;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.TargetedDisguise;
import me.libraryaddict.disguise.utilities.DisguiseUtilities;
import me.libraryaddict.disguise.utilities.reflection.ReflectionManager;
import me.libraryaddict.disguise.utilities.sounds.SoundGroup;
import me.libraryaddict.disguise.utilities.sounds.SoundGroup.SoundType;
import org.bukkit.Location;
import org.bukkit.entity.*;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

public class PacketListenerSounds extends PacketAdapter {

    /**
     * This is a fix for the stupidity that is "I can't separate the sounds from the sounds the player
     * heard, and the sounds of the entity tracker heard"
     */
    private static boolean cancelSound;

    public PacketListenerSounds(LibsDisguises plugin) {
        super(plugin, ListenerPriority.NORMAL, Server.NAMED_SOUND_EFFECT, Server.ENTITY_STATUS);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (event.isAsync()) {
            return;
        }

        if (event.getPlayer().getName().contains("UNKNOWN[")) {
            return;
        }

        event.setPacket(event.getPacket().deepClone());

        if (event.getPacketType() == Server.ENTITY_STATUS) {
            handleEntityStatus(event);
        } else if (event.getPacketType() == Server.NAMED_SOUND_EFFECT) {
            handleNamedSoundEffect(event);
        }
    }

    private void handleNamedSoundEffect(PacketEvent event) {
        StructureModifier<Object> mods = event.getPacket().getModifier();
        Player observer = event.getPlayer();

        SoundType soundType = null;

        Entity disguisedEntity = null;
        SoundGroup entitySound = null;
        Object soundEffectObj = mods.read(0);

        Disguise disguise = null;

        int[] soundCords = new int[]{(Integer) mods.read(2), (Integer) mods.read(3), (Integer) mods.read(4)};

        for (Set<TargetedDisguise> disguises : DisguiseUtilities.getDisguises().values()) {
            for (TargetedDisguise entityDisguise : disguises) {
                Entity entity = entityDisguise.getEntity();

                if (entity == null || entity.getWorld() != observer.getWorld()) {
                    continue;
                }

                if (!entityDisguise.canSee(observer)) {
                    continue;
                }

                Location loc = entity.getLocation();

                int[] entCords = new int[]{(int) (loc.getX() * 8), (int) (loc.getY() * 8), (int) (loc.getZ() * 8)};

                if (soundCords[0] != entCords[0] || soundCords[1] != entCords[1] || soundCords[2] != entCords[2]) {
                    continue;
                }

                disguise = entityDisguise;
                disguisedEntity = entity;
                entitySound = SoundGroup.getGroup(entity.getType().name());

                if (entitySound == null) {
                    event.setCancelled(true);
                    continue;
                }

                if ((!(entity instanceof LivingEntity)) || ((LivingEntity) entity).getHealth() > 0) {
                    boolean hasInvun = ReflectionManager.hasInvul(entity);
                    soundType = entitySound.getType(soundEffectObj, !hasInvun);
                } else {
                    soundType = SoundType.DEATH;
                }
            }
            if (disguise != null) {
                break;
            }
        }

        if (disguise == null || !disguise.isSoundsReplaced()) {
            return;
        }

        // Blocks null and CANCEL, HURT and DEATH are 100% handled by entity status!
        if (soundType != SoundType.STEP && soundType != SoundType.IDLE) {
            event.setCancelled(true);
            return;
        }

        if (disguisedEntity == observer && !disguise.isSelfDisguiseSoundsReplaced()) {
            return;
        }

        Object sound = null;
        SoundGroup disguiseSound = SoundGroup.getGroup(disguise);

        if (disguiseSound != null) {
            sound = disguiseSound.getSound(soundType);
        }

        if (sound == null) {
            event.setCancelled(true);
            return;
        }

        Enum soundCat = ReflectionManager.getSoundCategory(disguise.getType());
        float volume = (float) mods.read(5);
        float pitch = (float) mods.read(6);

        // If the volume is the default
        if (volume == entitySound.getDamageAndIdleSoundVolume()) {
            volume = disguiseSound.getDamageAndIdleSoundVolume();
        }

        // Here I assume its the default pitch as I can't calculate if its real.
        if (disguise instanceof MobDisguise && disguisedEntity instanceof LivingEntity && ((MobDisguise) disguise).doesDisguiseAge()) {
            boolean baby = false;

            if (disguisedEntity instanceof Zombie) {
                baby = ((Zombie) disguisedEntity).isBaby();
            } else if (disguisedEntity instanceof Ageable) {
                baby = !((Ageable) disguisedEntity).isAdult();
            }

            if (((MobDisguise) disguise).isAdult() == baby) {
                if (baby) {
                    // If the pitch is not the expected
                    if (pitch < 1.5 || pitch > 1.7) {
                        return;
                    }

                    pitch = (DisguiseUtilities.random.nextFloat() - DisguiseUtilities.random.nextFloat()) * 0.2F + 1.5F;
                } else {
                    // If the pitch is not the expected
                    if (pitch < 1 || pitch > 1.2) {
                        return;
                    }

                    pitch = (DisguiseUtilities.random.nextFloat() - DisguiseUtilities.random.nextFloat()) * 0.2F + 1.0F;
                }
            }
        }

        if (sound.getClass().getSimpleName().equals("MinecraftKey")) {
            PacketContainer newPacket = new PacketContainer(Server.CUSTOM_SOUND_EFFECT);
            StructureModifier<Object> newModifs = newPacket.getModifier();

            newModifs.write(0, sound);
            newModifs.write(1, soundCat);

            newModifs.write(2, mods.read(2));
            newModifs.write(3, mods.read(3));
            newModifs.write(4, mods.read(4));
            newModifs.write(5, volume);
            newModifs.write(6, pitch);

            event.setPacket(newPacket);
        } else {
            mods.write(0, sound);
            mods.write(1, soundCat);
            mods.write(5, volume);
            mods.write(6, pitch);
        }
    }

    private void handleEntityStatus(PacketEvent event) {
        StructureModifier<Object> mods = event.getPacket().getModifier();
        Player observer = event.getPlayer();

        if ((byte) mods.read(1) != 2) {
            return;
        }

        // It made a damage animation
        Disguise disguise = DisguiseUtilities.getDisguise(observer, event.getPacket().getIntegers().read(0));

        if (disguise == null) {
            return;
        }

        Entity entity = disguise.getEntity();

        if (disguise instanceof TargetedDisguise) {
            Set<TargetedDisguise> discs = DisguiseUtilities.getDisguises().get(entity.getEntityId());
            for (TargetedDisguise targetedDisguise : discs) {
                if (targetedDisguise != disguise) {
                    continue;
                }
                if (!targetedDisguise.canSee(observer)) {
                    return;
                }
            }
        }

        SoundType soundType = SoundType.HURT;

        if (entity instanceof LivingEntity && ((LivingEntity) entity).getHealth() == 0) {
            soundType = SoundType.DEATH;
        }

        if (entity == event.getPlayer() && !disguise.getType().isPlayer()) {
            if (!disguise.isSelfDisguiseSoundsReplaced()) {
                cancelSound = !cancelSound;
                if (cancelSound) {
                    return;
                }
            }
        }

        SoundGroup disSound = SoundGroup.getGroup(entity.getType().name());

        if (disSound == null) {
            return;
        }

        if (disSound.getSound(soundType) != null) {
            disSound = SoundGroup.getGroup(disguise);

            if (disSound != null) {
                Object sound = disSound.getSound(soundType);

                if (sound != null) {
                    Location loc = entity.getLocation();
                    PacketContainer packet = new PacketContainer(
                            sound.getClass().getSimpleName().equals("MinecraftKey") ? Server.CUSTOM_SOUND_EFFECT : Server.NAMED_SOUND_EFFECT);

                    mods = packet.getModifier();

                    mods.write(0, sound);
                    mods.write(1, ReflectionManager.getSoundCategory(disguise.getType())); // Meh
                    mods.write(2, (int) (loc.getX() * 8D));
                    mods.write(3, (int) (loc.getY() * 8D));
                    mods.write(4, (int) (loc.getZ() * 8D));
                    mods.write(5, disSound.getDamageAndIdleSoundVolume());

                    float pitch;

                    if (disguise instanceof MobDisguise && !((MobDisguise) disguise).isAdult()) {
                        pitch = (DisguiseUtilities.random.nextFloat() - DisguiseUtilities.random.nextFloat()) * 0.2F + 1.5F;
                    } else {
                        pitch = (DisguiseUtilities.random.nextFloat() - DisguiseUtilities.random.nextFloat()) * 0.2F + 1.0F;
                    }

                    if (disguise.getType() == DisguiseType.BAT) {
                        pitch *= 0.95F;
                    }

                    mods.write(6, pitch);

                    event.setCancelled(true);
                    try {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(observer, packet, false);
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}

