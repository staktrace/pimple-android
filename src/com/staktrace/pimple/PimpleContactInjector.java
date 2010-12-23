/*
 * (c) staktrace systems, 2010.
 * See http://staktrace.com/ for license details.
 */

package com.staktrace.pimple;

import android.app.AlertDialog;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.view.View;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

class PimpleContactInjector implements View.OnClickListener {
    private static final String QUERY = "account_type=? AND account_name=?";
    private static final String PIMPLE_TYPE = "com.staktrace.pimple";

    private final Context _context;
    private String _source;

    PimpleContactInjector( Context context ) {
        _context = context;
    }

    private InputStream fetchContacts() throws IOException {
        BufferedReader br = new BufferedReader( new InputStreamReader( _context.getResources().openRawResource( R.raw.pimple ) ) );
        try {
            _source = br.readLine();
            URL url = new URL( "https://" + _source + "/touch/vcard.php?tag=vcard" );
            URLConnection conn = url.openConnection();
            for (String s = br.readLine(); s != null; s = br.readLine()) {
                int colon = s.indexOf( ':' );
                if (colon < 0) {
                    continue;
                }
                conn.addRequestProperty( s.substring( 0, colon ), s.substring( colon + 1 ) );
            }
            System.out.println( "Received document of type " + conn.getContentType() + " from pimple" );
            if (conn.getContentType().equals( "text/x-vcard" )) {
                return conn.getInputStream();
            } else {
                return null;
            }
        } finally {
            br.close();
        }
    }

    private void clearContacts() {
        Uri adapterUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter( ContactsContract.CALLER_IS_SYNCADAPTER, null ).build();
        int deleted = _context.getContentResolver().delete( adapterUri, QUERY, new String[] { PIMPLE_TYPE, _source } );
        System.out.println( "Deleted " + deleted + " contacts previously created with PimpleContactInjector" );
    }

    private int getGroupId() {
        int groupId = -1;
        Cursor c = _context.getContentResolver().query( ContactsContract.Groups.CONTENT_URI, new String[] { "_id" }, QUERY, new String[] { PIMPLE_TYPE, _source }, null );
        if (c.moveToNext()) {
            groupId = c.getInt( 0 );
        }
        c.close();
        if (groupId < 0) {
            // 19-Dec-2010: this code hasn't been exercised yet (should work though, did the equivalent manually via OneTimeAction)
            ContentValues values = new ContentValues();
            values.put( ContactsContract.Groups.ACCOUNT_TYPE, PIMPLE_TYPE );
            values.put( ContactsContract.Groups.ACCOUNT_NAME, _source );
            values.put( ContactsContract.Groups.GROUP_VISIBLE, 1 );
            values.put( ContactsContract.Groups.TITLE, "Pimple from " + _source );
            values.put( ContactsContract.Groups.NOTES, "Contacts from " + _source + " via Pimple" );
            Uri inserted = _context.getContentResolver().insert( ContactsContract.Groups.CONTENT_URI, values );
            groupId = Integer.parseInt( inserted.getLastPathSegment() );
        }
        return groupId;
    }

    private int injectContacts( BufferedReader source ) throws IOException, RemoteException, OperationApplicationException {
        int groupId = getGroupId();
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        int count = 0;
        int rawContactIndex = -1;
        for (String s = source.readLine(); s != null; s = source.readLine()) {
            if (s.startsWith( "FN:" ) || s.startsWith( "TEL;type" )) {
                if (rawContactIndex < 0) {
                    rawContactIndex = ops.size();
                    ops.add( ContentProviderOperation.newInsert( ContactsContract.RawContacts.CONTENT_URI )
                                                     .withValue( ContactsContract.RawContacts.ACCOUNT_TYPE, PIMPLE_TYPE )
                                                     .withValue( ContactsContract.RawContacts.ACCOUNT_NAME, _source )
                                                     .build() );
                    ops.add( ContentProviderOperation.newInsert( ContactsContract.Data.CONTENT_URI )
                                                     .withValueBackReference( ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex )
                                                     .withValue( ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE )
                                                     .withValue( ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId )
                                                     .build() );
                    count++;
                }
                if (s.startsWith( "FN:" )) {
                    ops.add( ContentProviderOperation.newInsert( ContactsContract.Data.CONTENT_URI )
                                                     .withValueBackReference( ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex )
                                                     .withValue( ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE )
                                                     .withValue( ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, s.substring( 3 ).trim() )
                                                     .build() );
                } else if (s.startsWith( "TEL;type" )) {
                    int typeInt = 0;
                    String type = s.substring( s.indexOf( '=' ) + 1, s.indexOf( ':' ) );
                    if (type.equals( "CELL" )) {
                        typeInt = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
                    } else if (type.equals( "HOME" )) {
                        typeInt = ContactsContract.CommonDataKinds.Phone.TYPE_HOME;
                    } else if (type.equals( "WORK" )) {
                        typeInt = ContactsContract.CommonDataKinds.Phone.TYPE_WORK;
                    }
                    String number = "+" + s.substring( s.indexOf( ':' ) + 1 );
                    ops.add( ContentProviderOperation.newInsert( ContactsContract.Data.CONTENT_URI )
                                                     .withValueBackReference( ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex )
                                                     .withValue( ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE )
                                                     .withValue( ContactsContract.CommonDataKinds.Phone.NUMBER, number )
                                                     .withValue( ContactsContract.CommonDataKinds.Phone.TYPE, typeInt )
                                                     .build() );
                }
            } else if (s.equals( "END:VCARD" )) {
                rawContactIndex = -1;
            }
        }
        _context.getContentResolver().applyBatch( ContactsContract.AUTHORITY, ops );
        return count;
    }

    // View.OnClickListener implementation

    public void onClick( View v ) {
        try {
            InputStream in = fetchContacts();
            if (in == null) {
                throw new IOException( "Unable to obtain connection to pimple vcard file" );
            }
            try {
                clearContacts();
                int count = injectContacts( new BufferedReader( new InputStreamReader( in ) ) );
                new AlertDialog.Builder( _context ).setTitle( "Pimple" ).setMessage( "Injected " + count + " contacts" ).show();
            } catch (Exception e) {
                e.printStackTrace();
                new AlertDialog.Builder( _context ).setTitle( "Pimple" ).setMessage( e.toString() ).show();
            } finally {
                in.close();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            new AlertDialog.Builder( _context ).setTitle( "Pimple" ).setMessage( ioe.toString() ).show();
        }
    }
}
