/*
 * (c) staktrace systems, 2014.
 * See http://staktrace.com/ for license details.
 */

package com.staktrace.pimple.accounts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

class PimpleContactInjector {
    private static final String TAG = "PimpleContactInjector";
    private static final String QUERY = ContactsContract.RawContacts.ACCOUNT_TYPE + "=? AND " + ContactsContract.RawContacts.ACCOUNT_NAME + "=?";
    private static final String PIMPLE_TYPE = "com.staktrace.pimple";
    private static final String MIME_VCARD = "text/x-vcard";
    private static final String TOKEN_TYPE_COOKIE = "cookie"; // must match com.staktrace.pimple.accounts.Pimple.TOKEN_TYPE_COOKIE

    private static final String NAME_ID = "X-PIMPLE-ID";
    private enum MappedField {
        NAME("FN", ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
        TELEPHONE("TEL", ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
        EMAIL("EMAIL", ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);

        public final String name;
        public final String mimeType;

        MappedField( String name_, String mimeType_ ) {
            name = name_;
            mimeType = mimeType_;
        }
    };

    private static final String PARAM_TYPE = "TYPE=";
    private static final String PARAM_ID = "X-PIMPLE-ID=";

    private enum PhoneTypeMap {
        HOME(ContactsContract.CommonDataKinds.Phone.TYPE_HOME),
        WORK(ContactsContract.CommonDataKinds.Phone.TYPE_WORK),
        CELL(ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);

        public final int androidType;

        PhoneTypeMap( int androidType_ ) {
            androidType = androidType_;
        }
    };

    private static final int COUNT_CONTACTS_ADDED = 0;
    private static final int COUNT_CONTACTS_DELETED = 1;
    private static final int COUNT_DATA_ADDED = 2;
    private static final int COUNT_DATA_UPDATED = 3;
    private static final int COUNT_DATA_DELETED = 4;
    private static final int NUM_COUNTS = 5;

    private static final int FIELD_NAME = 0;
    private static final int FIELD_TYPE = 1;
    private static final int FIELD_ID = 2;
    private static final int FIELD_VALUE = 3;
    private static final int NUM_FIELDS = 4;

    private final Context _context;
    private final Account _account;
    private final ContentResolver _resolver;
    private final MessageDigest _digester;
    private final String _accountName;

    PimpleContactInjector( Context context, Account account ) {
        _context = context;
        _account = account;
        _accountName = _account.name;
        _resolver = _context.getContentResolver();

        MessageDigest digester;
        try {
            digester = MessageDigest.getInstance( "SHA-1" );
        } catch (NoSuchAlgorithmException nsae) {
            digester = null;
        }
        _digester = digester;
    }

    private InputStream fetchContacts() throws IOException {
        AccountManager am = AccountManager.get( _context );

        String cookie = null;
        try {
            cookie = am.getAuthToken( _account, TOKEN_TYPE_COOKIE, null, true, null, null ).getResult().getString( AccountManager.KEY_AUTHTOKEN );
        } catch (Exception e) {
            Log.e( TAG, "Error while getting auth token", e );
        }
        if (cookie == null) {
            Log.w( TAG, "Got a null cookie from auth token fetch" );
            return null;
        }

        Log.d( TAG, "Auth token fetch complete, got " + cookie );
        String server = _accountName;
        int at = server.indexOf( '@' );
        if (at >= 0) {
            server = server.substring( at + 1 );
        }
        URL url = new URL( "https://" + server + "/touch/vcard.php?tag=vcard" );
        URLConnection conn = url.openConnection();
        conn.addRequestProperty( "Cookie", cookie );
        Log.i( TAG, "Received document of type " + conn.getContentType() + " from pimple" );
        if (conn.getContentType().equalsIgnoreCase( MIME_VCARD )) {
            return conn.getInputStream();
        } else {
            Log.w( TAG, "Didn't get back a vcard file; invalidating auth token" );
            am.invalidateAuthToken( PIMPLE_TYPE, cookie );
            return null;
        }
    }

    private int doInsert( Uri table, ContentValues values ) {
        Uri inserted = _resolver.insert( table, values );
        return Integer.parseInt( inserted.getLastPathSegment() );
    }

    private int getGroupId() {
        int groupId = -1;
        Cursor c = _resolver.query( ContactsContract.Groups.CONTENT_URI,
                                                        new String[] { ContactsContract.Groups._ID },
                                                        QUERY,
                                                        new String[] { PIMPLE_TYPE, _accountName },
                                                        null );
        try {
            if (c.moveToNext()) {
                groupId = c.getInt( 0 );
            }
        } finally {
            c.close();
        }
        if (groupId < 0) {
            ContentValues values = new ContentValues();
            values.put( ContactsContract.Groups.ACCOUNT_TYPE, PIMPLE_TYPE );
            values.put( ContactsContract.Groups.ACCOUNT_NAME, _accountName );
            values.put( ContactsContract.Groups.GROUP_VISIBLE, 1 );
            values.put( ContactsContract.Groups.TITLE, "Pimple from " + _accountName );
            values.put( ContactsContract.Groups.NOTES, "Contacts from " + _accountName + " via Pimple" );
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
            Cursor c = _resolver.query( ContactsContract.RawContacts.CONTENT_URI,
                                        new String[] { ContactsContract.RawContacts._ID },
                                        QUERY + " AND " + ContactsContract.RawContacts.SOURCE_ID + "=?",
                                        new String[] { PIMPLE_TYPE, _accountName, pimpleId },
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
            values.put( ContactsContract.RawContacts.ACCOUNT_NAME, _accountName );
            values.put( ContactsContract.RawContacts.SOURCE_ID, pimpleId );
            rawContactId = doInsert( ContactsContract.RawContacts.CONTENT_URI, values );

            ops.add( ContentProviderOperation.newInsert( ContactsContract.Data.CONTENT_URI )
                                             .withValue( ContactsContract.Data.RAW_CONTACT_ID, rawContactId )
                                             .withValue( ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE )
                                             .withValue( ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId )
                                             .build() );
            counts[ COUNT_CONTACTS_ADDED ]++;
        }

        return rawContactId;
    }

    private MappedField mapField( String fieldName ) {
        for (MappedField field : MappedField.values()) {
            if (field.name.equals( fieldName )) {
                return field;
            }
        }
        return null;
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
        counts[ COUNT_CONTACTS_DELETED ] += _resolver.delete( adapterUri, query.toString(), new String[] { PIMPLE_TYPE, _accountName } );

        query.setLength( 0 );
        query.append( ContactsContract.Data._ID ).append( " IN (-1" );
        for (int id : deadDataIds) {
            query.append( ',' ).append( id );
        }
        query.append( ')' );
        counts[ COUNT_DATA_DELETED ] += _resolver.delete( ContactsContract.Data.CONTENT_URI, query.toString(), null );
    }

    private String getDigest( int rawContactId, String[] fields ) {
        StringBuffer sb = new StringBuffer( rawContactId ).append( ',' )
                            .append( fields[ FIELD_NAME ] ).append( ',' )
                            .append( fields[ FIELD_ID ] ).append( ',' )
                            .append( fields[ FIELD_TYPE ] ).append( ',' )
                            .append( fields[ FIELD_VALUE ] );
        _digester.reset();
        byte[] bytes = _digester.digest( sb.toString().getBytes() );
        sb.setLength( 0 );
        for (int i = 0; i < bytes.length; i++) {
            int b = (bytes[i] & 0xF0) >> 4;
            sb.append( (char)(b < 10 ? '0' + b : 'a' + b - 10) );
            b = (bytes[i] & 0x0F);
            sb.append( (char)(b < 10 ? '0' + b : 'a' + b - 10) );
        }
        return sb.toString();
    }

    private ContentProviderOperation.Builder updateBuilder( ContentProviderOperation.Builder builder, MappedField field, String[] fields ) {
        switch (field) {
            case NAME:
                builder = builder.withValue( ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, fields[ FIELD_VALUE ] );
                break;
            case TELEPHONE:
                int typeInt = ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM;
                for (PhoneTypeMap type : PhoneTypeMap.values()) {
                    if (type.name().equalsIgnoreCase( fields[ FIELD_TYPE ] )) {
                        typeInt = type.androidType;
                    }
                }
                builder = builder.withValue( ContactsContract.CommonDataKinds.Phone.NUMBER, fields[ FIELD_VALUE ] )
                                 .withValue( ContactsContract.CommonDataKinds.Phone.TYPE, typeInt );
                if (typeInt == ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM) {
                    builder = builder.withValue( ContactsContract.CommonDataKinds.Phone.LABEL, fields[ FIELD_TYPE ] );
                }
                break;
            case EMAIL:
                builder = builder.withValue( ContactsContract.CommonDataKinds.Email.DATA, fields[ FIELD_VALUE ] )
                                 .withValue( ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM )
                                 .withValue( ContactsContract.CommonDataKinds.Email.LABEL, fields[ FIELD_TYPE ] );
                break;
        }
        return builder;
    }

    private Set<Integer> updateContactData( int rawContactId, List<String[]> vcard, Cursor dataCursor, ArrayList<ContentProviderOperation> ops, int[] counts ) {
        Set<Integer> liveDataIds = new TreeSet<Integer>();

        for (String[] fields : vcard) {
            MappedField field = mapField( fields[ FIELD_NAME ] );
            if (field == null) {
                continue;
            }

            if (fields[ FIELD_ID ] == null) {
                continue;
            }

            int dataId = -1;
            String oldDigest = null;
            dataCursor.moveToFirst();
            while (dataCursor.moveToNext()) {
                if (fields[ FIELD_ID ].equals( dataCursor.getString( 2 ) ) && field.mimeType.equals( dataCursor.getString( 1 ))) {
                    dataId = dataCursor.getInt( 0 );
                    oldDigest = dataCursor.getString( 3 );
                    break;
                }
            }

            String newDigest = getDigest( rawContactId, fields );

            ContentProviderOperation.Builder builder = null;
            if (dataId < 0) {
                Log.d( TAG, "Data item for [" + fields[ FIELD_NAME ] + "]/[" + fields[ FIELD_ID ] + "] not found; creating..." );
                builder = ContentProviderOperation.newInsert( ContactsContract.Data.CONTENT_URI )
                                                  .withValue( ContactsContract.Data.RAW_CONTACT_ID, rawContactId )
                                                  .withValue( ContactsContract.Data.SYNC1, fields[ FIELD_ID ] )
                                                  .withValue( ContactsContract.Data.SYNC2, newDigest )
                                                  .withValue( ContactsContract.Data.MIMETYPE, field.mimeType );
                counts[ COUNT_DATA_ADDED ]++;
            } else {
                liveDataIds.add( dataId );
                if (! newDigest.equals( oldDigest )) {
                    Log.d( TAG, "Data item for [" + fields[ FIELD_NAME ] + "]/[" + fields[ FIELD_ID ] + "] modified; updating..." );
                    builder = ContentProviderOperation.newUpdate( ContactsContract.Data.CONTENT_URI )
                                                      .withSelection( ContactsContract.Data._ID + "=?", new String[] { Integer.toString( dataId ) } )
                                                      .withValue( ContactsContract.Data.SYNC2, newDigest );
                    counts[ COUNT_DATA_UPDATED ]++;
                }
            }

            if (builder != null) {
                builder = updateBuilder( builder, field, fields );
                ops.add( builder.build() );
            }
        }

        return liveDataIds;
    }

    private int[] updateContacts( BufferedReader source, int groupId ) throws IOException, RemoteException, OperationApplicationException, NoSuchAlgorithmException {
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

            Log.d( TAG, "Processing vcard for pimple id " + pimpleId );

            int rawContactId = getRawContactId( pimpleId, groupId, ops, counts );
            liveRawContactIds.add( rawContactId );

            Log.d( TAG, "Mapped to raw contact id [" + rawContactId + "]" );

            Cursor c = _resolver.query( ContactsContract.Data.CONTENT_URI,
                                        new String[] { ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.SYNC1, ContactsContract.Data.SYNC2 },
                                        ContactsContract.Data.RAW_CONTACT_ID + "=?",
                                        new String[] { Integer.toString( rawContactId ) },
                                        null );
            try {
                Set<Integer> liveDataIds = updateContactData( rawContactId, vcard, c, ops, counts );
                c.moveToFirst();
                while (c.moveToNext()) {
                    int dataId = c.getInt( 0 );
                    if (! liveDataIds.contains( dataId )) {
                        Log.i( TAG, "Found dead data item [" + dataId + "]" );
                        deadDataIds.add( dataId );
                    }
                }

                _resolver.applyBatch( ContactsContract.AUTHORITY, ops );
                ops.clear();
            } finally {
                c.close();
            }
            vcard.clear();
        }

        removeDeadItems( liveRawContactIds, deadDataIds, counts );

        return counts;
    }

    public void run() {
        Log.i( TAG, "Running injector for account " + _account );
        String message;

        try {
            InputStream in = fetchContacts();
            if (in == null) {
                throw new IOException( "Unable to obtain connection to pimple vcard file" );
            }
            try {
                int[] counts = updateContacts( new BufferedReader( new InputStreamReader( in ) ), getGroupId() );
                message = "Injected " + counts[ COUNT_CONTACTS_ADDED ] + " new contacts"
                        + " and deleted " + counts[ COUNT_CONTACTS_DELETED ] + " contacts."
                        + " Added " + counts[ COUNT_DATA_ADDED ] + " new data items, updated " + counts[ COUNT_DATA_UPDATED ]
                        + " existing data items, and deleted " + counts[ COUNT_DATA_DELETED ] + " data items.";
            } catch (Exception e) {
                e.printStackTrace();
                message = e.toString();
            } finally {
                in.close();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            message = ioe.toString();
        }
        final String msg = message;
        Log.i( TAG, msg );
    }
}
