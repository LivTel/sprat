// MULTRUNImplementation.java
// $HeadURL$
package ngat.sprat;

import java.lang.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.sprat.ccd.command.*;
import ngat.sprat.mechanism.command.*;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the MULTRUN command sent to a server using the
 * Java Message System.
 * @author Chris Motram
 * @version $Revision: 1.3 $
 */
public class MULTRUNImplementation extends HardwareImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The last FITS filename returned by the multrun command.
	 */
	protected String lastFilename = null;
	/**
	 * The multrun number used for FITS filenames for this command.
	 */
	protected int multrunNumber = 0;

	/**
	 * Constructor.
	 */
	public MULTRUNImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.MULTRUN&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.MULTRUN";
	}

	/**
	 * This method returns the MULTRUN command's acknowledge time. 
	 * <ul>
	 * <li>The time to be added for readout is retrieved from the sprat.multrun.acknowledge_time.readout property.
	 * <li>The acknowledge time is the exposure length plus the readout time, multiplied by the exposure count.
	 * <li>The default acknowledge time is added to the total and returned.
	 * </ul>
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see SpratTCPServerConnectionThread#getDefaultAcknowledgeTime
	 * @see #sprat
	 * @see #status
	 * @see #serverConnectionThread
	 * @see ngat.sprat.Sprat#getStatus
	 * @see ngat.sprat.SpratStatus#getPropertyInteger
	 * @see MULTRUN#getExposureTime
	 * @see MULTRUN#getNumberExposures
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		MULTRUN multRunCommand = (MULTRUN)command;
		ACK acknowledge = null;
		int exposureLength,exposureCount,ackTime=0,readoutAckTime;

		exposureLength = multRunCommand.getExposureTime();
		exposureCount = multRunCommand.getNumberExposures();
		try
		{
			readoutAckTime = sprat.getStatus().
				getPropertyInteger("sprat.multrun.acknowledge_time.readout");
		}
		catch(NumberFormatException e)
		{
			sprat.error(this.getClass().getName()+":calculateAcknowledgeTime:"+e);
			readoutAckTime = serverConnectionThread.getDefaultAcknowledgeTime();
		}
		sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			  ":calculateAcknowledgeTime:exposureLength = "+exposureLength+
			  " :exposureCount = "+exposureCount+" :readoutAckTime = "+readoutAckTime);
		ackTime = ((exposureLength+readoutAckTime)*exposureCount);
		sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":calculateAcknowledgeTime:ackTime = "+ackTime);
		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(ackTime+serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the MULTRUN command. 
	 * <ul>
	 * <li>moveMirror is called to move the calibration mirror out of the beam.
	 * <li>It moves the fold mirror to the correct location.
	 * <li>clearFitsHeaders is called.
	 * <li>setFitsHeaders is called to get some FITS headers from the properties files and add them to the C layer.
	 * <li>getFitsHeadersFromISS is called to gets some FITS headers from the ISS (RCS). A filtered subset
	 *     is sent on to the C layer.
	 * <li>sendMultrunCommand is called to actually send the multrun command to the C layer.
	 * <li>The done object is setup.
	 * </ul>
	 * @see #testAbort
	 * @see ngat.sprat.HardwareImplementation#moveFold
	 * @see ngat.sprat.HardwareImplementation#clearFitsHeaders
	 * @see ngat.sprat.HardwareImplementation#setFitsHeaders
	 * @see ngat.sprat.HardwareImplementation#getFitsHeadersFromISS
	 * @see #sendMultrunCommand
	 * @see ngat.sprat.HardwareImplementation#moveMirror
	 * @see ngat.phase2.SpratConfig#POSITION_OUT
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		MULTRUN multRunCommand = (MULTRUN)command;
		MULTRUN_ACK multRunAck = null;
		MULTRUN_DONE multRunDone = new MULTRUN_DONE(command.getId());
		int exposureLength,exposureCount;
		boolean standard;

		sprat.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Started.");
		if(testAbort(multRunCommand,multRunDone) == true)
			return multRunDone;
		// move the sprat calibration mirror out of the beam
		sprat.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Moving calibration mirror.");
		try
		{
			moveMirror(SpratConfig.POSITION_OUT);
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:Moving Calibration Mirror failed:"+
				    command,e);
			configDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1201);
			configDone.setErrorString(e.toString());
			configDone.setSuccessful(false);
			return configDone;
		}
		// move the fold mirror to the correct location
		sprat.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Moving fold mirror.");
		if(moveFold(multRunCommand,multRunDone) == false)
			return multRunDone;
		if(testAbort(multRunCommand,multRunDone) == true)
			return multRunDone;
		// get multrun data
		exposureLength = multRunCommand.getExposureTime();
		exposureCount = multRunCommand.getNumberExposures();
		standard = multRunCommand.getStandard();
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":processCommand:exposureLength = "+exposureLength+
			 " :exposureCount = "+exposureCount+" :standard = "+standard+".");
		// get fits headers
		clearFitsHeaders();
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":processCommand:getting FITS headers from properties.");
		if(setFitsHeaders(multRunCommand,multRunDone) == false)
			return multRunDone;
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":processCommand:getting FITS headers from ISS.");
		if(getFitsHeadersFromISS(multRunCommand,multRunDone) == false)
			return multRunDone;
		if(testAbort(multRunCommand,multRunDone) == true)
			return multRunDone;
		if(testAbort(multRunCommand,multRunDone) == true)
			return multRunDone;
		// call multrun command
		try
		{
			sendMultrunCommand(exposureLength,exposureCount,standard);
		}
		catch(Exception e )
		{
			sprat.error(this.getClass().getName()+":processCommand:sendMultrunCommand failed:",e);
			multRunDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1200);
			multRunDone.setErrorString(this.getClass().getName()+
						   ":processCommand:sendMultrunCommand failed:"+e);
			multRunDone.setSuccessful(false);
			return multRunDone;
		}
		// diddly real time data pipeline?
	// setup return values.
	// Only the filename (setFilename) is set to something useful.
	// setCounts,setSeeing,setXpix,setYpix 
	// setPhotometricity, setSkyBrightness, setSaturation are set to some nominal value.
		multRunDone.setFilename(lastFilename);
		// set to some blank value
		multRunDone.setSeeing(0.0f);
		multRunDone.setCounts(0.0f);
		multRunDone.setXpix(0.0f);
		multRunDone.setYpix(0.0f);
		multRunDone.setPhotometricity(0.0f);
		multRunDone.setSkyBrightness(0.0f);
		multRunDone.setSaturation(false);
		// standard success values
		multRunDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
		multRunDone.setErrorString("");
		multRunDone.setSuccessful(true);
	// return done object.
		sprat.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":processCommand:finished.");
		return multRunDone;
	}

	/**
	 * Send the multrun command to the C layer.
	 * @param exposureLength The exposure length in milliseconds.
	 * @param exposureCount The number of frames to take.
	 * @param standard If true this is a standard frame.
	 * @exception Exception Thrown if an error occurs.
	 * @see #multrunNumber
	 * @see #lastFilename
	 * @see ngat.sprat.command.MultrunCommand
	 * @see ngat.sprat.command.MultrunCommand#setAddress
	 * @see ngat.sprat.command.MultrunCommand#setPortNumber
	 * @see ngat.sprat.command.MultrunCommand#setCommand
	 * @see ngat.sprat.command.MultrunCommand#sendCommand
	 * @see ngat.sprat.command.MultrunCommand#getParsedReplyOK
	 * @see ngat.sprat.command.MultrunCommand#getReturnCode
	 * @see ngat.sprat.command.MultrunCommand#getParsedReply
	 * @see ngat.sprat.command.MultrunCommand#getMultrunNumber
	 * @see ngat.sprat.command.MultrunCommand#getFilename
	 */
	protected void sendMultrunCommand(int exposureLength, int exposureCount,boolean standard) throws Exception
	{
		MultrunCommand command = null;
		int portNumber,returnCode;
		String hostname = null;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultrunCommand:"+
			 "\n\t:exposureLength = "+exposureLength+
			 "\n\t:exposureCount = "+exposureCount+
			 "\n\t:standard = "+standard+".");
		command = new MultrunCommand();
		// configure C comms
		hostname = status.getProperty("sprat.ccd.c.hostname");
		portNumber = status.getPropertyInteger("sprat.ccd.c.port_number");
		command.setAddress(hostname);
		command.setPortNumber(portNumber);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultrunCommand:hostname = "+hostname+
			   " :port number = "+portNumber+".");
		command.setCommand(exposureLength,exposureCount,standard);
		// actually send the command to the C layer
		command.sendCommand();
		// check the parsed reply
		if(command.getParsedReplyOK() == false)
		{
			returnCode = command.getReturnCode();
			errorString = command.getParsedReply();
			sprat.log(Logging.VERBOSITY_TERSE,
				   "sendMultrunCommand:multrun command failed with return code "+
				   returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
					    ":sendMultrunCommand:Command failed with return code "+returnCode+
					    " and error string:"+errorString);
		}
		// extract data from successful reply.
		multrunNumber = command.getMultrunNumber();
		lastFilename = command.getFilename();
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultrunCommand:finished.");
	}
}

