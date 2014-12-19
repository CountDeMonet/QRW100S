QRW100S
=======

High Performance MJPEG viewer with support for Google Glass. Built to run against a Walkera QRW100S Quadcopter (iOS version)

To enable support for Google Glass set this parameter to true in MainActivity.java

private final boolean forGlass = false;

and set the compile SDK in build.gradle to

compileSdkVersion 'Google Inc.:Glass Development Kit Preview:19'
	
To build for standard android set forGlass to false and set the sdk version to the latest android sdk, at the moment 21.
 
The forGlass parameter allows the network interface to throw away frames and also slows down the UI refresh. Glass is unable to handle a high framerate. The original QRW100S only supports iOS officailly and can send upwards of 50fps to Android and glass can not handle this at all. If this parameter is set to false the code tries to maintain a high fps.

The url for the motion jpeg streamer is set in the code at the moment. There is no UI to set it. In my case I'm using a a Walkera QRW100S quadcopter so you must be connected to the walkera wifi access point when you start up this application. The URL for the Walkera mjpeg streamer once connected to the access point is:

private final String URL = "http://192.168.10.123:7060/?action=stream";

You can set this to and ipcamera or whatever other mjpeg source you may have.

