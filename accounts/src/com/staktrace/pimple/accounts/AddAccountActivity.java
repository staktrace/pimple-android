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
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class AddAccountActivity extends AccountAuthenticatorActivity {
    private static final String TAG = "AddAccountActivity";
    static final String PARAM_USERNAME = "com.staktrace.pimple.accounts.username";

    private TextView _messageField;
    private EditText _usernameField;
    private EditText _passwordField;

    private Thread _authThread;

    @Override public void onCreate( Bundle icicle ) {
        super.onCreate( icicle );

        setContentView( R.layout.login_activity );
        _usernameField = (EditText)findViewById( R.id.username_edit );
        _passwordField = (EditText)findViewById( R.id.password_edit );
        _messageField = (TextView)findViewById( R.id.message );

        String username = getIntent().getStringExtra( PARAM_USERNAME );
        if (username != null) {
            _usernameField.setText( username, TextView.BufferType.NORMAL );
        }
    }

    @Override protected Dialog onCreateDialog( int id ) {
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
        if (TextUtils.isEmpty( username )) {
            _messageField.setText( getText( R.string.error_empty_username ) );
            return;
        }

        showDialog( 0 );
        final Handler handler = new Handler();
        _authThread = new Thread() {
            public void run() {
                final boolean success = true;
                handler.post( new Runnable() {
                    public void run() {
                        handleLoginCompleted( username, password, success );
                    }
                } );
                _authThread = null;
            }
        };
        _authThread.start();
    }

    private void handleLoginCompleted( String username, String password, boolean success ) {
        dismissDialog( 0 );
        if (success) {
            if (getIntent().getStringExtra( PARAM_USERNAME ) == null) {
                AccountManager.get( this ).addAccountExplicitly( new Account( username, Pimple.ACCOUNT_TYPE ), password, null );
            } else {
                AccountManager.get( this ).setPassword( new Account( username, Pimple.ACCOUNT_TYPE ), password );
            }

            Intent intent = new Intent();
            intent.putExtra( AccountManager.KEY_ACCOUNT_NAME, username );
            intent.putExtra( AccountManager.KEY_ACCOUNT_TYPE, Pimple.ACCOUNT_TYPE );
            setAccountAuthenticatorResult( intent.getExtras() );
            setResult( RESULT_OK, intent );
            Log.i( TAG, "Authentication succeeded, returning..." );
            finish();
        } else {
            _messageField.setText( getText( R.string.error_failed_auth ) );
        }
    }
}
