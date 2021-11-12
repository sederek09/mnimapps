package org.minima.system.network.minima;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Date;

import org.minima.objects.base.MiniData;
import org.minima.system.Main;
import org.minima.utils.MiniFormat;
import org.minima.utils.MinimaLogger;
import org.minima.utils.json.JSONObject;
import org.minima.utils.messages.Message;

public class NIOClient {

	public static boolean mTraceON = false;
	
	/**
	 * 8K buffer for send and receive..
	 */
	public static final int MAX_NIO_BUFFERS = 32 * 1024;
	
	String mUID;
	
	SelectionKey mKey;
	
	ByteBuffer mBufferIn;
	int mReadCurrentPosition 	= 0;
    int mReadCurrentLimit 		= 0;
    byte[] mReadData 			= null;
	
	ByteBuffer mBufferOut;
	int mWritePosition 			= 0;
	int mWriteLimit				= 0;
	boolean mWriteStart			= false;
	byte[] mWriteData			= null;
	
	SocketChannel 	mSocket;
	
	String 			mHost;
	int 			mPort;
	
	boolean mIncoming;
	
	private ArrayList<MiniData> mMessages;
	
	
	NIOManager mNIOMAnager;
	
	String mWelcomeMessage = "";
	
	long mTimeConnected = 0;
	
	long mLastMessageRead;
	
	/**
	 * Specify extra info
	 */
	private Object mExtraData = null;
	
	public NIOClient(String zHost, int zPort) {
		mUID 		= MiniFormat.createRandomString(8);
		mHost		= zHost;
		mPort		= zPort;
		mIncoming	= false;
		mNIOMAnager = Main.getInstance().getNetworkManager().getNIOManager();
	}
	
	public NIOClient(boolean zIncoming, String zHost, int zPort, SocketChannel zSocket, SelectionKey zKey) {
        mUID 		= MiniFormat.createRandomString(8);
        
        mHost 	= zHost;
        mPort 	= zPort;
        mSocket	= zSocket;
        mKey	= zKey;
        
        mIncoming	= zIncoming;
        
        //Max buffer chunks for read and write
        mBufferIn 	= ByteBuffer.allocate(MAX_NIO_BUFFERS);
        mBufferOut 	= ByteBuffer.allocate(MAX_NIO_BUFFERS);
        
        //Writing
        mMessages 				= new ArrayList<>();
        
        //Reading
    	mReadData 				= null;
    	
    	mNIOMAnager = Main.getInstance().getNetworkManager().getNIOManager();
    	
    	mTimeConnected 		= System.currentTimeMillis();
    	mLastMessageRead 	= mTimeConnected; 
    }
	
	public JSONObject toJSON() {
		JSONObject ret = new JSONObject();
		
		ret.put("welcome", mWelcomeMessage);
		ret.put("uid", getUID());
		ret.put("incoming", isIncoming());
		ret.put("host", mHost);
		ret.put("port", mPort);
		ret.put("connected", new Date(mTimeConnected).toString());
		
		return ret;
	}
	
	public void setExtraData(Object zExtraData) {
		mExtraData = zExtraData;
	}
	
	public Object getExtraData() {
		return mExtraData;
	}
	
	public String getUID() {
		return mUID;
	}
	
	public boolean isIncoming() {
		return mIncoming;
	}
	
	public String getHost() {
		return mHost;
	}
	
	public int getPort() {
		return mPort;
	}
	
	public void setWelcomeMessage(String zWelcome) {
		mWelcomeMessage = zWelcome;
	}
	
	public long getTimeConnected() {
		return  mTimeConnected;
	}
	
	public long getLastReadTime() {
		return mLastMessageRead;
	}
	
	public void sendData(MiniData zData) {
		synchronized (mMessages) {
			mMessages.add(zData);
		
			//And now say we want to write..
			mKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
			mKey.selector().wakeup();
		}
	}
	
	private boolean isNextData() {
		synchronized (mMessages) {
			return mMessages.size()>0;
		}
	}
	
	private MiniData getNextData() {
		synchronized (mMessages) {
			if(mMessages.size()>0) {
				return mMessages.remove(0);
			}
		}
		
		return  null;
	}
	
