package com.example.lama;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder> {

    private List<Playlist> playlists;
    private OnPlaylistClickListener listener;

    public interface OnPlaylistClickListener {
        void onPlaylistClick(Playlist playlist);
        void onCreatePlaylistClick();
    }

    public PlaylistAdapter(List<Playlist> playlists, OnPlaylistClickListener listener) {
        this.playlists = playlists;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? 0 : 1; // 0 for "Create Playlist" button
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 0) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_create_playlist, parent, false);
            return new PlaylistViewHolder(view, true);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.playlist_item, parent, false);
        return new PlaylistViewHolder(view, false);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        if (getItemViewType(position) == 0) {
            holder.itemView.setOnClickListener(v -> listener.onCreatePlaylistClick());
        } else {
            Playlist playlist = playlists.get(position - 1);
            holder.txtName.setText(playlist.getName());
            holder.txtCount.setText(playlist.getSongCount() + " músicas");
            
            Object coverSource = android.R.drawable.ic_menu_agenda; // Default placeholder
            
            if (playlist.getCoverUri() != null) {
                coverSource = playlist.getCoverUri();
            } else if (playlist.getSongCount() > 0) {
                coverSource = playlist.getSongs().get(0).getAlbumArtUri();
            }
            
            Glide.with(holder.itemView.getContext())
                .load(coverSource)
                .placeholder(android.R.drawable.ic_menu_agenda)
                .into(holder.imgCover);
            
            holder.itemView.setOnClickListener(v -> listener.onPlaylistClick(playlist));
        }
    }

    @Override
    public int getItemCount() {
        return playlists.size() + 1;
    }

    static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtCount;
        ImageView imgCover;

        public PlaylistViewHolder(@NonNull View itemView, boolean isCreateButton) {
            super(itemView);
            if (!isCreateButton) {
                txtName = itemView.findViewById(R.id.txtPlaylistName);
                txtCount = itemView.findViewById(R.id.txtPlaylistCount);
                imgCover = itemView.findViewById(R.id.imgPlaylistCover);
            }
        }
    }
}
