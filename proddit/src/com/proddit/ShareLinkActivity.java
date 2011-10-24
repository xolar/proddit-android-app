/*
 * Copyright 2009 Andrew Shu
 *
 * This file is part of "reddit is fun".
 *
 * "reddit is fun" is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * "reddit is fun" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with "reddit is fun".  If not, see <http://www.gnu.org/licenses/>.
 */

package com.proddit;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.CookieSyncManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;
import android.widget.Toast;


public class ShareLinkActivity extends TabActivity {
	
	private static final String TAG = "SubmitLinkActivity";

    // Group 1: Subreddit. Group 2: thread id (no t3_ prefix)
    private final Pattern NEW_THREAD_PATTERN = Pattern.compile(Constants.COMMENT_PATH_PATTERN_STRING);
    // Group 1: whole error. Group 2: the time part
    private final Pattern RATELIMIT_RETRY_PATTERN = Pattern.compile("(you are trying to submit too fast. try again in (.+?)\\.)");
	// Group 1: Subreddit
//    private final Pattern SUBMIT_PATH_PATTERN = Pattern.compile("/(?:r/([^/]+)/)?submit/?");
    
	TabHost mTabHost;
	ProdditSettings mSettings = new ProdditSettings();
	
	private final HttpClient mClient = Common.getGzipHttpClient();

	private String mSubmitUrl = Constants.REDDIT_BASE_URL + "/submit";
	
	private volatile String mCaptchaIden = null;
	private volatile String mCaptchaUrl = null;
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		CookieSyncManager.createInstance(getApplicationContext());
		
		mSettings.loadRedditPreferences(this, mClient);

		setContentView(R.layout.submit_link_main);

//		final FrameLayout fl = (FrameLayout) findViewById(android.R.id.tabcontent);
		
		mTabHost = getTabHost();
		mTabHost.addTab(mTabHost.newTabSpec(Constants.TAB_LINK).setIndicator("link").setContent(R.id.submit_link_view));
		mTabHost.addTab(mTabHost.newTabSpec(Constants.TAB_TEXT).setIndicator("text").setContent(R.id.submit_text_view));
		mTabHost.setOnTabChangedListener(new OnTabChangeListener() {
			public void onTabChanged(String tabId) {
				// Copy everything (except url and text) from old tab to new tab
				final EditText submitLinkTitle = (EditText) findViewById(R.id.submit_link_title);
				final EditText submitLinkReddit = (EditText) findViewById(R.id.submit_link_reddit);
	        	final EditText submitTextTitle = (EditText) findViewById(R.id.submit_text_title);
	        	final EditText submitTextReddit = (EditText) findViewById(R.id.submit_text_reddit);
				if (Constants.TAB_LINK.equals(tabId)) {
					submitLinkTitle.setText(submitTextTitle.getText());
					submitLinkReddit.setText(submitTextReddit.getText());
				} else {
					submitTextTitle.setText(submitLinkTitle.getText());
					submitTextReddit.setText(submitLinkReddit.getText());
				}
			}
		});
		mTabHost.setCurrentTab(0);
		
		if (mSettings.isLoggedIn()) {
			start();
		} else {
			showDialog(Constants.DIALOG_LOGIN);
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		CookieSyncManager.getInstance().startSync();
	}
	
	@Override
    protected void onPause() {
    	super.onPause();
    	mSettings.saveRedditPreferences(this);
		CookieSyncManager.getInstance().stopSync();
    }
    
