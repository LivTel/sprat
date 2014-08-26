// BIASImplementation.java
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
 * This class provides the implementation for the BIAS command sent to a server using the
 * Java Message System.
 * @author Chris Motram
 * @version $Revision$
 */
public class BIASImplementation extends CALIBRATEImplementation implements JMSCommandImplementation
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
	 * The FITS filename returned by the bias command.
	 */
	protected String filename = null;

	/**
	 * Constructor.
	 */
	public BIASImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.BIAS&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.BIAS";
	}

	/**
	 * This method returns the BIAS command's acknowledge time. 
	 * <ul>
	 * <li>The acknowledge time is the default acknowledge time.
	 * </ul>
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see SpratTCPServerConnectionThread#getDefaultAcknowledgeTime
	 * @see #status
	 * @see #serverConnectionThread
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the BIAS command. 
	 * <ul>
	 * <li>clearFitsHeaders is called.
	 * <li>setFitsHeaders is called to get some FITS headers from the properties files and add them to the C layer.
	 * <li>getFitsHeadersFromISS is called to gets some FITS headers from the ISS (RCS). A filtered subset
	 *     is sent on to the C layer.
	 * <li>sendBiasCommand is called to actually send the bias command to the C layer.
	 * <li>A FILENAME_ACK is sent back to the client with the new filename.
	 * <li>reduceCalibrate is called to reduce the bias.
	 * <li>The done object is setup.
	 * </ul>
	 * @see #testAbort
	 * @see FITSImplementation#clearFitsHeaders
	 * @see FITSImplementation#setFitsHeaders
	 * @see FITSImplementation#getFitsHeadersFromISS
	 * @see #sendBiasCommand
	 * @see #filename
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		BIAS biasCommand = (BIAS)command;
		BIAS_DONE biasDone = new BIAS_DONE(command.getId());
		FILENAME_ACK filenameAck = null;

		sprat.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Started.");
		if(testAbort(biasCommand,biasDone) == true)
			return biasDone;
		// get fits headers
		try
		{
			clearFitsHeaders();
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:clearFitsHeaders failed:",e);
			biasDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+700);
			biasDone.setErrorString(this.getClass().getName()+
						   ":processCommand:clearFitsHeaders failed:"+e);
			biasDone.setSuccessful(false);
			return biasDone;
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":processCommand:getting FITS headers from properties.");
		if(setFitsHeaders(biasCommand,biasDone) == false)
			return biasDone;
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":processCommand:getting FITS headers from ISS.");
		if(getFitsHeadersFromISS(biasCommand,biasDone) == false)
			return biasDone;
		if(testAbort(biasCommand,biasDone) == true)
			return biasDone;
		// call bias command
		try
		{
			sendBiasCommand();
		}
		catch(Exception e )
		{
			sprat.error(this.getClass().getName()+":processCommand:sendBiasCommand failed:",e);
			biasDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+701);
			biasDone.setErrorString(this.getClass().getName()+
						":processCommand:sendBiasCommand failed:"+e);
			biasDone.setSuccessful(false);
			return biasDone;
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
			biasDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+702);
			biasDone.setErrorString(this.getClass().getName()+":processCommand:sendAcknowledge:"+
						e.toString());
			biasDone.setSuccessful(false);
			return biasDone;
		}
	// call pipeline to process data and get results
		if(reduceCalibrate(biasCommand,biasDone,filename) == false)
			return biasDone;
	// setup return values.
	// meanCounts and peakCounts set by pipelineProcess for last image reduced.
		biasDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
		biasDone.setErrorString("");
		biasDone.setSuccessful(true);
		sprat.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":processCommand:finished.");
		return biasDone;
	}

	/**
	 * Send the bias command to the C layer.
	 * @exception Exception Thrown if an error occurs.
	 * @see #filename
	 * @see #multrunNumber
	 * @see HardwareImplementation#ccdCLayerHostname
	 * @see HardwareImplementation#ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.BiasCommand
	 * @see ngat.sprat.ccd.command.BiasCommand#setAddress
	 * @see ngat.sprat.ccd.command.BiasCommand#setPortNumber
	 * @see ngat.sprat.ccd.command.BiasCommand#setCommand
	 * @see ngat.sprat.ccd.command.BiasCommand#sendCommand
	 * @see ngat.sprat.ccd.command.BiasCommand#getParsedReplyOK
	 * @see ngat.sprat.ccd.command.BiasCommand#getReturnCode
	 * @see ngat.sprat.ccd.command.BiasCommand#getParsedReply
	 * @see ngat.sprat.ccd.command.BiasCommand#getMultrunNumber
	 * @see ngat.sprat.ccd.command.BiasCommand#getFilename
	 */
	protected void sendBiasCommand() throws Exception
	{
		BiasCommand command = null;
		List filenameList = null;
		int returnCode;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendBiasCommand:Started.");
		command = new BiasCommand();
		// configure C comms
		command.setAddress(ccdCLayerHostname);
		command.setPortNumber(ccdCLayerPortNumber);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendBiasCommand:hostname = "+ccdCLayerHostname+
			   " :port number = "+ccdCLayerPortNumber+".");
		// actually send the command to the C layer
		command.sendCommand();
		// check the parsed reply
		if(command.getParsedReplyOK() == false)
		{
			returnCode = command.getReturnCode();
			errorString = command.getParsedReply();
			sprat.log(Logging.VERBOSITY_TERSE,
				   "sendBiasCommand:bias command failed with return code "+
				   returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
					    ":sendBiasCommand:Command failed with return code "+returnCode+
					    " and error string:"+errorString);
		}
		// extract data from successful reply.
		multrunNumber = command.getMultrunNumber();
		filenameList = command.getFilenameList();
		if(filenameList.size() != 1)
		{
			sprat.log(Logging.VERBOSITY_TERSE,
				  "sendBiasCommand:bias command returned wrong number of FITS filenames:"+
				  filenameList.size());
			throw new Exception(this.getClass().getName()+
				       ":sendBiasCommand:bias command returned wrong number of FITS filenames:"+
					    filenameList.size());
		}
		filename = (String)(filenameList.get(0));
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendBiasCommand:Finished.");
	}
}
