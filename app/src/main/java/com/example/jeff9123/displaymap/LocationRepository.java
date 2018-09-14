package com.example.jeff9123.roadlesstravelled;

import android.app.Application;
import android.arch.lifecycle.LiveData;
import android.os.AsyncTask;

import java.util.List;

public class LocationRepository {
    private LocationDao mLocationDao;
    private LiveData<List<Location>> mAllLocations;
    private LiveData<List<Location>> mBarLocations;
    private LiveData<List<Location>> mPastLocations;

    LocationRepository(Application application) {
        LocationDatabase db = LocationDatabase.getDatabase(application);
        mLocationDao = db.locationDao();
        mAllLocations = mLocationDao.getAllLocations();
        mBarLocations = mLocationDao.findByLocation("bar%");
        mPastLocations = mLocationDao.findByLocation("past%");
    }

    LiveData<List<Location>> getAllLocations() { return mAllLocations; }
    LiveData<List<Location>> getBarLocations() { return mBarLocations; }
    LiveData<List<Location>> getPastLocations() { return mPastLocations; }

    boolean locationNotExists(String loc) {
        return (mLocationDao.findByLocation_notLive(loc).size() <= 0);
    }

    public void insert(Location location) {
        new insertASyncTask(mLocationDao).execute(location);
    }
    private static class insertASyncTask extends AsyncTask<Location, Void, Void> {
        private LocationDao mAsyncTaskDao;

        insertASyncTask(LocationDao dao) {
            mAsyncTaskDao = dao;
        }

        @Override
        protected Void doInBackground(final Location... params) {
            mAsyncTaskDao.insert(params[0]);
            return null;
        }
    }
}


