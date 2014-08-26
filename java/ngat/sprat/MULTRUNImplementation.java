// MULTRUNImplementation.java
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
 * This class provides the implementation for the MULTRUN command sent to a server using the
 * Java Message System.
 * @author Chris Motram
 * @version $Revision$
 */
public class MULTRUNImplementation extends EXPOSEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The list of FITS filenames returned by the multrun command.
	 */
	protected List<String> filenameList = null;
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
	 * <li>For each filename returned we call reduceExpose to reduce the exposure. A MULTRUN_DP_ACK is sent back
	 *     to the client after each reduction.
	 * <li>The done object is setup.
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
		MULTRUN multRunCommand = (MULTRUN)command;
		MULTRUN_ACK multRunAck = null;
		MULTRUN_DP_ACK multRunDpAck = null;
		MULTRUN_DONE multRunDone = new MULTRUN_DONE(command.getId());
		String filename = null;
		int exposureLength,exposureCount,index;
		boolean standard,retval;

		sprat.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Started.");
		if(testAbort(multRunCommand,multRunDone) == true)
			return multRunDone;
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
			multRunDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1201);
			multRunDone.setErrorString(e.toString());
			multRunDone.setSuccessful(false);
			return multRunDone;
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
		try
		{
			clearFitsHeaders();
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:clearFitsHeaders failed:",e);
			multRunDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1202);
			multRunDone.setErrorString(this.getClass().getName()+
						   ":processCommand:clearFitsHeaders failed:"+e);
			multRunDone.setSuccessful(false);
			return multRunDone;
		}
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
		// send filename ACKs
		for(int i = 0; i < filenameList.size(); i++ )
		{
			filename = (String)filenameList.get(i);
			if(!sendMultrunAck(multRunCommand,multRunDone,filename))
				return multRunDone;
		}
	// call pipeline to process data and get results
		retval = true;
		if(multRunCommand.getPipelineProcess())
		{
			index = 0;
			while(retval&&(index < filenameList.size()))
			{
				filename = (String)filenameList.get(index);
			// do reduction.
				if(reduceExpose(multRunCommand,multRunDone,filename) == false)
					retval = false;
			// send acknowledge to say frame has been reduced.
				multRunDpAck = new MULTRUN_DP_ACK(command.getId());
				multRunDpAck.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
			// copy Data Pipeline results from DONE to ACK
				multRunDpAck.setFilename(multRunDone.getFilename());
				multRunDpAck.setCounts(multRunDone.getCounts());
				multRunDpAck.setSeeing(multRunDone.getSeeing());
				multRunDpAck.setXpix(multRunDone.getXpix());
				multRunDpAck.setYpix(multRunDone.getYpix());
				multRunDpAck.setPhotometricity(multRunDone.getPhotometricity());
				multRunDpAck.setSkyBrightness(multRunDone.getSkyBrightness());
				multRunDpAck.setSaturation(multRunDone.getSaturation());
				try
				{
					serverConnectionThread.sendAcknowledge(multRunDpAck);
				}
				catch(IOException e)
				{
					retval = false;
					sprat.error(this.getClass().getName()+
						":processCommand:sendAcknowledge(DP):"+command+":"+e.toString());
					multRunDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1203);
					multRunDone.setErrorString(e.toString());
					multRunDone.setSuccessful(false);
					return multRunDone;
				}
				if(testAbort(multRunCommand,multRunDone) == true)
				{
					retval = false;
				}
				index++;
			}// end while on MULTRUN exposures
		}// end if Data Pipeline is to be called
		else
		{
		// no pipeline processing occured, set return value to something bland.
		// set filename to last filename exposed.
			multRunDone.setFilename(filename);
			multRunDone.setCounts(0.0f);
			multRunDone.setSeeing(0.0f);
			multRunDone.setXpix(0.0f);
			multRunDone.setYpix(0.0f);
			multRunDone.setPhotometricity(0.0f);
			multRunDone.setSkyBrightness(0.0f);
			multRunDone.setSaturation(false);
		}
	// if a failure occurs, return now
		if(!retval)
			return multRunDone;
	// setup return values.
	// setCounts,setFilename,setSeeing,setXpix,setYpix 
	// setPhotometricity, setSkyBrightness, setSaturation set by reduceExpose for last image reduced.
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
	 * @see #filenameList
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
	protected void sendMultrunCommand(int exposureLength, int exposureCount,boolean standard) throws Exception
	{
		MultrunCommand command = null;
		int returnCode;
		String exposureTypeString = null;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultrunCommand:"+
			 "\n\t:exposureLength = "+exposureLength+
			 "\n\t:exposureCount = "+exposureCount+
			 "\n\t:standard = "+standard+".");
		if(standard)
			exposureTypeString = MultrunCommand.EXPOSURE_TYPE_STANDARD;
		else
			exposureTypeString = MultrunCommand.EXPOSURE_TYPE_EXPOSURE;
		command = new MultrunCommand();
		// configure C comms
		command.setAddress(ccdCLayerHostname);
		command.setPortNumber(ccdCLayerPortNumber);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultrunCommand:hostname = "+ccdCLayerHostname+
			   " :port number = "+ccdCLayerPortNumber+".");
		command.setCommand(exposureLength,exposureCount,exposureTypeString);
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
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultrunCommand:finished with multrun number "+
			  multrunNumber+" and "+filenameList.size()+" filnames.");
	}


	/**
	 * Send a MULTRUN_ACK back to the client with the specified filename.
	 * An Ack time retrieved from the config: "sprat.multrun.acknowledge_time.data_pipeline"
	 * plus the default ACK time is returned.
	 * @param multRunCommand The MULTRUN command.
	 * @param multRunDone The MULTRUN_DONE, the error fields are filled in if sending the ACK fails.
	 * @param filename The FITS filename to send.
	 * @return The routine returns true on success and false on failure.
	 */
	protected boolean sendMultrunAck(MULTRUN multRunCommand,MULTRUN_DONE multRunDone,String filename)
	{
		MULTRUN_ACK multRunAck = null;
		int dataPipelineAckTime;

		multRunAck = new MULTRUN_ACK(multRunCommand.getId());
		dataPipelineAckTime = status.
			getPropertyInteger("sprat.multrun.acknowledge_time.data_pipeline");
		multRunAck.setTimeToComplete(dataPipelineAckTime+serverConnectionThread.getDefaultAcknowledgeTime());
		multRunAck.setFilename(filename);
		try
		{
			serverConnectionThread.sendAcknowledge(multRunAck);
		}
		catch(IOException e)
		{
			sprat.error(this.getClass().getName()+
				    ":sendMultrunAck:sendAcknowledge:"+multRunCommand+":"+e.toString(),e);
			multRunDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1204);
			multRunDone.setErrorString(":sendMultrunAck:sendAcknowledge:"+e.toString());
			multRunDone.setSuccessful(false);
			return false;
		}
		return true;
	}
}

