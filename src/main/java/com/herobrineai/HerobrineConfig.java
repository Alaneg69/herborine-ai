package com.herobrineai;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class HerobrineConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue ALLOW_TAMPERING;
    public static final ModConfigSpec.BooleanValue DARKNESS_ON_RUSH;
    public static final ModConfigSpec.BooleanValue CHAT_MESSAGES;
    public static final ModConfigSpec.IntValue GRACE_MINUTES;
    public static final ModConfigSpec.IntValue MIN_DELAY_MINUTES;
    public static final ModConfigSpec.IntValue MAX_DELAY_MINUTES;
    public static final ModConfigSpec.DoubleValue LOOK_THRESHOLD;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        ENABLED = b.comment("Czy Herobrine w ogole straszy graczy.")
                .define("enabled", true);

        GRACE_MINUTES = b.comment("Ile minut spokoju po pierwszym wejsciu gracza do swiata.")
                .defineInRange("graceMinutes", 8, 0, 600);
        MIN_DELAY_MINUTES = b.comment("Minimalna przerwa miedzy straszeniami (minuty). Realna przerwa zalezy od tego, jak bardzo gracz sie boi.")
                .defineInRange("minDelayMinutes", 4, 1, 600);
        MAX_DELAY_MINUTES = b.comment("Maksymalna przerwa miedzy straszeniami (minuty).")
                .defineInRange("maxDelayMinutes", 14, 1, 600);

        ALLOW_TAMPERING = b.comment("Czy Herobrine moze gasic pochodnie (usuwa je z swiata). Wylacz, jesli nie chcesz zadnych zmian w terenie.")
                .define("allowWorldTampering", false);
        DARKNESS_ON_RUSH = b.comment("Czy po 'jumpscare' (nagly bieg w Twoja strone) nalozyc na 3 sekundy efekt Ciemnosci. Herobrine nigdy nie zadaje obrazen.")
                .define("darknessOnRush", true);
        CHAT_MESSAGES = b.comment("Czy Herobrine moze pisac na czacie.")
                .define("chatMessages", true);

        LOOK_THRESHOLD = b.comment("Jak dokladnie trzeba na niego patrzec, by uznal, ze zostal zauwazony (cosinus kata; 0.985 = ok. 10 stopni).")
                .defineInRange("lookThreshold", 0.985, 0.8, 0.9999);

        SPEC = b.build();
    }

    private HerobrineConfig() {}
}
