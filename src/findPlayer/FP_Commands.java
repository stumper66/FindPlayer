package findPlayer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class FP_Commands implements CommandExecutor, TabCompleter {
    public FP_Commands(final FindPlayer main){
        this.main = main;
        this.userFilterOptions = new TreeMap<>();
    }

    private final FindPlayer main;
    private final Map<UUID, FilterOption> userFilterOptions;

    @Override
    public boolean onCommand(final @NotNull CommandSender sender, final Command cmd, final @NotNull String label, final String[] args) {
        if (!cmd.getName().equalsIgnoreCase("findp"))
            return true;

        if(args.length == 0 || args.length > 2) {
            showUsage(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload"))
            doReload(sender);
        else if (args[0].equalsIgnoreCase("filter"))
            doFilter(sender, args);
        else if (args[0].equalsIgnoreCase("purge"))
            doPurge(sender);
        else if (args[0].equalsIgnoreCase("player")) {
            if (args.length >= 2)
                getPlayer(sender, args[1]);
            else
                sender.sendMessage("Must specify a player name");
        }
        else if (args[0].equalsIgnoreCase("version")) {
            sender.sendMessage("FindPlayer, version " + main.getDescription().getVersion());
            sender.sendMessage("Current logging mode: " + main.loggingType);
        }
        else
            showUsage(sender);

        return true;
    }

    private void showUsage(final @NotNull CommandSender sender){
        final ChatColor g = ChatColor.GREEN;
        final ChatColor y = ChatColor.YELLOW;
        sender.sendMessage(y + "Usage: /findp " + g + "filter" + "|" + " player" + g + "|" + y + "reload" + g + "|" + y + "purge" + g + "|" + y + "version");
    }

    private void doFilter(final @NotNull CommandSender sender, final String[] args){
        if (!sender.hasPermission("FindPlayer.player")) {
            sender.sendMessage(ChatColor.RED + "You don't have permissions for this command");
            return;
        }

        if (!(sender instanceof Player)){
            sender.sendMessage(ChatColor.RED + "This option can only be run by a player");
            return;
        }

        final Player player = (Player) sender;
        FilterOption filter = this.userFilterOptions.containsKey(player.getUniqueId()) ?
                this.userFilterOptions.get(player.getUniqueId()) : main.defaultFilter;

        if (args.length < 2)
            sender.sendMessage("Current filter option is: " + ChatColor.GREEN + filter);
        else {
            final FilterOption oldOption = filter;
            try{
                filter = FilterOption.valueOf(args[1].toUpperCase());
            }
            catch (Exception e){
                sender.sendMessage("Invalid filter option: " + ChatColor.RED + args[1]);
                return;
            }

            this.userFilterOptions.put(player.getUniqueId(), filter);

            if (filter == oldOption)
                sender.sendMessage("New filter option was the same as before: " + ChatColor.YELLOW + filter);
            else
                sender.sendMessage("Updated filter option to: " + ChatColor.YELLOW + filter);
        }
    }

    private FilterOption getFilterOption(final CommandSender sender){
        if (!(sender instanceof Player))
            return main.defaultFilter;

        final Player player = (Player) sender;

        if (this.userFilterOptions.containsKey(player.getUniqueId()))
            return this.userFilterOptions.get(player.getUniqueId());

        return main.defaultFilter;
    }

    private void getPlayer(final CommandSender sender, final String playername){
        if (!sender.hasPermission("FindPlayer.player")) {
            sender.sendMessage(ChatColor.RED + "You don't have permissions for this command");
            return;
        }

        final String sendMsg = main.getMessageForPlayer(playername);
        sender.sendMessage(sendMsg);
    }

    private void doPurge(final @NotNull CommandSender sender){
        if (!sender.hasPermission("FindPlayer.purge")) {
            sender.sendMessage(ChatColor.RED + "You don't have permissions for this command");
            return;
        }
        main.playerCache.purgeData();
        sender.sendMessage(ChatColor.YELLOW + "Purged all cached data.");
    }

    private void doReload(final @NotNull CommandSender sender){
        if (!sender.hasPermission("FindPlayer.reload")) {
            sender.sendMessage(ChatColor.RED + "You don't have permissions for this command");
            return;
        }

        main.saveDefaultConfig();
        main.reloadConfig();
        main.config = main.getConfig();
        main.processConfig(sender);
        sender.sendMessage(ChatColor.YELLOW + "Reloaded the config.");
    }

    @Override
    public List<String> onTabComplete(final @NotNull CommandSender sender, final @NotNull Command cmd, final @NotNull String alias, @NotNull final String[] args) {
        if (args.length == 1) {
            final List<String> lst = new LinkedList<>();
            if (sender.hasPermission("FindPlayer.reload")) lst.add("reload");
            if (sender.hasPermission("FindPlayer.player")) {
                lst.add("player");
                lst.add("filter");
            }
            if (sender.hasPermission("FindPlayer.purge")) lst.add("purge");
            lst.add("version");

            return lst;
        }
        else if (args.length == 2 && "player".equalsIgnoreCase(args[0]) && sender.hasPermission("FindPlayer.player")){
            final List<String> lst = new LinkedList<>();

            final FilterOption filterOption = getFilterOption(sender);

            if (!filterOption.equals(FilterOption.OFFLINE)) {
                for (final Player player : Bukkit.getOnlinePlayers())
                    lst.add(player.getName());
            }

            if (filterOption.equals(FilterOption.ALL)) {
                for (final String playerName : main.playerCache.getAllPlayers()) {
                    if (!lst.contains(playerName)) lst.add(playerName);
                }
            }
            else if (filterOption.equals(FilterOption.OFFLINE)) {
                final List<String> onlinePlayers = new LinkedList<>();
                for (Player player : Bukkit.getOnlinePlayers())
                    onlinePlayers.add(player.getName());

                for (final String playerName : main.playerCache.getAllPlayers()) {
                    if (!onlinePlayers.contains(playerName)) lst.add(playerName);
                }
            }

            lst.sort(String.CASE_INSENSITIVE_ORDER);
            return lst;
        }
        else if (args.length == 2 && "filter".equalsIgnoreCase(args[0]) && sender.hasPermission("FindPlayer.player"))
            return Arrays.asList("all", "online" , "offline");

        return null;
    }
}
