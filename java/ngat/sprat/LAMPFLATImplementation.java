// LAMPFLATImplementation.java
// $HeadURL$
package ngat.sprat;

import java.lang.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

import ngat.sprat.ccd.command.*;
import ngat.sprat.mechanism.command.*;
import ngat.fits.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.LAMPFLAT;
import ngat.message.ISS_INST.LAMPFLAT_DONE;
import ngat.message.ISS_INST.FILENAME_ACK;
import ngat.phase2.SpratConfig;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the LAMPFLAT command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision$
 */
public class LAMPFLATImplementation extends CALIBRATEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The multrun number used for FITS filenames for this command.
	 */
	protected int multrunNumber = 0;
	/**
	 * The string, representing a FITS image filename containing LAMPFLAT data, that is returned from
	 * the multrun command.
	 */
	protected String lampFlatFilename = null;

	/**
	 * Constructor.
	 */
	public LAMPFLATImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.LAMPFLAT&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.LAMPFLAT";
	}

	/**
	 * This method returns the LAMPFLAT command's acknowledge time. 
	 * The frame in the LAMPFLAT takes 
	 * the configured exposure time plus the default acknowledge time to complete. The default acknowledge time
	 * allows time to setup the camera, get information about the telescope and save the frame to disk.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see SpratTCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		LAMPFLAT lampFlatCommand = (LAMPFLAT)command;
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		// the exposure length is not known at this point, so just return a default ack time for now
		acknowledge.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the LAMPFLAT command. 
	 * <ul>
	 * <li>We get the grism position, rotation and the slit position from the mechanism
	 * <li>We call getLampExposureLength to get the exposure length for the mechanism combination.
	 * <li>sendBasicAck sends an Ack back to the client, to stop the connection timing out before the exposure
	 *     is finished.
	 * <li>The mirror is moved into the beam.
	 * <li>The lamp light is turned on.
	 * <li>FITS headers are setup with calls to setFitsHeaders and getFitsHeadersFromISS.
	 * <li>The OBJECT FITS header is set to indicate the fact that this is an LAMPFLAT.
	 * <li>We retrieve the length of time to leave the lamp warming up from: 
	 *     sprat.lamp.+lampsString+.warmup.length
	 * <li>We send an ACK to ensure the client connection remains open, and sleep for the warmup time.
	 * <li>We resend another ACK to the client to ensure the connection is kept alive 
	 *     whilst we actually take the exposure.
	 * <li>The exposure is taken and saved to the FITS filename.
	 * <li>We turn the lamp off.
	 * <li>A FILENAME_ACK is sent back to the client with the new filename.
	 * <li>reduceCalibrate is called to reduce the lamp flat.
	 * </ul>
	 * @see #getLampExposureLength
	 * @see #setWLamp
	 * @see #lampFlatFilename
	 * @see CommandImplementation#testAbort
	 * @see FITSImplementation#objectName
	 * @see FITSImplementation#addFitsHeader
	 * @see FITSImplementation#clearFitsHeaders
	 * @see FITSImplementation#setFitsHeaders
	 * @see FITSImplementation#getFitsHeadersFromISS
	 * @see HardwareImplementation#getMechanismConfig
	 * @see HardwareImplementation#getSlitPosition
	 * @see HardwareImplementation#getGrismPosition
	 * @see HardwareImplementation#getRotationPosition
	 * @see HardwareImplementation#moveFold
	 * @see HardwareImplementation#moveMirror
	 * @see HardwareImplementation#sendBasicAck
	 * @see ngat.message.ISS_INST.LAMPFLAT#getLamp
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		LAMPFLAT lampFlatCommand = null;
		LAMPFLAT_DONE lampFlatDone = new LAMPFLAT_DONE(command.getId());
		FILENAME_ACK filenameAck = null;
		ACK acknowledge = null;
		FitsHeaderCardImage objectCardImage = null;
		String lampsString = null;
		String lampFlatObjectName = null;
		int slitPosition,grismPosition,rotationPosition,exposureLength,readoutAckTime,warmupLengthMS;

		if(testAbort(command,lampFlatDone) == true)
			return lampFlatDone;
		lampFlatCommand = (LAMPFLAT)command;
		try
		{
			// get mechanism arduino connection details
			// Now called in HardwareImplementation.init
			// getMechanismConfig();
			// where is the slit?
			slitPosition = getSlitPosition();
			// where is the grism?
			grismPosition = getGrismPosition();
			// what is the grism rotation position?
			rotationPosition = getRotationPosition();
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:Getting mechanism positions failed:"+
				    command,e);
			lampFlatDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2700);
			lampFlatDone.setErrorString(e.toString());
			lampFlatDone.setSuccessful(false);
			return lampFlatDone;
		}
		// move the sprat calibration mirror into the beam
		sprat.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+
			  ":processCommand:Moving calibration mirror.");
		try
		{
			moveMirror(SpratConfig.POSITION_IN);
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:Moving Calibration Mirror failed:"+
				    command,e);
			lampFlatDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2701);
			lampFlatDone.setErrorString(e.toString());
			lampFlatDone.setSuccessful(false);
			return lampFlatDone;
		}
		// get lamp and exposure length
		lampsString = lampFlatCommand.getLamp();
		// diddly check lampString against name of lamp
		try
		{
			exposureLength = getLampExposureLength(lampsString,slitPosition,grismPosition,
							       rotationPosition);
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:Getting exposure length failed:"+
				    command,e);
			lampFlatDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2702);
			lampFlatDone.setErrorString(e.toString());
			lampFlatDone.setSuccessful(false);
			return lampFlatDone;
		}
		// send a basic Ack to keep the connection alive whilst we do an exposure
		if(sendBasicAck(lampFlatCommand,lampFlatDone,
				exposureLength+serverConnectionThread.getDefaultAcknowledgeTime()) == false)
			return lampFlatDone;
		// turn lamp on
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":processCommand:Turn on lamp.");
		try
		{
			setWLamp(true);
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:Turning lamp on failed:"+
				    command,e);
			lampFlatDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2703);
			lampFlatDone.setErrorString(e.toString());
			lampFlatDone.setSuccessful(false);
			return lampFlatDone;
		}
		// try / finally to ensure we always turn off the lamp after it has been turned on.
		try
		{
			// get fits headers
			try
			{
				clearFitsHeaders();
			}
			catch(Exception e)
			{
				sprat.error(this.getClass().getName()+":processCommand:clearFitsHeaders failed:",e);
				lampFlatDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2704);
				lampFlatDone.setErrorString(this.getClass().getName()+
						       ":processCommand:clearFitsHeaders failed:"+e);
				lampFlatDone.setSuccessful(false);
				return lampFlatDone;
			}
			sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				  ":processCommand:getting FITS headers from properties.");
			if(setFitsHeaders(lampFlatCommand,lampFlatDone) == false)
				return lampFlatDone;
			sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				  ":processCommand:getting FITS headers from ISS.");
			if(getFitsHeadersFromISS(lampFlatCommand,lampFlatDone) == false)
				return lampFlatDone;
			if(testAbort(lampFlatCommand,lampFlatDone) == true)
				return lampFlatDone;
			// Modify "OBJECT" FITS header value to distinguish between spectra of the OBJECT
			// and calibration LAMPFLATs taken for that observation.
			lampFlatObjectName = new String("LAMPFLAT: "+lampsString+" for "+objectName);
			try
			{
				addFitsHeader("OBJECT",lampFlatObjectName);
			}
			catch(Exception e )
			{
				sprat.error(this.getClass().getName()+":processCommand:addFitsHeader failed:",e);
				lampFlatDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2705);
				lampFlatDone.setErrorString(this.getClass().getName()+
							   ":processCommand:addFitsHeader failed:"+e);
				lampFlatDone.setSuccessful(false);
				return lampFlatDone;
			}
			// wait for the lamp to warm up before doing the exposure
			warmupLengthMS = status.getPropertyInteger("sprat.lamp."+lampsString+".warmup.length");
			sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				  ":processCommand:Waiting "+warmupLengthMS+" ms for the lamp to warm up.");
			if(sendBasicAck(lampFlatCommand,lampFlatDone,warmupLengthMS+
					serverConnectionThread.getDefaultAcknowledgeTime()) == false)
			{
				sprat.error(this.getClass().getName()+":processCommand:Sending ACK of length "+
					    warmupLengthMS+serverConnectionThread.getDefaultAcknowledgeTime()+
					    " failed.");
				return lampFlatDone;
			}
			try
			{
				Thread.sleep(warmupLengthMS);
			}
			catch(InterruptedException e)
			{
				sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					  ":processCommand:Interrupted whilst waiting for the lamp to warm up.");
			}
			if(testAbort(lampFlatCommand,lampFlatDone) == true)
				return lampFlatDone;
			// send ack of exposurelength + readout before starting exposure
			readoutAckTime = status.getPropertyInteger("sprat.multrun.acknowledge_time.readout");
			if(sendBasicAck(lampFlatCommand,lampFlatDone,
					exposureLength+readoutAckTime+
					serverConnectionThread.getDefaultAcknowledgeTime()) == false)
			{
				sprat.error(this.getClass().getName()+":processCommand:Sending ACK of length "+
					    exposureLength+readoutAckTime+
					    serverConnectionThread.getDefaultAcknowledgeTime()+" failed.");
				return lampFlatDone;
			}
			// call multrun command
			sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				  ":processCommand:Calling  sendMultrunCommand with exposure length "+exposureLength+
				  " ms.");
			try
			{
				sendMultrunCommand(exposureLength);
			}
			catch(Exception e )
			{
				sprat.error(this.getClass().getName()+":processCommand:sendMultrunCommand failed:",e);
				lampFlatDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2706);
				lampFlatDone.setErrorString(this.getClass().getName()+
							    ":processCommand:sendMultrunCommand failed:"+e);
				lampFlatDone.setSuccessful(false);
				return lampFlatDone;
			}
		}
		finally
		{
			// turn lamp off
			try
			{
				sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					  ":processCommand:Turning off lamp.");
				setWLamp(false);
				sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					  ":processCommand:Lamp Turned off.");
			}
			catch(Exception e)
			{
				sprat.error(this.getClass().getName()+":processCommand:Turning lamp off failed:"+
					    command,e);
			}
		}
		// send acknowledge to say frame is completed.
		filenameAck = new FILENAME_ACK(command.getId());
		filenameAck.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
		filenameAck.setFilename(lampFlatFilename);
		try
		{
			serverConnectionThread.sendAcknowledge(filenameAck);
		}
		catch(IOException e)
		{
			sprat.error(this.getClass().getName()+
					":processCommand:sendAcknowledge:"+command+":"+e.toString());
			lampFlatDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2707);
			lampFlatDone.setErrorString(e.toString());
			lampFlatDone.setSuccessful(false);
			return lampFlatDone;
		}
	// call pipeline to process data and get results
		if(reduceCalibrate(lampFlatCommand,lampFlatDone,lampFlatFilename) == false)
			return lampFlatDone;
	// setup return values.
	// meanCounts and peakCounts set by pipelineProcess for last image reduced.
		lampFlatDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
		lampFlatDone.setErrorString("");
		lampFlatDone.setSuccessful(true);
		return lampFlatDone;
	}

	/**
	 * Method to turn the LAMPFLAT lamp on or off.
	 * @param on A boolean, if true turn the lamp on, otherwise turn it off.
	 * @exception Exception Thrown if an error occurs.
	 * @see #mechanismHostname
	 * @see #mechanismPortNumber
	 * @see ngat.sprat.mechanism.command.WLampCommand
	 * @see ngat.sprat.mechanism.command.WLampCommand#run
	 * @see ngat.sprat.mechanism.command.WLampCommand#getRunException
	 * @see ngat.sprat.mechanism.command.WLampCommand#getIsError
	 * @see ngat.sprat.mechanism.command.WLampCommand#getErrorString
	 */
	protected void setWLamp(boolean on) throws Exception
	{
		WLampCommand command = null;
		int lampState;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"setWLamp:started.");
		if(on)
			lampState = WLampCommand.STATE_ON;
		else
			lampState = WLampCommand.STATE_OFF;
		command = new WLampCommand(mechanismHostname,mechanismPortNumber,lampState);
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":setWLamp:WLamp command threw exception.",
					    command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+
					    ":setWLamp:WLamp command returned an error:"+
					    command.getErrorString());
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"setWLamp:finished.");
	}

	/**
	 * Send the multrun command to the C layer.
	 * @param exposureLength The exposure length in milliseconds.
	 * @exception Exception Thrown if an error occurs.
	 * @see #multrunNumber
	 * @see #lampFlatFilename
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
		List filenameList = null;
		int portNumber,returnCode;
		String hostname = null;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultrunCommand:"+
			 "\n\t:exposureLength = "+exposureLength+".");
		command = new MultrunCommand();
		// configure C comms
		hostname = status.getProperty("sprat.ccd.c.hostname");
		portNumber = status.getPropertyInteger("sprat.ccd.c.port_number");
		command.setAddress(hostname);
		command.setPortNumber(portNumber);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultrunCommand:hostname = "+hostname+
			   " :port number = "+portNumber+".");
		command.setCommand(exposureLength,1,MultrunCommand.EXPOSURE_TYPE_LAMPFLAT);
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
		lampFlatFilename = (String)(filenameList.get(0));
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultrunCommand:finished.");
	}
}
