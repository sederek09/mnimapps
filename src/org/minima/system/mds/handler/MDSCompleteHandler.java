package org.minima.system.mds.handler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Date;
import java.util.StringTokenizer;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import org.minima.objects.base.MiniString;
import org.minima.system.mds.MDSManager;
import org.minima.system.mds.polling.PollStack;
import org.minima.utils.MinimaLogger;
import org.minima.utils.json.JSONObject;

/**
 * This class handles a single request then exits
 * 
 * @author spartacusrex
 *
 */
public class MDSCompleteHandler implements Runnable {

	/**
	 * The Net Socket
	 */
	Socket mSocket;
	
	/**
	 * The MDS Manager
	 */
	MDSManager mMDS;
	
	/**
	 * The POLL Stack
	 */
	PollStack mPollStack;
	
	/**
	 * Main Constructor
	 * @param zSocket
	 */
	public MDSCompleteHandler(Socket zSocket, MDSManager zMDS, PollStack zPollStack) {
		//Store..
		mSocket 	= zSocket;
		mMDS		= zMDS;
		mPollStack	= zPollStack;
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
			
			//Get the command / params only
			int index 		= fileRequested.indexOf("?");
			String command 	= fileRequested.substring(0,index);
			String params 	= fileRequested.substring(index+1);
			
			//Get the UID
			String uid = "";
			StringTokenizer strtok = new StringTokenizer(params,"&");
			while(strtok.hasMoreElements()) {
				String tok = strtok.nextToken();
				
				index 			= tok.indexOf("=");
				String param 	= tok.substring(0,index);
				String value 	= tok.substring(index+1,tok.length());
				
				if(param.equals("uid")) {
					uid=value;
				}
			}
			
			//Get the Headers..
			int contentlength = 0;
			while(input != null && !input.trim().equals("")) {
				//MinimaLogger.log("RPC : "+input);
				int ref = input.indexOf("Content-Length:"); 
				if(ref != -1) {
					//Get it..
					int start     = input.indexOf(":");
					contentlength = Integer.parseInt(input.substring(start+1).trim());
				}	
				input = in.readLine();
			}
			
			//Is it a POST request
			if(!method.equals("POST") || uid.equals("")) {
				
				//Not a valid request
				throw new Exception("Invalid request no UID or not POST");
				
			}else{
				
				//How much data
				char[] cbuf 	= new char[contentlength];
				
				//Read it all in
				//Read it ALL in
				int len,total=0;
				while( (len = in.read(cbuf,total,contentlength-total)) != -1) {
					total += len;
					if(total == contentlength) {
						break;
					}
				}
				
				//Set this..
				String data = new String(cbuf);
				
				String result = null;
				if(command.equals("sql")) {
				
					SQLcommand sql = new SQLcommand(mMDS);
					result = sql.runCommand(uid, data);
					
				}else if(command.equals("cmd")) {
					
					CMDcommand cmd = new CMDcommand();
					result = cmd.runCommand(uid, data);
					
				}else if(command.equals("poll")) {
					
					POLLcommand poll = new POLLcommand(mPollStack);
					result = poll.runCommand(uid, data);
					
				}else{
					
					//Is it a CMD / SQL / FILE / FUNC ..
					MinimaLogger.log("ERROR COMPLETE FILE REQ : "+command+" "+params);
					
					//Invalid command
					JSONObject error = new JSONObject();
					error.put("status", false);
				}
				
				
				//Calculate the size of the response
				int finallength = result.getBytes(MiniString.MINIMA_CHARSET).length;
				
				// send HTTP Headers
				out.println("HTTP/1.1 200 OK");
				out.println("Server: HTTP SQL Server from Minima : 1.3");
				out.println("Date: " + new Date());
				out.println("Content-type: text/plain");
				out.println("Content-length: " + finallength);
				out.println("Access-Control-Allow-Origin: *");
				out.println(); // blank line between headers and content, very important !
				out.println(result);
				out.flush(); // flush character output stream buffer
			}
			
		}catch(SSLHandshakeException exc) {
		}catch(SSLException exc) {
		} catch (Exception ioe) {
			MinimaLogger.log(ioe);
			
			// send HTTP Headers
			out.println("HTTP/1.1 500 OK");
			out.println("Server: HTTP RPC Server from Minima : 1.3");
			out.println("Date: " + new Date());
			out.println("Content-type: text/plain");
			out.println("Access-Control-Allow-Origin: *");
			out.println(); // blank line between headers and content, very important !
			out.flush(); // flush character output stream buffer
			
			
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