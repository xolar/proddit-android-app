package com.proddit;

import android.app.Activity;

public class Constants {
	public static final String COMMENT_PATH_PATTERN_STRING
	= "(?:/r/([^/]+)/comments|/comments|/tb)/([^/]+)(?:/?$|/[^/]+/([a-zA-Z0-9]+)?)?";
	
	public static final String USER_AGENT_STRING = "proddit (Android)";
    public static final String TAB_LINK = "tab_link";
    public static final String TAB_TEXT = "tab_text";
    public static final String REDDIT_BASE_URL = "http://proddit.com";
    public static final String SUBMIT_KIND_LINK = "link";
    public static final boolean LOGGING = true; 
    public static final String SUBMIT_KIND_SELF = "self";
    public static final int RESULT_LOGIN_REQUIRED = Activity.RESULT_FIRST_USER;
    public static final String MODHASH_URL = REDDIT_BASE_URL + "/r";
    public static final int DIALOG_LOGIN = 2;
    public static final int DIALOG_LOGGING_IN = 1000;
    public static final int DIALOG_SUBMITTING = 1004;
	public static final int DIALOG_LOADING_REDDITS_LIST = 1006;
	
	public static final String EXTRA_HIDE_FAKE_SUBREDDITS_STRING = "hideFakeSubreddits";

	public static final boolean USE_SUBREDDITS_CACHE = true;
    public static final int ACTIVITY_PICK_SUBREDDIT = 0;



	public static final String REDDIT_PATH_PATTERN_STRING = "(?:/r/([^/]+))?/?$";
    public static final String JSON_ERRORS = "errors";
    public static final String JSON_MODHASH = "modhash";
} 

