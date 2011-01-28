/*
 * (c) staktrace systems, 2011.
 * See http://staktrace.com/ for license details.
 */

package com.staktrace.pimple.accounts;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

class HttpAuthenticator {
    private static final String TAG = "HttpAuthenticator";

    private final String _username;
    private final String _password;
    private int _error;
    private String _cookie;

    HttpAuthenticator( String username, String password ) {
        _username = username;
        _password = password;
    }

    boolean authenticate() {
        if (TextUtils.isEmpty( _username )) {
            _error = R.string.error_empty_username;
            return false;
        }
        int at = _username.indexOf( '@' );
        if (at < 0) {
            _error = R.string.error_unknown_username;
            return false;
        }

        String host = _username.substring( at + 1 );
        String user = _username.substring( 0, at );
        try {
            URL url = new URL( "https://" + host + "/login/index.php" );
            HttpURLConnection http = (HttpURLConnection)url.openConnection();
            http.setRequestMethod( "POST" );
            http.setDoOutput( true );
            http.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded" );
            try {
                String encoding = "UTF-8";
                StringBuffer sb = new StringBuffer();
                sb.append( "username=" ).append( URLEncoder.encode( user, encoding ) )
                  .append( "&password=" ).append( URLEncoder.encode( _password, encoding ) )
                  .append( "&sname=" ).append( URLEncoder.encode( Build.MODEL + "/pimple", encoding ) );
                byte[] b = sb.toString().getBytes();
                http.setRequestProperty( "Content-Length", Integer.toString( b.length ) );
                OutputStream out = http.getOutputStream();
                out.write( b );
                out.close();
                if (http.getResponseCode() == 200) {
                    _cookie = http.getHeaderField( Pimple.HTTP_COOKIE_HEADER );
                    if (_cookie != null) {
                        return true;
                    }
                    _error = R.string.error_failed_auth;
                }
            } finally {
                http.disconnect();
            }
        } catch (Exception e) {
            Log.e( TAG, "Error while validating credentials", e );
            _error = R.string.error_network;
        }
        return false;
    }

    int getErrorId() {
        return _error;
    }

    String getToken() {
        return _cookie;
    }
}
