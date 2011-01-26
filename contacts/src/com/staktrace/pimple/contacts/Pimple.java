/*
 * (c) staktrace systems, 2010.
 * See http://staktrace.com/ for license details.
 */

package com.staktrace.pimple.contacts;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class Pimple extends Activity {
    @Override public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.main );
    }

    // onclick handler

    public void injectContacts( View view ) {
        new PimpleContactInjector( this ).inject();
    }
}
