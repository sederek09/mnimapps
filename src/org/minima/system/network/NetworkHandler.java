package org.minima.system.network;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Random;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLServerSocketFactory;

import org.minima.Start;
import org.minima.system.Main;
import org.minima.system.brains.ConsensusHandler;
import org.minima.system.input.InputHandler;
import org.minima.system.network.base.MinimaClient;
import org.minima.system.network.base.MinimaServer;
import org.minima.system.network.maxima.Maxima;
import org.minima.system.network.minidapps.DAPPManager;
import org.minima.system.network.minidapps.SelfSignedCertGenerator;
import org.minima.system.network.minidapps.websocket.WebSocketManager;
import org.minima.system.network.rpc.NanoRPCServer;
import org.minima.system.network.sshtunnel.SSHTunnel;
import org.minima.utils.MinimaLogger;
import org.minima.utils.json.JSONArray;
import org.minima.utils.messages.Message;
import org.minima.utils.messages.MessageProcessor;
import org.minima.utils.messages.TimerMessage;
import org.minima.utils.nanohttpd.protocols.http.NanoHTTPD;

public class NetworkHandler extends MessageProcessor {

	public static final String NETWORK_STARTUP 		= "NETWORK_START";
	public static final String NETWORK_SHUTDOWN 	= "NETWORK_SHUTDOWN";
	
	public static final String NETWORK_CONNECT 		= "NETWORK_CONNECT";
	public static final String NETWORK_DISCONNECT 	= "NETWORK_DISCONNECT";
	public static final String NETWORK_RECONNECT 	= "NETWORK_RECONNECT";
	
	public static final String NETWORK_NEWCLIENT 	= "NETWORK_NEWCLIENT";
	public static final String NETWORK_CLIENTERROR 	= "NETWORK_CLIENTERROR";
	
	public static final String NETWORK_PING 		= "NETWORK_PING";
	public static final String NETWORK_PONG 		= "NETWORK_PONG";
	public static final String NETWORK_TRACE 		= "NETWORK_TRACE";
	
	public static final String NETWORK_SENDALL 		= "NETWORK_SENDALL";
	
	public static final String NETWORK_WEBPROXY 	= "NETWORK_WEBPROXY";
	
	/**
	 * The Main Minima Server
	 */
	MinimaServer mServer;
	
	/**
	 * The RPC server listening for remote commands
	 */
//	RPCServer mRPCServer;
	NanoRPCServer mNanoRPC;
	
	/**
	 * DAPP Server
	 */
	DAPPManager mDAPPManager;
	
	/**
	 * WebSocket Manager
	 */
	WebSocketManager mWebSocketManager;
	
	/**
	 * MAXIMA..
	 */
	Maxima mMaxima;
	
	/**
	 * SSH Tunnel
	 */
	SSHTunnel mTunnel;

	/**
	 * URL to call with MiniDAPP JSON details
	 */
	String mExternalURL = "";
	
	/**
	 * All the network channels..
	 */
	ArrayList<MinimaClient> mClients 	= new ArrayList<>();
	
	/**
	 * A list of all the requested TxPoW messages.. 
	 * they could be invalid on arrival as in a different  ranch
	 */
	ArrayList<String> mRequestedTxPoW = new ArrayList<>();
	
	/**
	 * Is reconnect enabled or not ?
	 */
	boolean mGlobalReconnect = true;
	
	/**
	 * HARD SET THE HOST
	 */
	boolean mHardSetLocal   = false;
	String mLocalHost  		= "";
	
	boolean mIsRemoteOn = false;
	String mRemoteHost = "";
	
	String mMinimaHost = "";
	String mMaximaHost = "";
	int mRemoteMinima  = -10;
	int mRemoteMaxima  = -11;
	
	/**
	 * The Main Minima port - all other ports are added to this one..
	 * 
	 * So you can specify just one port per client.
	 */
	int mBasePort;
	
	/**
	 * SSL Socket Factory
	 */
	SSLServerSocketFactory mSSLFactory;
	boolean SSL_ENABLED = true;
	
