package com.example.lama;

import android.Manifest;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private final List<Song> songList = new ArrayList<>();
    private final List<Playlist> playlists = new ArrayList<>();
    private RecyclerView recyclerView;
    private SongAdapter songAdapter;
    private PlaylistAdapter playlistAdapter;
    
    private final MediaPlayer mediaPlayer = new MediaPlayer();
    private int currentSongIndex = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateSeekBar;

    private View miniPlayer;
    private TextView txtSongTitle, txtArtistName;
    private ImageView imgAlbumArt;
    private ImageButton btnPlayPause;
    
    private ImageButton navHome, navPlaylists, navLibrary;
    private LinearLayout sideIndex;

    private SharedPreferences prefs;
    private boolean isShuffle = false;
    private int repeatMode = 0; // 0: No repeat, 1: Repeat song, 2: Repeat all

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("LamaPrefs", MODE_PRIVATE);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        checkPermissions();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerViewMusic);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        sideIndex = findViewById(R.id.sideIndex);
        miniPlayer = findViewById(R.id.miniPlayer);
        txtSongTitle = findViewById(R.id.txtSongTitle);
        txtArtistName = findViewById(R.id.txtArtistName);
        imgAlbumArt = findViewById(R.id.imgAlbumArt);
        btnPlayPause = findViewById(R.id.btnPlayPause);

        navHome = findViewById(R.id.navHome);
        navPlaylists = findViewById(R.id.navPlaylists);
        navLibrary = findViewById(R.id.navLibrary);

        navHome.setOnClickListener(v -> showHome());
        navPlaylists.setOnClickListener(v -> showPlaylists());
        navLibrary.setOnClickListener(v -> showLibrary());

        findViewById(R.id.btnFilter).setOnClickListener(v -> showFilterModal());
        
        EditText searchEditText = findViewById(R.id.searchEditText);
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (songAdapter != null) songAdapter.filter(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        miniPlayer.setOnClickListener(v -> { if (currentSongIndex != -1) showPlayerModal(); });
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
    }

    private void showHome() {
        updateNavIcons(navHome);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(songAdapter);
        sideIndex.setVisibility(View.GONE);
    }

    private void showPlaylists() {
        updateNavIcons(navPlaylists);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        if (playlistAdapter == null) {
            playlistAdapter = new PlaylistAdapter(playlists, new PlaylistAdapter.OnPlaylistClickListener() {
                @Override
                public void onPlaylistClick(Playlist playlist) { /* Show playlist songs */ }
                @Override
                public void onCreatePlaylistClick() { showCreatePlaylistDialog(); }
            });
        }
        recyclerView.setAdapter(playlistAdapter);
        sideIndex.setVisibility(View.GONE);
    }

    private void showLibrary() {
        updateNavIcons(navLibrary);
        showHome(); // For now, library is just the song list
    }

    private void updateNavIcons(ImageButton active) {
        navHome.setColorFilter(0xFFB3B3B3);
        navPlaylists.setColorFilter(0xFFB3B3B3);
        navLibrary.setColorFilter(0xFFB3B3B3);
        active.setColorFilter(0xFFFFFFFF);
    }

    private void checkPermissions() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? 
            Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, PERMISSION_REQUEST_CODE);
        } else {
            loadSongs();
        }
    }

    private void loadSongs() {
        fetchSongs();
        songAdapter = new SongAdapter(new ArrayList<>(songList), (song, position) -> {
            currentSongIndex = songList.indexOf(song);
            playSong(song);
            showPlayerModal();
        });
        recyclerView.setAdapter(songAdapter);
    }

    public void fetchSongs() {
        songList.clear();
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor musicCursor = musicResolver.query(musicUri, null, MediaStore.Audio.Media.IS_MUSIC + "!=0", null, MediaStore.Audio.Media.TITLE + " ASC");

        if (musicCursor != null && musicCursor.moveToFirst()) {
            int idCol = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int titleCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int artistCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int albumCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
            int albumIdCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            int dataCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            int durationCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
            int dateCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED);

            do {
                songList.add(new Song(musicCursor.getLong(idCol), musicCursor.getString(titleCol), musicCursor.getString(artistCol),
                        musicCursor.getString(albumCol), musicCursor.getLong(albumIdCol), musicCursor.getString(dataCol),
                        musicCursor.getInt(durationCol), musicCursor.getLong(dateCol)));
            } while (musicCursor.moveToNext());
            musicCursor.close();
        }
    }

    private void playSong(Song song) {
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(song.getPath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            
            txtSongTitle.setText(song.getTitle());
            txtArtistName.setText(song.getArtist());
            Glide.with(this).load(song.getAlbumArtUri()).placeholder(android.R.drawable.ic_menu_report_image).into(imgAlbumArt);
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            miniPlayer.setVisibility(View.VISIBLE);

            mediaPlayer.setOnCompletionListener(mp -> {
                if (repeatMode == 1) playSong(songList.get(currentSongIndex));
                else playNext();
            });
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void togglePlayPause() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        } else if (currentSongIndex != -1) {
            mediaPlayer.start();
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        }
    }

    private void playNext() {
        if (songList.isEmpty()) return;
        if (isShuffle) currentSongIndex = (int) (Math.random() * songList.size());
        else currentSongIndex = (currentSongIndex + 1) % songList.size();
        playSong(songList.get(currentSongIndex));
    }

    private void playPrevious() {
        if (songList.isEmpty()) return;
        currentSongIndex = (currentSongIndex - 1 + songList.size()) % songList.size();
        playSong(songList.get(currentSongIndex));
    }

    private void showFilterModal() {
        String[] options = {"Ordem Alfabética", "Mais Recentes", "Por Artista"};
        new AlertDialog.Builder(this)
            .setTitle("Filtrar por")
            .setItems(options, (dialog, which) -> {
                if (which == 0) Collections.sort(songList, (s1, s2) -> s1.getTitle().compareToIgnoreCase(s2.getTitle()));
                else if (which == 1) Collections.sort(songList, (s1, s2) -> Long.compare(s2.getDateAdded(), s1.getDateAdded()));
                else Collections.sort(songList, (s1, s2) -> s1.getArtist().compareToIgnoreCase(s2.getArtist()));
                songAdapter.updateList(new ArrayList<>(songList));
            }).show();
    }

    private void showCreatePlaylistDialog() {
        View view = getLayoutInflater().inflate(R.layout.playlist_dialog, null);
        EditText editName = view.findViewById(R.id.editPlaylistName);
        new AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Criar", (dialog, which) -> {
                String name = editName.getText().toString();
                if (!name.isEmpty()) {
                    playlists.add(new Playlist(name));
                    if (playlistAdapter != null) playlistAdapter.notifyDataSetChanged();
                }
            }).show();
    }

    private void showPlayerModal() {
        BottomSheetDialog modal = new BottomSheetDialog(this, R.style.PlayerBottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.modal_player, null);
        modal.setContentView(view);

        TextView mTitle = view.findViewById(R.id.modalSongTitle);
        TextView mArtist = view.findViewById(R.id.modalArtistName);
        ImageView mArt = view.findViewById(R.id.modalAlbumArt);
        ImageButton mPlayPause = view.findViewById(R.id.modalBtnPlayPause);
        SeekBar mSeekBar = view.findViewById(R.id.modalSeekBar);
        ImageButton mShuffle = view.findViewById(R.id.btnShuffle);
        ImageButton mRepeat = view.findViewById(R.id.btnRepeat);

        updatePlayerModalUI(mTitle, mArtist, mArt, mPlayPause, mSeekBar, mShuffle, mRepeat);

        mPlayPause.setOnClickListener(v -> { togglePlayPause(); mPlayPause.setImageResource(mediaPlayer.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play); });
        view.findViewById(R.id.modalBtnNext).setOnClickListener(v -> { playNext(); updatePlayerModalUI(mTitle, mArtist, mArt, mPlayPause, mSeekBar, mShuffle, mRepeat); });
        view.findViewById(R.id.modalBtnPrev).setOnClickListener(v -> { playPrevious(); updatePlayerModalUI(mTitle, mArtist, mArt, mPlayPause, mSeekBar, mShuffle, mRepeat); });
        
        mShuffle.setOnClickListener(v -> { isShuffle = !isShuffle; mShuffle.setColorFilter(isShuffle ? 0xFF1DB954 : 0xFFB3B3B3); });
        mRepeat.setOnClickListener(v -> { repeatMode = (repeatMode + 1) % 3; mRepeat.setColorFilter(repeatMode > 0 ? 0xFF1DB954 : 0xFFB3B3B3); });

        updateSeekBar = new Runnable() {
            @Override public void run() {
                if (mediaPlayer.isPlaying()) {
                    mSeekBar.setProgress(mediaPlayer.getCurrentPosition());
                    ((TextView)view.findViewById(R.id.txtCurrentTime)).setText(formatTime(mediaPlayer.getCurrentPosition()));
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(updateSeekBar);
        modal.show();
    }

    private void updatePlayerModalUI(TextView t, TextView a, ImageView i, ImageButton pp, SeekBar sb, ImageButton sh, ImageButton re) {
        Song s = songList.get(currentSongIndex);
        t.setText(s.getTitle()); a.setText(s.getArtist());
        Glide.with(this).load(s.getAlbumArtUri()).placeholder(android.R.drawable.ic_menu_report_image).into(i);
        pp.setImageResource(mediaPlayer.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        sb.setMax(mediaPlayer.getDuration());
        sh.setColorFilter(isShuffle ? 0xFF1DB954 : 0xFFB3B3B3);
        re.setColorFilter(repeatMode > 0 ? 0xFF1DB954 : 0xFFB3B3B3);
    }

    private String formatTime(int ms) {
        return String.format(Locale.getDefault(), "%d:%02d", (ms/60000), (ms%60000)/1000);
    }
}
