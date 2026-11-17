package com.example.callblocker;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;

import java.lang.reflect.Method;

/** Ends the current ringing call on Android 6 - 10. */
final class CallEnder {

    private CallEnder() {}

    @SuppressWarnings("deprecation")
    static boolean endCall(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Android 9+ : official API, needs ANSWER_PHONE_CALLS
                if (ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS)
                        != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
                TelecomManager tm = (TelecomManager) ctx.getSystemService(Context.TELECOM_SERVICE);
                return tm != null && tm.endCall();
            } else {
                // Android 6 - 8 : hidden ITelephony.endCall() through reflection (needs CALL_PHONE)
                TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
                Method getITelephony = TelephonyManager.class.getDeclaredMethod("getITelephony");
                getITelephony.setAccessible(true);
                Object telephony = getITelephony.invoke(tm);
                Method endCall = Class.forName("com.android.internal.telephony.ITelephony")
                        .getMethod("endCall");
                Object result = endCall.invoke(telephony);
                return result instanceof Boolean && (Boolean) result;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
