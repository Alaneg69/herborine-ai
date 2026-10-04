package com.herobrineai;

import com.herobrineai.ai.FearProfile;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HerobrineMod.MODID);

    /** Pamiec Herobrine'a o danym graczu. Zapisywana z graczem i kopiowana po smierci. */
    public static final Supplier<AttachmentType<FearProfile>> FEAR = ATTACHMENTS.register("fear",
            () -> AttachmentType.serializable(FearProfile::new).copyOnDeath().build());

    private ModAttachments() {}
}
