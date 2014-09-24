// HardwareImplementation.java
// $HeadURL$
package ngat.sprat;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.text.*;
import java.util.*;

import ngat.fits.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.phase2.*;
import ngat.sprat.ccd.command.*;
import ngat.sprat.mechanism.*;
import ngat.sprat.mechanism.command.*;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the MULTRUN command sent to a server using the
 * Java Message System.
 * @author Chris Motram
 * @version $Revision$
 */
public class HardwareImplementation extends CommandImplementation implements JMSCommandImplementation
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
	 * The hostname to send CCD C layer commands to.
	 */
	protected String ccdCLayerHostname = null;
	/**
	 * The port number to send CCD C layer commands to.
	 */
	protected int ccdCLayerPortNumber;

	/**
	 * <ul>
	 * <li>This method calls the super-classes method. 
	 * <li>It then calls getMechanismConfig to retrieve the hostname/port number of the Arduino 
	 *     controlling the mechanisms. 
	 * <li>It calls getCCDCLayerConfig to retrieve the hostname/port number of the 
	 *     C layer controlling the CCD camera.
	 * </ul>
	 * @param command The command to be implemented.
	 * @see #getMechanismConfig
	 * @see #getCCDCLayerConfig
	 */
	public void init(COMMAND command)
	{
		super.init(command);
		if(status != null)
		{
			try
			{
				sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					  ":init:Trying to retrieve mechanism config.");
				getMechanismConfig();
				sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					  ":init:Trying to retrieve CCD C layer communication config.");
				getCCDCLayerConfig();
			}
			catch(Exception e)
			{
				sprat.error(this.getClass().getName()+
					    ":init:getMechanismConfig or getCCDCLayerConfig failed:"+
					    command.getClass().getName()+":"+e,e);
			}
		}
	}

	/**
	 * This method is used to calculate how long an implementation of a command is going to take, so that the
	 * client has an idea of how long to wait before it can assume the server has died.
	 * @param command The command to be implemented.
	 * @return The time taken to implement this command, or the time taken before the next acknowledgement
	 * is to be sent.
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		return super.calculateAcknowledgeTime(command);
	}

	/**
	 * This routine performs the generic command implementation.
	 * @param command The command to be implemented.
	 * @return The results of the implementation of this command.
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		return super.processCommand(command);
	}

	/**
	 * This routine tries to move the mirror fold to a certain location, by issuing a MOVE_FOLD command
	 * to the ISS. The position to move the fold to is specified by the Sprat property file.
	 * If an error occurs the done objects field's are set accordingly.
	 * @param command The command being implemented that made this call to the ISS. This is used
	 * 	for error logging.
	 * @param done A COMMAND_DONE subclass specific to the command being implemented. If an
	 * 	error occurs the relevant fields are filled in with the error.
	 * @return The routine returns a boolean to indicate whether the operation was completed
	 *  	successfully.
	 * @see SpratStatus#getPropertyInteger
	 * @see Sprat#sendISSCommand
	 */
	public boolean moveFold(COMMAND command,COMMAND_DONE done)
	{
		INST_TO_ISS_DONE instToISSDone = null;
		MOVE_FOLD moveFold = null;
		int mirrorFoldPosition = 0;

		moveFold = new MOVE_FOLD(command.getId());
		try
		{
			mirrorFoldPosition = status.getPropertyInteger("sprat.mirror_fold_position");
		}
		catch(NumberFormatException e)
		{
			mirrorFoldPosition = 0;
			sprat.error(this.getClass().getName()+":moveFold:"+command.getClass().getName(),e);
			done.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1201);
			done.setErrorString("moveFold:"+e);
			done.setSuccessful(false);
			return false;
		}
		moveFold.setMirror_position(mirrorFoldPosition);
		instToISSDone = sprat.sendISSCommand(moveFold,serverConnectionThread);
		if(instToISSDone.getSuccessful() == false)
		{
			sprat.error(this.getClass().getName()+":moveFold:"+
				command.getClass().getName()+":"+instToISSDone.getErrorString());
			done.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1202);
			done.setErrorString(instToISSDone.getErrorString());
			done.setSuccessful(false);		
			return false;
		}
		return true;
	}

	/**
	 * Routine to set the telescope focus offset.The offset sent is based on:
	 * <ul>
	 * <li>The instrument's offset with respect to the telescope's natural offset (in the configuration
	 *     property 'sprat.focus.offset'.
	 * <ul>
	 * This method sends a OFFSET_FOCUS command to
	 * the ISS. 
	 * @param id The Id is used as the OFFSET_FOCUS command's id.
	 * @exception Exception Thrown if the return value of the OFFSET_FOCUS ISS command is false.
	 */
	protected void setFocusOffset(String id) throws Exception
	{
		OFFSET_FOCUS offsetFocusCommand = null;
		INST_TO_ISS_DONE instToISSDone = null;
		String instrumentName = null;
		float focusOffset = 0.0f;

		sprat.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":setFocusOffset:Started.");
		focusOffset = 0.0f;
	// get default focus offset
		focusOffset += status.getPropertyFloat("sprat.focus.offset");
		sprat.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":setFocusOffset:Master offset is "+
			 focusOffset+".");
	// send the overall focusOffset to the ISS using  OFFSET_FOCUS
		offsetFocusCommand = new OFFSET_FOCUS(id);
		offsetFocusCommand.setFocusOffset(focusOffset);
		sprat.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":setFocusOffset:Total offset for "+
			  instrumentName+" is "+focusOffset+".");
		instToISSDone = sprat.sendISSCommand(offsetFocusCommand,serverConnectionThread);
		if(instToISSDone.getSuccessful() == false)
		{
			throw new Exception(this.getClass().getName()+":focusOffset failed:"+focusOffset+":"+
					    instToISSDone.getErrorString());
		}
		sprat.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":setFocusOffset:Finished.");
	}

	/**
	 * Get the configured exposure length for the specified lamp from the configuration file.
	 * @param lampString Which lamp to use.
	 * @param slitPosition The position of the slit, one of POSITION_IN | POSITION_OUT.
	 * @param grismPosition The position of the grism, one of POSITION_IN | POSITION_OUT.
	 * @param rotationPosition The rotation position of the grism, one of 0 | 1.
	 * @return The exposure length in milliseconds.
	 * @see #status
	 * @see SpratStatus#getPropertyInteger
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#POSITION_IN
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#POSITION_OUT
	 */
	protected int getLampExposureLength(String lampString,int slitPosition,int grismPosition,int rotationPosition)
	{
		int exposureLength;
		String keywordString = null;

		// sprat.lamp.slit position[in|out].grism position[in|out].rotation position[0|1].
		// lamp name.exposure_length
		keywordString = new String("sprat.lamp."+InOutReplyCommand.positionToLowerCaseString(slitPosition)+"."+
					   InOutReplyCommand.positionToLowerCaseString(grismPosition)+"."+
					   rotationPosition+"."+lampString+".exposure_length");
		sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			  ":getLampExposureLength:Trying to retrieve exposure length from property keyword:"+
			  keywordString);
		exposureLength = status.getPropertyInteger(keywordString);
		return exposureLength;
	}

	/**
	 * Get the Sprat mechanism Arduino hostname and port number from the properties into some 
	 * internal variables.
	 * <ul>
	 * <li><b>sprat.mechanism.hostname</b>
	 * <li><b>sprat.mechanism.port_number</b>
	 * </ul>
	 * @see #status
	 * @see #mechanismHostname
	 * @see #mechanismPortNumber
	 * @see SpratStatus#getProperty
	 * @see SpratStatus#getPropertyInteger
	 */
	protected void getMechanismConfig() throws Exception
	{
		sprat.log(Logging.VERBOSITY_VERBOSE,"getMechanismConfig:started.");
		mechanismHostname = status.getProperty("sprat.mechanism.hostname");
		mechanismPortNumber = status.getPropertyInteger("sprat.mechanism.port_number");
		sprat.log(Logging.VERBOSITY_VERBOSE,"getMechanismConfig:finished with hostname: "+mechanismHostname+
			  " port number: "+mechanismPortNumber+".");
	}

	/**
	 * Move the Sprat calibration mirror to the desired position.
	 * The following status is used to configure the move parameters:
	 * <ul>
	 * <li><b>sprat.config.mirror.move.sleep_time</b>
	 * <li><b>sprat.config.mirror.move.timeout_time</b>
	 * </ul>
	 * getMechanismConfig must have been called before this method to set the mechanismHostname/mechanismPortNumber
	 * up correctly.
	 * @param position The position to attain, one of POSITION_IN, POSITION_OUT.
	 * @exception Exception Thrown if an error occurs.
	 * @see #sprat
	 * @see #status
	 * @see #mechanismHostname
	 * @see #mechanismPortNumber
	 * @see Sprat#log
	 * @see SpratStatus#getProperty
	 * @see SpratStatus#getPropertyInteger
	 * @see ngat.phase2.SpratConfig#POSITION_IN
	 * @see ngat.phase2.SpratConfig#POSITION_OUT
	 * @see ngat.phase2.SpratConfig#positionToString
	 * @see ngat.sprat.mechanism.command.MirrorCommand
	 * @see ngat.sprat.mechanism.MoveInOutMechanism
	 * @see ngat.sprat.mechanism.MoveInOutMechanism#setCommand
	 * @see ngat.sprat.mechanism.MoveInOutMechanism#setSleepTime
	 * @see ngat.sprat.mechanism.MoveInOutMechanism#setTimeoutTime
	 * @see ngat.sprat.mechanism.MoveInOutMechanism#moveInOutMechanism
	 */
	protected void moveMirror(int position) throws Exception
	{
		MoveInOutMechanism mechanismMover = null;
		MirrorCommand command = null;
		int sleepTime,timeoutTime;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":moveMirror:"+
			  "Position = "+SpratConfig.positionToString(position)+" ("+position+").");
		// retrieve config
		sleepTime = status.getPropertyInteger("sprat.config.mirror.move.sleep_time");
		timeoutTime = status.getPropertyInteger("sprat.config.mirror.move.timeout_time");
		// setup command and mover objects
		sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			  ":moveMirror:Creating MirrorCommand to send to "+
			  mechanismHostname+":"+mechanismPortNumber+".");
		command = new MirrorCommand(mechanismHostname,mechanismPortNumber);
		mechanismMover = new MoveInOutMechanism();
		mechanismMover.setCommand(command,position);
		sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			  ":moveMirror:Setting mover sleep time to "+sleepTime+
			  " and timeout time to "+timeoutTime+".");
		mechanismMover.setSleepTime(sleepTime);
		mechanismMover.setTimeoutTime(timeoutTime);
		sprat.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":moveMirror:Starting move.");
		mechanismMover.moveInOutMechanism();
		sprat.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":moveMirror:Finished move.");
	}

	/**
	 * Move the grism to the desired position.
	 * The following status is used to configure the move parameters:
	 * <ul>
	 * <li><b>sprat.mechanism.hostname</b>
	 * <li><b>sprat.mechanism.port_number</b>
	 * <li><b>sprat.config.grism.move.sleep_time</b>
	 * <li><b>sprat.config.grism.move.timeout_time</b>
	 * </ul>
	 * @param position The position to attain, one of POSITION_IN, POSITION_OUT.
	 * @exception Exception Thrown if an error occurs.
	 * @see #sprat
	 * @see #status
	 * @see Sprat#log
	 * @see HardwareImplementation#mechanismHostname
	 * @see HardwareImplementation#mechanismPortNumber
	 * @see SpratStatus#getProperty
	 * @see SpratStatus#getPropertyInteger
	 * @see ngat.phase2.SpratConfig#POSITION_IN
	 * @see ngat.phase2.SpratConfig#POSITION_OUT
	 * @see ngat.phase2.SpratConfig#positionToString
	 * @see ngat.sprat.mechanism.command.GrismCommand
	 * @see ngat.sprat.mechanism.MoveInOutMechanism
	 * @see ngat.sprat.mechanism.MoveInOutMechanism#setCommand
	 * @see ngat.sprat.mechanism.MoveInOutMechanism#setSleepTime
	 * @see ngat.sprat.mechanism.MoveInOutMechanism#setTimeoutTime
	 * @see ngat.sprat.mechanism.MoveInOutMechanism#moveInOutMechanism
	 */
	protected void moveGrism(int position) throws Exception
	{
		MoveInOutMechanism mechanismMover = null;
		GrismCommand command = null;
		int sleepTime,timeoutTime,returnCode;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"moveGrism:"+
			  "Position = "+SpratConfig.positionToString(position)+" ("+position+").");
		// retrieve config
		sleepTime = status.getPropertyInteger("sprat.config.grism.move.sleep_time");
		timeoutTime = status.getPropertyInteger("sprat.config.grism.move.timeout_time");
		// setup command and mover objects
		sprat.log(Logging.VERBOSITY_VERBOSE,"moveGrism:Creating GrismCommand to send to "+
			  mechanismHostname+":"+mechanismPortNumber+".");
		command = new GrismCommand(mechanismHostname,mechanismPortNumber);
		mechanismMover = new MoveInOutMechanism();
		mechanismMover.setCommand(command,position);
		sprat.log(Logging.VERBOSITY_VERBOSE,"moveGrism:Setting mover sleep time to "+sleepTime+
			  " and timeout time to "+timeoutTime+".");
		mechanismMover.setSleepTime(sleepTime);
		mechanismMover.setTimeoutTime(timeoutTime);
		sprat.log(Logging.VERBOSITY_TERSE,"moveGrism:Starting move.");
		mechanismMover.moveInOutMechanism();
		sprat.log(Logging.VERBOSITY_TERSE,"moveGrism:Finished move.");
	}

	/**
	 * Rotate the grism to the desired position.
	 * The following status is used to configure the rotation parameters:
	 * <ul>
	 * <li><b>sprat.mechanism.hostname</b>
	 * <li><b>sprat.mechanism.port_number</b>
	 * <li><b>sprat.config.grism.rotate.sleep_time</b>
	 * <li><b>sprat.config.grism.rotate.timeout_time</b>
	 * </ul>
	 * @param position The position to attain, one of POSITION_IN, POSITION_OUT.
	 * @exception Exception Thrown if an error occurs.
	 * @see #sprat
	 * @see #status
	 * @see Sprat#log
	 * @see SpratStatus#getProperty
	 * @see SpratStatus#getPropertyInteger
	 * @see HardwareImplementation#mechanismHostname
	 * @see HardwareImplementation#mechanismPortNumber
	 * @see ngat.phase2.SpratConfig#POSITION_IN
	 * @see ngat.phase2.SpratConfig#POSITION_OUT
	 * @see ngat.sprat.mechanism.command.RotationCommand
	 * @see ngat.sprat.mechanism.MoveRotationMechanism
	 * @see ngat.sprat.mechanism.MoveRotationMechanism#setCommand
	 * @see ngat.sprat.mechanism.MoveRotationMechanism#setSleepTime
	 * @see ngat.sprat.mechanism.MoveRotationMechanism#setTimeoutTime
	 * @see ngat.sprat.mechanism.MoveRotationMechanism#moveRotationMechanism
	 */
	protected void rotateGrism(int position) throws Exception
	{
		MoveRotationMechanism mechanismMover = null;
		RotationCommand command = null;
		int sleepTime,timeoutTime;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"rotateGrism:Position = "+position+".");
		// retrieve config
		sleepTime = status.getPropertyInteger("sprat.config.grism.rotate.sleep_time");
		timeoutTime = status.getPropertyInteger("sprat.config.grism.rotate.timeout_time");
		// setup command and mover objects
		sprat.log(Logging.VERBOSITY_VERBOSE,"rotateGrism:Creating RotationCommand to send to "+
			  mechanismHostname+":"+mechanismPortNumber+".");
		command = new RotationCommand(mechanismHostname,mechanismPortNumber);
		mechanismMover = new MoveRotationMechanism();
		mechanismMover.setCommand(command,position);
		sprat.log(Logging.VERBOSITY_VERBOSE,"rotateGrism:Setting mover sleep time to "+sleepTime+
			  " and timeout time to "+timeoutTime+".");
		mechanismMover.setSleepTime(sleepTime);
		mechanismMover.setTimeoutTime(timeoutTime);
		sprat.log(Logging.VERBOSITY_TERSE,"rotateGrism:Starting move.");
		mechanismMover.moveRotationMechanism();
		sprat.log(Logging.VERBOSITY_TERSE,"rotateGrism:Finished move.");
	}

	/**
	 * Move the slit to the desired position.
	 * The following status is used to configure the move parameters:
	 * <ul>
	 * <li><b>sprat.mechanism.hostname</b>
	 * <li><b>sprat.mechanism.port_number</b>
	 * <li><b>sprat.config.slit.move.sleep_time</b>
	 * <li><b>sprat.config.slit.move.timeout_time</b>
	 * </ul>
	 * @param position The position to attain, one of POSITION_IN, POSITION_OUT.
	 * @exception Exception Thrown if an error occurs.
	 * @see #sprat
	 * @see #status
	 * @see Sprat#log
	 * @see SpratStatus#getProperty
	 * @see SpratStatus#getPropertyInteger
	 * @see HardwareImplementation#mechanismHostname
	 * @see HardwareImplementation#mechanismPortNumber
	 * @see ngat.phase2.SpratConfig#POSITION_IN
	 * @see ngat.phase2.SpratConfig#POSITION_OUT
	 * @see ngat.phase2.SpratConfig#positionToString
	 * @see ngat.sprat.mechanism.command.SlitCommand
	 * @see ngat.sprat.mechanism.MoveInOutMechanism
	 * @see ngat.sprat.mechanism.MoveInOutMechanism#setCommand
	 * @see ngat.sprat.mechanism.MoveInOutMechanism#setSleepTime
	 * @see ngat.sprat.mechanism.MoveInOutMechanism#setTimeoutTime
	 * @see ngat.sprat.mechanism.MoveInOutMechanism#moveInOutMechanism
	 */
	protected void moveSlit(int position) throws Exception
	{
		MoveInOutMechanism mechanismMover = null;
		SlitCommand command = null;
		int sleepTime,timeoutTime;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"moveSlit:"+
			  "Position = "+SpratConfig.positionToString(position)+" ("+position+").");
		// retrieve config
		sleepTime = status.getPropertyInteger("sprat.config.slit.move.sleep_time");
		timeoutTime = status.getPropertyInteger("sprat.config.slit.move.timeout_time");
		// setup command and mover objects
		sprat.log(Logging.VERBOSITY_VERBOSE,"moveSlit:Creating SlitCommand to send to "+
			  mechanismHostname+":"+mechanismPortNumber+".");
		command = new SlitCommand(mechanismHostname,mechanismPortNumber);
		mechanismMover = new MoveInOutMechanism();
		mechanismMover.setCommand(command,position);
		sprat.log(Logging.VERBOSITY_VERBOSE,"moveSlit:Setting mover sleep time to "+sleepTime+
			  " and timeout time to "+timeoutTime+".");
		mechanismMover.setSleepTime(sleepTime);
		mechanismMover.setTimeoutTime(timeoutTime);
		sprat.log(Logging.VERBOSITY_TERSE,"moveSlit:Starting move.");
		mechanismMover.moveInOutMechanism();
		sprat.log(Logging.VERBOSITY_TERSE,"moveSlit:Finished move.");
	}

	/**
	 * Get the current mirror position.
	 * An instance of MirrorCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * @return The current position is returned as an integer, one of:POSITION_ERROR, POSITION_UNKNOWN, 
	 *         POSITION_IN, POSITION_OUT.
	 * @exception Thrown if the command fails in some way, or returns an error.
	 * @see HardwareImplementation#mechanismHostname
	 * @see HardwareImplementation#mechanismPortNumber
	 * @see ngat.sprat.mechanism.command.MirrorCommand
	 * @see ngat.sprat.mechanism.command.MirrorCommand#run
	 * @see ngat.sprat.mechanism.command.MirrorCommand#getRunException
	 * @see ngat.sprat.mechanism.command.MirrorCommand#getIsError
	 * @see ngat.sprat.mechanism.command.MirrorCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.MirrorCommand#getCurrentPosition
	 * @see ngat.sprat.mechanism.command.MirrorCommand#positionToString
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#POSITION_ERROR
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#POSITION_UNKNOWN
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#POSITION_IN
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#POSITION_OUT
	 */
	protected int getMirrorPosition() throws Exception
	{
		MirrorCommand command = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getMirrorPosition:started.");
		command = new MirrorCommand(mechanismHostname,mechanismPortNumber);
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":getMirrorPosition:Mirror command threw exception.",
					    command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+
					    ":getMirrorPosition:Mirror command returned an error:"+
					    command.getErrorString());
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getMirrorPosition:finished with position:"+
			  MirrorCommand.positionToString(command.getCurrentPosition()));
		return command.getCurrentPosition();
	}

 	/**
	 * Get the current slit position.
	 * An instance of SlitCommand is "run". If a run exception occurs this is thrown.
	 * getMechanismConfig must have been called before this method to set the mechanismHostname/mechanismPortNumber
	 * up correctly.
	 * If an error is returned this is thrown as an exception.
	 * @return The current position is returned as an integer, one of:POSITION_ERROR, POSITION_UNKNOWN, 
	 *         POSITION_IN, POSITION_OUT.
	 * @exception Exception Thrown if an error occurs.
	 * @see #mechanismHostname
	 * @see #mechanismPortNumber
	 * @see #getMechanismConfig
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

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":getSlitPosition:started.");
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
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			  ":getSlitPosition:finished with position:"+
			  SlitCommand.positionToString(command.getCurrentPosition()));
		return command.getCurrentPosition();
	}

	/**
	 * Get the current grism position.
	 * An instance of GrismCommand is "run". If a run exception occurs this is thrown.
	 * getMechanismConfig must have been called before this method to set the mechanismHostname/mechanismPortNumber
	 * up correctly.
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

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":getGrismPosition:started.");
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
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			  ":getGrismPosition:finished with position:"+
			  GrismCommand.positionToString(command.getCurrentPosition()));
		return command.getCurrentPosition();
	}

	/**
	 * Get the current rotation position.
	 * An instance of RotationCommand is "run". If a run exception occurs this is thrown.
	 * getMechanismConfig must have been called before this method to set the mechanismHostname/mechanismPortNumber
	 * up correctly.
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

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":getRotationPosition:started.");
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
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			  ":getRotationPosition:finished with position:"+
			  command.getCurrentPosition());
		return command.getCurrentPosition();
	}

	/**
	 * Get a mechanism temperature.
	 * An instance of TemperatureCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * @param sensorNumber Which number temperature sensor to query.
	 * @return The current temeprature as a double, in Kelvin.
	 * @exception Thrown if the command fails in some way, or returns an error.
	 * @see #mechanismHostname
	 * @see #mechanismPortNumber
	 * @see Sprat#CENTIGRADE_TO_KELVIN
	 * @see ngat.sprat.mechanism.command.TemperatureCommand
	 * @see ngat.sprat.mechanism.command.TemperatureCommand#run
	 * @see ngat.sprat.mechanism.command.TemperatureCommand#getRunException
	 * @see ngat.sprat.mechanism.command.TemperatureCommand#getIsError
	 * @see ngat.sprat.mechanism.command.TemperatureCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.TemperatureCommand#getTemperature
	 */
 	protected double getMechanismTemperature(int sensorNumber) throws Exception
	{
		TemperatureCommand command = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			  ":getMechanismTemperature(sensorNumber="+sensorNumber+"):started.");
		command = new TemperatureCommand(mechanismHostname,mechanismPortNumber,sensorNumber);
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":getMechanismTemperature(sensorNumber="+sensorNumber+
					    "):command threw exception.",command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+
					    ":getMechanismTemperature(sensorNumber="+sensorNumber+
					    "):command returned an error:"+command.getErrorString());
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			  ":getMechanismTemperature(sensorNumber="+
			  sensorNumber+"):finished with temperature:"+
			  (command.getTemperature()+Sprat.CENTIGRADE_TO_KELVIN)+" K.");
		return (command.getTemperature()+Sprat.CENTIGRADE_TO_KELVIN);
	}

 	/**
	 * Get a mechanism humidity measurement.
	 * An instance of HumidityCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * @param sensorNumber Which number humidity sensor to query.
	 * @return The current humidity in percent as a double.
	 * @exception Thrown if the command fails in some way, or returns an error.
	 * @see HardwareImplementation#mechanismHostname
	 * @see HardwareImplementation#mechanismPortNumber
	 * @see ngat.sprat.mechanism.command.HumidityCommand
	 * @see ngat.sprat.mechanism.command.HumidityCommand#run
	 * @see ngat.sprat.mechanism.command.HumidityCommand#getRunException
	 * @see ngat.sprat.mechanism.command.HumidityCommand#getIsError
	 * @see ngat.sprat.mechanism.command.HumidityCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.HumidityCommand#getHumidity
	 */
 	protected double getHumidity(int sensorNumber) throws Exception
	{
		HumidityCommand command = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			  ":getHumidity(sensorNumber="+sensorNumber+"):started.");
		command = new HumidityCommand(mechanismHostname,mechanismPortNumber,sensorNumber);
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":getHumidity(sensorNumber="+sensorNumber+
					    "):command threw exception.",command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+
					    ":getHumidity(sensorNumber="+sensorNumber+
					    "):command returned an error:"+command.getErrorString());
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":getHumidity(sensorNumber="+
			  sensorNumber+"):finished with humidity:"+command.getHumidity()+" %.");
		return command.getHumidity();
	}

 	/**
	 * Get the gyro's X position in ADUs.
	 * An instance of GyroCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * @return The gyro's X position in ADUs as a double.
	 * @exception Thrown if the command fails in some way, or returns an error.
	 * @see HardwareImplementation#mechanismHostname
	 * @see HardwareImplementation#mechanismPortNumber
	 * @see ngat.sprat.mechanism.command.GyroCommand
	 * @see ngat.sprat.mechanism.command.GyroCommand#run
	 * @see ngat.sprat.mechanism.command.GyroCommand#getRunException
	 * @see ngat.sprat.mechanism.command.GyroCommand#getIsError
	 * @see ngat.sprat.mechanism.command.GyroCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.GyroCommand#getPositionX
	 */
 	protected double getGyroPositionX() throws Exception
	{
		GyroCommand command = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":getGyroPositionX:started.");
		command = new GyroCommand(mechanismHostname,mechanismPortNumber);
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":getGyroPositionX:command threw exception.",command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+
					    ":getGyroPositionX:command returned an error:"+command.getErrorString());
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			  ":getGyroPositionX:finished with positions X:"+command.getPositionX()+
			  " Y:"+command.getPositionY()+" Z:"+command.getPositionZ()+".");
		return command.getPositionX();
	}

 	/**
	 * Get the gyro's Y position in ADUs.
	 * An instance of GyroCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * @return The gyro's Y position in ADUs as a double.
	 * @exception Thrown if the command fails in some way, or returns an error.
	 * @see HardwareImplementation#mechanismHostname
	 * @see HardwareImplementation#mechanismPortNumber
	 * @see ngat.sprat.mechanism.command.GyroCommand
	 * @see ngat.sprat.mechanism.command.GyroCommand#run
	 * @see ngat.sprat.mechanism.command.GyroCommand#getRunException
	 * @see ngat.sprat.mechanism.command.GyroCommand#getIsError
	 * @see ngat.sprat.mechanism.command.GyroCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.GyroCommand#getPositionY
	 */
 	protected double getGyroPositionY() throws Exception
	{
		GyroCommand command = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":getGyroPositionY:started.");
		command = new GyroCommand(mechanismHostname,mechanismPortNumber);
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":getGyroPositionY:command threw exception.",command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+
					    ":getGyroPositionY:command returned an error:"+command.getErrorString());
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			  ":getGyroPositionY:finished with positions X:"+command.getPositionX()+
			  " Y:"+command.getPositionY()+" Z:"+command.getPositionZ()+".");
		return command.getPositionY();
	}

 	/**
	 * Get the gyro's Z position in ADUs.
	 * An instance of GyroCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * @return The gyro's Z position in ADUs as a double.
	 * @exception Thrown if the command fails in some way, or returns an error.
	 * @see HardwareImplementation#mechanismHostname
	 * @see HardwareImplementation#mechanismPortNumber
	 * @see ngat.sprat.mechanism.command.GyroCommand
	 * @see ngat.sprat.mechanism.command.GyroCommand#run
	 * @see ngat.sprat.mechanism.command.GyroCommand#getRunException
	 * @see ngat.sprat.mechanism.command.GyroCommand#getIsError
	 * @see ngat.sprat.mechanism.command.GyroCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.GyroCommand#getPositionZ
	 */
 	protected double getGyroPositionZ() throws Exception
	{
		GyroCommand command = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":getGyroPositionZ:started.");
		command = new GyroCommand(mechanismHostname,mechanismPortNumber);
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":getGyroPositionZ:command threw exception.",command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+
					    ":getGyroPositionZ:command returned an error:"+command.getErrorString());
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			  ":getGyroPositionZ:finished with positions X:"+command.getPositionX()+
			  " Y:"+command.getPositionY()+" Z:"+command.getPositionZ()+".");
		return command.getPositionZ();
	}

	/**
	 * Get whether the Arc Lamp is turned on.
	 * An instance of ArcLampCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * @return If the ArcLampCommand returns 'on' then true is returned. 
	 *         If the ArcLampCommand returns 'off' false is returned. Otherwise an exception is thrown.
	 * @exception Thrown if the command fails in some way, or returns an error, 
	 *            or returns a current state that is not 'on' or 'off'.
	 * @see HardwareImplementation#mechanismHostname
	 * @see HardwareImplementation#mechanismPortNumber
	 * @see ngat.sprat.mechanism.command.ArcLampCommand
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#run
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#getRunException
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#getIsError
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#getCurrentState
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#STATE_ON
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#STATE_OFF
	 */
 	protected boolean getArcLampIsOn() throws Exception
	{
		ArcLampCommand command = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":getArcLampIsOn:started.");
		command = new ArcLampCommand(mechanismHostname,mechanismPortNumber);
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":getArcLampIsOn:command threw exception.",command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+
					    ":getArcLampIsOn:command returned an error:"+command.getErrorString());
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			  ":getArcLampIsOn:finished with lamp state:"+
			  ArcLampCommand.stateToString(command.getCurrentState())+".");
		switch(command.getCurrentState())
		{
			case ArcLampCommand.STATE_ON:
				return true;
			case ArcLampCommand.STATE_OFF:
				return false;
			case ArcLampCommand.STATE_ERROR:
			case ArcLampCommand.STATE_UNKNOWN:
			default:
				throw new Exception(this.getClass().getName()+
						    ":getArcLampIsOn:command returned an unknwon or illegal state:"+
						    command.getCurrentState());
		}
	}

	/**
	 * Get whether the W (Tungsten) Lamp is turned on.
	 * An instance of WLampCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * @return If the WLampCommand returns 'on' then true is returned. 
	 *         If the WLampCommand returns 'off' false is returned. Otherwise an exception is thrown.
	 * @exception Thrown if the command fails in some way, or returns an error, 
	 *            or returns a current state that is not 'on' or 'off'.
	 * @see HardwareImplementation#mechanismHostname
	 * @see HardwareImplementation#mechanismPortNumber
	 * @see ngat.sprat.mechanism.command.WLampCommand
	 * @see ngat.sprat.mechanism.command.WLampCommand#run
	 * @see ngat.sprat.mechanism.command.WLampCommand#getRunException
	 * @see ngat.sprat.mechanism.command.WLampCommand#getIsError
	 * @see ngat.sprat.mechanism.command.WLampCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.WLampCommand#getCurrentState
	 * @see ngat.sprat.mechanism.command.WLampCommand#STATE_ON
	 * @see ngat.sprat.mechanism.command.WLampCommand#STATE_OFF
	 */
 	protected boolean getWLampIsOn() throws Exception
	{
		WLampCommand command = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":getWLampIsOn:started.");
		command = new WLampCommand(mechanismHostname,mechanismPortNumber);
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":getWLampIsOn:command threw exception.",command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+
					    ":getWLampIsOn:command returned an error:"+command.getErrorString());
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			  ":getWLampIsOn:finished with lamp state:"+
			  WLampCommand.stateToString(command.getCurrentState())+".");
		switch(command.getCurrentState())
		{
			case WLampCommand.STATE_ON:
				return true;
			case WLampCommand.STATE_OFF:
				return false;
			case WLampCommand.STATE_ERROR:
			case WLampCommand.STATE_UNKNOWN:
			default:
				throw new Exception(this.getClass().getName()+
						    ":getWLampIsOn:command returned an unknwon or illegal state:"+
						    command.getCurrentState());
		}
	}

   	/**
	 * Get the CCD C layer hostname and port number from the properties into some internal variables.
	 * <ul>
	 * <li>"sprat.ccd.c.hostname" contains the hostname of the CCD C layer.
	 * <li>"sprat.ccd.c.port_number" contains the port number of the CCD C layer.
	 * @see #status
	 * @see #ccdCLayerHostname
	 * @see #ccdCLayerPortNumber
	 * @see #status
	 * @see SpratStatus#getProperty
	 * @see SpratStatus#getPropertyInteger
	 */
	protected void getCCDCLayerConfig() throws Exception
	{
		ccdCLayerHostname = status.getProperty("sprat.ccd.c.hostname");
		ccdCLayerPortNumber = status.getPropertyInteger("sprat.ccd.c.port_number");
	}

	/**
	 * Send the extracted config data onto the C layer.
	 * @param xBin X/serial binning.
	 * @param yBin Y/parallel binning.
	 * @param useWindow If true, a subwindow is specified.
	 * @param window The subwindow, if specified.
	 * @exception Exception Thrown if an error occurs.
	 * @see HardwareImplementation#ccdCLayerHostname
	 * @see HardwareImplementation#ccdCLayerPortNumber
	 * @see ngat.phase2.Window
	 * @see ngat.sprat.ccd.command.ConfigCommand
	 * @see ngat.sprat.ccd.command.ConfigCommand#setAddress
	 * @see ngat.sprat.ccd.command.ConfigCommand#setPortNumber
	 * @see ngat.sprat.ccd.command.ConfigCommand#setCommand
	 * @see ngat.sprat.ccd.command.ConfigCommand#sendCommand
	 * @see ngat.sprat.ccd.command.ConfigCommand#getParsedReplyOK
	 * @see ngat.sprat.ccd.command.ConfigCommand#getReturnCode
	 * @see ngat.sprat.ccd.command.ConfigCommand#getParsedReply
	 */
	protected void sendConfigCommand(int xBin,int yBin,boolean useWindow,Window window) throws Exception
	{
		ConfigCommand command = null;
		int returnCode;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendConfigCommand:"+
			  "\n\t:X Bin = "+xBin+
			  "\n\t:Y Bin = "+yBin+"."+
			  "\n\t:Use Window = "+useWindow+".");
		if(useWindow)
		{
			sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendConfigCommand:"+
				  "\n\t:X Start = "+window.getXs()+
				  "\n\t:Y Start = "+window.getYs()+
				  "\n\t:X End = "+window.getXe()+
				  "\n\t:Y End = "+window.getYe()+".");
		}
		command = new ConfigCommand();
		// configure C comms
		command.setAddress(ccdCLayerHostname);
		command.setPortNumber(ccdCLayerPortNumber);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendConfigCommand:hostname = "+ccdCLayerHostname+
			  " :port number = "+ccdCLayerPortNumber+".");
		// set command parameters
		if(useWindow)
		{
			command.setCommand(xBin,yBin,window.getXs(),window.getXe(),window.getYs(),window.getYe());
		}
		else
		{
			command.setCommand(xBin,yBin);
		}
		// actually send the command to the C layer
		// This method can throw an exception 
		command.sendCommand();
		// check the parsed reply
		if(command.getParsedReplyOK() == false)
		{
			returnCode = command.getReturnCode();
			errorString = command.getParsedReply();
			sprat.log(Logging.VERBOSITY_TERSE,"sendConfigCommand:config command failed with return code "+
				  returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
					    ":sendConfigCommand:Command failed with return code "+returnCode+
					    " and error string:"+errorString);
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendConfigCommand:finished.");
	}

	/**
	 * Get the current, or C layer cached, CCD temperature.
	 * An instance of StatusTemperatureGetCommand is used to send the command
	 * to the C layer, using ccdCLayerHostname and ccdCLayerPortNumber. A timestamp is also
	 * retrieved (when the temperature was actually measured, it may be a cached value), and this
	 * is stored in the "Temperature Timestamp" key.
	 * @return A double representing the CCD temperature in Kelvin.
	 * @exception Exception Thrown if an error occurs.
	 * @see HardwareImplementation#ccdCLayerHostname
	 * @see HardwareImplementation#ccdCLayerPortNumber
	 * @see ngat.sprat.Sprat#CENTIGRADE_TO_KELVIN
	 * @see ngat.sprat.ccd.command.StatusTemperatureGetCommand
	 * @see ngat.sprat.ccd.command.StatusTemperatureGetCommand#setAddress
	 * @see ngat.sprat.ccd.command.StatusTemperatureGetCommand#setPortNumber
	 * @see ngat.sprat.ccd.command.StatusTemperatureGetCommand#sendCommand
	 * @see ngat.sprat.ccd.command.StatusTemperatureGetCommand#getReturnCode
	 * @see ngat.sprat.ccd.command.StatusTemperatureGetCommand#getParsedReply
	 * @see ngat.sprat.ccd.command.StatusTemperatureGetCommand#getTemperature
	 * @see ngat.sprat.ccd.command.StatusTemperatureGetCommand#getTimestamp
	 */
	protected double getCCDTemperature() throws Exception
	{
		StatusTemperatureGetCommand statusCommand = null;
		int returnCode;
		String errorString = null;
		double temperature;
		Date timestamp;

		sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":getCCDTemperature:started.");
		statusCommand = new StatusTemperatureGetCommand();
		statusCommand.setAddress(ccdCLayerHostname);
		statusCommand.setPortNumber(ccdCLayerPortNumber);
		// actually send the command to the CCD C layer
		statusCommand.sendCommand();
		// check the parsed reply
		if(statusCommand.getParsedReplyOK() == false)
		{
			returnCode = statusCommand.getReturnCode();
			errorString = statusCommand.getParsedReply();
			sprat.log(Logging.VERBOSITY_TERSE,
				 "getCCDTemperature:exposure run command failed with return code "+
				 returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
					 ":getCCDTemperature:exposure run command failed with return code "+
					    returnCode+" and error string:"+errorString);
		}
		temperature = statusCommand.getTemperature();
		timestamp = statusCommand.getTimestamp();
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			  ":getCCDTemperature:finished with temperature:"+
			   temperature+" C measured at "+timestamp);
		return temperature+Sprat.CENTIGRADE_TO_KELVIN;
	}

	/**
	 * This routine takes a Date, and formats a string to the correct FITS format for that date and returns it.
	 * The format should be 'CCYY-MM-DDThh:mm:ss[.sss...]'.
	 * @param date The date to return a string for.
	 * @return Returns a String version of the date in the correct new FITS format.
	 */
