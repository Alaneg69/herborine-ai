package com.herobrineai.client;

import com.herobrineai.HerobrineMod;
import com.herobrineai.entity.HerobrineEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

public class HerobrineRenderer extends HumanoidMobRenderer<HerobrineEntity, HumanoidModel<HerobrineEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HerobrineMod.MODID, "textures/entity/herobrine.png");
    private static final RenderType EYES = RenderType.eyes(
            ResourceLocation.fromNamespaceAndPath(HerobrineMod.MODID, "textures/entity/herobrine_eyes.png"));

    public HerobrineRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new GlowingEyes(this));
    }

    @Override
    public ResourceLocation getTextureLocation(HerobrineEntity entity) {
        return TEXTURE;
    }

    /** Biale oczy swiecace w ciemnosci (jak oczy pajaka/endermana). */
    private static class GlowingEyes extends EyesLayer<HerobrineEntity, HumanoidModel<HerobrineEntity>> {
        GlowingEyes(RenderLayerParent<HerobrineEntity, HumanoidModel<HerobrineEntity>> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return EYES;
        }
    }
}