	/**
	 * Enable the UI after user is logged in.
	 */
	private void start() {
		// Intents can be external (browser share page) or from Reddit is fun.
        String intentAction = getIntent().getAction();
        if (Intent.ACTION_SEND.equals(intentAction)) {
        	// Share
	        Bundle extras = getIntent().getExtras();
	        if(getIntent().getType().startsWith("text")){
		        if (extras != null) {
		        	String url = extras.getString(Intent.EXTRA_TEXT);
		        	final EditText submitLinkUrl = (EditText) findViewById(R.id.submit_link_url);
		        	final EditText submitLinkReddit = (EditText) findViewById(R.id.submit_link_reddit);
		        	final EditText submitTextReddit = (EditText) findViewById(R.id.submit_text_reddit);
		        	submitLinkUrl.setText(url);
		        	submitLinkReddit.setText("proddit.com");
	        		submitTextReddit.setText("proddit.com");
	        		mSubmitUrl = Constants.REDDIT_BASE_URL + "/submit";
		        }
	        }else if(getIntent().getType().startsWith("image")){
		        	Uri imageUri = (Uri)extras.get(Intent.EXTRA_STREAM);
		        	InputStream fileIs;
					try {
						fileIs = getContentResolver().openInputStream(imageUri);
			        	new UploadImgurTask(fileIs) {
							@Override
							protected void onPostExecute(Uri result) {
								dismissDialog(Constants.DIALOG_SUBMITTING);
								if (result!=null) {
					    			((EditText)findViewById(R.id.submit_link_url)).setText(result.toString());
						        	final EditText submitLinkReddit = (EditText) findViewById(R.id.submit_link_reddit);
						        	submitLinkReddit.setText("poze");
						        	final EditText submitTitle = (EditText) findViewById(R.id.submit_link_title);
						        	submitTitle.requestFocus();
					        	} else {
					            	Common.showErrorToast("Uploadul catre imgur nu a reusit", Toast.LENGTH_LONG, ShareLinkActivity.this);
					        	}
							}
	
							@Override
							protected void onPreExecute() {
								showDialog(Constants.DIALOG_SUBMITTING);
							}
			        	}
			        	.execute();
					} catch (FileNotFoundException e) {
						Log.e("No file found",e.toString());
					}
	        }
        }  
        
        final Button submitLinkButton = (Button) findViewById(R.id.submit_link_button);
        submitLinkButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		if (validateLinkForm()) {
	        		final EditText submitLinkTitle = (EditText) findViewById(R.id.submit_link_title);
	        		final EditText submitLinkUrl = (EditText) findViewById(R.id.submit_link_url);
	        		final EditText submitLinkReddit = (EditText) findViewById(R.id.submit_link_reddit);
	        		final EditText submitLinkCaptcha = (EditText) findViewById(R.id.submit_link_captcha);
	        		new SubmitLinkTask(submitLinkTitle.getText().toString(),
	        				submitLinkUrl.getText().toString(),
	        				submitLinkReddit.getText().toString(),
	        				Constants.SUBMIT_KIND_LINK,
	        				submitLinkCaptcha.getText().toString()).execute();
        		}
        	}
        });
        final Button submitTextButton = (Button) findViewById(R.id.submit_text_button);
        submitTextButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		if (validateTextForm()) {
	        		final EditText submitTextTitle = (EditText) findViewById(R.id.submit_text_title);
	        		final EditText submitTextText = (EditText) findViewById(R.id.submit_text_text);
	        		final EditText submitTextReddit = (EditText) findViewById(R.id.submit_text_reddit);
	        		final EditText submitTextCaptcha = (EditText) findViewById(R.id.submit_text_captcha);
	        		new SubmitLinkTask(submitTextTitle.getText().toString(),
	        				submitTextText.getText().toString(),
	        				submitTextReddit.getText().toString(),
	        				Constants.SUBMIT_KIND_SELF,
	        				submitTextCaptcha.getText().toString()).execute();
        		}
        	}
        });
        
        // Check the CAPTCHA
        new MyCaptchaCheckRequiredTask().execute();
	}
	
	private void returnStatus(int status) {
		Intent i = new Intent();
		setResult(status, i);
		finish();
	}

	
	
	private class MyLoginTask extends LoginTask {
    	public MyLoginTask(String username, String password) {
    		super(username, password, mSettings, mClient, getApplicationContext());
    	}
    	
    	@Override
    	protected void onPreExecute() {
    		showDialog(Constants.DIALOG_LOGGING_IN);
    	}
    	
    	@Override
    	protected void onPostExecute(Boolean success) {
    		dismissDialog(Constants.DIALOG_LOGGING_IN);
			if (success) {
    			Toast.makeText(ShareLinkActivity.this, "Logat ca "+mUsername, Toast.LENGTH_SHORT).show();
    			mSettings.saveRedditPreferences(ShareLinkActivity.this);
    			start();
        	} else {
            	Common.showErrorToast(mUserError, Toast.LENGTH_LONG, ShareLinkActivity.this);
    			returnStatus(Constants.RESULT_LOGIN_REQUIRED);
        	}
    	}
    }
    
    

	private class SubmitLinkTask extends AsyncTask<Void, Void, Uri> {
    	String _mTitle, _mUrlOrText, _mSubreddit, _mKind, _mCaptcha;
		String _mUserError = "Eroare. Te rog incearca din nou.";
    	
    	SubmitLinkTask(String title, String urlOrText, String subreddit, String kind, String captcha) {
    		_mTitle = title;
    		_mUrlOrText = urlOrText;
    		_mSubreddit = subreddit;
    		_mKind = kind;
    		_mCaptcha = captcha;
    	}
    	
    	@Override
        public Uri doInBackground(Void... voidz) {
        	Uri newlyCreatedThread = null;
        	HttpEntity entity = null;
        	
        	String status = "";
        	if (!mSettings.isLoggedIn()) {
        		_mUserError = "Nelogat.";
        		return null;
        	}
        	// Update the modhash if necessary
        	if (mSettings.getModhash() == null) {
        		String modhash = Common.doUpdateModhash(mClient);
        		if (modhash == null) {
        			// doUpdateModhash should have given an error about credentials
        			Common.doLogout(mSettings, mClient, getApplicationContext());
        			if (Constants.LOGGING) Log.e(TAG, "Reply failed because doUpdateModhash() failed");
        			return null;
        		}
        		mSettings.setModhash(modhash);
        	}
        	
        	try {
        		// Construct data
    			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    			nvps.add(new BasicNameValuePair("sr", _mSubreddit.toString()));
    			nvps.add(new BasicNameValuePair("r", _mSubreddit.toString()));
    			nvps.add(new BasicNameValuePair("title", _mTitle.toString()));
    			nvps.add(new BasicNameValuePair("kind", _mKind.toString()));
    			// Put a url or selftext based on the kind of submission
    			if (Constants.SUBMIT_KIND_LINK.equals(_mKind))
    				nvps.add(new BasicNameValuePair("url", _mUrlOrText.toString()));
    			else // if (Constants.SUBMIT_KIND_SELF.equals(_mKind))
    				nvps.add(new BasicNameValuePair("text", _mUrlOrText.toString()));
    			nvps.add(new BasicNameValuePair("uh", mSettings.getModhash().toString()));
    			if (mCaptchaIden != null) {
    				nvps.add(new BasicNameValuePair("iden", mCaptchaIden));
    				nvps.add(new BasicNameValuePair("captcha", _mCaptcha.toString()));
    			}
    			// Votehash is currently unused by reddit 
//    				nvps.add(new BasicNameValuePair("vh", "0d4ab0ffd56ad0f66841c15609e9a45aeec6b015"));
    			
    			HttpPost httppost = new HttpPost(Constants.REDDIT_BASE_URL + "/api/submit");
    	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
    	        // The progress dialog is non-cancelable, so set a shorter timeout than system's
    	        HttpParams params = httppost.getParams();
    	        params.setParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, Boolean.FALSE);
    	        HttpConnectionParams.setConnectionTimeout(params, 30000);
    	        HttpConnectionParams.setSoTimeout(params, 30000);
    	        
    	        if (Constants.LOGGING) Log.d(TAG, nvps.toString());
    	        
                // Perform the HTTP POST request
    	    	HttpResponse response = mClient.execute(httppost);
    	    	status = response.getStatusLine().toString();
            	if (!status.contains("OK"))
            		throw new HttpException(status);
            	
            	entity = response.getEntity();

            	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
            	String line = in.readLine();
            	in.close();
            	if (Common.isEmpty(line)) {
            		throw new HttpException("No content returned from reply POST");
            	}
            	if (line.contains("WRONG_PASSWORD")) {
            		throw new Exception("Parola gresita.");
            	}
            	if (line.contains("USER_REQUIRED")) {
            		// The modhash probably expired
            		mSettings.setModhash(null);
            		throw new Exception("Este necesar un nume de utilizator.");
            	}
            	if (line.contains("SUBREDDIT_NOEXIST")) {
            		_mUserError = "Acel proddit nu exista.";
            		throw new Exception("SUBREDDIT_NOEXIST: " + _mSubreddit);
            	}
            	if (line.contains("SUBREDDIT_NOTALLOWED")) {
            		_mUserError = "Nu aveti permisiunea de a posta linkul asta pe acel proddit.";
            		throw new Exception("SUBREDDIT_NOTALLOWED: " + _mSubreddit);
            	}
            	
            	if (Constants.LOGGING) Common.logDLong(TAG, line);

            	String newId;
            	Matcher idMatcher = NEW_THREAD_PATTERN.matcher(line);
            	if (idMatcher.find()) {
            		newId = idMatcher.group(2);
            	} else {
            		if (line.contains("RATELIMIT")) {
                		// Try to find the # of minutes using regex
                    	Matcher rateMatcher = RATELIMIT_RETRY_PATTERN.matcher(line);
                    	if (rateMatcher.find())
                    		_mUserError = rateMatcher.group(1);
                    	else
                    		_mUserError = "Incerci prea repede. Asteapta cateva minute...";
                		throw new Exception(_mUserError);
                	}
            		if (line.contains("BAD_CAPTCHA")) {
            			_mUserError = "CAPTCHA gresit. Reincearca!";
            			new MyCaptchaDownloadTask().execute();
            		}
                	throw new Exception("No id returned by reply POST.");
            	}
            	
            	entity.consumeContent();
            	
            	// Getting here means success. Create a new Uri.
            	newlyCreatedThread = Uri.parse(Constants.REDDIT_BASE_URL+"/"+newId);
            	
            	return newlyCreatedThread;
            	
        	} catch (Exception e) {
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (Exception e2) {
        				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent()", e2);
        			}
        		}
        		if (Constants.LOGGING) Log.e(TAG, "SubmitLinkTask", e);
        	}
        	return null;
        }
    	
    	@Override
    	public void onPreExecute() {
    		showDialog(Constants.DIALOG_SUBMITTING);
    	}
    	
    	
    	@Override
    	public void onPostExecute(final Uri newlyCreatedThread) {
    		dismissDialog(Constants.DIALOG_SUBMITTING);
    		if (newlyCreatedThread == null) {
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, ShareLinkActivity.this);
    		} else {
        		// Success. Return the subreddit and thread id
    			//open browser with returned Uri
    			DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
    			    @Override
    			    public void onClick(DialogInterface dialog, int which) {
    			        switch (which){
    			        case DialogInterface.BUTTON_POSITIVE:
    			            //Yes button clicked
			    			Uri mobUri = Uri.parse(newlyCreatedThread.toString().replace("proddit", "i.proddit"));
			    			final Intent intent = new Intent(Intent.ACTION_VIEW).setData(mobUri);
			    			startActivity(intent);
    			            break;

    			        case DialogInterface.BUTTON_NEGATIVE:
    			            //No button clicked
    			            break;
    			        }
    			        finish();
    			    }
    			};

    			AlertDialog.Builder builder = new AlertDialog.Builder(ShareLinkActivity.this);
    			builder.setMessage("Deschideti in browser noul link?").setPositiveButton("Da", dialogClickListener)
    			    .setNegativeButton("Nu", dialogClickListener).show();
    			
    		}
    	}
    }
	
	private class MyCaptchaCheckRequiredTask extends CaptchaCheckRequiredTask {
		public MyCaptchaCheckRequiredTask() {
			super(mSubmitUrl, mClient);
		}
		
		@Override
		protected void saveState() {
			ShareLinkActivity.this.mCaptchaIden = _mCaptchaIden;
			ShareLinkActivity.this.mCaptchaUrl = _mCaptchaUrl;
		}

		@Override
		public void onPreExecute() {
			// Hide submit buttons so user can't submit until we know whether he needs captcha
			final Button submitLinkButton = (Button) findViewById(R.id.submit_link_button);
			final Button submitTextButton = (Button) findViewById(R.id.submit_text_button);
			submitLinkButton.setVisibility(View.GONE);
			submitTextButton.setVisibility(View.GONE);
			// Show "loading captcha" label
			final TextView loadingLinkCaptcha = (TextView) findViewById(R.id.submit_link_captcha_loading);
			final TextView loadingTextCaptcha = (TextView) findViewById(R.id.submit_text_captcha_loading);
			loadingLinkCaptcha.setVisibility(View.VISIBLE);
			loadingTextCaptcha.setVisibility(View.VISIBLE);
		}
		
		@Override
		public void onPostExecute(Boolean required) {
			final TextView linkCaptchaLabel = (TextView) findViewById(R.id.submit_link_captcha_label);
			final ImageView linkCaptchaImage = (ImageView) findViewById(R.id.submit_link_captcha_image);
			final EditText linkCaptchaEdit = (EditText) findViewById(R.id.submit_link_captcha);
			final TextView textCaptchaLabel = (TextView) findViewById(R.id.submit_text_captcha_label);
			final ImageView textCaptchaImage = (ImageView) findViewById(R.id.submit_text_captcha_image);
			final EditText textCaptchaEdit = (EditText) findViewById(R.id.submit_text_captcha);
			final TextView loadingLinkCaptcha = (TextView) findViewById(R.id.submit_link_captcha_loading);
			final TextView loadingTextCaptcha = (TextView) findViewById(R.id.submit_text_captcha_loading);
			final Button submitLinkButton = (Button) findViewById(R.id.submit_link_button);
			final Button submitTextButton = (Button) findViewById(R.id.submit_text_button);
			if (required == null) {
				Common.showErrorToast("Error retrieving captcha. Use the menu to try again.", Toast.LENGTH_LONG, ShareLinkActivity.this);
				return;
			}
			if (required) {
				linkCaptchaLabel.setVisibility(View.VISIBLE);
				linkCaptchaImage.setVisibility(View.VISIBLE);
				linkCaptchaEdit.setVisibility(View.VISIBLE);
				textCaptchaLabel.setVisibility(View.VISIBLE);
				textCaptchaImage.setVisibility(View.VISIBLE);
				textCaptchaEdit.setVisibility(View.VISIBLE);
				// Launch a task to download captcha and display it
				new MyCaptchaDownloadTask().execute();
			} else {
				linkCaptchaLabel.setVisibility(View.GONE);
				linkCaptchaImage.setVisibility(View.GONE);
				linkCaptchaEdit.setVisibility(View.GONE);
				textCaptchaLabel.setVisibility(View.GONE);
				textCaptchaImage.setVisibility(View.GONE);
				textCaptchaEdit.setVisibility(View.GONE);
			}
			loadingLinkCaptcha.setVisibility(View.GONE);
			loadingTextCaptcha.setVisibility(View.GONE);
			submitLinkButton.setVisibility(View.VISIBLE);
			submitTextButton.setVisibility(View.VISIBLE);
		}
	}
	
	private class MyCaptchaDownloadTask extends CaptchaDownloadTask {
		public MyCaptchaDownloadTask() {
			super(mCaptchaUrl, mClient);
		}

		@Override
		public void onPostExecute(Drawable captcha) {
			if (captcha == null) {
				Common.showErrorToast("Nu a fost citit captcha-ul. Reincercati cu butonul din meniu.", Toast.LENGTH_LONG, ShareLinkActivity.this);
				return;
			}
			final ImageView linkCaptchaView = (ImageView) findViewById(R.id.submit_link_captcha_image);
			final ImageView textCaptchaView = (ImageView) findViewById(R.id.submit_text_captcha_image);
			linkCaptchaView.setImageDrawable(captcha);
			linkCaptchaView.setVisibility(View.VISIBLE);
			textCaptchaView.setImageDrawable(captcha);
			textCaptchaView.setVisibility(View.VISIBLE);
		}
	}
    
	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		ProgressDialog pdialog;
		switch (id) {
		case Constants.DIALOG_LOGIN:
			dialog = new LoginDialog(this, mSettings, true) {
				@Override
				public void onLoginChosen(String user, String password) {
					dismissDialog(Constants.DIALOG_LOGIN);
    				new MyLoginTask(user, password).execute();
				}
			};
    		break;

       	// "Please wait"
    	case Constants.DIALOG_LOGGING_IN:
    		pdialog = new ProgressDialog(this);
    		pdialog.setMessage("Logare...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(false);
    		dialog = pdialog;
    		break;
		case Constants.DIALOG_SUBMITTING:
			pdialog = new ProgressDialog(this);
    		pdialog.setMessage("Trimitere...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(false);
    		dialog = pdialog;
    		break;
		default:
    		break;
		}
		return dialog;
	}
	
    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
    	super.onPrepareDialog(id, dialog);
    	
    	switch (id) {
    	case Constants.DIALOG_LOGIN:
    		if (mSettings.getUsername() != null) {
	    		final TextView loginUsernameInput = (TextView) dialog.findViewById(R.id.login_username_input);
	    		loginUsernameInput.setText(mSettings.getUsername());
    		}
    		final TextView loginPasswordInput = (TextView) dialog.findViewById(R.id.login_password_input);
    		loginPasswordInput.setText("");
    		break;
    		
		default:
			break;
    	}
    }
	
	private boolean validateLinkForm() {
		final EditText titleText = (EditText) findViewById(R.id.submit_link_title);
		final EditText urlText = (EditText) findViewById(R.id.submit_link_url);
		final EditText redditText = (EditText) findViewById(R.id.submit_link_reddit);
		if (Common.isEmpty(titleText.getText())) {
			Common.showErrorToast("Trebuie introdus un titlu.", Toast.LENGTH_LONG, this);
			return false;
		}
		if (Common.isEmpty(urlText.getText())) {
			Common.showErrorToast("Trebuie introdus un URL.", Toast.LENGTH_LONG, this);
			return false;
		}
		if (Common.isEmpty(redditText.getText())) {
			Common.showErrorToast("Trebuie introdusa o categorie.", Toast.LENGTH_LONG, this);
			return false;
		}
		return true;
	}
	private boolean validateTextForm() {
		final EditText titleText = (EditText) findViewById(R.id.submit_text_title);
		final EditText redditText = (EditText) findViewById(R.id.submit_text_reddit);
		if (Common.isEmpty(titleText.getText())) {
			Common.showErrorToast("Trebuie introdus un titlu.", Toast.LENGTH_LONG, this);
			return false;
		}
		if (Common.isEmpty(redditText.getText())) {
			Common.showErrorToast("Trebuie aleasa o categorie.", Toast.LENGTH_LONG, this);
			return false;
		}
		return true;
	}
	
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.submit_link, menu);

        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	super.onPrepareOptionsMenu(menu);
    	
    	if (mCaptchaUrl == null)
    		menu.findItem(R.id.update_captcha_menu_id).setVisible(false);
    	else
    		menu.findItem(R.id.update_captcha_menu_id).setVisible(true);
    	
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case R.id.pick_subreddit_menu_id:
    		Intent pickSubredditIntent = new Intent(getApplicationContext(), PickSubredditActivity.class);
    		pickSubredditIntent.putExtra(Constants.EXTRA_HIDE_FAKE_SUBREDDITS_STRING, true);
    		startActivityForResult(pickSubredditIntent, Constants.ACTIVITY_PICK_SUBREDDIT);
    		break;
    	case R.id.update_captcha_menu_id:
    		new MyCaptchaCheckRequiredTask().execute();
    		break;
    	default:
    		throw new IllegalArgumentException("Unexpected action value "+item.getItemId());
    	}
    	
    	return true;
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	super.onActivityResult(requestCode, resultCode, intent);
    	
    	switch(requestCode) {
    	case Constants.ACTIVITY_PICK_SUBREDDIT:
    		if (resultCode == Activity.RESULT_OK) {
    		    // Group 1: Subreddit.
    		    final Pattern REDDIT_PATH_PATTERN = Pattern.compile(Constants.REDDIT_PATH_PATTERN_STRING);
    			Matcher redditContextMatcher = REDDIT_PATH_PATTERN.matcher(intent.getData().getPath());
    			if (redditContextMatcher.find()) {
    				String newSubreddit = redditContextMatcher.group(1);
    				final EditText linkSubreddit = (EditText) findViewById(R.id.submit_link_reddit);
	    			final EditText textSubreddit = (EditText) findViewById(R.id.submit_text_reddit);
	    			if (newSubreddit != null) {
	    				linkSubreddit.setText(newSubreddit);
		    			textSubreddit.setText(newSubreddit);
    				} else {
	    				linkSubreddit.setText("proddit.com");
		    			textSubreddit.setText("proddit.com");
    				}
	    		}
    		}
    		break;
    	default:
    		break;
    	}
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle state) {
    	super.onRestoreInstanceState(state);
        final int[] myDialogs = {
        	Constants.DIALOG_LOGGING_IN,
        	Constants.DIALOG_LOGIN,
        	Constants.DIALOG_SUBMITTING,
        };
        for (int dialog : myDialogs) {
	        try {
	        	dismissDialog(dialog);
		    } catch (IllegalArgumentException e) {
		    	// Ignore.
		    }
        }
    }
}
