package com.example.lama;

import android.content.ContentUris;
import android.net.Uri;

public class Song {
    private long id;
    private String title;
    private String artist;
    private String album;
    private long albumId;
    private String path;
    private int duration;
    private long dateAdded;

    public Song(long id, String title, String artist, String album, long albumId, String path, int duration, long dateAdded) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumId = albumId;
        this.path = path;
        this.duration = duration;
        this.dateAdded = dateAdded;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public String getPath() { return path; }
    public int getDuration() { return duration; }
    public long getDateAdded() { return dateAdded; }

    public Uri getAlbumArtUri() {
        return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);
    }
}
