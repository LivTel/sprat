// UnknownCommandImplementation.java
// $HeadURL$
package ngat.sprat;

import ngat.message.base.*;

/**
 * This class provides the implementation of a command sent to a server using the
 * Java Message System. The command sent is unknown to the server in this case.
 * @author Chris Mottram
 * @version $Revision: 28 $
 */
public class UnknownCommandImplementation extends CommandImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * This method gets the unknown command's acknowledge time. This returns the server connection threads 
	 * min acknowledge time.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see SpratTCPServerConnectionThread#getMinAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(serverConnectionThread.getMinAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This routine performs the command implementation of a command that is unknown to the server.
	 * This just returns a COMMAND_DONE instance, with successful set to false and an error code.
	 * @param command The command to be implemented.
	 * @return The results of the implementation of this command.
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		COMMAND_DONE done = null;

		done = new COMMAND_DONE(command.getId());
		sprat.error("Unknown Commmand:"+command.getClass().getName());
		done.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+400);
		done.setErrorString("Unknown Commmand:"+command.getClass().getName());
		done.setSuccessful(false);
		return done;
	}
}
