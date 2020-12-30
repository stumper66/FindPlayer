package findPlayer;

import org.bukkit.Location;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;

public class WorldGuardStuff {
	public static Boolean CheckForWorldGuard() {
        try {
        	WorldGuard.getInstance();
        } catch (NoClassDefFoundError e) {
            return false;
        }
        
        return true;
	}
	
	public static String GetWorldGuardRegionsForLocation(Location l) {
		com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(l.getWorld());
		
		BlockVector3 position = BlockVector3.at(l.getBlockX(), l.getBlockY(), l.getBlockZ());
		
		RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
		RegionManager regions = container.get(world);
		
		ApplicableRegionSet set = regions.getApplicableRegions(position);
		
		StringBuilder sb = new StringBuilder();
		
		set.forEach((region) -> {
			if (sb.length() > 100) return;
			String name = region.getId();
			if (sb.length() > 0) sb.append(", ");
			sb.append(name);
		});
		
		if (sb.length() > 100) {
			sb.setLength(97);
			sb.append("...");
		}
		
		if (sb.length() > 0) return sb.toString();
		else return null;
	}

}
