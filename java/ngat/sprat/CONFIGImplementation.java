// CONFIGImplementation.java
// $HeadURL$
package ngat.sprat;

import java.lang.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.phase2.*;
import ngat.sprat.ccd.command.*;
import ngat.sprat.mechanism.*;
import ngat.sprat.mechanism.command.*;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the CONFIG command sent to a server using the
 * Java Message System.
 * @author Chris Motram
 * @version $Revision: 1.3 $
 */
public class CONFIGImplementation extends CommandImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Constructor. 
	 */
	public CONFIGImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.CONFIG&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.CONFIG";
	}

	/**
	 * This method gets the CONFIG command's acknowledge time.
	 * This method returns an ACK with timeToComplete set to the &quot; sprat.config.acknowledge_time &quot;
	 * held in the SPRAT configuration file. 
	 * If this cannot be found/is not a valid number the default acknowledge time is used instead.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set to a time (in milliseconds).
	 * @see #sprat
	 * @see ngat.sprat.Sprat#getStatus
	 * @see ngat.sprat.SpratStatus#getPropertyInteger
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see SpratTCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;
		int timeToComplete = 0;

		acknowledge = new ACK(command.getId());
		try
		{
			timeToComplete += sprat.getStatus().getPropertyInteger("sprat.config.acknowledge_time");
		}
		catch(NumberFormatException e)
		{
			sprat.error(this.getClass().getName()+":calculateAcknowledgeTime:"+e);
			timeToComplete += serverConnectionThread.getDefaultAcknowledgeTime();
		}
		acknowledge.setTimeToComplete(timeToComplete);
		return acknowledge;
	}

	/**
	 * This method implements the CONFIG command. 
	 * <ul>
	 * <li>The command is casted.
	 * <li>The DONE message is created.
	 * <li>The Sprat Status instance is retrieved.
	 * <li>The config is checked to ensure it isn't null and is of the right class.
	 * <li>The detector is extracted.
	 * <li>The detector is checked to ensure it is the right class.
	 * <li>The binning is checked, as are the window flags.
	 * <li>The window is extracted.
	 * <li>sendConfigCommand is called with the extracted data to send a C layer Config command.
	 * <li>We test for command abort.
	 * <li>We move the grism by calling moveGrism.
	 * <li>We test for command abort.
	 * <li>We rotate the grism by calling rotateGrism.
	 * <li>We test for command abort.
	 * <li>We move the slit by calling moveSlit.
	 * <li>We test for command abort.
	 * <li>We call setFocusOffset to tell the RCS/TCS the focus offset required.
	 * <li>We increment the config Id.
	 * <li>We save the config name in the Sprat status instance for future reference.
	 * <li>We return success.
	 * </ul>
	 * @see #testAbort
	 * @see #setFocusOffset
	 * @see #sprat
	 * @see #status
	 * @see #sendConfigCommand
	 * @see #moveSlit
	 * @see #rotateGrism
	 * @see #moveGrism
	 * @see ngat.sprat.Sprat#getStatus
	 * @see ngat.sprat.SpratStatus#incConfigId
	 * @see ngat.sprat.SpratStatus#setConfigName
	 * @see ngat.phase2.SpratConfig
	 * @see ngat.phase2.SpratConfig#POSITION_OUT
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		CONFIG configCommand = null;
		SpratDetector detector = null;
		SpratConfig config = null;
		Window window = null;
		CONFIG_DONE configDone = null;
		String configName = null;
		boolean useWindow = false;

	// test contents of command.
		configCommand = (CONFIG)command;
		configDone = new CONFIG_DONE(command.getId());
		if(testAbort(configCommand,configDone) == true)
			return configDone;
		if(configCommand.getConfig() == null)
		{
			sprat.error(this.getClass().getName()+":processCommand:"+command+":Config was null.");
			configDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+800);
			configDone.setErrorString(":Config was null.");
			configDone.setSuccessful(false);
			return configDone;
		}
		if((configCommand.getConfig() instanceof SpratConfig) == false)
		{
			sprat.error(this.getClass().getName()+":processCommand:"+
				    command+":Config has wrong class:"+
				    configCommand.getConfig().getClass().getName());
			configDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+801);
			configDone.setErrorString(":Config has wrong class:"+
				configCommand.getConfig().getClass().getName());
			configDone.setSuccessful(false);
			return configDone;
		}
	// test abort
		if(testAbort(configCommand,configDone) == true)
			return configDone;
	// get config from configCommand.
		config = (SpratConfig)configCommand.getConfig();
	// get local detector copy
		if((config.getDetector(0) instanceof SpratDetector) == false)
		{
			sprat.error(this.getClass().getName()+":processCommand:"+
				command+":Config detector has wrong class:"+
				config.getDetector(0).getClass().getName());
			configDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+802);
			configDone.setErrorString(":Config detector has wrong class:"+
				config.getDetector(0).getClass().getName());
			configDone.setSuccessful(false);
			return configDone;
		}
		detector = (SpratDetector)config.getDetector(0);
	// get configuration Id - used later
		configName = config.getId();
		sprat.log(Logging.VERBOSITY_VERY_TERSE,"Command:"+
			  configCommand.getClass().getName()+
			  "\n\t:id = "+configName+
			  "\n\t:X Bin = "+detector.getXBin()+
			  "\n\t:Y Bin = "+detector.getYBin()+".");
	// windows
		for(int i = 0; i < detector.getMaxWindowCount(); i++)
		{
			Window w = null;

			if(detector.isActiveWindow(i))
			{
				w = detector.getWindow(i);
				if(w == null)
				{
					String errorString = new String("Window "+i+" is null.");

					sprat.error(this.getClass().getName()+":processCommand:"+
						command+":"+errorString);
					configDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+803);
					configDone.setErrorString(errorString);
					configDone.setSuccessful(false);
					return configDone;
				}
				window = w;
			}
		}// end for on windows
		// send command to C layer
		try
		{
			sendConfigCommand(detector.getXBin(),detector.getYBin(),
					  (detector.getWindowFlags() > 0),window);
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+
				    ":processCommand:Sending config command to C layer failed:"+command,e);
			configDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+804);
			configDone.setErrorString(e.toString());
			configDone.setSuccessful(false);
			return configDone;
		}
	// test abort
		if(testAbort(configCommand,configDone) == true)
			return configDone;
		// configure sprat mechanisms
		// move and rotate grism.
		try
		{
			// we can only move the grism in and out, if the rotation cylinder is stowed
			rotateGrism(0);
			// move the grism to the configured position
			moveGrism(config.getGrismPosition());
			// rotate the grism to the configured position
			// Due to the construction of the rotation mechanism, this will only work if the
			// grism is IN the beam
			if(config.getGrismPosition() == SpratConfig.POSITION_IN)
				rotateGrism(config.getGrismRotation());
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:Moving Grism failed:"+command,e);
			configDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+805);
			configDone.setErrorString(e.toString());
			configDone.setSuccessful(false);
			return configDone;
		}
	// test abort
		if(testAbort(configCommand,configDone) == true)
			return configDone;
		// move slit
		try
		{
			moveSlit(config.getSlitPosition());
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:Moving Slit failed:"+command,e);
			configDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+809);
			configDone.setErrorString(e.toString());
			configDone.setSuccessful(false);
			return configDone;
		}
	// test abort
		if(testAbort(configCommand,configDone) == true)
			return configDone;
	// Issue ISS OFFSET_FOCUS commmand. 
		try
		{
			setFocusOffset(configCommand.getId());
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:"+command+":setFocusOffset failed:",e);
			configDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+807);
			configDone.setErrorString(e.toString());
			configDone.setSuccessful(false);
			return configDone;
		}
	// Increment unique config ID.
	// This is queried when saving FITS headers to get the CONFIGID value.
		try
		{
			status.incConfigId();
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:"+
				    command+":Incrementing configuration ID:"+e.toString());
			configDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+806);
			configDone.setErrorString("Incrementing configuration ID:"+e.toString());
			configDone.setSuccessful(false);
			return configDone;
		}
	// Store name of configuration used in status object
	// This is queried when saving FITS headers to get the CONFNAME value.
		status.setConfigName(configName);
	// setup return object.
		configDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
		configDone.setErrorString("");
		configDone.setSuccessful(true);
	// return done object.
		return configDone;
	}

	/**
	 * Send the extracted config data onto the C layer.
	 * @param xBin X/serial binning.
	 * @param yBin Y/parallel binning.
	 * @param useWindow If true, a subwindow is specified.
	 * @param window The subwindow, if specified.
	 * @exception Exception Thrown if an error occurs.
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
		int portNumber,returnCode;
		String hostname = null;
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
		hostname = status.getProperty("sprat.ccd.c.hostname");
		portNumber = status.getPropertyInteger("sprat.ccd.c.port_number");
		command.setAddress(hostname);
		command.setPortNumber(portNumber);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendConfigCommand:hostname = "+hostname+
			  " :port number = "+portNumber+".");
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
		int portNumber,sleepTime,timeoutTime,returnCode;
		String hostname = null;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"moveGrism:"+
			  "Position = "+SpratConfig.positionToString(position)+" ("+position+").");
		// retrieve config
		hostname = status.getProperty("sprat.mechanism.hostname");
		portNumber = status.getPropertyInteger("sprat.mechanism.port_number");
		sleepTime = status.getPropertyInteger("sprat.config.grism.move.sleep_time");
		timeoutTime = status.getPropertyInteger("sprat.config.grism.move.timeout_time");
		// setup command and mover objects
		sprat.log(Logging.VERBOSITY_VERBOSE,"moveGrism:Creating GrismCommand to send to "+
			  hostname+":"+portNumber+".");
		command = new GrismCommand(hostname,portNumber);
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
		int portNumber,sleepTime,timeoutTime;
		String hostname = null;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"rotateGrism:Position = "+position+".");
		// retrieve config
		hostname = status.getProperty("sprat.mechanism.hostname");
		portNumber = status.getPropertyInteger("sprat.mechanism.port_number");
		sleepTime = status.getPropertyInteger("sprat.config.grism.rotate.sleep_time");
		timeoutTime = status.getPropertyInteger("sprat.config.grism.rotate.timeout_time");
		// setup command and mover objects
		sprat.log(Logging.VERBOSITY_VERBOSE,"rotateGrism:Creating RotationCommand to send to "+
			  hostname+":"+portNumber+".");
		command = new RotationCommand(hostname,portNumber);
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
		int portNumber,sleepTime,timeoutTime;
		String hostname = null;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"moveSlit:"+
			  "Position = "+SpratConfig.positionToString(position)+" ("+position+").");
		// retrieve config
		hostname = status.getProperty("sprat.mechanism.hostname");
		portNumber = status.getPropertyInteger("sprat.mechanism.port_number");
		sleepTime = status.getPropertyInteger("sprat.config.slit.move.sleep_time");
		timeoutTime = status.getPropertyInteger("sprat.config.slit.move.timeout_time");
		// setup command and mover objects
		sprat.log(Logging.VERBOSITY_VERBOSE,"moveSlit:Creating SlitCommand to send to "+
			  hostname+":"+portNumber+".");
		command = new SlitCommand(hostname,portNumber);
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
	private void setFocusOffset(String id) throws Exception
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
}
