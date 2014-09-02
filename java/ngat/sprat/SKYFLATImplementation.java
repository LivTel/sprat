// SKYFLATImplementation.java
// $HeadURL$
package ngat.sprat;

import java.io.*;
import java.lang.*;
import java.util.List;

import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.phase2.SpratConfig;
import ngat.sprat.ccd.command.*;
import ngat.sprat.mechanism.command.*;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the SKYFLAT command sent to a server using the
 * Java Message System.
 * @author Chris Motram
 * @version $Revision: 36 $
 */
public class SKYFLATImplementation extends CALIBRATEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The FITS filename returned by the multrun command.
	 */
	protected String filename = null;
	/**
	 * The multrun number used for FITS filename for this command.
	 */
	protected int multrunNumber = 0;

	/**
	 * Constructor.
	 */
	public SKYFLATImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.SKYFLAT&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.SKYFLAT";
	}

	/**
	 * This method gets the SKYFLAT command's acknowledge time. The SKYFLATs exposure time is based on the
	 * following:
	 * <ul>
	 * <li>the current local time.
	 * <li>the date
	 * <li>filter
	 * <li>the supplied current telescope position. 
	 * <li>etc.(!) 
	 * </ul> 
	 * An algorithm for this method needs to be devised.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see SpratTCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		SKYFLAT skyFlatCommand = (SKYFLAT)command;
		ACK acknowledge = null;
		int exposureTime = 0;

		acknowledge = new ACK(command.getId());

		if(skyFlatCommand.getUseTime())
			exposureTime = skyFlatCommand.getExposureTime();
		else
		{
			// diddly - this needs to calculated from the variables above.
			// as done in processCommand
			exposureTime = 10*1000;
		}
		acknowledge.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime()+exposureTime);
		return acknowledge;
	}

	/**
	 * This method implements the SKYFLAT command. 
	 * <ul>
	 * </ul>
	 * @see #testAbort
	 * @see #sendMultrunCommand
	 * @see HardwareImplementation#moveFold
	 * @see HardwareImplementation#moveMirror
	 * @see FITSImplementation#clearFitsHeaders
	 * @see FITSImplementation#setFitsHeaders
	 * @see FITSImplementation#getFitsHeadersFromISS
	 * @see EXPOSEImplementation#reduceExpose
	 * @see ngat.phase2.SpratConfig#POSITION_OUT
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		SKYFLAT skyFlatCommand = (SKYFLAT)command;
		SKYFLAT_DONE skyFlatDone = new SKYFLAT_DONE(command.getId());
		int exposureLength;
		String filename = null;
		boolean retval;

		sprat.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Started.");
		if(testAbort(skyFlatCommand,skyFlatDone) == true)
			return skyFlatDone;
		// move the sprat calibration mirror out of the beam
		sprat.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
			  ":processCommand:Moving calibration mirror.");
		try
		{
			// get mechanism arduino connection details
			// Now called in HardwareImplementation.init
			// getMechanismConfig();
			moveMirror(SpratConfig.POSITION_OUT);
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:Moving Calibration Mirror failed:"+
				    command,e);
			skyFlatDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1800);
			skyFlatDone.setErrorString("Moving Calibration Mirror failed:"+e.toString());
			skyFlatDone.setSuccessful(false);
			return skyFlatDone;
		}
		// move the fold mirror to the correct location
		sprat.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Moving fold mirror.");
		if(moveFold(skyFlatCommand,skyFlatDone) == false)
			return skyFlatDone;
		if(testAbort(skyFlatCommand,skyFlatDone) == true)
			return skyFlatDone;
		// exposure length
		if(skyFlatCommand.getUseTime())
			exposureLength = skyFlatCommand.getExposureTime();
		else
			exposureLength = 10000;
		
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":processCommand:exposureLength = "+exposureLength+".");
		// get fits headers
		try
		{
			clearFitsHeaders();
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:clearFitsHeaders failed:",e);
			skyFlatDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1801);
			skyFlatDone.setErrorString(this.getClass().getName()+
						   ":processCommand:clearFitsHeaders failed:"+e);
			skyFlatDone.setSuccessful(false);
			return skyFlatDone;
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":processCommand:getting FITS headers from properties.");
		if(setFitsHeaders(skyFlatCommand,skyFlatDone) == false)
			return skyFlatDone;
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":processCommand:getting FITS headers from ISS.");
		if(getFitsHeadersFromISS(skyFlatCommand,skyFlatDone) == false)
			return skyFlatDone;
		if(testAbort(skyFlatCommand,skyFlatDone) == true)
			return skyFlatDone;
		// call multrun command
		try
		{
			sendMultrunCommand(exposureLength);
		}
		catch(Exception e )
		{
			sprat.error(this.getClass().getName()+":processCommand:sendMultrunCommand failed:",e);
			skyFlatDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1802);
			skyFlatDone.setErrorString(this.getClass().getName()+
						   ":processCommand:sendMultrunCommand failed:"+e);
			skyFlatDone.setSuccessful(false);
			return skyFlatDone;
		}
		// send filename ACKs
		if(!sendFilenameAck(skyFlatCommand,skyFlatDone,filename))
				return skyFlatDone;
		// call pipeline to process data and get results
		// do reduction.
		if(reduceCalibrate(skyFlatCommand,skyFlatDone,filename) == false)
			retval = false;
	// set return values to indicate success.	
		skyFlatDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
		skyFlatDone.setErrorString("");
		skyFlatDone.setSuccessful(true);
	// return done object.
		return skyFlatDone;
	}

	/**
	 * Send the multrun command to the C layer.
	 * @param exposureLength The exposure length in milliseconds.
	 * @exception Exception Thrown if an error occurs.
	 * @see #multrunNumber
	 * @see #filename
	 * @see HardwareImplementation#ccdCLayerHostname
	 * @see HardwareImplementation#ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.MultrunCommand
	 * @see ngat.sprat.ccd.command.MultrunCommand#setAddress
	 * @see ngat.sprat.ccd.command.MultrunCommand#setPortNumber
	 * @see ngat.sprat.ccd.command.MultrunCommand#setCommand
	 * @see ngat.sprat.ccd.command.MultrunCommand#sendCommand
	 * @see ngat.sprat.ccd.command.MultrunCommand#getParsedReplyOK
	 * @see ngat.sprat.ccd.command.MultrunCommand#getReturnCode
	 * @see ngat.sprat.ccd.command.MultrunCommand#getParsedReply
	 * @see ngat.sprat.ccd.command.MultrunCommand#getMultrunNumber
	 * @see ngat.sprat.ccd.command.MultrunCommand#getFilenameList
	 */
	protected void sendMultrunCommand(int exposureLength) throws Exception
	{
		MultrunCommand command = null;
		List<String> filenameList = null;
		int returnCode;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultrunCommand:exposureLength = "+exposureLength+".");
		// configure C comms
		command.setAddress(ccdCLayerHostname);
		command.setPortNumber(ccdCLayerPortNumber);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultrunCommand:hostname = "+ccdCLayerHostname+
			   " :port number = "+ccdCLayerPortNumber+".");
		command.setCommand(exposureLength,1,MultrunCommand.EXPOSURE_TYPE_SKYFLAT);
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
		filenameList = command.getFilenameList();
		if(filenameList.size() != 1)
		{
			sprat.log(Logging.VERBOSITY_TERSE,
				  "sendMultrunCommand:multrun command returned wrong number of FITS filenames:"+
				  filenameList.size());
			throw new Exception(this.getClass().getName()+
				       ":sendMultrunCommand:multrun command returned wrong number of FITS filenames:"+
					    filenameList.size());
		}
		filename = (String)(filenameList.get(0));
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultrunCommand:finished with multrun number "+
			  multrunNumber+" and filename"+filename+".");
	}

	/**
	 * Send a FILENAME_ACK back to the client with the specified filename.
	 * An Ack time retrieved from the config: "sprat.multrun.acknowledge_time.data_pipeline"
	 * plus the default ACK time is returned.
	 * @param skyFlatCommand The SKYFLAT command.
	 * @param skyFlatDone The SKYFLAT_DONE, the error fields are filled in if sending the ACK fails.
	 * @param filename The FITS filename to send.
	 * @return The routine returns true on success and false on failure.
	 */
	protected boolean sendFilenameAck(SKYFLAT skyFlatCommand,SKYFLAT_DONE skyFlatDone,String filename)
	{
		FILENAME_ACK filenameAck = null;
		int dataPipelineAckTime;

		filenameAck = new FILENAME_ACK(skyFlatCommand.getId());
		dataPipelineAckTime = status.
			getPropertyInteger("sprat.multrun.acknowledge_time.data_pipeline");
		filenameAck.setTimeToComplete(dataPipelineAckTime+serverConnectionThread.getDefaultAcknowledgeTime());
		filenameAck.setFilename(filename);
		try
		{
			serverConnectionThread.sendAcknowledge(filenameAck);
		}
		catch(IOException e)
		{
			sprat.error(this.getClass().getName()+
				    ":sendFilenameAck:sendAcknowledge:"+skyFlatCommand+":"+e.toString(),e);
			skyFlatDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1803);
			skyFlatDone.setErrorString("sendFilenameAck:sendAcknowledge:"+e.toString());
			skyFlatDone.setSuccessful(false);
			return false;
		}
		return true;
	}

}
