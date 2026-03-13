package com.example.lama;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MusicService extends Service {

    private final IBinder binder = new MusicBinder();
    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private List<Song> queue = new ArrayList<>();
    private int currentIndex = -1;
    private boolean isShuffle = false;
    private int repeatMode = 0; // 0: None, 1: Song, 2: All

    private static final String CHANNEL_ID = "music_service_channel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_SHUFFLE = "com.example.lama.ACTION_SHUFFLE";
    public static final String ACTION_REPEAT = "com.example.lama.ACTION_REPEAT";

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());

        mediaPlayer.setOnCompletionListener(mp -> {
            if (repeatMode == 1) {
                play(currentIndex);
            } else {
                playNext();
            }
        });

        initMediaSession();
        createNotificationChannel();
    }

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "LamaMusicSession");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() { resume(); }
            @Override
            public void onPause() { pause(); }
            @Override
            public void onSkipToNext() { playNext(); }
            @Override
            public void onSkipToPrevious() { playPrevious(); }
        });

        mediaSession.setActive(true);
        updatePlaybackState();
    }

    public void setQueue(List<Song> songs, int startIndex) {
        this.queue = new ArrayList<>(songs);
        this.currentIndex = startIndex;
    }

    public void play(int index) {
        if (index < 0 || index >= queue.size()) return;
        currentIndex = index;
        Song song = queue.get(currentIndex);

        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(song.getPath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            updateNotification();
            updatePlaybackState();
            updateMetadata(song);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            updateNotification();
            updatePlaybackState();
        }
    }

    public void resume() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying() && currentIndex != -1) {
            mediaPlayer.start();
            updateNotification();
            updatePlaybackState();
        }
    }

    public void playNext() {
        if (queue.isEmpty()) return;
        if (isShuffle) {
            currentIndex = (int) (Math.random() * queue.size());
        } else {
            currentIndex = (currentIndex + 1) % queue.size();
        }
        play(currentIndex);
    }

    public void playPrevious() {
        if (queue.isEmpty()) return;
        currentIndex = (currentIndex - 1 + queue.size()) % queue.size();
        play(currentIndex);
    }

    public void toggleShuffle() {
        this.isShuffle = !isShuffle;
        updateNotification();
    }

    public void setShuffle(boolean shuffle) {
        this.isShuffle = shuffle;
        updateNotification();
    }

    public void cycleRepeatMode() {
        this.repeatMode = (repeatMode + 1) % 3;
        updateNotification();
    }

    private void updatePlaybackState() {
        int state = mediaPlayer.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .setState(state, mediaPlayer.getCurrentPosition(), 1.0f)
                .build());
    }

    private void updateMetadata(Song song) {
        Bitmap art = null;
        try {
            InputStream is = getContentResolver().openInputStream(song.getAlbumArtUri());
            art = BitmapFactory.decodeStream(is);
            if (is != null) is.close();
        } catch (Exception e) {
            art = null;
        }

        MediaMetadataCompat.Builder metaBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.getArtist());
        
        if (art != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art);
        }

        mediaSession.setMetadata(metaBuilder.build());
    }

    private void updateNotification() {
        if (currentIndex == -1 || queue.isEmpty()) return;
        Song song = queue.get(currentIndex);

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Bitmap art = null;
        try {
            InputStream is = getContentResolver().openInputStream(song.getAlbumArtUri());
            art = BitmapFactory.decodeStream(is);
            if (is != null) is.close();
        } catch (Exception e) {
            art = null;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setLargeIcon(art)
                .setContentTitle(song.getTitle())
                .setContentText(song.getArtist())
                .setContentIntent(contentIntent)
                .setOngoing(mediaPlayer.isPlaying())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(1, 2, 3));

        // Action Previous
        builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_previous, "Previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)));

        // Action Play/Pause
        if (mediaPlayer.isPlaying()) {
            builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE)));
        } else {
            builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_play, "Play",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY)));
        }

        // Action Next
        builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_next, "Next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)));

        // Action Shuffle
        Intent shuffleIntent = new Intent(this, MusicService.class).setAction(ACTION_SHUFFLE);
        PendingIntent pShuffle = PendingIntent.getService(this, 0, shuffleIntent, PendingIntent.FLAG_IMMUTABLE);
        int shuffleIcon = isShuffle ? android.R.drawable.ic_menu_directions : android.R.drawable.ic_menu_help; // Replace with appropriate icons
        builder.addAction(new NotificationCompat.Action(shuffleIcon, "Shuffle", pShuffle));

        startForeground(NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Lama Music", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Controles de música");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_SHUFFLE.equals(action)) {
                toggleShuffle();
            } else if (ACTION_REPEAT.equals(action)) {
                cycleRepeatMode();
            } else {
                MediaButtonReceiver.handleIntent(mediaSession, intent);
            }
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        if (mediaSession != null) {
            mediaSession.release();
        }
        super.onDestroy();
    }

    public List<Song> getQueue() { return queue; }
    public int getCurrentIndex() { return currentIndex; }
    public boolean isShuffle() { return isShuffle; }
    public int getRepeatMode() { return repeatMode; }
    public MediaPlayer getMediaPlayer() { return mediaPlayer; }

    public Song getCurrentSong() {
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            return queue.get(currentIndex);
        }
        return null;
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public int getDuration() {
        if (mediaPlayer != null && currentIndex != -1) {
            try {
                return mediaPlayer.getDuration();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public int getCurrentPosition() {
        if (mediaPlayer != null && currentIndex != -1) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
}
