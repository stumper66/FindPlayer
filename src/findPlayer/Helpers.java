package findPlayer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

public class Helpers {
	public static String ReplaceEx(String original, String pattern, String replacement) {
		if (original == null || pattern == null) return null;
		
		int count, position0, position1;
		count = position0 = position1 = 0;
		String upperString = original.toUpperCase();
		String upperPattern = pattern.toUpperCase();
		int inc = (original.length() / pattern.length()) *
				(replacement.length() - pattern.length());
		char[] chars = new char[original.length() + Math.max(0, inc)];
		while ((position1 = upperString.indexOf(upperPattern, position0)) != -1) {
			for (int i = position0; i < position1; ++i)
				chars[count++] = original.charAt(i);
			for (int i = 0; i < replacement.length(); ++i)
				chars[count++] = replacement.charAt(i);
			position0 = position1 + pattern.length();
		}
		if (position0 == 0) return original;
		for (int i = position0; i < original.length(); ++i)
			chars[count++] = original.charAt(i);
		
		return new String(chars, 0, count);
	}
	
	public static boolean isNullOrEmpty(String str){
		if(str == null || str.isEmpty())
			return true;
		return false;
	}
	
	private static class TempHolder {
		public static Boolean suppressWarnings = false;
		public static Boolean suppressErrors = false;
	}
	
	public static void outputIntoConsole(String message, int severity) {
		ChatColor green = ChatColor.GREEN;
		ChatColor yellow = ChatColor.YELLOW;
		ChatColor red = ChatColor.RED;
		final String pluginName = "FindPlayer";
    
		if (severity <= 0) {
			Bukkit.getServer().getConsoleSender().sendMessage(green + "[" + pluginName + "] " + message);
		}
		else if (severity == 1 && !TempHolder.suppressWarnings) {
			Bukkit.getServer().getConsoleSender().sendMessage(yellow + "[" + pluginName + "] " + message);
		}
		else if (severity >= 2 && !TempHolder.suppressErrors) {
			Bukkit.getServer().getConsoleSender().sendMessage(red + "[" + pluginName + "] " + message);
		}
	}

}
