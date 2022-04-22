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

}
