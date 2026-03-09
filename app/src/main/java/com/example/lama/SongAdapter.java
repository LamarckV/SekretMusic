package com.example.lama;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.ArrayList;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    private List<Song> songList;
    private List<Song> songListFull;
    private OnSongClickListener listener;
    private boolean isSelectionMode = false;
    private List<Song> selectedSongs = new ArrayList<>();

    public interface OnSongClickListener {
        void onSongClick(Song song, int position);
        default void onSongLongClick(Song song, int position) {}
    }

    public SongAdapter(List<Song> songList, OnSongClickListener listener) {
        this.songList = songList;
        this.songListFull = new ArrayList<>(songList);
        this.listener = listener;
    }

    public void setSelectionMode(boolean selectionMode) {
        isSelectionMode = selectionMode;
        selectedSongs.clear();
        notifyDataSetChanged();
    }

    public List<Song> getSelectedSongs() {
        return selectedSongs;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.song_item, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = songList.get(position);
        holder.txtTitle.setText(song.getTitle());
        holder.txtArtist.setText(song.getArtist());

        Glide.with(holder.itemView.getContext())
                .load(song.getAlbumArtUri())
                .placeholder(android.R.drawable.ic_menu_report_image)
                .error(android.R.drawable.ic_menu_report_image)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.imgAlbum);

        if (isSelectionMode) {
            holder.itemView.setBackgroundColor(selectedSongs.contains(song) ? 0x401DB954 : 0x00000000);
        } else {
            holder.itemView.setBackgroundColor(0x00000000);
        }

        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                if (selectedSongs.contains(song)) selectedSongs.remove(song);
                else selectedSongs.add(song);
                notifyItemChanged(position);
            } else {
                listener.onSongClick(song, position);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                listener.onSongLongClick(song, position);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return songList.size();
    }

    public void updateList(List<Song> newList) {
        this.songList = newList;
        notifyDataSetChanged();
    }

    public void filter(String text) {
        List<Song> filteredList = new ArrayList<>();
        for (Song item : songListFull) {
            if (item.getTitle().toLowerCase().contains(text.toLowerCase()) ||
                item.getArtist().toLowerCase().contains(text.toLowerCase())) {
                filteredList.add(item);
            }
        }
        updateList(filteredList);
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        TextView txtTitle, txtArtist;
        ImageView imgAlbum;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtSongTitle);
            txtArtist = itemView.findViewById(R.id.txtSongArtist);
            imgAlbum = itemView.findViewById(R.id.imgSongAlbum);
        }
    }
}
