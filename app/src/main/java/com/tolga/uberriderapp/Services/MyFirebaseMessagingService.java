package com.tolga.uberriderapp.Services;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.tolga.uberriderapp.Common;
import com.tolga.uberriderapp.Utils.UserUtils;

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
            Common.showNotification(this, new Random().nextInt(),
                    dataRcv.get(Common.NOTI_TITLE),
                    dataRcv.get(Common.NOTI_CONTENT),
                    null);
        }
    }
}
