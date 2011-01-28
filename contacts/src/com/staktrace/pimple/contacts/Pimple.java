/*
 * (c) staktrace systems, 2010.
 * See http://staktrace.com/ for license details.
 */

package com.staktrace.pimple.contacts;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class Pimple extends Activity {
    @Override public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.main );
    }

    @Override protected Dialog onCreateDialog( int id ) {
        if (id == 0) {
            ProgressDialog dialog = new ProgressDialog( this );
            dialog.setIndeterminate( true );
            dialog.setMessage( getText( R.string.syncing_contacts ) );
            return dialog;
        }
        return null;
    }

    // onclick handler

    public void injectContacts( View view ) {
        showDialog( 0 );
        new PimpleContactInjector( Pimple.this ).start();
    }
}
