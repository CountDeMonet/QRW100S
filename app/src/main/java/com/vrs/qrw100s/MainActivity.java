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

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;

public class MainActivity extends Activity {
    // to benchmark the network and display FPS instead of frames set to true and recompile
	private final boolean benchmarkNetwork = false;

    // To enable support for Google Glass set this parameter to true and set the compile SDK to
    // compileSdkVersion 'Google Inc.:Glass Development Kit Preview:19'
    //
    // This parameter allows the network interface to throw away frames
    // and also slows down the refresh. The original QRW100S can send upwards of
    // 50fps and glass can not handle this at all.
	private final boolean forGlass = false;

    // url for the motion jpeg streamer. In my case a Walkera QRW100S quadcopter
    // You must be connected to the walkera wifi access point to run this code.
    private final String URL = "http://192.168.10.123:7060/?action=stream";
	private boolean suspending = false;

	private CameraSurface mySurfaceView;
	private NetworkReader myNetworkReader;

	private int frameDelay = 0;
	private long frame_decimator = 0;
	private boolean hasNewFrame = false;
	private Bitmap processedFrame;

	private TextView myFPSDisplay;
	private TextView myFrameDisplay;

	private int myCurrentFrame=0;
	private int frameCounter = 0;
	private long start;

    private final BitmapFactory.Options myOptions = new BitmapFactory.Options();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// make sure we keep the screen on while doing this thing
		this.getWindow().addFlags(
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // setup the bitmap options for the device
		if( forGlass ){
			frameDelay = 100;
			myOptions.inSampleSize = 4;
		}else{
			myOptions.inSampleSize = 1;
		}

        // if benchmark network setup the text areas for display
		if( benchmarkNetwork ){
			setContentView(R.layout.activity_main);

			myFPSDisplay= (TextView) findViewById(R.id.txtFPS);
			myFPSDisplay.setText("");
		
			myFrameDisplay= (TextView) findViewById(R.id.txtFrames);
			myFrameDisplay.setText("");
		}else{
			mySurfaceView = new CameraSurface(this);
			setContentView(mySurfaceView);
		}

        // initialize the network thread and start the image renderer
		startPlayback();
	}

	private final Handler incomingNetworkMessageHandler = new Handler(new Handler.Callback() {
		@Override
		public boolean handleMessage(Message msg) {
			if((System.currentTimeMillis() - frame_decimator) >= frameDelay) { // 10 fps
				frame_decimator = System.currentTimeMillis();

				if( benchmarkNetwork ){
					frameCounter++;
		            if((System.currentTimeMillis() - start) >= 1000) {
                        String fps = String.valueOf(frameCounter) + "fps";
		                frameCounter = 0; 
		                start = System.currentTimeMillis();
		                myFPSDisplay.setText(fps);
		            }
	
					myCurrentFrame++;
					myFrameDisplay.setText(String.valueOf(myCurrentFrame));
				}else{
					new JpegEncoderTask().execute((byte[])msg.obj);
				}
			}
			return true;
		}
	});

	// this is the fastest way I have found to decode the jpeg from the network that works
    // with GLASS. I tried several NDK options but GLASS appears to have performance issues
    // when passing data between C++ and Java. A pure java solution needed to be found that
    // was as high performance as possible.
	private class JpegEncoderTask extends AsyncTask<byte[], Void, Bitmap> {		
		protected Bitmap doInBackground(byte[]... jpegData) {
			return BitmapFactory.decodeByteArray(jpegData[0], 0, jpegData[0].length, null);
		}

		protected void onPostExecute(Bitmap result) {
			processedFrame = result;
			hasNewFrame = true;
		}
	}
	
	public void onPause() {
		super.onPause();
		stopPlayback();
	}

	public void onResume() {
		super.onResume();
		resumePlayback();
	}

	public void onStart() {
		super.onStart();
	}

	public void onStop() {
		super.onStop();
	}

	public void onDestroy() {
		super.onDestroy();
	}

	void startPlayback() {
		start = System.currentTimeMillis();
		frame_decimator = System.currentTimeMillis();
		myNetworkReader = new NetworkReader(incomingNetworkMessageHandler, URL, forGlass);
		myNetworkReader.start();
		if( !benchmarkNetwork ){
			mySurfaceView.onResumeMySurfaceView();
		}
	}

	void resumePlayback() {
		if (suspending) {
			start = System.currentTimeMillis();
			frame_decimator = System.currentTimeMillis();
			suspending = false;
			myNetworkReader = new NetworkReader(incomingNetworkMessageHandler, URL, forGlass);
			myNetworkReader.start();
			if( !benchmarkNetwork ){
				mySurfaceView.onResumeMySurfaceView();
			}
		}
	}

	void stopPlayback() {
		suspending = true;
		if( !benchmarkNetwork ){
			mySurfaceView.onPauseMySurfaceView();
		}
		myNetworkReader.ShutDown();
	}

	class CameraSurface extends SurfaceView implements Runnable {
		Thread thread = null;
		final SurfaceHolder surfaceHolder;
		volatile boolean running = false;
		private int dispWidth;
		private int dispHeight;
		private Rect destRect = null;

		public Rect destRect(int bmw, int bmh) {
			int tempx;
			int tempy;

			getScreenSize();

			float bmasp = (float) bmw / (float) bmh;
			bmw = dispWidth;
			bmh = (int) (dispWidth / bmasp);
			if (bmh > dispHeight) {
				bmh = dispHeight;
				bmw = (int) (dispHeight * bmasp);
			}
			tempx = (dispWidth / 2) - (bmw / 2);
			tempy = (dispHeight / 2) - (bmh / 2);
			return new Rect(tempx, tempy, bmw + tempx, bmh + tempy);
		}

		public CameraSurface(Context context) {
			super(context);
			surfaceHolder = getHolder();

		}

		public void getScreenSize(){
			destRect = null; // force rect to be updated			
			dispWidth = surfaceHolder.getSurfaceFrame().width();
			dispHeight = surfaceHolder.getSurfaceFrame().height();
		}

		public void onResumeMySurfaceView() {
			running = true;
			thread = new Thread(this);
			thread.setName("Display Thread");

			thread.start();
		}

		public void onPauseMySurfaceView() {
			boolean retry = true;
			running = false;
			while (retry) {
				try {
					thread.join();
					retry = false;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		@Override
		public void run() {
			while (running) {
				if (surfaceHolder.getSurface().isValid()) {
					if( hasNewFrame && processedFrame != null ){
						hasNewFrame = false;
						Canvas canvas = surfaceHolder.lockCanvas();
						if( destRect == null  ){
							destRect = destRect(processedFrame.getWidth(), processedFrame.getHeight());
						}
						canvas.drawBitmap(processedFrame, null, destRect, null);
						surfaceHolder.unlockCanvasAndPost(canvas);
					}
					
					if(forGlass){
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
	}
}
