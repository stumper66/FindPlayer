package me.stumper66.findplayer;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PAPI_Manager extends PlaceholderExpansion {

    public PAPI_Manager(final FindPlayer main) {
        this.main = main;
    }

    private final FindPlayer main;

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @NotNull
    String getIdentifier() {
        return main.getDescription().getName();
    }

    @Override
    public @NotNull
    String getAuthor() {
        return main.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull
    String getVersion() {
        return main.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(final Player player, final @NotNull String identifier) {
        if(player == null) {
            return "";
        }

        if("location".equalsIgnoreCase(identifier)) {
            return main.getMessageForPlayer(player.getName());
        }

        return null;
    }
}
