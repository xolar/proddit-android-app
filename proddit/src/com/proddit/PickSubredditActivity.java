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
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieSyncManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public final class PickSubredditActivity extends ListActivity {
	
	private static final String TAG = "PickSubredditActivity";
	
	// Group 1: inner
//    private final Pattern MY_SUBREDDITS_OUTER = Pattern.compile("PRODDITURILE TALE DE PRIM*?<ul>(.*?)</ul>", Pattern.CASE_INSENSITIVE);
    // Group 3: subreddit name. Repeat the matcher.find() until it fails.
    private final Pattern MY_SUBREDDITS_INNER = Pattern.compile("<a(.*?)/r/(.*?)>(.+?)</a>");

	private ProdditSettings mSettings = new ProdditSettings();
	private HttpClient mClient = Common.getGzipHttpClient();
	
	private PickSubredditAdapter mSubredditsAdapter;
	private ArrayList<String> mSubredditsList;
	private static final Object ADAPTER_LOCK = new Object();
	private EditText mEt;
	
    private AsyncTask<?, ?, ?> mCurrentTask = null;
    private final Object mCurrentTaskLock = new Object();
	
    public static final String[] DEFAULT_SUBREDDITS = {
    	"proddit.com",
    	"poze",
    	"politica",
    	"cpm",
    	"amuzant",
    	"tehnologie",
    	"siteuripraf",
    	"stiinta",
    	"programare",
    	"secularism",
    	"international",
    	"comics",
    	"pevremeamea",
    	"video",
    	"divertisment",
    	"intrebari",
    	"economie",
    	"cui",
    	"aziamaflat",
    	"culturagenerala",
    };
    
    // A list of special subreddits that can be viewed, but cannot be used for submissions. They inherit from the FakeSubreddit class
    // in the redditdev source, so we use the same naming here. Note: Should we add r/Random and r/Friends?
    public static final String[] FAKE_SUBREDDITS = {
    	"tot"    	
	};
    


    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        
		CookieSyncManager.createInstance(getApplicationContext());
		
		mSettings.loadRedditPreferences(this, mClient);
    	requestWindowFeature(Window.FEATURE_PROGRESS);
    	requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    	
    	resetUI(null);
        
    	mSubredditsList = cacheSubredditsList(mSubredditsList);
    	
        if (Common.isEmpty(mSubredditsList))
            restoreLastNonConfigurationInstance();
        
        if (Common.isEmpty(mSubredditsList)) {
        	new DownloadRedditsTask().execute();
        }
        else {
	        addFakeSubredditsUnlessSuppressed();
	        resetUI(new PickSubredditAdapter(this, mSubredditsList));
        }
    }
    
    @Override
    public void onResume() {
    	super.onResume();
		CookieSyncManager.getInstance().startSync();
    }
    
    @Override
    public void onPause() {
    	super.onPause();
		CookieSyncManager.getInstance().stopSync();
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        // Avoid having to re-download and re-parse the subreddits list
    	// when rotating or opening keyboard.
    	return mSubredditsList;
    }
    
    @SuppressWarnings("unchecked")
	private void restoreLastNonConfigurationInstance() {
    	mSubredditsList = (ArrayList<String>) getLastNonConfigurationInstance();
    }

    void resetUI(PickSubredditAdapter adapter) {
        registerForContextMenu(getListView());
        setContentView(R.layout.pick_subreddit_view);

    	synchronized (ADAPTER_LOCK) {
	    	if (adapter == null) {
	            // Reset the list to be empty.
		    	mSubredditsList = new ArrayList<String>();
		    	mSubredditsAdapter = new PickSubredditAdapter(this, mSubredditsList);
	    	} else {
	    		mSubredditsAdapter = adapter;
	    	}
		    setListAdapter(mSubredditsAdapter);
		    mSubredditsAdapter.mLoading = false;
		    mSubredditsAdapter.notifyDataSetChanged();  // Just in case
		}
	    
        // Set the EditText to do same thing as onListItemClick
        mEt = (EditText) findViewById(R.id.pick_subreddit_input);
        if (mEt != null) {
			mEt.setOnKeyListener(new OnKeyListener() {
				public boolean onKey(View v, int keyCode, KeyEvent event) {
			        if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
			        	returnSubreddit(mEt.getText().toString().trim());
			        	return true;
			        }
			        return false;
			    }
			});
	        mEt.setFocusableInTouchMode(true);
        }
        Button goButton = (Button) findViewById(R.id.pick_subreddit_button);
        if (goButton != null) {
	        goButton.setOnClickListener(new OnClickListener() {
	        	public void onClick(View v) {
	        		returnSubreddit(mEt.getText().toString().trim());
	        	}
	        });
        }
        
        getListView().requestFocus();
    }
    
        
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String item = mSubredditsAdapter.getItem(position);
        returnSubreddit(item);
    }
    
    private void returnSubreddit(String subreddit) {
       	Intent intent = new Intent();
       	intent.setData(Common.createSubredditUri(subreddit));
       	setResult(RESULT_OK, intent);
       	finish();	
    }
    
    private void enableLoadingScreen() {
		setContentView(R.layout.loading_light);
    	synchronized (ADAPTER_LOCK) {
	    	if (mSubredditsAdapter != null)
	    		mSubredditsAdapter.mLoading = true;
    	}
    	getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 0);
    }
    
    private void disableLoadingScreen() {
    	resetUI(mSubredditsAdapter);
    	getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 10000);
    }
    
    class DownloadRedditsTask extends AsyncTask<Void, Void, ArrayList<String>> {
    	@Override
    	public ArrayList<String> doInBackground(Void... voidz) {
    		ArrayList<String> reddits = null;
    		HttpEntity entity = null;
            try {
            	
            	reddits = cacheSubredditsList(reddits);
            	
            	if (reddits == null) {
            		reddits = new ArrayList<String>();
            		
	            	HttpGet request = new HttpGet(Constants.REDDIT_BASE_URL + "/proddits");
	            	// Set timeout to 15 seconds
	                HttpParams params = request.getParams();
	    	        HttpConnectionParams.setConnectionTimeout(params, 15000);
	    	        HttpConnectionParams.setSoTimeout(params, 15000);
	    	        
	    	        HttpResponse response = mClient.execute(request);
	            	entity = response.getEntity();
	            	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
	                
	            	StringBuffer sb = new StringBuffer();
	            	String line = "";
	            	while((line=in.readLine())!=null) sb.append(line);
	                in.close();
	                entity.consumeContent();
	                String lineStr = sb.toString();
	                
//	                Matcher outer = MY_SUBREDDITS_OUTER.matcher(lineStr);
//	                if (outer.find()) {
	                	Matcher inner = MY_SUBREDDITS_INNER.matcher(lineStr);
	                	while (inner.find()) {
	                		reddits.add(inner.group(3));
	                	}
//	                } else {
//	                	return null;
//	                }
	                
	                if (Constants.LOGGING) Log.d(TAG, "new subreddit list size: " + reddits.size());
	                
            	}
                
                return reddits;
                
            } catch (Exception e) {
            	if (Constants.LOGGING) Log.e(TAG, "failed", e);
                if (entity != null) {
	                try {
	                	entity.consumeContent();
	                } catch (Exception e2) {
	                	// Ignore.
	                }
                }
            }
            return null;
	    }
    	
    	@Override
    	public void onPreExecute() {
    		synchronized (mCurrentTaskLock) {
	    		if (mCurrentTask != null) {
	    			this.cancel(true);
	    			return;
	    		}
    			mCurrentTask = this;
    		}
    		resetUI(null);
    		enableLoadingScreen();
    	}
    	
    	@Override
    	public void onPostExecute(ArrayList<String> reddits) {
    		synchronized (mCurrentTaskLock) {
    			mCurrentTask = null;
    		}
    		disableLoadingScreen();
			
    		if (reddits == null || reddits.size() == 0) {
    			// Need to make a copy because Arrays.asList returns List backed by original array
    	        mSubredditsList = new ArrayList<String>();
    	        mSubredditsList.addAll(Arrays.asList(DEFAULT_SUBREDDITS));
    		} else {
    			mSubredditsList = reddits;
    		}
    		addFakeSubredditsUnlessSuppressed();
	        resetUI(new PickSubredditAdapter(PickSubredditActivity.this, mSubredditsList));
    	}
    }
    
    private void addFakeSubredditsUnlessSuppressed() {
	    // Insert special reddits (front page, all) into subreddits list, unless suppressed by Intent extras
		Bundle extras = getIntent().getExtras();
		boolean addFakeSubreddits = false;
	    if (extras != null) {
        	boolean shouldHideFakeSubreddits = extras.getBoolean(Constants.EXTRA_HIDE_FAKE_SUBREDDITS_STRING, false);
        	if (!shouldHideFakeSubreddits)
        	{
        		addFakeSubreddits = true;
        	}
        } else {    		
        	addFakeSubreddits = true;    			    		
        }
	    if (addFakeSubreddits)
	    {
	    	mSubredditsList.addAll(0, Arrays.asList(FAKE_SUBREDDITS));    		
	    }
    }

    private final class PickSubredditAdapter extends ArrayAdapter<String> {
    	private LayoutInflater mInflater;
        private boolean mLoading = true;
        private int mFrequentSeparatorPos = ListView.INVALID_POSITION;

        
        public PickSubredditAdapter(Context context, List<String> objects) {
            super(context, 0, objects);
            
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public boolean isEmpty() {
            if (mLoading) {
                // We don't want the empty state to show when loading.
                return false;
            } else {
                return super.isEmpty();
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == mFrequentSeparatorPos) {
                // We don't want the separator view to be recycled.
                return IGNORE_ITEM_VIEW_TYPE;
            }
            return super.getItemViewType(position);
        }

        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;

            // Here view may be passed in for re-use, or we make a new one.
            if (convertView == null) {
                view = mInflater.inflate(android.R.layout.simple_list_item_1, null);
            } else {
                view = convertView;
            }
                        
            TextView text = (TextView) view.findViewById(android.R.id.text1);
            text.setText(mSubredditsAdapter.getItem(position));
            
            return view;
        }
    }
    
    protected Dialog onCreateDialog(int id) {
    	Dialog dialog;
    	ProgressDialog pdialog;
    	
    	switch (id) {
	    	// "Please wait"
		case Constants.DIALOG_LOADING_REDDITS_LIST:
			pdialog = new ProgressDialog(this);
			pdialog.setMessage("Loading your reddits...");
			pdialog.setIndeterminate(true);
			pdialog.setCancelable(false);
			dialog = pdialog;
			break;
		default:
			throw new IllegalArgumentException("Unexpected dialog id "+id);
    	}
    	return dialog;
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle state) {
    	super.onRestoreInstanceState(state);
        final int[] myDialogs = {
        	Constants.DIALOG_LOADING_REDDITS_LIST,
        };
        for (int dialog : myDialogs) {
	        try {
	        	dismissDialog(dialog);
		    } catch (IllegalArgumentException e) {
		    	// Ignore.
		    }
        }
    }
    
    protected ArrayList<String> cacheSubredditsList(ArrayList<String> reddits){
		return reddits;
    }
}
