/*
 * Copyright (C) 2014 Vine Ripe Consulting LLC.
 * http://www.vineripeconsulting.com/
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vrs.qrw100s;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.URI;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

class NetworkReader extends Thread{
	private boolean _runThread = true; 
	private static final String TAG = "NetworkReader";
	private final Handler mainHandler;

	private byte[] curFrame;

    private final String myURL;
	
	private boolean skipFrame = false;
	private int skipNum = 0;
	private int frameDecrement = 1;
	
	// this udp server only receives messages. 
	public NetworkReader( Handler myHandler, String url, boolean isGlass ){
		mainHandler = myHandler;
		myURL = url;
		curFrame = new byte[0];
		if( isGlass ){
			frameDecrement = 6;
		}
	}

    // reads mjpeg from the network returning valid frame data to the calling thread
	@Override
	public void run() {
		Thread.currentThread().setName("Network Reader");

		HttpResponse res;
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpParams httpParams = httpclient.getParams();
		HttpConnectionParams.setConnectionTimeout(httpParams, 5 * 1000);
		HttpConnectionParams.setSoTimeout(httpParams, 10 * 1000);

		Log.d(TAG, "1. Sending http request");
		try {
			res = httpclient.execute(new HttpGet(URI.create(myURL)));
			Log.d(TAG, "2. Request finished, status = " + res.getStatusLine().getStatusCode());
			if (res.getStatusLine().getStatusCode() == 401) {
				return;
			}

			DataInputStream bis = new DataInputStream(res.getEntity().getContent());
            ByteArrayOutputStream jpgOut = new ByteArrayOutputStream(10000);
						
			int prev = 0;
			int cur;

			while ( (cur = bis.read()) >= 0 && _runThread ) 
			{
				if (prev == 0xFF && cur == 0xD8) {
					// reset the output stream
					if( !skipFrame ){
						jpgOut.reset();
						jpgOut.write((byte) prev);
					}
				}
				
				if( !skipFrame ){
					if (jpgOut != null) {
						jpgOut.write((byte) cur);
					}
				}
				
				if (prev == 0xFF && cur == 0xD9) {		
					if( !skipFrame ){
                        synchronized (curFrame) {
                            curFrame = jpgOut.toByteArray();
                        }

						skipFrame = true;
						
						Message threadMessage = mainHandler.obtainMessage();
						threadMessage.obj = curFrame;
						mainHandler.sendMessage(threadMessage);
					}else{
						if( skipNum < frameDecrement ){
							skipNum++;
						}else{
							skipNum = 0;
							skipFrame = false;
						}
					}
					
				}
				prev = cur;
			}
		} catch (ClientProtocolException e) {
			Log.d(TAG, "Request failed-ClientProtocolException", e);
		} catch (IOException e) {
			Log.d(TAG, "Request failed-IOException", e);
		}

    }

	public void ShutDown(){
		_runThread = false;
	}

}


