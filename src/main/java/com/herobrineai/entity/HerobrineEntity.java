package com.herobrineai.entity;

import com.herobrineai.HerobrineConfig;
import com.herobrineai.ai.Director;
import com.herobrineai.ai.Tactic;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Herobrine. Nie ma klasycznych goali, bo cala jego "wola" siedzi w {@link Director}:
 * to Director wybiera taktyke, a ta encja ja tylko wykonuje.
 * Nie zadaje obrazen, nie jest zapisywana ze swiatem i znika, gdy ktos go zaatakuje.
 */
public class HerobrineEntity extends PathfinderMob {
    private UUID targetId;
    private Tactic tactic = Tactic.WATCHER;
    private int life;
    private int lookedTicks;
    private boolean vanished;

    public HerobrineEntity(EntityType<? extends HerobrineEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    public void init(UUID target, Tactic tactic) {
        this.targetId = target;
        this.tactic = tactic;
    }

    @Override
    public boolean shouldBeSaved() {
        return false; // nigdy nie zostaje w swiecie po zapisie
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Kazde uderzenie (i kazda inna krzywda) konczy straszenie. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !vanished) {
            if (source.getEntity() instanceof ServerPlayer sp) {
                Director.noteConfront(sp.getUUID());
            }
            vanish();
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || vanished) return;

        ServerPlayer player = targetId == null || getServer() == null
                ? null : getServer().getPlayerList().getPlayer(targetId);
        if (player == null || !player.isAlive() || player.level() != level()) {
            vanish();
            return;
        }

        life++;
        getLookControl().setLookAt(player, 40.0F, 40.0F);

        double dist = distanceTo(player);
        boolean seen = isLookedAtBy(player);
        lookedTicks = seen ? lookedTicks + 1 : 0;

        switch (tactic) {
            case WATCHER -> {
                getNavigation().stop();
                if (lookedTicks >= 30 || dist < 8 || life > 1200) vanish();
            }
            case BEHIND -> {
                getNavigation().stop();
                if (lookedTicks >= 6 || life > 400) vanish();
            }
            case STALKER -> {
                if (seen) {
                    getNavigation().stop();
                } else if (dist > 10 && tickCount % 10 == 0) {
                    getNavigation().moveTo(player, 0.9);
                } else if (dist <= 10) {
                    getNavigation().stop();
                }
                if (dist < 5 || life > 1200 || lookedTicks > 80) vanish();
            }
            case RUSH -> {
                if (tickCount % 5 == 0) getNavigation().moveTo(player, 1.5);
                if (dist < 3.2) {
                    scare(player);
                    vanish();
                } else if (life > 200) {
                    vanish();
                }
            }
            default -> vanish();
        }
    }

    private void scare(ServerPlayer player) {
        Director.play((ServerLevel) level(), "entity.enderman.scream", player.position(),
                SoundSource.HOSTILE, 1.2F, 0.5F);
        if (HerobrineConfig.DARKNESS_ON_RUSH.get()) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
        }
    }

    private boolean isLookedAtBy(ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 toMe = new Vec3(getX() - player.getX(), getEyeY() - player.getEyeY(), getZ() - player.getZ());
        double len = toMe.length();
        if (len < 0.001) return true;
        double dot = look.dot(toMe.scale(1.0 / len));
        return dot > HerobrineConfig.LOOK_THRESHOLD.get() && player.hasLineOfSight(this);
    }

    public void vanish() {
        if (vanished) return;
        vanished = true;
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 14, 0.25, 0.6, 0.25, 0.01);
            if (targetId != null) Director.finish(targetId, tactic);
        }
        discard();
    }
}
