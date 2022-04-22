package me.stumper66.findplayer;

import java.time.LocalDateTime;
import java.util.UUID;
import org.bukkit.Location;

public class PlayerStoreInfo {

    public PlayerStoreInfo(UUID userId) {
        this.userId = userId;
    }

    public PlayerStoreInfo(UUID userId, String playerName, String worldName, Location l) {
        this.userId = userId;
        this.playerName = playerName;
        this.worldName = worldName;
        this.locationX = l.getBlockX();
        this.locationY = l.getBlockY();
        this.locationZ = l.getBlockZ();
    }

    public final UUID userId;
    public String playerName;
    public String worldName;
    public int locationX;
    public int locationY;
    public int locationZ;
    public String regionNames;
    public LocalDateTime lastOnline;
}
