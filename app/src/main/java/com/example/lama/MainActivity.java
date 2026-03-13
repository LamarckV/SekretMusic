package com.example.lama;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
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

    private MusicService musicService;
    private boolean isBound = false;

    private List<Song> currentQueue = new ArrayList<>();
    private List<Song> originalQueue = new ArrayList<>();
    private int currentSongIndex = -1;
    private String currentSourceTitle = "Home";
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

    private Playlist currentEditingPlaylist;
    private ImageView currentEditingCoverView;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;
            syncWithService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (currentEditingPlaylist != null && imageUri != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                            try {
                                getContentResolver().takePersistableUriPermission(imageUri, takeFlags);
                            } catch (SecurityException e) { e.printStackTrace(); }
                        }
                        currentEditingPlaylist.setCoverUri(imageUri.toString());
                        if (currentEditingCoverView != null) {
                            Glide.with(this).load(imageUri).placeholder(android.R.drawable.ic_menu_agenda).into(currentEditingCoverView);
                        }
                        if (playlistAdapter != null) playlistAdapter.notifyDataSetChanged();
                    }
                }
            }
    );

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

        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        startService(intent);
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

        findViewById(R.id.btnFilter).setOnClickListener(v -> showFilterModal(songList, songAdapter));

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

    private void syncWithService() {
        if (!isBound || musicService == null) return;
        
        // Sync playback state and UI
        updateUI();
        
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (isBound && musicService != null) {
                    updateUI();
                    handler.postDelayed(this, 1000);
                }
            }
        });
    }

    private void updateUI() {
        if (musicService == null) return;
        Song current = musicService.getCurrentSong();
        if (current != null) {
            txtSongTitle.setText(current.getTitle());
            txtArtistName.setText(current.getArtist());
            Glide.with(this).load(current.getAlbumArtUri()).placeholder(android.R.drawable.ic_menu_report_image).into(imgAlbumArt);
            btnPlayPause.setImageResource(musicService.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            miniPlayer.setVisibility(View.VISIBLE);
            if (songAdapter != null) songAdapter.setPlayingSongId(current.getId());
        }
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
                public void onPlaylistClick(Playlist playlist) { showPlaylistDetails(playlist); }
                @Override
                public void onCreatePlaylistClick() { showCreatePlaylistDialog(); }
            });
        }
        recyclerView.setAdapter(playlistAdapter);
        sideIndex.setVisibility(View.GONE);
    }

    private void showLibrary() {
        updateNavIcons(navLibrary);
        showHome();
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void loadSongs() {
        fetchSongs();
        songAdapter = new SongAdapter(new ArrayList<>(songList), new SongAdapter.OnSongClickListener() {
            @Override
            public void onSongClick(Song song, int position) {
                currentSourceTitle = "Home";
                originalQueue = new ArrayList<>(songList);
                currentQueue.clear();
                currentQueue.addAll(originalQueue);
                currentSongIndex = currentQueue.indexOf(song);
                
                if (musicService != null) {
                    musicService.setQueue(currentQueue, currentSongIndex);
                    if (isShuffle) musicService.toggleShuffle();
                    musicService.play(currentSongIndex);
                }
                showPlayerModal();
            }

            @Override
            public void onSongLongClick(Song song, int position) {
                showAddToPlaylistDialog(song);
            }
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

    private void togglePlayPause() {
        if (musicService == null) return;
        if (musicService.isPlaying()) {
            musicService.pause();
        } else {
            musicService.resume();
        }
        updateUI();
    }

    private void showFilterModal(List<Song> list, SongAdapter adapter) {
        String[] options = {"Ordem Alfabética", "Mais Recentes", "Por Artista"};
        new AlertDialog.Builder(this)
                .setTitle("Filtrar por")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) Collections.sort(list, (s1, s2) -> s1.getTitle().compareToIgnoreCase(s2.getTitle()));
                    else if (which == 1) Collections.sort(list, (s1, s2) -> Long.compare(s2.getDateAdded(), s1.getDateAdded()));
                    else Collections.sort(list, (s1, s2) -> s1.getArtist().compareToIgnoreCase(s2.getArtist()));
                    adapter.updateList(new ArrayList<>(list));
                }).show();
    }

    private void showCreatePlaylistDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.PlayerBottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.playlist_dialog, null);
        dialog.setContentView(view);

        EditText editName = view.findViewById(R.id.editPlaylistName);
        Button btnCreate = view.findViewById(R.id.btnCreatePlaylist);

        btnCreate.setOnClickListener(v -> {
            String name = editName.getText().toString();
            if (!name.isEmpty()) {
                Playlist newPlaylist = new Playlist(name);
                showSongSelectionDialog(newPlaylist, dialog);
            } else {
                Toast.makeText(this, "Insira um nome", Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show();
    }

    private void showSongSelectionDialog(Playlist playlist, BottomSheetDialog creationDialog) {
        BottomSheetDialog selectionDialog = new BottomSheetDialog(this, R.style.PlayerBottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.modal_song_selection, null);
        selectionDialog.setContentView(view);

        RecyclerView rv = view.findViewById(R.id.rvSongSelection);
        rv.setLayoutManager(new LinearLayoutManager(this));

        SongAdapter selectionAdapter = new SongAdapter(new ArrayList<>(songList), new SongAdapter.OnSongClickListener() {
            @Override public void onSongClick(Song song, int position) {}
        });
        selectionAdapter.setSelectionMode(true);
        rv.setAdapter(selectionAdapter);

        view.findViewById(R.id.btnConfirmSelection).setOnClickListener(v -> {
            List<Song> selected = selectionAdapter.getSelectedSongs();
            for (Song s : selected) playlist.addSong(s);
            playlists.add(playlist);
            if (playlistAdapter != null) playlistAdapter.notifyDataSetChanged();
            selectionDialog.dismiss();
            creationDialog.dismiss();
            Toast.makeText(this, "Playlist criada!", Toast.LENGTH_SHORT).show();
        });

        selectionDialog.show();
    }

    private void showAddToPlaylistDialog(Song song) {
        if (playlists.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Nenhuma playlist")
                    .setMessage("Deseja criar uma nova playlist?")
                    .setPositiveButton("Sim", (dialog, which) -> showCreatePlaylistDialog())
                    .setNegativeButton("Não", null)
                    .show();
            return;
        }

        String[] names = new String[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) names[i] = playlists.get(i).getName();

        new AlertDialog.Builder(this)
                .setTitle("Adicionar à playlist")
                .setItems(names, (dialog, which) -> {
                    playlists.get(which).addSong(song);
                    if (playlistAdapter != null) playlistAdapter.notifyDataSetChanged();
                    Toast.makeText(this, "Adicionado a " + names[which], Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void showPlaylistDetails(Playlist playlist) {
        BottomSheetDialog detailsDialog = new BottomSheetDialog(this, R.style.PlayerBottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.modal_playlist_details, null);
        detailsDialog.setContentView(view);

        TextView txtName = view.findViewById(R.id.txtPlaylistDetailName);
        ImageView imgCover = view.findViewById(R.id.imgPlaylistDetailCover);
        RecyclerView rv = view.findViewById(R.id.rvPlaylistSongs);

        txtName.setText(playlist.getName());
        Object coverSource = android.R.drawable.ic_menu_agenda;
        if (playlist.getCoverUri() != null) coverSource = Uri.parse(playlist.getCoverUri());
        else if (playlist.getSongCount() > 0) coverSource = playlist.getSongs().get(0).getAlbumArtUri();

        Glide.with(this).load(coverSource).placeholder(android.R.drawable.ic_menu_report_image).into(imgCover);

        rv.setLayoutManager(new LinearLayoutManager(this));
        SongAdapter detailsAdapter = new SongAdapter(new ArrayList<>(playlist.getSongs()), new SongAdapter.OnSongClickListener() {
            @Override
            public void onSongClick(Song song, int position) {
                currentSourceTitle = playlist.getName();
                currentQueue = new ArrayList<>(playlist.getSongs());
                if (musicService != null) {
                    musicService.setQueue(currentQueue, position);
                    musicService.play(position);
                }
                showPlayerModal();
            }
        });
        rv.setAdapter(detailsAdapter);

        view.findViewById(R.id.btnPlayPlaylist).setOnClickListener(v -> {
            if (!playlist.getSongs().isEmpty()) {
                currentSourceTitle = playlist.getName();
                if (musicService != null) {
                    musicService.setQueue(new ArrayList<>(playlist.getSongs()), 0);
                    musicService.play(0);
                }
                showPlayerModal();
            }
        });

        view.findViewById(R.id.btnShufflePlaylist).setOnClickListener(v -> {
            if (!playlist.getSongs().isEmpty()) {
                currentSourceTitle = playlist.getName();
                isShuffle = true;
                if (musicService != null) {
                    musicService.setQueue(new ArrayList<>(playlist.getSongs()), 0);
                    musicService.setShuffle(true);
                    musicService.play(0);
                }
                showPlayerModal();
            }
        });

        view.findViewById(R.id.btnFilterPlaylist).setOnClickListener(v -> showFilterModal(playlist.getSongs(), detailsAdapter));

        view.findViewById(R.id.btnEditPlaylistCover).setOnClickListener(v -> {
            currentEditingPlaylist = playlist;
            currentEditingCoverView = imgCover;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });

        detailsDialog.show();
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
        ImageButton btnQueue = view.findViewById(R.id.btnQueue);
        ImageButton btnShowQueue = view.findViewById(R.id.btnShowQueue);

        Runnable updateModalUI = new Runnable() {
            @Override
            public void run() {
                if (musicService == null) return;
                Song s = musicService.getCurrentSong();
                if (s != null) {
                    mTitle.setText(s.getTitle());
                    mArtist.setText(s.getArtist());
                    Glide.with(MainActivity.this).load(s.getAlbumArtUri()).placeholder(android.R.drawable.ic_menu_report_image).into(mArt);
                    mPlayPause.setImageResource(musicService.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                    mSeekBar.setMax(musicService.getDuration());
                    mSeekBar.setProgress(musicService.getCurrentPosition());
                    
                    mShuffle.setColorFilter(musicService.isShuffle() ? 0xFF1DB954 : 0xFFB3B3B3);
                    int repeatMode = musicService.getRepeatMode();
                    mRepeat.setColorFilter(repeatMode > 0 ? 0xFF1DB954 : 0xFFB3B3B3);
                }
            }
        };
        updateModalUI.run();

        mPlayPause.setOnClickListener(v -> { togglePlayPause(); updateModalUI.run(); });
        view.findViewById(R.id.modalBtnNext).setOnClickListener(v -> { if (musicService != null) musicService.playNext(); updateModalUI.run(); });
        view.findViewById(R.id.modalBtnPrev).setOnClickListener(v -> { if (musicService != null) musicService.playPrevious(); updateModalUI.run(); });
        
        mShuffle.setOnClickListener(v -> { if (musicService != null) musicService.toggleShuffle(); updateModalUI.run(); });
        mRepeat.setOnClickListener(v -> { if (musicService != null) musicService.cycleRepeatMode(); updateModalUI.run(); });
        
        btnQueue.setOnClickListener(v -> { if (musicService != null) showAddToPlaylistDialog(musicService.getCurrentSong()); });
        btnShowQueue.setOnClickListener(v -> showQueueModal());

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (modal.isShowing()) {
                    updateModalUI.run();
                    handler.postDelayed(this, 1000);
                }
            }
        });
        modal.show();
    }

    private void showQueueModal() {
        if (musicService == null) return;
        BottomSheetDialog queueModal = new BottomSheetDialog(this, R.style.PlayerBottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.modal_queue, null);
        queueModal.setContentView(view);

        TextView txtSubtitle = view.findViewById(R.id.txtQueueSubtitle);
        txtSubtitle.setText("Playing from " + currentSourceTitle);

        RecyclerView rv = view.findViewById(R.id.rvQueue);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rv.setLayoutManager(layoutManager);

        List<Song> q = musicService.getQueue();
        int idx = musicService.getCurrentIndex();
        
        List<Song> nextSongs = new ArrayList<>();
        if (idx != -1 && !q.isEmpty()) {
            nextSongs.addAll(q.subList(idx, q.size()));
        }

        SongAdapter queueAdapter = new SongAdapter(nextSongs, new SongAdapter.OnSongClickListener() {
            @Override
            public void onSongClick(Song song, int position) {
                musicService.play(idx + position);
                queueModal.dismiss();
            }
        });
        if (!nextSongs.isEmpty()) queueAdapter.setPlayingSongId(nextSongs.get(0).getId());
        queueAdapter.setShowDragHandle(true);
        rv.setAdapter(queueAdapter);

        layoutManager.scrollToPositionWithOffset(0, 0);

        view.findViewById(R.id.btnScrollToCurrent).setOnClickListener(v -> rv.smoothScrollToPosition(0));

        // Shuffle and Repeat inside Queue Modal logic would go here similarly to showPlayerModal
        
        queueModal.show();
    }

    @Override
    protected void onDestroy() {
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        super.onDestroy();
    }

    private String formatTime(int ms) {
        return String.format(Locale.getDefault(), "%d:%02d", (ms/60000), (ms%60000)/1000);
    }
}