	/**
	 * 
	 * @param zMain
	 */
	public NetworkHandler(String zHost, int zMainPort) {
		super("NETWORK");

		if(zHost.equals("")) {
			mHardSetLocal = false;
			calculateHostIP();
		}else {
			mHardSetLocal 	  = true;
			mLocalHost    	  = zHost;
		}
		
		//Starts local
		mIsRemoteOn = false;
		
		//The base port all the other ports are derived from
		mBasePort   = zMainPort;
		mRemoteMinima = mBasePort;
		mRemoteMaxima = mBasePort+4;
		
		//SSL Factory
		try {
			//The KeyStore
			KeyStore keystore = null;
			
			//The keystore file
			File keysfile = Main.getMainHandler().getBackupManager().getBackUpFile("sslkeystore");
			
			//Do we have a store allready..
			if(keysfile.exists()) {
				MinimaLogger.log("Loading SSL Keystore.. "+KeyStore.getDefaultType());
				
				//Load it..
				 KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
				 InputStream fis = new FileInputStream(keysfile);
				 keyStore.load(fis, "MINIMAPWD".toCharArray());
				 fis.close();
				
				 //Get the KEY - this breaks..?
				 Key kk = keystore.getKey(SelfSignedCertGenerator.CERTIFICATE_ALIAS, "MINIMAPWD".toCharArray());
				 MinimaLogger.log("KEY "+kk.toString());
				 
			}else {
				MinimaLogger.log("Generating SSL Keystore..");
				
				KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
				keyPairGenerator.initialize(4096);
				KeyPair keyPair = keyPairGenerator.generateKeyPair();
				final X509Certificate cert = SelfSignedCertGenerator.generate(keyPair, "SHA256withRSA", "localhost", 730);
				keystore = SelfSignedCertGenerator.createKeystore(cert, keyPair.getPrivate());
			
				//Save it..
				OutputStream fos = new FileOutputStream(keysfile);
				keystore.store(fos, "MINIMAPWD".toCharArray());
				fos.flush();
				fos.close();
			}
			
			KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keyManagerFactory.init(keystore, "MINIMAPWD".toCharArray());
			
			//And create!
			mSSLFactory =  NanoHTTPD.makeSSLSocketFactory(keystore, keyManagerFactory);
		
		}catch (IOException e) {
			MinimaLogger.log("MiniDAPP server error " + e.toString());
		} catch (KeyStoreException e) {
			MinimaLogger.log("MiniDAPP KeyStoreException " + e.toString());
		} catch (NoSuchAlgorithmException e) {
			MinimaLogger.log("MiniDAPP NoSuchAlgorithmException " + e.toString());
		} catch (UnrecoverableKeyException e) {
			MinimaLogger.log("MiniDAPP UnrecoverableKeyException " + e.toString());
		} catch (CertificateException e) {
			MinimaLogger.log("MiniDAPP CertificateException " + e.toString());
		} catch (java.lang.Exception e){
			MinimaLogger.log("MiniDAPP SSL create error " + e.toString());
		}
	}
	
	public SSLServerSocketFactory getSSLServerFactory() {
		return mSSLFactory;
	}
	
	public boolean isSSLEnabled() {
		return SSL_ENABLED;
	}
	
	public void sshHardSetIP(boolean zRemoteOn, String zIP, int zRemoteBase) {
		mIsRemoteOn = zRemoteOn;
		mRemoteHost = zIP;
		mRemoteMinima = zRemoteBase;
		mRemoteMaxima = zRemoteBase+1;
	}
	
	public String getBaseHost() {
		return mLocalHost;
	}
	
	public String getMiniMaxiHost() {
		if(mIsRemoteOn) {
			return mRemoteHost;
		}
		
		return getBaseHost();
	}
	
	public int getBasePort() {
		return mBasePort;
	}
	
	public int getMinimaPort() {
		if(mIsRemoteOn) {
			return mRemoteMinima;
		}
		
		return mBasePort;
	}
	
	public int getRPCPort() {
		return mBasePort+1;
	}
	
	public int getWSPort() {
		return mBasePort+2;
	}
	
	public int getMiniDAPPServerPort() {
		return mBasePort+3;
	}
	
	public int getMaximaPort() {
		if(mIsRemoteOn) {
			return mRemoteMaxima;
		}
		return mBasePort+4;
	}
	
	public String getExternalURL() {
		return mExternalURL;
	}
	
	public void setExternalURL(String zURL) {
		MinimaLogger.log("External URL : "+zURL);
		mExternalURL = zURL;
	}
	
	public String calculateHostIP() {
		if(mHardSetLocal) {
			return mLocalHost;
		}
		
		mLocalHost = "127.0.0.1";
		try {
			boolean found = false;
		    Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
	        while (!found && interfaces.hasMoreElements()) {
	            NetworkInterface iface = interfaces.nextElement();
	            // filters out 127.0.0.1 and inactive interfaces
	            if (iface.isLoopback() || !iface.isUp())
	                continue;

	            Enumeration<InetAddress> addresses = iface.getInetAddresses();
	            while(!found && addresses.hasMoreElements()) {
	                InetAddress addr = addresses.nextElement();
	                String ip   = addr.getHostAddress();
	                String name = iface.getDisplayName();
	                
	                //Only get the IPv4
	                if(!ip.contains(":")) {
	                	mLocalHost = ip;
	                	
	                	//If you're on WiFi..
	                	if(name.startsWith("wl")) {
	                		found = true;
	                		break;
	                	}
	                }
	            }
	        }
	    } catch (SocketException e) {
	        MinimaLogger.log("calculateHostIP : "+e);
	    }
		
		return mLocalHost;
	}
	
