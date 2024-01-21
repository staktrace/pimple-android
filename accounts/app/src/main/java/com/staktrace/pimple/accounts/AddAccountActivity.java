/*
 * (c) staktrace systems, 2011.
 * See http://staktrace.com/ for license details.
 */

package com.staktrace.pimple.accounts;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class AddAccountActivity extends AccountAuthenticatorActivity {
    private static final String TAG = "PimpleAddAccountActivity";
    static final String PARAM_USERNAME = "com.staktrace.pimple.accounts.username";
    static final String PARAM_TOKEN_TYPE = "com.staktrace.pimple.accounts.tokentype";

    private TextView _messageField;
    private EditText _usernameField;
    private EditText _passwordField;
    private String _tokenType;

    private Thread _authThread;

    @Override public void onCreate( Bundle icicle ) {
        super.onCreate( icicle );

        setContentView( R.layout.login_activity );
        _usernameField = (EditText)findViewById( R.id.username_edit );
        _passwordField = (EditText)findViewById( R.id.password_edit );
        _messageField = (TextView)findViewById( R.id.message );
        _tokenType = getIntent().getStringExtra( PARAM_TOKEN_TYPE );

        String username = getIntent().getStringExtra( PARAM_USERNAME );
        if (username != null) {
            _usernameField.setText( username, TextView.BufferType.NORMAL );
        }
    }

    @Override protected Dialog onCreateDialog( int id ) {
        if (id != 0) {
            return null;
        }
        final ProgressDialog dialog = new ProgressDialog( this );
        dialog.setMessage( getText( R.string.login_activity_authenticating ) );
        dialog.setIndeterminate( true );
        dialog.setCancelable( true );
        dialog.setOnCancelListener( new DialogInterface.OnCancelListener() {
            public void onCancel( DialogInterface dialog ) {
                if (_authThread != null) {
                    _authThread.interrupt();
                    finish();
                }
            }
        });
        return dialog;
    }

    // onclick handler

    public void handleLogin( View view ) {
        final String username = _usernameField.getText().toString();
        final String password = _passwordField.getText().toString();
        Log.d( TAG, "Handling login for username [" + username + "]" );
        final HttpAuthenticator authenticator = new HttpAuthenticator( username, password );
        showDialog( 0 );
        final Handler handler = new Handler();
        _authThread = new Thread() {
            public void run() {
                Log.d( TAG, "Starting http authenticator" );
                final boolean success = authenticator.authenticate();
                Log.d( TAG, "Completed http authentication with result " + (success ? "PASS" : "FAIL") );
                handler.post( new Runnable() {
                    public void run() {
                        dismissDialog( 0 );
                        if (success) {
                            handleLoginCompleted( username, password, authenticator.getToken() );
                        } else {
                            _messageField.setText( getText( authenticator.getErrorId() ) );
                        }
                    }
                } );
                _authThread = null;
            }
        };
        _authThread.start();
    }

    private void handleLoginCompleted( String username, String password, String token ) {
        Intent intent = new Intent();
        if (Pimple.TOKEN_TYPE_CONFIRM.equals( _tokenType )) {
            intent.putExtra( AccountManager.KEY_BOOLEAN_RESULT, true );
        } else {
            // currently don't save the password in the AccountManager since it's out of our control then
            if (getIntent().getStringExtra( PARAM_USERNAME ) == null) {
                AccountManager.get( this ).addAccountExplicitly( new Account( username, Pimple.ACCOUNT_TYPE ), null/*password*/, null );
            } else {
                //AccountManager.get( this ).setPassword( new Account( username, Pimple.ACCOUNT_TYPE ), password );
            }

            intent.putExtra( AccountManager.KEY_ACCOUNT_NAME, username );
            intent.putExtra( AccountManager.KEY_ACCOUNT_TYPE, Pimple.ACCOUNT_TYPE );
            if (Pimple.TOKEN_TYPE_COOKIE.equals( _tokenType )) {
                intent.putExtra( AccountManager.KEY_AUTHTOKEN, token );
                AccountManager.get( this ).setAuthToken( new Account( username, Pimple.ACCOUNT_TYPE ), Pimple.TOKEN_TYPE_COOKIE, token );
            }
        }
        setAccountAuthenticatorResult( intent.getExtras() );
        setResult( RESULT_OK, intent );
        finish();
    }

    // onclick handler

    public void handleCancel( View view ) {
        Intent intent = new Intent();
        if (Pimple.TOKEN_TYPE_COOKIE.equals( _tokenType )) {
            setAccountAuthenticatorResult( null );
        } else if (Pimple.TOKEN_TYPE_CONFIRM.equals( _tokenType )) {
            intent.putExtra( AccountManager.KEY_BOOLEAN_RESULT, false );
            setAccountAuthenticatorResult( intent.getExtras() );
        }
        setResult( RESULT_CANCELED, intent );
        finish();
    }
}
