// SicfTCPServer.java
// $HeadURL$
package ngat.sprat.test;

import java.lang.*;
import java.io.*;
import java.net.*;

import ngat.net.*;

/**
 * This class extends the TCPServer class for the SendConfigCommand application. The SendConfigCommand sends
 * CONFIG commands to Sprat to test it's execution. The CONFIG command involves sending commands back to the ISS, 
 * and this class is designed to catch these requests and to spawn a SicfTCPServerConnectionThread to deal with them.
 * @author Chris Mottram
 * @version $Revision$
 */
public class SicfTCPServer extends TCPServer
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Field holding the instance of the controller object currently executing this server, 
	 * so we can pass this to spawned threads.
	 */
	private Object controller = null;

	/**
	 * The constructor.
	 * @param name The name of the server.
	 * @param portNumber The port number the server sits on.
	 */
	public SicfTCPServer(String name,int portNumber)
	{
		super(name,portNumber);
	}

	/**
	 * Routine to set this objects pointer to the controller object.
	 * @param o The controller object.
	 * @see #controller
	 */
	public void setController(Object o)
	{
		this.controller = o;
	}

	/**
	 * This routine spawns threads to handle connection to the server. This routine
	 * spawns SicfTCPServerConnectionThread threads.
	 * @see SicfTCPServerConnectionThread
	 */
	public void startConnectionThread(Socket connectionSocket)
	{
		SicfTCPServerConnectionThread thread = null;

		thread = new SicfTCPServerConnectionThread(connectionSocket);
		thread.setController(controller);
		thread.start();
	}

}
