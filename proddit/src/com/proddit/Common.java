package com.proddit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieSyncManager;
import android.widget.TextView;
import android.widget.Toast;


public class Common {
	private static final int SOCKET_OPERATION_TIMEOUT = 60 * 1000;
	private static final DefaultHttpClient mGzipHttpClient = createGzipHttpClient();
	private static final CookieStore mCookieStore = mGzipHttpClient.getCookieStore();
	
	public static DefaultHttpClient createGzipHttpClient() {
		DefaultHttpClient httpclient = new DefaultHttpClient(){
		    @Override
		    protected ClientConnectionManager createClientConnectionManager() {
		        SchemeRegistry registry = new SchemeRegistry();
		        registry.register(
		                new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		        registry.register(
		        		new Scheme("https", getHttpsSocketFactory(), 443));
		        HttpParams params = getParams();
				HttpConnectionParams.setConnectionTimeout(params, SOCKET_OPERATION_TIMEOUT);
				HttpConnectionParams.setSoTimeout(params, SOCKET_OPERATION_TIMEOUT);
		        return new ThreadSafeClientConnManager(params, registry);
		    }
		    
		    /** Gets an HTTPS socket factory with SSL Session Caching if such support is available, otherwise falls back to a non-caching factory
		     * @return
		     */
		    protected SocketFactory getHttpsSocketFactory(){
				try {
					Class<?> sslSessionCacheClass = Class.forName("android.net.SSLSessionCache");
			    	Object sslSessionCache = sslSessionCacheClass.getConstructor(Context.class).newInstance(ProdditApp.getApplication());
			    	Method getHttpSocketFactory = Class.forName("android.net.SSLCertificateSocketFactory").getMethod("getHttpSocketFactory", new Class<?>[]{int.class, sslSessionCacheClass});
			    	return (SocketFactory) getHttpSocketFactory.invoke(null, SOCKET_OPERATION_TIMEOUT, sslSessionCache);
				}catch(Exception e){
					return SSLSocketFactory.getSocketFactory();
				}
		    }
		};
		
		
        httpclient.addRequestInterceptor(new HttpRequestInterceptor() {
            public void process(
                    final HttpRequest request,
                    final HttpContext context
            ) throws HttpException, IOException {
                request.setHeader("User-Agent", Constants.USER_AGENT_STRING);
                if (!request.containsHeader("Accept-Encoding"))
                    request.addHeader("Accept-Encoding", "gzip");
            }
        });
        httpclient.addResponseInterceptor(new HttpResponseInterceptor() {
            public void process(
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                HttpEntity entity = response.getEntity();
                Header ceheader = entity.getContentEncoding();
                if (ceheader != null) {
                    HeaderElement[] codecs = ceheader.getElements();
                    for (int i = 0; i < codecs.length; i++) {
                        if (codecs[i].getName().equalsIgnoreCase("gzip")) {
                            response.setEntity(
                                    new GzipDecompressingEntity(response.getEntity())); 
                            return;
                        }
                    }
                }
            }
        });
        return httpclient;
	}
    static class GzipDecompressingEntity extends HttpEntityWrapper {
        public GzipDecompressingEntity(final HttpEntity entity) {
            super(entity);
        }
        @Override
        public InputStream getContent()
            throws IOException, IllegalStateException {
            // the wrapped entity's getContent() decides about repeatability
            InputStream wrappedin = wrappedEntity.getContent();
            return new GZIPInputStream(wrappedin);
        }
        @Override
        public long getContentLength() {
            // length of ungzipped content is not known
            return -1;
        }
    }
	
	public static boolean isHttpStatusOK(HttpResponse response) {
		if (response == null || response.getStatusLine() == null) {
			return false;
		}
		return response.getStatusLine().getStatusCode() == 200;
	}
	
	public static boolean isEmpty(CharSequence s) {
		return s == null || "".equals(s);
	}

	public static boolean listContainsIgnoreCase(ArrayList<String> list, String str){
		for (Iterator<String> iterator = list.iterator(); iterator.hasNext();) {
			String string = (String) iterator.next();
			
			if(string.equalsIgnoreCase(str))
				return true;
		}
		return false;
	}

    public static void logDLong(String tag, String msg) {
		int c;
		boolean done = false;
		StringBuilder sb = new StringBuilder();
		for (int k = 0; k < msg.length(); k += 80) {
			for (int i = 0; i < 80; i++) {
				if (k + i >= msg.length()) {
					done = true;
					break;
				}
				c = msg.charAt(k + i);
				sb.append((char) c);
			}
			if (Constants.LOGGING) Log.d(tag, "multipart log: " + sb.toString());
			sb = new StringBuilder();
			if (done)
				break;
		}
	} 
	
	public static void showErrorToast(String error, int duration, Context context) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		Toast t = new Toast(context);
		t.setDuration(duration);
		View v = inflater.inflate(R.layout.error_toast, null);
		TextView errorMessage = (TextView) v.findViewById(R.id.errorMessage);
		errorMessage.setText(error);
		t.setView(v);
		t.show();
	}
	
