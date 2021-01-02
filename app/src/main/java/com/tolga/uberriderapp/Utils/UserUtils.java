package com.tolga.uberriderapp.Utils;

import android.content.Context;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.tolga.uberriderapp.Common;
import com.tolga.uberriderapp.Model.DriverGeoModel;
import com.tolga.uberriderapp.Model.EventBus.SelectedPlaceEvent;
import com.tolga.uberriderapp.Model.FCMSendData;
import com.tolga.uberriderapp.Model.TokenModel;
import com.tolga.uberriderapp.R;
import com.tolga.uberriderapp.Remote.IFCMService;
import com.tolga.uberriderapp.Remote.RetrofitFCMClient;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;


public class UserUtils {
    public static void updateUser(View view, Map<String, Object> updateData) {
        FirebaseDatabase.getInstance()
                .getReference(Common.RIDER_INFO_REFERENCE)
                .child(FirebaseAuth.getInstance().getCurrentUser().getUid())
                .updateChildren(updateData)
                .addOnFailureListener(e -> Snackbar.make(view,e.getMessage(),Snackbar.LENGTH_SHORT).show())
                .addOnSuccessListener(aVoid -> Snackbar.make(view,"Update information succesfully.",Snackbar.LENGTH_SHORT).show());
    }


    public static void updateToken(Context context, String token) {

        TokenModel tokenModel = new TokenModel(token);
        FirebaseDatabase.getInstance()
                .getReference(Common.TOKEN_REFERENCE)
                .child(FirebaseAuth.getInstance().getCurrentUser().getUid())
                .setValue(tokenModel)
                .addOnFailureListener(e -> Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show())
                .addOnSuccessListener(aVoid -> {

                });
    }

    public static void sendRequestToDriver(Context context, RelativeLayout main_layout, DriverGeoModel foundDriver, SelectedPlaceEvent selectedPlaceEvent) {

        CompositeDisposable compositeDisposable = new CompositeDisposable();
        IFCMService ifcmService = RetrofitFCMClient.getInstance().create(IFCMService.class);

        //Get token
        FirebaseDatabase
                .getInstance()
                .getReference(Common.TOKEN_REFERENCE)
                .child(foundDriver.getKey())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        if(snapshot.exists()){

                            TokenModel tokenModel = snapshot.getValue(TokenModel.class);
                            Map<String,String> notificationData = new HashMap<>();
                            notificationData.put(Common.NOTI_TITLE,Common.REQUEST_DRIVER_TITLE);
                            notificationData.put(Common.NOTI_CONTENT,"This message represent for request driver action");
                            notificationData.put(Common.RIDER_KEY,FirebaseAuth.getInstance().getCurrentUser().getUid());

                            notificationData.put(Common.RIDER_PICKUP_LOCATION_STRING, selectedPlaceEvent.getOriginString());
                            notificationData.put(Common.RIDER_PICKUP_LOCATION,new StringBuilder("")
                                    .append(selectedPlaceEvent.getOrigin().latitude)
                                    .append(",")
                                    .append(selectedPlaceEvent.getOrigin().longitude)
                                    .toString());
                            notificationData.put(Common.RIDER_DESTINATION_STRING, selectedPlaceEvent.getAddress());
                            notificationData.put(Common.RIDER_DESTINATION,new StringBuilder("")
                                    .append(selectedPlaceEvent.getDestination().latitude)
                                    .append(",")
                                    .append(selectedPlaceEvent.getDestination().longitude)
                                    .toString());

                            FCMSendData fcmSendData = new FCMSendData(tokenModel.getToken(),notificationData);

                            compositeDisposable.add(ifcmService.sendNotification(fcmSendData)
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(fcmResponse -> {
                                if(fcmResponse.getSuccess() == 0){
                                    compositeDisposable.clear();
                                    Snackbar.make(main_layout,context.getString(R.string.request_driver_failed),Snackbar.LENGTH_LONG).show();
                                }
                            }, throwable -> {
                                compositeDisposable.clear();
                                Snackbar.make(main_layout,throwable.getMessage(),Snackbar.LENGTH_LONG).show();
                            }));

                        }else {
                            Snackbar.make(main_layout,context.getString(R.string.token_not_found),Snackbar.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Snackbar.make(main_layout,error.getMessage(),Snackbar.LENGTH_LONG).show();
                    }
                });



    }
}
