package com.proddit;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

/**
 * The dialog created when the user selects "Login" from the menu.
 */
public abstract class LoginDialog extends Dialog {
	private final Activity mActivity;
	private final ProdditSettings mSettings;
	private final EditText loginUsernameInput;
	private final EditText loginPasswordInput;

	public LoginDialog(final Activity activity, ProdditSettings settings, boolean finishActivityIfCanceled) {
		super(activity);
		mActivity = activity;
		mSettings = settings;
		
		setContentView(R.layout.login_dialog);
		setTitle("Login to proddit.com");
		if (finishActivityIfCanceled) {
			// If user presses "back" then quit.
			setOnCancelListener(new OnCancelListener() {
				public void onCancel(DialogInterface d) {
					if (!mSettings.isLoggedIn()) {
						mActivity.setResult(Activity.RESULT_CANCELED);
						mActivity.finish();
					}
				}
			});
		}
		
		loginUsernameInput = (EditText) findViewById(R.id.login_username_input);
		loginUsernameInput.setOnKeyListener(new View.OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
		        if ((event.getAction() == KeyEvent.ACTION_DOWN)
		        		&& (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_TAB)) {
		        	loginPasswordInput.requestFocus();
		        	return true;
		        }
		        return false;
		    }
		});
		
		loginPasswordInput = (EditText) findViewById(R.id.login_password_input);
		loginPasswordInput.setOnKeyListener(new View.OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
		        if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
    				handleLoginChosen();
		        	return true;
		        }
		        return false;
		    }
		});
		
		final Button loginButton = (Button) findViewById(R.id.login_button);
		loginButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				handleLoginChosen();
		    }
		});
	}
	
	private void handleLoginChosen() {
		String user = loginUsernameInput.getText().toString().trim();
		String password = loginPasswordInput.getText().toString();
		onLoginChosen(user, password);
	}
	
	/**
	 * Called when the login button is clicked
	 * or when enter is pressed on the password field. 
	 */
	public abstract void onLoginChosen(String user, String password);

}
