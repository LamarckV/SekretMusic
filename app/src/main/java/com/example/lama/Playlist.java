package com.example.lama;

import java.util.ArrayList;
import java.util.List;

public class Playlist {
    private String name;
    private List<Song> songs;

    public Playlist(String name) {
        this.name = name;
        this.songs = new ArrayList<>();
    }

    public String getName() { return name; }
    public List<Song> getSongs() { return songs; }
    public void addSong(Song song) {
        if (!songs.contains(song)) {
            songs.add(song);
        }
    }
    public int getSongCount() { return songs.size(); }
}
