package com.muthumol.player.dummy;

/** Bounded local title matching. Exact words rank before a single typo or transposition. */
final class LibrarySearch {
  static int rank(String title, String query) {
    String hay = MediaTitle.normalize(title.substring(0, Math.min(512, title.length())));
    String needle = MediaTitle.normalize(query.substring(0, Math.min(128, query.length())));
    if (needle.isEmpty()) return 0;
    if (hay.equals(needle)) return 0;
    String[] words = hay.split(" +");
    int score = 1;
    for (String wanted : needle.split(" +")) {
      if (hay.contains(wanted)) continue;
      boolean near = false;
      if (wanted.length() >= 4) for (String word : words) {
        if (oneEdit(wanted, word)) { near = true; break; }
      }
      if (!near) return Integer.MAX_VALUE;
      score += 2;
    }
    return score;
  }

  static boolean oneEdit(String a, String b) {
    if (Math.abs(a.length() - b.length()) > 1) return false;
    int i = 0;
    while (i < Math.min(a.length(), b.length()) && a.charAt(i) == b.charAt(i)) i++;
    if (i == Math.min(a.length(), b.length())) return true;
    if (a.length() == b.length()) {
      if (a.substring(i + 1).equals(b.substring(i + 1))) return true;
      return i + 1 < a.length() && a.charAt(i) == b.charAt(i + 1)
          && a.charAt(i + 1) == b.charAt(i)
          && a.substring(i + 2).equals(b.substring(i + 2));
    }
    return a.length() > b.length()
        ? a.substring(i + 1).equals(b.substring(i))
        : a.substring(i).equals(b.substring(i + 1));
  }
}
