package com.example.callblocker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Keeps the settings, the number patterns and the blocked-call log, and does the matching. */
public final class RuleStore {

    private static final String PREFS = "blocker_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_RULES = "rules";
    private static final String KEY_LOG = "log";
    private static final int MAX_LOG_ENTRIES = 50;

    private static String lastBlockedNumber = "";
    private static long lastBlockedAt = 0L;

    private RuleStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- enabled flag

    public static boolean isEnabled(Context c) {
        return prefs(c).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    // ---------------------------------------------------------------- patterns

    public static List<String> getRules(Context c) {
        return readList(c, KEY_RULES);
    }

    /** @return false if the pattern is empty, has no digits, or already exists. */
    public static boolean addRule(Context c, String rule) {
        if (rule == null) return false;
        String r = rule.trim();
        if (normalize(r).replace("*", "").isEmpty()) return false;
        List<String> rules = getRules(c);
        if (rules.contains(r)) return false;
        rules.add(r);
        writeList(c, KEY_RULES, rules);
        return true;
    }

    public static void removeRule(Context c, String rule) {
        List<String> rules = getRules(c);
        rules.remove(rule);
        writeList(c, KEY_RULES, rules);
    }

    // ---------------------------------------------------------------- matching

    /**
     * Keeps only digits and '*'. A leading "00" (international prefix) is dropped and '+' is ignored,
     * so "+1456...", "001456..." and "1456..." are all compared the same way.
     */
    static String normalize(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if ((ch >= '0' && ch <= '9') || ch == '*') sb.append(ch);
        }
        String s = sb.toString();
        if (s.startsWith("00")) s = s.substring(2);
        return s;
    }

    private static Pattern toRegex(String normalizedPattern) {
        StringBuilder re = new StringBuilder("^");
        for (int i = 0; i < normalizedPattern.length(); i++) {
            char ch = normalizedPattern.charAt(i);
            if (ch == '*') re.append(".*");
            else re.append(ch); // digits are safe in a regex
        }
        re.append('$');
        return Pattern.compile(re.toString());
    }

    public static boolean shouldBlock(Context c, String number) {
        if (!isEnabled(c)) return false;
        String n = normalize(number);
        if (n.isEmpty()) return false;
        for (String rule : getRules(c)) {
            String p = normalize(rule);
            if (p.isEmpty()) continue;
            if (toRegex(p).matcher(n).matches()) return true;
        }
        return false;
    }

    /**
     * Both the receiver and the screening service may see the same call.
     * Only the first one to claim it should act on / log it.
     */
    static synchronized boolean claim(String number) {
        String n = normalize(number);
        long now = System.currentTimeMillis();
        if (n.equals(lastBlockedNumber) && now - lastBlockedAt < 8000) return false;
        lastBlockedNumber = n;
        lastBlockedAt = now;
        return true;
    }

    // ---------------------------------------------------------------- log

    public static void addLog(Context c, String text) {
        List<String> log = readList(c, KEY_LOG);
        log.add(0, System.currentTimeMillis() + "|" + text);
        while (log.size() > MAX_LOG_ENTRIES) log.remove(log.size() - 1);
        writeList(c, KEY_LOG, log);
    }

    public static List<String> getLog(Context c) {
        return readList(c, KEY_LOG);
    }

    public static void clearLog(Context c) {
        prefs(c).edit().remove(KEY_LOG).apply();
    }

    // ---------------------------------------------------------------- helpers

    private static List<String> readList(Context c, String key) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(key, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (JSONException ignored) {
        }
        return out;
    }

    private static void writeList(Context c, String key, List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        prefs(c).edit().putString(key, arr.toString()).apply();
    }
}
