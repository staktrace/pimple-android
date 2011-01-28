/*
 * (c) staktrace systems, 2011.
 * See http://staktrace.com/ for license details.
 */

package com.staktrace.pimple.accounts;

import android.accounts.AccountManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class Pimple extends Service {
    public static final String ACCOUNT_TYPE = "com.staktrace.pimple";
    public static final String TOKEN_TYPE_COOKIE = "cookie";
    public static final String TOKEN_TYPE_CONFIRM = "confirm";
    static final String HTTP_COOKIE_HEADER = "x-pimple-cookie";

    private final PimpleAccountAuthenticator _authenticator;

    public Pimple() {
        _authenticator = new PimpleAccountAuthenticator( this );
    }

    @Override public IBinder onBind( Intent intent ) {
        if (AccountManager.ACTION_AUTHENTICATOR_INTENT.equals( intent.getAction() )) {
            return _authenticator.getIBinder();
        }
        return null;
    }
}
