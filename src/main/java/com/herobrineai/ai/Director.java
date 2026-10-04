package com.herobrineai.ai;

import com.herobrineai.HerobrineConfig;
import com.herobrineai.HerobrineMod;
import com.herobrineai.ModAttachments;
import com.herobrineai.ModEntities;
import com.herobrineai.entity.HerobrineEntity;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * "Mozg" Herobrine'a. Dziala w petli:
 * 1. OBSERVE  - ocenia sytuacje gracza (swiatlo, pora dnia, czy jest pod ziemia, czy jest sam, czy ma baze, ile ma pochodni),
 * 2. DECIDE   - wybiera taktyke wazac: nauczona skutecznosc * dopasowanie do sytuacji * brak przyzwyczajenia
 *               * dopasowanie intensywnosci do poziomu strachu gracza (przestraszony -> subtelnie, odporny -> eskalacja),
 * 3. ACT      - wykonuje taktyke,
 * 4. LEARN    - mierzy reakcje gracza (szarpniecia kamera, ucieczka sprintem, odwaga) i aktualizuje profil.
 */
@EventBusSubscriber(modid = HerobrineMod.MODID)
public final class Director {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static long tick = 0;
    private static final Map<UUID, Observation> OBSERVATIONS = new HashMap<>();
    private static final List<Task> TASKS = new ArrayList<>();

    private record Task(long at, Runnable action) {}

    /** Stan jednego trwajacego straszenia + pomiar reakcji gracza. */
    private static final class Observation {
        final ServerPlayer player;
        final Tactic tactic;
        final long started;
        Vec3 focus;
        long endAt = -1;
        float lastYaw, lastPitch;
        double startDist, lastDist;
        int snaps, snapCooldown, fleeTicks, ticks;
        boolean confronted;

        Observation(ServerPlayer player, Tactic tactic, Vec3 focus, long now, long endAt) {
            this.player = player;
            this.tactic = tactic;
            this.focus = focus;
            this.started = now;
            this.endAt = endAt;
            this.lastYaw = player.getYRot();
            this.lastPitch = player.getXRot();
            this.startDist = player.position().distanceTo(focus);
            this.lastDist = startDist;
        }
    }

    /** Opis sytuacji gracza w chwili decyzji. */
    private record Context(boolean surface, boolean dark, boolean night, boolean nearBase,
                           double healthFraction, List<BlockPos> torches) {}

    private Director() {}

    // ------------------------------------------------------------------ eventy

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick++;

        if (!TASKS.isEmpty()) {
            List<Task> due = new ArrayList<>();
            TASKS.removeIf(t -> {
                if (t.at() <= tick) { due.add(t); return true; }
                return false;
            });
            for (Task t : due) t.action().run();
        }

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            observe(player);
            if (tick % 20 == 0) consider(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        OBSERVATIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        OBSERVATIONS.clear();
        TASKS.clear();
    }

    // ------------------------------------------------------------------ publiczne API

    public static FearProfile profile(ServerPlayer player) {
        return player.getData(ModAttachments.FEAR.get());
    }

    /** Encja informuje, ze zniknela (ucieczka, zauwazenie, koniec biegu). */
    public static void finish(UUID playerId, Tactic tactic) {
        Observation o = OBSERVATIONS.get(playerId);
        if (o != null && o.tactic == tactic) conclude(o);
    }

    /** Gracz zaatakowal Herobrine'a - to bedzie liczone jako odwaga. */
    public static void noteConfront(UUID playerId) {
        Observation o = OBSERVATIONS.get(playerId);
        if (o != null) o.confronted = true;
    }

