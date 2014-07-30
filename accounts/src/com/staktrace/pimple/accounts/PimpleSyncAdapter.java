/*
 * (c) staktrace systems, 2014.
 * See http://staktrace.com/ for license details.
 */

package com.staktrace.pimple.accounts;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;

public class PimpleSyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String TAG = "PimpleSyncAdapter";

    PimpleSyncAdapter( Context context ) {
        super( context, true );
    }

    // AbstractThreadedSyncAdapter implementation

    @Override public void onPerformSync( Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult ) {
        Log.i( TAG, "In onPerformSync [" + account + "," + extras + "," + authority + "," + provider + "," + syncResult + "]" );
        if (ContactsContract.AUTHORITY.equals( authority )) {
        } // else if CalendarContract.AUTHORITY...
    }
}
