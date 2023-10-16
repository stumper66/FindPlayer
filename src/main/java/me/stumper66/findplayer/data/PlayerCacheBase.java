package me.stumper66.findplayer.data;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public class PlayerCacheBase {

    public PlayerCacheBase() {
        this.mapping = new HashMap<>();
        this.nameMappings = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    public HashMap<UUID, PlayerStoreInfo> mapping;
    public final TreeMap<String, UUID> nameMappings;
    public File dataFile;

    public PlayerStoreInfo getPlayerInfo(final String playername) {
        if(!nameMappings.containsKey(playername)) {
            return null;
        }
        UUID userId = nameMappings.get(playername);

        return this.getPlayerInfo(userId);
    }

    public PlayerStoreInfo getPlayerInfo(final UUID userId) {
        if(!mapping.containsKey(userId)) {
            return null;
        }

        return mapping.get(userId);
    }

    @NotNull
    public List<String> getAllPlayers() {
        final List<String> lst = new ArrayList<>(this.mapping.size());

        for(final UUID id : this.mapping.keySet()) {
            PlayerStoreInfo psi = this.mapping.get(id);
            lst.add(psi.playerName);
        }

        return lst;
    }
}
