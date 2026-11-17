package com.example.callblocker;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;

/**
 * Android 10: once the app holds the "call screening" role, the system asks us about every
 * incoming call before it rings, and we can reject it silently.
 */
@TargetApi(Build.VERSION_CODES.N)
public class ScreeningService extends CallScreeningService {

    @Override
    public void onScreenCall(Call.Details details) {
        CallResponse.Builder response = new CallResponse.Builder();

        boolean incoming = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            incoming = details.getCallDirection() == Call.Details.DIRECTION_INCOMING;
        }

        Uri handle = details.getHandle();
        String number = handle != null ? handle.getSchemeSpecificPart() : null;

        if (incoming && number != null && RuleStore.shouldBlock(this, number)) {
            response.setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true);
            if (RuleStore.claim(number)) {
                RuleStore.addLog(this, number);
            }
        }

        respondToCall(details, response.build());
    }
}
