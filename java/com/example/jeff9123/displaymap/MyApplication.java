package com.example.jeff9123.displaymap;

import android.app.Application;

import com.esri.arcgisruntime.mapping.view.LocationDisplay;
import com.esri.arcgisruntime.mapping.view.MapView;

public class MyApplication extends Application {
    private LocationDisplay mLocationDisplay;
    private MapView mMapView;

    public LocationDisplay getLocationDisplay() {
        return mLocationDisplay;
    }
    public MapView getMapView() { return mMapView; }

    public void setLocationDisplay(LocationDisplay locationDisplay) { mLocationDisplay = locationDisplay; }
    public void setMapView(MapView mapView) {
        mMapView = mapView;
    }
}