    /**
     * Get a new modhash by scraping and return it
     * 
     * @param client
     * @return
     */
    public static String doUpdateModhash(HttpClient client) {
        final Pattern MODHASH_PATTERN = Pattern.compile("modhash: '(.*?)'");
    	String modhash;
    	HttpEntity entity = null;
        // The pattern to find modhash from HTML javascript area
    	try {
    		HttpGet httpget = new HttpGet(Constants.MODHASH_URL);
    		HttpResponse response = client.execute(httpget);
    		
    		// For modhash, we don't care about the status, since the 404 page has the info we want.
//    		status = response.getStatusLine().toString();
//        	if (!status.contains("OK"))
//        		throw new HttpException(status);
        	
        	entity = response.getEntity();

        	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
        	// modhash should appear within first 1200 chars
        	char[] buffer = new char[1200];
        	in.read(buffer, 0, 1200);
        	in.close();
        	String line = String.valueOf(buffer);
        	entity.consumeContent();
        	
        	if (Common.isEmpty(line)) {
        		throw new HttpException("No content returned from doUpdateModhash GET to "+Constants.MODHASH_URL);
        	}
        	if (line.contains("USER_REQUIRED")) {
        		throw new Exception("User session error: USER_REQUIRED");
        	}
        	
        	Matcher modhashMatcher = MODHASH_PATTERN.matcher(line);
        	if (modhashMatcher.find()) {
        		modhash = modhashMatcher.group(1);
        		if (Common.isEmpty(modhash)) {
        			// Means user is not actually logged in.
        			return null;
        		}
        	} else {
        		throw new Exception("No modhash found at URL "+Constants.MODHASH_URL);
        	}

        	if (Constants.LOGGING) Common.logDLong("updateModHash", line);
        	
        	if (Constants.LOGGING) Log.d("updateModHash", "modhash: "+modhash);
        	return modhash;
        	
    	} catch (Exception e) {
    		if (entity != null) {
    			try {
    				entity.consumeContent();
    			} catch (Exception e2) {
    				if (Constants.LOGGING) Log.e("updateModHash", "entity.consumeContent()", e);
    			}
    		}
    		if (Constants.LOGGING) Log.e("updateModHash", "doUpdateModhash()", e);
    		return null;
    	}
    }
    
    public static void doLogout(ProdditSettings settings, HttpClient client, Context context) {
    	clearCookies(settings, client, context);
    	settings.setUsername(null);
    }
    
	public static CookieStore getCookieStore() {
		return mCookieStore;
	}    
	/**
	 * http://hc.apache.org/httpcomponents-client/examples.html
	 * @return a Gzip-enabled DefaultHttpClient
	 */
	public static HttpClient getGzipHttpClient() {
		return mGzipHttpClient;
	}
	public static boolean isEmpty(Collection<?> theCollection) {
		return theCollection == null || theCollection.isEmpty();
	}
	
	public static Uri createSubredditUri(String subreddit) {
		return Uri.parse(new StringBuilder(Constants.REDDIT_BASE_URL + "/r/")
			.append(subreddit)
			.toString());
	}
	
    static void clearCookies(ProdditSettings settings, HttpClient client, Context context) {
        settings.setRedditSessionCookie(null);

        Common.getCookieStore().clear();
        CookieSyncManager.getInstance().sync();
        
        SharedPreferences sessionPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    	SharedPreferences.Editor editor = sessionPrefs.edit();
    	editor.remove("reddit_sessionValue");
    	editor.remove("reddit_sessionDomain");
    	editor.remove("reddit_sessionPath");
    	editor.remove("reddit_sessionExpiryDate");
        editor.commit();
    }
};
