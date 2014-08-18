// GET_STATUSImplementation.java
// $HeadURL$
package ngat.sprat;

import java.lang.*;
import java.util.*;

import ngat.message.base.*;
import ngat.message.ISS_INST.ISS_TO_INST;
import ngat.message.ISS_INST.GET_STATUS;
import ngat.message.ISS_INST.GET_STATUS_DONE;
import ngat.sprat.ccd.command.*;
import ngat.sprat.mechanism.command.*;
import ngat.util.logging.*;
import ngat.util.ExecuteCommand;

/**
 * This class provides the implementation for the GET_STATUS command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision$
 */
public class GET_STATUSImplementation extends HardwareImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The index in the commsInstrumentStatus array of the CCD comms instrument status.
	 * @see #commsInstrumentStatus
	 */
	public final static int COMMS_INSTRUMENT_STATUS_CCD = 0;
	/**
	 * The index in the commsInstrumentStatus array of the mechanism control (arduino) 
	 * comms instrument status.
	 * @see #commsInstrumentStatus
	 */
	public final static int COMMS_INSTRUMENT_STATUS_MECHANISM = 1;
	/**
	 * The number of elements in the commsInstrumentStatus array.
	 * @see #commsInstrumentStatus
	 */
	public final static int COMMS_INSTRUMENT_STATUS_COUNT = 2;
	/**
	 * This hashtable is created in processCommand, and filled with status data,
	 * and is returned in the GET_STATUS_DONE object.
	 */
	private Hashtable hashTable = null;
	/**
	 * Standard status string passed back in the hashTable, 
	 * describing the detector temperature status health,
	 * using the standard keyword KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS. 
	 * Initialised to VALUE_STATUS_UNKNOWN.
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_UNKNOWN
	 */
	private String detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_UNKNOWN;
	/**
	 * An array of standard status strings passed back in the hashTable, 
	 * describing the communication status of various bits of software/hardware the 
	 * Sprat robotic control system talks to.
	 * @see #COMMS_INSTRUMENT_STATUS_COUNT
	 */
	private String commsInstrumentStatus[] = new String[COMMS_INSTRUMENT_STATUS_COUNT];
	/**
	 * The hostname to send CCD C layer commands to.
	 */
	protected String ccdCLayerHostname = null;
	/**
	 * The port number to send CCD C layer commands to.
	 */
	protected int ccdCLayerPortNumber;
	/**
	 * The hostname to send Sprat mechanism Arduino commands to.
	 */
	protected String mechanismHostname = null;
	/**
	 * The port number to send Sprat mechanism Arduino commands to.
	 */
	protected int mechanismPortNumber;
	/**
	 * Exposure status retrieved by the StatusExposureStatusCommand command.
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_NONE
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_WAIT_START
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_CLEAR
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_EXPOSE
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_PRE_READOUT
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_READOUT
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_POST_READOUT
	 */
	protected int exposureStatus = StatusExposureStatusCommand.CCD_EXPOSURE_STATUS_NONE;
	/**
	 * String version of the exposure status returned by the StatusExposureStatusCommand command.
	 * Usually one of: NONE,WAIT_START,CLEAR,EXPOSE,PRE_READOUT,READOUT,POST_READOUT
	 */
	protected String exposureStatusString = null;

	/**
	 * Constructor.
	 */
	public GET_STATUSImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.GET_STATUS&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.GET_STATUS";
	}

	/**
	 * This method gets the GET_STATUS command's acknowledge time. 
	 * This takes the default acknowledge time to implement.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see SpratTCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the GET_STATUS command. 
	 * The local hashTable is setup (returned in the done object) and a local copy of status setup.
	 * <ul>
	 * <li>getCCDCLayerConfig is called to get the CCD  C layer address/port number.
	 * <li>getMechanismConfig is called to get the mechanism Arduino address/port number.
	 * <li>getExposureStatus is called to get the exposure status into the 
	 *     exposureStatus and exposureStatusString variables.
	 * <li>"Exposure Status" and "Exposure Status String" status properties are added to the hashtable.
	 * <li>The "Instrument" status property is set to the "sprat.get_status.instrument_name" 
	 *     property value.
	 * <li>The detectorTemperatureInstrumentStatus is initialised.
	 * <li>The "currentCommand" status hashtable value is set to the currently executing command.
	 * <li>getStatusExposureIndex / getStatusExposureMultrun / getStatusExposureRun / 
	 *     getStatusExposureWindow are called to add some basic status to the hashtable.
	 * <li>"Exposure Count" status hashtable value is set to 0, 
	 *     and "Exposure Length" status hashtable value is set to the exposure length using 
	 *     getStatusExposureLength.
	 * <li>getIntermediateStatus is called if the GET_STATUS command level is at least intermediate.
	 * <li>getFullStatusis called if the GET_STATUS command level is at least full.
	 * </ul>
	 * An object of class GET_STATUS_DONE is returned, with the information retrieved.
	 * @param command The GET_STATUS command.
	 * @return An object of class GET_STATUS_DONE is returned.
	 * @see #COMMS_INSTRUMENT_STATUS_COUNT
	 * @see #sprat
	 * @see #status
	 * @see #hashTable
	 * @see #detectorTemperatureInstrumentStatus
	 * @see #commsInstrumentStatus
	 * @see #getCCDCLayerConfig
	 * @see #getMechanismConfig
	 * @see #getExposureStatus
	 * @see #getStatusExposureIndex
	 * @see #getStatusExposureCount
	 * @see #getStatusExposureMultrun
	 * @see #getStatusExposureRun
	 * @see #getStatusExposureLength
	 * @see #getStatusExposureStartTime
	 * @see #getIntermediateStatus
	 * @see #getFullStatus
	 * @see #exposureStatus
	 * @see #exposureStatusString
	 * @see SpratStatus#getProperty
	 * @see SpratStatus#getCurrentCommand
	 * @see GET_STATUS#getLevel
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#KEYWORD_INSTRUMENT_STATUS
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		GET_STATUS getStatusCommand = (GET_STATUS)command;
		GET_STATUS_DONE getStatusDone = new GET_STATUS_DONE(command.getId());
		ISS_TO_INST currentCommand = null;
		int currentMode;

		try
		{
			// Create new hashtable to be returned
			// v1.5 generic typing of collections:<String, Object>, 
			// can't be used due to v1.4 compatibility
			hashTable = new Hashtable();
			// get CCD C layer and Arduino mechanism comms configuration
			getCCDCLayerConfig();
			getMechanismConfig();
			// exposure status
			getExposureStatus();
			hashTable.put("Exposure Status",new Integer(exposureStatus));
			hashTable.put("Exposure Status String",new String(exposureStatusString));
			// current mode, derived from exposure status
			currentMode = getCurrentMode();
			getStatusDone.setCurrentMode(currentMode);
			// What instrument is this?
			hashTable.put("Instrument",status.getProperty("sprat.get_status.instrument_name"));
			// Initialise Standard status to UNKNOWN
			detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_UNKNOWN;
			hashTable.put(GET_STATUS_DONE.KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS,
				      detectorTemperatureInstrumentStatus);
			hashTable.put(GET_STATUS_DONE.KEYWORD_INSTRUMENT_STATUS,
				      GET_STATUS_DONE.VALUE_STATUS_UNKNOWN);
			// initialise comms status to unknown
			for(int i = 0; i < COMMS_INSTRUMENT_STATUS_COUNT; i++)
			{
				commsInstrumentStatus[i] = GET_STATUS_DONE.VALUE_STATUS_UNKNOWN;
			}
			// current command
			currentCommand = status.getCurrentCommand();
			if(currentCommand == null)
				hashTable.put("currentCommand","");
			else
				hashTable.put("currentCommand",currentCommand.getClass().getName());
			// basic information
			getStatusExposureIndex();
			// "Exposure Count" is searched for by the IcsGUI
			getStatusExposureCount(); 
			getStatusExposureMultrun();
			getStatusExposureRun();
			// "Exposure Number" is added in getStatusExposureIndex
			// "Exposure Length" is needed for IcsGUI
			getStatusExposureLength(); 
			// "Exposure Start Time" is needed for IcsGUI
			getStatusExposureStartTime();
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+
				    ":processCommand:Retrieving basic status failed.",e);
			getStatusDone.setDisplayInfo(hashTable);
			getStatusDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2500);
			getStatusDone.setErrorString("processCommand:Retrieving basic status failed:"+e);
			getStatusDone.setSuccessful(false);
			return getStatusDone;
		}
	// intermediate level information - basic plus controller calls.
		if(getStatusCommand.getLevel() >= GET_STATUS.LEVEL_INTERMEDIATE)
		{
			getIntermediateStatus();
		}// end if intermediate level status
	// Get full status information.
		if(getStatusCommand.getLevel() >= GET_STATUS.LEVEL_FULL)
		{
			getFullStatus();
		}
	// set hashtable and return values.
		getStatusDone.setDisplayInfo(hashTable);
		getStatusDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
		getStatusDone.setErrorString("");
		getStatusDone.setSuccessful(true);
	// return done object.
		return getStatusDone;
	}

	/**
	 * Get the CCD C layer hostname and port number from the properties into some internal variables.
	 * @see #status
	 * @see #ccdCLayerHostname
	 * @see #ccdCLayerPortNumber
	 * @see SpratStatus#getProperty
	 * @see SpratStatus#getPropertyInteger
	 */
	protected void getCCDCLayerConfig() throws Exception
	{
		ccdCLayerHostname = status.getProperty("sprat.ccd.c.hostname");
		ccdCLayerPortNumber = status.getPropertyInteger("sprat.ccd.c.port_number");
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
	 * Get the exposure status. An instance of StatusExposureStatusCommand is used to send the command
	 * to the CCD C layer, using ccdCLayerHostname and ccdCLayerPortNumber. 
	 * The returned values are stored in exposureStatus and exposureStatusString.
	 * @exception Exception Thrown if an error occurs.
	 * @see #exposureStatus
	 * @see #exposureStatusString
	 * @see #ccdCLayerHostname
	 * @see #ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_NONE
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_WAIT_START
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_CLEAR
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_EXPOSE
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_PRE_READOUT
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_READOUT
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_POST_READOUT
	 */
	protected void getExposureStatus() throws Exception
	{
		StatusExposureStatusCommand statusCommand = null;
		int returnCode;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getExposureStatus:started.");
		statusCommand = new StatusExposureStatusCommand();
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
				  "getExposureStatus:exposure status command failed with return code "+
				  returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
				      ":getExposureStatus:exposure status command failed with return code "+
					    returnCode+" and error string:"+errorString);
		}
		exposureStatus = statusCommand.getExposureStatus();
		exposureStatusString = statusCommand.getExposureStatusString();
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getExposureStatus:finished with status:"+
			  exposureStatusString);
	}

	/**
	 * Internal method to get the current mode, the GET_STATUS command will return.
	 * This is derived from the exposureStatus which should have previously been retrieved.
	 * @see #exposureStatus
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_NONE
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_WAIT_START
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_CLEAR
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_EXPOSE
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_PRE_READOUT
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_READOUT
	 * @see ngat.sprat.ccd.command.StatusExposureStatusCommand#CCD_EXPOSURE_STATUS_POST_READOUT
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_IDLE
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_EXPOSING
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_READING_OUT
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#MODE_ERROR
	 */
	private int getCurrentMode()
	{
		int currentMode;

		currentMode = GET_STATUS_DONE.MODE_IDLE;
		switch(exposureStatus)
		{
			case StatusExposureStatusCommand.CCD_EXPOSURE_STATUS_NONE:
				currentMode = GET_STATUS_DONE.MODE_IDLE;
				break;
			case StatusExposureStatusCommand.CCD_EXPOSURE_STATUS_WAIT_START:
				currentMode = GET_STATUS_DONE.MODE_IDLE;
				break;
			case StatusExposureStatusCommand.CCD_EXPOSURE_STATUS_CLEAR:
				currentMode = GET_STATUS_DONE.MODE_IDLE;
				break;
			case StatusExposureStatusCommand.CCD_EXPOSURE_STATUS_EXPOSE:
				currentMode = GET_STATUS_DONE.MODE_EXPOSING;
				break;
			case StatusExposureStatusCommand.CCD_EXPOSURE_STATUS_PRE_READOUT:
				currentMode = GET_STATUS_DONE.MODE_EXPOSING;
				break;
			case StatusExposureStatusCommand.CCD_EXPOSURE_STATUS_READOUT:
				currentMode = GET_STATUS_DONE.MODE_READING_OUT;
				break;
			case StatusExposureStatusCommand.CCD_EXPOSURE_STATUS_POST_READOUT:
				currentMode = GET_STATUS_DONE.MODE_READING_OUT;
				break;
			default:
				currentMode = GET_STATUS_DONE.MODE_ERROR;
				break;
		}
		return currentMode;
	}

	/**
	 * Get the exposure index. An instance of StatusMultrunIndexCommand is used to send the command
	 * to the CCD C layer, using ccdCLayerHostname and ccdCLayerPortNumber. 
	 * The returned value is stored in the hashTable, under the "Exposure Index" key.
	 * @exception Exception Thrown if an error occurs.
	 * @see #hashTable
	 * @see #ccdCLayerHostname
	 * @see #ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.StatusMultrunIndexCommand
	 */
	protected void getStatusExposureIndex() throws Exception
	{
		StatusMultrunIndexCommand statusCommand = null;
		int returnCode,exposureIndex;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getStatusExposureIndex:started.");
		statusCommand = new StatusMultrunIndexCommand();
		statusCommand.setAddress(ccdCLayerHostname);
		statusCommand.setPortNumber(ccdCLayerPortNumber);
		// actually send the command to the C layer
		statusCommand.sendCommand();
		// check the parsed reply
		if(statusCommand.getParsedReplyOK() == false)
		{
			returnCode = statusCommand.getReturnCode();
			errorString = statusCommand.getParsedReply();
			sprat.log(Logging.VERBOSITY_TERSE,
				  "getStatusExposureIndex:exposure index command failed with return code "+
				  returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
				 ":getStatusExposureIndex:exposure index command failed with return code "+
					    returnCode+" and error string:"+errorString);
		}
		exposureIndex = statusCommand.getExposureIndex();
		hashTable.put("Exposure Index",new Integer(exposureIndex));
		// exposure number is really the same thing, but is used by the IcsGUI.
		hashTable.put("Exposure Number",new Integer(exposureIndex));
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getStatusExposureIndex:finished with index:"+
			  exposureIndex);
	}

	/**
	 * Get the exposure count. An instance of StatusMultrunCountCommand is used to send the command
	 * to the CCD C layer, using ccdCLayerHostname and ccdCLayerPortNumber. 
	 * The returned value is stored in the hashTable, under the "Exposure Count" key.
	 * @exception Exception Thrown if an error occurs.
	 * @see #hashTable
	 * @see #ccdCLayerHostname
	 * @see #ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.StatusMultrunCountCommand
	 */
	protected void getStatusExposureCount() throws Exception
	{
		StatusMultrunCountCommand statusCommand = null;
		int returnCode,exposureCount;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getStatusExposureCount:started.");
		statusCommand = new StatusMultrunCountCommand();
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
				  "getStatusExposureCount:exposure count command failed with return code "+
				  returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
				  ":getStatusExposureCount:exposure count command failed with return code "+
					    returnCode+" and error string:"+errorString);
		}
		exposureCount = statusCommand.getExposureCount();
		hashTable.put("Exposure Count",new Integer(exposureCount));
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getStatusExposureCount:finished with count:"+
			  exposureCount);
	}

	/**
	 * Get the exposure multrun. An instance of StatusExposureMultrunCommand is used to send the command
	 * to the CCD C layer, using ccdCLayerHostname and ccdCLayerPortNumber. 
	 * The returned value is stored in the hashTable, under the "Exposure Multrun" key.
	 * @exception Exception Thrown if an error occurs.
	 * @see #hashTable
	 * @see #ccdCLayerHostname
	 * @see #ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.StatusExposureMultrunCommand
	 */
	protected void getStatusExposureMultrun() throws Exception
	{
		StatusExposureMultrunCommand statusCommand = null;
		int returnCode,exposureMultrun;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getStatusExposureMultrun:started.");
		statusCommand = new StatusExposureMultrunCommand();
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
			     "getStatusExposureMultrun:exposure multrun command failed with return code "+
				  returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
			      ":getStatusExposureMultrun:exposure multrun command failed with return code "+
					    returnCode+" and error string:"+errorString);
		}
		exposureMultrun = statusCommand.getExposureMultrun();
		hashTable.put("Exposure Multrun",new Integer(exposureMultrun));
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,
			  "getStatusExposureMultrun:finished with multrun number:"+exposureMultrun);
	}

	/**
	 * Get the exposure multrun run. An instance of StatusExposureRunCommand is used to send the command
	 * to the CCD C layer, using ccdCLayerHostname and ccdCLayerPortNumber. 
	 * The returned value is stored in the hashTable, under the "Exposure Run" key.
	 * @exception Exception Thrown if an error occurs.
	 * @see #hashTable
	 * @see #ccdCLayerHostname
	 * @see #ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.StatusExposureRunCommand
	 */
	protected void getStatusExposureRun() throws Exception
	{
		StatusExposureRunCommand statusCommand = null;
		int returnCode,exposureRun;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getStatusExposureRun:started.");
		statusCommand = new StatusExposureRunCommand();
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
				   "getStatusExposureRun:exposure run command failed with return code "+
				   returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
				    ":getStatusExposureRun:exposure run command failed with return code "+
					    returnCode+" and error string:"+errorString);
		}
		exposureRun = statusCommand.getExposureRun();
		hashTable.put("Exposure Run",new Integer(exposureRun));
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getStatusExposureRun:finished with run number:"+
			   exposureRun);
	}

	/**
	 * Get the exposure length. An instance of StatusExposureLengthCommand is used to send the command
	 * to the CCD C layer, using ccdCLayerHostname and ccdCLayerPortNumber. 
	 * The returned value is stored in the hashTable, under the "Exposure Length" key.
	 * @exception Exception Thrown if an error occurs.
	 * @see #hashTable
	 * @see #ccdCLayerHostname
	 * @see #ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.StatusExposureLengthCommand
	 */
	protected void getStatusExposureLength() throws Exception
	{
		StatusExposureLengthCommand statusCommand = null;
		int returnCode,exposureLength;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getStatusExposureLength:started.");
		statusCommand = new StatusExposureLengthCommand();
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
			      "getStatusExposureLength:exposure length command failed with return code "+
				  returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
			       ":getStatusExposureLength:exposure length command failed with return code "+
					    returnCode+" and error string:"+errorString);
		}
		exposureLength = statusCommand.getExposureLength();
		hashTable.put("Exposure Length",new Integer(exposureLength));
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getStatusExposureLength:finished with length:"+
			  exposureLength+ " ms");
	}

	/**
	 * Get the exposure start time. 
	 * An instance of StatusExposureStartTimeCommand is used to send the command
	 * to the CCD C layer, using ccdCLayerHostname and ccdCLayerPortNumber. 
	 * The returned value is stored in
	 * the hashTable, under the "Exposure Start Time" and "Exposure Start Time Date" key.
	 * @exception Exception Thrown if an error occurs.
	 * @see #hashTable
	 * @see #ccdCLayerHostname
	 * @see #ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.StatusExposureStartTimeCommand
	 */
	protected void getStatusExposureStartTime() throws Exception
	{
		StatusExposureStartTimeCommand statusCommand = null;
		Date exposureStartTime = null;
		int returnCode;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getStatusExposureStartTime:started.");
		statusCommand = new StatusExposureStartTimeCommand();
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
			  "getStatusExposureStartTime:exposure start time command failed with return code "+
				   returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
			 ":getStatusExposureStartTime:exposure start time command failed with return code "+
					    returnCode+" and error string:"+errorString);
		}
		exposureStartTime = statusCommand.getTimestamp();
		hashTable.put("Exposure Start Time",new Long(exposureStartTime.getTime()));
		hashTable.put("Exposure Start Time Date",exposureStartTime);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,
			  "getStatusExposureStartTime:finished with start time:"+exposureStartTime);
	}

	/**
	 * Get intermediate level status. This is temperature information from the CCD.
	 * The overall health and well-being statii are then computed using setInstrumentStatus.
	 * @see #COMMS_INSTRUMENT_STATUS_CCD
	 * @see #COMMS_INSTRUMENT_STATUS_MECHANISM
	 * @see #getCCDTemperature
	 * @see HardwareImplementation#getSlitPosition
	 * @see HardwareImplementation#getGrismPosition
	 * @see #getMirrorPosition
	 * @see #getArcLampState
	 * @see #getWLampState
	 * @see HardwareImplementation#getRotationPosition
	 * @see #getMechanismTemperature
	 * @see #getHumidity
	 * @see #getGyroPosition
	 * @see #setInstrumentStatus
	 * @see #commsInstrumentStatus
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_OK
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_FAIL
	 */
	private void getIntermediateStatus()
	{
		int currentPosition;
		int temperatureSensorCount,humiditySensorCount;

		try
		{
			getCCDTemperature();
			commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_CCD] = GET_STATUS_DONE.
				VALUE_STATUS_OK;
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+
				      ":getIntermediateStatus:Retrieving temperature status failed.",e);
			commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_CCD] = GET_STATUS_DONE.
				VALUE_STATUS_FAIL;
		}
		try
		{
			// slit position
			currentPosition = getSlitPosition();
			hashTable.put("Slit.Position",new Integer(currentPosition));
			hashTable.put("Slit.Position.String",SlitCommand.positionToString(currentPosition));
			// grism position
			currentPosition = getGrismPosition();
			hashTable.put("Grism.Position",new Integer(currentPosition));
			hashTable.put("Grism.Position.String",GrismCommand.positionToString(currentPosition));
			// rotation position
			currentPosition = getRotationPosition();
			hashTable.put("Rotation.Position",new Integer(currentPosition));
			getMirrorPosition();
			getArcLampState();
			getWLampState();
			temperatureSensorCount = status.getPropertyInteger(
							 "sprat.mechanism.temperature.sensor.count");
			for(int i = 0; i < temperatureSensorCount; i++)
			{
				getMechanismTemperature(i);
			}
			humiditySensorCount = status.getPropertyInteger(
							"sprat.mechanism.humidity.sensor.count");
			for(int i = 0; i < humiditySensorCount; i++)
			{
				getHumidity(i);
			}
			getGyroPosition();
			commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_MECHANISM] = GET_STATUS_DONE.
				VALUE_STATUS_OK;
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+
				    ":getIntermediateStatus:Retrieving arduino mechanism status failed.",e);
			commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_MECHANISM] = GET_STATUS_DONE.
				VALUE_STATUS_FAIL;
		}
		hashTable.put("CCD.Comms.Status",commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_CCD]);
		hashTable.put("Mechanism.Comms.Status",
			      commsInstrumentStatus[COMMS_INSTRUMENT_STATUS_MECHANISM]);
	// Standard status
		setInstrumentStatus();
	}

	/**
	 * Get the current, or C layer cached, CCD temperature.
	 * An instance of StatusTemperatureGetCommand is used to send the command
	 * to the C layer, using ccdCLayerHostname and ccdCLayerPortNumber. The returned value is stored in
	 * the hashTable, under the "Temperature" key (converted to Kelvin). A timestamp is also
	 * retrieved (when the temperature was actually measured, it may be a cached value), and this
	 * is stored in the "Temperature Timestamp" key.
	 * @exception Exception Thrown if an error occurs.
	 * @see #ccdCLayerHostname
	 * @see #ccdCLayerPortNumber
	 * @see #hashTable
	 * @see #setDetectorTemperatureInstrumentStatus
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
	protected void getCCDTemperature() throws Exception
	{
		StatusTemperatureGetCommand statusCommand = null;
		int returnCode;
		String errorString = null;
		double temperature;
		Date timestamp;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getCCDTemperature:started.");
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
		hashTable.put("Temperature",new Double(temperature+Sprat.CENTIGRADE_TO_KELVIN));
		hashTable.put("Temperature Timestamp",timestamp);
		setDetectorTemperatureInstrumentStatus(temperature);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getCCDTemperature:finished with temperature:"+
			   temperature+" measured at "+timestamp);
	}

	/**
	 * Get the current mirror position.
	 * An instance of MirrorCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * The current position is stored in the GET_STATUS hashtable as an integer and a string.
	 * @see #mechanismHostname
	 * @see #mechanismPortNumber
	 * @see #hashTable
	 * @see ngat.sprat.mechanism.command.MirrorCommand
	 * @see ngat.sprat.mechanism.command.MirrorCommand#run
	 * @see ngat.sprat.mechanism.command.MirrorCommand#getRunException
	 * @see ngat.sprat.mechanism.command.MirrorCommand#getIsError
	 * @see ngat.sprat.mechanism.command.MirrorCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.MirrorCommand#getCurrentPosition
	 * @see ngat.sprat.mechanism.command.MirrorCommand#positionToString
	 */
	protected void getMirrorPosition() throws Exception
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
		hashTable.put("Mirror.Position",new Integer(command.getCurrentPosition()));
		hashTable.put("Mirror.Position.String",
			      MirrorCommand.positionToString(command.getCurrentPosition()));
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getMirrorPosition:finished with position:"+
			  MirrorCommand.positionToString(command.getCurrentPosition()));
	}

 	/**
	 * Get the current state of the arc lamp.
	 * An instance of ArcLampCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * The current state is stored in the GET_STATUS hashtable as an integer and a string.
	 * @see #mechanismHostname
	 * @see #mechanismPortNumber
	 * @see #hashTable
	 * @see ngat.sprat.mechanism.command.ArcLampCommand
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#run
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#getRunException
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#getIsError
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#getCurrentState
	 * @see ngat.sprat.mechanism.command.ArcLampCommand#stateToString
	 */
	protected void getArcLampState() throws Exception
 	{
		ArcLampCommand command = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getArcLampPosition:started.");
		command = new ArcLampCommand(mechanismHostname,mechanismPortNumber);
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":getArcLamPosition:MirrorCommand command threw exception.",
					    command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+
					    ":getArcLamPosition:MirrorCommand command returned an error:"+
					    command.getErrorString());
		}
		hashTable.put("ArcLamp.Status",new Integer(command.getCurrentState()));
		hashTable.put("ArcLamp.Status.String",
			      ArcLampCommand.stateToString(command.getCurrentState()));
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getArcLampPosition:finished with state:"+
			  ArcLampCommand.stateToString(command.getCurrentState()));
	}

 	/**
	 * Get the current state of the tungsten lamp.
	 * An instance of WLampCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * The current state is stored in the GET_STATUS hashtable as an integer and a string.
	 * @see #mechanismHostname
	 * @see #mechanismPortNumber
	 * @see #hashTable
	 * @see ngat.sprat.mechanism.command.WLampCommand
	 * @see ngat.sprat.mechanism.command.WLampCommand#run
	 * @see ngat.sprat.mechanism.command.WLampCommand#getRunException
	 * @see ngat.sprat.mechanism.command.WLampCommand#getIsError
	 * @see ngat.sprat.mechanism.command.WLampCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.WLampCommand#getCurrentState
	 * @see ngat.sprat.mechanism.command.WLampCommand#stateToString
	 */
	protected void getWLampState() throws Exception
 	{
		WLampCommand command = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getWLampPosition:started.");
		command = new WLampCommand(mechanismHostname,mechanismPortNumber);
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":getWLamPosition:MirrorCommand command threw exception.",
					    command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+
					    ":getWLamPosition:MirrorCommand command returned an error:"+
					    command.getErrorString());
		}
		hashTable.put("WLamp.Status",new Integer(command.getCurrentState()));
		hashTable.put("WLamp.Status.String",
			      WLampCommand.stateToString(command.getCurrentState()));
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getWLampPosition:finished with state:"+
			  WLampCommand.stateToString(command.getCurrentState()));
	}

	/**
	 * Get a mechanism temperature.
	 * An instance of TemperatureCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * The current temeprature is stored in the GET_STATUS hashtable as a double, in Kelvin.
	 * @param sensorNumber Which number temperature sensor to query.
	 * @see #mechanismHostname
	 * @see #mechanismPortNumber
	 * @see #hashTable
	 * @see ngat.sprat.mechanism.command.TemperatureCommand
	 * @see ngat.sprat.mechanism.command.TemperatureCommand#run
	 * @see ngat.sprat.mechanism.command.TemperatureCommand#getRunException
	 * @see ngat.sprat.mechanism.command.TemperatureCommand#getIsError
	 * @see ngat.sprat.mechanism.command.TemperatureCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.TemperatureCommand#getTemperature
	 */
 	protected void getMechanismTemperature(int sensorNumber) throws Exception
	{
		TemperatureCommand command = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,
			  "getMechanismTemperature(sensorNumber="+sensorNumber+"):started.");
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
		hashTable.put("Mechanism.Temperature."+sensorNumber,
			      new Double(command.getTemperature()+Sprat.CENTIGRADE_TO_KELVIN));
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getMechanismTemperature(sensorNumber="+
			  sensorNumber+"):finished with temperature:"+
			  (command.getTemperature()+Sprat.CENTIGRADE_TO_KELVIN)+" K.");
	}

 	/**
	 * Get a mechanism humidity measurement.
	 * An instance of HumidityCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * The current humidity is stored in the GET_STATUS hashtable as a double.
	 * @param sensorNumber Which number humidity sensor to query.
	 * @see #mechanismHostname
	 * @see #mechanismPortNumber
	 * @see #hashTable
	 * @see ngat.sprat.mechanism.command.HumidityCommand
	 * @see ngat.sprat.mechanism.command.HumidityCommand#run
	 * @see ngat.sprat.mechanism.command.HumidityCommand#getRunException
	 * @see ngat.sprat.mechanism.command.HumidityCommand#getIsError
	 * @see ngat.sprat.mechanism.command.HumidityCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.HumidityCommand#getHumidity
	 */
 	protected void getHumidity(int sensorNumber) throws Exception
	{
		HumidityCommand command = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,
			  "getHumidity(sensorNumber="+sensorNumber+"):started.");
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
		hashTable.put("Mechanism.Humidity."+sensorNumber,new Double(command.getHumidity()));
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getHumidity(sensorNumber="+
			  sensorNumber+"):finished with humidity:"+command.getHumidity()+" %.");
	}

   	/**
	 * Get the mechanism gyro position.
	 * An instance of GyroCommand is "run". If a run exception occurs this is thrown.
	 * If an error is returned this is thrown as an exception.
	 * The current gyro orientation position is stored in the GET_STATUS hashtable as a series of 
	 * doubles, each axis represented by an ADU number.
	 * @see #mechanismHostname
	 * @see #mechanismPortNumber
	 * @see #hashTable
	 * @see ngat.sprat.mechanism.command.GyroCommand
	 * @see ngat.sprat.mechanism.command.GyroCommand#run
	 * @see ngat.sprat.mechanism.command.GyroCommand#getRunException
	 * @see ngat.sprat.mechanism.command.GyroCommand#getIsError
	 * @see ngat.sprat.mechanism.command.GyroCommand#getErrorString
	 * @see ngat.sprat.mechanism.command.GyroCommand#getPositionX
	 * @see ngat.sprat.mechanism.command.GyroCommand#getPositionY
	 * @see ngat.sprat.mechanism.command.GyroCommand#getPositionZ
	 */
 	protected void getGyroPosition() throws Exception
	{
		GyroCommand command = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getGyroPosition:started.");
		command = new GyroCommand(mechanismHostname,mechanismPortNumber);
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":getGyroPosition:command threw exception.",
					    command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+
					    ":getGyroPosition:command returned an error:"+
					    command.getErrorString());
		}
		hashTable.put("Mechanism.Gyro.X",new Double(command.getPositionX()));
		hashTable.put("Mechanism.Gyro.Y",new Double(command.getPositionY()));
		hashTable.put("Mechanism.Gyro.Z",new Double(command.getPositionZ()));
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getGyroPosition:finished with position:( X = "+
			  command.getPositionX()+", Y = "+command.getPositionY()+
			  ", Z = "+command.getPositionZ()+" ).");
	}


	/**
	 * Set the standard entry for detector temperature in the hashtable based upon the 
	 * current temperature.
	 * Reads the folowing config:
	 * <ul>
	 * <li>sprat.get_status.detector.temperature.warm.warn
	 * <li>sprat.get_status.detector.temperature.warm.fail
	 * <li>sprat.get_status.detector.temperature.cold.warn
	 * <li>sprat.get_status.detector.temperature.cold.fail
	 * </ul>
	 * @param currentTemperature The current temperature in degrees C.
	 * @exception NumberFormatException Thrown if the config is not a valid double.
	 * @see #hashTable
	 * @see #status
	 * @see #detectorTemperatureInstrumentStatus
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_OK
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_WARN
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_FAIL
	 */
	protected void setDetectorTemperatureInstrumentStatus(double currentTemperature) 
		throws NumberFormatException
	{
		double warmWarnTemperature,warmFailTemperature,coldWarnTemperature,coldFailTemperature;

		// get config for warn and fail temperatures
		warmWarnTemperature = status.getPropertyDouble("sprat.get_status.detector.temperature.warm.warn");
		warmFailTemperature = status.getPropertyDouble("sprat.get_status.detector.temperature.warm.fail");
		coldWarnTemperature = status.getPropertyDouble("sprat.get_status.detector.temperature.cold.warn");
		coldFailTemperature = status.getPropertyDouble("sprat.get_status.detector.temperature.cold.fail");
		// set status
		if(currentTemperature > warmFailTemperature)
			detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_FAIL;
		else if(currentTemperature > warmWarnTemperature)
			detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_WARN;
		else if(currentTemperature < coldFailTemperature)
			detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_FAIL;
		else if(currentTemperature < coldWarnTemperature)
			detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_WARN;
		else
			detectorTemperatureInstrumentStatus = GET_STATUS_DONE.VALUE_STATUS_OK;
		// set hashtable entry
		hashTable.put(GET_STATUS_DONE.KEYWORD_DETECTOR_TEMPERATURE_INSTRUMENT_STATUS,
			      detectorTemperatureInstrumentStatus);
	}

	/**
	 * Set the overall instrument status keyword in the hashtable. 
	 * This is derived from sub-system keyword values,
	 * the detector temperature and comms status data. HashTable entry KEYWORD_INSTRUMENT_STATUS)
	 * should be set to the worst of OK/WARN/FAIL. If sub-systems are UNKNOWN, OK is returned.
	 * @see #COMMS_INSTRUMENT_STATUS_COUNT
	 * @see #hashTable
	 * @see #status
	 * @see #detectorTemperatureInstrumentStatus
	 * @see #commsInstrumentStatus
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#KEYWORD_INSTRUMENT_STATUS
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_OK
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_WARN
	 * @see ngat.message.ISS_INST.GET_STATUS_DONE#VALUE_STATUS_FAIL
	 */
	protected void setInstrumentStatus()
	{
		String instrumentStatus;

		// default to OK
		instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_OK;
		// if a sub-status is in warning, overall status is in warning
		if(detectorTemperatureInstrumentStatus.equals(GET_STATUS_DONE.VALUE_STATUS_WARN))
			instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_WARN;
		for(int i = 0; i < COMMS_INSTRUMENT_STATUS_COUNT; i++)
		{
			if(commsInstrumentStatus[i].equals(GET_STATUS_DONE.VALUE_STATUS_WARN))
				instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_WARN;
		}
		// if a sub-status is in fail, overall status is in fail. This overrides a previous warn
	        if(detectorTemperatureInstrumentStatus.equals(GET_STATUS_DONE.VALUE_STATUS_FAIL))
			instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_FAIL;
		for(int i = 0; i < COMMS_INSTRUMENT_STATUS_COUNT; i++)
		{
			if(commsInstrumentStatus[i].equals(GET_STATUS_DONE.VALUE_STATUS_FAIL))
				instrumentStatus = GET_STATUS_DONE.VALUE_STATUS_FAIL;
		}
		// set standard status in hashtable
		hashTable.put(GET_STATUS_DONE.KEYWORD_INSTRUMENT_STATUS,instrumentStatus);
	}

	/**
	 * Method to get misc status, when level FULL has been selected.
	 * The following data is put into the hashTable:
	 * <ul>
	 * <li><b>Log Level</b> The current logging level Sprat is using.
	 * <li><b>Disk Usage</b> The results of running a &quot;df -k&quot;, to get the disk usage.
	 * <li><b>Process List</b> The results of running a 
	 *       &quot;ps -e -o pid,pcpu,vsz,ruser,stime,time,args&quot;, 
	 * 	to get the processes running on this machine.
	 * <li><b>Uptime</b> The results of running a &quot;uptime&quot;, 
	 * 	to get system load and time since last reboot.
	 * <li><b>Total Memory, Free Memory</b> The total and free memory in the Java virtual machine.
	 * <li><b>java.version, java.vendor, java.home, java.vm.version, java.vm.vendor, java.class.path</b>
	 * 	Java virtual machine version, classpath and type.
	 * <li><b>os.name, os.arch, os.version</b> The operating system type/version.
	 * <li><b>user.name, user.home, user.dir</b> Data about the user the process is running as.
	 * <li><b>thread.list</b> A list of threads the THOR process is running.
	 * </ul>
	 * @see #serverConnectionThread
	 * @see #hashTable
	 * @see ExecuteCommand#run
	 * @see SpratStatus#getLogLevel
	 */
	private void getFullStatus()
	{
		ExecuteCommand executeCommand = null;
		Runtime runtime = null;
		StringBuffer sb = null;
		Thread threadList[] = null;
		int threadCount;

		// log level
		hashTable.put("Log Level",new Integer(status.getLogLevel()));
		// execute 'df -k' on instrument computer
		executeCommand = new ExecuteCommand("df -k");
		executeCommand.run();
		if(executeCommand.getException() == null)
			hashTable.put("Disk Usage",new String(executeCommand.getOutputString()));
		else
			hashTable.put("Disk Usage",new String(executeCommand.getException().toString()));
		// execute "ps -e -o pid,pcpu,vsz,ruser,stime,time,args" on instrument computer
		executeCommand = new ExecuteCommand("ps -e -o pid,pcpu,vsz,ruser,stime,time,args");
		executeCommand.run();
		if(executeCommand.getException() == null)
			hashTable.put("Process List",new String(executeCommand.getOutputString()));
		else
			hashTable.put("Process List",new String(executeCommand.getException().toString()));
		// execute "uptime" on instrument computer
		executeCommand = new ExecuteCommand("uptime");
		executeCommand.run();
		if(executeCommand.getException() == null)
			hashTable.put("Uptime",new String(executeCommand.getOutputString()));
		else
			hashTable.put("Uptime",new String(executeCommand.getException().toString()));
		// get vm memory situation
		runtime = Runtime.getRuntime();
		hashTable.put("Free Memory",new Long(runtime.freeMemory()));
		hashTable.put("Total Memory",new Long(runtime.totalMemory()));
		// get some java vm information
		hashTable.put("java.version",new String(System.getProperty("java.version")));
		hashTable.put("java.vendor",new String(System.getProperty("java.vendor")));
		hashTable.put("java.home",new String(System.getProperty("java.home")));
		hashTable.put("java.vm.version",new String(System.getProperty("java.vm.version")));
		hashTable.put("java.vm.vendor",new String(System.getProperty("java.vm.vendor")));
		hashTable.put("java.class.path",new String(System.getProperty("java.class.path")));
		hashTable.put("os.name",new String(System.getProperty("os.name")));
		hashTable.put("os.arch",new String(System.getProperty("os.arch")));
		hashTable.put("os.version",new String(System.getProperty("os.version")));
		hashTable.put("user.name",new String(System.getProperty("user.name")));
		hashTable.put("user.home",new String(System.getProperty("user.home")));
		hashTable.put("user.dir",new String(System.getProperty("user.dir")));
	}
}

