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
import com.esri.arcgisruntime.geometry.Point;
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
import com.esri.arcgisruntime.tasks.networkanalysis.PointBarrier;
import com.esri.arcgisruntime.tasks.networkanalysis.Route;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteParameters;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteResult;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteTask;
import com.esri.arcgisruntime.tasks.networkanalysis.Stop;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MapActivity extends AppCompatActivity {
    private static final String TAG = MapActivity.class.getSimpleName();

    public MapView getMapView() { return mMapView; }
    private MapView mMapView;
    private LocationDisplay mLocationDisplay;

    private static List<GraphicsOverlay> mRouteOverlays;
    private GraphicsOverlay mPastOverlay;
    private GraphicsOverlay mPinsOverlay;

    private static Route mRoute;
    private static RouteParameters mRouteParameters;
    private static RouteTask mRouteTask;
    private static Point startPoint;
    private static Point endPoint;

    private static LocationRepository mLocationRepository;
    private static List<Location> mBarLocations;

    private SearchView mAddressSearchView;
    private GeocodeParameters mAddressGeocodeParameters;
    private LocatorTask mLocatorTask;
    private PictureMarkerSymbol mPinSourceSymbol;
    private Callout mCallout;
    private GeocodeResult mGeocodeResult;

    private Intent backgroundService;
    private boolean tracking = false;

    private enum SearchType{ SEARCH_FROM, SEARCH_TO }
    private SearchType searchType = SearchType.SEARCH_FROM;

    private final String COLUMN_NAME_ADDRESS = "address";
    private final String[] mColumnNames = { BaseColumns._ID, COLUMN_NAME_ADDRESS };

    private Portal mPortal;

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
            geoCodeTypedAddress(address, searchType);
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
            }else if(searchType == SearchType.SEARCH_FROM) {
                startPoint = null;
                refreshStartEndGraphics(false);
            }else if(searchType == SearchType.SEARCH_TO){
                endPoint = null;
                refreshStartEndGraphics(false);
            }
            return true;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map_activity);

        backgroundService = new Intent(this, BackgroundService.class);

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

            mAddressSearchView = findViewById(R.id.searchView);
            mAddressSearchView.setIconified(false);
            mAddressSearchView.setFocusable(false);
            mAddressSearchView.clearFocus();
            mAddressSearchView.setQueryHint(getResources().getString(R.string.address_fromsearch_hint));

            BitmapDrawable pinDrawable = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.pin);
            try {
                assert pinDrawable != null;
                mPinSourceSymbol = PictureMarkerSymbol.createAsync(pinDrawable).get();
            } catch (InterruptedException | ExecutionException e) {
                Log.e(TAG, "Picture Marker Symbol error: " + e.getMessage());
                Toast.makeText(getApplicationContext(), "Failed to load pin drawable.", Toast.LENGTH_LONG).show();
            }
            // set pin to half of native size
            mPinSourceSymbol.setWidth(19f);
            mPinSourceSymbol.setHeight(72f);

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

            mLocationDisplay = mMapView.getLocationDisplay();
            mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.RECENTER);
            mLocationDisplay.startAsync();

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
            mLocatorTask = new LocatorTask("http://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer");

            mLocationRepository = new LocationRepository(getApplication());
            mLocationRepository.getBarLocations().observe(this, locations -> mBarLocations = locations);
            mLocationRepository.getPastLocations().observe(this, locations -> {
                mPastOverlay.getGraphics().clear();
                for (int x = 0; x < (locations != null ? locations.size() : 0); x++) {
                    Point p = CoordinateFormatter.fromLatitudeLongitude(
                            locations.get(x).getLatitude()+","+locations.get(x).getLongitude(),
                            null);
                    mPastOverlay.getGraphics().add(new Graphic(p, new SimpleMarkerSymbol(
                            SimpleMarkerSymbol.Style.CIRCLE, Color.GREEN, 20)));
                }
            });

            mRouteOverlays = new ArrayList<>();
            mPinsOverlay = new GraphicsOverlay();
            mMapView.getGraphicsOverlays().add(mPinsOverlay);
            mPastOverlay = new GraphicsOverlay();
            mMapView.getGraphicsOverlays().add(mPastOverlay);
            setupAddressSearchView();
        });
        mPortal.loadAsync();

        Log.d(TAG, "Main activity created");
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

    private void setupAddressSearchView() {
        mAddressGeocodeParameters = new GeocodeParameters();
        mAddressGeocodeParameters.getResultAttributeNames().add("PlaceName");
        mAddressGeocodeParameters.getResultAttributeNames().add("StAddr");
        mAddressGeocodeParameters.setMaxResults(1);

        mAddressSearchView.setOnQueryTextListener(new SearchViewListener());
    }

    public void toggleSearch(View view) {
        String queryHint;
        String searchType_str;
        if(searchType == SearchType.SEARCH_FROM) {
            searchType = SearchType.SEARCH_TO;
            searchType_str = getString(R.string.route_to);
            endPoint = null;
            queryHint = getResources().getString(R.string.address_tosearch_hint);
        }else {
            searchType = SearchType.SEARCH_FROM;
            searchType_str = getString(R.string.route_from);
            startPoint = null;
            queryHint = getResources().getString(R.string.address_fromsearch_hint);
        }

        Button toggleSearchButton = findViewById(R.id.toggleSearchButton);
        toggleSearchButton.setText(searchType_str);
        mAddressSearchView.setQueryHint(queryHint);
        mAddressSearchView.setQuery("", false);
        refreshStartEndGraphics(false);
    }

    private void geoCodeTypedAddress(final String address, final SearchType searchType) {
        if (address != null) {
            mLocatorTask.addDoneLoadingListener(() -> {
                if (mLocatorTask.getLoadStatus() == LoadStatus.LOADED) {
                    final ListenableFuture<List<GeocodeResult>> geocodeResultListenableFuture = mLocatorTask
                            .geocodeAsync(address, mAddressGeocodeParameters);
                    geocodeResultListenableFuture.addDoneListener(() -> {
                        try {
                            List<GeocodeResult> geocodeResults = geocodeResultListenableFuture.get();
                            if (geocodeResults.size() > 0) {
                                displaySearchResult(geocodeResults.get(0), searchType);
                            } else {
                                Toast.makeText(getApplicationContext(), getString(R.string.location_not_found) + address,
                                        Toast.LENGTH_LONG).show();
                            }
                        } catch (InterruptedException | ExecutionException e) {
                            Log.e(TAG, "Geocode error: " + e.getMessage());
                            Toast.makeText(getApplicationContext(), getString(R.string.geo_locate_error), Toast.LENGTH_LONG)
                                    .show();
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

    private void displaySearchResult(GeocodeResult geocodeResult, final SearchType searchType) {
        if (mMapView.getCallout() != null && mMapView.getCallout().isShowing()) {
            mMapView.getCallout().dismiss();
        }

        if(searchType == SearchType.SEARCH_FROM) {
            startPoint = geocodeResult.getDisplayLocation();
        }else if(searchType == SearchType.SEARCH_TO) {
            endPoint = geocodeResult.getDisplayLocation();
        }

        mGeocodeResult = geocodeResult;
        refreshStartEndGraphics(true);
    }

    private void refreshStartEndGraphics(boolean zoomTo) {
        mPinsOverlay.getGraphics().clear();
        if(startPoint != null) {
            Graphic startLocGraphic = new Graphic(startPoint, mGeocodeResult.getAttributes(), mPinSourceSymbol);
            mPinsOverlay.getGraphics().add(startLocGraphic);
        }
        if(endPoint != null) {
            Graphic endLocGraphic = new Graphic(endPoint, mGeocodeResult.getAttributes(), mPinSourceSymbol);
            mPinsOverlay.getGraphics().add(endLocGraphic);
        }

        if(zoomTo) {
            if (startPoint != null && endPoint != null) {
                mMapView.setViewpointAsync(new Viewpoint(new Envelope(startPoint, endPoint)));
            } else if (startPoint != null) {
                mMapView.setViewpointAsync(new Viewpoint(startPoint, 10000));
            } else if (endPoint != null){
                mMapView.setViewpointAsync(new Viewpoint(endPoint, 10000));
            }
        }

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
            Toast.makeText(MapActivity.this, "location denied", Toast.LENGTH_SHORT).show();
        }
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

    public void locateMe(View view) {
        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.RECENTER);
    }

    public void startTracking(View view) {
        if(tracking) {
            tracking = false;
            Button serviceButton = findViewById(R.id.serviceButton);
            serviceButton.setText(R.string.start_location_tracking);
            stopService(backgroundService);
        }else {
            tracking = true;
            Button serviceButton = findViewById(R.id.serviceButton);
            serviceButton.setText(R.string.stop_location_tracking);
            ((MyApplication) this.getApplication()).setLocationDisplay(mLocationDisplay);
            startService(backgroundService);
        }
    }

    private static boolean routeMe() {
        List<PointBarrier> pointBarriers = new ArrayList<>();
        for (int x = 0; x < mBarLocations.size(); x++) {
            Point p = new Point(mBarLocations.get(x).getLongitude(), mBarLocations.get(x).getLatitude());
            PointBarrier pb = new PointBarrier(p);
            pb.setAddedCost(mRouteParameters.getTravelMode().getImpedanceAttributeName(), 60.0);
            pb.setType(BarrierType.COST_ADJUSTMENT);
            pointBarriers.add(pb);
        }

        mRouteParameters.setPointBarriers(pointBarriers);

        List<Stop> stops = new ArrayList<>();
        try {
            stops.add(new Stop(startPoint));
            stops.add(new Stop(endPoint));
        }catch(Exception e) {
            e.printStackTrace();
            return false;
        }

        mRouteParameters.setStops(stops);
        mRouteParameters.setReturnDirections(true);

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
        int resolution = 10;
        for (int x = 0; x < (resolution / 2); x++) {
            Point lowerMidPoint = mRoute.getRouteGeometry().getParts().get(0).getPoint((int) lowerHalf);
            Point upperMidPoint = mRoute.getRouteGeometry().getParts().get(0).getPoint((int) upperHalf);
            lowerHalf = lowerHalf - ptct / resolution;
            upperHalf = upperHalf + ptct / resolution;
            Location lowerLocation = new Location("bar lower " + System.currentTimeMillis(), lowerMidPoint.getY(), lowerMidPoint.getX());
            Location upperLocation = new Location("bar upper " + System.currentTimeMillis(), upperMidPoint.getY(), upperMidPoint.getX());
            if (mLocationRepository.locationNotExists(lowerLocation.getLocation())) {
                mLocationRepository.insert(lowerLocation);
            }
            if (!(upperLocation.getLatitude() == lowerLocation.getLatitude() && upperLocation.getLongitude() == lowerLocation.getLongitude())
                    && mLocationRepository.locationNotExists(upperLocation.getLocation())) {
                mLocationRepository.insert(upperLocation);
            }
        }

        return true;
    }
    private static class routeMeASyncTask extends AsyncTask<Void, Void, Boolean> {
        WeakReference<MapActivity> mWeakActivity;

        routeMeASyncTask(MapActivity activity) {
            mWeakActivity = new WeakReference<>(activity);
        }

        @Override
        protected Boolean doInBackground(final Void... params) {
            return routeMe();
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
                mRouteOverlays.get(x).setOpacity(mRouteOverlays.get(x).getOpacity() / 2);
                mWeakActivity.get().getMapView().getGraphicsOverlays().add(mRouteOverlays.get(x));
            }
            GraphicsOverlay routeOverlay = new GraphicsOverlay();
            routeOverlay.getGraphics().add(
                    new Graphic(mRoute.getRouteGeometry(),
                            new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 8)));
            mRouteOverlays.add(routeOverlay);
            mWeakActivity.get().getMapView().getGraphicsOverlays().add(mRouteOverlays.get(mRouteOverlays.size() - 1));

            Viewpoint viewPoint = new Viewpoint(mRoute.getRouteGeometry().getExtent());
            mWeakActivity.get().getMapView().setViewpointAsync(viewPoint);

            ProgressBar loadingSpinner = mWeakActivity.get().findViewById(R.id.loadingSpinner);
            loadingSpinner.setVisibility(View.GONE);
        }
    }

    public void routeMe(View view) {
        if(findViewById(R.id.loadingSpinner).getVisibility() == View.GONE
                && startPoint != null && endPoint != null) {
            new routeMeASyncTask(this).execute();
        }
    }
}
