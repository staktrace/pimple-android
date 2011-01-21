/*
 * (c) staktrace systems, 2010-2011.
 * See http://staktrace.com/ for license details.
 */

package com.staktrace.pimple.accounts;

import android.accounts.*;
import android.content.Context;
import android.os.Bundle;

public class PimpleAccountAuthenticator extends AbstractAccountAuthenticator {
    public PimpleAccountAuthenticator( Context context ) {
        super( context );
    }

    // AbstractAccountAuthenticator implementation

    public Bundle addAccount( AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options ) {
        System.out.println( "In addAccount [" + response + "," + accountType + "," + authTokenType + "," + requiredFeatures + "," + options + "]" );
        // TODO - actually prompt the user for stuff
        Bundle b = new Bundle();
        b.putString( AccountManager.KEY_ACCOUNT_TYPE, accountType );
        b.putString( AccountManager.KEY_ACCOUNT_NAME, "staktrace.com" );
        return b;
    }

    public Bundle confirmCredentials( AccountAuthenticatorResponse response, Account account, Bundle options ) {
        System.out.println( "In confirmCredentials with [" + response + "," + account + "," + options + "]" );
        // TODO
        Bundle b = new Bundle();
        b.putBoolean( AccountManager.KEY_BOOLEAN_RESULT, true );
        return b;
    }

    public Bundle editProperties( AccountAuthenticatorResponse response, String accountType ) {
        System.out.println( "In editProperties with [" + response + "," + accountType + "]" );
        // TODO
        return new Bundle();
    }

    public Bundle getAuthToken( AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options ) {
        System.out.println( "In getAuthToken with [" + response + "," + account + "," + authTokenType + "," + options + "]" );
        // TODO
        Bundle b = new Bundle();
        b.putString( AccountManager.KEY_ACCOUNT_TYPE, account.type );
        b.putString( AccountManager.KEY_ACCOUNT_NAME, account.name );
        b.putString( AccountManager.KEY_AUTHTOKEN, "dummy" );
        return b;
    }

    public String getAuthTokenLabel( String authTokenType ) {
        System.out.println( "In getAuthTokenLabel with [" + authTokenType + "]" );
        // TODO
        return "dummy";
    }

    public Bundle hasFeatures( AccountAuthenticatorResponse response, Account account, String[] features ) {
        System.out.println( "In hasFeatures with [" + response + "," + account + "," + features + "]" );
        // TODO
        Bundle b = new Bundle();
        b.putBoolean( AccountManager.KEY_BOOLEAN_RESULT, true );
        return b;
    }

    public Bundle updateCredentials( AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options ) {
        System.out.println( "In updateCredentials with [" + response + "," + account + "," + authTokenType + "," + options + "]" );
        // TODO
        Bundle b = new Bundle();
        b.putString( AccountManager.KEY_ACCOUNT_TYPE, account.type );
        b.putString( AccountManager.KEY_ACCOUNT_NAME, account.name );
        return b;
    }
}
