/*
 * (c) staktrace systems, 2010-2011.
 * See http://staktrace.com/ for license details.
 */

package com.staktrace.pimple.accounts;

import android.accounts.*;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class PimpleAccountAuthenticator extends AbstractAccountAuthenticator {
    private static final String TAG = "PimpleAccountAuthenticator";

    public PimpleAccountAuthenticator( Context context ) {
        super( context );
    }

    // AbstractAccountAuthenticator implementation

    public Bundle addAccount( AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options ) {
        Log.i( TAG, "In addAccount [" + response + "," + accountType + "," + authTokenType + "," + requiredFeatures + "," + options + "]" );
        Bundle b = new Bundle();
        Intent intent = new Intent();
        intent.setClassName( "com.staktrace.pimple.accounts", "com.staktrace.pimple.accounts.AddAccountActivity" );
        intent.putExtra( AccountManager.KEY_ACCOUNT_MANAGER_RESPONSE, response );
        b.putParcelable( AccountManager.KEY_INTENT, intent );
        return b;
    }

    public Bundle confirmCredentials( AccountAuthenticatorResponse response, Account account, Bundle options ) {
        Log.i( TAG, "In confirmCredentials with [" + response + "," + account + "," + options + "]" );
        // TODO
        Bundle b = new Bundle();
        b.putBoolean( AccountManager.KEY_BOOLEAN_RESULT, true );
        return b;
    }

    public Bundle editProperties( AccountAuthenticatorResponse response, String accountType ) {
        throw new UnsupportedOperationException();
    }

    public Bundle getAuthToken( AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options ) {
        Log.i( TAG, "In getAuthToken with [" + response + "," + account + "," + authTokenType + "," + options + "]" );
        // TODO
        Bundle b = new Bundle();
        return b;
    }

    public String getAuthTokenLabel( String authTokenType ) {
        return "pimple";
    }

    public Bundle hasFeatures( AccountAuthenticatorResponse response, Account account, String[] features ) {
        Bundle b = new Bundle();
        b.putBoolean( AccountManager.KEY_BOOLEAN_RESULT, false );
        return b;
    }

    public Bundle updateCredentials( AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options ) {
        Log.i( TAG, "In updateCredentials with [" + response + "," + account + "," + authTokenType + "," + options + "]" );
        // TODO
        Bundle b = new Bundle();
        b.putString( AccountManager.KEY_ACCOUNT_TYPE, account.type );
        b.putString( AccountManager.KEY_ACCOUNT_NAME, account.name );
        return b;
    }
}
