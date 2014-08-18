// MULTBIASImplementation.java
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
 * This class provides the implementation for the MULTBIAS command sent to a server using the
 * Java Message System.
 * @author Chris Motram
 * @version $Revision$
 */
public class MULTBIASImplementation extends CALIBRATEImplementation implements JMSCommandImplementation
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
	public MULTBIASImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.MULTBIAS&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.MULTBIAS";
	}

	/**
	 * This method returns the MULTBIAS command's acknowledge time. 
	 * <ul>
	 * <li>The time to be added for readout is retrieved from the sprat.multrun.acknowledge_time.readout property.
	 * <li>The acknowledge time is the readout time multiplied by the exposure count.
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
	 * @see MULTBIAS#getNumberExposures
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		MULTBIAS multBiasCommand = (MULTBIAS)command;
		ACK acknowledge = null;
		int exposureCount,ackTime=0,readoutAckTime;

		exposureCount = multBiasCommand.getNumberExposures();
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
			  ":calculateAcknowledgeTime:exposureCount = "+exposureCount+
			  " :readoutAckTime = "+readoutAckTime);
		ackTime = (readoutAckTime*exposureCount);
		sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":calculateAcknowledgeTime:ackTime = "+ackTime);
		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(ackTime+serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the MULTBIAS command. 
	 * <ul>
	 * <li>clearFitsHeaders is called.
	 * <li>setFitsHeaders is called to get some FITS headers from the properties files and add them to the C layer.
	 * <li>getFitsHeadersFromISS is called to gets some FITS headers from the ISS (RCS). A filtered subset
	 *     is sent on to the C layer.
	 * <li>sendMultBiasCommand is called to actually send the multbias command to the C layer.
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
		MULTBIAS multBiasCommand = (MULTBIAS)command;
		CALIBRATE_DP_ACK calibrateDpAck = null;
		MULTBIAS_DONE multBiasDone = new MULTBIAS_DONE(command.getId());
		String filename = null;
		int exposureCount,index;
		boolean retval;

		sprat.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Started.");
		if(testAbort(multBiasCommand,multBiasDone) == true)
			return multBiasDone;
		// get fits headers
		try
		{
			clearFitsHeaders();
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:clearFitsHeaders failed:",e);
			multBiasDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2600);
			multBiasDone.setErrorString(this.getClass().getName()+
						   ":processCommand:clearFitsHeaders failed:"+e);
			multBiasDone.setSuccessful(false);
			return multBiasDone;
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":processCommand:getting FITS headers from properties.");
		if(setFitsHeaders(multBiasCommand,multBiasDone) == false)
			return multBiasDone;
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":processCommand:getting FITS headers from ISS.");
		if(getFitsHeadersFromISS(multBiasCommand,multBiasDone) == false)
			return multBiasDone;
		if(testAbort(multBiasCommand,multBiasDone) == true)
			return multBiasDone;
		// call multbias command
		try
		{
			sendMultBiasCommand(multBiasCommand.getNumberExposures());
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:sendMultBiasCommand failed:",e);
			multBiasDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2601);
			multBiasDone.setErrorString(this.getClass().getName()+
						":processCommand:sendMultBiasCommand failed:"+e);
			multBiasDone.setSuccessful(false);
			return multBiasDone;
		}
		// send acknowledge to say frame is completed.
		//filenameAck = new FILENAME_ACK(command.getId());
		//filenameAck.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
		//filenameAck.setFilename(filename);
		//try
		//{
		//	serverConnectionThread.sendAcknowledge(filenameAck);
		//}
		//catch(IOException e)
		//{
		//	sprat.error(this.getClass().getName()+":processCommand:sendAcknowledge:"+command+":",e);
		//	multBiasDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+);
		//	multBiasDone.setErrorString(this.getClass().getName()+":processCommand:sendAcknowledge:"+
		//				    e.toString());
		//	multBiasDone.setSuccessful(false);
		//	return multBiasDone;
		//}
	// call pipeline to process data and get results
		retval = true;
		index = 0;
		while(retval&&(index < filenameList.size()))
		{
			filename = (String)filenameList.get(index);
			if(reduceCalibrate(multBiasCommand,multBiasDone,filename) == false)
				return multBiasDone;
			// send acknowledge to say frame has been reduced.
			calibrateDpAck = new CALIBRATE_DP_ACK(command.getId());
			calibrateDpAck.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
			// copy Data Pipeline results from DONE to ACK
			calibrateDpAck.setFilename(multBiasDone.getFilename());
			calibrateDpAck.setPeakCounts(multBiasDone.getPeakCounts());
			calibrateDpAck.setMeanCounts(multBiasDone.getMeanCounts());
			try
			{
				serverConnectionThread.sendAcknowledge(calibrateDpAck);
			}
			catch(IOException e)
			{
				retval = false;
				sprat.error(this.getClass().getName()+
					    ":processCommand:sendAcknowledge(DP):"+command+":"+e.toString());
				multBiasDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2602);
				multBiasDone.setErrorString(e.toString());
				multBiasDone.setSuccessful(false);
				return multBiasDone;
			}
			if(testAbort(multBiasCommand,multBiasDone) == true)
			{
				retval = false;
			}
			index++;
		}// end while on MULTRUN exposures
		// setup return values.
		// meanCounts and peakCounts set by pipelineProcess for last image reduced.
		multBiasDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
		multBiasDone.setErrorString("");
		multBiasDone.setSuccessful(true);
		sprat.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":processCommand:finished.");
		return multBiasDone;
	}

	/**
	 * Send the multbias command to the C layer.
	 * @param exposureCount The number of bias frames to generate.
	 * @exception Exception Thrown if an error occurs.
	 * @see #filenameList
	 * @see #multrunNumber
	 * @see ngat.sprat.ccd.command.MultBiasCommand
	 * @see ngat.sprat.ccd.command.MultBiasCommand#setAddress
	 * @see ngat.sprat.ccd.command.MultBiasCommand#setPortNumber
	 * @see ngat.sprat.ccd.command.MultBiasCommand#setCommand
	 * @see ngat.sprat.ccd.command.MultBiasCommand#sendCommand
	 * @see ngat.sprat.ccd.command.MultBiasCommand#getParsedReplyOK
	 * @see ngat.sprat.ccd.command.MultBiasCommand#getReturnCode
	 * @see ngat.sprat.ccd.command.MultBiasCommand#getParsedReply
	 * @see ngat.sprat.ccd.command.MultBiasCommand#getMultrunNumber
	 * @see ngat.sprat.ccd.command.MultBiasCommand#getFilename
	 */
	protected void sendMultBiasCommand(int exposureCount) throws Exception
	{
		MultBiasCommand command = null;
		int portNumber,returnCode;
		String hostname = null;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendBiasCommand:Started.");
		command = new MultBiasCommand();
		// configure C comms
		hostname = status.getProperty("sprat.ccd.c.hostname");
		portNumber = status.getPropertyInteger("sprat.ccd.c.port_number");
		command.setAddress(hostname);
		command.setPortNumber(portNumber);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendBiasCommand:hostname = "+hostname+
			   " :port number = "+portNumber+".");
		command.setCommand(exposureCount);
		// actually send the command to the C layer
		command.sendCommand();
		// check the parsed reply
		if(command.getParsedReplyOK() == false)
		{
			returnCode = command.getReturnCode();
			errorString = command.getParsedReply();
			sprat.log(Logging.VERBOSITY_TERSE,
				   "sendMultBiasCommand:bias command failed with return code "+
				   returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
					    ":sendMultBiasCommand:Command failed with return code "+returnCode+
					    " and error string:"+errorString);
		}
		// extract data from successful reply.
		multrunNumber = command.getMultrunNumber();
		filenameList = command.getFilenameList();
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultBiasCommand:Finished.");
	}
}
