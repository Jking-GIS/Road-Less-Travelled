package com.example.jeff9123.displaymap;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

public class LocationListAdapter extends RecyclerView.Adapter<LocationListAdapter.LocationViewHolder>{
    class LocationViewHolder extends RecyclerView.ViewHolder {
        private final TextView LocationItemView;

        private LocationViewHolder(View itemView) {
            super(itemView);
            LocationItemView = itemView.findViewById(R.id.textView);
        }
    }

    private final LayoutInflater mInflater;
    private List<Location> mLocations; // Cached copy of Locations

    LocationListAdapter(Context context) { mInflater = LayoutInflater.from(context); }

    @NonNull
    @Override
    public LocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = mInflater.inflate(R.layout.recyclerview_item, parent, false);
        return new LocationViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull LocationViewHolder holder, int position) {
        if (mLocations != null) {
            Location current = mLocations.get(position);
            String st = current.getLocation() + ": (" + current.getLatitude() + ", " + current.getLongitude() + ")";
            holder.LocationItemView.setText(st);
        } else {
            // Covers the case of data not being ready yet.
            holder.LocationItemView.setText(R.string.no_location);
        }
    }

    void setLocations(List<Location> Locations){
        mLocations = Locations;
        notifyDataSetChanged();
    }

    // getItemCount() is called many times, and when it is first called,
    // mLocations has not been updated (means initially, it's null, and we can't return null).
    @Override
    public int getItemCount() {
        if (mLocations != null)
            return mLocations.size();
        else return 0;
    }
}
