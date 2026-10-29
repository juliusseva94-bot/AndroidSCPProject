package com.example.scparchive;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Map;

final class ArchiveDb extends SQLiteOpenHelper {
    ArchiveDb(Context c) { super(c, "scp_archive.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (slug TEXT PRIMARY KEY, scp_id TEXT NOT NULL, title TEXT NOT NULL, url TEXT NOT NULL, image TEXT NOT NULL DEFAULT '', summary TEXT NOT NULL DEFAULT '', author TEXT NOT NULL DEFAULT '', content_file TEXT NOT NULL DEFAULT '', classification TEXT NOT NULL DEFAULT '', tags TEXT NOT NULL DEFAULT '', favorite INTEGER NOT NULL DEFAULT 0, locally_saved INTEGER NOT NULL DEFAULT 0, recently_added INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_scp_id ON entries(scp_id)");
        db.execSQL("CREATE INDEX idx_title ON entries(title)");
        db.execSQL("CREATE INDEX idx_content_file ON entries(content_file)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM entries", null)) {
            c.moveToFirst(); return c.getInt(0);
        }
    }

    // Remote datasets are applied in a single transaction. Existing favorites and local content survive.
    int merge(JSONObject data, boolean preserveExistingSummaries, boolean flagNew) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        int added = 0;
        db.beginTransaction();
        try {
            if (flagNew) db.execSQL("UPDATE entries SET recently_added=0");
            Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject obj = data.optJSONObject(key);
                if (obj == null) continue;
                SCPData.Entry e = SCPData.fromJson(key, obj);
                if (!e.slug.startsWith("scp-") || e.id.length() > 32) continue;
                ContentValues v = new ContentValues();
                v.put("slug", e.slug); v.put("scp_id", e.id); v.put("title", e.name);
                v.put("url", e.url); v.put("image", e.image); v.put("author", e.author);
                v.put("content_file", e.file); v.put("classification", e.classification);
                v.put("tags", e.tags);
                if (!preserveExistingSummaries) v.put("summary", e.summary);
                v.put("recently_added", flagNew ? 1 : 0);
                long newRow = db.insertWithOnConflict("entries", null, v, SQLiteDatabase.CONFLICT_IGNORE);
                if (newRow != -1) { added++; continue; }
                // Do not erase previously cached summaries, favorites, images, or offline flags.
                v.remove("slug"); v.remove("recently_added");
                if (preserveExistingSummaries || e.summary.isEmpty()) v.remove("summary");
                db.update("entries", v, "slug=?", new String[]{e.slug});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return added;
    }

    void updateNames(Map<String,String> titles) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            for (Map.Entry<String,String> entry : titles.entrySet()) {
                v.clear(); v.put("title", entry.getValue());
                db.update("entries", v, "slug=?", new String[]{entry.getKey()});
            }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    List<SCPData.Entry> search(String text, String filter, int limit, int offset) {
        List<SCPData.Entry> result = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<String> args = new ArrayList<>();
        if (text != null && !text.trim().isEmpty()) {
            where.append(" AND (scp_id LIKE ? OR title LIKE ? OR summary LIKE ? OR tags LIKE ?)");
            String pattern = "%" + text.trim() + "%";
            for (int i=0;i<4;i++) args.add(pattern);
        }
        if ("saved".equals(filter)) where.append(" AND locally_saved=1");
        if ("favorites".equals(filter)) where.append(" AND favorite=1");
        if ("new".equals(filter)) where.append(" AND recently_added=1");
        String q = "SELECT * FROM entries" + where +
            " ORDER BY CAST(SUBSTR(scp_id,5) AS INTEGER), scp_id LIMIT " + Math.max(1,limit) + " OFFSET " + Math.max(0,offset);
        try (Cursor c = getReadableDatabase().rawQuery(q, args.toArray(new String[0]))) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    SCPData.Entry get(String slug) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM entries WHERE slug=?", new String[]{slug})) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    private SCPData.Entry fromCursor(Cursor c) {
        SCPData.Entry e = new SCPData.Entry();
        e.slug = c.getString(c.getColumnIndexOrThrow("slug"));
        e.id = c.getString(c.getColumnIndexOrThrow("scp_id"));
        e.name = c.getString(c.getColumnIndexOrThrow("title"));
        e.url = c.getString(c.getColumnIndexOrThrow("url"));
        e.image = c.getString(c.getColumnIndexOrThrow("image"));
        e.summary = c.getString(c.getColumnIndexOrThrow("summary"));
        e.author = c.getString(c.getColumnIndexOrThrow("author"));
        e.file = c.getString(c.getColumnIndexOrThrow("content_file"));
        e.classification = c.getString(c.getColumnIndexOrThrow("classification"));
        e.tags = c.getString(c.getColumnIndexOrThrow("tags"));
        e.favorite = c.getInt(c.getColumnIndexOrThrow("favorite")) != 0;
        e.saved = c.getInt(c.getColumnIndexOrThrow("locally_saved")) != 0;
        e.recent = c.getInt(c.getColumnIndexOrThrow("recently_added")) != 0;
        return e;
    }

    void setFavorite(String slug, boolean value) { setField(slug, "favorite", value ? 1 : 0); }
    void setSaved(String slug, boolean value) { setField(slug, "locally_saved", value ? 1 : 0); }
    private void setField(String slug, String field, int value) {
        ContentValues v = new ContentValues(); v.put(field, value);
        getWritableDatabase().update("entries", v, "slug=?", new String[]{slug});
    }
    void putSummary(String slug, String summary, boolean replace) {
        if (summary == null || summary.isEmpty()) return;
        ContentValues v = new ContentValues(); v.put("summary", summary);
        String selector = replace ? "slug=?" : "slug=? AND summary=''";
        getWritableDatabase().update("entries", v, selector, new String[]{slug});
    }
    // A content file corresponds to multiple SCPs; process its JSON just once.
    int mergeDescriptions(JSONObject content, boolean replace) {
        int updated = 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Iterator<String> it = content.keys();
            while (it.hasNext()) {
                String key = it.next();
                JSONObject obj = content.optJSONObject(key);
                if (obj == null) continue;
                String slug = obj.optString("link", key);
                String summary = SCPData.extractSummary(obj.optString("raw_content", ""));
                if (summary.isEmpty()) continue;
                ContentValues v = new ContentValues(); v.put("summary", summary);
                int n = db.update("entries", v, replace ? "slug=?" : "slug=? AND summary=''", new String[]{slug});
                updated += n;
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return updated;
    }

    List<String> contentFiles(boolean missingOnly) {
        String where = " WHERE content_file<>''" + (missingOnly ? " AND summary=''" : "");
        List<String> files = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT content_file FROM entries" + where + " ORDER BY content_file", null)) {
            while (c.moveToNext()) files.add(c.getString(0));
        }
        return files;
    }

    List<SCPData.Entry> allImages() {
        List<SCPData.Entry> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM entries WHERE image<>'' ORDER BY scp_id", null)) {
            while (c.moveToNext()) list.add(fromCursor(c));
        }
        return list;
    }
}
