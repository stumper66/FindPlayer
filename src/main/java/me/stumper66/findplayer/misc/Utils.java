package me.stumper66.findplayer.misc;

public class Utils {

    private Utils() {
        throw new IllegalStateException("Instantiation of utility-type class");
    }

    /*
    Provides a function similar to J11+'s String#repeat.
     */
    public static String repeatString(final String toRepeat, final int amount) {
        final StringBuilder stringBuilder = new StringBuilder();
        for(int i = 1; i <= amount; i++) {
            stringBuilder.append(toRepeat);
        }
        return stringBuilder.toString();
    }

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

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

}
