package localside;

import core.JavaReceiver;
import core.JavaTeardown;
import localside.listen.KeepAliveThread;
import localside.listen.SenderThread;
import localside.listen.ThreadGenerator;
import subprogram.SubProgram;

/**
 * 
 * JavaSocket class, an instance of a listening station on a particular port.
 * 
 * This class manages the details of running a Java-end socket and calling the Python program (which
 * we assume will take an argument in the first position that is the matching socket number and set
 * up a sending-socket that our Java-socket listens to).
 * 
 * Simply instantiate this class with the file path to your Python file and provide a class that
 * has implemented the JavaReceiver interface and this should automate the regular sending of data
 * to your Java program from a support Python program.
 *
 * Just in case your Python program requires additional inputs from the command line to function, you
 * may also include a String array (or var args) as a third argument to the constructor which will be
 * passed AFTER the port number to your Python program.
 * 
 * We call your python program as the command line instruction "python [file_path][file_name.py] [port #] [arg1] [arg2] ..."
 *
 * The keyword 'exit' is used to end the communication between Java and Python sockets; if the Python program
 * sends the Java socket 'exit' it closes things down.
 * 
 * A 5-second timeout period is used to decide if the communication between sockets has broken down and requires
 * resetting; if this occurs, it automatically terminates the connection and starts a new one on the next port
 * number.
 *
 */

public class JavaSocket implements InitiateListening, MessageSender {
	
//---  Constants   ----------------------------------------------------------------------------
	
	private final static int START_PORT = 5439;
	
	private final static int TIMEOUT_PERIOD = 5000;
	
	private final static int KEEP_ALIVE_DEFAULT = -1;
	
	private final static int TIMING_DELAY = 5000;
	
//---  Instance Variables   -------------------------------------------------------------------
	
	private JavaReceiver passTo;
	private SubProgram subProgram;
	private int currentListenPort;
	private int currentSendPort;
	private int timingDelay;
	
	/** int value used to denote how long to wait for a message from the listened-to port before resetting the connection;
	 * a value of -1 denotes do not track last message received for timing out*/
	private int timeout;
	/** int value used to denote how frequently to send a keepalive message to the connected-to port;
	 * a value of -1 denotes do not send keep alive messages*/
	private int keepalive;
	
	private volatile ListenerPacket packet;
	
	private JavaTeardown terminate;
	
	private boolean quiet;
	
//---  Constructors   -------------------------------------------------------------------------
	
	public JavaSocket() {
		setListenPort(START_PORT);
		setSendPort(START_PORT + 5);
		setTimeout(TIMEOUT_PERIOD);
		setTimingDelay(TIMING_DELAY);
		setKeepAlive(KEEP_ALIVE_DEFAULT);
	}
	
	public JavaSocket(int listenPort, int sendPort, int timeout, int keepalive, int timingDelay) {
		setListenPort(listenPort);
		setSendPort(sendPort);
		setTimeout(timeout);
		setKeepAlive(keepalive);
		setTimingDelay(timingDelay);
	}
	
	public JavaSocket(JavaReceiver reference) {
		passTo = reference;
		setListenPort(START_PORT);
		setSendPort(START_PORT + 5);
		setTimeout(TIMEOUT_PERIOD);
		setTimingDelay(TIMING_DELAY);
		setKeepAlive(KEEP_ALIVE_DEFAULT);
	}
	
	public JavaSocket(JavaReceiver reference, SubProgram runnable) {
		passTo = reference;
		subProgram = runnable;
		setListenPort(START_PORT);
		setSendPort(START_PORT + 5);
		setTimeout(TIMEOUT_PERIOD);
		setTimingDelay(TIMING_DELAY);
		setKeepAlive(KEEP_ALIVE_DEFAULT);
	}

//---  Operations   ---------------------------------------------------------------------------
	
	/**
	 * This function is called automatically by lower-level classes in this package and should
	 * not be called directly by a programmer using this package.
	 * 
	 * This effectively restarts the current listening session and iterates the port number forward,
	 * so if you did need to reset the listening manually, you could call this.
	 * 
	 */
	
	public void setUpListening() {
		if(packet != null) {
			packet.closeOutSession();
			if(terminate != null) {
				terminate.teardownProgram();
				return;
			}
			iteratePortNumber();
		}
		print("Initiating Listening Process on Listen Port: " + currentListenPort + " and Send Port: " + currentSendPort);
		startLocalListener(passTo);
		if(subProgram != null)
			subProgram.initiateSubprogram("" + currentListenPort, "" + currentSendPort, quiet);
	}
	
	@Override
	public void sendMessage(String message) throws Exception {
		if(packet != null) {
			packet.sendMessage(message);
		}
		else {
			throw new Exception("Message Sending Capabilities Not Ready Yet, Please Try Again");
		}
	}
	
	private void startLocalListener(JavaReceiver reference){
		packet = new ListenerPacket(currentListenPort, currentSendPort, keepalive);
		
		KeepAliveThread listen = ThreadGenerator.generateListeningThread(packet, reference);
		
		KeepAliveThread timeOut = timeout == -1 ? null : ThreadGenerator.generateTimeOutThread(packet, this,  1000,  timeout, timingDelay, subProgram == null ? null : subProgram.getContext());
		
		packet.assignThreads(listen, timeOut);
				
		
		listen.start();
		
		if(timeOut != null) {
			timeOut.start();
		}
		
	}
	
	/**
	 * Interrupts and ends the active Threads to stop the communication between the Java and
	 * Python sockets.
	 * 
	 */
	
	public void closeListening() {
		if(packet != null) {
			packet.closeOutSession();
		}
	}

	private void iteratePortNumber() {
		currentListenPort++;
		currentSendPort++;
		if(currentListenPort > 9999) {
			currentListenPort = 2000;
		}
		if(currentSendPort > 9999) {
			currentSendPort = 2005;
		}
	}

	public void assignTearDown(JavaTeardown tear) {
		terminate = tear;
	}
	
//---  Setter Methods   -----------------------------------------------------------------------

	public void setListenPort(int in) {
		currentListenPort = in >= 2000 && in <= 9999 ? in : START_PORT;
	}
	
	public void setSendPort(int in) {
		currentSendPort = in >= 2000 && in <= 9999 ? in : START_PORT + 5;
	}
	
	public void setTimeout(int in) {
		timeout = in < 0 ? -1 : in;
	}
	
	public void setKeepAlive(int in) {
		keepalive = in < 0 ? -1 : in;
	}
	
	public void setTimingDelay(int in) {
		timingDelay = in < 0 ? 0 : in;
	}
	
	public void setReceiver(JavaReceiver in) {
		passTo = in;
	}
	
	public void setSubprogram(SubProgram in) {
		subProgram = in;
	}
	
	public void setQuiet(boolean shh) {
		quiet = shh;
		KeepAliveThread.setQuiet(shh);
	}
	
//---  Getter Methods   -----------------------------------------------------------------------

	public int getCurrentListenPortNumber() {
		return currentListenPort;
	}

	public int getCurrentSendPortNumber() {
		return currentSendPort;
	}
	
//---  Support Methods   ----------------------------------------------------------------------
	
	private void print(String in) {
		if(!quiet) {
			System.out.println(in);
		}
	}
	
}
