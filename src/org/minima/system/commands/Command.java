package org.minima.system.commands;

import java.util.ArrayList;
import java.util.StringTokenizer;

import org.minima.system.commands.all.automine;
import org.minima.system.commands.all.balance;
import org.minima.system.commands.all.coins;
import org.minima.system.commands.all.connect;
import org.minima.system.commands.all.debugflag;
import org.minima.system.commands.all.disconnect;
import org.minima.system.commands.all.help;
import org.minima.system.commands.all.incentivecash;
import org.minima.system.commands.all.message;
import org.minima.system.commands.all.missingcmd;
import org.minima.system.commands.all.network;
import org.minima.system.commands.all.newaddress;
import org.minima.system.commands.all.printmmr;
import org.minima.system.commands.all.printtree;
import org.minima.system.commands.all.quit;
import org.minima.system.commands.all.rpc;
import org.minima.system.commands.all.send;
import org.minima.system.commands.all.sshtunnel;
import org.minima.system.commands.all.status;
import org.minima.system.commands.all.tokencreate;
import org.minima.system.commands.all.trace;
import org.minima.system.commands.all.txpow;
import org.minima.system.commands.all.webhooks;
import org.minima.utils.MinimaLogger;
import org.minima.utils.json.JSONArray;
import org.minima.utils.json.JSONObject;

public abstract class Command {

	public static final Command[] ALL_COMMANDS = 
		{   new quit(), new status(), new coins(), new txpow(), new connect(), new disconnect(), new network(),
			new message(), new trace(), new help(), new printtree(), new automine(), new printmmr(), new rpc(),
			new send(), new balance(), new tokencreate(), new newaddress(), new debugflag(),
			new incentivecash(), new sshtunnel(), new webhooks()};
	
	String mName;
	String mHelp;
	
	JSONObject mParams = new JSONObject();
	
	public Command(String zName, String zHelp) {
		mName = zName;
		mHelp = zHelp;
	}
	
	public String getHelp() {
		return mHelp;
	}
	
	public JSONObject getJSONReply() {
		JSONObject json = new JSONObject();
		json.put("command", getname());
		
		//Are they empty..
		if(!getParams().isEmpty()) {
			json.put("params", getParams());
		}
		
		json.put("status", true);
		return json;
	}
	
	public String getname() {
		return mName;
	}
	
	public JSONObject getParams() {
		return mParams;
	}
	
	public boolean existsParam(String zParamName) {
		return mParams.containsKey(zParamName);
	}
	
	public String getParam(String zParamName) {
		return getParam(zParamName, "");
	}
	
	public String getParam(String zParamName, String zDefault) {
		if(existsParam(zParamName)) {
			return (String) mParams.get(zParamName);
		}
		
		return zDefault;
	}
	
	public abstract JSONObject runCommand() throws Exception;
	
	public abstract Command getFunction();
	
	/**
	 * Run a with possible multiple functions
	 * 
	 * @param zCommand
	 */
	public static JSONArray runMultiCommand(String zCommand) {
		JSONArray res = new JSONArray();
		
		//First break it up..
		StringTokenizer strtok = new StringTokenizer(zCommand, ";");
		while(strtok.hasMoreTokens()) {
			String command = strtok.nextToken().trim();
			
			//Run this command..
			Command cmd = Command.getCommand(command);
			
			//Run it..
			JSONObject result = null;
			try {
				result = cmd.runCommand();
			}catch(Exception exc) {
				MinimaLogger.log(exc);
				
				result = cmd.getJSONReply();
				result.put("status", false);
				result.put("error", exc.getMessage());
			}
			
			//Add it..
			res.add(result);
		}
		
		return res;
	}
	
	public static Command getCommand(String zCommand) {
		int commandlen = ALL_COMMANDS.length;
		
		//Get the first word..
		String[] split = splitString(zCommand);
		
		//The first is the command..
		String command = split[0];
		
		Command comms = null;
		for(int i=0;i<commandlen;i++) {
			if(ALL_COMMANDS[i].getname().equals(command)) {
				comms = ALL_COMMANDS[i].getFunction();
				break;
			}
		}
		
		//If not found return error
		if(comms == null) {
			return new missingcmd(command,"Command not found");
		}
		
		//get the parameters if any
		int len = split.length;
		for(int i=1;i<len;i++) {
			String token = split[i];
			
			//Find the :
			int index 	 = token.indexOf(":");
			if(index == -1) {
				return new missingcmd(command,"Invalid parameters for "+command+" @ "+token);
			}
			
			String name  = token.substring(0, index).trim();
			String value = token.substring(index+1).trim();
			
			//Add to JSON..
			comms.getParams().put(name, value);
		}
		
		return comms;
	}

	/**
	 * Split the input string keeping quoted sections as single units
	 * 
	 * @param zString
	 * @return
	 */
	private static String[] splitString(String zInput) {
		ArrayList<String> token = new ArrayList<>();
		String ss = zInput.trim();
		
		//Cycle through looking for spaces or quotes..
		String current = new String();
		boolean quoted = false;
		int len = ss.length();
		for(int i=0;i<len;i++) {
			char cc = ss.charAt(i);
			
			if(cc == ' ') {
				//End of the line..
				if(!quoted) {
					//Add current
					if(!current.equals("")) {
						token.add(current.trim());
					}
						
					//New Current
					current = new String();
				}else {
					current += cc;
				}
			}else if(cc == '\"') {
				//it's a quote!
				if(quoted) {
					//It's finished..
					quoted=false;
				}else {
					quoted=true;
				}
			}else {
				current += cc;
			}
		}
		
		//Add the last bit..
		if(!current.equals("")) {
			token.add(current.trim());
		}
		
		return token.toArray(new String[0]);
	}
}