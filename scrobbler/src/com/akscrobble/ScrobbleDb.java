package com.akscrobble;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import com.akscrobble.core.Const;
import com.akscrobble.core.PendingScrobble;
import com.akscrobble.core.QueuePolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScrobbleDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "scrobbles.db";
    private static final int DB_VERSION = 1;

    public ScrobbleDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE scrobbles ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "dedup_key TEXT NOT NULL UNIQUE,"
                + "artist TEXT NOT NULL,track TEXT NOT NULL,album TEXT,album_artist TEXT,"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                + "track_number INTEGER NOT NULL DEFAULT 0,"
                + "started_at INTEGER NOT NULL,source_pkg TEXT NOT NULL,"
                + "state INTEGER NOT NULL DEFAULT 0,attempts INTEGER NOT NULL DEFAULT 0,"
                + "last_error TEXT,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_state_id ON scrobbles(state, _id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS scrobbles");
        onCreate(db);
    }

    public long enqueue(PendingScrobble item, long nowSec) {
        try {
            long startedAt = QueuePolicy.clampTimestamp(item.startedAtSec, nowSec);
            boolean tooOld = QueuePolicy.isTooOld(startedAt, nowSec);
            ContentValues values = new ContentValues();
            values.put("dedup_key", dedupKey(item, startedAt));
            values.put("artist", item.artist);
            values.put("track", item.track);
            values.put("album", item.album);
            values.put("album_artist", item.albumArtist);
            values.put("duration_ms", Long.valueOf(item.durationMs));
            values.put("track_number", Integer.valueOf(item.trackNumber));
            values.put("started_at", Long.valueOf(startedAt));
            values.put("source_pkg", item.sourcePkg);
            values.put("state", Integer.valueOf(tooOld ? 2 : 0));
            values.put("attempts", Integer.valueOf(item.attempts));
            values.put("last_error", tooOld ? "too old" : item.lastError);
            values.put("created_at", Long.valueOf(nowSec));
            long id = getWritableDatabase().insertWithOnConflict(
                    "scrobbles", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            trimToCap();
            if (tooOld) trimDead();
            return id;
        } catch (SQLiteException e) {
            log("enqueue", e);
            return -1L;
        }
    }

    public List<PendingScrobble> takeBatch(int max) {
        return queryItems("state=0", null, "_id ASC", Math.min(Math.max(0, max), QueuePolicy.MAX_BATCH));
    }

    public void markSent(List<Long> ids) {
        deleteIds(ids);
    }

    public void markRetry(List<Long> ids, String error) {
        updateIds(ids, "attempts=attempts+1, last_error=?", new Object[] {error});
    }

    public void markDead(List<Long> ids, String error) {
        updateIds(ids, "state=2, last_error=?", new Object[] {error});
        trimDead();
    }

    public int countPending() { return count("state=0"); }
    public int countDead() { return count("state=2"); }

    public List<PendingScrobble> recent(int limit) {
        return queryItems(null, null, "_id DESC", Math.max(0, limit));
    }

    public int purgeDead() {
        try { return getWritableDatabase().delete("scrobbles", "state=2", null); }
        catch (SQLiteException e) { log("purgeDead", e); return 0; }
    }

    public int retryDead() {
        try {
            ContentValues values = new ContentValues();
            values.put("state", Integer.valueOf(0));
            values.put("attempts", Integer.valueOf(0));
            values.putNull("last_error");
            return getWritableDatabase().update("scrobbles", values, "state=2", null);
        } catch (SQLiteException e) { log("retryDead", e); return 0; }
    }

    public int trimToCap() {
        int excess = QueuePolicy.evictCount(count(null));
        return deleteOldest("state=0", excess);
    }

    public int trimDead() {
        int excess = Math.max(0, countDead() - QueuePolicy.MAX_DEAD_ROWS);
        return deleteOldest("state=2", excess);
    }

    private List<PendingScrobble> queryItems(String selection, String[] args,
            String order, int limit) {
        if (limit <= 0) return Collections.emptyList();
        Cursor cursor = null;
        try {
            cursor = getReadableDatabase().query("scrobbles", null, selection, args,
                    null, null, order, String.valueOf(limit));
            List<PendingScrobble> result = new ArrayList<PendingScrobble>();
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
            return result;
        } catch (SQLiteException e) {
            log("queryItems", e);
            return Collections.emptyList();
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private int count(String where) {
        Cursor cursor = null;
        try {
            cursor = getReadableDatabase().rawQuery(
                    "SELECT COUNT(*) FROM scrobbles" + (where == null ? "" : " WHERE " + where), null);
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } catch (SQLiteException e) {
            log("count", e);
            return 0;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private int deleteOldest(String where, int count) {
        if (count <= 0) return 0;
        try {
            return getWritableDatabase().delete("scrobbles", "_id IN (SELECT _id FROM scrobbles WHERE "
                    + where + " ORDER BY _id ASC LIMIT " + count + ")", null);
        } catch (SQLiteException e) { log("trim", e); return 0; }
    }

    private void deleteIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        try { getWritableDatabase().delete("scrobbles", idClause(ids), null); }
        catch (SQLiteException e) { log("markSent", e); }
    }

    private void updateIds(List<Long> ids, String assignment, Object[] bindArgs) {
        if (ids == null || ids.isEmpty()) return;
        try {
            String sql = "UPDATE scrobbles SET " + assignment + " WHERE " + idClause(ids);
            getWritableDatabase().execSQL(sql, bindArgs);
        } catch (SQLiteException e) { log("update", e); }
    }

    private static String idClause(List<Long> ids) {
        StringBuilder out = new StringBuilder("_id IN (");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) out.append(',');
            out.append(ids.get(i).longValue());
        }
        return out.append(')').toString();
    }

    private static PendingScrobble fromCursor(Cursor c) {
        int state = c.getInt(c.getColumnIndexOrThrow("state"));
        return new PendingScrobble(c.getLong(c.getColumnIndexOrThrow("_id")),
                c.getString(c.getColumnIndexOrThrow("source_pkg")),
                c.getString(c.getColumnIndexOrThrow("artist")),
                c.getString(c.getColumnIndexOrThrow("track")),
                c.getString(c.getColumnIndexOrThrow("album")),
                c.getString(c.getColumnIndexOrThrow("album_artist")),
                c.getLong(c.getColumnIndexOrThrow("duration_ms")),
                c.getInt(c.getColumnIndexOrThrow("track_number")),
                c.getLong(c.getColumnIndexOrThrow("started_at")),
                c.getInt(c.getColumnIndexOrThrow("attempts")),
                state == 2 ? "dead" : state == 1 ? "sent" : "pending",
                c.getString(c.getColumnIndexOrThrow("last_error")));
    }

    private static String dedupKey(PendingScrobble item, long startedAt) {
        return item.sourcePkg + "|" + nz(item.artist) + "|" + nz(item.track) + "|"
                + item.durationMs + "|" + startedAt;
    }

    private static String nz(String value) { return value == null ? "" : value; }
    private static void log(String operation, SQLiteException e) {
        Log.e(Const.TAG, "ScrobbleDb " + operation + " failed", e);
    }
}
