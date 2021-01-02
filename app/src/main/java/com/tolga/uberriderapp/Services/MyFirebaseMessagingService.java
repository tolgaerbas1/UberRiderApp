package com.tolga.uberriderapp.Services;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.tolga.uberriderapp.Common;
import com.tolga.uberriderapp.Model.EventBus.DeclineRequestAndRemoveTripFromDriver;
import com.tolga.uberriderapp.Model.EventBus.DeclineRequestFromDriver;
import com.tolga.uberriderapp.Model.EventBus.DriverAcceptTripEvent;
import com.tolga.uberriderapp.Model.EventBus.DriverCompleteTripEvent;
import com.tolga.uberriderapp.Utils.UserUtils;

import org.greenrobot.eventbus.EventBus;

import java.util.Map;
import java.util.Random;

public class MyFirebaseMessagingService extends FirebaseMessagingService {


    @Override
    public void onNewToken(@NonNull String s) {
        super.onNewToken(s);
        if(FirebaseAuth.getInstance().getCurrentUser() != null){
            UserUtils.updateToken(this,s);
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Map<String,String> dataRcv = remoteMessage.getData();
        if(dataRcv != null){
            if(dataRcv.get(Common.NOTI_TITLE)!= null) {

                if(dataRcv.get(Common.NOTI_TITLE).equals(Common.REQUEST_DRIVER_DECLINE)){
                    EventBus.getDefault().postSticky(new DeclineRequestFromDriver());
                }

                else if(dataRcv.get(Common.NOTI_TITLE).equals(Common.REQUEST_DRIVER_DECLINE_AND_REMOVE_TRIP)){
                    EventBus.getDefault().postSticky(new DeclineRequestAndRemoveTripFromDriver());
                }

                else if(dataRcv.get(Common.NOTI_TITLE).equals(Common.REQUEST_DRIVER_ACCEPT)){
                   String tripKey = dataRcv.get(Common.TRIP_KEY);
                   EventBus.getDefault().postSticky(new DriverAcceptTripEvent(tripKey));
                }
                else if(dataRcv.get(Common.NOTI_TITLE).equals(Common.RIDER_COMPLETE_TRIP)){
                    String tripKey = dataRcv.get(Common.TRIP_KEY);
                    EventBus.getDefault().postSticky(new DriverCompleteTripEvent(tripKey));
                }
                else{
                Common.showNotification(this, new Random().nextInt(),
                        dataRcv.get(Common.NOTI_TITLE),
                        dataRcv.get(Common.NOTI_CONTENT),
                        null);
                }
            }
        }
    }
}
