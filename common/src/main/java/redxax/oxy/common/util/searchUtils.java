package redxax.oxy.common.util;

public class searchUtils {


    public static boolean isFuzzyMatch(String text, String query) {
        String[] queryTokens = query.split("\\s+");
        if (queryTokens.length > 1) {
            int matchedTokens = 0;
            for (String token : queryTokens) {
                if (token.length() < 2) continue;
                if (text.contains(token) || getLevenshteinDistance(text, token) <= Math.max(1, token.length() / 3)) {
                    matchedTokens++;
                }
            }
            if (matchedTokens >= Math.max(1, queryTokens.length / 2)) {
                return true;
            }
        }
        int maxDistance = Math.max(1, query.length() / 3);
        for (String word : text.split("[\\s_.-]+")) {
            if (word.length() < 2) continue;
            if (getLevenshteinDistance(word, query) <= maxDistance) {
                return true;
            }
            if (word.length() > query.length() + 2) {
                for (int i = 0; i <= word.length() - query.length(); i++) {
                    String substring = word.substring(i, i + query.length());
                    if (getLevenshteinDistance(substring, query) <= maxDistance) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static int getLevenshteinDistance(String s1, String s2) {
        if (Math.abs(s1.length() - s2.length()) > Math.min(s1.length(), s2.length()) / 2) {
            return Math.max(s1.length(), s2.length());
        }
        int[] prev = new int[s2.length() + 1];
        int[] curr = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= s1.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i-1) == s2.charAt(j-1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j-1] + 1, prev[j] + 1), prev[j-1] + cost);
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }
        return prev[s2.length()];
    }
}
