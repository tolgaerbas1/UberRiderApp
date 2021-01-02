package com.tolga.uberriderapp.Remote;

import com.tolga.uberriderapp.Model.FCMResponse;
import com.tolga.uberriderapp.Model.FCMSendData;

import io.reactivex.Observable;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
public interface IFCMService {

    @Headers({
            "Content-Type:application/json",
            "Authorization:key=AAAAQqElHJU:APA91bHIU5deQ-mY77HFzyFfb7P6blm5cr8IDzxtHaQz9BNmHg0c_EaMhh8NrXVQOdCn4wmRWH2w5CeeauPAouBn4UFi61MvGl4fw1xZ0D6S5f3AUTxAJqy8oe2D-9-lpDShx6JU5ld0"
    })
    @POST("fcm/send")
    Observable<FCMResponse> sendNotification(@Body FCMSendData body);
}
