package com.example.prototype;

import android.net.Uri;

public class MusicTrack {

    private String artist;
    private String trackName;
    private String duration;
    private Uri audioUri;

    public MusicTrack(String artist, String trackName, String duration, Uri audioUri) {
        this.artist = artist;
        this.trackName = trackName;
        this.duration = duration;
        this.audioUri = audioUri;
    }
    public MusicTrack(String artist, String trackName, String duration) {
        this.artist = artist;
        this.trackName = trackName;
        this.duration = duration;
    }
    public String getArtist() {
        return artist;
    }

    public String getTrackName() {
        return trackName;
    }

    public String getDuration() {
        return duration;
    }
    public Uri getAudioUri() {
        return audioUri;
    }
}
