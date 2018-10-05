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
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.AngularUnit;
import com.esri.arcgisruntime.geometry.AngularUnitId;
import com.esri.arcgisruntime.geometry.CoordinateFormatter;
import com.esri.arcgisruntime.geometry.GeodeticCurveType;
import com.esri.arcgisruntime.geometry.GeodeticDistanceResult;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.GeometryType;
import com.esri.arcgisruntime.geometry.LinearUnit;
import com.esri.arcgisruntime.geometry.LinearUnitId;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.Polygon;
import com.esri.arcgisruntime.geometry.ProximityResult;
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
    private LocationDisplay mLocationDisplay;
    private List<Point> lastKnownLocations;
    private boolean locatingEnabled = false;

    private List<GraphicsOverlay> mRouteOverlays;
    private GraphicsOverlay mPastOverlay;
    private GraphicsOverlay mPinsOverlay;
    private GraphicsOverlay mDirectionOverlay;

    private static RouteParameters mRouteParameters;
    private static RouteTask mRouteTask;
    private Point endPoint;
    private boolean routingBusy = false;

    private List<DirectionManeuver> mDirectionManeuvers;
    private Polygon currentDirectionBuffer;
    private Polygon nextDirectionBuffer;
    private int directionsIndex = 0;
    private Timer directionTimer;
    private boolean directionTimerWaiting = false;
    private int lostNavigationCounter = 0;
    private boolean navigationLockedUp = false;

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
        lastKnownLocations = new ArrayList<>();
        mLocatorTask = new LocatorTask("http://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer");

        DefaultAuthenticationChallengeHandler handler = new DefaultAuthenticationChallengeHandler(this);
        AuthenticationManager.setAuthenticationChallengeHandler(handler);
        mPortal = new Portal(getString(R.string.portal_url), true);
        mPortal.addLoadStatusChangedListener(loadStatusChangedEvent -> {
            if(loadStatusChangedEvent.getNewLoadStatus() == LoadStatus.LOADED) {
                Log.d(TAG, "Portal Loaded");
                if (!canAccessLocation()) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
                }

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
            }else if(loadStatusChangedEvent.getNewLoadStatus() == LoadStatus.FAILED_TO_LOAD) {
                Log.d(TAG, "Portal Failed to Load");
                mPortal.retryLoadAsync();
            }else if(loadStatusChangedEvent.getNewLoadStatus() == LoadStatus.NOT_LOADED) {
                Log.d(TAG, "Portal not Loaded");
            }else if(loadStatusChangedEvent.getNewLoadStatus() == LoadStatus.LOADING) {
                Log.d(TAG, "Portal Loading");
            }
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
        double lon = -117.195800;
        double lat = 34.056295;
        ArcGISMap map = new ArcGISMap(Basemap.Type.TOPOGRAPHIC, lat, lon, 16);
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
                            SimpleMarkerSymbol.Style.X, Color.BLACK, 20)));
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
                locatingEnabled = false;
                ((ImageButton)findViewById(R.id.locateButton)).setImageResource(R.drawable.locate_icon);
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
        if(directionsIndex >= mDirectionManeuvers.size()-2) {
            //Reached the destination!!!
            mAddressSearchView.setQuery("", true);
        }else {
            //Still navigating to the destination
            DirectionManeuver nextManeuver = mDirectionManeuvers.get(directionsIndex + 1);
            ((TextView) findViewById(R.id.approachingDirectionsText)).setText(nextManeuver.getDirectionText());
            findViewById(R.id.approachingDirectionsText).setVisibility(View.VISIBLE);

            //Choose the right graphic for the job
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

            //Add next direction graphic
            mDirectionOverlay.getGraphics().add(directionGraphic);
        }
    }

    public void nextDirection() {
        if(++directionsIndex < mDirectionManeuvers.size()) {
            DirectionManeuver currentManeuver = mDirectionManeuvers.get(directionsIndex);
            if(directionsIndex < mDirectionManeuvers.size()) {
                double dist = 2500;
                if(directionsIndex == mDirectionManeuvers.size()-2) {
                    //Lower the buffer distance to be more precise when approaching the destination
                    dist = 500;
                }

                //Buffer 2500 feet for the next direction geometry
                //Or 500 feet, if we are approaching the destination, to be more precise
                DirectionManeuver nextManeuver = mDirectionManeuvers.get(directionsIndex+1);
                nextDirectionBuffer = GeometryEngine.bufferGeodetic(
                        nextManeuver.getGeometry(),
                        dist,
                        new LinearUnit(LinearUnitId.FEET),
                        5.0,
                        GeodeticCurveType.GEODESIC);
            }
            ((TextView) findViewById(R.id.approachingDirectionsText)).setText("");
            findViewById(R.id.approachingDirectionsText).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.directionsText)).setText(currentManeuver.getDirectionText());
            findViewById(R.id.directionsText).setVisibility(View.VISIBLE);

            //Buffer 150 feet for the current direction geometry
            currentDirectionBuffer = GeometryEngine.bufferGeodetic(
                    currentManeuver.getGeometry(),
                    150,
                    new LinearUnit(LinearUnitId.FEET),
                    5.0,
                    GeodeticCurveType.GEODESIC);
            mDirectionOverlay.getGraphics().clear();

            //Choose the right graphic for the job
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

            //Add current direction graphic
            mDirectionOverlay.getGraphics().add(directionGraphic);

            //If the next direction maneuver is very close to the beginning of this one
            if(GeometryEngine.contains(nextDirectionBuffer, mLocationDisplay.getLocation().getPosition())) {
                approachingNextDirection();
            }
        }else {
            Log.d(TAG, "Error in getting next direction");
        }
    }

    public void changeNavigation(View view) {
        navigationLockedUp = !navigationLockedUp;
        if(navigationLockedUp) {
            ((ImageButton)findViewById(R.id.navigateButton)).setImageResource(R.drawable.navigate_icon_enabled);
        }else {
            ((ImageButton)findViewById(R.id.navigateButton)).setImageResource(R.drawable.navigate_icon);
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
            mLocationDisplay.startAsync();
        } else {
            showToast("Location denied.");
        }
    }

    public void locateMe(View view) {
        locatingEnabled = !locatingEnabled;
        if(locatingEnabled) {
            mMapView.setViewpointAsync(new Viewpoint(mLocationDisplay.getLocation().getPosition(), 10000));
            ((ImageButton)findViewById(R.id.locateButton)).setImageResource(R.drawable.locate_icon_enabled);
        }else {
            ((ImageButton)findViewById(R.id.locateButton)).setImageResource(R.drawable.locate_icon);
        }
    }

    public void clearTracked(View view) {
        mLocationRepository.deleteAllLike("past");
        view.setBackgroundColor(getResources().getColor(R.color.colorAccent));
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

        serviceButton.setBackgroundColor(getResources().getColor(R.color.colorPrimaryLight));
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
            return false;
        });
        findViewById(R.id.serviceButton).setOnTouchListener((v, event) -> {
            v.setBackgroundColor(0xFF6432c8);
            return false;
        });
        findViewById(R.id.clearTrackedButton).setOnTouchListener((v, event) -> {
            v.setBackgroundColor(0xFF6432c8);
            return false;
        });
        findViewById(R.id.clearRoutesButton).setOnTouchListener((v, event) -> {
            v.setBackgroundColor(0xFF6432c8);
            return false;
        });
    }

    //--- ROUTING FUNCTIONS ---

    public void routeMe(View view) {
        Button routeButton = (Button)view;

        if(findViewById(R.id.loadingSpinner).getVisibility() == View.GONE && endPoint != null && !routingBusy) {
            new routeMeASyncTask(this).execute(new RouteTaskParams(mLocationDisplay.getLocation().getPosition(), endPoint, false));
            routeButton.setText(R.string.route_again);
        }else if(endPoint == null) {
            showToast("You must enter an address.");
        }else {
            showToast("Routing in progress, try again.");
        }

        routeButton.setBackgroundColor(getResources().getColor(R.color.colorPrimaryLight));
    }

    private static RouteTaskReturns routeMe(RouteTaskParams params) {
        Point startPoint = params.startPoint;
        Point endPoint = params.endPoint;
        boolean reroute = params.reroute;

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
            Stop currentLoc = new Stop(startPoint);
            currentLoc.setName("current location");
            stops.add(currentLoc);
            Stop destination = new Stop(endPoint);
            destination.setName("destination");
            stops.add(destination);
        }catch(Exception e) {
            e.printStackTrace();
            return new RouteTaskReturns(null, false);
        }

        mRouteParameters.setStops(stops);
        mRouteParameters.setReturnDirections(true);
        mRouteParameters.setReturnStops(true);

        RouteResult result;
        try {
            result = mRouteTask.solveRouteAsync(mRouteParameters).get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return new RouteTaskReturns(null, false);
        }
        final List<Route> routes = result.getRoutes();
        Route route = routes.get(0);

        int ptct = route.getRouteGeometry().getParts().get(0).getPointCount();
        int resolution = 30;
        if(ptct < resolution) {
            resolution = ptct;
        }
        float lowerHalf = ptct / 2;
        float upperHalf = lowerHalf;

        for (int x = 0; x < (resolution / 2); x++) {
            Point lowerMidPoint = route.getRouteGeometry().getParts().get(0).getPoint((int) lowerHalf);
            Point upperMidPoint = route.getRouteGeometry().getParts().get(0).getPoint((int) upperHalf);
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

        return new RouteTaskReturns(route, true);
    }

    private static class RouteTaskParams {
        Point startPoint;
        Point endPoint;
        boolean reroute;

        RouteTaskParams(Point startPoint, Point endPoint, boolean reroute) {
            this.startPoint = startPoint;
            this.endPoint = endPoint;
            this.reroute = reroute;
        }
    }

    private static class RouteTaskReturns {
        boolean success;
        Route route;

        RouteTaskReturns(Route route, boolean success) {
            this.route = route;
            this.success = success;
        }
    }

    private static class routeMeASyncTask extends AsyncTask<RouteTaskParams, Void, RouteTaskReturns> {
        WeakReference<MapActivity> mWeakActivity;
        RouteTaskParams mRouteTaskParams;

        routeMeASyncTask(MapActivity activity) {
            mWeakActivity = new WeakReference<>(activity);
        }

        @Override
        protected RouteTaskReturns doInBackground(final RouteTaskParams... params) {
            mRouteTaskParams = params[0];
            return routeMe(mRouteTaskParams);
        }

        @Override
        protected void onPreExecute() {
            mWeakActivity.get().routingBusy = true;
            ProgressBar loadingSpinner = mWeakActivity.get().findViewById(R.id.loadingSpinner);
            loadingSpinner.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onPostExecute(RouteTaskReturns result) {
            if(result.success) {
                for (int x = 0; x < mWeakActivity.get().mRouteOverlays.size(); x++) {
                    mWeakActivity.get().getMapView().getGraphicsOverlays().remove(mWeakActivity.get().mRouteOverlays.get(x));
                }
                GraphicsOverlay routeOverlay = new GraphicsOverlay();
                routeOverlay.getGraphics().add(
                        new Graphic(result.route.getRouteGeometry(),
                                new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 8)));
                mWeakActivity.get().mRouteOverlays.add(routeOverlay);
                mWeakActivity.get().getMapView().getGraphicsOverlays().add(0,
                        mWeakActivity.get().mRouteOverlays.get(mWeakActivity.get().mRouteOverlays.size() - 1));

                if(!mRouteTaskParams.reroute) {
                    Viewpoint viewPoint = new Viewpoint(result.route.getRouteGeometry().getExtent());
                    mWeakActivity.get().getMapView().setViewpointAsync(viewPoint, 3);
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            mWeakActivity.get().routingBusy = false;
                        }
                    }, 5000);
                }else{
                    mWeakActivity.get().routingBusy = false;
                }

                mWeakActivity.get().mDirectionManeuvers = result.route.getDirectionManeuvers();
                mWeakActivity.get().directionsIndex = 0;
                mWeakActivity.get().nextDirection();

                ProgressBar loadingSpinner = mWeakActivity.get().findViewById(R.id.loadingSpinner);
                loadingSpinner.setVisibility(View.GONE);
            }
        }
    }

    public void clearRoutes() {
        for(int x=0; x<mRouteOverlays.size(); x++) {
            mRouteOverlays.get(x).getGraphics().clear();
        }
        mRouteOverlays.clear();
        mDirectionOverlay.getGraphics().clear();
        directionsIndex = 0;
        ((TextView) findViewById(R.id.approachingDirectionsText)).setText("");
        findViewById(R.id.approachingDirectionsText).setVisibility(View.GONE);
        ((TextView)findViewById(R.id.directionsText)).setText("");
        findViewById(R.id.directionsText).setVisibility(View.GONE);
        ((Button)findViewById(R.id.routeMeButton)).setText(R.string.route_me);
    }

    public void clearRoutes(View view) {
        mAddressSearchView.setQuery("", true);
        Button clearRoutesButton = findViewById(R.id.clearRoutesButton);
        clearRoutesButton.setBackgroundColor(getResources().getColor(R.color.colorAccent));
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
            if(directionsIndex > 0 && currentDirectionBuffer != null && nextDirectionBuffer != null) {
                boolean contains = GeometryEngine.contains(currentDirectionBuffer, locationChangedEvent.getLocation().getPosition());
                boolean nextContains;
                if(!contains) {
                    lostNavigationCounter++;
                    nextContains = GeometryEngine.contains(nextDirectionBuffer, locationChangedEvent.getLocation().getPosition());
                    if (nextContains && lostNavigationCounter > 2) {
                        lostNavigationCounter = 0;
                        nextDirectionBuffer = null;
                        nextDirection();
                    } else if (lostNavigationCounter > 5) {
                        lostNavigationCounter = 0;
                        if (findViewById(R.id.loadingSpinner).getVisibility() == View.GONE && !routingBusy
                                && endPoint != null && !directionTimerWaiting) {
                            new routeMeASyncTask(mMapActivity).execute(new RouteTaskParams(locationChangedEvent.getLocation().getPosition(), endPoint, true));
                            startTimer();
                        }
                    }
                }else {
                    lostNavigationCounter = 0;
                }

                nextContains = GeometryEngine.contains(nextDirectionBuffer, locationChangedEvent.getLocation().getPosition());

                //If directionsIndex > 0 then we are in the process of navigating,
                //otherwise the navigation has ended or is not started yet
                if(nextContains && mDirectionOverlay.getGraphics().size() < 2 && directionsIndex > 0) {
                    approachingNextDirection();
                }

                Point nearPoint = GeometryEngine.nearestCoordinate(
                        mDirectionManeuvers.get(directionsIndex+1).getGeometry(),
                        locationChangedEvent.getLocation().getPosition()).getCoordinate();
                GeodeticDistanceResult geodeticDistanceResult = GeometryEngine.distanceGeodetic(
                        locationChangedEvent.getLocation().getPosition(),
                        nearPoint,
                        new LinearUnit(LinearUnitId.MILES),
                        new AngularUnit(AngularUnitId.DEGREES),
                        GeodeticCurveType.GEODESIC);
                ((TextView)findViewById(R.id.directionsText)).setText(
                        String.format("%s for %.2f Miles",
                                mDirectionManeuvers.get(directionsIndex).getDirectionText(),
                                geodeticDistanceResult.getDistance()));
            }

            if(findViewById(R.id.loadingSpinner).getVisibility() == View.GONE
                    && !routingBusy && locatingEnabled) {
                this.locate();
            }

            if(lastKnownLocations.size() > 5) {
                lastKnownLocations.remove(0);
            }
            lastKnownLocations.add(locationChangedEvent.getLocation().getPosition());
        }

        private void locate() {
            Point lastAvgLocation = null;
            if(lastKnownLocations != null && lastKnownLocations.size() > 0) {
                double avgX = 0; double avgY = 0;
                for (int x = 0; x < lastKnownLocations.size(); x++) {
                    avgX += lastKnownLocations.get(x).getX();
                    avgY += lastKnownLocations.get(x).getY();
                }
                avgX = avgX / lastKnownLocations.size();
                avgY = avgY / lastKnownLocations.size();

                lastAvgLocation = new Point(avgX, avgY, mLocationDisplay.getLocation().getPosition().getSpatialReference());
            }

            double rotation = 0;
            if(navigationLockedUp && lastAvgLocation != null) {
                GeodeticDistanceResult geodeticDistanceResult = GeometryEngine.distanceGeodetic(mLocationDisplay.getLocation().getPosition(),
                        lastAvgLocation,
                        new LinearUnit(LinearUnitId.FEET),
                        new AngularUnit(AngularUnitId.DEGREES),
                        GeodeticCurveType.GEODESIC);
                rotation = geodeticDistanceResult.getAzimuth2();
                if (rotation < 0) {
                    rotation = 360 + rotation;
                }
            }
            mMapView.setViewpointAsync(new Viewpoint(
                    mLocationDisplay.getLocation().getPosition(),
                    10000,
                    rotation));
        }
    }
}
