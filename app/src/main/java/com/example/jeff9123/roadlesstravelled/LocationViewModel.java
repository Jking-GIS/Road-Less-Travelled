package com.example.jeff9123.roadlesstravelled;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;

import java.util.List;

public class LocationViewModel extends AndroidViewModel {
    private LocationRepository mRepository;
    private LiveData<List<Location>> mAllLocations;

    public LocationViewModel(Application application) {
        super(application);
        mRepository = new LocationRepository(application);
        mAllLocations = mRepository.getAllLocations();
    }

    LiveData<List<Location>> getAllLocations() {return mAllLocations;}

    public void insert(Location location) {mRepository.insert(location);}
}
