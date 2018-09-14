package com.example.jeff9123.displaymap;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

@Entity(tableName = "location_table")
public class Location {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "location")
    private String mLocation;

    @ColumnInfo(name = "latitude")
    private double mLatitude;

    @ColumnInfo(name = "longitude")
    private double mLongitude;

    public Location(@NonNull String location, double latitude, double longitude) {
        this.mLocation = location;
        this.mLatitude = latitude;
        this.mLongitude = longitude;
    }

    public String getLocation() {return this.mLocation;}
    public double getLatitude() {return this.mLatitude;}
    public double getLongitude() {return this.mLongitude;}
}
