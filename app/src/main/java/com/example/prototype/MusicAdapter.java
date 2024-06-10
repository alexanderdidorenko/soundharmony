package com.example.prototype;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class MusicAdapter extends RecyclerView.Adapter<MusicAdapter.MusicViewHolder> {

    private List<MusicTrack> musicList;

    public MusicAdapter(ArrayList<MusicTrack> musicList) {
        this.musicList = musicList;
    }

    @NonNull
    @Override
    public MusicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recyclerview, parent, false);
        return new MusicViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MusicViewHolder holder, int position) {
        MusicTrack musicTrack = musicList.get(position);
        holder.bind(musicTrack);
    }

    @Override
    public int getItemCount() {
        return musicList.size();
    }
    public void updateTracks(List<MusicTrack> newTracks) {
        this.musicList = newTracks;
        notifyDataSetChanged();
    }

    public class MusicViewHolder extends RecyclerView.ViewHolder {

        private TextView artistTextView;
        private TextView trackNameTextView;
        private TextView durationTextView;

        public MusicViewHolder(@NonNull View itemView) {
            super(itemView);
            artistTextView = itemView.findViewById(R.id.artistTextView);
            trackNameTextView = itemView.findViewById(R.id.trackNameTextView);
            durationTextView = itemView.findViewById(R.id.durationTextView);
        }

        public void bind(MusicTrack musicTrack) {
            artistTextView.setText(musicTrack.getArtist());
            trackNameTextView.setText(musicTrack.getTrackName());
            durationTextView.setText(musicTrack.getDuration());
        }
    }

}
