package com.herobrineai.ai;

/**
 * Taktyki straszenia. {@code intensity} (0..1) to jak mocne jest dane straszenie;
 * Herobrine dobiera intensywnosc do tego, jak bardzo gracz juz sie boi.
 */
public enum Tactic {
    WHISPER(0.20, false),     // dziwny dzwiek za plecami (+ czasem wiadomosc na czacie)
    FOOTSTEPS(0.30, false),   // kroki, ktore zblizaja sie zza plecow i nagle cichna
    TORCH_OUT(0.35, false),   // gasnace pochodnie wokol gracza
    WATCHER(0.50, true),      // stoi w oddali i patrzy, znika gdy na niego spojrzysz
    STALKER(0.70, true),      // idzie za graczem, ale tylko gdy ten nie patrzy
    BEHIND(0.80, true),       // pojawia sie tuz za plecami
    RUSH(1.00, true);         // biegnie na gracza i znika tuz przed nim

    public final double intensity;
    public final boolean usesEntity;

    Tactic(double intensity, boolean usesEntity) {
        this.intensity = intensity;
        this.usesEntity = usesEntity;
    }
}
