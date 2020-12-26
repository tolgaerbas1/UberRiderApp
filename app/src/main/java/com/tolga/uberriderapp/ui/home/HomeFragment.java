package com.tolga.uberriderapp.ui.home;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import com.tolga.uberriderapp.Callback.IFirebaseDriverInfoListener;
import com.tolga.uberriderapp.Callback.IFirebaseFailedListener;
import com.tolga.uberriderapp.Common;
import com.tolga.uberriderapp.Model.AnimationModel;
import com.tolga.uberriderapp.Model.DriverGeoModel;
import com.tolga.uberriderapp.Model.DriverInfoModel;
import com.tolga.uberriderapp.Model.GeoQueryModel;
import com.tolga.uberriderapp.R;
import com.tolga.uberriderapp.Remote.IGoogleAPI;
import com.tolga.uberriderapp.Remote.RetrofitClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.internal.operators.observable.ObservableFromIterable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class HomeFragment extends Fragment implements OnMapReadyCallback, IFirebaseDriverInfoListener,IFirebaseFailedListener {

    @BindView(R.id.activity_main)
    SlidingUpPanelLayout slidingUpPanelLayout;
    @BindView(R.id.txt_welcome)
    TextView txt_welcome;

    private HomeViewModel homeViewModel;
    private GoogleMap mMap;
    private SupportMapFragment mapFragment;

    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    GeoFire geoFire;

    //Load Driver
    private double distance = 1.0; //default in km
    private static final double LIMIT_RANGE = 10.0; //km
    private Location previousLocation, currentLocation;
    private boolean firstTime = true;
    //Listeners
    IFirebaseDriverInfoListener iFirebaseDriverInfoListener;
    IFirebaseFailedListener iFirebaseFailedListener;
    private String cityName;

    //
    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private IGoogleAPI iGoogleAPI;



    @Override
    public void onStop() {
        compositeDisposable.clear();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
    }


    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        init();
        initViews(root);
        mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        return root;

    }

    private void initViews(View root) {
        ButterKnife.bind(this,root);

        Common.setWelcomeMessage(txt_welcome);


    }

    private void init() {
        Places.initialize(getContext(),getString(R.string.google_maps_key));


        iGoogleAPI = RetrofitClient.getInstance().create(IGoogleAPI.class);


        iFirebaseDriverInfoListener = (IFirebaseDriverInfoListener) this;
        iFirebaseFailedListener = (IFirebaseFailedListener) this;


        locationRequest = new LocationRequest();
        locationRequest.setSmallestDisplacement(10f);
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(3000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);

                LatLng newPosition = new LatLng(locationResult.getLastLocation().getLatitude(),
                        locationResult.getLastLocation().getLongitude());
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPosition, 18f));

                if (firstTime) {
                    previousLocation = currentLocation = locationResult.getLastLocation();
                    firstTime = false;
                } else {
                    previousLocation = currentLocation;
                    currentLocation = locationResult.getLastLocation();
                }

                if (previousLocation.distanceTo(currentLocation) / 1000 <= LIMIT_RANGE) {
                    loadAvailableDrivers();
                } else {

                }
            }
        };


        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(getContext());
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());

        loadAvailableDrivers();
    }

    private void loadAvailableDrivers() {

        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                &&
                ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(getView(),getString(R.string.permission_require),Snackbar.LENGTH_SHORT).show();
            return;
        }
        fusedLocationProviderClient.getLastLocation()
                .addOnFailureListener(e -> {
                    Snackbar.make(getView(), e.getMessage(), Snackbar.LENGTH_SHORT).show();
                }).addOnSuccessListener(location -> {
                    Geocoder geocoder = new Geocoder(getContext(),Locale.getDefault());
                    List<Address> addressList;
                    try {
                        addressList = geocoder.getFromLocation(location.getLatitude(),location.getLongitude(),1);
                        cityName =  addressList.get(0).getLocality();

                        //Query
                        DatabaseReference driver_location_ref = FirebaseDatabase.getInstance()
                                .getReference(Common.DRIVERS_LOCATION_REFERENCES)
                                .child(cityName);
                        GeoFire geoFire = new GeoFire(driver_location_ref);
                        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(location.getLatitude(),
                                location.getLongitude()),distance);
                        geoQuery.removeAllListeners();

                        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
                            @Override
                            public void onKeyEntered(String key, GeoLocation location) {
                                Common.driversFound.add(new DriverGeoModel(key,location));
                            }

                            @Override
                            public void onKeyExited(String key) {

                            }

                            @Override
                            public void onKeyMoved(String key, GeoLocation location) {

                            }

                            @Override
                            public void onGeoQueryReady() {
                                if(distance <= LIMIT_RANGE){
                                    distance ++;
                                    loadAvailableDrivers();
                                }else{
                                    distance = 1.0;
                                    addDriveMarker();
                                }

                            }

                            @Override
                            public void onGeoQueryError(DatabaseError error) {
                                Snackbar.make(getView(),error.getMessage(),Snackbar.LENGTH_SHORT).show();
                            }
                        });

                        driver_location_ref.addChildEventListener(new ChildEventListener() {
                            @Override
                            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                                GeoQueryModel  geoQueryModel = snapshot.getValue(GeoQueryModel.class);
                                GeoLocation geoLocation = new GeoLocation(geoQueryModel.getL().get(0),
                                        geoQueryModel.getL().get(1));
                                DriverGeoModel driverGeoModel = new DriverGeoModel(snapshot.getKey(),
                                        geoLocation);
                                Location newDriverLocation = new Location("");
                                newDriverLocation.setLatitude(geoLocation.latitude);
                                newDriverLocation.setLongitude(geoLocation.longitude);
                                float newDistance = location.distanceTo(newDriverLocation)/1000;
                                if(newDistance <= LIMIT_RANGE)
                                    findDriverByKey(driverGeoModel); //if driver in range, add to map
                            }

                            @Override
                            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                            }

                            @Override
                            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

                            }

                            @Override
                            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {

                            }
                        });

                    }catch (IOException e){
                        e.printStackTrace();
                        Snackbar.make(getView(),e.getMessage(),Snackbar.LENGTH_SHORT).show();
                    }
        });

    }

    private void addDriveMarker() {
        if(Common.driversFound.size() > 0){
            new ObservableFromIterable(Common.driversFound)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(driverGeoModel -> {
                        findDriverByKey((DriverGeoModel) driverGeoModel);
                    },throwable -> {
                       Snackbar.make(getView(),throwable.toString(),Snackbar.LENGTH_SHORT).show();
                    },()->{

                    });
        }else {
            Snackbar.make(getView(),getString(R.string.drivers_not_found),Snackbar.LENGTH_SHORT).show();
        }
    }

    private void findDriverByKey(DriverGeoModel driverGeoModel) {
        FirebaseDatabase.getInstance()
                .getReference(Common.DRIVERS_INFO_REFERENCE)
                .child(driverGeoModel.getKey())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if(snapshot.hasChildren()){
                            driverGeoModel.setDriverInfoModel(snapshot.getValue(DriverInfoModel.class));
                            iFirebaseDriverInfoListener.onDriverInfoLoad(driverGeoModel);
                        }else{
                            iFirebaseFailedListener.onFirebaseLoadFailed(getString(R.string.not_found_key)+driverGeoModel.getKey());
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });

    }
    public void onFirebaseLoadFailed(String message){
        Snackbar.make(getView(),message,Snackbar.LENGTH_SHORT).show();

    }
    public void onDriverInfoLoadSuccess(DriverGeoModel driverGeoModel){
        if(!Common.markerList.containsKey(driverGeoModel.getKey())){
            Common.markerList.put(driverGeoModel.getKey(),
                    mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(driverGeoModel.getGeoLocation().latitude,driverGeoModel.getGeoLocation().longitude))
                    .flat(true)
                    .title(Common.buildName(driverGeoModel.getDriverInfoModel().getFirstName(),
                            driverGeoModel.getDriverInfoModel().getLastName()))
                    .snippet(driverGeoModel.getDriverInfoModel().getPhoneNumber())
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))));
        }
        if(!TextUtils.isEmpty(cityName)){
            DatabaseReference driverLocation = FirebaseDatabase.getInstance()
                    .getReference(Common.DRIVERS_LOCATION_REFERENCES)
                    .child(cityName)
                    .child(driverGeoModel.getKey());
            driverLocation.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if(!snapshot.hasChildren()){
                        if(Common.markerList.get(driverGeoModel.getKey()) != null ){
                            Common.markerList.get(driverGeoModel.getKey()).remove(); //Remove marker
                        }
                        Common.markerList.remove(driverGeoModel.getKey()); //Remove marker from hashmap
                        Common.driverLocationSubscribe.remove(driverGeoModel.getKey()); //Remove driver information
                        driverLocation.removeEventListener(this); //Remove event listener
                    }else{
                        if(Common.markerList.get(driverGeoModel.getKey())!= null){
                            GeoQueryModel geoQueryModel = snapshot.getValue(GeoQueryModel.class);
                            AnimationModel animationModel = new AnimationModel(false,geoQueryModel);
                            if(Common.driverLocationSubscribe.get(driverGeoModel.getKey()) != null){
                                Marker currentMarker = Common.markerList.get(driverGeoModel.getKey());
                                AnimationModel oldPosition = Common.driverLocationSubscribe.get(driverGeoModel.getKey());

                                String from = new StringBuilder()
                                        .append(oldPosition.getGeoQueryModel().getL().get(0))
                                        .append(",")
                                        .append(oldPosition.getGeoQueryModel().getL().get(1))
                                        .toString();
                                String to = new StringBuilder()
                                        .append(animationModel.getGeoQueryModel().getL().get(0))
                                        .append(",")
                                        .append(animationModel.getGeoQueryModel().getL().get(1))
                                        .toString();
                                moveMarkerAnimation(driverGeoModel.getKey(),animationModel,currentMarker,from,to);

                            }
                            else{
                                Common.driverLocationSubscribe.put(driverGeoModel.getKey(),animationModel);
                            }



                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Snackbar.make(getView(),error.getMessage(),Snackbar.LENGTH_SHORT).show();
                }
            });

        }


    }

    private void moveMarkerAnimation(String key, AnimationModel animationModel, Marker currentMarker, String from, String to) {
        if(!animationModel.isRun()){
            compositeDisposable.add(iGoogleAPI.getDirections("driving",
                    "less driving",
                    from,to,
                    getString(R.string.google_api_key))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(returnResult ->{
             Log.d("API_RETURN",returnResult);
             try {
                 JSONObject jsonObject = new JSONObject(returnResult);
                 JSONArray jsonArray = jsonObject.getJSONArray("routes");
                 for (int i=0 ; i< jsonArray.length();i++){
                     JSONObject route = jsonArray.getJSONObject(i);
                     JSONObject poly = jsonArray.getJSONObject(Integer.parseInt("overview polyline"));
                     String polyline = poly.getString("points");
                     //polylineList = Common.decodePoly(polyline);
                     animationModel.setPolylineList(Common.decodePoly(polyline));
                 }


                 //index = -1;
                 //next = -1;
                 animationModel.setIndex(-1);
                 animationModel.setNext(1);

                 Runnable runnable = new Runnable() {
                     @Override
                     public void run() {
                        if(animationModel.getPolylineList() != null && animationModel.getPolylineList().size() > 1){
                            if(animationModel.getIndex() < animationModel.getPolylineList().size() - 2){
                                animationModel.setIndex(animationModel.getIndex()+1);
                                animationModel.setNext(animationModel.getIndex()+1);
                                animationModel.setStart(animationModel.getPolylineList().get(animationModel.getIndex()));
                                animationModel.setEnd(animationModel.getPolylineList().get(animationModel.getNext()));
                            }
                            ValueAnimator valueAnimator = ValueAnimator.ofInt(0,1);
                            valueAnimator.setDuration(3000);
                            valueAnimator.setInterpolator(new LinearInterpolator());
                            valueAnimator.addUpdateListener(value -> {
                                //v = value.getAnimatedFraction();
                                animationModel.setV(value.getAnimatedFraction());
                                //lat = v*end.latitude+ (1-v)* start.latitude;
                                animationModel.setLat(animationModel.getV()* animationModel.getEnd().latitude+(1-animationModel.getV()) * animationModel.getStart().latitude);
                                //lng = v*end.longitude+ (1-v)*start.longitude;
                                animationModel.setLat(animationModel.getV()* animationModel.getEnd().longitude+(1-animationModel.getV()) * animationModel.getStart().longitude);
                                LatLng newPos = new LatLng(animationModel.getLat(),animationModel.getLng());
                                currentMarker.setPosition(newPos);
                                currentMarker.setAnchor(0.5f,0.5f);
                                currentMarker.setRotation(Common.getBearing(animationModel.getStart(),newPos));

                            });
                            valueAnimator.start();
                            if(animationModel.getIndex() < animationModel.getPolylineList().size()-2) // Reach destination
                                animationModel.getHandler().postDelayed(this,1500);
                            else if(animationModel.getIndex() < animationModel.getPolylineList().size()-1) { //Done
                                animationModel.setRun(false);
                                Common.driverLocationSubscribe.put(key,animationModel);
                            }
                        }
                     }
                 };
                animationModel.getHandler().postDelayed(runnable,1500); // Run handler


             }catch (Exception e){
                 Snackbar.make(getView(),e.getMessage(),Snackbar.LENGTH_SHORT).show();
             }
            })
            );
        }

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        mMap = googleMap;

        Dexter.withContext(getContext())
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                                ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            Snackbar.make(getView(),getString(R.string.permission_require),Snackbar.LENGTH_SHORT).show();

                            return;
                        }
                        mMap.setMyLocationEnabled(true);
                        mMap.getUiSettings().setMyLocationButtonEnabled(true);
                        mMap.setOnMyLocationButtonClickListener(() -> {
                            fusedLocationProviderClient.getLastLocation()
                                    .addOnFailureListener(e -> Toast.makeText(getContext(), ""+e.getMessage(), Toast.LENGTH_SHORT).show())
                                    .addOnSuccessListener(location -> {
                                        LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng,18f));
                                    });
                            return true;
                        });

                        //Layout button
                        View locationButton = ((View)mapFragment.getView().findViewById(Integer.parseInt("1")).getParent())
                                .findViewById(Integer.parseInt("2"));
                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) locationButton.getLayoutParams();

                        //Right Button
                        params.addRule(RelativeLayout.ALIGN_PARENT_TOP,0);
                        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,RelativeLayout.TRUE);
                        params.setMargins(0,0,0,50);


                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                        Snackbar.make(getView(), permissionDeniedResponse.getPermissionName()+ "need enable" ,Snackbar.LENGTH_SHORT).show();

                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {

                    }
                })
                .check();
            mMap.getUiSettings().setZoomControlsEnabled(true);
        try {
            boolean success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(getContext(),R.raw.uber_maps_style ));
            if(!success){
                Log.e("ERROR", "Style Parsing Error");
            }
        }catch (Resources.NotFoundException e){
            Log.e("ERROR", e.getMessage());
        }
    }

    @Override
    public void onDriverInfoLoad(DriverGeoModel driverGeoModel) {

    }
}