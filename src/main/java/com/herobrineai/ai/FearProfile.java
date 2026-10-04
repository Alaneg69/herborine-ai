package com.herobrineai.ai;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * To, co Herobrine "wie" o graczu: ktore taktyki dzialaja (score), do ktorych gracz sie
 * przyzwyczail (habituation), jak bardzo jest ogolnie przestraszony (dread).
 */
public class FearProfile implements INBTSerializable<CompoundTag> {
    private final double[] score = new double[Tactic.values().length];
    private final double[] habituation = new double[Tactic.values().length];
    private double dread = 0.5;
    private int haunts = 0;
    private int cooldown = 0;
    private boolean initialised = false;

    public FearProfile() {
        java.util.Arrays.fill(score, 0.5);
    }

    public double score(Tactic t) { return score[t.ordinal()]; }
    public double habituation(Tactic t) { return habituation[t.ordinal()]; }
    public double dread() { return dread; }
    public int haunts() { return haunts; }
    public int cooldown() { return cooldown; }
    public boolean initialised() { return initialised; }

    public void markInitialised(int startCooldownTicks) {
        this.initialised = true;
        this.cooldown = startCooldownTicks;
    }

    public void setCooldown(int ticks) { this.cooldown = Math.max(0, ticks); }
    public void tickCooldown(int ticks) { this.cooldown = Math.max(0, cooldown - ticks); }

    /** Wywolywane przy starcie straszenia: gracz powoli przyzwyczaja sie do uzytej taktyki. */
    public void habituate(Tactic used) {
        for (int i = 0; i < habituation.length; i++) habituation[i] *= 0.85;
        habituation[used.ordinal()] += 1.0;
        haunts++;
    }

    /** Wywolywane na koncu straszenia z zmierzona reakcja gracza (0 = zero strachu, 1 = panika). */
    public void record(Tactic used, double fear) {
        int i = used.ordinal();
        score[i] = score[i] * 0.65 + fear * 0.35;
        dread = dread * 0.7 + fear * 0.3;
    }

    public void reset() {
        java.util.Arrays.fill(score, 0.5);
        java.util.Arrays.fill(habituation, 0.0);
        dread = 0.5;
        haunts = 0;
        cooldown = 0;
        initialised = false;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        for (Tactic t : Tactic.values()) {
            tag.putDouble("score_" + t.name(), score[t.ordinal()]);
            tag.putDouble("hab_" + t.name(), habituation[t.ordinal()]);
        }
        tag.putDouble("dread", dread);
        tag.putInt("haunts", haunts);
        tag.putInt("cooldown", cooldown);
        tag.putBoolean("init", initialised);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        for (Tactic t : Tactic.values()) {
            if (tag.contains("score_" + t.name())) score[t.ordinal()] = tag.getDouble("score_" + t.name());
            if (tag.contains("hab_" + t.name())) habituation[t.ordinal()] = tag.getDouble("hab_" + t.name());
        }
        if (tag.contains("dread")) dread = tag.getDouble("dread");
        haunts = tag.getInt("haunts");
        cooldown = tag.getInt("cooldown");
        initialised = tag.getBoolean("init");
    }
}
