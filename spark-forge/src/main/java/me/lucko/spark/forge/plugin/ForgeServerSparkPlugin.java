/*
 * This file is part of spark.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.lucko.spark.forge.plugin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import me.lucko.spark.common.platform.PlatformInfo;
import me.lucko.spark.common.tick.TickHook;
import me.lucko.spark.common.tick.TickReporter;
import me.lucko.spark.forge.ForgeCommandSender;
import me.lucko.spark.forge.ForgePlatformInfo;
import me.lucko.spark.forge.ForgeSparkMod;
import me.lucko.spark.forge.ForgeTickHook;
import me.lucko.spark.forge.ForgeTickReporter;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fmllegacy.server.ServerLifecycleHooks;
import net.minecraftforge.fmlserverevents.FMLServerStoppingEvent;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ForgeServerSparkPlugin extends ForgeSparkPlugin implements Command<CommandSourceStack>, SuggestionProvider<CommandSourceStack> {

    public static void register(ForgeSparkMod mod, RegisterCommandsEvent event) {
        ForgeServerSparkPlugin plugin = new ForgeServerSparkPlugin(mod, ServerLifecycleHooks::getCurrentServer);
        plugin.enable();

        // register listeners
        MinecraftForge.EVENT_BUS.register(plugin);

        // register commands & permissions
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        registerCommands(dispatcher, plugin, plugin, "spark");
        PermissionAPI.registerNode("spark", DefaultPermissionLevel.OP, "Access to the spark command");
    }

    private final Supplier<MinecraftServer> server;

    public ForgeServerSparkPlugin(ForgeSparkMod mod, Supplier<MinecraftServer> server) {
        super(mod);
        this.server = server;
    }

    @SubscribeEvent
    public void onDisable(FMLServerStoppingEvent event) {
        disable();
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String[] args = processArgs(context, false);
        if (args == null) {
            return 0;
        }

        this.threadDumper.ensureSetup();
        this.platform.executeCommand(new ForgeCommandSender(context.getSource().source, this), args);
        return Command.SINGLE_SUCCESS;
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) throws CommandSyntaxException {
        String[] args = processArgs(context, true);
        if (args == null) {
            return Suggestions.empty();
        }

        ServerPlayer player = context.getSource().getPlayerOrException();
        return CompletableFuture.supplyAsync(() -> {
            for (String suggestion : this.platform.tabCompleteCommand(new ForgeCommandSender(player, this), args)) {
                builder.suggest(suggestion);
            }
            return builder.build();
        });
    }

    private static String [] processArgs(CommandContext<CommandSourceStack> context, boolean tabComplete) {
        String[] split = context.getInput().split(" ", tabComplete ? -1 : 0);
        if (split.length == 0 || !split[0].equals("/spark") && !split[0].equals("spark")) {
            return null;
        }

        return Arrays.copyOfRange(split, 1, split.length);
    }

    @Override
    public boolean hasPermission(CommandSource sender, String permission) {
        if (sender instanceof Player) {
            return PermissionAPI.hasPermission((Player) sender, permission);
        } else {
            return true;
        }
    }

    @Override
    public Stream<ForgeCommandSender> getCommandSenders() {
        return Stream.concat(
            this.server.get().getPlayerList().getPlayers().stream(),
            Stream.of(this.server.get())
        ).map(sender -> new ForgeCommandSender(sender, this));
    }

    @Override
    public TickHook createTickHook() {
        return new ForgeTickHook(TickEvent.Type.SERVER);
    }

    @Override
    public TickReporter createTickReporter() {
        return new ForgeTickReporter(TickEvent.Type.SERVER);
    }

    @Override
    public PlatformInfo getPlatformInfo() {
        return new ForgePlatformInfo(PlatformInfo.Type.SERVER);
    }

    @Override
    public String getCommandName() {
        return "spark";
    }
}
