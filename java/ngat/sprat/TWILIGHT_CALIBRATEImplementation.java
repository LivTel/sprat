// TWILIGHT_CALIBRATEImplementation2.java
// $HeadURL$
package ngat.sprat;

import java.io.*;
import java.lang.*;
import java.util.*;

import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.phase2.*;
import ngat.sprat.ccd.command.*;
import ngat.util.*;
import ngat.util.logging.*;

/**
 * This class provides the implementation of a TWILIGHT_CALIBRATE command sent to a server using the
 * Java Message System. It performs a series of SKYFLAT frames from a configurable list,
 * taking into account frames done in previous invocations of this command (it saves it's state).
 * The exposure length is dynamically adjusted as the sky gets darker or brighter. TWILIGHT_CALIBRATE commands
 * should be sent just after sunset and just before sunrise.
 * @author Chris Mottram
 * @version $Revision$
 */
public class TWILIGHT_CALIBRATEImplementation extends CALIBRATEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Initial part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_STRING = "sprat.twilight_calibrate.";
	/**
	 * Middle part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_CALIBRATION_STRING = "calibration.";
	/**
	 * Middle part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_SUNSET_STRING = "sunset.";
	/**
	 * Middle part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_SUNRISE_STRING = "sunrise.";
	/**
	 * Final part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_BINX_STRING = ".bin.x";
	/**
	 * Final part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_BINY_STRING = ".bin.y";
	/**
	 * Final part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_SLIT_STRING = ".slit";
	/**
	 * Final part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_GRISM_STRING = ".grism";
	/**
	 * Final part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_GRISM_ROTATION_STRING = ".grism.rotation";
	/**
	 * Final part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_FREQUENCY_STRING = ".frequency";
	/**
	 * Middle part of a key string, used for saving and restoring the stored calibration state.
	 */
	protected final static String LIST_KEY_LAST_TIME_STRING = "last_time.";
	/**
	 * Middle part of a key string, used to load a list of telescope RA/DEC offsets from a Java property file.
	 */
	protected final static String LIST_KEY_OFFSET_STRING = "offset.";
	/**
	 * Last part of a key string, used to load a list of telescope RA/DEC offsets from a Java property file.
	 */
	protected final static String LIST_KEY_RA_STRING = ".ra";
	/**
	 * Last part of a key string, used to load a list of telescope RA/DEC offsets from a Java property file.
	 */
	protected final static String LIST_KEY_DEC_STRING = ".dec";
	/**
	 * Constant used for time of night determination.
	 * @see #timeOfNight
	 */
	protected final static int TIME_OF_NIGHT_UNKNOWN = 0;
	/**
	 * Constant used for time of night determination.
	 * @see #timeOfNight
	 */
	protected final static int TIME_OF_NIGHT_SUNSET	= 1;
	/**
	 * Constant used for time of night determination.
	 * @see #timeOfNight
	 */
	protected final static int TIME_OF_NIGHT_SUNRISE = 2;
	/**
	 * A possible state of a frame taken by this command. 
	 * The frame did not have enough counts to be useful, i.e. the mean counts were less than minMeanCounts.
	 * @see #minMeanCounts
	 */
	protected final static int FRAME_STATE_UNDEREXPOSED 	= 0;
	/**
	 * A possible state of a frame taken by this command. 
	 * The mean counts for the frame were sensible, i.e. the mean counts were more than minMeanCounts and less
	 * than maxMeanCounts.
	 * @see #minMeanCounts
	 * @see #maxMeanCounts
	 */
	protected final static int FRAME_STATE_OK 		= 1;
	/**
	 * A possible state of a frame taken by this command. 
	 * The frame had too many counts to be useful, i.e. the mean counts were higher than maxMeanCounts.
	 * @see #maxMeanCounts
	 */
	protected final static int FRAME_STATE_OVEREXPOSED 	= 2;
	/**
	 * The number of possible frame states.
	 * @see #FRAME_STATE_UNDEREXPOSED
	 * @see #FRAME_STATE_OK
	 * @see #FRAME_STATE_OVEREXPOSED
	 */
	protected final static int FRAME_STATE_COUNT 	= 3;
	/**
	 * Description strings for the frame states, indexed by the frame state enumeration numbers.
	 * @see #FRAME_STATE_UNDEREXPOSED
	 * @see #FRAME_STATE_OK
	 * @see #FRAME_STATE_OVEREXPOSED
	 * @see #FRAME_STATE_COUNT
	 */
	protected final static String FRAME_STATE_NAME_LIST[] = {"underexposed","ok","overexposed"};
	/**
	 * The time, in milliseconds since the epoch, that the implementation of this command was started.
	 */
	private long implementationStartTime = 0L;
	/**
	 * The saved state of calibrations done over time by invocations of this command.
	 * @see TWILIGHT_CALIBRATESavedState
	 */
	private TWILIGHT_CALIBRATESavedState twilightCalibrateState = null;
	/**
	 * The filename holding the saved state data.
	 */
	private String stateFilename = null;
	/**
	 * The list of calibrations to select from.
	 * Each item in the list is an instance of TWILIGHT_CALIBRATECalibration.
	 * @see TWILIGHT_CALIBRATECalibration
	 */
	protected List calibrationList = null;
	/**
	 * The list of telescope offsets to do each calibration for.
	 * Each item in the list is an instance of TWILIGHT_CALIBRATEOffset.
	 * @see TWILIGHT_CALIBRATEOffset
	 */
	protected List offsetList = null;
	/**
	 * The frame overhead for a full frame, in milliseconds. This takes into account readout time,
	 * real time data reduction, communication overheads and the like.
	 */
	private int frameOverhead = 0;
	/**
	 * The minimum allowable exposure time for a frame, in milliseconds.
	 */
	private int minExposureLength = 0;
	/**
	 * The maximum allowable exposure time for a frame, in milliseconds.
	 */
	private int maxExposureLength = 0;
	/**
	 * The exposure time for the current frame, in milliseconds.
	 */
	private int exposureLength = 0;
	/**
	 * The exposure time for the last frame exposed, in milliseconds.
	 */
	private int lastExposureLength = 0;
	/**
	 * What time of night we are doing the calibration, is it sunset or sunrise.
	 * @see #TIME_OF_NIGHT_UNKNOWN
	 * @see #TIME_OF_NIGHT_SUNSET
	 * @see #TIME_OF_NIGHT_SUNRISE
	 */
	private int timeOfNight = TIME_OF_NIGHT_UNKNOWN;
	/**
	 * Filename used to save FITS frames to, until they are determined to contain valid data
	 * (the counts in them are within limits).
	 */
	private String temporaryFITSFilename = null;
	/**
	 * The last mean counts measured by the DpRt on the last frame exposed.
	 * Probably associated with the exposure length lastExposureLength most of the time.
	 */
	private float meanCounts;
	/**
	 * The minimum mean counts. A &quot;good&quot; frame will have a mean counts greater than this number.
	 */
	private int minMeanCounts = 0;
	/**
	 * The best mean counts. The &quot;ideal&quot; frame will have a mean counts of this number.
	 */
	private int bestMeanCounts = 0;
	/**
	 * The maximum mean counts. A &quot;good&quot; frame will have a mean counts less than this number.
	 */
	private int maxMeanCounts = 0;
	/**
	 * The last X bin factor used for calculating exposure lengths.
	 */
	private int lastBinX = 1;
	/**
	 * The last Y bin factor used for calculating exposure lengths.
	 */
	private int lastBinY = 1;
	/**
	 * Loop terminator for the calibration list loop.
	 */
	private boolean doneCalibration = false;
	/**
	 * Loop terminator for the offset list loop.
	 */
	private boolean doneOffset = false;
	/**
	 * The number of calibrations completed that have a frame state of good,
	 * for the currently executing calibration.
	 * A calibration is successfully completed if the calibrationFrameCount is
	 * equal to the offsetList size at the end of the offset list loop.
	 */
	private int calibrationFrameCount = 0;

	/**
	 * Constructor.
	 */
	public TWILIGHT_CALIBRATEImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.TWILIGHT_CALIBRATE&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.TWILIGHT_CALIBRATE";
	}

	/**
	 * This method is the first to be called in this class. 
	 * <ul>
	 * <li>It calls the superclass's init method.
	 * </ul>
	 * @param command The command to be implemented.
	 */
	public void init(COMMAND command)
	{
		super.init(command);
	}

	/**
	 * This method gets the TWILIGHT_CALIBRATE command's acknowledge time. 
	 * This returns the server connection threads default acknowledge time.
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
	 * This method implements the TWILIGHT_CALIBRATE command.
	 * <ul>
	 * <li>The implementation start time is saved.
	 * <li><b>setTimeOfNight</b> is called to set the time of night flag.
	 * <li><b>loadProperties</b> is called to get configuration data from the properties.
	 * <li><b>addSavedStateToCalibration</b> is called, which finds the correct last time for each
	 * 	calibration in the list and sets the relevant field.
	 * <li>The FITS headers are cleared.
	 * <li>The fold mirror is moved to the correct location using <b>moveFold</b>.
	 * <li>For each calibration, we do the following:
	 *      <ul>
	 *      <li><b>doCalibration</b> is called.
	 *      </ul>
	 * <li>sendBasicAck is called, to stop the client timing out whilst creating the master flat.
	 * <li>The makeMasterFlat method is called, to create master flat fields from the data just taken.
	 * </ul>
	 * Note this method assumes the loading and initialisation before the main loop takes less than the
	 * default acknowledge time, as no ACK's are sent to the client until we are ready to do the first
	 * sequence of calibration frames.
	 * @param command The command to be implemented.
	 * @return An instance of TWILIGHT_CALIBRATE_DONE is returned, with it's fields indicating
	 * 	the result of the command implementation.
	 * @see #implementationStartTime
	 * @see #exposureLength
	 * @see #setTimeOfNight
	 * @see #addSavedStateToCalibration
	 * @see FITSImplementation#moveFold
	 * @see FITSImplementation#clearFitsHeaders
	 * @see #doCalibration
	 * @see #frameOverhead
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		TWILIGHT_CALIBRATE twilightCalibrateCommand = (TWILIGHT_CALIBRATE)command;
		TWILIGHT_CALIBRATE_DONE twilightCalibrateDone = new TWILIGHT_CALIBRATE_DONE(command.getId());
		TWILIGHT_CALIBRATECalibration calibration = null;
		int calibrationListIndex = 0;
		String directoryString = null;
		int makeFlatAckTime;

		twilightCalibrateDone.setMeanCounts(0.0f);
		twilightCalibrateDone.setPeakCounts(0.0f);
	// initialise
		implementationStartTime = System.currentTimeMillis();
		setTimeOfNight();
		if(loadProperties(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return twilightCalibrateDone;
	// match saved state to calibration list (put last time into calibration list)
		if(addSavedStateToCalibration(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return twilightCalibrateDone;
	// move the fold mirror to the correct location
		if(moveFold(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return twilightCalibrateDone;
		if(testAbort(twilightCalibrateCommand,twilightCalibrateDone) == true)
			return twilightCalibrateDone;
	// initialise exposureLength
		if(timeOfNight == TIME_OF_NIGHT_SUNRISE)
			exposureLength = maxExposureLength;
		else if(timeOfNight == TIME_OF_NIGHT_SUNSET)
			exposureLength = minExposureLength;
		else // this should never happen
			exposureLength = minExposureLength;
		// set lastBin to contents of first calibration so initial exposure length
		// remains the same as the calculated above.
		lastExposureLength = exposureLength;
		lastBinX = 1;
		lastBinY = 1;
		if(calibrationList.size() > 0)
		{
			calibration = (TWILIGHT_CALIBRATECalibration)(calibrationList.get(0));
			lastBinX = calibration.getBinX();
			lastBinY = calibration.getBinY();
		}
		// initialise meanCounts
		meanCounts = bestMeanCounts;
		// initialise loop variables
		calibrationListIndex = 0;
		doneCalibration = false;
	// main loop, do calibrations until we run out of time.
		while((doneCalibration == false) && (calibrationListIndex < calibrationList.size()))
		{
		// get calibration
			calibration = (TWILIGHT_CALIBRATECalibration)(calibrationList.get(calibrationListIndex));
		// do calibration
			if(doCalibration(twilightCalibrateCommand,twilightCalibrateDone,calibration) == false)
				return twilightCalibrateDone;
			calibrationListIndex++;
		}// end for on calibration list
	// send an ack before make master processing, so the client doesn't time out.
		makeFlatAckTime = status.getPropertyInteger("sprat.twilight_calibrate.acknowledge_time.make_flat");
		if(sendBasicAck(twilightCalibrateCommand,twilightCalibrateDone,makeFlatAckTime) == false)
			return twilightCalibrateDone;
	// Call pipeline to create master flat.
		directoryString = status.getProperty("sprat.file.fits.path");
		if(directoryString.endsWith(System.getProperty("file.separator")) == false)
			directoryString = directoryString.concat(System.getProperty("file.separator"));
		if(makeMasterFlat(twilightCalibrateCommand,twilightCalibrateDone,directoryString) == false)
			return twilightCalibrateDone;
	// return done
		twilightCalibrateDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
		twilightCalibrateDone.setErrorString("");
		twilightCalibrateDone.setSuccessful(true);
		return twilightCalibrateDone;
	}

	/**
	 * Method to set time of night flag.
	 * @see #TIME_OF_NIGHT_UNKNOWN
	 * @see #TIME_OF_NIGHT_SUNRISE
	 * @see #TIME_OF_NIGHT_SUNSET
	 * @see #timeOfNight
	 */
	protected void setTimeOfNight()
	{
		Calendar calendar = null;
		int hour;

		timeOfNight = TIME_OF_NIGHT_UNKNOWN;
	// get Instance initialises the calendar to the current time.
		calendar = Calendar.getInstance();
	// the hour returned using HOUR of DAY is between 0 and 23
		hour = calendar.get(Calendar.HOUR_OF_DAY);
		if(hour < 12)
			timeOfNight = TIME_OF_NIGHT_SUNRISE;
		else
			timeOfNight = TIME_OF_NIGHT_SUNSET;
	}

	/**
	 * Method to load twilight calibration configuration data from the properties file.
	 * The following configuration properties are retrieved:
	 * <ul>
	 * <li>frame overhead
	 * <li>minimum exposure length
	 * <li>maximum exposure length
	 * <li>temporary FITS filename
	 * <li>saved state filename
	 * <li>minimum mean counts (for each binning factor)
	 * <li>best mean counts (for each binning factor)
	 * <li>maximum mean counts (for each binning factor)
	 * </ul>
	 * The following methods are then called to load more calibration data:
	 * <ul>
	 * <li><b>loadCalibrationList</b>
	 * <li><b>loadOffsetList</b>
	 * <li><b>loadState</b>
	 * </ul>
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @return The method returns true if the method succeeds, false if an error occurs.
	 * 	If false is returned the error data in twilightCalibrateDone is filled in.
	 * @see #loadCalibrationList
	 * @see #loadState
	 * @see #loadOffsetList
	 * @see #frameOverhead
	 * @see #minExposureLength
	 * @see #maxExposureLength
	 * @see #temporaryFITSFilename
	 * @see #stateFilename
	 * @see #minMeanCounts
	 * @see #bestMeanCounts
	 * @see #maxMeanCounts
	 * @see #timeOfNight
	 * @see #LIST_KEY_SUNSET_STRING
	 * @see #LIST_KEY_SUNRISE_STRING
	 * @see #LIST_KEY_STRING
	 */
	protected boolean loadProperties(TWILIGHT_CALIBRATE twilightCalibrateCommand,
		TWILIGHT_CALIBRATE_DONE twilightCalibrateDone)
	{
		String timeOfNightString = null;
		String propertyName = null;

		if(timeOfNight == TIME_OF_NIGHT_SUNSET)
			timeOfNightString = LIST_KEY_SUNSET_STRING;
		else
			timeOfNightString = LIST_KEY_SUNRISE_STRING;
		try
		{
		// frame overhead
			propertyName = LIST_KEY_STRING+"frame_overhead";
			frameOverhead = status.getPropertyInteger(propertyName);
		// minimum exposure length
			propertyName = LIST_KEY_STRING+"min_exposure_time";
			minExposureLength = status.getPropertyInteger(propertyName);
		// maximum exposure length
			propertyName = LIST_KEY_STRING+"max_exposure_time";
			maxExposureLength = status.getPropertyInteger(propertyName);
		// temporary FITS filename
			propertyName = LIST_KEY_STRING+"file.tmp";
			temporaryFITSFilename = status.getProperty(propertyName);
		// saved state filename
			propertyName = LIST_KEY_STRING+"state_filename";
			stateFilename = status.getProperty(propertyName);
			// minimum mean counts
			propertyName = LIST_KEY_STRING+"mean_counts.min";
			minMeanCounts = status.getPropertyInteger(propertyName);
			// best mean counts
			propertyName = LIST_KEY_STRING+"mean_counts.best";
			bestMeanCounts = status.getPropertyInteger(propertyName);
			// maximum mean counts
			propertyName = LIST_KEY_STRING+"mean_counts.max";
			maxMeanCounts = status.getPropertyInteger(propertyName);
		}
		catch (Exception e)
		{
			String errorString = new String(twilightCalibrateCommand.getId()+
				":loadProperties:Failed to get property:"+propertyName);
			sprat.error(this.getClass().getName()+":"+errorString,e);
			twilightCalibrateDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2300);
			twilightCalibrateDone.setErrorString(errorString);
			twilightCalibrateDone.setSuccessful(false);
			return false;
		}
		if(loadCalibrationList(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return false;
		if(loadOffsetList(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return false;
		if(loadState(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return false;
		return true;
	}

	/**
	 * Method to load a list of calibrations to do. The list used depends on whether timeOfNight is set to
	 * sunrise or sunset.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @return The method returns true if it succeeds, false if it fails. If false is returned the error
	 * 	data in twilightCalibrateDone is filled in.
	 * @see #calibrationList
	 * @see #LIST_KEY_STRING
	 * @see #LIST_KEY_CALIBRATION_STRING
	 * @see #LIST_KEY_BINX_STRING
	 * @see #LIST_KEY_BINY_STRING
	 * @see #LIST_KEY_SLIT_STRING
	 * @see #LIST_KEY_GRISM_STRING
	 * @see #LIST_KEY_GRISM_ROTATION_STRING
	 * @see #LIST_KEY_FREQUENCY_STRING
	 * @see #LIST_KEY_SUNSET_STRING
	 * @see #LIST_KEY_SUNRISE_STRING
	 * @see #timeOfNight
	 * @see ngat.phase2.SpratConfig#POSITION_IN
	 * @see ngat.phase2.SpratConfig#POSITION_OUT
	 * @see ngat.phase2.SpratConfig#parsePosition
	 * @see ngat.phase2.SpratConfig#positionToString
	 */
	protected boolean loadCalibrationList(TWILIGHT_CALIBRATE twilightCalibrateCommand,
					      TWILIGHT_CALIBRATE_DONE twilightCalibrateDone)
	{
		TWILIGHT_CALIBRATECalibration calibration = null;
		String timeOfNightString = null;
		String slitPosition,grismPosition,grismRotation;
		int index,binx,biny;
		long frequency;
		boolean done;

		index = 0;
		done = false;
		calibrationList = new Vector();
		if(timeOfNight == TIME_OF_NIGHT_SUNSET)
			timeOfNightString = LIST_KEY_SUNSET_STRING;
		else
			timeOfNightString = LIST_KEY_SUNRISE_STRING;
		while(done == false)
		{
			if(status.propertyContainsKey(LIST_KEY_STRING+LIST_KEY_CALIBRATION_STRING+
						      timeOfNightString+index+LIST_KEY_FREQUENCY_STRING))
			{
			// create calibration instance
				calibration = new TWILIGHT_CALIBRATECalibration();
			// get parameters from properties
				try
				{
					frequency = status.getPropertyLong(LIST_KEY_STRING+LIST_KEY_CALIBRATION_STRING+
							timeOfNightString+index+LIST_KEY_FREQUENCY_STRING);
					binx = status.getPropertyInteger(LIST_KEY_STRING+LIST_KEY_CALIBRATION_STRING+
							timeOfNightString+index+LIST_KEY_BINX_STRING);
					biny = status.getPropertyInteger(LIST_KEY_STRING+LIST_KEY_CALIBRATION_STRING+
							timeOfNightString+index+LIST_KEY_BINY_STRING);
				        slitPosition = SpratConfig.parsePosition(status.getProperty(
						       LIST_KEY_STRING+LIST_KEY_CALIBRATION_STRING+
						       timeOfNightString+index+LIST_KEY_SLIT_STRING));
				        grismPosition = SpratConfig.parsePosition(status.getProperty(
							LIST_KEY_STRING+LIST_KEY_CALIBRATION_STRING+
							timeOfNightString+index+LIST_KEY_GRISM_STRING));
				        grismRotation = status.getPropertyInteger(LIST_KEY_STRING+
							      LIST_KEY_CALIBRATION_STRING+
							      timeOfNightString+index+LIST_KEY_GRISM_ROTATION_STRING);
				}
				catch(Exception e)
				{
					String errorString = new String(twilightCalibrateCommand.getId()+
						":loadCalibrationList:Failed at index "+index+".");
					sprat.error(this.getClass().getName()+":"+errorString,e);
					twilightCalibrateDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2301);
					twilightCalibrateDone.setErrorString(errorString);
					twilightCalibrateDone.setSuccessful(false);
					return false;
				}
			// set calibration data
				try
				{
					calibration.setBinX(binx);
					calibration.setBinY(biny);
					calibration.setSlit(slitPosition);
					calibration.setGrism(grismPosition);
					calibration.setGrismRotation(grismRotation);
					calibration.setFrequency(frequency);
				}
				catch(Exception e)
				{
					String errorString = new String(twilightCalibrateCommand.getId()+
						":loadCalibrationList:Failed to set calibration data at index "+index+
						":binx:"+binx+":biny:"+biny+":slit:"+slitPosition+
						":grism:"+grismPosition+":grism rotation:"+grismRotation+
									":frequency:"+frequency+".");
					sprat.error(this.getClass().getName()+":"+errorString,e);
					twilightCalibrateDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2302);
					twilightCalibrateDone.setErrorString(errorString);
					twilightCalibrateDone.setSuccessful(false);
					return false;
				}
			// add calibration instance to list
				calibrationList.add(calibration);
			// log
				sprat.log(Logging.VERBOSITY_VERBOSE,
					  "Command:"+twilightCalibrateCommand.getClass().getName()+
					  ":Loaded calibration:"+index+
					  ":binx:"+calibration.getBinX()+
					  ":biny:"+calibration.getBinY()+
					  ":slit:"+SpratConfig.positionToString(calibration.getSlit())+
					  ":grism:"+SpratConfig.positionToString(calibration.getGrism())+
					  ":grism rotation:"+calibration.getGrismRotation()+
					  ":frequency:"+calibration.getFrequency()+".");
			}
			else
				done = true;
			index++;
		}
		return true;
	}

	/**
	 * Method to initialse twilightCalibrateState.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @return The method returns true if it succeeds, false if it fails. If false is returned the error
	 * 	data in twilightCalibrateDone is filled in.
	 * @see #twilightCalibrateState
	 * @see #stateFilename
	 */
	protected boolean loadState(TWILIGHT_CALIBRATE twilightCalibrateCommand,
		TWILIGHT_CALIBRATE_DONE twilightCalibrateDone)
	{
	// initialise and load twilightCalibrateState instance
		twilightCalibrateState = new TWILIGHT_CALIBRATESavedState();
		try
		{
			twilightCalibrateState.load(stateFilename);
		}
		catch (Exception e)
		{
			String errorString = new String(twilightCalibrateCommand.getId()+
				":loadState:Failed to load state filename:"+stateFilename);
			sprat.error(this.getClass().getName()+":"+errorString,e);
			twilightCalibrateDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2304);
			twilightCalibrateDone.setErrorString(errorString);
			twilightCalibrateDone.setSuccessful(false);
			return false;
		}
		return true;
	}

	/**
	 * Method to load a list of telescope RA/DEC offsets. These are used to offset the telescope
	 * between frames of the same calibration.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @return The method returns true if it succeeds, false if it fails. If false is returned the error
	 * 	data in twilightCalibrateDone is filled in.
	 * @see #offsetList
	 * @see #LIST_KEY_OFFSET_STRING
	 * @see #LIST_KEY_RA_STRING
	 * @see #LIST_KEY_DEC_STRING
	 */
	protected boolean loadOffsetList(TWILIGHT_CALIBRATE twilightCalibrateCommand,
					 TWILIGHT_CALIBRATE_DONE twilightCalibrateDone)
	{

		TWILIGHT_CALIBRATEOffset offset = null;
		String testString = null;
		int index;
		double raOffset,decOffset;
		boolean done;

		index = 0;
		done = false;
		offsetList = new Vector();
		while(done == false)
		{
			testString = status.getProperty(LIST_KEY_STRING+LIST_KEY_OFFSET_STRING+
							index+LIST_KEY_RA_STRING);
			if((testString != null))
			{
			// create offset
				offset = new TWILIGHT_CALIBRATEOffset();
			// get parameters from properties
				try
				{
					raOffset = status.getPropertyDouble(LIST_KEY_STRING+LIST_KEY_OFFSET_STRING+
							index+LIST_KEY_RA_STRING);
					decOffset = status.getPropertyDouble(LIST_KEY_STRING+LIST_KEY_OFFSET_STRING+
							index+LIST_KEY_DEC_STRING);
				}
				catch(Exception e)
				{
					String errorString = new String(twilightCalibrateCommand.getId()+
						":loadOffsetList:Failed at index "+index+".");
					sprat.error(this.getClass().getName()+":"+errorString,e);
					twilightCalibrateDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2305);
					twilightCalibrateDone.setErrorString(errorString);
					twilightCalibrateDone.setSuccessful(false);
					return false;
				}
			// set offset data
				try
				{
					offset.setRAOffset((float)raOffset);
					offset.setDECOffset((float)decOffset);
				}
				catch(Exception e)
				{
					String errorString = new String(twilightCalibrateCommand.getId()+
						":loadOffsetList:Failed to set data at index "+index+
						":RA offset:"+raOffset+":DEC offset:"+decOffset+".");
					sprat.error(this.getClass().getName()+":"+errorString,e);
					twilightCalibrateDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2306);
					twilightCalibrateDone.setErrorString(errorString);
					twilightCalibrateDone.setSuccessful(false);
					return false;
				}
			// add offset instance to list
				offsetList.add(offset);
			// log
				sprat.log(Logging.VERBOSITY_VERBOSE,
					  "Command:"+twilightCalibrateCommand.getClass().getName()+
					  ":Loaded offset "+index+
					  ":RA Offset:"+offset.getRAOffset()+
					  ":DEC Offset:"+offset.getDECOffset()+".");
			}
			else
				done = true;
			index++;
		}
		return true;
	}

	/**
	 * This method matches the saved state to the calibration list to set the last time
	 * each calibration was completed.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @return The method returns true if it succeeds, false if it fails. Currently always returns true.
	 * @see #calibrationList
	 * @see #twilightCalibrateState
	 * @see ngat.phase2.SpratConfig#positionToString
	 */
	protected boolean addSavedStateToCalibration(TWILIGHT_CALIBRATE twilightCalibrateCommand,
		TWILIGHT_CALIBRATE_DONE twilightCalibrateDone)
	{
		TWILIGHT_CALIBRATECalibration calibration = null;
		int binx,biny,slitPosition,grismPosition,grismRotation;
		long lastTime;

		for(int i = 0; i< calibrationList.size(); i++)
		{
			calibration = (TWILIGHT_CALIBRATECalibration)(calibrationList.get(i));
			binx = calibration.getBinX();
			biny = calibration.getBinY();
			slitPosition = calibration.getSlitPosition();
			grismPosition = calibration.getGrismPosition();
			grismRotation = calibration.getGrismRotation();
			lastTime = twilightCalibrateState.getLastTime(binx,biny,slitPosition,grismPosition,grismRotation);
			calibration.setLastTime(lastTime);
			sprat.log(Logging.VERBOSITY_VERBOSE,
				  "Command:"+twilightCalibrateCommand.getClass().getName()+":Calibration:"+
				  "binx:"+calibration.getBinX()+
				  "biny:"+calibration.getBinY()+
				  ":slit:"+SpratConfig.positionToString(calibration.getSlitPosition())+
				  ":grism:"+SpratConfig.positionToString(calibration.getGrismPosition())+
				  ":grism rotation:"+calibration.getGrismRotation()+
				  ":frequency:"+calibration.getFrequency()+
				  " now has last time set to:"+lastTime+".");
		}
		return true;
	}

	//diddly
	/**
	 * This method does the specified calibration.
	 * <ul>
	 * <li>The relevant data is retrieved from the calibration parameter.
	 * <li>If we did this calibration more recently than frequency, log and return.
	 * <li>The optimal exposure length is recalculated to take account of differences from the last binning
	 *     to the new binning.
	 * <li>We set the exposure length to be a range bound version of optimal exposure length, between
	 *     the minimum and maximum exposure length.
	 * <li>We calculate the predicted mean counts, by taking the last mean counts and adjusting by the ratios
	 *     between the old and new binning squared (as binning 2 allows
	 *     four times the flux to fall on a pixel as binning 1), and the old and new exposure length.
	 * <li>Check whether we expect the predicted mean counts to be too small at sunset
	 *     (it is too dark for this bin combo). If so, try the next calibration.
	 * <li>Check whether we expect the predicted mean counts to be too big at sunrise
	 *     (it is too light for this bin combo). If so, try the next calibration.
	 * <li>We update the  last binning factor.
	 * <li><b>sendBasicAck</b> is called to stop the client timing out before the config is completed.
	 * <li><b>doConfig</b> is called for the relevant binning factor/mechanism config set to be setup.
	 * <li><b>sendBasicAck</b> is called to stop the client timing out before the first frame is completed.
	 * <li><b>doOffsetList</b> is called to go through the telescope RA/DEC offsets and take frames at
	 * 	each offset.
	 * <li>If the calibration suceeded, the saved state's last time is updated to now, and the state saved.
	 * </ul>
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @param calibration The calibration to do.
	 * @return The method returns true if the calibration was done successfully, false if an error occured.
	 * @see #doConfig
	 * @see #sendBasicAck
	 * @see #stateFilename
	 * @see #calibrationFrameCount
	 * @see #meanCounts
	 * @see ngat.phase2.SpratConfig#positionToString
	 */
	protected boolean doCalibration(TWILIGHT_CALIBRATE twilightCalibrateCommand,
			TWILIGHT_CALIBRATE_DONE twilightCalibrateDone,TWILIGHT_CALIBRATECalibration calibration)
	{
		int binx,biny,slitPosition,grismPosition,grismRotation,optimalExposureLength;
		long lastTime,frequency;
		long now;
		float predictedMeanCounts;

		sprat.log(Logging.VERBOSITY_VERBOSE,
			  "Command:"+twilightCalibrateCommand.getClass().getName()+":doCalibrate:"+
			  "binx:"+calibration.getBinX()+
			  ":biny:"+calibration.getBinY()+
			  ":slit:"+SpratConfig.positionToString(calibration.getSlitPosition())+
			  ":grism:"+SpratConfig.positionToString(calibration.getGrismPosition())+
			  ":grism rotation:"+calibration.getGrismRotation()+
			  ":frequency:"+calibration.getFrequency()+
			  ":last time:"+calibration.getLastTime()+" Started.");
	// get copy of calibration data
		binx = calibration.getBinX();
		biny = calibration.getBinY();
		slitPosition = calibration.getSlitPosition();
		grismPosition = calibration.getGrismPosition();
		grismRotation = calibration.getGrismRotation();
		frequency = calibration.getFrequency();
		lastTime = calibration.getLastTime();
	// get current time
		now = System.currentTimeMillis();
	// if we did the calibration more recently than frequency, log and return
		if(now-lastTime < frequency)
		{
			sprat.log(Logging.VERBOSITY_VERBOSE,
			      "Command:"+twilightCalibrateCommand.getClass().getName()+":doCalibrate:"+
				  "binx:"+calibration.getBinX()+
				  ":biny:"+calibration.getBinY()+
				  ":slit:"+SpratConfig.positionToString(calibration.getSlitPosition())+
				  ":grism:"+SpratConfig.positionToString(calibration.getGrismPosition())+
				  ":grism rotation:"+calibration.getGrismRotation()+
				  ":frequency:"+calibration.getFrequency()+
				  ":last time:"+lastTime+
				  "NOT DONE: too soon since last completed:"+now+" - "+lastTime+" < "+frequency+".");
			return true;
		}
	// recalculate the exposure length
		sprat.log(Logging.VERBOSITY_VERBOSE,
			  "Command:"+twilightCalibrateCommand.getClass().getName()+
			  ":doCalibrate:lastExposureLength:"+lastExposureLength);
		optimalExposureLength = (lastExposureLength*(lastBinX*lastBinY))/(binx*biny);
		sprat.log(Logging.VERBOSITY_VERBOSE,
		      "Command:"+twilightCalibrateCommand.getClass().getName()+
		      ":doCalibrate:optimalExposureLength after multiplication through by last bin:("+
		      lastBinX+"x"+lastBinY+") / bin:("+binx+"x"+biny+") =:"+optimalExposureLength);
		exposureLength = optimalExposureLength;
		if(optimalExposureLength < minExposureLength)
			exposureLength = minExposureLength;
		if(optimalExposureLength > maxExposureLength)
			exposureLength = maxExposureLength;
		predictedMeanCounts = meanCounts * (float)(((binx*biny)/(lastBinX*lastBinY))*
			(exposureLength/lastExposureLength));
		sprat.log(Logging.VERBOSITY_VERBOSE,
		      "Command:"+twilightCalibrateCommand.getClass().getName()+":doCalibrate:predictedMeanCounts are "+
		      predictedMeanCounts+" using exposure legnth "+exposureLength);
		// check whether we expect the predicted mean counts to be too small at sunset
		// (it is too dark for this filter/bin combo). If so, try the next calibration.
		if((timeOfNight == TIME_OF_NIGHT_SUNSET)&&(optimalExposureLength > maxExposureLength)&&
		   (predictedMeanCounts < minMeanCounts))
		{
			sprat.log(Logging.VERBOSITY_VERBOSE,
				  "Command:"+twilightCalibrateCommand.getClass().getName()+
				  ":doCalibrate:predictedMeanCounts "+predictedMeanCounts+" too small: Not attempting "+
				  " binx "+binx+" biny "+biny+".");
			return true; // try next calibration
		}
		// check whether we expect the predicted mean counts to be too big at sunrise
		// (it is too light for this filter/bin combo). If so, try the next calibration.
		if((timeOfNight == TIME_OF_NIGHT_SUNRISE)&&(optimalExposureLength < minExposureLength)&&
		   (predictedMeanCounts > maxMeanCounts[bin]))
		{
			sprat.log(Logging.VERBOSITY_VERBOSE,
			      "Command:"+twilightCalibrateCommand.getClass().getName()+
			      ":doCalibrate:predictedMeanCounts "+predictedMeanCounts+" too large: Not attempting "+
			      " binx "+binx+" biny "+biny+".");
			return true; // try next calibration
		}
	// if we are going to do this calibration, reset the last bin for next time
	// We need to think about when to do this when the new exposure length means we DON'T do the calibration
		lastBinX = binx;
		lastBinY = biny;
		if((now+exposureLength+frameOverhead) > 
			(implementationStartTime+twilightCalibrateCommand.getTimeToComplete()))
		{
			sprat.log(Logging.VERBOSITY_VERBOSE,
				"Command:"+twilightCalibrateCommand.getClass().getName()+
				":doCalibrate:Ran out of time to complete:"+
				"((now:"+now+
				")+(exposureLength:"+exposureLength+
				")+(frameOverhead:"+frameOverhead+")) > "+
				"((implementationStartTime:"+implementationStartTime+
				")+(timeToComplete:"+twilightCalibrateCommand.getTimeToComplete()+")).");
			return true;
		}
	// send an ack before the frame, so the client doesn't time out during configuration
		if(sendBasicAck(twilightCalibrateCommand,twilightCalibrateDone,frameOverhead) == false)
			return false;
	// configure slide/filter/CCD camera
		if(doConfig(twilightCalibrateCommand,twilightCalibrateDone,binx,biny,slitPosition,grismPosition,
			    grismRotation) == false)
			return false;
	// send an ack before the frame, so the client doesn't time out during the first exposure
		if(sendBasicAck(twilightCalibrateCommand,twilightCalibrateDone,exposureLength+frameOverhead) == false)
			return false;
	// do the frames with this configuration
		calibrationFrameCount = 0;
		if(doOffsetList(twilightCalibrateCommand,twilightCalibrateDone,binx,biny,slitPosition,grismPosition,
				grismRotation) == false)
			return false;
	// update state, if we completed the whole calibration.
		if(calibrationFrameCount == offsetList.size())
		{
			twilightCalibrateState.setLastTime(binx,biny,slitPosition,grismPosition,grismRotation);
			try
			{
				twilightCalibrateState.save(stateFilename);
			}
			catch(IOException e)
			{
				String errorString = new String(twilightCalibrateCommand.getId()+
					":doCalibration:Failed to save state filename:"+stateFilename);
				sprat.error(this.getClass().getName()+":"+errorString,e);
				twilightCalibrateDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2307);
				twilightCalibrateDone.setErrorString(errorString);
				twilightCalibrateDone.setSuccessful(false);
				return false;
			}
			lastTime = twilightCalibrateState.getLastTime(binx,biny,slitPosition,grismPosition,grismRotation);
			calibration.setLastTime(lastTime);
			sprat.log(Logging.VERBOSITY_VERBOSE,
				  "Command:"+twilightCalibrateCommand.getClass().getName()+
				  ":doCalibrate:Calibration successfully completed:"+
				  "binx:"+calibration.getBinX()+
				  ":biny:"+calibration.getBinY()+
				  ":slit:"+SpratConfig.positionToString(calibration.getSlitPosition())+
				  ":grism:"+SpratConfig.positionToString(calibration.getGrismPosition())+
				  ":grism rotation:"+calibration.getGrismRotation()+".");
		}// end if done calibration
		else
		{
			sprat.log(Logging.VERBOSITY_VERBOSE,
				  "Command:"+twilightCalibrateCommand.getClass().getName()+
				  ":doCalibrate:Calibration NOT completed:"+
				  "binx:"+calibration.getBinX()+
				  ":biny:"+calibration.getBinY()+
				  ":slit:"+SpratConfig.positionToString(calibration.getSlitPosition())+
				  ":grism:"+SpratConfig.positionToString(calibration.getGrismPosition())+
				  ":grism rotation:"+calibration.getGrismRotation()+".");
		}
		return true;
	}

	/**
	 * Method to setup the CCD configuration with the specified binning factor.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @param binx The X binning factor to use.
	 * @param biny The Y binning factor to use.
	 * @param slitPosition Whether the slit is in or out of the beam.
	 * @param grismPosition Whether the grism is in or out of the beam.
	 * @param grismRotation The rotation position of the grism (either 0 | 1).
	 * @return The method returns true if the calibration was done successfully, false if an error occured.
	 * @see ngat.phase2.SpratConfig#POSITION_IN
	 * @see ngat.phase2.SpratConfig#POSITION_OUT
	 * @see ngat.phase2.SpratConfig#positionToString
	 */
	protected boolean doConfig(TWILIGHT_CALIBRATE twilightCalibrateCommand,
				   TWILIGHT_CALIBRATE_DONE twilightCalibrateDone,int binx,int biny,
				   int slitPosition,int grismPosition,int grismRotation)
	{
	}
}
