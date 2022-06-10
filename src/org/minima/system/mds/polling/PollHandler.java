package org.minima.system.mds.polling;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Date;
import java.util.StringTokenizer;

import org.minima.objects.base.MiniString;
import org.minima.utils.MinimaLogger;
import org.minima.utils.json.JSONObject;

/**
 * This class handles a single request then exits
 * 
 * @author spartacusrex
 *
 */
public class PollHandler implements Runnable {

	/**
	 * The Net Socket
	 */
	Socket mSocket;
	
	PollStack mPollStack;
	
	/**
	 * Main Constructor
	 * @param zSocket
	 */
	public PollHandler(PollStack zStack, Socket zSocket) {
		//Store..
		mPollStack 	= zStack;
		mSocket = zSocket;
	}

	@Override
	public void run() {
		// we manage our particular client connection
		BufferedReader in 	 		 	= null; 
		PrintWriter out 	 			= null; 
		String firstline = "no first line..";
		
		try {
			// Input Stream
			in = new BufferedReader(new InputStreamReader(mSocket.getInputStream(), MiniString.MINIMA_CHARSET));
			
			// Output Stream
			out = new PrintWriter(new OutputStreamWriter(mSocket.getOutputStream(), MiniString.MINIMA_CHARSET));
			
			// get first line of the request from the client
			String input = in.readLine();
			if (input == null){
				input = "";
			}
			
			//Get the first line..
			firstline = new String(input);
			
			// we parse the request with a string tokenizer
			StringTokenizer parse = new StringTokenizer(input);
			String method = parse.nextToken().toUpperCase(); // we get the HTTP method of the client
			
			// we get file requested
			String fileRequested = parse.nextToken();
			
			//Remove slashes..
			if(fileRequested.startsWith("/")) {
				fileRequested = fileRequested.substring(1);
			}
			if(fileRequested.endsWith("/")) {
				fileRequested = fileRequested.substring(0,fileRequested.length()-1);
			}
			
			//And finally URL decode..
			fileRequested = URLDecoder.decode(fileRequested,"UTF-8").trim();
			
			//MinimaLogger.log(fileRequested);
			
			//Get the series and counter
			int index 			= fileRequested.indexOf("&");
			String fullseries 	= fileRequested.substring(0,index);
			index 				= fullseries.indexOf("=");
			String series		= fullseries.substring(index+1);
			String fullcounter 	= fileRequested.substring(index+1);
			index 				= fullcounter.indexOf("=");
			String strcounter	= fullcounter.substring(index+1);
			int counter 		= Integer.parseInt(strcounter);
			
			//The returned data..
			JSONObject res = new JSONObject();
			res.put("series", mPollStack.getSeries());
			res.put("counter", mPollStack.getCounter());
			res.put("status", false);
			
			//Are we on the correct series.. 
			if(mPollStack.getSeries().equals(series)) {
				
				int clocksecs 	= 0;
				PollMessage msg = null;
				while(msg == null && clocksecs<60) {
					//Get the message..
					msg = mPollStack.getMessage(counter);
					if(msg !=null) {
						break;
					}
					
					//Wait 1 second and try again 
					Thread.sleep(1000);
					clocksecs++;
				}
				
				//Did we get a message
				if(msg != null) {
					res.put("status", true);
					res.put("response", msg.toJSON());
				}
			}
			
			//Put the latest counter
			res.put("counter", mPollStack.getCounter());
			
			//Do we have any new messages..
			String result = res.toJSONString();
	    	
			//Calculate the size of the response
			int finallength = result.getBytes(MiniString.MINIMA_CHARSET).length; 
			
			// send HTTP Headers
			out.println("HTTP/1.1 200 OK");
			out.println("Server: HTTP RPC Server from Minima : 1.3");
			out.println("Date: " + new Date());
			out.println("Content-type: text/plain");
			out.println("Content-length: " + finallength);
			out.println("Access-Control-Allow-Origin: *");
			out.println(); // blank line between headers and content, very important !
			out.println(result);
			out.flush(); // flush character output stream buffer
			
		} catch (Exception ioe) {
			MinimaLogger.log("POLLHANDLER : "+ioe+" "+firstline);
			
		} finally {
			try {
				in.close();
				out.close();
				mSocket.close(); // we close socket connection
			} catch (Exception e) {
				MinimaLogger.log(e);
			} 	
		}	
	}
}