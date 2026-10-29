package com.example.scparchive;

import android.text.Html;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SCPData {
    static final String BASE = "https://scp-data.tedivm.com/data/scp/items/";
    static final String INDEX = BASE + "index.json";

    static final class Entry {
        String slug = "", id = "", name = "", url = "", image = "", summary = "";
        String author = "", file = "", classification = "", tags = "";
        boolean favorite = false, saved = false, recent = false;
    }

    static Entry fromJson(String key, JSONObject raw) {
        Entry e = new Entry();
        e.slug = raw.optString("link", key);
        if (e.slug.isEmpty()) e.slug = key;
        e.id = raw.optString("scp", "");
        if (e.id.isEmpty()) e.id = e.slug.toUpperCase(Locale.US);
        e.name = raw.optString("display_name", raw.optString("title", e.id)).replaceAll("\\s+", " ").trim();
        if (e.name.isEmpty()) e.name = e.id;
        e.url = raw.optString("url", "");
        if (!e.url.startsWith("https://")) e.url = "https://scp-wiki.wikidot.com/" + e.slug;
        e.author = raw.optString("created_by", "");
        e.file = raw.optString("content_file", "");
        e.image = chooseImage(raw.optJSONArray("images"));
        JSONArray tags = raw.optJSONArray("tags");
        if (tags != null) {
            StringBuilder t = new StringBuilder();
            for (int i = 0; i < tags.length(); i++) {
                String tag = tags.optString(i).toLowerCase(Locale.US);
                if (t.length() > 0) t.append(' ');
                t.append(tag);
                if (e.classification.isEmpty() &&
                    (tag.equals("safe") || tag.equals("euclid") || tag.equals("keter") ||
                    tag.equals("thaumiel") || tag.equals("neutralized") || tag.equals("apollyon") ||
                    tag.equals("archon") || tag.equals("esoteric"))) {
                    e.classification = tag.toUpperCase(Locale.US);
                }
            }
            e.tags = t.toString();
        }
        e.summary = raw.optString("summary", "");
        return e;
    }

    static String chooseImage(JSONArray images) {
        if (images == null) return "";
        for (int i = 0; i < images.length(); i++) {
            String url = images.optString(i, "");
            if (url.startsWith("http://")) url = "https://" + url.substring(7);
            // Display only media served by the wiki's own infrastructure.
            if (!url.startsWith("https://")) continue;
            String host;
            try { host = new URI(url).getHost(); } catch (Exception ex) { continue; }
            if (host == null || !(host.endsWith(".wdfiles.com") || host.equals("scp-wiki.wikidot.com"))) continue;
            String path = url.toLowerCase(Locale.US).split("\\?")[0];
            if (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
                path.endsWith(".webp")) return url;
        }
        return "";
    }

    // This is an extractive synopsis from the article's Description, not a generated or canonical summary.
    static String extractSummary(String html) {
        if (html == null || html.isEmpty()) return "";
        String plain = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace('\u00a0', ' ').replaceAll("[ \\t]+", " ")
            .replaceAll("\\n[ \\t]*\\n+", "\\n").trim();
        Matcher match = Pattern.compile("(?im)\\bDescription\\s*:\\s*").matcher(plain);
        String part;
        if (match.find()) {
            part = plain.substring(match.end());
        } else {
            // Nonstandard format. Avoid misrepresenting containment instructions as the description.
            return "";
        }
        part = part.split("(?im)\\n\\s*(?:Addendum|Appendix|Incident Report|Test Log|Interview Log|Footnotes?)\\b", 2)[0];
        part = part.replaceAll("\\s+", " ").trim();
        if (part.length() < 25) return "";
        if (part.length() > 460) {
            String truncated = part.substring(0, 460);
            int cut = Math.max(truncated.lastIndexOf(". "), truncated.lastIndexOf("! "));
            if (cut > 150) return truncated.substring(0, cut + 1);
            cut = truncated.lastIndexOf(' ');
            return truncated.substring(0, Math.max(200, cut)) + "…";
        }
        return part;
    }

    static Map<String,String> seriesNames(String html) {
        Map<String,String> names = new HashMap<>();
        String plain = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString();
        Matcher matcher = Pattern.compile("(?m)^\\s*(SCP-\\d{3,5})\\s*[-–]\\s*(.+)$", Pattern.CASE_INSENSITIVE).matcher(plain);
        while (matcher.find()) {
            String name = matcher.group(2).trim();
            if (name.length() > 1 && name.length() < 160)
                names.put(matcher.group(1).toLowerCase(Locale.US), name);
        }
        return names;
    }

    static String safeContentUrl(String file) {
        if (file == null || !file.matches("[a-zA-Z0-9_./-]+") || file.contains("..") || file.startsWith("/"))
            throw new IllegalArgumentException("Invalid content file");
        return BASE + file;
    }

    static String summaryFor(JSONObject content, String slug) {
        JSONObject record = content.optJSONObject(slug);
        if (record == null) {
            Iterator<String> keys = content.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject candidate = content.optJSONObject(key);
                if (candidate != null && slug.equals(candidate.optString("link"))) {
                    record = candidate;
                    break;
                }
            }
        }
        return record == null ? "" : extractSummary(record.optString("raw_content", ""));
    }
}
