// SpratTCPServer.java
// $HeadURL$
package ngat.sprat;

import java.lang.*;
import java.io.*;
import java.net.*;

import ngat.net.*;

/**
 * This class extends the TCPServer class for the Sprat robotic control software.
 * @author Chris Mottram
 * @version $Revision: 28 $
 */
public class SpratTCPServer extends TCPServer
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Field holding the instance of Sprat currently executing, so we can pass this to spawned threads.
	 */
	private Sprat sprat = null;

	/**
	 * The constructor.
	 */
	public SpratTCPServer(String name,int portNumber)
	{
		super(name,portNumber);
	}

	/**
	 * Routine to set this objects pointer to the sprat object.
	 * @param o The sprat object.
	 */
	public void setSprat(Sprat o)
	{
		this.sprat = o;
	}

	/**
	 * This routine spawns threads to handle connection to the server. This routine
	 * spawns SpratTCPServerConnectionThread threads.
	 * The routine also sets the new threads priority to higher than normal. This makes the thread
	 * reading it's command a priority so we can quickly determine whether the thread should
	 * continue to execute at a higher priority.
	 * @see SpratTCPServerConnectionThread
	 */
	public void startConnectionThread(Socket connectionSocket)
	{
		SpratTCPServerConnectionThread thread = null;

		thread = new SpratTCPServerConnectionThread(connectionSocket);
		thread.setSprat(sprat);
		thread.setPriority(sprat.getStatus().getThreadPriorityInterrupt());
		thread.start();
	}
}
