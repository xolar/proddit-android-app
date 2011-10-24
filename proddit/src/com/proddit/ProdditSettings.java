package com.proddit;

import java.util.Date;

import org.apache.http.client.HttpClient;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookie;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.CookieSyncManager;

public class ProdditSettings {
	private static final String TAG = "prodditSettings";
	private String username = null;
	private Cookie redditSessionCookie = null;
	private String modhash = null;

    public void loadRedditPreferences(Context context, HttpClient client) {
        // Session
    	SharedPreferences sessionPrefs = context.getSharedPreferences("PRODDIT", 0);
    	this.setUsername(sessionPrefs.getString("username", null));
    	this.setModhash(sessionPrefs.getString("modhash", null));
        String cookieValue = sessionPrefs.getString("reddit_sessionValue", null);
        String cookieDomain = sessionPrefs.getString("reddit_sessionDomain", null);
        String cookiePath = sessionPrefs.getString("reddit_sessionPath", null);
        long cookieExpiryDate = sessionPrefs.getLong("reddit_sessionExpiryDate", -1);
        if (cookieValue != null) {
        	BasicClientCookie redditSessionCookie = new BasicClientCookie("reddit_session", cookieValue);
        	redditSessionCookie.setDomain(cookieDomain);
        	redditSessionCookie.setPath(cookiePath);
        	if (cookieExpiryDate != -1)
        		redditSessionCookie.setExpiryDate(new Date(cookieExpiryDate));
        	else
        		redditSessionCookie.setExpiryDate(null);
        	this.setRedditSessionCookie(redditSessionCookie);
    		Common.getCookieStore().addCookie(redditSessionCookie);
    		try {
    			CookieSyncManager.getInstance().sync();
    		} catch (IllegalStateException ex) {
    			if (Constants.LOGGING) Log.e(TAG, "CookieSyncManager.getInstance().sync()", ex);
    		}
        }
    }
    public void saveRedditPreferences(Context context) {
    	SharedPreferences settings = context.getSharedPreferences("PRODDIT", 0);
    	SharedPreferences.Editor editor = settings.edit();
    	
    	// Session
    	if (this.username != null)
    		editor.putString("username", this.username);
    	else
    		editor.remove("username");
    	if (this.redditSessionCookie != null) {
    		editor.putString("reddit_sessionValue",  this.redditSessionCookie.getValue());
    		editor.putString("reddit_sessionDomain", this.redditSessionCookie.getDomain());
    		editor.putString("reddit_sessionPath",   this.redditSessionCookie.getPath());
    		if (this.redditSessionCookie.getExpiryDate() != null)
    			editor.putLong("reddit_sessionExpiryDate", this.redditSessionCookie.getExpiryDate().getTime());
    	}
    	if (this.modhash != null)
    		editor.putString("modhash", this.modhash.toString());
    	//fuuuuuuuu
    	editor.commit();
    }
    
	
	public boolean isLoggedIn() {
		return username != null;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public Cookie getRedditSessionCookie() {
		return redditSessionCookie;
	}

	public void setRedditSessionCookie(Cookie redditSessionCookie) {
		this.redditSessionCookie = redditSessionCookie;
	}

	public String getModhash() {
		return modhash;
	}

	public void setModhash(String modhash) {
		this.modhash = modhash;
	}

}
