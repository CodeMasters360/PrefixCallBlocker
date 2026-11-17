package com.example.callblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

/** Android 6-9 path (also a fallback on Android 10): watches for an incoming call and hangs up. */
public class CallReceiver extends BroadcastReceiver {

    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context context, Intent intent) {
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (!TelephonyManager.EXTRA_STATE_RINGING.equals(state)) return;

        // The number can be missing in the first broadcast; a second one usually carries it.
        String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
        if (number == null || number.isEmpty()) return;

        if (!RuleStore.shouldBlock(context, number)) return;
        if (!RuleStore.claim(number)) return;

        boolean ended = CallEnder.endCall(context);
        RuleStore.addLog(context, ended ? number : number + " (could not hang up - check permissions)");
    }
}
