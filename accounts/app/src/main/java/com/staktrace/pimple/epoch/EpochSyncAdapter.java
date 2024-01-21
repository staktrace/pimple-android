/*
 * (c) staktrace systems, 2014.
 * See http://staktrace.com/ for license details.
 */

package com.staktrace.pimple.epoch;

import android.accounts.Account;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.CalendarContract;
import android.util.Log;

public class EpochSyncAdapter extends Service {
    private static final String TAG = "PimpleEpochSyncAdapter";

    private final AbstractThreadedSyncAdapter _syncAdapter;

    public EpochSyncAdapter() {
        _syncAdapter = new AbstractThreadedSyncAdapter( this, true ) {
            @Override public void onPerformSync( Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult ) {
                Log.i( TAG, "In onPerformSync [" + account + "," + extras + "," + authority + "," + provider + "," + syncResult + "]" );
                if (CalendarContract.AUTHORITY.equals( authority )) {
                    new CalendarCreator( getContext(), account ).run();
                }
            }
        };
    }

    @Override public IBinder onBind( Intent intent ) {
        Log.i( TAG, "System requesting binder for intent [" + intent.getAction() + "]" );
        IBinder binder = null;
        if ("android.content.SyncAdapter".equals( intent.getAction() )) {
            binder = _syncAdapter.getSyncAdapterBinder();
        }
        Log.i( TAG, "Returning binder " + binder );
        return binder;
    }
}
