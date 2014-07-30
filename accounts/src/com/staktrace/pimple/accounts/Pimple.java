/*
 * (c) staktrace systems, 2014.
 * See http://staktrace.com/ for license details.
 */

package com.staktrace.pimple.accounts;

import android.accounts.AccountManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class Pimple extends Service {
    private static final String TAG = "Pimple";

    public static final String ACCOUNT_TYPE = "com.staktrace.pimple";
    public static final String TOKEN_TYPE_COOKIE = "cookie";
    public static final String TOKEN_TYPE_CONFIRM = "confirm";
    static final String HTTP_COOKIE_HEADER = "x-pimple-cookie";

    private final PimpleAccountAuthenticator _authenticator;
    private final PimpleSyncAdapter _syncAdapter;

    public Pimple() {
        _authenticator = new PimpleAccountAuthenticator( this );
        _syncAdapter = new PimpleSyncAdapter( this );
    }

    @Override public IBinder onBind( Intent intent ) {
        Log.i( TAG, "System requesting binder for intent [" + intent.getAction() + "]" );
        IBinder binder = null;
        if (AccountManager.ACTION_AUTHENTICATOR_INTENT.equals( intent.getAction() )) {
            binder = _authenticator.getIBinder();
        } else if ("android.content.SyncAdapter".equals( intent.getAction() )) {
            binder = _syncAdapter.getSyncAdapterBinder();
        }
        Log.i( TAG, "Returning binder " + binder );
        return binder;
    }
}
