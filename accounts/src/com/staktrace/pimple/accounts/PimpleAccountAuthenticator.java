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
    private final Context _context;

    public PimpleAccountAuthenticator( Context context ) {
        super( context );
        _context = context;
    }

    private Intent activityIntent( AccountAuthenticatorResponse response, String authTokenType ) {
        Intent intent = new Intent();
        intent.setClassName( "com.staktrace.pimple.accounts", "com.staktrace.pimple.accounts.AddAccountActivity" );
        intent.putExtra( AccountManager.KEY_ACCOUNT_MANAGER_RESPONSE, response );
        intent.putExtra( AddAccountActivity.PARAM_TOKEN_TYPE, authTokenType );
        return intent;
    }

    // AbstractAccountAuthenticator implementation

    public Bundle addAccount( AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options ) {
        Log.i( TAG, "In addAccount [" + response + "," + accountType + "," + authTokenType + "," + requiredFeatures + "," + options + "]" );
        Bundle b = new Bundle();
        b.putParcelable( AccountManager.KEY_INTENT, activityIntent( response, authTokenType ) );
        return b;
    }

    public Bundle confirmCredentials( AccountAuthenticatorResponse response, Account account, Bundle options ) {
        Log.i( TAG, "In confirmCredentials with [" + response + "," + account + "," + options + "]" );

        Bundle result = new Bundle();
        Intent intent = activityIntent( response, Pimple.TOKEN_TYPE_CONFIRM );
        intent.putExtra( AddAccountActivity.PARAM_USERNAME, account.name );
        result.putParcelable( AccountManager.KEY_INTENT, intent );
        return result;
    }

    public Bundle editProperties( AccountAuthenticatorResponse response, String accountType ) {
        throw new UnsupportedOperationException();
    }

    public Bundle getAuthToken( AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options ) {
        Log.i( TAG, "In getAuthToken with [" + response + "," + account + "," + authTokenType + "," + options + "]" );

        String password = AccountManager.get( _context ).getPassword( account );
        if (password != null) {
            HttpAuthenticator authenticator = new HttpAuthenticator( account.name, password );
            if (authenticator.authenticate()) {
                Bundle result = new Bundle();
                result.putString( AccountManager.KEY_ACCOUNT_NAME, account.name );
                result.putString( AccountManager.KEY_ACCOUNT_TYPE, Pimple.ACCOUNT_TYPE );
                result.putString( AccountManager.KEY_AUTHTOKEN, authenticator.getToken() );
                return result;
            }
        }

        return updateCredentials( response, account, authTokenType, options );
    }

    public String getAuthTokenLabel( String authTokenType ) {
        if (Pimple.TOKEN_TYPE_COOKIE.equals( authTokenType )) {
            return _context.getString( R.string.label_cookie );
        } else {
            return _context.getString( R.string.label_unknown );
        }
    }

    public Bundle hasFeatures( AccountAuthenticatorResponse response, Account account, String[] features ) {
        Bundle b = new Bundle();
        b.putBoolean( AccountManager.KEY_BOOLEAN_RESULT, false );
        return b;
    }

    public Bundle updateCredentials( AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options ) {
        Log.i( TAG, "In updateCredentials with [" + response + "," + account + "," + authTokenType + "," + options + "]" );

        Bundle result = new Bundle();
        Intent intent = activityIntent( response, authTokenType );
        intent.putExtra( AddAccountActivity.PARAM_USERNAME, account.name );
        result.putParcelable( AccountManager.KEY_INTENT, intent );
        return result;
    }
}
