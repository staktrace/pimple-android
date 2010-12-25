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
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

class PimpleContactInjector implements View.OnClickListener {
    private static final String QUERY = ContactsContract.RawContacts.ACCOUNT_TYPE + "=? AND " + ContactsContract.RawContacts.ACCOUNT_NAME + "=?";
    private static final String PIMPLE_TYPE = "com.staktrace.pimple";
    private static final String MIME_VCARD = "text/x-vcard";

    private static final String NAME_ID = "X-PIMPLE-ID";
    private static final int NAME_FULLNAME = 0;
    private static final int NAME_TELEPHONE = 1;
    private static final int NAME_EMAIL = 2;
    private static final String[] NAME_STRINGS = new String[] {
        "FN",
        "TEL",
        "EMAIL",
    };
    private static final String[] NAME_MIMETYPES = new String[] {
        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
    };

    private static final String PARAM_TYPE = "TYPE=";
    private static final String PARAM_ID = "X-PIMPLE-ID=";

    private static final String TYPE_HOME = "HOME";
    private static final String TYPE_WORK = "WORK";
    private static final String TYPE_CELL = "CELL";

    private static final int COUNT_CONTACTS_ADDED = 0;
    private static final int COUNT_CONTACTS_UPDATED = 1;
    private static final int COUNT_CONTACTS_DELETED = 2;
    private static final int COUNT_DATA_ADDED = 3;
    private static final int COUNT_DATA_UPDATED = 4;
    private static final int COUNT_DATA_DELETED = 5;
    private static final int NUM_COUNTS = 6;