    public static void play(ServerLevel level, String soundId, Vec3 pos, SoundSource source, float volume, float pitch) {
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.withDefaultNamespace(soundId));
        if (event != null) play(level, event, pos, source, volume, pitch);
    }

    public static void play(ServerLevel level, SoundEvent event, Vec3 pos, SoundSource source, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, event, source, volume, pitch);
    }

    /**
     * Wymusza/uruchamia straszenie. {@code forced == null} -> Herobrine sam wybiera taktyke.
     * Zwraca opis wyniku (do komendy), albo null przy sukcesie.
     */
    public static String haunt(ServerPlayer player, Tactic forced) {
        if (!(player.level() instanceof ServerLevel level)) return "Nie jestes na serwerze.";
        if (OBSERVATIONS.containsKey(player.getUUID())) return "Herobrine juz Cie straszy.";

        FearProfile profile = profile(player);
        Context ctx = analyse(level, player);
        RandomSource rng = level.random;
        Set<Tactic> excluded = EnumSet.noneOf(Tactic.class);

        for (int attempt = 0; attempt < Tactic.values().length; attempt++) {
            Tactic chosen = forced != null ? forced : choose(profile, ctx, rng, excluded);
            if (chosen == null) return "Nic nie pasuje do sytuacji.";

            Vec3 focus = execute(level, player, chosen, ctx);
            if (focus != null) {
                profile.habituate(chosen);
                long endAt = chosen.usesEntity ? -1 : tick + (chosen == Tactic.FOOTSTEPS ? 200 : 100);
                OBSERVATIONS.put(player.getUUID(), new Observation(player, chosen, focus, tick, endAt));
                LOGGER.debug("Herobrine -> {} uses {}", player.getGameProfile().getName(), chosen);
                return null;
            }
            if (forced != null) return "Nie udalo sie wykonac taktyki " + forced + " (brak miejsca/celu).";
            excluded.add(chosen);
        }
        return "Zadna taktyka nie byla wykonalna w tym miejscu.";
    }

    // ------------------------------------------------------------------ OBSERVE / LEARN

    private static void observe(ServerPlayer player) {
        Observation o = OBSERVATIONS.get(player.getUUID());
        if (o == null) return;
        o.ticks++;

        float dyaw = Math.abs(Mth.wrapDegrees(player.getYRot() - o.lastYaw));
        float dpitch = Math.abs(player.getXRot() - o.lastPitch);
        if (o.snapCooldown > 0) o.snapCooldown--;
        if ((dyaw > 35 || dpitch > 30) && o.snapCooldown == 0 && o.ticks > 2) {
            o.snaps++;
            o.snapCooldown = 15;
        }
        o.lastYaw = player.getYRot();
        o.lastPitch = player.getXRot();

        double dist = player.position().distanceTo(o.focus);
        if (player.isSprinting() && dist > o.lastDist) o.fleeTicks++;
        o.lastDist = dist;

        boolean timedOut = tick - o.started > 2400;
        if (timedOut || (o.endAt >= 0 && tick >= o.endAt)) conclude(o);
    }

    private static void conclude(Observation o) {
        if (!OBSERVATIONS.remove(o.player.getUUID(), o)) return;

        double distEnd = o.player.position().distanceTo(o.focus);
        double fear = 0.08
                + Math.min(o.snaps, 4) * 0.15
                + Math.min(o.fleeTicks, 30) / 30.0 * 0.30
                + (distEnd - o.startDist > 6 ? 0.20 : 0.0)
                - (o.startDist - distEnd > 8 ? 0.15 : 0.0)
                - (o.confronted ? 0.25 : 0.0);
        fear = Mth.clamp(fear, 0.0, 1.0);

        FearProfile profile = profile(o.player);
        profile.record(o.tactic, fear);

        // Rytm napiecia: duzy strach -> dluzsza cisza (gracz ma sie bac oczekiwania), maly strach -> szybsza eskalacja.
        int min = HerobrineConfig.MIN_DELAY_MINUTES.get();
        int max = Math.max(min, HerobrineConfig.MAX_DELAY_MINUTES.get());
        double base = (min + o.player.getRandom().nextDouble() * (max - min)) * 1200.0;
        profile.setCooldown((int) (base * (0.7 + 0.6 * fear)));

        LOGGER.debug("Herobrine: {} reacted to {} with fear {} (snaps={}, flee={}, confronted={})",
                o.player.getGameProfile().getName(), o.tactic, String.format("%.2f", fear),
                o.snaps, o.fleeTicks, o.confronted);
    }

    // ------------------------------------------------------------------ DECIDE

    private static void consider(ServerPlayer player) {
        if (!HerobrineConfig.ENABLED.get()) return;
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) return;
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (OBSERVATIONS.containsKey(player.getUUID())) return;

        FearProfile profile = profile(player);
        if (!profile.initialised()) {
            profile.markInitialised(HerobrineConfig.GRACE_MINUTES.get() * 1200);
        }
        if (profile.cooldown() > 0) {
            profile.tickCooldown(20);
            return;
        }
        if (haunt(player, null) != null) {
            profile.setCooldown(1200); // nic nie pasowalo - sprobuj za minute
        }
    }

    private static Context analyse(ServerLevel level, ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        boolean surface = level.canSeeSky(pos);
        boolean dark = level.getMaxLocalRawBrightness(pos) <= 7;
        long time = level.getDayTime() % 24000L;
        boolean night = time >= 13000L && time <= 23000L;

        boolean nearBase = false;
        List<BlockPos> torches = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-12, -6, -12), pos.offset(12, 6, 12))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH)) {
                torches.add(p.immutable());
            } else if (!nearBase && (s.is(BlockTags.BEDS) || s.is(Blocks.CRAFTING_TABLE)
                    || s.is(Blocks.FURNACE) || s.is(Blocks.CHEST))) {
                nearBase = true;
            }
        }
        double health = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
        return new Context(surface, dark, night, nearBase, health, torches);
    }

    /** Jak bardzo taktyka pasuje do sytuacji (0 = w ogole nie). */
    private static double fit(Tactic t, Context c) {
        boolean cave = !c.surface() || c.dark();
        return switch (t) {
            case WHISPER -> 1.0 + (!c.surface() ? 0.5 : 0.0);
            case FOOTSTEPS -> cave ? 1.4 : 0.6;
            case TORCH_OUT -> HerobrineConfig.ALLOW_TAMPERING.get() && !c.torches().isEmpty()
                    ? (c.night() || c.dark() ? 1.5 : 0.6) : 0.0;
            case WATCHER -> c.surface() ? (c.night() ? 1.6 : 1.2) : 0.3;
            case STALKER -> c.surface() ? (c.night() ? 1.8 : 0.8) : 0.1;
            case BEHIND -> c.dark() ? 1.5 : 0.5;
            case RUSH -> c.healthFraction() < 0.6 ? 0.0 : (cave ? 1.4 : 0.3);
        };
    }

    private static Tactic choose(FearProfile profile, Context ctx, RandomSource rng, Set<Tactic> excluded) {
        // Nowy gracz dostaje lagodny start; potem: przestraszony -> subtelnie (budowanie napiecia), odporny -> eskalacja.
        double desired = profile.haunts() < 2 ? 0.30 : 0.25 + 0.65 * (1.0 - profile.dread());

        double[] weights = new double[Tactic.values().length];
        double total = 0.0;
        for (Tactic t : Tactic.values()) {
            if (excluded.contains(t)) continue;
            double fit = fit(t, ctx);
            if (fit <= 0.0) continue;
            double match = 0.4 + (1.0 - Math.abs(t.intensity - desired));
            double w = (0.15 + profile.score(t)) * fit * match / (1.0 + profile.habituation(t));
            weights[t.ordinal()] = w;
            total += w;
        }
        if (total <= 0.0) return null;

        double r = rng.nextDouble() * total;
        for (Tactic t : Tactic.values()) {
            r -= weights[t.ordinal()];
            if (weights[t.ordinal()] > 0 && r <= 0) return t;
        }
        return null;
    }

    // ------------------------------------------------------------------ ACT

    /** Zwraca punkt "ogniskowy" straszenia albo null, jesli sie nie da. */
    private static Vec3 execute(ServerLevel level, ServerPlayer player, Tactic tactic, Context ctx) {
        RandomSource rng = level.random;
        return switch (tactic) {
            case WATCHER -> {
                Vec3 spot = spawn(level, player, tactic, 24, 36, 0, 70, true);
                if (spot != null && rng.nextFloat() < 0.3F) whisperChat(player, rng);
                yield spot;
            }
            case BEHIND -> spawn(level, player, tactic, 4, 7, 180, 35, true);
            case STALKER -> spawn(level, player, tactic, 18, 26, 180, 70, false);
            case RUSH -> spawn(level, player, tactic, 20, 28, 0, 40, true);
            case WHISPER -> {
                String[] sounds = {"ambient.cave", "block.portal.ambient", "entity.enderman.stare", "ambient.cave"};
                Vec3 at = behind(player, 6.0);
                play(level, sounds[rng.nextInt(sounds.length)], at, SoundSource.AMBIENT, 1.4F, 0.8F + rng.nextFloat() * 0.3F);
                if (rng.nextFloat() < 0.7F) whisperChat(player, rng);
                yield at;
            }
            case FOOTSTEPS -> {
                for (int i = 0; i < 6; i++) {
                    final double dist = 9.0 - i * 1.4;
                    final long at = tick + i * 9L + rng.nextInt(3);
                    TASKS.add(new Task(at, () -> {
                        if (!player.isAlive() || !(player.level() instanceof ServerLevel l)) return;
                        BlockState under = l.getBlockState(player.blockPosition().below());
                        play(l, under.getSoundType().getStepSound(), behind(player, dist),
                                SoundSource.HOSTILE, 0.9F, 0.85F + l.random.nextFloat() * 0.15F);
                    }));
                }
                yield behind(player, 6.0);
            }
            case TORCH_OUT -> {
                if (ctx.torches().isEmpty()) yield null;
                List<BlockPos> pool = new ArrayList<>(ctx.torches());
                Vec3 focus = Vec3.atCenterOf(pool.get(0));
                int count = 1 + rng.nextInt(3);
                for (int i = 0; i < count && !pool.isEmpty(); i++) {
                    final BlockPos torch = pool.remove(rng.nextInt(pool.size()));
                    TASKS.add(new Task(tick + 15L + i * 25L, () -> snuffTorch(player, torch)));
                }
                yield focus;
            }
        };
    }

    private static void snuffTorch(ServerPlayer player, BlockPos torch) {
        if (!player.isAlive() || !(player.level() instanceof ServerLevel l)) return;
        BlockState s = l.getBlockState(torch);
        if (!s.is(Blocks.TORCH) && !s.is(Blocks.WALL_TORCH)) return;
        // Tylko gdy gracz akurat nie patrzy na ta pochodnie.
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 to = Vec3.atCenterOf(torch).subtract(player.getEyePosition()).normalize();
        if (look.dot(to) > 0.6) return;
        l.setBlock(torch, Blocks.AIR.defaultBlockState(), 3);
        play(l, "block.fire.extinguish", Vec3.atCenterOf(torch), SoundSource.BLOCKS, 0.8F, 1.0F);
        l.sendParticles(ParticleTypes.SMOKE, torch.getX() + 0.5, torch.getY() + 0.7, torch.getZ() + 0.5, 6, 0.1, 0.1, 0.1, 0.01);
    }

    private static void whisperChat(ServerPlayer player, RandomSource rng) {
        if (!HerobrineConfig.CHAT_MESSAGES.get()) return;
        int n = 1 + rng.nextInt(8);
        player.sendSystemMessage(Component.literal("<Herobrine> ")
                .append(Component.translatable("herobrine_ai.whisper." + n, player.getName())));
    }

    private static Vec3 behind(ServerPlayer player, double distance) {
        double a = Math.toRadians(player.getYRot() + 180.0);
        return new Vec3(player.getX() - Math.sin(a) * distance, player.getY(), player.getZ() + Math.cos(a) * distance);
    }

    // ------------------------------------------------------------------ spawn helpers

    private static Vec3 spawn(ServerLevel level, ServerPlayer player, Tactic tactic,
                              double minD, double maxD, double centerDeg, double spreadDeg, boolean needLineOfSight) {
        RandomSource rng = level.random;
        for (int i = 0; i < 20; i++) {
            double d = minD + rng.nextDouble() * (maxD - minD);
            double a = Math.toRadians(player.getYRot() + centerDeg + (rng.nextDouble() * 2 - 1) * spreadDeg);
            int x = Mth.floor(player.getX() - Math.sin(a) * d);
            int z = Mth.floor(player.getZ() + Math.cos(a) * d);

            BlockPos feet = standable(level, x, z, player.getBlockY());
            if (feet == null) continue;

            Vec3 head = new Vec3(feet.getX() + 0.5, feet.getY() + 1.62, feet.getZ() + 0.5);
            if (needLineOfSight && !clear(level, player, head)) continue;

            HerobrineEntity e = ModEntities.HEROBRINE.get().create(level);
            if (e == null) return null;
            Vec3 spot = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            float yaw = (float) (Mth.atan2(player.getZ() - spot.z, player.getX() - spot.x) * (180.0 / Math.PI)) - 90.0F;
            e.moveTo(spot.x, spot.y, spot.z, yaw, 0.0F);
            e.setYHeadRot(yaw);
            e.setYBodyRot(yaw);
            e.init(player.getUUID(), tactic);
            level.addFreshEntity(e);
            return spot;
        }
        return null;
    }

    private static BlockPos standable(ServerLevel level, int x, int z, int aroundY) {
        if (!level.isLoaded(new BlockPos(x, aroundY, z))) return null;
        for (int y = aroundY + 6; y >= aroundY - 8; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos below = feet.below();
            if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) continue;
            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) continue;
            if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) continue;
            if (!level.getFluidState(feet).isEmpty()) continue;
            return feet;
        }
        return null;
    }

    private static boolean clear(ServerLevel level, ServerPlayer player, Vec3 to) {
        return level.clip(new ClipContext(player.getEyePosition(), to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }
}
