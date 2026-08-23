package com.akscrobble;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Build;
import android.provider.MediaStore;

import com.akscrobble.core.AlbumInfo;

import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves AK Music media ids against the device MediaStore. */
public final class AlbumResolver {
    private static final int CACHE_SIZE = 200;
    private static final String[] FULL_PROJECTION = {"album", "album_artist", "track", "year"};
    private static final String[] FALLBACK_PROJECTION = {"album", "track", "year"};

    private final Context context;
    private final Map<Long, AlbumInfo> cache = new LinkedHashMap<Long, AlbumInfo>(
            CACHE_SIZE + 1, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, AlbumInfo> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public AlbumResolver(Context context) {
        this.context = context.getApplicationContext();
    }

    public AlbumInfo resolve(long mediaId) {
        if (mediaId <= 0L || !hasStoragePermission()) return null;
        Long key = Long.valueOf(mediaId);
        synchronized (cache) {
            if (cache.containsKey(key)) return cache.get(key);
        }

        AlbumInfo result;
        try {
            result = query(mediaId, FULL_PROJECTION, true);
        } catch (IllegalArgumentException e) {
            result = queryFallback(mediaId);
        } catch (SQLiteException e) {
            result = queryFallback(mediaId);
        }
        if (result != null) {
            synchronized (cache) {
                cache.put(key, result);
            }
        }
        return result;
    }

    public boolean hasStoragePermission() {
        return Build.VERSION.SDK_INT < 23 || context.checkSelfPermission(
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private AlbumInfo queryFallback(long mediaId) {
        try {
            return query(mediaId, FALLBACK_PROJECTION, false);
        } catch (IllegalArgumentException e) {
            return null;
        } catch (SQLiteException e) {
            return null;
        }
    }

    private AlbumInfo query(long mediaId, String[] projection, boolean hasAlbumArtist) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                    MediaStore.Audio.Media._ID + "=?",
                    new String[] {Long.toString(mediaId)}, null);
            if (cursor == null || !cursor.moveToFirst()) return null;
            String album = AlbumInfo.normalize(
                    cursor.getString(cursor.getColumnIndexOrThrow("album")));
            String albumArtist = hasAlbumArtist ? AlbumInfo.normalize(
                    cursor.getString(cursor.getColumnIndexOrThrow("album_artist"))) : null;
            int rawTrack = cursor.getInt(cursor.getColumnIndexOrThrow("track"));
            int year = cursor.getInt(cursor.getColumnIndexOrThrow("year"));
            return new AlbumInfo(album, albumArtist,
                    AlbumInfo.trackFromMediaStore(rawTrack),
                    AlbumInfo.discFromMediaStore(rawTrack), year);
        } finally {
            if (cursor != null) cursor.close();
        }
    }
}