	public MinimaServer getMinimaServer() {
		return mServer;
	}
	
//	public RPCServer getRPCServer() {
//		return mRPCServer;
//	}
	
	public DAPPManager getDAPPManager() {
		return mDAPPManager;
	}
	
	public WebSocketManager getWebSocketManager() {
		return mWebSocketManager;
	}
	
	public Maxima getMaxima() {
		return mMaxima;
	}
	
	public SSHTunnel getSSHTunnel() {
		return mTunnel;
	}
	
	public void setGlobalReconnect(boolean zGlobalReconnect) {
		mGlobalReconnect = zGlobalReconnect;
	}
	
	@Override
	protected void processMessage(Message zMessage) throws Exception {
		
		if(zMessage.isMessageType(NETWORK_STARTUP)) {
			MinimaLogger.log("Network Startup..");
			
			//Start the network Server
			mServer = new MinimaServer(this,getMinimaPort());
			Thread multimain = new Thread(mServer, "Multi Server");
			multimain.setDaemon(true);
			multimain.start();
			
			//Small pause..
			Thread.sleep(200);
			
			//Start the RPC server
			mNanoRPC = new NanoRPCServer(getRPCPort());
			mNanoRPC.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
			
//			mRPCServer = new RPCServer(getRPCPort());
//			Thread rpc = new Thread(mRPCServer, "RPC Server");
//			rpc.setDaemon(true);
//			rpc.start();
			
			//Small pause..
			Thread.sleep(200);
			
			//Start the DAPP Server
			mDAPPManager = new DAPPManager();
			
			//Start the WebSocket Manager
			mWebSocketManager = new WebSocketManager(getWSPort());
			
			//Start Maxima
			mMaxima = new Maxima();
			
			//Start the SSH Tunnel Manager
			mTunnel = new SSHTunnel();
			
		}else if(zMessage.isMessageType(NETWORK_SHUTDOWN)) {
			//Stop the server
			try {mServer.stop();}catch(Exception exc) {
				MinimaLogger.log(exc);
			}
			
			//Stop the RPC server
			try {mNanoRPC.stop();}catch(Exception exc) {
				MinimaLogger.log(exc);
			}
			
//			try {mRPCServer.stop();}catch(Exception exc) {
//				MinimaLogger.log(exc);
//			}
			
			//Stop the RPC server
			try {mDAPPManager.stop();}catch(Exception exc) {
				MinimaLogger.log(exc);
			}
			
			//Stop the WebSocket server
			try {mWebSocketManager.stop();}catch(Exception exc) {
				MinimaLogger.log(exc);
			}
			
			//Stop Maxima
			try {mMaxima.stop();}catch(Exception exc) {
				MinimaLogger.log(exc);
			}
			
			//Stop SSH Tunnel
			try {mTunnel.stop();}catch(Exception exc) {
				MinimaLogger.log(exc);
			}
			
			//Shutdown all the clients
			for(MinimaClient client : mClients) {
				client.shutdown();
			}
			
			//And finish up..
			stopMessageProcessor();
			
		}else if(zMessage.isMessageType(NETWORK_CONNECT)) {
			String host = zMessage.getString("host");
			int port 	= zMessage.getInteger("port");
			
			MinimaLogger.log("Attempting to connect to "+host+":"+port);
			
			//Create a NetClient
			MinimaClient client = new MinimaClient(host, port, this);
			
			//Store with the rest
			PostMessage(new Message(NETWORK_NEWCLIENT).addObject("client", client));
		
			InputHandler.endResponse(zMessage, true, "Attempting to connect to "+host+":"+port);
			
		}else if(zMessage.isMessageType(NETWORK_PING)) {
			
			
		}else if(zMessage.isMessageType(NETWORK_RECONNECT)) {
			//Disconnect and reconnect
			JSONArray shut = new JSONArray();
			for(MinimaClient client : mClients) {
				//get the UID
				shut.add(client.getUID());
				
				//tell it to shut down..
				client.PostMessage(MinimaClient.NETCLIENT_SHUTDOWN);
			}
			
			InputHandler.getResponseJSON(zMessage).put("total", shut.size());
			InputHandler.getResponseJSON(zMessage).put("clients", shut);
			InputHandler.endResponse(zMessage, true, "All network clients reset - reconnect in 30 seconds");
	
			//Notify..
			Main.getMainHandler().getConsensusHandler().updateListeners(new Message(ConsensusHandler.CONSENSUS_NOTIFY_RECONNECT));
			
		}else if(zMessage.isMessageType(NETWORK_DISCONNECT)) {
			String uid = zMessage.getString("uid");
			
			for(MinimaClient client : mClients) {
				if(client.getUID().equals(uid)) {
					//Don;t want to reconnect if we choose to disconnect
					client.noReconnect();
					
					//tell it to shut down..
					client.PostMessage(MinimaClient.NETCLIENT_SHUTDOWN);
			
					InputHandler.endResponse(zMessage, true, "Client "+uid+" disconnected - won't reconnect");
					
					return;
				}
			}
			
			InputHandler.endResponse(zMessage, false, "Could not find client UID "+uid);
			
		}else if(zMessage.isMessageType(NETWORK_NEWCLIENT)) {
			//get the client
			MinimaClient client = (MinimaClient)zMessage.getObject("client");
			
			//Add it
			mClients.add(client);
			
		}else if(zMessage.isMessageType(NETWORK_CLIENTERROR)) {
			//get the client
			MinimaClient client = (MinimaClient)zMessage.getObject("client");
			
			//Is it a reconnect-er ?
			boolean reconnect = client.isReconnect();
			if(reconnect && mGlobalReconnect) {
				String host = client.getHost();
				int port    = client.getPort();
				
				//Is this one of the Initial Host/Port BootStrap Server ?
				boolean bootstrapnode = false;
				for(int i=0;i<Start.VALID_BOOTSTRAP_NODES.length;i++) {
					if(host.equals(Start.VALID_BOOTSTRAP_NODES[i])) {
						bootstrapnode = true;
						break;
					}
				}
				if(bootstrapnode) {
					String oldhost = new String(host);
					
					//Pick random host and one of 3 ports
					Random rand = new Random();
					host = Start.VALID_BOOTSTRAP_NODES[rand.nextInt(Start.VALID_BOOTSTRAP_NODES.length)];
					port = 9001 + (1000*rand.nextInt(3));
							
					MinimaLogger.log("BOOTSTRAP NODE Connection lost.. resetting from "+oldhost+" to "+host);
				}
				
				//And post a message..
				TimerMessage  recon = new TimerMessage(30000,NETWORK_CONNECT);
				recon.addString("host", host);
				recon.addInteger("port", port);
				
				MinimaLogger.log("Attempting reconnect to "+host+":"+port+" in 30s..");
				
				PostTimerMessage(recon);
			}
			
			//Remove him from our list..
			mClients.remove(client);
		
			//Shut him down..
			client.PostMessage(new Message(MinimaClient.NETCLIENT_SHUTDOWN));
			
		}else if(zMessage.isMessageType(NETWORK_TRACE)) {
			boolean traceon = zMessage.getBoolean("trace");
			
			setLOG(traceon);
			
			for(MinimaClient client : mClients) {
				client.setLOG(traceon);
			}
			
			//Set trace for Maxima
			mMaxima.setLOG(traceon);
			mTunnel.setLOG(traceon);
		
		}else if(zMessage.isMessageType(NETWORK_SENDALL)) {
			//Get the message to send
			Message msg = (Message)zMessage.getObject("message");
			
			//Send to all the clients..
			for(MinimaClient client : mClients) {
				client.PostMessage(msg);
			}
		}
	}
	
	/**
	 * When you request a TxPOW it may be invalid as from a different branch..
	 */
	public void addRequestedTxPow(String zTxPoWID) {
		if(!isRequestedTxPow(zTxPoWID)) {
			mRequestedTxPoW.add(zTxPoWID);	
		}
	}
	
	public boolean isRequestedTxPow(String zTxPoWID) {
		return mRequestedTxPoW.contains(zTxPoWID);
	}
	
	public void removeRequestedTxPow(String zTxPoWID) {
		mRequestedTxPoW.remove(zTxPoWID);
	}
	
	public void clearAllrequestedTxPow() {
		mRequestedTxPoW.clear();
	}
	
	public int sizeRequestedTxPow() {
		return mRequestedTxPoW.size();
	}
	
	/**
	 * Get all the current net clients..
	 * @return the list
	 */
	public ArrayList<MinimaClient> getNetClients() {
		return mClients;
	}
	
}