// diddly use keyword to determine format of string
// DATE-OBS format 'CCYY-MM-DDThh:mm:ss[.sss...]'
// DATE 'CCYY-MM-DD'
// UTSTART 'HH:MM:SS.s'
// others?
	protected String dateFitsFieldToString(Date date)
	{
		Calendar calendar = Calendar.getInstance();
		NumberFormat numberFormat = NumberFormat.getInstance();

		numberFormat.setMinimumIntegerDigits(2);
		calendar.setTime(date);
		return new String(calendar.get(Calendar.YEAR)+"-"+
			numberFormat.format(calendar.get(Calendar.MONTH)+1)+"-"+
			numberFormat.format(calendar.get(Calendar.DAY_OF_MONTH))+"T"+
			numberFormat.format(calendar.get(Calendar.HOUR_OF_DAY))+":"+
			numberFormat.format(calendar.get(Calendar.MINUTE))+":"+
			numberFormat.format(calendar.get(Calendar.SECOND))+"."+
			calendar.get(Calendar.MILLISECOND));
	}

	/**
	 * Method to send an instance of ACK back to the client. This stops the client timing out, whilst we
	 * work out what to attempt next.
	 * @param command The instance of COMMAND we are currently running.
	 * @param done The instance of COMMAND_DONE to fill in with errors we receive.
	 * @param timeToComplete The time it will take to complete the next set of operations
	 *	before the next ACK or DONE is sent to the client. The time is in milliseconds. 
	 * 	The server connection thread's default acknowledge time is added to the value before it
	 * 	is sent to the client, to allow for network delay etc.
	 * @return The method returns true if the ACK was sent successfully, false if an error occured.
	 * @see #serverConnectionThread
	 * @see ngat.message.base.ACK
	 * @see SpratTCPServerConnectionThread#sendAcknowledge
	 */
	protected boolean sendBasicAck(COMMAND command,COMMAND_DONE done,int timeToComplete)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(timeToComplete+serverConnectionThread.getDefaultAcknowledgeTime());
		try
		{
			serverConnectionThread.sendAcknowledge(acknowledge,true);
		}
		catch(IOException e)
		{
			String errorString = new String(command.getId()+":sendBasicAck:Sending ACK failed:");
			sprat.error(this.getClass().getName()+":"+errorString,e);
			done.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1203);
			done.setErrorString(errorString+e);
			done.setSuccessful(false);
			return false;
		}
		return true;
	}
}

