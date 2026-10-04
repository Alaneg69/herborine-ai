package com.herobrineai;

import com.herobrineai.entity.HerobrineEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, HerobrineMod.MODID);

    public static final Supplier<EntityType<HerobrineEntity>> HEROBRINE = ENTITIES.register("herobrine",
            () -> EntityType.Builder.of(HerobrineEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(16)
                    .fireImmune()
                    .build("herobrine"));

    private ModEntities() {}
}
