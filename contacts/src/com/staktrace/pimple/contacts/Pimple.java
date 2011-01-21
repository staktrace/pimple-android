/*
 * (c) staktrace systems, 2010.
 * See http://staktrace.com/ for license details.
 */

package com.staktrace.pimple.contacts;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;

public class Pimple extends Activity {
    @Override public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.main );
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
        }
        ( (Button)findViewById( R.id.contactinjector ) ).setOnClickListener( new PimpleContactInjector( this ) );
    }
}