    private static final int FIELD_NAME = 0;
    private static final int FIELD_TYPE = 1;
    private static final int FIELD_ID = 2;
    private static final int FIELD_VALUE = 3;
    private static final int NUM_FIELDS = 4;

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
            if (conn.getContentType().equalsIgnoreCase( MIME_VCARD )) {
                return conn.getInputStream();
            } else {
                return null;
            }
        } finally {
            br.close();
        }
    }

    private int doInsert( Uri table, ContentValues values ) {
        Uri inserted = _context.getContentResolver().insert( table, values );
        return Integer.parseInt( inserted.getLastPathSegment() );
    }

    private int getGroupId() {
        int groupId = -1;
        Cursor c = _context.getContentResolver().query( ContactsContract.Groups.CONTENT_URI,
                                                        new String[] { ContactsContract.Groups._ID },
                                                        QUERY,
                                                        new String[] { PIMPLE_TYPE, _source },
                                                        null );
        try {
            if (c.moveToNext()) {
                groupId = c.getInt( 0 );
            }
        } finally {
            c.close();
        }
        if (groupId < 0) {
            // 19-Dec-2010: this code hasn't been exercised yet (should work though, did the equivalent manually via OneTimeAction)
            ContentValues values = new ContentValues();
            values.put( ContactsContract.Groups.ACCOUNT_TYPE, PIMPLE_TYPE );
            values.put( ContactsContract.Groups.ACCOUNT_NAME, _source );
            values.put( ContactsContract.Groups.GROUP_VISIBLE, 1 );
            values.put( ContactsContract.Groups.TITLE, "Pimple from " + _source );
            values.put( ContactsContract.Groups.NOTES, "Contacts from " + _source + " via Pimple" );
            groupId = doInsert( ContactsContract.Groups.CONTENT_URI, values );
        }
        return groupId;
    }

    private String[] splitVcardInput( String vcardLine ) {
        String[] data = new String[ NUM_FIELDS ];
        int semicolon = vcardLine.indexOf( ';' );
        int colon = vcardLine.indexOf( ':' );
        if (colon < 0) {
            return null;
        }
        if (semicolon < 0 || semicolon > colon) {
            semicolon = colon;
        }
        data[ FIELD_NAME ] = vcardLine.substring( 0, semicolon ).toUpperCase();
        data[ FIELD_VALUE ] = vcardLine.substring( colon + 1 );
        if (semicolon == colon) {
            return data;
        }
        StringTokenizer parameters = new StringTokenizer( vcardLine.substring( semicolon + 1, colon ), ";" );
        while (parameters.hasMoreTokens()) {
            String parameter = parameters.nextToken();
            if (parameter.toUpperCase().startsWith( PARAM_TYPE )) {
                data[ FIELD_TYPE ] = parameter.substring( PARAM_TYPE.length() );
            } else if (parameter.toUpperCase().startsWith( PARAM_ID )) {
                data[ FIELD_ID ] = parameter.substring( PARAM_ID.length() );
            }
        }
        return data;
    }

    private String vcardId( List<String[]> vcard ) {
        for (String[] fields : vcard) {
            if (fields[ FIELD_NAME ].equals( NAME_ID )) {
                return fields[ FIELD_VALUE ];
            }
        }
        return null;
    }

    private int getRawContactId( String pimpleId, int groupId, ArrayList<ContentProviderOperation> ops, int[] counts ) {
        int rawContactId = -1;
        if (pimpleId != null) {
            Cursor c = _context.getContentResolver().query( ContactsContract.RawContacts.CONTENT_URI,
                                                            new String[] { ContactsContract.RawContacts._ID },
                                                            QUERY + " AND " + ContactsContract.RawContacts.SOURCE_ID + "=?",
                                                            new String[] { PIMPLE_TYPE, _source, pimpleId },
                                                            null );
            try {
                if (c.moveToNext()) {
                    rawContactId = c.getInt( 0 );
                }
            } finally {
                c.close();
            }
        }

        // if we don't have a contact index, create a contact and add it to the group
        if (rawContactId < 0) {
            ContentValues values = new ContentValues();
            values.put( ContactsContract.RawContacts.ACCOUNT_TYPE, PIMPLE_TYPE );
            values.put( ContactsContract.RawContacts.ACCOUNT_NAME, _source );
            values.put( ContactsContract.RawContacts.SOURCE_ID, pimpleId );
            rawContactId = doInsert( ContactsContract.RawContacts.CONTENT_URI, values );

            ops.add( ContentProviderOperation.newInsert( ContactsContract.Data.CONTENT_URI )
                                             .withValue( ContactsContract.Data.RAW_CONTACT_ID, rawContactId )
                                             .withValue( ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE )
                                             .withValue( ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId )
                                             .build() );
            counts[ COUNT_CONTACTS_ADDED ]++;
        } else {
            counts[ COUNT_CONTACTS_UPDATED ]++;
        }

        return rawContactId;
    }

    private int nameInt( String fieldName ) {
        for (int i = NAME_STRINGS.length - 1; i >= 0; i--) {
            if (NAME_STRINGS[i].equals( fieldName )) {
                return i;
            }
        }
        return -1;
    }

    private void removeDeadItems( List<Integer> liveRawContactIds, List<Integer> deadDataIds, int[] counts ) {
        Uri adapterUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter( ContactsContract.CALLER_IS_SYNCADAPTER, null ).build();
        StringBuffer query = new StringBuffer( QUERY ).append( " AND " )
                                                      .append( ContactsContract.RawContacts._ID )
                                                      .append( " NOT IN (-1" );  // -1 since some SQL engines require at least one item
        for (int id : liveRawContactIds) {
            query.append( ',' ).append( id );
        }
        query.append( ')' );
        counts[ COUNT_CONTACTS_DELETED ] += _context.getContentResolver().delete( adapterUri, query.toString(), new String[] { PIMPLE_TYPE, _source } );

        query.setLength( 0 );
        query.append( ContactsContract.Data._ID ).append( " IN (-1" );
        for (int id : deadDataIds) {
            query.append( ',' ).append( id );
        }
        query.append( ')' );
        counts[ COUNT_DATA_DELETED ] += _context.getContentResolver().delete( ContactsContract.Data.CONTENT_URI, query.toString(), null );
    }

    private int[] updateContacts( BufferedReader source, int groupId ) throws IOException, RemoteException, OperationApplicationException {
        List<Integer> liveRawContactIds = new ArrayList<Integer>();
        List<Integer> deadDataIds = new ArrayList<Integer>();

        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        int[] counts = new int[ NUM_COUNTS ];

        List<String[]> vcard = new ArrayList<String[]>();
        for (String s = source.readLine(); s != null; s = source.readLine()) {
            // TODO: handle unfolding as per RFC 2425
            String[] inputFields = splitVcardInput( s );
            if (inputFields == null) {
                continue;
            }

            if (! (inputFields[ FIELD_NAME ].equals( "END" ) && inputFields[ FIELD_VALUE ].equalsIgnoreCase( "VCARD" ))) {
                vcard.add( inputFields );
                continue;
            }

            // reached the end of a card, process it now

            String pimpleId = vcardId( vcard );
            if (pimpleId == null) {
                vcard.clear();
                continue;
            }

            System.out.println( "Processing vcard for pimple id " + pimpleId );

            int rawContactId = getRawContactId( pimpleId, groupId, ops, counts );
            liveRawContactIds.add( rawContactId );

            System.out.println( "Mapped to raw contact id [" + rawContactId + "]" );

            Cursor c = _context.getContentResolver().query( ContactsContract.Data.CONTENT_URI,
                                                            new String[] { ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.SYNC1 },
                                                            ContactsContract.Data.RAW_CONTACT_ID + "=?",
                                                            new String[] { Integer.toString( rawContactId ) },
                                                            null );
            try {
                Set<Integer> liveDataIds = new TreeSet<Integer>();
                for (String[] fields : vcard) {
                    int name = nameInt( fields[ FIELD_NAME ] );
                    if (name < 0) {
                        continue;
                    }

                    if (fields[ FIELD_ID ] == null) {
                        continue;
                    }

                    int dataId = -1;
                    c.moveToFirst();
                    while (c.moveToNext()) {
                        if (fields[ FIELD_ID ].equals( c.getString( 2 ) ) && NAME_MIMETYPES[ name ].equals( c.getString( 1 ))) {
                            dataId = c.getInt( 0 );
                            break;
                        }
                    }

                    ContentProviderOperation.Builder builder;
                    if (dataId < 0) {
                        builder = ContentProviderOperation.newInsert( ContactsContract.Data.CONTENT_URI )
                                                          .withValue( ContactsContract.Data.RAW_CONTACT_ID, rawContactId )
                                                          .withValue( ContactsContract.Data.SYNC1, fields[ FIELD_ID ] )
                                                          .withValue( ContactsContract.Data.MIMETYPE, NAME_MIMETYPES[ name ] );
                        counts[ COUNT_DATA_ADDED ]++;
                    } else {
                        liveDataIds.add( dataId );
                        builder = ContentProviderOperation.newUpdate( ContactsContract.Data.CONTENT_URI )
                                                          .withSelection( ContactsContract.Data._ID + "=?", new String[] { Integer.toString( dataId ) } );
                        counts[ COUNT_DATA_UPDATED ]++;
                    }

                    switch (name) {
                        case NAME_FULLNAME:
                            builder = builder.withValue( ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, fields[ FIELD_VALUE ] );
                            break;
                        case NAME_TELEPHONE:
                            int typeInt = 0;
                            if (TYPE_CELL.equalsIgnoreCase( fields[ FIELD_TYPE ] )) {
                                typeInt = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
                            } else if (TYPE_HOME.equalsIgnoreCase( fields[ FIELD_TYPE ] )) {
                                typeInt = ContactsContract.CommonDataKinds.Phone.TYPE_HOME;
                            } else if (TYPE_WORK.equalsIgnoreCase( fields[ FIELD_TYPE ] )) {
                                typeInt = ContactsContract.CommonDataKinds.Phone.TYPE_WORK;
                            }
                            builder = builder.withValue( ContactsContract.CommonDataKinds.Phone.NUMBER, fields[ FIELD_VALUE ] )
                                             .withValue( ContactsContract.CommonDataKinds.Phone.TYPE, typeInt );
                            break;
                        case NAME_EMAIL:
                            builder = builder.withValue( ContactsContract.CommonDataKinds.Email.DATA, fields[ FIELD_VALUE ] )
                                             .withValue( ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM )
                                             .withValue( ContactsContract.CommonDataKinds.Email.LABEL, fields[ FIELD_TYPE ] );
                            break;
                    }

                    ops.add( builder.build() );
                }

                c.moveToFirst();
                while (c.moveToNext()) {
                    int dataId = c.getInt( 0 );
                    if (! liveDataIds.contains( dataId )) {
                        deadDataIds.add( dataId );
                    }
                }
            } finally {
                c.close();
            }
            vcard.clear();
        }

        _context.getContentResolver().applyBatch( ContactsContract.AUTHORITY, ops );

        removeDeadItems( liveRawContactIds, deadDataIds, counts );

        return counts;
    }

    // View.OnClickListener implementation

    public void onClick( View v ) {
        try {
            InputStream in = fetchContacts();
            if (in == null) {
                throw new IOException( "Unable to obtain connection to pimple vcard file" );
            }
            try {
                int[] counts = updateContacts( new BufferedReader( new InputStreamReader( in ) ), getGroupId() );
                String msg = "Injected " + counts[ COUNT_CONTACTS_ADDED ] + " new contacts, updated " + counts[ COUNT_CONTACTS_UPDATED ]
                           + " existing contacts, and deleted " + counts[ COUNT_CONTACTS_DELETED ] + " contacts."
                           + " Added " + counts[ COUNT_DATA_ADDED ] + " new data items, updated " + counts[ COUNT_DATA_UPDATED ]
                           + " existing data items, and deleted " + counts[ COUNT_DATA_DELETED ] + " data items.";
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
