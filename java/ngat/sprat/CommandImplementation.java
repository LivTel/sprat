// CommandImplementation.java
// $HeadURL$
package ngat.sprat;

import ngat.message.base.*;
import ngat.message.ISS_INST.INTERRUPT;

/**
 * This class provides the generic implementation of a command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision: 28 $
 */
public class CommandImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The Sprat object.
	 */
	protected Sprat sprat = null;
	/**
	 * Sprat status reference.
	 * @see SpratStatus
	 */
	protected SpratStatus status = null;
	/**
	 * Reference to the Sprat thread running the implementation of this command.
	 */
	protected SpratTCPServerConnectionThread serverConnectionThread = null;

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;null&quot;, as this class implements no message class.
	 * @return A string, the classname of the class of command this class implements.
	 */
	public static String getImplementString()
	{
		return null;
	}

	/**
	 * This method is called from the TCPServerConnection's init method, after the command to be 
	 * implemented has been 
	 * received. This enables us to do any setup required for the implementation before implementation 
	 * actually starts. It then tries to fill in the Sprat status references.
	 * @param command The command to be implemented.
	 * @see #sprat
	 * @see #status
	 * @see Sprat#getStatus
	 */
	public void init(COMMAND command)
	{
		if(sprat != null)
		{
			status = sprat.getStatus();
		}
		if(command == null)
			return;
	}

	/**
	 * This method is used to calculate how long an implementation of a command is going to take, so that the
	 * client has an idea of how long to wait before it can assume the server has died.
	 * @param command The command to be implemented.
	 * @return An instance of class ngat.message.base.ACK, with the time taken to implement 
	 * 	this command, or the time taken before the next acknowledgement is to be sent.
	 * @see ngat.message.base.ACK
	 * @see ngat.message.base.ACK#setTimeToComplete
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(1000);
		return acknowledge;
	}

	/**
	 * This routine performs the generic command implementation.
	 * @param command The command to be implemented.
	 * @return The results of the implementation of this command.
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		COMMAND_DONE done = null;

		done = new COMMAND_DONE(command.getId());
		done.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
		done.setErrorString("Generic Command Implementation.");
		done.setSuccessful(true);
		return done;
	}

	/**
	 * Routine to set this objects pointer to the Sprat object.
	 * @param o The Sprat object.
	 */
	public void setSprat(Sprat o)
	{
		this.sprat = o;
	}

	/**
	 * Routine to set this objects pointer to the IOI server connection thread running this commands
	 * implementation.
	 * @param s The server connection thread.
	 */
	public void setServerConnectionThread(SpratTCPServerConnectionThread s)
	{
		this.serverConnectionThread = s;
	}

	/**
	 * This routine tests the current status of the threads abortProcessCommand
	 * to see whether the operation is to be terminated. 
	 * @param command The command being implemented. This is used
	 * 	for error logging.
	 * @param done A COMMAND_DONE subclass specific to the command being implemented. If an
	 * 	error occurs the relevant fields are filled in with the error.
	 * @return The routine returns a boolean to indicate whether the operation has been aborted or not.
	 * @see SpratTCPServerConnectionThread#getAbortProcessCommand
	 * @see #serverConnectionThread
	 */
	public boolean testAbort(COMMAND command,COMMAND_DONE done)
	{
		boolean abortProcessCommand = false;

		if(serverConnectionThread != null)
		{
			abortProcessCommand = serverConnectionThread.getAbortProcessCommand();
			if(abortProcessCommand)
			{
				String s = new String("Command "+command.getClass().getName()+
						" Operation Aborted.");
				sprat.error(s);
				done.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+200);
				done.setErrorString(s);
				done.setSuccessful(false);
			}
		}
		return abortProcessCommand;
	}
}
