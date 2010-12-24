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
import java.util.StringTokenizer;

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

    private String[] splitVcardInput( String vcardLine ) {
        String[] data = new String[3];
        int semicolon = vcardLine.indexOf( ';' );
        int colon = vcardLine.indexOf( ':' );
        if (colon < 0) {
            return null;
        }
        if (semicolon < 0 || semicolon > colon) {
            semicolon = colon;
        }
        data[0] = vcardLine.substring( 0, semicolon ).toUpperCase();
        data[2] = vcardLine.substring( colon + 1 );
        if (semicolon == colon) {
            return data;
        }
        StringTokenizer parameters = new StringTokenizer( vcardLine.substring( semicolon + 1, colon ), ";" );
        while (parameters.hasMoreTokens()) {
            String parameter = parameters.nextToken();
            if (parameter.toUpperCase().startsWith( "TYPE=" )) {
                data[1] = parameter.substring( 5 );
                break;
            }
        }
        return data;
    }

    private int[] updateContacts( BufferedReader source ) throws IOException, RemoteException, OperationApplicationException {
        int groupId = getGroupId();
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        int newCount = 0;
        int oldCount = 0;
        ArrayList<String[]> vcard = new ArrayList<String[]>();
        for (String s = source.readLine(); s != null; s = source.readLine()) {
            String[] data = splitVcardInput( s );
            if (data == null) {
                continue;
            }

            if (! (data[0].equals( "END" ) && data[2].equalsIgnoreCase( "VCARD" ))) {
                vcard.add( data );
                continue;
            }

            // reached the end of a card, process it now
            String pimpleIndex = null;
            for (String[] inputs : vcard) {
                if (inputs[0].equals( "X-PIMPLE-TOUCH-ID" )) {
                    pimpleIndex = inputs[2];
                    break;
                }
            }

            System.out.println( "Processing vcard for touch id " + pimpleIndex );

            // turn the pimple index into a contact index
            boolean backReference = false;
            int rawContactIndex = -1;
            if (pimpleIndex != null) {
                Cursor c = _context.getContentResolver().query( ContactsContract.RawContacts.CONTENT_URI,
                                                                new String[] { "_id" },
                                                                QUERY + " and " + ContactsContract.RawContacts.SOURCE_ID + "=?",
                                                                new String[] { PIMPLE_TYPE, _source, pimpleIndex },
                                                                null );
                if (c.moveToNext()) {
                    rawContactIndex = c.getInt( 0 );
                }
            }

            System.out.println( "Mapped to raw contact index [" + rawContactIndex + "]" );

            // if we don't have a contact index, create a contact and add it to the group
            // otherwise, un-delete the raw contact and add it back to the group
            if (rawContactIndex < 0) {
                rawContactIndex = ops.size();
                backReference = true;
                ops.add( ContentProviderOperation.newInsert( ContactsContract.RawContacts.CONTENT_URI )
                                                 .withValue( ContactsContract.RawContacts.ACCOUNT_TYPE, PIMPLE_TYPE )
                                                 .withValue( ContactsContract.RawContacts.ACCOUNT_NAME, _source )
                                                 .withValue( ContactsContract.RawContacts.SOURCE_ID, pimpleIndex )
                                                 .build() );
                ops.add( ContentProviderOperation.newInsert( ContactsContract.Data.CONTENT_URI )
                                                 .withValueBackReference( ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex )
                                                 .withValue( ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE )
                                                 .withValue( ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId )
                                                 .build() );
                newCount++;
            } else {
                ops.add( ContentProviderOperation.newUpdate( ContactsContract.RawContacts.CONTENT_URI )
                                                 .withSelection( "_id=?", new String[] { Integer.toString( rawContactIndex ) } )
                                                 .withValue( ContactsContract.RawContacts.DELETED, 0 )
                                                 .build() );
                ops.add( ContentProviderOperation.newInsert( ContactsContract.Data.CONTENT_URI )
                                                 .withValue( ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex )
                                                 .withValue( ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE )
                                                 .withValue( ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId )
                                                 .build() );
                oldCount++;
            }

            // now update the remaining data fields for the contact
            for (String[] inputs : vcard) {
                if (! (inputs[0].equals( "FN" ) || inputs[0].equals( "TEL" ) || inputs[0].equals( "EMAIL" ))) {
                    continue;
                }

                ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert( ContactsContract.Data.CONTENT_URI );
                if (backReference) {
                    builder = builder.withValueBackReference( ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex );
                } else {
                    builder = builder.withValue( ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex );
                }
                if (inputs[0].equals( "FN" )) {
                    ops.add( builder.withValue( ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE )
                                    .withValue( ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, inputs[2] )
                                    .build() );
                } else if (inputs[0].equals( "TEL" )) {
                    int typeInt = 0;
                    if (inputs[1].equalsIgnoreCase( "CELL" ) || inputs[1].equalsIgnoreCase( "MOBILE" )) {
                        typeInt = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
                    } else if (inputs[1].equalsIgnoreCase( "HOME" )) {
                        typeInt = ContactsContract.CommonDataKinds.Phone.TYPE_HOME;
                    } else if (inputs[1].equalsIgnoreCase( "WORK" )) {
                        typeInt = ContactsContract.CommonDataKinds.Phone.TYPE_WORK;
                    }
                    ops.add( builder.withValue( ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE )
                                    .withValue( ContactsContract.CommonDataKinds.Phone.NUMBER, "+" + inputs[2] )
                                    .withValue( ContactsContract.CommonDataKinds.Phone.TYPE, typeInt )
                                    .build() );
                } else if (inputs[0].equals( "EMAIL" )) {
                    ops.add( builder.withValue( ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE )
                                    .withValue( ContactsContract.CommonDataKinds.Email.DATA, inputs[2] )
                                    .withValue( ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM )
                                    .withValue( ContactsContract.CommonDataKinds.Email.LABEL, inputs[1] )
                                    .build() );
                }
            }

            vcard.clear();
        }
        _context.getContentResolver().applyBatch( ContactsContract.AUTHORITY, ops );
        return new int[] { newCount, oldCount };
    }

    private void deleteData() {
        _context.getContentResolver().delete( ContactsContract.Data.CONTENT_URI, QUERY, new String[] { PIMPLE_TYPE, _source } );
        _context.getContentResolver().delete( ContactsContract.RawContacts.CONTENT_URI, QUERY, new String[] { PIMPLE_TYPE, _source } );
    }

    private int clearDatalessContacts() {
        Uri adapterUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter( ContactsContract.CALLER_IS_SYNCADAPTER, null ).build();
        String query = QUERY + " and " + ContactsContract.RawContacts.DELETED + "=?";
        return _context.getContentResolver().delete( adapterUri, query, new String[] { PIMPLE_TYPE, _source, "1" } );
    }

    // View.OnClickListener implementation

    public void onClick( View v ) {
        try {
            InputStream in = fetchContacts();
            if (in == null) {
                throw new IOException( "Unable to obtain connection to pimple vcard file" );
            }
            try {
                deleteData();
                int[] counts = updateContacts( new BufferedReader( new InputStreamReader( in ) ) );
                int deleted = clearDatalessContacts();
                String msg = "Injected " + counts[0] + " new contacts, updated " + counts[1] + " existing contacts, and deleted " + deleted + " contacts";
                new AlertDialog.Builder( _context ).setTitle( "Pimple" ).setMessage( msg ).show();
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
