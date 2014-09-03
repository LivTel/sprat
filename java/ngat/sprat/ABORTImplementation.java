// ABORTImplementation.java 
// $HeadURL$
package ngat.sprat;

import java.lang.*;
import java.util.*;

import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.sprat.ccd.command.*;
import ngat.sprat.mechanism.command.*;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the ABORT command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision: 1.1 $
 */
public class ABORTImplementation extends HardwareImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Constructor.
	 */
	public ABORTImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.ABORT&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.ABORT";
	}

	/**
	 * This method gets the ABORT command's acknowledge time. This takes the default acknowledge time to implement.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see SpratTCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the ABORT command. 
	 * <ul>
	 * <li>We send a C layer abort command.
	 * <li>We get the current command thread using status.getCurrentThread.
	 * <li>If the current thread is non-null, we call setAbortProcessCommand to tell the thread it is 
	 *     being aborted. When the command implemented in the running thread nect calls 'testAbort' this will
	 *     inform the command it has been aborted.
	 * <li>We send the DpRt an abort command.
	 * </ul>
	 * There is no mechanism to abort a mechanism move.
	 * @param command The abort command.
	 * @return An object of class ABORT_DONE is returned.
	 * @see #sendAbortCommand
	 * @see #sprat
	 * @see Sprat#sendDpRtCommand
	 * @see SpratStatus#getCurrentThread
	 * @see SpratTCPServerConnectionThread#setAbortProcessCommand
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		ngat.message.INST_DP.ABORT dprtAbort = new ngat.message.INST_DP.ABORT(command.getId());
		ABORT_DONE abortDone = new ABORT_DONE(command.getId());
		SpratTCPServerConnectionThread thread = null;

		try
		{
			sendAbortCommand();
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":Aborting exposure failed:",e);
			abortDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2400);
			abortDone.setErrorString("Aborting exposure failed:"+e.toString());
			abortDone.setSuccessful(false);
			return abortDone;
		}
	// tell the thread itself to abort at a suitable point
		sprat.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Tell thread to abort.");
		thread = (SpratTCPServerConnectionThread)status.getCurrentThread();
		if(thread != null)
			thread.setAbortProcessCommand();
		// abort data pipeline
		sprat.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Tell DpRt to abort.");
		sprat.sendDpRtCommand(dprtAbort,serverConnectionThread);
	// return done object.
		sprat.log(Logging.VERBOSITY_VERY_TERSE,"Command:"+command.getClass().getName()+
			  ":Abort command completed.");
		abortDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
		abortDone.setErrorString("");
		abortDone.setSuccessful(true);
		return abortDone;
	}

	/**
	 * Send an "abort" command to the C layer.
	 * @exception Exception Thrown if an error occurs.
	 * @see #status
	 * @see HardwareImplementation#ccdCLayerHostname
	 * @see HardwareImplementation#ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.AbortCommand
	 * @see ngat.sprat.ccd.command.AbortCommand#setAddress
	 * @see ngat.sprat.ccd.command.AbortCommand#setPortNumber
	 * @see ngat.sprat.ccd.command.AbortCommand#setCommand
	 * @see ngat.sprat.ccd.command.AbortCommand#sendCommand
	 * @see ngat.sprat.ccd.command.AbortCommand#getParsedReplyOK
	 * @see ngat.sprat.ccd.command.AbortCommand#getReturnCode
	 * @see ngat.sprat.ccd.command.AbortCommand#getParsedReply
	 */
	protected void sendAbortCommand() throws Exception
	{
		AbortCommand command = null;
		int portNumber,returnCode;
		String hostname = null;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendAbortCommand:Started.");
		command = new AbortCommand();
		// configure C comms
		command.setAddress(ccdCLayerHostname);
		command.setPortNumber(ccdCLayerPortNumber);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendAbortCommand:hostname = "+ccdCLayerHostname+
			  " :port number = "+ccdCLayerPortNumber+".");
		// actually send the command to the C layer
		command.sendCommand();
		// check the parsed reply
		if(command.getParsedReplyOK() == false)
		{
			returnCode = command.getReturnCode();
			errorString = command.getParsedReply();
			sprat.log(Logging.VERBOSITY_TERSE,
				   "sendAbortCommand:abort command failed with return code "+
				   returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
					    ":sendAbortCommand:Command failed with return code "+returnCode+
					    " and error string:"+errorString);
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendAbortCommand:Finished.");
	}
}
