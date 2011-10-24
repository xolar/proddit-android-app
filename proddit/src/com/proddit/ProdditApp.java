package com.proddit;

import android.app.Application;

public class ProdditApp extends Application {
	private static ProdditApp application;
	
	public ProdditApp(){
		application = this;
	}
	
	public static ProdditApp getApplication(){
		return application;
	}
}
