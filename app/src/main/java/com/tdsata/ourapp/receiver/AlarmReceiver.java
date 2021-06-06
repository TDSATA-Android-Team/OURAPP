package com.tdsata.ourapp.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.tdsata.ourapp.service.PullMessageService;
import com.tdsata.ourapp.util.FixedValue;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (FixedValue.MY_ACTION_NAME.equals(intent.getAction())) {
            Intent startService = new Intent(context, PullMessageService.class);
            context.startService(startService);
        }
    }
}