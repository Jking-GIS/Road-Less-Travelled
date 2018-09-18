package com.example.jeff9123.roadlesstravelled;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.MatrixCursor;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.CoordinateFormatter;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.GeodeticCurveType;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.GeometryType;
import com.esri.arcgisruntime.geometry.LinearUnit;
import com.esri.arcgisruntime.geometry.LinearUnitId;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.Polygon;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.IdentifyGraphicsOverlayResult;
import com.esri.arcgisruntime.mapping.view.LocationDisplay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.portal.Portal;
import com.esri.arcgisruntime.portal.PortalInfo;
import com.esri.arcgisruntime.security.AuthenticationChallenge;
import com.esri.arcgisruntime.security.AuthenticationChallengeHandler;
import com.esri.arcgisruntime.security.AuthenticationChallengeResponse;
import com.esri.arcgisruntime.security.AuthenticationManager;
import com.esri.arcgisruntime.security.DefaultAuthenticationChallengeHandler;
import com.esri.arcgisruntime.symbology.PictureMarkerSymbol;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol;
import com.esri.arcgisruntime.tasks.geocode.GeocodeParameters;
import com.esri.arcgisruntime.tasks.geocode.GeocodeResult;
import com.esri.arcgisruntime.tasks.geocode.LocatorTask;
import com.esri.arcgisruntime.tasks.geocode.SuggestResult;
import com.esri.arcgisruntime.tasks.networkanalysis.BarrierType;
import com.esri.arcgisruntime.tasks.networkanalysis.DirectionManeuver;
import com.esri.arcgisruntime.tasks.networkanalysis.PointBarrier;
import com.esri.arcgisruntime.tasks.networkanalysis.Route;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteParameters;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteResult;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteTask;
import com.esri.arcgisruntime.tasks.networkanalysis.Stop;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

public class MapActivity extends AppCompatActivity {
    private static final String TAG = MapActivity.class.getSimpleName();

    public MapView getMapView() { return mMapView; }
    private MapView mMapView;
    private static LocationDisplay mLocationDisplay;

    private static List<GraphicsOverlay> mRouteOverlays;
    private GraphicsOverlay mPastOverlay;
    private GraphicsOverlay mPinsOverlay;
    private GraphicsOverlay mDirectionOverlay;

    private static Route mRoute;
    private static RouteParameters mRouteParameters;
    private static RouteTask mRouteTask;
    private static Point endPoint;

    private List<DirectionManeuver> mDirectionManeuvers;
    private Polygon currentDirectionBuffer;
    private Polygon nextDirectionBuffer;
    private int directionsIndex = 0;
    private Timer directionTimer;
    private boolean directionTimerWaiting = false;
    private int lostNavigationCounter = 0;

    private static LocationRepository mLocationRepository;
    private static List<Location> mBarLocations;
    private static List<Location> mPastLocations;

    private SearchView mAddressSearchView;
    private GeocodeParameters mAddressGeocodeParameters;
    private LocatorTask mLocatorTask;
    private PictureMarkerSymbol mPinSourceSymbol;
    private Callout mCallout;
    private GeocodeResult mGeocodeResult;

    private Intent backgroundService;
    private boolean tracking = false;

    private final String COLUMN_NAME_ADDRESS = "address";
    private final String[] mColumnNames = { BaseColumns._ID, COLUMN_NAME_ADDRESS };

