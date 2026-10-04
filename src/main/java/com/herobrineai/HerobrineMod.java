package com.herobrineai;

import com.herobrineai.entity.HerobrineEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(HerobrineMod.MODID)
public class HerobrineMod {
    public static final String MODID = "herobrine_ai";

    public HerobrineMod(IEventBus modBus, ModContainer container) {
        ModEntities.ENTITIES.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        modBus.addListener(this::registerAttributes);
        container.registerConfig(ModConfig.Type.COMMON, HerobrineConfig.SPEC);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.HEROBRINE.get(), HerobrineEntity.createAttributes().build());
    }
}
