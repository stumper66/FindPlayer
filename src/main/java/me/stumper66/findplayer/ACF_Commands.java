package me.stumper66.findplayer;

import co.aikar.commands.*;
import co.aikar.commands.annotation.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@CommandAlias("fp|findp")
public class ACF_Commands extends BaseCommand {

    public ACF_Commands(final FindPlayer main) {
        this.main = main;
        this.userFilterOptions = new TreeMap<>();
        registerCommandCompletions();
    }

    private final FindPlayer main;
    private final Map<UUID, FilterOption> userFilterOptions;

    @CatchUnknown
    @ACF_Method
    public void onUnknown(final CommandSender sender) {
        sender.sendMessage("you have entered an unknown command");
    }

    @Subcommand("reload")
    @CommandPermission("FindPlayer.reload")
    @ACF_Method
    public void onReload(final CommandSender sender) {
        main.saveDefaultConfig();
        main.reloadConfig();
        main.config = main.getConfig();
        main.processConfig(sender);
        sender.sendMessage(ChatColor.YELLOW + "Reloaded the config.");
    }

    @Subcommand("purge")
    @CommandPermission("FindPlayer.purge")
    @ACF_Method
    public void onPurge(final CommandSender sender) {
        main.playerCache.purgeData();
        sender.sendMessage(ChatColor.YELLOW + "Purged all cached data.");
    }

    @HelpCommand
    @Subcommand("help")
    @ACF_Method
    public static void onHelp(final CommandSender sender, final CommandHelp help) {
        //sendMsg(sender, heading("FindPlayer Help"));
        //final ChatColor g = ChatColor.GREEN;
        //final ChatColor y = ChatColor.YELLOW;
        //sender.sendMessage(y + "Usage: /findp " + g + "filter" + "|" + " player" + g + "|" + y + "reload" + g + "|" + y + "purge" + g + "|" + y + "version");
        help.showHelp();
    }

    @Subcommand("filter")
    @CommandPermission("FindPlayer.player")
    @CommandCompletion("all|online|offline")
    @ACF_Method
    public void onFilter(final @NotNull CommandSender sender, final String[] args) {
        if(!(sender instanceof Player) && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(
                ChatColor.RED + "This option can only be run by a player or console");
            return;
        }

        final UUID useId = sender instanceof Player ?
            ((Player) sender).getUniqueId() :
            UUID.fromString("eca820af-8715-43b9-8520-f17b2094cf18");

        FilterOption filter = this.userFilterOptions.containsKey(useId) ?
            this.userFilterOptions.get(useId) : main.defaultFilter;

        if(args.length == 0) {
            sender.sendMessage("Current filter option is: " + ChatColor.GREEN + filter);
        } else {
            final FilterOption oldOption = filter;
            try {
                filter = FilterOption.valueOf(args[0].toUpperCase());
            } catch(Exception e) {
                sender.sendMessage("Invalid filter option: " + ChatColor.RED + args[0]);
                return;
            }

            this.userFilterOptions.put(useId, filter);

            if(filter == oldOption) {
                sender.sendMessage(
                    "New filter option was the same as before: " + ChatColor.YELLOW + filter);
            } else {
                sender.sendMessage("Updated filter option to: " + ChatColor.YELLOW + filter);
            }
        }
    }

    private FilterOption getFilterOption(final CommandSender sender) {
        if(!(sender instanceof Player)) {
            return main.defaultFilter;
        }

        final Player player = (Player) sender;

        if(this.userFilterOptions.containsKey(player.getUniqueId())) {
            return this.userFilterOptions.get(player.getUniqueId());
        }

        return main.defaultFilter;
    }

    @Subcommand("player")
    @CommandPermission("FindPlayer.player")
    @CommandCompletion("@players")
    @ACF_Method
    public void getPlayer(final CommandSender sender, final String playername) {
        final String sendMsg = main.getMessageForPlayer(playername);
        sender.sendMessage(sendMsg);
    }

    @Subcommand("info")
    @ACF_Method
    public void onInfo(final CommandSender sender) {
        sender.sendMessage("FindPlayer, version " + main.getDescription().getVersion());
        sender.sendMessage("Current logging mode: " + main.loggingType);
    }

    private void registerCommandCompletions() {
        final CommandCompletions<BukkitCommandCompletionContext> commandCompletions = main.commandManager.getCommandCompletions();

        commandCompletions.registerAsyncCompletion("players", c -> {
            final List<String> lst = new LinkedList<>();

            final FilterOption filterOption = getFilterOption(c.getSender());

            if(!filterOption.equals(FilterOption.OFFLINE)) {
                for(final Player player : Bukkit.getOnlinePlayers()) {
                    lst.add(player.getName());
                }
            }

            if(filterOption.equals(FilterOption.ALL)) {
                for(final String playerName : main.playerCache.getAllPlayers()) {
                    if(!lst.contains(playerName)) {
                        lst.add(playerName);
                    }
                }
            } else if(filterOption.equals(FilterOption.OFFLINE)) {
                final List<String> onlinePlayers = new LinkedList<>();
                for(Player player : Bukkit.getOnlinePlayers()) {
                    onlinePlayers.add(player.getName());
                }

                for(final String playerName : main.playerCache.getAllPlayers()) {
                    if(!onlinePlayers.contains(playerName)) {
                        lst.add(playerName);
                    }
                }
            }

            lst.sort(String.CASE_INSENSITIVE_ORDER);
            return lst;
        });
    }
}
