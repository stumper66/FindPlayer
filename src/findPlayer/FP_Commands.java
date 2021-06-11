package findPlayer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

public class FP_Commands implements CommandExecutor, TabCompleter {
    public FP_Commands(final FindPlayer main){
        this.main = main;
    }

    private final FindPlayer main;

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
        else if (args[0].equalsIgnoreCase("purge"))
            doPurge(sender);
        else if (args[0].equalsIgnoreCase("player")) {
            if (args.length >= 2)
                getPlayer(sender, args[1]);
            else
                sender.sendMessage("Must specify a player name");
        }
        else if (args[0].equalsIgnoreCase("version"))
            sender.sendMessage("FindPlayer, version " + main.getDescription().getVersion());
        else
            showUsage(sender);

        return true;
    }

    private void showUsage(final @NotNull CommandSender sender){
        final ChatColor g = ChatColor.GREEN;
        final ChatColor y = ChatColor.YELLOW;
        sender.sendMessage(y + "Usage: /findp player" + g + "|" + y + "reload" + g + "|" + y + "purge" + g + "|" + y + "version");
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
            if (sender.hasPermission("FindPlayer.find")) lst.add("player");
            if (sender.hasPermission("FindPlayer.purge")) lst.add("purge");
            lst.add("version");

            return lst;
        }
        else if (args.length == 2 && "player".equalsIgnoreCase(args[0])){
            final List<String> lst = new LinkedList<>();

            for (final Player player : Bukkit.getOnlinePlayers())
                lst.add(player.getName());

            for (final String playerName : main.playerCache.getAllPlayers()) {
                if (!lst.contains(playerName)) lst.add(playerName);
            }

            lst.sort(String.CASE_INSENSITIVE_ORDER);
            return lst;
        }

        return null;
    }
}
