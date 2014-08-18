// MULTDARKImplementation.java
// $HeadURL$
package ngat.sprat;

import java.io.*;
import java.lang.*;
import java.util.*;

import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.sprat.ccd.command.*;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the MULTDARK command sent to a server using the
 * Java Message System.
 * @author Chris Motram
 * @version $Revision$
 */
public class MULTDARKImplementation extends CALIBRATEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The list of FITS filenames returned by the multbias command.
	 */
	protected List<String> filenameList = null;
	/**
	 * The multrun number used for FITS filenames for this command.
	 */
	protected int multrunNumber;

	/**
	 * Constructor.
	 */
	public MULTDARKImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.MULTDARK&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.MULTDARK";
	}

	/**
	 * This method returns the MULTDARK command's acknowledge time. 
	 * <ul>
	 * <li>The time to be added for readout is retrieved from the sprat.multrun.acknowledge_time.readout property.
	 * <li>The acknowledge time is the exposure length plus the readout time multiplied by the exposure count.
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
	 * @see MULTDARK#getExposureTime
	 * @see MULTDARK#getNumberExposures
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		MULTDARK multDarkCommand = (MULTDARK)command;
		ACK acknowledge = null;
		int exposureLength,exposureCount,ackTime=0,readoutAckTime;

		exposureLength = multDarkCommand.getExposureTime();
		exposureCount = multDarkCommand.getNumberExposures();
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
			  " exposureCount = "+exposureCount+" :readoutAckTime = "+readoutAckTime);
		ackTime = ((exposureLength+readoutAckTime)*exposureCount);
		sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":calculateAcknowledgeTime:ackTime = "+ackTime);
		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(ackTime+serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the MULTDARK command. 
	 * <ul>
	 * <li>clearFitsHeaders is called.
	 * <li>setFitsHeaders is called to get some FITS headers from the properties files and add them to the C layer.
	 * <li>getFitsHeadersFromISS is called to gets some FITS headers from the ISS (RCS). A filtered subset
	 *     is sent on to the C layer.
	 * <li>sendMultDarkCommand is called to actually send the multdark command to the C layer.
	 * <li>For each filename returned we call reduceCalibrate to reduce the exposure. A CALIBRATE_DP_ACK 
	 *     is sent back to the client after each reduction.
	 * <li>The done object is setup.
	 * </ul>
	 * @see #filenameList
	 * @see #testAbort
	 * @see #sendMultBiasCommand
	 * @see FITSImplementation#clearFitsHeaders
	 * @see FITSImplementation#setFitsHeaders
	 * @see FITSImplementation#getFitsHeadersFromISS
	 * @see CALIBRATEImplementation#reduceCalibrate
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		MULTDARK multDarkCommand = (MULTDARK)command;
		CALIBRATE_DP_ACK calibrateDpAck = null;
		MULTDARK_DONE multDarkDone = new MULTDARK_DONE(command.getId());
		String filename = null;
		int exposureCount,index;
		boolean retval;

		sprat.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Started.");
		if(testAbort(multDarkCommand,multDarkDone) == true)
			return multDarkDone;
		// get fits headers
		try
		{
			clearFitsHeaders();
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:clearFitsHeaders failed:",e);
			multDarkDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2800);
			multDarkDone.setErrorString(this.getClass().getName()+
						   ":processCommand:clearFitsHeaders failed:"+e);
			multDarkDone.setSuccessful(false);
			return multDarkDone;
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":processCommand:getting FITS headers from properties.");
		if(setFitsHeaders(multDarkCommand,multDarkDone) == false)
			return multDarkDone;
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":processCommand:getting FITS headers from ISS.");
		if(getFitsHeadersFromISS(multDarkCommand,multDarkDone) == false)
			return multDarkDone;
		if(testAbort(multDarkCommand,multDarkDone) == true)
			return multDarkDone;
		// call multdark command
		try
		{
			sendMultDarkCommand(multDarkCommand.getExposureTime(),multDarkCommand.getNumberExposures());
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:sendMultDarkCommand failed:",e);
			multDarkDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2801);
			multDarkDone.setErrorString(this.getClass().getName()+
						    ":processCommand:sendMultDarkCommand failed:"+e);
			multDarkDone.setSuccessful(false);
			return multDarkDone;
		}
	// call pipeline to process data and get results
		retval = true;
		index = 0;
		while(retval&&(index < filenameList.size()))
		{
			filename = (String)filenameList.get(index);
			if(reduceCalibrate(multDarkCommand,multDarkDone,filename) == false)
				return multDarkDone;
			// send acknowledge to say frame has been reduced.
			calibrateDpAck = new CALIBRATE_DP_ACK(command.getId());
			calibrateDpAck.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
			// copy Data Pipeline results from DONE to ACK
			calibrateDpAck.setFilename(multDarkDone.getFilename());
			calibrateDpAck.setPeakCounts(multDarkDone.getPeakCounts());
			calibrateDpAck.setMeanCounts(multDarkDone.getMeanCounts());
			try
			{
				serverConnectionThread.sendAcknowledge(calibrateDpAck);
			}
			catch(IOException e)
			{
				retval = false;
				sprat.error(this.getClass().getName()+
					    ":processCommand:sendAcknowledge(DP):"+command+":",e);
				multDarkDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2802);
				multDarkDone.setErrorString(e.toString());
				multDarkDone.setSuccessful(false);
				return multDarkDone;
			}
			if(testAbort(multDarkCommand,multDarkDone) == true)
			{
				retval = false;
			}
			index++;
		}// end while on MULTRUN exposures
		// setup return values.
		// meanCounts and peakCounts set by pipelineProcess for last image reduced.
		multDarkDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
		multDarkDone.setErrorString("");
		multDarkDone.setSuccessful(true);
		sprat.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":processCommand:finished.");
		return multDarkDone;
	}

	/**
	 * Send the multdark command to the C layer.
	 * @param exposureLength The exposure length of each dark frame in milliseconds.
	 * @param exposureCount The number of dark frames to generate.
	 * @exception Exception Thrown if an error occurs.
	 * @see #filenameList
	 * @see #multrunNumber
	 * @see ngat.sprat.ccd.command.MultDarkCommand
	 * @see ngat.sprat.ccd.command.MultDarkCommand#setAddress
	 * @see ngat.sprat.ccd.command.MultDarkCommand#setPortNumber
	 * @see ngat.sprat.ccd.command.MultDarkCommand#setCommand
	 * @see ngat.sprat.ccd.command.MultDarkCommand#sendCommand
	 * @see ngat.sprat.ccd.command.MultDarkCommand#getParsedReplyOK
	 * @see ngat.sprat.ccd.command.MultDarkCommand#getReturnCode
	 * @see ngat.sprat.ccd.command.MultDarkCommand#getParsedReply
	 * @see ngat.sprat.ccd.command.MultDarkCommand#getMultrunNumber
	 * @see ngat.sprat.ccd.command.MultDarkCommand#getFilename
	 */
	protected void sendMultDarkCommand(int exposureLength,int exposureCount) throws Exception
	{
		MultDarkCommand command = null;
		int portNumber,returnCode;
		String hostname = null;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendDarkCommand:Started.");
		command = new MultDarkCommand();
		// configure C comms
		hostname = status.getProperty("sprat.ccd.c.hostname");
		portNumber = status.getPropertyInteger("sprat.ccd.c.port_number");
		command.setAddress(hostname);
		command.setPortNumber(portNumber);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendDarkCommand:hostname = "+hostname+
			   " :port number = "+portNumber+".");
		command.setCommand(exposureLength,exposureCount);
		// actually send the command to the C layer
		command.sendCommand();
		// check the parsed reply
		if(command.getParsedReplyOK() == false)
		{
			returnCode = command.getReturnCode();
			errorString = command.getParsedReply();
			sprat.log(Logging.VERBOSITY_TERSE,
				   "sendMultDarkCommand:multdark command failed with return code "+
				   returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
					    ":sendMultDarkCommand:Command failed with return code "+returnCode+
					    " and error string:"+errorString);
		}
		// extract data from successful reply.
		multrunNumber = command.getMultrunNumber();
		filenameList = command.getFilenameList();
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultDarkCommand:Finished.");
	}
}


