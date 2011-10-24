package com.proddit;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

public class UploadImgurTask extends AsyncTask<Void, Integer, Uri> {
	InputStream imageInputStream;
	//please change
	private final static String DEV_KEY = "4cca07498f82a4d12780840f3e26c158";
    private final Pattern IMGUR_RESP_URL_PATTERN = Pattern.compile("<original>(.+?)</original>");
	
	public UploadImgurTask(InputStream is){
		this.imageInputStream = is;
	}
	@Override
	protected Uri doInBackground(Void... params) {
//		if(true) return Uri.parse("http://proddit.com");
        BufferedInputStream buf;
        String data = null;
        try {
            buf = new BufferedInputStream(imageInputStream);
	        
	        ByteArrayOutputStream bos = new ByteArrayOutputStream();
	        	        	
	        byte[] buff = new byte[32 * 1024];
	        int len;
	        while ((len = buf.read(buff)) > 0)
	          bos.write(buff, 0, len);
	        
	        data = Base64.encodeToString(bos.toByteArray(), false);
	        if (imageInputStream != null) 
	        	imageInputStream.close();
	        if (buf != null) 
	        	buf.close();
	
        } catch (Exception e) {
            Log.e("Error reading file", e.toString());
        }
		HttpPost hpost = new HttpPost("http://api.imgur.com/2/upload");

		List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
		nameValuePairs.add(new BasicNameValuePair("image", data));
		nameValuePairs.add(new BasicNameValuePair("key", DEV_KEY));
		
        HttpParams hparams = hpost.getParams();
        hparams.setParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, Boolean.FALSE);
        
		try 
		{
			hpost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
		} catch (UnsupportedEncodingException e) 
		{
			Log.e("Error posting",e.toString());
		}
		DefaultHttpClient client = new DefaultHttpClient();
		HttpResponse resp = null;
		try 
		{
			resp = client.execute(hpost);
			String content;
			Log.d("Uploaded to imgur",content=EntityUtils.toString(resp.getEntity()));
        	Matcher inner = IMGUR_RESP_URL_PATTERN.matcher(content);
        	if(inner.find()) 
					return Uri.parse(inner.group(1));
            	else return null;
		} catch (ClientProtocolException e) 
		{
			Log.e("Error posting",e.toString());
		} catch (IOException e) 
		{
			Log.e("Error posting",e.toString());
		}
		return null;
	}

}
