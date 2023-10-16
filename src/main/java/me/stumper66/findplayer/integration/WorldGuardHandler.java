package me.stumper66.findplayer.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

public class WorldGuardHandler {

    public static boolean hasWorldGuard() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
    }

    @Nullable
    public static String getWorldGuardRegionsForLocation(final Location l) {
        final World world = l.getWorld();
        if(world == null) {
            return null;
        }

        final com.sk89q.worldedit.world.World wg_world = BukkitAdapter.adapt(world);
        final BlockVector3 position = BlockVector3.at(l.getBlockX(), l.getBlockY(), l.getBlockZ());
        final RegionContainer container = WorldGuard.getInstance().getPlatform()
            .getRegionContainer();
        final RegionManager regions = container.get(wg_world);
        if(regions == null) {
            return null;
        }

        final StringBuilder sb = new StringBuilder();

        for(final ProtectedRegion region : regions.getApplicableRegions(position)) {
            if(sb.length() > 100) {
                return null;
            }
            if(sb.length() > 0) {
                sb.append(", ");
            }
            final String name = region.getId();
            sb.append(name);
        }

        if(sb.length() > 100) {
            sb.setLength(97);
            sb.append("...");
        }

        if(sb.length() > 0) {
            return sb.toString();
        } else {
            return null;
        }
    }

}
