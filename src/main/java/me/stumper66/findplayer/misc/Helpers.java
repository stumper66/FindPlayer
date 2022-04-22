package me.stumper66.findplayer.misc;

import me.lokka30.microlib.MicroLogger;
import org.jetbrains.annotations.NotNull;

//TODO remove
public class Helpers {

    //TODO remove?
    public static String replaceIgnoreCase(String original, String pattern, String replacement) {
        if(original == null || pattern == null) {
            return null;
        }

        int count = 0;
        int position0 = 0;
        int position1;
        final String upperString = original.toUpperCase();
        final String upperPattern = pattern.toUpperCase();
        final int inc = (original.length() / pattern.length()) *
            (replacement.length() - pattern.length());
        final char[] chars = new char[original.length() + Math.max(0, inc)];
        while((position1 = upperString.indexOf(upperPattern, position0)) != -1) {
            for(int i = position0; i < position1; ++i) {
                chars[count++] = original.charAt(i);
            }
            for(int i = 0; i < replacement.length(); ++i) {
                chars[count++] = replacement.charAt(i);
            }
            position0 = position1 + pattern.length();
        }
        if(position0 == 0) {
            return original;
        }
        for(int i = position0; i < original.length(); ++i) {
            chars[count++] = original.charAt(i);
        }

        return new String(chars, 0, count);
    }

    //TODO remove: use Bukkit logger instead.
    @NotNull
    public static final MicroLogger logger = new MicroLogger("&b&lFindPlayer: &7");
}
