/*
 * (c) staktrace systems, 2014.
 * See http://staktrace.com/ for license details.
 */

package com.staktrace.pimple.epoch;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;

import com.staktrace.pimple.accounts.Pimple;

class CalendarCreator {
    private static final String TAG = "PimpleCalendarCreator";
    private static final String QUERY = CalendarContract.Calendars.ACCOUNT_TYPE + "=? AND " + CalendarContract.Calendars.ACCOUNT_NAME + "=?";

    private final Context _context;
    private final Account _account;
    private final String _accountName;
    private final ContentResolver _resolver;

    CalendarCreator( Context context, Account account ) {
        _context = context;
        _account = account;
        _accountName = _account.name;
        _resolver = _context.getContentResolver();
    }

    private int doInsert( Uri table, ContentValues values ) {
        Uri inserted = _resolver.insert( table, values );
        return Integer.parseInt( inserted.getLastPathSegment() );
    }

    private int createCalendar() {
        int calendarId = -1;
        Cursor c = _resolver.query( CalendarContract.Calendars.CONTENT_URI,
                new String[] { CalendarContract.Calendars._ID },
                QUERY,
                new String[] { Pimple.ACCOUNT_TYPE, _accountName },
                null );
        try {
            if (c.moveToNext()) {
                calendarId = c.getInt( 0 );
            }
        } finally {
            c.close();
        }
        if (calendarId < 0) {
            ContentValues values = new ContentValues();
            values.put( CalendarContract.Calendars.ACCOUNT_TYPE, Pimple.ACCOUNT_TYPE );
            values.put( CalendarContract.Calendars.ACCOUNT_NAME, _accountName );
            values.put( CalendarContract.Calendars.NAME, "Events" );
            values.put( CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Events from " + _accountName + " via Pimple" );
            values.put( CalendarContract.Calendars.CALENDAR_COLOR, Color.GREEN );
            values.put( CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER );
            values.put( CalendarContract.Calendars.OWNER_ACCOUNT, _accountName );
            values.put( CalendarContract.Calendars.VISIBLE, 1 );
            values.put( CalendarContract.Calendars.SYNC_EVENTS, 1 );
            Uri adapterUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                    .appendQueryParameter( CalendarContract.CALLER_IS_SYNCADAPTER, "true" )
                    .appendQueryParameter( CalendarContract.Calendars.ACCOUNT_TYPE, Pimple.ACCOUNT_TYPE )
                    .appendQueryParameter( CalendarContract.Calendars.ACCOUNT_NAME, _accountName )
                    .build();
            calendarId = doInsert( adapterUri, values );
        }
        return calendarId;
    }

    public void run() {
        Log.i( TAG, "Running calendar creator for account " + _account );
        int calendarId = createCalendar();
        Log.i( TAG, "Calendar id is " + calendarId );
    }
}