	public void handleRead() throws IOException {
		
		//read in..
 	   	int readbytes = mSocket.read(mBufferIn);
 	   	if(readbytes == -1) {
 	   		throw new IOException("Socket Closed!");
 	   	}
 	   
 	   	//Debug
 		if(mTraceON) {
 			MinimaLogger.log("[NIOCLIENT] "+mUID+" read "+readbytes);
 		}
 		
 	   	//Nothing..
 	   	if(readbytes == 0) {
 	   		return;
 	   	}
 	   	
 	   	//Ready to read
 	   	mBufferIn.flip();
 	   	
 	   	while(mBufferIn.hasRemaining()) {
 	   	
 	   		//What are we reading
 	   		if(mReadData == null) {
 	   			
 	   			//Do we have enough for the size..
 	   			if(mBufferIn.remaining() >= 4) {
 	   				mReadCurrentLimit 		= mBufferIn.getInt();
 	   				mReadCurrentPosition 	= 0;
 	   				
 	   				mReadData = new byte[mReadCurrentLimit];
 	   			}else {
 	   				//Not enough for the size..
 	   				break;
 	   			}
 	   		}
 	   		
 	   		//We have something..
 	   		if(mReadData != null) {
 	   			//How much left to read for this object
				int readremaining = mReadCurrentLimit - mReadCurrentPosition;
				   
				//How much is there still to read
				int buffread = mBufferIn.remaining();
				if(buffread > readremaining) {
					buffread = readremaining;
				}
				   
				//Copy into the structure
				mBufferIn.get(mReadData, mReadCurrentPosition, buffread);
				mReadCurrentPosition += buffread;
				   
				//Are we done..
				if(mReadCurrentPosition == mReadCurrentLimit) {
					//Post it !
					Message msg = new Message(NIOManager.NIO_INCOMINGMSG);
					msg.addString("uid", mUID);
					msg.addObject("data", new MiniData(mReadData));
					mNIOMAnager.PostMessage(msg);
					
					//New array required..
					mReadData = null;
					
					//Last message we have received from this client
					mLastMessageRead = System.currentTimeMillis();
				}
 	   		}
 	   	}
 	   	
		//ready to read more..
		mBufferIn.compact();
	}
	
	public void handleWrite() throws IOException {
		
		//First fill the buffer if it has the space
		while(mBufferOut.hasRemaining()) {
			
			//Do we have a packet we are working on
			if(mWriteData == null) {
				
				//Get the next packet
				if(isNextData()) {
					mWriteData 		= getNextData().getBytes();
					mWritePosition 	= 0;
					mWriteLimit 	= mWriteData.length; 
					mWriteStart		= false;
				}else {
					//Nothing to add
					break;
				}
			}
			
			//We have data to write
			if(mWriteData != null) {
				
				//Have we written the size yet
				if(!mWriteStart) {
					if(mBufferOut.remaining() >= 4) {
						mBufferOut.putInt(mWriteLimit);
						mWriteStart = true;
					}else {
						//Not enough space to write the size
						break;
					}
				}
				
				if(mWriteStart) {
					//How much left in the buffer
					int remaining = mBufferOut.remaining();
					
					//How much left to write
					int writeremain = mWriteLimit - mWritePosition;
					if(writeremain > remaining) {
						writeremain = remaining; 
					}
					   
					//Copy that to the buffer..   
					mBufferOut.put(mWriteData, mWritePosition, writeremain);
					mWritePosition += writeremain;
					
					//Have we finished
					if(mWritePosition == mWriteLimit) {
						mWriteData = null;
					}
				}
			}
		}
		
		//Ready to write
		mBufferOut.flip();
		
		//Write
		int write = mSocket.write(mBufferOut);
		if(mTraceON) {
			MinimaLogger.log("[NIOCLIENT] "+mUID+" wrote : "+write);
		}
		
		//Any left
		synchronized (mMessages) {
			if(!mBufferOut.hasRemaining() && mMessages.size()==0 && mWriteData == null) {
				//Only interested in RERAD
				mKey.interestOps(SelectionKey.OP_READ);
			}
		}
		
		//Compact..
		mBufferOut.compact();
	}
	
	public void disconnect() {
        try {
     	   mKey.cancel();
     	   mSocket.close();
     	} catch (Exception ioe) {
     		
     	}
    }
}