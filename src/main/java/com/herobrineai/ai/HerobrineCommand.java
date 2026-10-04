package com.herobrineai.ai;

import com.herobrineai.HerobrineMod;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /herobrine haunt [taktyka] | profile | reset  (wymaga uprawnien operatora) */
@EventBusSubscriber(modid = HerobrineMod.MODID)
public final class HerobrineCommand {
    private HerobrineCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("herobrine")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("haunt")
                        .executes(ctx -> haunt(ctx, null))
                        .then(Commands.argument("tactic", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (Tactic t : Tactic.values()) {
                                        String name = t.name().toLowerCase(Locale.ROOT);
                                        if (name.startsWith(builder.getRemainingLowerCase())) builder.suggest(name);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> haunt(ctx, StringArgumentType.getString(ctx, "tactic")))))
                .then(Commands.literal("profile").executes(HerobrineCommand::profile))
                .then(Commands.literal("reset").executes(HerobrineCommand::reset)));
    }

    private static int haunt(CommandContext<CommandSourceStack> ctx, String tacticName) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Tactic tactic = null;
        if (tacticName != null) {
            try {
                tactic = Tactic.valueOf(tacticName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                ctx.getSource().sendFailure(Component.literal("Nieznana taktyka. Dostepne: "
                        + Arrays.stream(Tactic.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toList()));
                return 0;
            }
        }
        String problem = Director.haunt(player, tactic);
        if (problem != null) {
            ctx.getSource().sendFailure(Component.literal(problem));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Herobrine budzi sie..."), false);
        return 1;
    }

    private static int profile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        FearProfile p = Director.profile(player);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "Strach ogolny: %.2f | straszen: %d razy | nastepne za ~%d s%n",
                p.dread(), p.haunts(), p.cooldown() / 20));
        for (Tactic t : Tactic.values()) {
            sb.append(String.format(Locale.ROOT, "%-10s skutecznosc %.2f, przyzwyczajenie %.2f%n",
                    t.name().toLowerCase(Locale.ROOT), p.score(t), p.habituation(t)));
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Director.profile(ctx.getSource().getPlayerOrException()).reset();
        ctx.getSource().sendSuccess(() -> Component.literal("Herobrine zapomnial wszystko o Tobie."), false);
        return 1;
    }
}
