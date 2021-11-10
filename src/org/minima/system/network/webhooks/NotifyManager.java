package org.minima.system.network.webhooks;

import java.util.ArrayList;

import org.minima.utils.MinimaLogger;
import org.minima.utils.RPCClient;
import org.minima.utils.json.JSONObject;
import org.minima.utils.messages.Message;
import org.minima.utils.messages.MessageProcessor;

public class NotifyManager extends MessageProcessor {

	public static final String NOTIFY_INIT 		= "NOTIFY_INIT";
	public static final String NOTIFY_SHUTDOWN 	= "NOTIFY_SHUTDOWN";
	
	public static final String NOTIFY_ADDHOOK 	= "NOTIFY_ADDHOOK";
	public static final String NOTIFY_POST 		= "NOTIFY_POST";
	
	ArrayList<String> mHooks;
	
	public NotifyManager() {
		super("NOTIFYMANAGER");
		
		mHooks = new ArrayList<>();
		
		PostMessage(NOTIFY_INIT);
	}

	/**
	 * Post an event to all the listeners
	 */
	public void PostEvent(JSONObject zEvent) {
		Message msg = new Message(NOTIFY_POST);
		msg.addObject("data", zEvent);
		PostMessage(msg);
	}
	
	@Override
	protected void processMessage(Message zMessage) throws Exception {
	
		if(zMessage.isMessageType(NOTIFY_INIT)) {
			
			//Load the hooks..
			//..
			
		}else if(zMessage.isMessageType(NOTIFY_SHUTDOWN)) {

			//Save the hooks..
			//..
			
			stopMessageProcessor();
			
		}else if(zMessage.isMessageType(NOTIFY_ADDHOOK)) {
			
			//Get the new Hook
			String hook = zMessage.getString("webhook");
			
			//Add if not already added
			if(!mHooks.contains(hook)) {
				mHooks.add(hook);
			}
			
		}else if(zMessage.isMessageType(NOTIFY_POST)) {
			
			//Get the Message
			JSONObject data = (JSONObject) zMessage.getObject("data");
			
			//Convert..
			String postmsg = data.toString();
			
			//Cycle through and Post to each hook..
			for(String hook : mHooks) {
				try {
				
					//Post it..
					RPCClient.sendPOST(hook, postmsg);
					
				}catch(Exception exc) {
					MinimaLogger.log("ERROR webhook : "+hook+" "+exc);
				}
			}
		}
	}
}
