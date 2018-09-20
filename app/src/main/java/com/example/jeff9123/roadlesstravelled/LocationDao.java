package com.example.jeff9123.roadlesstravelled;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public interface LocationDao {
    @Query("SELECT * FROM location_table")
    LiveData<List<Location>> getAllLocations();

    @Query("SELECT * FROM location_table WHERE location LIKE :loc")
    LiveData<List<Location>> findByLocation(String loc);

    @Query("SELECT * FROM location_table WHERE location LIKE :loc")
    List<Location> findByLocation_notLive(String loc);

    @Query("SELECT * FROM location_table WHERE location = :loc LIMIT 1")
    Location findLocation(String loc);

    @Query("SELECT * FROM location_table WHERE latitude LIKE :lat AND longitude LIKE :lon LIMIT 1")
    Location findByLatLon(double lat, double lon);

    @Insert
    void insert(Location location);

    @Insert
    void insertAll(Location... locations);

    @Delete
    void delete(Location location);

    @Query("DELETE FROM location_table")
    void deleteAll();

    @Query("DELETE FROM location_table WHERE location LIKE :loc")
    void deleteAllLike(String loc);
}