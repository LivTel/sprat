// ARCImplementation.java
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
import ngat.message.ISS_INST.ARC;
import ngat.message.ISS_INST.ARC_DONE;
import ngat.message.ISS_INST.FILENAME_ACK;
import ngat.phase2.SpratConfig;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the ARC command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision: 1.11 $
 */
public class ARCImplementation extends CALIBRATEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The hostname to send Sprat mechanism Arduino commands to.
	 */
	protected String mechanismHostname = null;
	/**
	 * The port number to send Sprat mechanism Arduino commands to.
	 */
	protected int mechanismPortNumber;
	/**
	 * The multrun number used for FITS filenames for this command.
	 */
	protected int multrunNumber = 0;
	/**
	 * The string, representing a FITS image filename containing ARC data, that is returned from
	 * the multrun command.
	 */
	protected String arcFilename = null;

	/**
	 * Constructor.
	 */
	public ARCImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.ARC&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.ARC";
	}

	/**
	 * This method returns the ARC command's acknowledge time. 
	 * The frame in the ARC takes 
	 * the configured exposure time plus the default acknowledge time to complete. The default acknowledge time
	 * allows time to setup the camera, get information about the telescope and save the frame to disk.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see SpratTCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ARC arcCommand = (ARC)command;
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		// diddly get exposure time 
		acknowledge.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the ARC command. 
	 * <ul>
	 * <li>We get the grism position, rotation and the slit position from the mechanism
	 * <li>We call getArcExposureLength to get the exposure length for the mechanism combination.
	 * <li>sendBasicAck sends an Ack back to the client, to stop the connection timing out before the exposure
	 *     is finished.
	 * <li>The mirror is moved into the beam.
	 * <li>The ARC light is turned on.
	 * <li>FITS headers for the specified arm are setup with calls to setFitsHeaders and getFitsHeadersFromISS.
	 * <li>The OBJECT FITS header is set to indicate the fact that this is an ARC.
	 * <li>We resend another ACK to the client to ensure the connection is kept alive 
	 *     whilst we actually take the exposure.
	 * <li>The exposure is taken and saved to the FITS filename.
	 * <li>We turn the Arc lamp off.
	 * <li>A FILENAME_ACK is sent back to the client with the new filename.
	 * <li>reduceCalibrate is called to reduce the arc.
	 * </ul>
	 * @see #getMechanismConfig
	 * @see #getSlitPosition
	 * @see #getGrismPosition
	 * @see #getRotationPosition
	 * @see #getArcExposureLength
	 * @see #setArcLamp
	 * @see #arcFilename
	 * @see SpratStatus#setExposureCount
	 * @see SpratStatus#setExposureNumber
	 * @see CommandImplementation#testAbort
	 * @see HardwareImplementation#objectName
	 * @see HardwareImplementation#addFitsHeader
	 * @see ngat.message.ISS_INST.ARC#getLamp
	 * @see ngat.sprat.HardwareImplementation#moveFold
	 * @see ngat.sprat.HardwareImplementation#moveMirror
	 * @see ngat.sprat.HardwareImplementation#clearFitsHeaders
	 * @see ngat.sprat.HardwareImplementation#setFitsHeaders
	 * @see ngat.sprat.HardwareImplementation#getFitsHeadersFromISS
	 * @see ngat.sprat.HardwareImplementation#sendBasicAck
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		ARC arcCommand = null;
		ARC_DONE arcDone = new ARC_DONE(command.getId());
		FILENAME_ACK arcAck = null;
		ACK acknowledge = null;
		FitsHeaderCardImage objectCardImage = null;
		String lampsString = null;
		String arcObjectName = null;
		int slitPosition,grismPosition,rotationPosition,exposureLength,readoutAckTime;

		if(testAbort(command,arcDone) == true)
			return arcDone;
		arcCommand = (ARC)command;
		try
		{
			// get mechanism arduino connection details
			getMechanismConfig();
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
			arcDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1500);
			arcDone.setErrorString(e.toString());
			arcDone.setSuccessful(false);
			return arcDone;
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
			arcDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1501);
			arcDone.setErrorString(e.toString());
			arcDone.setSuccessful(false);
			return arcDone;
		}
		// get lamp and exposure length
		lampsString = arcCommand.getLamp();
		// diddly check lampString against name of lamp
		try
		{
			exposureLength = getArcExposureLength(lampsString,slitPosition,grismPosition,rotationPosition);
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:Getting exposure length failed:"+
				    command,e);
			arcDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1502);
			arcDone.setErrorString(e.toString());
			arcDone.setSuccessful(false);
			return arcDone;
		}
		// send a basic Ack to keep the connection alive whilst we do an exposure
		if(sendBasicAck(arcCommand,arcDone,
				exposureLength+serverConnectionThread.getDefaultAcknowledgeTime()) == false)
			return arcDone;
		// setup exposure status.
		status.setExposureCount(1);
		status.setExposureNumber(0);
		// turn lamp on
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":processCommand:Turn on lamp.");
		try
		{
			setArcLamp(true);
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:Turning lamp on failed:"+
				    command,e);
			arcDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1503);
			arcDone.setErrorString(e.toString());
			arcDone.setSuccessful(false);
			return arcDone;
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
				arcDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1504);
				arcDone.setErrorString(this.getClass().getName()+
						       ":processCommand:clearFitsHeaders failed:"+e);
				arcDone.setSuccessful(false);
				return arcDone;
			}
			sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				  ":processCommand:getting FITS headers from properties.");
			if(setFitsHeaders(arcCommand,arcDone) == false)
				return arcDone;
			sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				  ":processCommand:getting FITS headers from ISS.");
			if(getFitsHeadersFromISS(arcCommand,arcDone) == false)
				return arcDone;
			if(testAbort(arcCommand,arcDone) == true)
				return arcDone;
			// Modify "OBJECT" FITS header value to distinguish between spectra of the OBJECT
			// and calibration ARCs taken for that observation.
			arcObjectName = new String("ARC: "+lampsString+" for "+objectName);
			try
			{
				addFitsHeader("OBJECT",arcObjectName);
			}
			catch(Exception e )
			{
				sprat.error(this.getClass().getName()+":processCommand:addFitsHeader failed:",e);
				arcDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1507);
				arcDone.setErrorString(this.getClass().getName()+
							   ":processCommand:addFitsHeader failed:"+e);
				arcDone.setSuccessful(false);
				return arcDone;
			}
			// send ack of exposurelength + readout before starting exposure
			readoutAckTime = sprat.getStatus().
				getPropertyInteger("sprat.multrun.acknowledge_time.readout");
			if(sendBasicAck(arcCommand,arcDone,
					exposureLength+readoutAckTime+
					serverConnectionThread.getDefaultAcknowledgeTime()) == false)
			{
				sprat.error(this.getClass().getName()+":processCommand:Sending ACK of length "+
					    exposureLength+readoutAckTime+
					    serverConnectionThread.getDefaultAcknowledgeTime()+" failed.");
				return arcDone;
			}
			// call multrun command
			try
			{
				sendMultrunCommand(exposureLength);
			}
			catch(Exception e )
			{
				sprat.error(this.getClass().getName()+":processCommand:sendMultrunCommand failed:",e);
				arcDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1505);
				arcDone.setErrorString(this.getClass().getName()+
							   ":processCommand:sendMultrunCommand failed:"+e);
				arcDone.setSuccessful(false);
				return arcDone;
			}
		}
		finally
		{
			// turn lamp off
			try
			{
				sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					  ":processCommand:Turning off lamp.");
				setArcLamp(false);
				sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
					  ":processCommand:Lamp Turned off.");
			}
			catch(Exception e)
			{
				sprat.error(this.getClass().getName()+":processCommand:Turning lamp off failed:"+
					    command,e);
			}
		}
		status.setExposureNumber(1);
		// send acknowledge to say frame is completed.
		arcAck = new FILENAME_ACK(command.getId());
		arcAck.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
		arcAck.setFilename(arcFilename);
		try
		{
			serverConnectionThread.sendAcknowledge(arcAck);
		}
		catch(IOException e)
		{
			sprat.error(this.getClass().getName()+
					":processCommand:sendAcknowledge:"+command+":"+e.toString());
			arcDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1506);
			arcDone.setErrorString(e.toString());
			arcDone.setSuccessful(false);
			return arcDone;
		}
	// call pipeline to process data and get results
		if(reduceCalibrate(arcCommand,arcDone,arcFilename) == false)
			return arcDone;
	// setup return values.
	// meanCounts and peakCounts set by pipelineProcess for last image reduced.
		arcDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
		arcDone.setErrorString("");
		arcDone.setSuccessful(true);
		return arcDone;
	}

	/**
	 * Get the Sprat mechanism Arduino hostname and port number from the properties into some 
	 * internal variables.
	 * @see #status
	 * @see #mechanismHostname
	 * @see #mechanismPortNumber
	 * @see SpratStatus#getProperty
	 * @see SpratStatus#getPropertyInteger
	 */
	protected void getMechanismConfig() throws Exception
	{
		mechanismHostname = status.getProperty("sprat.mechanism.hostname");
		mechanismPortNumber = status.getPropertyInteger("sprat.mechanism.port_number");
	}

	/**
	 * Get the current slit position.
	 * An instance of SlitCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * @return The current position is returned as an integer, one of:POSITION_ERROR, POSITION_UNKNOWN, 
	 *         POSITION_IN, POSITION_OUT.
	 * @exception Exception Thrown if an error occurs.
	 * @see #mechanismHostname
	 * @see #mechanismPortNumber
	 * @see ngat.sprat.mechanism.command.SlitCommand
	 * @see ngat.sprat.mechanism.command.SlitCommand#run
	 * @see ngat.sprat.mechanism.command.SlitCommand#getRunException
	 * @see ngat.sprat.mechanism.command.SlitCommand#getIsError
	 * @see ngat.sprat.mechanism.command.SlitCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.SlitCommand#getCurrentPosition
	 * @see ngat.sprat.mechanism.command.SlitCommand#positionToString
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#POSITION_ERROR
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#POSITION_UNKNOWN
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#POSITION_IN
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#POSITION_OUT
	 */
	protected int getSlitPosition() throws Exception
	{
		SlitCommand command = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getSlitPosition:started.");
		command = new SlitCommand(mechanismHostname,mechanismPortNumber);
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":getSlitPosition:Slit command threw exception.",
					    command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+
					    ":getSlitPosition:Slit command returned an error:"+
					    command.getErrorString());
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getSlitPosition:finished with position:"+
			  SlitCommand.positionToString(command.getCurrentPosition()));
		return command.getCurrentPosition();
	}

	/**
	 * Get the current grism position.
	 * An instance of GrismCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * @return The current position is returned as an integer, one of:POSITION_ERROR, POSITION_UNKNOWN, 
	 *         POSITION_IN, POSITION_OUT.
	 * @exception Exception Thrown if an error occurs.
	 * @see #mechanismHostname
	 * @see #mechanismPortNumber
	 * @see ngat.sprat.mechanism.command.GrismCommand
	 * @see ngat.sprat.mechanism.command.GrismCommand#run
	 * @see ngat.sprat.mechanism.command.GrismCommand#getRunException
	 * @see ngat.sprat.mechanism.command.GrismCommand#getIsError
	 * @see ngat.sprat.mechanism.command.GrismCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.GrismCommand#getCurrentPosition
	 * @see ngat.sprat.mechanism.command.GrismCommand#positionToString
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#POSITION_ERROR
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#POSITION_UNKNOWN
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#POSITION_IN
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#POSITION_OUT
	 */
	protected int getGrismPosition() throws Exception
	{
		GrismCommand command = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getGrismPosition:started.");
		command = new GrismCommand(mechanismHostname,mechanismPortNumber);
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":getGrismPosition:Grism command threw exception.",
					    command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+
					    ":getGrismPosition:Grism command returned an error:"+
					    command.getErrorString());
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getGrismPosition:finished with position:"+
			  GrismCommand.positionToString(command.getCurrentPosition()));
		return command.getCurrentPosition();
	}

	/**
	 * Get the current rotation position.
	 * An instance of RotationCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * @return The current position is returned as an integer, one of:POSITION_ERROR, POSITION_UNKNOWN, 
	 *         0 or 1.
	 * @exception Exception Thrown if an error occurs.
	 * @see #mechanismHostname
	 * @see #mechanismPortNumber
	 * @see ngat.sprat.mechanism.command.RotationCommand
	 * @see ngat.sprat.mechanism.command.RotationCommand#run
	 * @see ngat.sprat.mechanism.command.RotationCommand#getRunException
	 * @see ngat.sprat.mechanism.command.RotationCommand#getIsError
	 * @see ngat.sprat.mechanism.command.RotationCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.RotationCommand#getCurrentPosition
	 * @see ngat.sprat.mechanism.command.RotationCommand#POSITION_ERROR
	 * @see ngat.sprat.mechanism.command.RotationCommand#POSITION_UNKNOWN
	 */
	protected int getRotationPosition() throws Exception
	{
		RotationCommand command = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getRotationPosition:started.");
		command = new RotationCommand(mechanismHostname,mechanismPortNumber);
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":getRotationPosition:Rotation command threw exception.",
					    command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+
					    ":getRotationPosition:Rotation command returned an error:"+
					    command.getErrorString());
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getRotationPosition:finished with position:"+
			  command.getCurrentPosition());
		return command.getCurrentPosition();
	}

	/**
	 * Method to turn the ARC lamp on or off.
	 * @param on A boolean, if true turn the lamp on, otherwise turn it off.
	 * @exception Exception Thrown if an error occurs.
	 * @see #mechanismHostname
	 * @see #mechanismPortNumber
	 * @see ngat.sprat.mechanism.command.ArcLampCommand
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#run
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#getRunException
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#getIsError
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#getErrorString
	 */
	protected void setArcLamp(boolean on) throws Exception
	{
		ArcLampCommand command = null;
		int lampState;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"setArcLamp:started.");
		if(on)
			lampState = ArcLampCommand.STATE_ON;
		else
			lampState = ArcLampCommand.STATE_OFF;
		command = new ArcLampCommand(mechanismHostname,mechanismPortNumber,lampState);
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":setArcLamp:Arc Lamp command threw exception.",
					    command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+
					    ":setArcLamp:Arc Lamp command returned an error:"+
					    command.getErrorString());
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"setArcLamp:finished.");
	}

	/**
	 * Send the multrun command to the C layer.
	 * @param exposureLength The exposure length in milliseconds.
	 * @exception Exception Thrown if an error occurs.
	 * @see #multrunNumber
	 * @see #arcFilename
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
		command.setCommand(exposureLength,1,MultrunCommand.EXPOSURE_TYPE_ARC);
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
		arcFilename = (String)(filenameList.get(0));
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultrunCommand:finished.");
	}
}