    private Portal mPortal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map_activity);
        setupButtons();
        backgroundService = new Intent(this, BackgroundService.class);
        directionTimer = new Timer("direction_timer");
        mDirectionManeuvers = new ArrayList<>();
        mLocatorTask = new LocatorTask("http://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer");

        if (!canAccessLocation()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }

        DefaultAuthenticationChallengeHandler handler = new DefaultAuthenticationChallengeHandler(this);
        AuthenticationManager.setAuthenticationChallengeHandler(handler);
        mPortal = new Portal(getString(R.string.portal_url), true);
        mPortal.addDoneLoadingListener(() -> {
            PortalInfo portalInfo = mPortal.getPortalInfo();
            ArcGISRuntimeEnvironment.setLicense(portalInfo.getLicenseInfo());
            AuthenticationManager.setAuthenticationChallengeHandler(new ChallengeHandler());
            setupPinGraphic();
            setupMap();
            setupTracking();
            setupRouting();
            setupLocationRepository();
            setupGraphics();
            setupAddressSearchView();
        });
        mPortal.loadAsync();

        Log.d(TAG, "Main activity created");
    }

    private void startTimer() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                directionTimerWaiting = false;
            }
        };
        directionTimerWaiting = true;
        directionTimer.schedule(task, 10*1000);
    }

    private void setupPinGraphic() {
        BitmapDrawable pinDrawable = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.pin);
        try {
            if(pinDrawable != null) {
                mPinSourceSymbol = PictureMarkerSymbol.createAsync(pinDrawable).get();
            }
        } catch (InterruptedException | ExecutionException e) {
            Log.e(TAG, "Picture Marker Symbol error: " + e.getMessage());
            showToast("Failed to load pin drawable.");
        }
        // set pin to half of native size
        mPinSourceSymbol.setWidth(19f);
        mPinSourceSymbol.setHeight(72f);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupMap() {
        mMapView = findViewById(R.id.mapView);
        ArcGISMap map = new ArcGISMap(Basemap.Type.TOPOGRAPHIC, 34.056295, -117.195800, 16);
        mMapView.setMap(map);
        ((MyApplication)this.getApplication()).setMapView(mMapView);

        mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, mMapView) {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
                identifyGraphic(motionEvent);
                return true;
            }
        });
    }

    private void setupTracking() {
        mLocationDisplay = mMapView.getLocationDisplay();
        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.RECENTER);
        mLocationDisplay.startAsync();
        mLocationDisplay.addLocationChangedListener(new LocationChangedListener(this));
    }

    private void setupRouting() {
        mRouteTask = new RouteTask(getApplicationContext(), "http://route.arcgis.com/arcgis/rest/services/World/Route/NAServer/Route_World");
        final ListenableFuture<RouteParameters> listenableFuture = mRouteTask.createDefaultParametersAsync();
        listenableFuture.addDoneListener(() -> {
            try {
                if (listenableFuture.isDone()) {
                    mRouteParameters = listenableFuture.get();
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        });
    }

    private void setupLocationRepository() {
        mLocationRepository = new LocationRepository(getApplication());
        mLocationRepository.getBarLocations().observe(this, locations -> mBarLocations = locations);
        mLocationRepository.getPastLocations().observe(this, locations -> {
            mPastLocations = locations;
            mPastOverlay.getGraphics().clear();
            if(locations != null) {
                int start = (locations.size() >= 5) ? (locations.size() - 5) : 0;
                for (int x = start; x < locations.size(); x++) {
                    Point p = CoordinateFormatter.fromLatitudeLongitude(
                            locations.get(x).getLatitude() + "," + locations.get(x).getLongitude(),
                            null);
                    mPastOverlay.getGraphics().add(new Graphic(p, new SimpleMarkerSymbol(
                            SimpleMarkerSymbol.Style.CIRCLE, Color.GREEN, 20)));
                }
            }
        });
    }

    private void setupGraphics() {
        mPastOverlay = new GraphicsOverlay();
        mMapView.getGraphicsOverlays().add(mPastOverlay);
        mDirectionOverlay = new GraphicsOverlay();
        mMapView.getGraphicsOverlays().add(mDirectionOverlay);
        mRouteOverlays = new ArrayList<>();
        mPinsOverlay = new GraphicsOverlay();
        mMapView.getGraphicsOverlays().add(mPinsOverlay);
    }

    private void setupAddressSearchView() {
        mAddressSearchView = findViewById(R.id.searchView);
        mAddressSearchView.setIconified(false);
        mAddressSearchView.setFocusable(false);
        mAddressSearchView.clearFocus();
        mAddressSearchView.setQueryHint(getString(R.string.address_search_hint));

        mAddressGeocodeParameters = new GeocodeParameters();
        mAddressGeocodeParameters.getResultAttributeNames().add("PlaceName");
        mAddressGeocodeParameters.getResultAttributeNames().add("StAddr");
        mAddressGeocodeParameters.setMaxResults(1);

        mAddressSearchView.setOnQueryTextListener(new SearchViewListener());
    }

    @Override
    protected void onPause(){
        super.onPause();
        if(mMapView != null){ mMapView.pause(); }

        Log.d(TAG, "Main activity paused");
    }

    @Override
    protected void onResume(){
        super.onResume();
        if(mMapView != null){ mMapView.resume(); }

        Log.d(TAG, "Main activity resumed");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mMapView != null){ mMapView.dispose(); }

        Log.d(TAG, "Main activity destroyed");
    }

    //--- SEARCH FUNCTIONS ---

    private void refreshEndPointGraphics(boolean zoomTo) {
        mPinsOverlay.getGraphics().clear();
        if(endPoint != null) {
            Graphic endLocGraphic = new Graphic(endPoint, mGeocodeResult.getAttributes(), mPinSourceSymbol);
            mPinsOverlay.getGraphics().add(endLocGraphic);
            if(zoomTo) {
                mMapView.setViewpointAsync(new Viewpoint(endPoint, 10000));
            }
        }
    }

    private void identifyGraphic(MotionEvent motionEvent) {
        android.graphics.Point screenPoint = new android.graphics.Point(Math.round(motionEvent.getX()),
                Math.round(motionEvent.getY()));
        final ListenableFuture<IdentifyGraphicsOverlayResult> identifyResultsFuture = mMapView
                .identifyGraphicsOverlayAsync(mPinsOverlay, screenPoint, 10, false);
        identifyResultsFuture.addDoneListener(() -> {
            try {
                IdentifyGraphicsOverlayResult identifyGraphicsOverlayResult = identifyResultsFuture.get();
                List<Graphic> graphics = identifyGraphicsOverlayResult.getGraphics();
                if (graphics.size() > 0) {
                    Graphic identifiedGraphic = graphics.get(0);
                    showCallout(identifiedGraphic);
                } else {
                    mCallout.dismiss();
                }
            } catch (Exception e) {
                Log.e(TAG, "Identify error: " + e.getMessage());
            }
        });
    }

    private void geoCodePlace(final String place) {
        if (place != null) {
            mLocatorTask.addDoneLoadingListener(() -> {
                if (mLocatorTask.getLoadStatus() == LoadStatus.LOADED) {
                    GeocodeParameters localParams = mAddressGeocodeParameters;
                    final ListenableFuture<List<GeocodeResult>> geocodeResultListenableFuture = mLocatorTask
                            .geocodeAsync(place, localParams);
                    geocodeResultListenableFuture.addDoneListener(() -> {
                        try {
                            List<GeocodeResult> geocodeResults = geocodeResultListenableFuture.get();
                            if (geocodeResults.size() > 0) {
                                displaySearchResult(geocodeResults.get(0));
                            } else {
                                showToast(getString(R.string.location_not_found));
                            }
                        } catch (InterruptedException | ExecutionException e) {
                            Log.e(TAG, "Geocode error: " + e.getMessage());
                            showToast(getString(R.string.geo_locate_error));
                        }
                    });
                } else {
                    Log.i(TAG, "Trying to reload locator task");
                    mLocatorTask.retryLoadAsync();
                }
            });
            mLocatorTask.loadAsync();
        }
    }

    private void displaySearchResult(GeocodeResult geocodeResult) {
        if (mMapView.getCallout() != null && mMapView.getCallout().isShowing()) {
            mMapView.getCallout().dismiss();
        }

        endPoint = geocodeResult.getDisplayLocation();
        mGeocodeResult = geocodeResult;
        refreshEndPointGraphics(true);
    }

    @SuppressLint("SetTextI18n")
    private void showCallout(final Graphic graphic) {
        TextView calloutContent = new TextView(getApplicationContext());
        calloutContent.setTextColor(Color.BLACK);
        calloutContent.setText(graphic.getAttributes().get("PlaceName").toString() + "\n"
                + graphic.getAttributes().get("StAddr").toString());
        mCallout = mMapView.getCallout();
        mCallout.setShowOptions(new Callout.ShowOptions(true, false, false));
        mCallout.setContent(calloutContent);
        Point calloutLocation = graphic.computeCalloutLocation(graphic.getGeometry().getExtent().getCenter(), mMapView);
        mCallout.setGeoElement(graphic, calloutLocation);
        mCallout.show();
    }

    //--- NAVIGATION FUNCTIONS ---

    public void approachingNextDirection() {
        DirectionManeuver nextManeuver = mDirectionManeuvers.get(directionsIndex+1);
        ((TextView) findViewById(R.id.approachingDirectionsText)).setText(nextManeuver.getDirectionText());
        findViewById(R.id.approachingDirectionsText).setVisibility(View.VISIBLE);
        Graphic directionGraphic;
        if (nextManeuver.getGeometry().getGeometryType() == GeometryType.POINT) {
            directionGraphic = new Graphic(nextManeuver.getGeometry(),
                    new SimpleMarkerSymbol(SimpleMarkerSymbol.Style.X, Color.YELLOW, 5));
        } else if (nextManeuver.getGeometry().getGeometryType() == GeometryType.POLYLINE) {
            directionGraphic = new Graphic(nextManeuver.getGeometry(),
                    new SimpleLineSymbol(SimpleLineSymbol.Style.DASH, Color.YELLOW, 5));
        } else {
            directionGraphic = new Graphic();
        }
        mDirectionOverlay.getGraphics().add(directionGraphic);
    }

    public void nextDirection() {
        if(++directionsIndex < mDirectionManeuvers.size()) {
            DirectionManeuver currentManeuver = mDirectionManeuvers.get(directionsIndex);
            if(directionsIndex < mDirectionManeuvers.size()) {
                DirectionManeuver nextManeuver = mDirectionManeuvers.get(directionsIndex+1);
                nextDirectionBuffer = GeometryEngine.bufferGeodetic(
                        nextManeuver.getGeometry(),
                        1000,
                        new LinearUnit(LinearUnitId.FEET),
                        5.0,
                        GeodeticCurveType.GEODESIC);
            }else {// you're at your destination
                directionsIndex = 0;
                mDirectionOverlay.getGraphics().clear();
                mAddressSearchView.setQuery("", true);
                ((TextView) findViewById(R.id.approachingDirectionsText)).setText("");
                findViewById(R.id.approachingDirectionsText).setVisibility(View.GONE);
                ((TextView) findViewById(R.id.directionsText)).setText("");
                findViewById(R.id.directionsText).setVisibility(View.GONE);
            }
            ((TextView) findViewById(R.id.approachingDirectionsText)).setText("");
            findViewById(R.id.approachingDirectionsText).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.directionsText)).setText(currentManeuver.getDirectionText());
            findViewById(R.id.directionsText).setVisibility(View.VISIBLE);
            currentDirectionBuffer = GeometryEngine.bufferGeodetic(
                    currentManeuver.getGeometry(),
                    300,
                    new LinearUnit(LinearUnitId.FEET),
                    5.0,
                    GeodeticCurveType.GEODESIC);
            mDirectionOverlay.getGraphics().clear();
            Graphic directionGraphic;
            if (currentManeuver.getGeometry().getGeometryType() == GeometryType.POINT) {
                directionGraphic = new Graphic(currentManeuver.getGeometry(),
                        new SimpleMarkerSymbol(SimpleMarkerSymbol.Style.X, Color.GREEN, 5));
            } else if (currentManeuver.getGeometry().getGeometryType() == GeometryType.POLYLINE) {
                directionGraphic = new Graphic(currentManeuver.getGeometry(),
                        new SimpleLineSymbol(SimpleLineSymbol.Style.DASH, Color.GREEN, 5));
            } else {
                directionGraphic = new Graphic();
            }
            mDirectionOverlay.getGraphics().add(directionGraphic);
        }else {
            Log.d(TAG, "Error in getting next direction");
        }
    }

    //--- TRACKING FUNCTIONS ---

    private boolean canAccessLocation() {
        return(hasPermission(Manifest.permission.ACCESS_FINE_LOCATION));
    }

    private boolean hasPermission(String perm) {
        return(PackageManager.PERMISSION_GRANTED== ActivityCompat.checkSelfPermission(this, perm));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            //mLocationDisplay.startAsync();
        } else {
            showToast("Location denied.");
            if (!canAccessLocation()) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            }
        }
    }

    public void locateMe(View view) {
        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);
    }

    public void clearTracked(View view) {
        mLocationRepository.deleteAllLike("past");
        view.setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
    }

    public void startTracking(View view) {
        Button serviceButton = (Button)view;

        if(tracking) {
            tracking = false;
            serviceButton.setText(R.string.start_location_tracking);
            stopService(backgroundService);
        }else {
            tracking = true;
            serviceButton.setText(R.string.stop_location_tracking);
            ((MyApplication) this.getApplication()).setLocationDisplay(mLocationDisplay);
            startService(backgroundService);
        }

        serviceButton.setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
    }

    //--- MISC FUNCTIONS ---

    public void showToast(String str) {
        Toast toast = Toast.makeText(getApplicationContext(), str, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupButtons() {
        findViewById(R.id.routeMeButton).setOnTouchListener((v, event) -> {
            v.setBackgroundColor(0xFF6432c8);
            //v.performClick();
            return false;
        });
        findViewById(R.id.serviceButton).setOnTouchListener((v, event) -> {
            v.setBackgroundColor(0xFF6432c8);
            //v.performClick();
            return false;
        });
        findViewById(R.id.clearTrackedButton).setOnTouchListener((v, event) -> {
            v.setBackgroundColor(0xFF6432c8);
            //v.performClick();
            return false;
        });
        findViewById(R.id.clearRoutesButton).setOnTouchListener((v, event) -> {
            v.setBackgroundColor(0xFF6432c8);
            //v.performClick();
            return false;
        });
    }

    //--- ROUTING FUNCTIONS ---

    public void routeMe(View view) {
        Button routeButton = (Button)view;

        if(findViewById(R.id.loadingSpinner).getVisibility() == View.GONE && endPoint != null) {
            new routeMeASyncTask(this).execute(false);
            routeButton.setText(R.string.route_again);
        }else if(endPoint == null) {
            showToast("You must enter an address.");
        }else {
            showToast("Routing in progress, try again.");
        }

        routeButton.setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
    }

    private static boolean routeMe(boolean reroute) {
        List<PointBarrier> pointBarriers = new ArrayList<>();
        for (int x=0; x<mBarLocations.size(); x++) {
            Point p = new Point(mBarLocations.get(x).getLongitude(), mBarLocations.get(x).getLatitude());
            PointBarrier pb = new PointBarrier(p);
            pb.setAddedCost(mRouteParameters.getTravelMode().getImpedanceAttributeName(), 1.0);
            pb.setType(BarrierType.COST_ADJUSTMENT);
            pointBarriers.add(pb);
        }
        for (int x=0; x<mPastLocations.size(); x++) {
            Point p = new Point(mPastLocations.get(x).getLongitude(), mPastLocations.get(x).getLatitude());
            PointBarrier pb = new PointBarrier(p);
            pb.setAddedCost(mRouteParameters.getTravelMode().getImpedanceAttributeName(), 1.0);
            pb.setType(BarrierType.COST_ADJUSTMENT);
            pointBarriers.add(pb);
        }

        if(!reroute) {
            mRouteParameters.setPointBarriers(pointBarriers);
        }

        List<Stop> stops = new ArrayList<>();
        try {
            Stop currentLoc = new Stop(mLocationDisplay.getLocation().getPosition());
            currentLoc.setName("current location");
            stops.add(currentLoc);
            Stop destination = new Stop(endPoint);
            destination.setName("destination");
            stops.add(destination);
        }catch(Exception e) {
            e.printStackTrace();
            return false;
        }

        mRouteParameters.setStops(stops);
        mRouteParameters.setReturnDirections(true);
        mRouteParameters.setReturnStops(true);

        RouteResult result;
        try {
            result = mRouteTask.solveRouteAsync(mRouteParameters).get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        final List<Route> routes = result.getRoutes();
        mRoute = routes.get(0);

        int ptct = mRoute.getRouteGeometry().getParts().get(0).getPointCount();
        float lowerHalf = ptct / 2;
        float upperHalf = lowerHalf;
        int resolution = 30;
        for (int x = 0; x < (resolution / 2); x++) {
            Point lowerMidPoint = mRoute.getRouteGeometry().getParts().get(0).getPoint((int) lowerHalf);
            Point upperMidPoint = mRoute.getRouteGeometry().getParts().get(0).getPoint((int) upperHalf);
            lowerHalf = lowerHalf - ptct / resolution;
            upperHalf = upperHalf + ptct / resolution;
            Location lowerLocation = new Location("bar lower " + System.currentTimeMillis(), lowerMidPoint.getY(), lowerMidPoint.getX());
            Location upperLocation = new Location("bar upper " + System.currentTimeMillis(), upperMidPoint.getY(), upperMidPoint.getX());
            if (mLocationRepository.locationNotExists(lowerLocation.getLocation())) {
                try {
                    mLocationRepository.insert(lowerLocation);
                }catch (Exception ex) {
                    Log.d(TAG, ex.getMessage());
                }
            }
            if (!(upperLocation.getLatitude() == lowerLocation.getLatitude() && upperLocation.getLongitude() == lowerLocation.getLongitude())
                    && mLocationRepository.locationNotExists(upperLocation.getLocation())) {
                try {
                    mLocationRepository.insert(upperLocation);
                }catch (Exception ex) {
                    Log.d(TAG, ex.getMessage());
                }
            }
        }

        return true;
    }

    private static class routeMeASyncTask extends AsyncTask<Boolean, Void, Boolean> {
        WeakReference<MapActivity> mWeakActivity;

        routeMeASyncTask(MapActivity activity) {
            mWeakActivity = new WeakReference<>(activity);
        }

        @Override
        protected Boolean doInBackground(final Boolean... params) {
            return routeMe(params[0]);
        }

        @Override
        protected void onPreExecute() {
            ProgressBar loadingSpinner = mWeakActivity.get().findViewById(R.id.loadingSpinner);
            loadingSpinner.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            for (int x = 0; x < mRouteOverlays.size(); x++) {
                mWeakActivity.get().getMapView().getGraphicsOverlays().remove(mRouteOverlays.get(x));
                //mRouteOverlays.get(x).setOpacity(mRouteOverlays.get(x).getOpacity() / 2); ADD THIS BACK IN IF YOU WANT TO SHOW OLD ROUTES WITH TRANSPARENCY
                //mWeakActivity.get().getMapView().getGraphicsOverlays().add(0, mRouteOverlays.get(x)); ^^^
            }
            GraphicsOverlay routeOverlay = new GraphicsOverlay();
            routeOverlay.getGraphics().add(
                    new Graphic(mRoute.getRouteGeometry(),
                            new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 8)));
            mRouteOverlays.add(routeOverlay);
            mWeakActivity.get().getMapView().getGraphicsOverlays().add(0, mRouteOverlays.get(mRouteOverlays.size() - 1));

            //Viewpoint viewPoint = new Viewpoint(mRoute.getRouteGeometry().getExtent());
            //mWeakActivity.get().getMapView().setViewpointAsync(viewPoint);
            mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);

            mWeakActivity.get().mDirectionManeuvers = mRoute.getDirectionManeuvers();
            mWeakActivity.get().directionsIndex = 0;
            mWeakActivity.get().nextDirection();

            ProgressBar loadingSpinner = mWeakActivity.get().findViewById(R.id.loadingSpinner);
            loadingSpinner.setVisibility(View.GONE);
        }
    }

    public void clearRoutes() {
        for(int x=0; x<mRouteOverlays.size(); x++) {
            mRouteOverlays.get(x).getGraphics().clear();
        }
        mRouteOverlays.clear();
        mDirectionOverlay.getGraphics().clear();
        ((TextView) findViewById(R.id.approachingDirectionsText)).setText("");
        findViewById(R.id.approachingDirectionsText).setVisibility(View.GONE);
        ((TextView)findViewById(R.id.directionsText)).setText("");
        findViewById(R.id.directionsText).setVisibility(View.GONE);
        ((Button)findViewById(R.id.routeMeButton)).setText(R.string.route_me);
    }

    public void clearRoutes(View view) {
        mAddressSearchView.setQuery("", true);
        Button clearRoutesButton = findViewById(R.id.clearRoutesButton);
        clearRoutesButton.setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
    }

    //--- CLASSES ---

    private class ChallengeHandler implements AuthenticationChallengeHandler {
        @Override
        public AuthenticationChallengeResponse handleChallenge(AuthenticationChallenge challenge) {
            return new AuthenticationChallengeResponse(
                    AuthenticationChallengeResponse.Action.CONTINUE_WITH_CREDENTIAL, mPortal.getCredential());
        }
    }

    private class SearchViewListener implements SearchView.OnQueryTextListener {

        @Override
        public boolean onQueryTextSubmit(String address) {
            geoCodePlace(address);
            mAddressSearchView.clearFocus();
            return true;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            if (!newText.equals("")) {
                final ListenableFuture<List<SuggestResult>> suggestionsFuture = mLocatorTask.suggestAsync(newText);
                suggestionsFuture.addDoneListener(() -> {
                    try {
                        List<SuggestResult> suggestResults = suggestionsFuture.get();
                        MatrixCursor suggestionsCursor = new MatrixCursor(mColumnNames);
                        int key = 0;
                        for (SuggestResult result : suggestResults) {
                            suggestionsCursor.addRow(new Object[]{key++, result.getLabel()});
                        }
                        String[] cols = new String[]{COLUMN_NAME_ADDRESS};
                        int[] to = new int[]{R.id.suggestion_address};
                        SimpleCursorAdapter suggestionAdapter = new SimpleCursorAdapter(MapActivity.this,
                                R.layout.suggestion, suggestionsCursor, cols, to, 0);
                        mAddressSearchView.setSuggestionsAdapter(suggestionAdapter);
                        mAddressSearchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
                            @Override
                            public boolean onSuggestionSelect(int position) {
                                return false;
                            }

                            @Override
                            public boolean onSuggestionClick(int position) {
                                MatrixCursor selectedRow = (MatrixCursor) suggestionAdapter.getItem(position);
                                int selectedCursorIndex = selectedRow.getColumnIndex(COLUMN_NAME_ADDRESS);
                                String address = selectedRow.getString(selectedCursorIndex);
                                mAddressSearchView.setQuery(address, true);
                                return true;
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Geocode suggestion error: " + e.getMessage());
                    }
                });
            }else {
                endPoint = null;
                refreshEndPointGraphics(false);
                clearRoutes();
                mLocationRepository.deleteAllLike("bar");
            }
            return true;
        }
    }

    private class LocationChangedListener implements LocationDisplay.LocationChangedListener {
        MapActivity mMapActivity;

        LocationChangedListener(MapActivity activity) {
            mMapActivity = activity;
        }

        @Override
        public void onLocationChanged(LocationDisplay.LocationChangedEvent locationChangedEvent) {
            if(directionsIndex != 0) {
                boolean contains = GeometryEngine.contains(currentDirectionBuffer, locationChangedEvent.getLocation().getPosition());
                boolean nextContains = GeometryEngine.contains(nextDirectionBuffer, locationChangedEvent.getLocation().getPosition());
                if(!contains) {
                    lostNavigationCounter++;
                    if((lostNavigationCounter > 2) && (lostNavigationCounter <= 5) && nextContains) {
                        lostNavigationCounter = 0;
                        nextDirection();
                    }else if(lostNavigationCounter > 5) {
                        lostNavigationCounter = 0;
                        if (findViewById(R.id.loadingSpinner).getVisibility() == View.GONE
                                && endPoint != null && !directionTimerWaiting) {
                            new routeMeASyncTask(mMapActivity).execute(true);
                            startTimer();
                        }
                    }
                }else if(nextContains) {
                    approachingNextDirection();
                }
            }
        }
    }
}
