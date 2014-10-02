// ACQUIREImplementationTweak.java
// $HeadURL: svn://ltdevsrv/sprat/java/ngat/sprat/ACQUIREImplementationTweak.java $
package ngat.sprat;

import java.io.*;
import java.lang.*;
import java.util.*;

import ngat.astrometry.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.message.INST_DP.*;
import ngat.sprat.ccd.command.*;
import ngat.phase2.*;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the ACQUIRE command sent to a server using the
 * Java Message System. This implementation calculates the RA/Dec offset when in Mode WCS_FIT in pixels,
 * so it can use TWEAK (OFFSET_X_Y) rather than OFFBY ARC (OFFSET_RA_DEC).  TWEAKs can be done whilst
 * the autoguider is locked, making for a more accurate acquisition. 
 * @author Chris Motram
 * @version $Revision: 50 $
 */
public class ACQUIREImplementationTweak extends FITSImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id: ACQUIREImplementationTweak.java 50 2014-09-24 14:57:03Z cjm $");
	/**
	 * How many arc-seconds in 1 second of RA. A double, of value 15.
	 */
	public final static double SECONDS_TO_ARCSECONDS = 15.0;
	/**
	 * How many arc-seconds in 1 day, The maximum number of arc-seconds represented by an RA
	 * before going back to zero.
	 */
	public final static double ARCSECONDS_PER_DAY = (24.0*60.0*60.0*15.0);
	/**
	 * Binning used for acquisition images.
	 */
	protected int bin = 1;
	/**
	 * Exposure length (in milliseconds) of acquisition images.
	 */
	protected int exposureLength = 1000;
	/**
	 * The frame overhead for a full frame, in milliseconds. This takes into account readout time,
	 * real time data reduction, communication overheads and the like.
	 */
	protected int frameOverhead = 0;
	/**
	 * The reduced FITS filename of the current acquisition image, after pipelining/wcs fitting.
	 */
	protected String reducedFITSFilename = null;
	/**
	 * The X pixel position of the brightest object on the reduced FITS image.
	 */
	protected double brightestObjectXPixel = 0.0;
	/**
	 * The Y pixel position of the brightest object on the reduced FITS image.
	 */
	protected double brightestObjectYPixel = 0.0;
	/**
	 * The current acquisition offset being applied in X, in binned pixels. 
	 */
	protected double xPixelOffset = 0.0;
	/**
	 * The current acquisition offset being applied in Y, in binned pixels. 
	 */
	protected double yPixelOffset = 0.0;
	/**
	 * The acuisition mode in the ACQUIRE command parameters. This determines how the 
	 * acquisition is don. Acceptable values are those in ngat.phase2.TelescopeConfig.
	 * Acquisition can be done on the brightest object in the frame, or from the RA and Dec by WCS fitting.
	 * ngat.phase2.TelescopeConfig#ACQUIRE_MODE_NONE
	 * ngat.phase2.TelescopeConfig#ACQUIRE_MODE_BRIGHTEST
	 * ngat.phase2.TelescopeConfig#ACQUIRE_MODE_WCS
	 */
	protected int acquisitionMode = TelescopeConfig.ACQUIRE_MODE_NONE;
	/**
	 * A string version of the target RA supplied in the ACQUIRE command parameters.
	 */
	protected String acquireRAString = null;
	/**
	 * A string version of the target Declination supplied in the ACQUIRE command parameters.
	 */
	protected String acquireDecString = null;
	/**
	 * The target RA supplied in the ACQUIRE command parameters.
	 */
	protected double acquireRARads = 0.0;
	/**
	 * The target Declination supplied in the ACQUIRE command parameters.
	 */
	protected double acquireDecRads = 0.0;
	/** 
	 * Non Sidereal Tracking rate in RA (rad/sec).
	 */
	protected double raRateRadPerSec = 0.0;
	/** 
	 * NS Tracking rate in Dec (rad/sec).
	 */
	protected double decRateRadPerSec = 0.0;
	/** 
	 * Time at which the supplied NS tracking rates and Ra/Dec coordinates were calculated.
	 */
	protected long rateTime = 0L;
	/** 
	 * If true, indicates that the target is moving and the supplied rates must be applied.
	 */
	protected boolean moving = false;
	/** 
	 * Record time of start of exposure.
	 */
	protected long timeStartExposure = 0L;
	/** 
	 * Record time of end of exposure.
	 */
	protected long timeEndExposure = 0L;
	/**
	 * The target X Pixel (unbinned) supplied in the ACQUIRE command parameters.
	 */
	protected int acquireXPixel = 0;
	/**
	 * The target Y Pixel (unbinned) supplied in the ACQUIRE command parameters.
	 */
	protected int acquireYPixel = 0;
	/**
	 * The positional acquisition error allowed between the specified target RA/Dec and the
	 * RA/Dec currently at the specified pixel. In decimal arcseconds.
	 */
	protected double acquireThreshold = 0.0;
	/**
	 * The maximum number of offsets to attempt before we decide we are in some sort of infinite loop
	 * and exit with an error.
	 */
	protected int maximumOffsetCount = 10;
	/**
	 * The list of FITS filenames returned by the multbias command.
	 */
	protected List<String> filenameList = null;
	/**
	 * The multrun number used for FITS filenames for this command.
	 */
	protected int multrunNumber;

	/**
	 * Constructor.
	 */
	public ACQUIREImplementationTweak()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.ACQUIRE&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.ACQUIRE";
	}

	/**
	 * This method gets the ACQUIRE command's acknowledge time. 
	 * This returns the server connection threads default acknowledge time is available.
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
	 * This method implements the ACQUIRE command. 
	 * <ul>
	 * <li>It extracts acquisitionMode, acquireThreshold , acquireXPixel, acquireYPixel, 
	 *     acquireRARads, acquireDecRads, 
	 *     acquireRAString, acquireDecString, moving, rateTime, raRateRadPerSec, decRateRadPerSec 
	 *     from the acquire command.
	 * <li>loadConfig is called.
	 * <li>moveFold is called.
	 * <li>We send a Basic Ack (sendBasicAck) so the command does not time out during Config.
	 * <li>doConfig is called to configure the imager.
	 * <li>If the acquisitionMode is TelescopeConfig.ACQUIRE_MODE_WCS, 
	 *     doAcquisitionWCS is called to acquire the specified RA/Dec onto the specified pixel.
	 * <li>If the acquisitionMode is TelescopeConfig.ACQUIRE_MODE_BRIGHTEST, 
	 *     doAcquisitionBrightest is called to acquire the brightest object in the frame onto the specified pixel.
	 * </ul>
	 * @see #acquisitionMode
	 * @see #acquireThreshold
	 * @see #acquireXPixel
	 * @see #acquireYPixel
	 * @see #acquireRARads
	 * @see #acquireDecRads
	 * @see #acquireRAString
	 * @see #acquireDecString
	 * @see #status
	 * @see #loadConfig
	 * @see #doConfig
	 * @see #doAcquisitionWCS
	 * @see #doAcquisitionBrightest
	 * @see #sendBasicAck
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		ACQUIRE acquireCommand = (ACQUIRE)command;
		ACQUIRE_DONE acquireDone = new ACQUIRE_DONE(command.getId());
		
		// get parameters
		acquisitionMode = acquireCommand.getAcquisitionMode();
		acquireThreshold = acquireCommand.getThreshold();
		acquireRARads = acquireCommand.getRA();
		acquireDecRads = acquireCommand.getDec();
		acquireRAString = Position.formatHMSString(acquireRARads,":");
		acquireDecString = Position.formatDMSString(acquireDecRads,":");
		acquireXPixel = acquireCommand.getXPixel();
		acquireYPixel = acquireCommand.getYPixel();
		moving = acquireCommand.getMoving();
		// if NOT moving these next will not be used
		rateTime = acquireCommand.getCalculationTime();
		raRateRadPerSec = acquireCommand.getRARate();
		decRateRadPerSec = acquireCommand.getDecRate();

		sprat.log(Logging.VERBOSITY_VERBOSE,
			  "Command:"+acquireCommand.getClass().getName()+
			  ":processCommand:Mode:"+acquisitionMode+":Threshold:"+acquireThreshold+
			  "(arcsec):RA:"+acquireRAString+":Dec:"+acquireDecString+
			  ":xPixel(unbinned):"+acquireXPixel+":yPixel(unbinned):"+acquireYPixel+".");
		sprat.log(Logging.VERBOSITY_VERBOSE,"Command:"+acquireCommand.getClass().getName()+
			  ":processCommand:Moving:"+moving+":rate time:"+rateTime+
			  ":RA Rate (rad/s):"+raRateRadPerSec+":Dec Rate (rad/s):"+decRateRadPerSec+".");
		// if acquire mode is "NONE" just return
		if(acquisitionMode == TelescopeConfig.ACQUIRE_MODE_NONE)
		{
			sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":processCommand:"+
				  command+":Acquisition mode was NONE: Returning done.");
			acquireDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
			acquireDone.setErrorString("");
			acquireDone.setSuccessful(true);
			return acquireDone;
		}
		if(moving)
		{
			sprat.log(Logging.VERBOSITY_VERBOSE, 
				  "Target is designated as moving with tracking rates: RA: "+
				  (3600.0*Math.toDegrees(raRateRadPerSec))+
				  " Dec: "+(3600.0*Math.toDegrees(decRateRadPerSec)));
		}
		// load configuration
		try
		{
			loadConfig();
		}
		catch(Exception e)
		{
			String errorString = new String(acquireCommand.getId()+
							":processCommand:Failed to load configuration:"+e);
			sprat.error(this.getClass().getName()+":"+errorString,e);
			acquireDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2900);
			acquireDone.setErrorString(errorString);
			acquireDone.setSuccessful(false);
			return acquireDone;
		}
		// move the fold mirror to the correct location
		if(moveFold(acquireCommand,acquireDone) == false)
			return acquireDone;
		if(testAbort(acquireCommand,acquireDone) == true)
			return acquireDone;
		// send an ack before the config, so the client doesn't time out during configuration
		if(sendBasicAck(acquireCommand,acquireDone,frameOverhead) == false)
			return acquireDone;
		// configure instrument
		try
		{
			doConfig(acquireCommand,acquireDone);
		}
		catch(Exception e)
		{
			String errorString = new String(acquireCommand.getId()+
							":processCommand:Failed to do configuration:"+e);
			sprat.error(this.getClass().getName()+":"+errorString,e);
			acquireDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2901);
			acquireDone.setErrorString(errorString);
			acquireDone.setSuccessful(false);
			return acquireDone;
		}
		if(testAbort(acquireCommand,acquireDone) == true)
			return acquireDone;
		// loop until acquisition complete
		try
		{
			if(acquisitionMode == TelescopeConfig.ACQUIRE_MODE_WCS)
				doAcquisitionWCS(acquireCommand,acquireDone);
			if(acquisitionMode == TelescopeConfig.ACQUIRE_MODE_BRIGHTEST)
				doAcquisitionBrightest(acquireCommand,acquireDone);
		}
		catch(Exception e)
		{
			String errorString = new String(acquireCommand.getId()+
							":processCommand:Failed to do acquisition:"+e);
			sprat.error(this.getClass().getName()+":"+errorString,e);
			acquireDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2902);
			acquireDone.setErrorString(errorString);
			acquireDone.setSuccessful(false);
			return acquireDone;
		}
		acquireDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
		acquireDone.setErrorString("");
		acquireDone.setSuccessful(true);
		return acquireDone;
	}

	/**
	 * Load some configuration.
	 * <dl>
	 * <dt>bin</dt><dd>Loaded from the <b>sprat.acquire.bin</b></dd>
	 * <dt>exposureLength</dt><dd>Loaded from the <b>sprat.acquire.exposure_length.brightest</b> or 
	 *     <b>sprat.acquire.exposure_length.wcs</b> depending on <i>acquisitionMode</i></dd>
	 * <dt>frameOverhead</dt><dd>Loaded from the <b>sprat.acquire.frame_overhead</b></dd>
	 * <dt>maximumOffsetCount</dt><dd>Loaded from the <b>sprat.acquire.offset.count.maximum</b></dd>
	 * </dl>
	 * @exception NumberFormatException Thrown if the specified property is not a valid number.
	 * @see #bin
	 * @see #exposureLength
	 * @see #status
	 * @see #frameOverhead
	 * @see #maximumOffsetCount
	 * @see #acquisitionMode
	 * @see SpratStatus#getPropertyInteger
	 */
	protected void loadConfig() throws NumberFormatException
	{
		bin = status.getPropertyInteger("sprat.acquire.bin");
		if(acquisitionMode == TelescopeConfig.ACQUIRE_MODE_WCS)
			exposureLength = status.getPropertyInteger("sprat.acquire.exposure_length.wcs");
		if(acquisitionMode == TelescopeConfig.ACQUIRE_MODE_BRIGHTEST)
			exposureLength = status.getPropertyInteger("sprat.acquire.exposure_length.brightest");
		frameOverhead = status.getPropertyInteger("sprat.acquire.frame_overhead");
		maximumOffsetCount = status.getPropertyInteger("sprat.acquire.offset.count.maximum");
	}

	/**
	 * Method to setup the CCD configuration with the specified binning factor.
	 * <ul>
	 * <li>Some configuration is loaded from the properties files.
	 * <li>We call sendConfigCommand to configure the CCD C layer (binning) with no window.
	 * <li>rotateGrism(0) is called so we can move the grism out of the beam.
	 * <li>moveGrism is called to move the grism out of the beam.
	 * <li>moveSlit is called to move the slit out of the beam.
	 * <li>moveMirror is called to move the calibration mirror out of the beam.
	 * <li>setFocusOffset is called to set the focus offset for the Sprat instrument focus. 
	 * <li>incConfigId is called to increment the config's unique ID.
	 * <li>The status's setConfigName is used to set a name for the config.
	 * </ul>
	 * @param acquireCommand The command we are implementing.
	 * @param acquireDone The done command to be returned to the client. The error details may be filled
	 *        in by this method if the procedure fails.
	 * @see #bin
	 * @see #sendConfigCommand
	 * @see #rotateGrism
	 * @see #moveGrism
	 * @see #moveMirror
	 * @see #setFocusOffset
	 * @see ngat.phase2.SpratConfig#POSITION_OUT
	 * @exception Exception Thrown if sendConfigCommand fails.
	 */
	protected void doConfig(ACQUIRE acquireCommand,ACQUIRE_DONE acquireDone) throws Exception
	{
		Window window = null;

		window = new Window();
		// send command to C layer
		sendConfigCommand(bin,bin,false,window);
		// test abort
		if(testAbort(acquireCommand,acquireDone) == true)
		{
			throw new Exception(this.getClass().getName()+"ACQUIRE:"+acquireCommand.getId()+
					    ":doConfig:Aborted.");
		}
		// configure sprat mechanisms
		// move and rotate grism.
		// we can only move the grism in and out, if the rotation cylinder is stowed
		rotateGrism(0);
		if(testAbort(acquireCommand,acquireDone) == true)
		{
			throw new Exception(this.getClass().getName()+"ACQUIRE:"+acquireCommand.getId()+
					    ":doConfig:Aborted.");
		}
		// move the grism to the configured position
		moveGrism(SpratConfig.POSITION_OUT);
		if(testAbort(acquireCommand,acquireDone) == true)
		{
			throw new Exception(this.getClass().getName()+"ACQUIRE:"+acquireCommand.getId()+
					    ":doConfig:Aborted.");
		}
		// move slit
		moveSlit(SpratConfig.POSITION_OUT);
		if(testAbort(acquireCommand,acquireDone) == true)
		{
			throw new Exception(this.getClass().getName()+"ACQUIRE:"+acquireCommand.getId()+
					    ":doConfig:Aborted.");
		}
		// move mirror
		moveMirror(SpratConfig.POSITION_OUT);
		if(testAbort(acquireCommand,acquireDone) == true)
		{
			throw new Exception(this.getClass().getName()+"ACQUIRE:"+acquireCommand.getId()+
					    ":doConfig:Aborted.");
		}
		// Issue ISS OFFSET_FOCUS commmand to focus the telescope on Sprat
		setFocusOffset(acquireCommand.getId());
		// Increment unique config ID.
		// This is queried when saving FITS headers to get the CONFIGID value.
		status.incConfigId();
		// Store name of configuration used in status object.
		// This is queried when saving FITS headers to get the CONFNAME value.
		status.setConfigName("ACQUIRE:"+acquireCommand.getId()+":"+bin);
	}

	/**
	 * This method offsets the telescope in X and Y (focal plane geometry) using the 
	 * xPixelOffset and yPixelOffset parameters. 
	 * These are converted to arcseconds and sent to the RCS using the OFFSET_X_Y command.
	 * The plate scale is currently retrieved from the FITS property file value:"sprat.fits.value.CCDSCALE."+bin.
	 * It is not clear how non-square binning is supported yet, or whether this is the right thing to do.
	 * @param id The string id of the command instance we are implementing. Used for generating ISS command id's.
	 * @param xPixelOffset The offset in X binned pixels in the focal plane of the acquisition instrument.
	 * @param yPixelOffset The offset in Y binned pixels in the focal plane of the acquisition instrument.
	 * @exception Exception Thrown if the ISS command OFFSET_RA_DEC fails.
	 * @see #sprat
	 * @see #serverConnectionThread
	 * @see #bin
	 * @see #status
	 * @see SpratStatus#getPropertyDouble
	 * @see Sprat#sendISSCommand
	 * @see ngat.message.ISS_INST.OFFSET_X_Y
	 */
	protected void doXYPixelOffset(String id,int xPixelOffset,int yPixelOffset) throws Exception
	{
		OFFSET_X_Y offsetXYCommand = null;
		INST_TO_ISS_DONE instToISSDone = null;
		double plateScale;
		float xArcsecOffset,yArcsecOffset;
		
		// log telescope offset
		sprat.log(Logging.VERBOSITY_VERBOSE,"Attempting telescope position XY Pixel offset x(binned pixels):"+
			  xPixelOffset+":y(binned pixels):"+yPixelOffset+".");
		// get plate scale from FITS header defaults and current binning
		plateScale = status.getPropertyDouble("sprat.fits.value.CCDSCALE."+bin);
		// convert pixel offset to arcsec offset (in focal plane geometry/aperture).
		xArcsecOffset = (float)(xPixelOffset*plateScale);
		yArcsecOffset = (float)(yPixelOffset*plateScale);
		sprat.log(Logging.VERBOSITY_VERBOSE,"Attempting telescope position XY offset x(arcsec):"+
			  xArcsecOffset+":y(arcsec):"+yArcsecOffset+".");
		// tell telescope of offset RA and DEC
		offsetXYCommand = new OFFSET_X_Y(id);
		offsetXYCommand.setXOffset(xArcsecOffset);
		offsetXYCommand.setYOffset(yArcsecOffset);
		offsetXYCommand.setRotation(0.0f);
		instToISSDone = sprat.sendISSCommand(offsetXYCommand,serverConnectionThread);
		if(instToISSDone.getSuccessful() == false)
		{
			throw new Exception(this.getClass().getName()+"ACQUIRE:"+id+"Offset X Y failed:x = "+
					    xArcsecOffset+", y = "+yArcsecOffset+":"+instToISSDone.getErrorString());
		}
	}

	/**
	 * The method that does the acquisition, using WCS fitting. 
	 * The RA and Dec offsets are initialised.
	 * Then the following is performed 
	 * in a while loop, that is terminated when the specified RA and Dec lie close to the specified pixel.
	 * <ul>
	 * <li><b>sendBasicAck</b> is called to ensure the command does not time out during an exposure being taken.
	 * <li><b>doFrame</b> is called to take an acquisition frame.
	 * <li>An instance of ACQUIRE_ACK is sent back to the client using <b>sendAcquireAck</b>.
	 * <li><b>reduceExpose</b> is called to pass the frame to the Real Time Data Pipeline for processing.
	 *     WCS fitting is also attempted here.
	 * <li><b>testAbort</b> is called to see if this command implementation has been aborted.
	 * <li>We call <b>computeRADecOffset</b> to check whether we are in the correct position, 
	 *     and calculate a new offset to apply if necessary.
diddly
	 * <li>If a new offset is required <b>doRADecOffset</b> is called.
	 * <li>We check to see if the loop should be terminated.
	 * </ul>
	 * @param acquireCommand The instance of ACQUIRE we are currently running.
	 * @param acquireDone The instance of ACQUIRE_DONE to fill in with errors we receive.
	 * @exception Exception Thrown if testAbort diddly... failed.	 
	 * @see #doFrame
	 * @see #sendAcquireDpAck
	 * @see #computeRADecOffset
	 * @see CommandImplementation#testAbort
	 * @see EXPOSEImplementation#reduceExpose
	 * @see #exposureLength
	 * @see #frameOverhead
	 * @see #reducedFITSFilename
	 * @see #maximumOffsetCount
	 * @see #sendBasicAck
	 * @see #sendAcquireAck
	 * @see #reduceExpose
	 */
	protected void doAcquisitionWCS(ACQUIRE acquireCommand,ACQUIRE_DONE acquireDone) throws Exception
	{
		String filename = null;
		long now;
		boolean done;
		int offsetCount;
		
		// initialise offset
		xPixelOffset = 0.0;
		yPixelOffset = 0.0;
		// keep track of how many times we attempt to offset
		offsetCount = 0;
		// start loop
		done = false;
		while(done == false)
		{
			// send an ack before the frame, so the client doesn't time out during the exposure
			if(sendBasicAck(acquireCommand,acquireDone,exposureLength+frameOverhead) == false)
			{
				throw new Exception(this.getClass().getName()+
						    ":doAcquisitionWCS:SendBasicAck failed.");
			}
			// Take frame
			filename = doFrame(acquireCommand,acquireDone);
			// send Acquire ACK with filename back to client
			// time to complete is reduction time, we will send another ACK after reduceCalibrate
			sendAcquireAck(acquireCommand.getId(),frameOverhead,filename);
			// Call pipeline to reduce data (and WCS fit).
			reduceExpose(acquireCommand.getId(),filename,true);
			// Test abort status.
			if(testAbort(acquireCommand,acquireDone) == true)
			{
				throw new Exception(this.getClass().getName()+"ACQUIRE:"+acquireCommand.getId()+
						    ":doAcquisitionWCS:Aborted.");
			}
			// log reduction
			sprat.log(Logging.VERBOSITY_VERBOSE,
				  "Command:"+acquireCommand.getId()+
				  ":doAcquisitionWCS:Exposure reduction:filename:"+reducedFITSFilename+".");
			// Extract WCS information from reducedFITSFilename
			// Calculate offset
			// check loop termination - is offset less than threshold?
			done = computeRADecOffset();
			if(done == false)
			{
				// issue new XY Pixel offset
				doXYPixelOffset(acquireCommand.getId(),(int)xPixelOffset,(int)yPixelOffset);
				offsetCount++;
			}
			// Have we taken too many goes to acquire?
			if(offsetCount > maximumOffsetCount)
			{
				throw new Exception(this.getClass().getName()+"ACQUIRE:"+acquireCommand.getId()+
						    ":doAcquisitionWCS:Too many attempts:"+offsetCount+".");
			}
		}// end while !done
	}


	/**
	 * The method that does the acquisition, dragging the brightest star position returned by the DpRt
	 * to the target pixel position. 
	 * Then the following is performed 
	 * in a while loop, that is terminated when the specified RA and Dec lie close to the specified pixel.
	 * <ul>
	 * <li><b>sendBasicAck</b> is called to ensure the command does not time out during an exposure being taken.
	 * <li><b>doFrame</b> is called to take an acquisition frame.
	 * <li>An instance of ACQUIRE_ACK is sent back to the client using <b>sendAcquireAck</b>.
	 * <li><b>reduceExpose</b> is called to pass the frame to the Real Time Data Pipeline for processing.
	 *     WCS fitting is <b>NOT</b> attempted here. brightestObjectXPixel and brightestObjectYPixel are set
	 *     within reduceExpose to the centroid of the brightest object in the frame.
	 * <li><b>testAbort</b> is called to see if this command implementation has been aborted.
	 * <li>We call <b>computeXYPixelOffset</b> with brightestObjectXPixel and brightestObjectYPixel as the 
	 *     object pixel to check whether we are in the correct position, 
	 *     and calculate a new offset to apply if necessary.
	 * <li>If a new offset is required <b>doXYPixelOffset</b> is called.
	 * <li>We check to see if the loop should be terminated.
	 * </ul>
	 * @param acquireCommand The instance of ACQUIRE we are currently running.
	 * @param acquireDone The instance of ACQUIRE_DONE to fill in with errors we receive.
	 * @exception Exception Thrown if testAbort/setFitsHeaders/getFitsHeadersFromISS/getFitsHeadersFromBSS/
	 *            saveFitsHeaders/doXYPixelOffset failed.
	 * @see #doFrame
	 * @see #brightestObjectXPixel
	 * @see #brightestObjectYPixel
	 * @see #xPixelOffset
	 * @see #yPixelOffset
	 * @see #sendAcquireDpAck
	 * @see #computeXYPixelOffset
	 * @see #doXYPixelOffset
	 * @see CommandImplementation#testAbort
	 * @see EXPOSEImplementation#reduceExpose
	 * @see #exposureLength
	 * @see #frameOverhead
	 * @see #reducedFITSFilename
	 * @see #maximumOffsetCount
	 * @see #sendBasicAck
	 * @see #sendAcquireAck
	 * @see #reduceExpose
	 */
	protected void doAcquisitionBrightest(ACQUIRE acquireCommand,ACQUIRE_DONE acquireDone) throws 
		Exception
	{
		String filename = null;
		long now;
		boolean done;
		int offsetCount;
		
		// initialise offset
		xPixelOffset = 0.0;
		yPixelOffset = 0.0;
		// keep track of how many times we attempt to offset
		offsetCount = 0;
		// start loop
		done = false;
		while(done == false)
		{
			// send an ack before the frame, so the client doesn't time out during the exposure
			if(sendBasicAck(acquireCommand,acquireDone,exposureLength+frameOverhead) == false)
			{
				throw new Exception(this.getClass().getName()+
						    ":doAcquisitionBrightest:SendBasicAck failed.");
			}
			// Take frame
			filename = doFrame(acquireCommand,acquireDone);
			// send Acquire ACK with filename back to client
			// time to complete is reduction time, we will send another ACK after reduceCalibrate
			sendAcquireAck(acquireCommand.getId(),frameOverhead,filename);
			// Call pipeline to reduce data. Do NOT WCS fit.
			reduceExpose(acquireCommand.getId(),filename,false);
			// Test abort status.
			if(testAbort(acquireCommand,acquireDone) == true)
			{
				throw new Exception(this.getClass().getName()+"ACQUIRE:"+acquireCommand.getId()+
						    ":doAcquisitionBrightest:Aborted.");
			}
			// log reduction
			sprat.log(Logging.VERBOSITY_VERBOSE,"Command:"+acquireCommand.getId()+
				  ":doAcquisitionBrightest:Exposure reduction:filename:"+reducedFITSFilename+".");
			// Calculate offset
			// check loop termination - is offset less than threshold?
			done = computeXYPixelOffset(brightestObjectXPixel,brightestObjectYPixel);
			if(done == false)
			{
				// issue new XY Pixel offset
				doXYPixelOffset(acquireCommand.getId(),(int)xPixelOffset,(int)yPixelOffset);
				offsetCount++;
			}
			// Have we taken too many goes to acquire?
			if(offsetCount > maximumOffsetCount)
			{
				throw new Exception(this.getClass().getName()+"ACQUIRE:"+acquireCommand.getId()+
						    ":doAcquisitionBrightest:Too many attempts:"+offsetCount+".");
			}
		}// end while !done
	}

	/**
	 * Take 1 acquisition frame with the imager.
	 * <ul>
	 * <li>The FITS headers are setup from the current configuration.
	 * <li>Some FITS headers are got from the ISS.
	 * <li><b>testAbort</b> is called to see if this command implementation has been aborted.
	 * <li>The frame is taken, using the sendMultrunCommand method.
	 * <li><b>testAbort</b> is called to see if this command implementation has been aborted.
	 * </ul>
	 * @param acquireCommand The instance of ACQUIRE we are currently running.
	 * @param acquireDone The instance of ACQUIRE_DONE to fill in with errors we receive.
	 * @return A string, the filename taken by the imager.
	 * @exception CCDLibraryNativeException Thrown if the exposure failed.
	 * @exception Exception Thrown if testAbort/setFitsHeaders/getFitsHeadersFromISS/getFitsHeadersFromBSS/
	 *            saveFitsHeaders failed.
	 * @see #status
	 * @see #exposureLength
	 * @see #sendMultrunCommand
	 * @see #timeStartExposure
	 * @see #timeEndExposure
	 * @see FITSImplementation#testAbort
	 * @see FITSImplementation#clearFitsHeaders
	 * @see FITSImplementation#setFitsHeaders
	 * @see FITSImplementation#getFitsHeadersFromISS
	 */
	protected String doFrame(ACQUIRE acquireCommand,ACQUIRE_DONE acquireDone) throws Exception
	{
		String filename = null;

		// setup fits headers
		clearFitsHeaders();
		if(setFitsHeaders(acquireCommand,acquireDone) == false)
		{
			throw new Exception(this.getClass().getName()+":ACQUIRE:"+acquireCommand.getId()+
					    ":doFrame:setFitsHeaders failed:"+acquireDone.getErrorNum()+
					    ":"+acquireDone.getErrorString());
		}
		if(getFitsHeadersFromISS(acquireCommand,acquireDone) == false)
		{
			throw new Exception(this.getClass().getName()+":ACQUIRE:"+acquireCommand.getId()+
					    ":doFrame:getFitsHeadersFromISS failed:"+
					    acquireDone.getErrorNum()+
					    ":"+acquireDone.getErrorString());
		}
		if(testAbort(acquireCommand,acquireDone) == true)
		{
			throw new Exception(this.getClass().getName()+":ACQUIRE:"+acquireCommand.getId()+
					    ":doFrame:Aborted.");
		}
		if(testAbort(acquireCommand,acquireDone) == true)
		{
			throw new Exception(this.getClass().getName()+":ACQUIRE:"+acquireCommand.getId()+
					    ":doFrame:Aborted.");
		}
		// log exposure
		sprat.log(Logging.VERBOSITY_VERBOSE,"Command:"+acquireCommand.getId()+
			  ":doFrame:Attempting exposure:"+"length "+exposureLength+".");
		
		// do exposure, record actual start and end times
		timeStartExposure = System.currentTimeMillis();
		filename = sendMultrunCommand(exposureLength);
		timeEndExposure = System.currentTimeMillis();
		// Test abort status.
		if(testAbort(acquireCommand,acquireDone) == true)
		{
			throw new Exception(this.getClass().getName()+":ACQUIRE:"+acquireCommand.getId()+
					    ":doFrame:Aborted.");
		}
		return filename;
	}

	/**
	 * Send the multrun command to the C layer.
	 * @param exposureLength The exposure length in milliseconds.
	 * @return The FITS filename produced.
	 * @exception Exception Thrown if an error occurs.
	 * @see #multrunNumber
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
	protected String sendMultrunCommand(int exposureLength) throws Exception
	{
		MultrunCommand command = null;
		List<String> filenameList = null;
		int returnCode;
		String filename = null;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultrunCommand:exposureLength = "+exposureLength+".");
		command = new MultrunCommand();
		// configure C comms
		command.setAddress(ccdCLayerHostname);
		command.setPortNumber(ccdCLayerPortNumber);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultrunCommand:hostname = "+ccdCLayerHostname+
			   " :port number = "+ccdCLayerPortNumber+".");
		command.setCommand(exposureLength,1,MultrunCommand.EXPOSURE_TYPE_ACQUIRE);
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
		if(filenameList == null)
		{
			throw new Exception(this.getClass().getName()+
					    ":sendMultrunCommand:Command returned null filename list.");
		}
		if(filenameList.size() != 1)
		{
			throw new Exception(this.getClass().getName()+
					    ":sendMultrunCommand:Wrong number of filenames returned+"+
					    filenameList.size());
		}
		filename = filenameList.get(0);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultrunCommand:finished with filename:"+filename);
		return filename;
	}

	/**
	 * Method to send an instance of ACQUIRE_ACK back to the client. This tells the client about
	 * a FITS frame that has been produced, and also stops the client timing out.
	 * @param id The id to set in the ack.
	 * @param timeToComplete The time it will take to complete the next set of operations
	 *	before the next ACK or DONE is sent to the client. The time is in milliseconds. 
	 * 	The server connection thread's default acknowledge time is added to the value before it
	 * 	is sent to the client, to allow for network delay etc.
	 * @param filename The FITS filename to be sent back to the client, that has just completed
	 * 	processing.
	 * @exception IOException Thrown if sending the acknowledge fails.
	 * @see ngat.message.ISS_INST.ACQUIRE_ACK
	 * @see ngat.message.ISS_INST.ACQUIRE_ACK#setTimeToComplete
	 * @see ngat.message.ISS_INST.ACQUIRE_ACK#setFilename
	 * @see #serverConnectionThread
	 * @see SpratTCPServerConnectionThread#sendAcknowledge
	 * @see SpratTCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	protected void sendAcquireAck(String id,int timeToComplete,String filename) throws IOException
	{
		ACQUIRE_ACK acquireAck = null;
		
		// send acknowledge to say frame is completed.
		acquireAck = new ACQUIRE_ACK(id);
		acquireAck.setTimeToComplete(timeToComplete+serverConnectionThread.getDefaultAcknowledgeTime());
		acquireAck.setFilename(filename);
		serverConnectionThread.sendAcknowledge(acquireAck,true);
	}

	/**
	 * Method to send an instance of ACQUIRE_DP_ACK back to the client. This tells the client about
	 * a FITS frame that has been produced, and some information about sources in the frame.
	 * The time to complete parameter stops the client timing out.
	 * @param id What id to use for the ACK.
	 * @param timeToComplete The time it will take to complete the next set of operations
	 *	before the next ACK or DONE is sent to the client. The time is in milliseconds. 
	 * 	The server connection thread's default acknowledge time is added to the value before it
	 * 	is sent to the client, to allow for network delay etc.
	 * @param filename The reduced filename.
	 * @param seeing The seeing derived from the data pipeline, in arcsecs.
	 * @param counts The integrated counts for the brightest object in the frame.
	 * @param xpix The X pixel position for the brightest object in the frame.
	 * @param ypix The Y pixel position for the brightest object in the frame.
	 * @param photometricity The photometricity for the brightest object in the frame.
	 * @param skyBrightness The sky brightness for the frame.
	 * @param saturation The sky brightness for the frame.
	 * @exception IOException Thrown if sending the acknowledge fails.
	 * @see ngat.message.ISS_INST.ACQUIRE_DP_ACK
	 * @see #serverConnectionThread
	 * @see SpratTCPServerConnectionThread#sendAcknowledge
	 * @see SpratTCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	protected void sendAcquireDpAck(String id,int timeToComplete,String filename,float seeing,float counts,
					float xpix,float ypix, float photometricity, float skyBrightness, 
					boolean saturation) throws IOException
	{
		ACQUIRE_DP_ACK acquireDpAck = null;

		// send acknowledge to say frame is completed.
		acquireDpAck = new ACQUIRE_DP_ACK(id);
		acquireDpAck.setTimeToComplete(timeToComplete+serverConnectionThread.getDefaultAcknowledgeTime());
		acquireDpAck.setFilename(filename);
		acquireDpAck.setCounts(counts);
		acquireDpAck.setXpix(xpix);
		acquireDpAck.setYpix(ypix);
		acquireDpAck.setPhotometricity(photometricity);
		acquireDpAck.setSkyBrightness(skyBrightness);
		acquireDpAck.setSaturation(saturation);
		serverConnectionThread.sendAcknowledge(acquireDpAck,true);
	}

	/**
	 * This routine calls the Real Time Data Pipeline to process the expose FITS image we have just captured.
	 * If the reduction suceeds, reducedFITSFilename is set to the reduced filename. 
	 * brightestObjectXPixel and brightestObjectYPixel are set to the X and P (pixel) position of the 
	 * brightest object on the frame (only required for ACQUIRE_MODE_BRIGHTEST). Also, an sendAcquireDpAck
	 * is sent to the client to stop it timing out.
	 * @param id The identifier to ID the EXPOSE_REDUCE command with.
	 * @param filename The raw FITS image to be reduced.
	 * @param wcsFit Whether the DpRt should attempt a WCS fit.
	 * @exception Exception Thrown if the EXPOSE_REDUCE_DONE is of the wrong class, 
	 *                      or EXPOSE_REDUCE returns an error.
	 * @see ngat.message.INST_DP.EXPOSE_REDUCE
	 * @see #sprat
	 * @see #serverConnectionThread
	 * @see #exposureLength
	 * @see #reducedFITSFilename
	 * @see #brightestObjectXPixel
	 * @see #brightestObjectYPixel
	 * @see #frameOverhead
	 * @see Sprat#sendDpRtCommand
	 * @see #sendAcquireDpAck
	 */
	public void reduceExpose(String id,String filename,boolean wcsFit) throws Exception
	{
		EXPOSE_REDUCE reduce = new EXPOSE_REDUCE(id);
		INST_TO_DP_DONE instToDPDone = null;
		EXPOSE_REDUCE_DONE reduceDone = null;

		reduce.setFilename(filename);
		reduce.setWcsFit(wcsFit);
		instToDPDone = sprat.sendDpRtCommand(reduce,serverConnectionThread);
		if(instToDPDone.getSuccessful() == false)
		{
			throw new Exception(this.getClass().getName()+"ACQUIRE:"+id+
					    "reduceExpose:EXPOSE_REDUCE failed:"+instToDPDone.getErrorNum()+":"+
					    instToDPDone.getErrorString());
		}
		// Copy the DP REDUCE DONE parameters to the EXPOSE DONE parameters
		if(instToDPDone instanceof EXPOSE_REDUCE_DONE)
		{
			reduceDone = (EXPOSE_REDUCE_DONE)instToDPDone;
			// get reduced filename
			reducedFITSFilename = reduceDone.getFilename();
			// send DpAck
			sendAcquireDpAck(id,frameOverhead,reducedFITSFilename,
					 reduceDone.getSeeing(),reduceDone.getCounts(),
					 reduceDone.getXpix(),reduceDone.getYpix(),
					 reduceDone.getPhotometricity(),reduceDone.getSkyBrightness(), 
					 reduceDone.getSaturation());
			// retrieve X/Y pos of brightest object on frame
			brightestObjectXPixel = (double)(reduceDone.getXpix());
			brightestObjectYPixel = (double)(reduceDone.getYpix());
		}
		else
		{
			throw new Exception(this.getClass().getName()+
					    ":reduceExpose:Wrong class returned from EXPOSE_REDUCE:"+
					    instToDPDone.getClass().getName());
		}
	}

	/**
	 * Compute the offset needed to get the specified RA and Dec on the specified pixel.
	 * <ul>
	 * <li>We call getWCSFITS to get a WCSTools handle for the reduced filename.
	 * <li>We call wcsToPixel to find the X,Y binned pixel position of the target RA/Dec 
	 * <li>We free the WCSTools handle.
	 * <li>We call computeXYPixelOffset with the binned pixel coordinates of the target RA/Dec as the target
	 *     pixel position.
	 * </ul>
	 * @return The method returns true if acquisition has been successful, i.e. the difference between the
	 *         target pixel position, and the pixel position containing ther acquisition RA/Dec 
	 *         is less than acquireThreshold arcseconds. Otherwise false is returned.
	 * @exception Exception Thrown if the WCSTools methods fail (getWCSFITS,wcsfree), or binning was zero.
	 * @see #reducedFITSFilename
	 * @see #acquireXPixel
	 * @see #acquireYPixel
	 * @see #acquireRARads
	 * @see #acquireDecRads
	 * @see #acquireRAString
	 * @see #acquireDecString
	 * @see #computeXYPixelOffset
	 * @see #moving
	 * @see #rateTime
	 * @see #raRateRadPerSec
	 * @see #acquireDecString
	 * @see #timeStartExposure
	 * @see ngat.astrometry.WCSTools#getWCSFITS
	 * @see ngat.astrometry.WCSTools#wcsToPixel
	 * @see ngat.astrometry.WCSTools#pixelToWCS
	 * @see ngat.astrometry.WCSTools#wcsfree
	 */
	protected boolean computeRADecOffset() throws Exception
	{
		WCSToolsWorldCoorHandle handle = null;
		WCSToolsCoordinate coordinate = null;
		double raRads,decRads,offsetRARads,offsetDecRads,offsetRADegs,
			offsetRAArcSecs,offsetDecArcSecs,distance;
		String raString = null;
		String decString = null;
		boolean done;

		// check binning is non-zero (division by zero
		if(bin == 0)
			throw new Exception(this.getClass().getName()+":computeRADecOffset:bin was zero.");
		// are we close enough yet?
		done = false;
		handle = WCSTools.getWCSFITS(reducedFITSFilename,3);
		try
		{
			// get pixel of Target
			acquireRAString = Position.formatHMSString(acquireRARads,":");
			acquireDecString = Position.formatDMSString(acquireDecRads,":");
			sprat.log(Logging.VERBOSITY_VERBOSE,
				  ":computeRADecOffset: Target original position: at T=0.0s: "+
				  acquireRAString+", "+acquireDecString);
			double checkRARads = acquireRARads;
			double checkDecRads = acquireDecRads;
			long timeElapsed = timeStartExposure - rateTime;
			if(moving) 
			{
				// Add offset = rate(rad/sec) * timediff to mid-exposure from start (sec)
				checkRARads =  acquireRARads + raRateRadPerSec*((double)timeStartExposure + 
					       (double)exposureLength/2.0 - (double)rateTime)/1000.0;
				checkDecRads = acquireDecRads + decRateRadPerSec*((double)timeStartExposure + 
					       (double)exposureLength/2.0 - (double)rateTime)/1000.0;
				acquireRAString = Position.formatHMSString(checkRARads,":");
				acquireDecString = Position.formatDMSString(checkDecRads,":");
				sprat.log(Logging.VERBOSITY_VERBOSE,
					  ":computeRADecOffset: Moving target position mid-exposure at T="+
					  ((double)timeElapsed/1000.0)+"s: "+acquireRAString+", "+
					  acquireDecString);
			}
			coordinate = WCSTools.wcsToPixel(handle,checkRARads*180.0/Math.PI,
							 checkDecRads*180.0/Math.PI);
			sprat.log(Logging.VERBOSITY_VERBOSE,
				  ":computeRADecOffset:Target Position at mid-exposure at T=:"+
				  ((double)timeElapsed/1000.0)+"s: "+acquireRAString+","+acquireDecString+
				  ") currently at binned pixel position : X:"+
				  coordinate.getXCoordinate()+":Y:"+coordinate.getYCoordinate()+".");
		}
		finally
		{
			WCSTools.wcsFree(handle);
		}
		// compute offset in pixels with object pixel position target RA/Dec
		return computeXYPixelOffset(coordinate.getXCoordinate(),coordinate.getYCoordinate());
	}

	/**
	 * Compute the XY pixel offset between the brightest object on the last reduced frame and the target
	 * pixel position. The computation is done in binned pixels.
	 * The plate scale is currently retrieved from the FITS property file value:"sprat.fits.value.CCDSCALE."+bin.
	 * It is not clear how non-square binning is supported yet, or whether this is the right thing to do.
	 * @param objectXPixel The X Pixel position of the object/position we are trying to acquire onto the 
	 *        acquisition pixel, in binned pixels. This is the brightestObjectXPixel for 
	 *        BRIGHTEST mode acquisitions, and the binned X pixel of the target RA/Dec for WCS mode acuisitions.
	 * @param objectYPixel The Y Pixel position of the object/position we are trying to acquire onto the 
	 *        acquisition pixel, in binned pixels. This is the brightestObjectYPixel for 
	 *        BRIGHTEST mode acquisitions, and the binned Y pixel of the target RA/Dec for WCS mode acuisitions.
	 * @return The method returns true if acquisition has been successful, i.e. the difference between the
	 *         two pixel positions is less than  acquireThreshold
	 *         arcseconds (after taking the plate scale into account). Otherwise false is returned.
	 * @see #acquireXPixel
	 * @see #acquireYPixel
	 * @see #xPixelOffset
	 * @see #yPixelOffset
	 * @see #bin
	 * @see #status
	 * @see SpratStatus#getPropertyDouble
	 */
	protected boolean computeXYPixelOffset(double objectXPixel,double objectYPixel) throws Exception
	{
		boolean done;
		double plateScale;
		double offsetXPixel,offsetYPixel;
		double distancePixels,distanceArcsecs;

		// are we close enough yet?
		done = false;
		// get plate scale from FITS header defaults and current binning
		// Not sure what happens if we use non-square binning.
		plateScale = status.getPropertyDouble("sprat.fits.value.CCDSCALE."+bin);
		sprat.log(Logging.VERBOSITY_VERBOSE,
			  "computeXYPixelOffset:Target Pixel(binned): ("+(acquireXPixel/bin)+", "+(acquireYPixel/bin)+
			  "):Object Pixel(binned): ("+objectXPixel+", "+objectYPixel+"):Plate Scale"+plateScale+".");
		offsetXPixel = objectXPixel-(acquireXPixel/bin);
		offsetYPixel = objectYPixel-(acquireYPixel/bin);
		sprat.log(Logging.VERBOSITY_VERBOSE,
			  "computeXYPixelOffset:We are ("+offsetXPixel+","+offsetYPixel+
			  ") binned pixels away from the correct position.");
		distancePixels = Math.sqrt((offsetXPixel*offsetXPixel)+(offsetYPixel*offsetYPixel));
		distanceArcsecs = distancePixels*plateScale;
		sprat.log(Logging.VERBOSITY_VERBOSE,
			  "computeXYPixelOffset:We are "+distancePixels+" binned pixels and "+
			  distanceArcsecs+" arcseconds away from the correct position.");
		if(distanceArcsecs < acquireThreshold)
		{
			sprat.log(Logging.VERBOSITY_VERBOSE,
				  "computeXYPixelOffset:Finish acquisition: "+distanceArcsecs+
				  " arcseconds  < threshold "+acquireThreshold+" arcseconds.");
			done = true;
		}
		else
		{
			done = false;
			// set new offset for next OFFSET_X_Y
			// OFFSET_X_Y is cumulative, therefore new offset is offset
			xPixelOffset = offsetXPixel;
			yPixelOffset = offsetYPixel;
			sprat.log(Logging.VERBOSITY_VERBOSE,
				  "computeXYPixelOffset:new X/Y offset: ("+xPixelOffset+","+yPixelOffset+
				  ") binned pixels.");
		}
		return done;
	}
}
