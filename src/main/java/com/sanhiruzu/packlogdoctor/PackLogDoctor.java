package com.sanhiruzu.packlogdoctor;

import com.mojang.brigadier.Command;
import com.sanhiruzu.packlogdoctor.runtime.LogDoctorRuntime;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.Path;

@Mod(PackLogDoctor.MOD_ID)
public final class PackLogDoctor {
    public static final String MOD_ID = "packlogdoctor";

    public PackLogDoctor() {
        LogDoctorRuntime.install();
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("packlogdoctor")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("report")
                        .executes(context -> {
                            LogDoctorRuntime runtime = LogDoctorRuntime.install();
                            runtime.writeReport("command");
                            Path htmlPath = runtime.htmlReportPath().toAbsolutePath();
                            Path draftPath = runtime.draftIndexPath().toAbsolutePath();
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Pack Log Doctor wrote latest reports, archived copies, and review drafts under pack-log-doctor/drafts."),
                                    false
                            );
                            context.getSource().sendSuccess(
                                    () -> Component.literal("[Open HTML report]")
                                            .withStyle(style -> style
                                                    .withColor(ChatFormatting.AQUA)
                                                    .withUnderlined(true)
                                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, htmlPath.toString()))),
                                    false
                            );
                            context.getSource().sendSuccess(
                                    () -> Component.literal("[Open draft index]")
                                            .withStyle(style -> style
                                                    .withColor(ChatFormatting.YELLOW)
                                                    .withUnderlined(true)
                                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, draftPath.toString()))),
                                    false
                            );
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("drafts")
                        .executes(context -> {
                            LogDoctorRuntime runtime = LogDoctorRuntime.install();
                            Path draftPath = runtime.draftIndexPath().toAbsolutePath();
                            context.getSource().sendSuccess(
                                    () -> Component.literal("[Open Pack Log Doctor draft index]")
                                            .withStyle(style -> style
                                                    .withColor(ChatFormatting.YELLOW)
                                                    .withUnderlined(true)
                                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, draftPath.toString()))),
                                    false
                            );
                            return Command.SINGLE_SUCCESS;
                        })));
    }
}
