// DARKImplementation.java
// $HeadURL$
package ngat.sprat;

import java.io.*;
import java.lang.*;
import java.util.*;

import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.sprat.ccd.command.*;
import ngat.sprat.mechanism.command.*;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the DARK command sent to a server using the
 * Java Message System.
 * @author Chris Motram
 * @version $Revision$
 */
public class DARKImplementation extends CALIBRATEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The multrun number used for FITS filenames for this command.
	 */
	protected int multrunNumber;
	/**
	 * The FITS filename returned by the dark command.
	 */
	protected String filename = null;

	/**
	 * Constructor.
	 */
	public DARKImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.DARK&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.DARK";
	}

	/**
	 * This method returns the DARK command's acknowledge time. 
	 * <ul>
	 * <li>The time to be added for readout is retrieved from the sprat.multrun.acknowledge_time.readout property.
	 * <li>The acknowledge time is the  exposure length plus the readout time plus the default acknowledge time.
	 * </ul>
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see SpratTCPServerConnectionThread#getDefaultAcknowledgeTime
	 * @see #sprat
	 * @see #status
	 * @see #serverConnectionThread
	 * @see ngat.sprat.SpratStatus#getPropertyInteger
	 * @see ngat.message.ISS_INST.DARK#getExposureTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		DARK darkCommand = (DARK)command;
		ACK acknowledge = null;
		int exposureLength,readoutAckTime,ackTime=0;

		exposureLength = darkCommand.getExposureTime();
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
			  " :readoutAckTime = "+readoutAckTime);
		ackTime = exposureLength+readoutAckTime+serverConnectionThread.getDefaultAcknowledgeTime();
		sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":calculateAcknowledgeTime:ackTime = "+ackTime);
		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(ackTime);
		return acknowledge;
	}

	/**
	 * This method implements the DARK command. 
	 * <ul>
	 * <li>clearFitsHeaders is called.
	 * <li>setFitsHeaders is called to get some FITS headers from the properties files and add them to the C layer.
	 * <li>getFitsHeadersFromISS is called to gets some FITS headers from the ISS (RCS). A filtered subset
	 *     is sent on to the C layer.
	 * <li>sendDarkCommand is called to actually send the dark command to the C layer.
	 * <li>A FILENAME_ACK is sent back to the client with the new filename.
	 * <li>reduceCalibrate is called to reduce the dark.
	 * <li>The done object is setup.
	 * </ul>
	 * @see #testAbort
	 * @see FITSImplementation#clearFitsHeaders
	 * @see FITSImplementation#setFitsHeaders
	 * @see FITSImplementation#getFitsHeadersFromISS
	 * @see #sendDarkCommand
	 * @see #filename
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		DARK darkCommand = (DARK)command;
		DARK_DONE darkDone = new DARK_DONE(command.getId());
		FILENAME_ACK filenameAck = null;

		sprat.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Started.");
		if(testAbort(darkCommand,darkDone) == true)
			return darkDone;
		// get fits headers
		try
		{
			clearFitsHeaders();
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:clearFitsHeaders failed:",e);
			darkDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+900);
			darkDone.setErrorString(this.getClass().getName()+
						":processCommand:clearFitsHeaders failed:"+e);
			darkDone.setSuccessful(false);
			return darkDone;
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			  ":processCommand:getting FITS headers from properties.");
		if(setFitsHeaders(darkCommand,darkDone) == false)
			return darkDone;
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":processCommand:getting FITS headers from ISS.");
		if(getFitsHeadersFromISS(darkCommand,darkDone) == false)
			return darkDone;
		if(testAbort(darkCommand,darkDone) == true)
			return darkDone;
		// call dark command
		try
		{
			sendDarkCommand(darkCommand.getExposureTime());
		}
		catch(Exception e )
		{
			sprat.error(this.getClass().getName()+":processCommand:sendDarkCommand failed:",e);
			darkDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+901);
			darkDone.setErrorString(this.getClass().getName()+
						":processCommand:sendDarkCommand failed:"+e);
			darkDone.setSuccessful(false);
			return darkDone;
		}
		// send acknowledge to say frame is completed.
		filenameAck = new FILENAME_ACK(command.getId());
		filenameAck.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
		filenameAck.setFilename(filename);
		try
		{
			serverConnectionThread.sendAcknowledge(filenameAck);
		}
		catch(IOException e)
		{
			sprat.error(this.getClass().getName()+":processCommand:sendAcknowledge:"+command+":",e);
			darkDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+902);
			darkDone.setErrorString(this.getClass().getName()+":processCommand:sendAcknowledge:"+
						e.toString());
			darkDone.setSuccessful(false);
			return darkDone;
		}
	// call pipeline to process data and get results
		if(reduceCalibrate(darkCommand,darkDone,filename) == false)
			return darkDone;
	// setup return values.
	// meanCounts and peakCounts set by pipelineProcess for last image reduced.
		darkDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
		darkDone.setErrorString("");
		darkDone.setSuccessful(true);
		sprat.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":processCommand:finished.");
		return darkDone;
	}

	/**
	 * Send the dark command to the C layer.
	 * @param exposureLength The exposure length in milliseconds.
	 * @exception Exception Thrown if an error occurs.
	 * @see #filename
	 * @see #multrunNumber
	 * @see HardwareImplementation#ccdCLayerHostname
	 * @see HardwareImplementation#ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.DarkCommand
	 * @see ngat.sprat.ccd.command.DarkCommand#setAddress
	 * @see ngat.sprat.ccd.command.DarkCommand#setPortNumber
	 * @see ngat.sprat.ccd.command.DarkCommand#setCommand
	 * @see ngat.sprat.ccd.command.DarkCommand#sendCommand
	 * @see ngat.sprat.ccd.command.DarkCommand#getParsedReplyOK
	 * @see ngat.sprat.ccd.command.DarkCommand#getReturnCode
	 * @see ngat.sprat.ccd.command.DarkCommand#getParsedReply
	 * @see ngat.sprat.ccd.command.DarkCommand#getMultrunNumber
	 * @see ngat.sprat.ccd.command.DarkCommand#getFilename
	 */
	protected void sendDarkCommand(int exposureLength) throws Exception
	{
		DarkCommand command = null;
		List filenameList = null;
		int portNumber,returnCode;
		String hostname = null;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendDarkCommand(exposure length= "+exposureLength+
			  " ms):Started.");
		command = new DarkCommand();
		// configure C comms
		command.setAddress(ccdCLayerHostname);
		command.setPortNumber(ccdCLayerPortNumber);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendDarkCommand:hostname = "+ccdCLayerHostname+
			  " :port number = "+ccdCLayerPortNumber+".");
		command.setCommand(exposureLength);
		// actually send the command to the C layer
		command.sendCommand();
		// check the parsed reply
		if(command.getParsedReplyOK() == false)
		{
			returnCode = command.getReturnCode();
			errorString = command.getParsedReply();
			sprat.log(Logging.VERBOSITY_TERSE,
				   "sendDarkCommand:dark command failed with return code "+
				   returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
					    ":sendDarkCommand:Command failed with return code "+returnCode+
					    " and error string:"+errorString);
		}
		// extract data from successful reply.
		multrunNumber = command.getMultrunNumber();
		filenameList = command.getFilenameList();
		if(filenameList.size() != 1)
		{
			sprat.log(Logging.VERBOSITY_TERSE,
				  "sendDarkCommand:dark command returned wrong number of FITS filenames:"+
				  filenameList.size());
			throw new Exception(this.getClass().getName()+
				       ":sendDarkCommand:dark command returned wrong number of FITS filenames:"+
					    filenameList.size());
		}
		filename = (String)(filenameList.get(0));
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendDarkCommand:Finished.");
	}
}
