// FITSImplementation.java
// $HeadURL$
package ngat.sprat;

import java.net.*;
import java.util.*;

import ngat.fits.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.sprat.ccd.command.*;
import ngat.sprat.mechanism.command.*;
import ngat.util.logging.*;

/**
 * This class provides the generic implementation of commands that write FITS files. It extends those that
 * use the hardware libraries as these are needed to generate FITS files.
 * @see HardwareImplementation
 * @author Chris Mottram
 * @version $Revision$
 */
public class FITSImplementation extends HardwareImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * This is a copy of the "OBJECT" FITS Header value retrieved from the RCS by getFitsHeadersFromISS.
	 * This is used to modify the "OBJECT" FITS Header value for ARCs etc....
	 * @see #getFitsHeadersFromISS
	 */
	protected String objectName = null;

	/**
	 * This method calls the super-classes method. 
	 * @param command The command to be implemented.
	 */
	public void init(COMMAND command)
	{
		super.init(command);
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
	 * This routine clears the current set of FITS headers. 
	 * @exception UnknownHostException Thrown if the C layer hostname address it not known.
	 * @exception Exception Thrown if sending the command fails.
	 * @see HardwareImplementation#ccdCLayerHostname
	 * @see HardwareImplementation#ccdCLayerPortNumber
	 * @see ngat.sprat.SpratStatus#getProperty
	 * @see ngat.sprat.SpratStatus#getPropertyInteger
	 * @see ngat.sprat.ccd.command.FitsHeaderClearCommand
	 * @see ngat.sprat.ccd.command.FitsHeaderClearCommand#setAddress
	 * @see ngat.sprat.ccd.command.FitsHeaderClearCommand#setPortNumber
	 * @see ngat.sprat.ccd.command.FitsHeaderClearCommand#getParsedReplyOK
	 * @see ngat.sprat.ccd.command.FitsHeaderClearCommand#getReturnCode
	 * @see ngat.sprat.ccd.command.FitsHeaderClearCommand#getParsedReply
	 */
	public void clearFitsHeaders() throws UnknownHostException, Exception
	{
		FitsHeaderClearCommand clearCommand = null;
		int returnCode;
		String errorString = null;

		clearCommand = new FitsHeaderClearCommand();
		// configure C comms
		clearCommand.setAddress(ccdCLayerHostname);
		clearCommand.setPortNumber(ccdCLayerPortNumber);
		// actually send the command to the C layer
		clearCommand.sendCommand();
		// check the parsed reply
		if(clearCommand.getParsedReplyOK() == false)
		{
			returnCode = clearCommand.getReturnCode();
			errorString = clearCommand.getParsedReply();
			sprat.log(Logging.VERBOSITY_TERSE,"clearFitsHeaders:Command failed with return code "+
				  returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
					    ":clearFitsHeaders:Command failed with return code "+returnCode+
					    " and error string:"+errorString);
		}
	}

	/**
	 * This routine gets a set of FITS header from a config file. The retrieved FITS headers are added to the 
	 * C layer. The "sprat.fits.keyword.<n>" properties is queried in ascending order of <n> to find keywords.
	 * The "sprat.fits.value.<keyword>" property contains the value of the keyword.
	 * The value's type is retrieved from the property "sprat.fits.value.type.<keyword>", 
	 * which should comtain one of the following values: boolean|float|integer|string.
	 * The addFitsHeader method is then called to actually add the FITS header to the C layer.
	 * @param command The command being implemented that made this call to the ISS. This is used
	 * 	for error logging.
	 * @param commandDone A COMMAND_DONE subclass specific to the command being implemented. If an
	 * 	error occurs the relevant fields are filled in with the error.
	 * @return The routine returns a boolean to indicate whether the operation was completed
	 *  	successfully.
	 * @see #setFitsHeader
	 */
	public boolean setFitsHeaders(COMMAND command,COMMAND_DONE commandDone)
	{
		String keyword = null;
		boolean done;
		int index,mirrorPosition,slitPosition,grismPosition,rotationPosition,binx,biny;
		boolean isSpectralFitsHeader;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":setFitsHeaders:Started.");
		// get mechanism positions
		try
		{
		// where is the mirror?
			mirrorPosition = getMirrorPosition();
		// where is the slit?
			slitPosition = getSlitPosition();
		// where is the grism?
			grismPosition = getGrismPosition();
		// what is the grism rotation position?
			rotationPosition = getRotationPosition();
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":setFitsHeaders:Failed to get mechanism positions.",e);
			commandDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+300);
			commandDone.setErrorString("setFitsHeaders:Failed to get mechanism positions:"+e.toString());
			commandDone.setSuccessful(false);
			return false;
		}
		// This is a spectral FITS header if the grism (dispersing element) is in the beam
		isSpectralFitsHeader = (grismPosition == GrismCommand.POSITION_IN);
		// get ccd binning
		try
		{
			binx = getStatusMultrunBinX();
			biny = getStatusMultrunBinY();
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":setFitsHeaders:Failed to get CCD binning.",e);
			commandDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+301);
			commandDone.setErrorString("setFitsHeaders:Failed to get CCD binning:"+e.toString());
			commandDone.setSuccessful(false);
			return false;
		}
		index = 0;
		done = false;
		while(done == false)
		{
			sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				   ":setFitsHeaders:Looking for keyword index "+index+" in list.");
			keyword = status.getProperty("sprat.fits.keyword."+index);
			if(keyword != null)
			{
				try
				{
					setFitsHeader(keyword,isSpectralFitsHeader,
						      mirrorPosition,slitPosition,grismPosition,
						      rotationPosition,binx,biny);
				}
				catch(Exception e)
				{
					sprat.error(this.getClass().getName()+
						    ":setFitsHeaders:setFitsHeader failed for keyword:"+keyword,e);
					commandDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+302);
					commandDone.setErrorString(this.getClass().getName()+
						     ":setFitsHeaders:setFitsHeader failed for keyword:"+keyword+
								   ":"+e);
					commandDone.setSuccessful(false);
					return false;
				}
				// increment index
				index++;
			}
			else
				done = true;
		}// end while
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":setFitsHeaders:Finished.");
		return true;
	}

	/**
	 * Method to retrieve the value of the FITS header from the config file, taking into account
	 * the current mechanism and CCD configuration, and pass the data to the C layer.
	 * getFITSValueKeywordString is called to generate the Java property keyword string the FITS keyword value
	 * is stored under. If getFITSValueKeywordString returns "internal", getFitsKeywordInternalValue is called
	 * to generate the value from some internal data/mechanism/CCD status.
	 * addFitsHeader is then called to add the keyword/value to the list.
	 * @param keyword The FITS header keyword to set.#
	 * @param isSpectralFitsHeader Whether we are writing FITS headers for Spectral data (true) or imaging data
	 *        (false).
	 * @param mirrorPosition The current mirror position. 
	 *        One of MirrorCommand.POSITION_IN | MirrorCommand.POSITION_OUT | MirrorCommand.POSITION_ERROR | 
	 *        MirrorCommand.POSITION_UNKNOWN .
	 * @param slitPosition The current slit position.
	 *        One of SlitCommand.POSITION_IN | SlitCommand.POSITION_OUT | SlitCommand.POSITION_ERROR | 
	 *        SlitCommand.POSITION_UNKNOWN .
	 * @param grismPosition The current grism position.
	 *        One of GrismCommand.POSITION_IN | GrismCommand.POSITION_OUT | GrismCommand.POSITION_ERROR | 
	 *        GrismCommand.POSITION_UNKNOWN .
	 * @param rotationPosition The current rotation position of the grism. One of 0 | 1 | 
	 *        RotationCommand.POSITION_ERROR | RotationCommand.POSITION_UNKNOWN
	 * @param binx The current X binning position.
	 * @param biny The current Y binning position.
	 * @exception Exception Thrown if an error occurs.
	 * @see #getFITSValueKeywordString
	 * @see #getFitsKeywordInternalValue
	 * @see #addFitsHeader
	 * @see #status
	 * @see SpratStatus#getProperty
	 * @see SpratStatus#getPropertyBoolean
	 * @see SpratStatus#getPropertyInteger
	 * @see SpratStatus#getPropertyDouble
	 */
	protected void setFitsHeader(String keyword,boolean isSpectralFitsHeader,
				     int mirrorPosition,int slitPosition,int grismPosition,
				     int rotationPosition,int binx,int biny) throws Exception
	{
		String typeString = null;
		String varyByString  = null;
		String valueKeywordString = null;
		String valueString = null;
		boolean done;
		double dvalue;
		int ivalue;
		boolean isFitsKeywordSpectral,bvalue;

		sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":setFitsHeader:Adding "+keyword+".");
		// get type of FITS keyword (string | integer | float | boolean)
		typeString = status.getProperty("sprat.fits.value.type."+keyword);
		if(typeString == null)
		{
			sprat.error(this.getClass().getName()+
				    ":setFitsHeaders:Failed to get value type for keyword:"+keyword);
			throw new Exception(this.getClass().getName()+
					    "setFitsHeader:Failed to get value type for keyword:"+keyword);
		}
		// get whether the FITS keyword is Spectral only or not.
		isFitsKeywordSpectral = status.getPropertyBoolean("sprat.fits.value.isspectral."+keyword);
		// get what the value of the keyword might vary by. This value can be null!
		varyByString = status.getProperty("sprat.fits.value.varyby."+keyword);
		// compute what the keyword containing the FITS keyword's value will be from the varyby string
		valueKeywordString = getFITSValueKeywordString(keyword,varyByString,mirrorPosition,slitPosition,
							       grismPosition,rotationPosition,binx,biny);
		// add FITS header if it is not spectral, or it is spectral and the header is for spectral data
		if((isFitsKeywordSpectral == false)||(isSpectralFitsHeader&&isFitsKeywordSpectral))
		{
			sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":setFitsHeader:Keyword "+
				  keyword+":type: "+typeString+":retrieve value from:"+valueKeywordString+".");
			if(typeString.equals("string"))
			{
				if(valueKeywordString.equals("internal") == false)
				{
					valueString = status.getProperty(valueKeywordString);
				}
				else
					valueString = (String)(getFitsKeywordInternalValue(keyword));
				addFitsHeader(keyword,valueString);
			}
			else if(typeString.equals("integer"))
			{
				Integer iov = null;
				
				if(valueKeywordString.equals("internal") == false)
				{
					ivalue = status.getPropertyInteger(valueKeywordString);
					iov = new Integer(ivalue);
				}
				else
					iov = (Integer)(getFitsKeywordInternalValue(keyword));
				addFitsHeader(keyword,iov);
			}
			else if(typeString.equals("float"))
			{
				Float fov = null;
				
				dvalue = status.getPropertyDouble(valueKeywordString);
				fov = new Float(dvalue);
				addFitsHeader(keyword,fov);
			}
			else if(typeString.equals("double"))
			{
				Double dov = null;

				if(valueKeywordString.equals("internal") == false)
				{
					dvalue = status.getPropertyDouble(valueKeywordString);
					dov = new Double(dvalue);
				}
				else
					dov = (Double)(getFitsKeywordInternalValue(keyword));
				addFitsHeader(keyword,dov);
			}
			else if(typeString.equals("boolean"))
			{
				Boolean bov = null;
				
				bvalue = status.getPropertyBoolean(valueKeywordString);
				bov = new Boolean(bvalue);
				addFitsHeader(keyword,bov);
			}
			else
			{
				sprat.error(this.getClass().getName()+
					    ":setFitsHeaders:Unknown value type "+typeString+
					    " for keyword:"+keyword);
				throw new Exception(this.getClass().getName()+":setFitsHeaders:Unknown value type "+
						    typeString+" for keyword:"+keyword);
			}
		}// if keyword should be written
	}

	/**
	 * Given a FITS header keyword, construct a properties keyword that will retrieve it's value from the 
	 * properties file,
	 * given the specified list of things it can vary by.
	 * @param keyword The FITS header keyword.
	 * @param varyByString A string (which may be null or blank), describing what things cause the value of the 
	 *        specified FITS keyword to change.  Legal values : <blank> | mirror | grism | grismrot | slit | 
	 *        slit.grism.grismrot | binx | biny | binxbiny | internal.
	 * @param mirrorPosition The current mirror position. 
	 *        One of MirrorCommand.POSITION_IN | MirrorCommand.POSITION_OUT | MirrorCommand.POSITION_ERROR | 
	 *        MirrorCommand.POSITION_UNKNOWN .
	 * @param slitPosition The current slit position.
	 *        One of SlitCommand.POSITION_IN | SlitCommand.POSITION_OUT | SlitCommand.POSITION_ERROR | 
	 *        SlitCommand.POSITION_UNKNOWN .
	 * @param grismPosition The current grism position.
	 *        One of GrismCommand.POSITION_IN | GrismCommand.POSITION_OUT | GrismCommand.POSITION_ERROR | 
	 *        GrismCommand.POSITION_UNKNOWN .
	 * @param rotationPosition The current rotation position of the grism. One of 0 | 1 | 
	 *        RotationCommand.POSITION_ERROR | RotationCommand.POSITION_UNKNOWN
	 * @param binx The current X binning position.
	 * @param biny The current Y binning position.
	 * @return A string, a keyword that exists in the property file and whose value can be retrieved.
	 *         Or the string "internal" for FITS values that are not stored in a config file.
	 * @exception Exception Thrown if an error occurs.
	 * @see ngat.sprat.mechanism.command.MirrorCommand#positionToString
	 * @see ngat.sprat.mechanism.command.GrismCommand#positionToString
	 * @see ngat.sprat.mechanism.command.SlitCommand#positionToString
	 */
	protected String getFITSValueKeywordString(String keyword,String varyByString,
						   int mirrorPosition,int slitPosition,int grismPosition,
						   int rotationPosition,int binx,int biny) throws Exception
	{
		String valueKeywordString = null;
		
		// cope with keywords whose values do not vary, is the varby string null or blank?
		if(varyByString == null)
		{
			valueKeywordString = new String("sprat.fits.value."+keyword);
			return valueKeywordString;
		}
		if(varyByString.equals(""))
		{
			valueKeywordString = new String("sprat.fits.value."+keyword);
			return valueKeywordString;
		}
		// if the value varies 'internally' (CCD temp, envionment temp, humidity) return something that
		// recognises this.
		if(varyByString.equals("internal"))
		{
			return new String("internal");
		}
		// based on what the keywords value's vary by, construct a suitable value property keyword string to 
		// return.
		else if(varyByString.equals("mirror"))
		{
			switch(mirrorPosition)
			{
				case MirrorCommand.POSITION_IN:
					valueKeywordString = new String("sprat.fits.value."+keyword+".in");
					break;
				case MirrorCommand.POSITION_OUT:
					valueKeywordString = new String("sprat.fits.value."+keyword+".out");
					break;
				default:
					throw new Exception(this.getClass().getName()+
							    ":getFITSValueKeywordString:keyword "+
							    keyword+" varys by "+varyByString+
							    " but mirror in illegal position:"+
							    MirrorCommand.positionToString(mirrorPosition));
			}
			return valueKeywordString;
		}
		else if(varyByString.equals("grism"))
		{
			switch(grismPosition)
			{
				case GrismCommand.POSITION_IN:
					valueKeywordString = new String("sprat.fits.value."+keyword+".in");
					break;
				case GrismCommand.POSITION_OUT:
					valueKeywordString = new String("sprat.fits.value."+keyword+".out");
					break;
				default:
					throw new Exception(this.getClass().getName()+
							    ":getFITSValueKeywordString:keyword "+
							    keyword+" varys by "+varyByString+
							    " but grism in illegal position:"+
							    GrismCommand.positionToString(grismPosition));
			}
			return valueKeywordString;
		}
		else if(varyByString.equals("slit"))
		{
			switch(slitPosition)
			{
				case SlitCommand.POSITION_IN:
					valueKeywordString = new String("sprat.fits.value."+keyword+".in");
					break;
				case SlitCommand.POSITION_OUT:
					valueKeywordString = new String("sprat.fits.value."+keyword+".out");
					break;
				default:
					throw new Exception(this.getClass().getName()+
							    ":getFITSValueKeywordString:keyword "+
							    keyword+" varys by "+varyByString+
							    " but slit in illegal position:"+
							    SlitCommand.positionToString(slitPosition));
			}
			return valueKeywordString;
		}
		else if(varyByString.equals("grismrot"))
		{
			switch(rotationPosition)
			{
				case 0:
					valueKeywordString = new String("sprat.fits.value."+keyword+".0");
					break;
				case 1:
					valueKeywordString = new String("sprat.fits.value."+keyword+".1");
					break;
				default:
					throw new Exception(this.getClass().getName()+
							    ":getFITSValueKeywordString:keyword "+
							    keyword+" varys by "+varyByString+
							    " but grism rotation in illegal position:"+
							    rotationPosition);
			}
			return valueKeywordString;
		}
		else if(varyByString.equals("slit.grism.grismrot"))
		{
			switch(slitPosition)
			{
				case SlitCommand.POSITION_IN:
					valueKeywordString = new String("sprat.fits.value."+keyword+".in");
					break;
				case SlitCommand.POSITION_OUT:
					valueKeywordString = new String("sprat.fits.value."+keyword+".out");
					break;
				default:
					throw new Exception(this.getClass().getName()+
							    ":getFITSValueKeywordString:keyword "+
							    keyword+" varys by "+varyByString+
							    " but slit in illegal position:"+
							    SlitCommand.positionToString(slitPosition));
			}
			switch(grismPosition)
			{
				case GrismCommand.POSITION_IN:
					valueKeywordString = new String(valueKeywordString+".in");
					break;
				case GrismCommand.POSITION_OUT:
					valueKeywordString = new String(valueKeywordString+".out");
					break;
				default:
					throw new Exception(this.getClass().getName()+
							    ":getFITSValueKeywordString:keyword "+
							    keyword+" varys by "+varyByString+
							    " but grism in illegal position:"+
							    GrismCommand.positionToString(grismPosition));
			}
			valueKeywordString = new String(valueKeywordString+"."+rotationPosition);
			return valueKeywordString;
		}
		else if(varyByString.equals("binx"))
		{
			valueKeywordString = new String("sprat.fits.value."+keyword+"."+binx);
			return valueKeywordString;
		}
		else if(varyByString.equals("biny"))
		{
			valueKeywordString = new String("sprat.fits.value."+keyword+"."+biny);
			return valueKeywordString;
		}
		else if(varyByString.equals("binxbiny"))
		{
			valueKeywordString = new String("sprat.fits.value."+keyword+"."+binx+"."+biny);
			return valueKeywordString;
		}
		else
		{
			throw new Exception(this.getClass().getName()+":getFITSValueKeywordString:keyword "+
					    keyword+" varys by unsupported "+varyByString+" .");
		}
	}

	/**
	 * Get the value for a FITS header keyword that has a "varyby" setting of "internal".
	 * Current keywords supported: CONFIGID | CONFNAME | CCDATEMP | ENVTEMP0 | ENVTEMP1 | ENVHUM0 | ENVHUM1 |
	 * ORIENT1 | ORIENT2 | ORIENT3 | LAMP1SET | LAMP2SET.
	 * @param keyword The keyword to retrieve a value for.
	 * @return An internally generated value.
	 * @exception Exception Thrown if the keyword is not known to have an internal value by this method.
	 * @see #status
	 * @see HardwareImplementation#getCCDTemperature
	 * @see HardwareImplementation#getMechanismTemperature
	 * @see HardwareImplementation#getHumidity
	 * @see HardwareImplementation#getGyroPositionX
	 * @see HardwareImplementation#getGyroPositionY
	 * @see HardwareImplementation#getGyroPositionZ
	 * @see HardwareImplementation#getArcLampIsOn
	 * @see HardwareImplementation#getWLampIsOn
	 * @see SpratStatus#getConfigId
	 * @see SpratStatus#getConfigName
	 */
	protected Object getFitsKeywordInternalValue(String keyword) throws Exception
	{
		if(keyword.equals("CONFIGID"))
		{
			return new Integer(status.getConfigId());
		}
		else if(keyword.equals("CONFNAME"))
		{
			return status.getConfigName();
		}
		else if(keyword.equals("CCDATEMP"))
		{
			return new Double(getCCDTemperature());
		}
		else if(keyword.equals("ENVTEMP0"))
		{
			return new Double(getMechanismTemperature(0));
		}
		else if(keyword.equals("ENVTEMP1"))
		{
			return new Double(getMechanismTemperature(1));
		}
		else if(keyword.equals("ENVHUM0"))
		{
			return new Double(getHumidity(0));
		}
		else if(keyword.equals("ENVHUM1"))
		{
			return new Double(getHumidity(1));
		}
		else if(keyword.equals("ORIENT1"))
		{
			return new Double(getGyroPositionX());
		}
		else if(keyword.equals("ORIENT2"))
		{
			return new Double(getGyroPositionY());
		}
		else if(keyword.equals("ORIENT3"))
		{
			return new Double(getGyroPositionZ());
		}
		else if(keyword.equals("LAMP1SET"))
		{
			return new Boolean(getArcLampIsOn());
		}
		else if(keyword.equals("LAMP2SET"))
		{
			return new Boolean(getWLampIsOn());
		}
		throw new Exception(this.getClass().getName()+
				    ":getFitsKeywordInternalValue:Failed to find internal handling procedure "+
				    "for keyword:"+keyword);
	}

	/**
	 * This routine tries to get a set of FITS headers for an exposure, by issuing a GET_FITS command
	 * to the ISS. The results from this command are put into the C layers list of FITS headers by calling
	 * addISSFitsHeaderList.
	 * If an error occurs the done objects field's can be set to record the error.
	 * The value of the OBJECT keyword is saved in the objectName, to be used when modifying the object name
	 * later (i.e. for ARC frames).
	 * @param command The command being implemented that made this call to the ISS. This is used
	 * 	for error logging.
	 * @param done A COMMAND_DONE subclass specific to the command being implemented. If an
	 * 	error occurs the relevant fields are filled in with the error.
	 * @return The routine returns a boolean to indicate whether the operation was completed
	 *  	successfully.
	 * @see #addISSFitsHeaderList
	 * @see #getObjectName
	 * @see Sprat#sendISSCommand
	 * @see Sprat#getStatus
	 * @see SpratStatus#getPropertyInteger
	 */
	public boolean getFitsHeadersFromISS(COMMAND command,COMMAND_DONE done)
	{
		INST_TO_ISS_DONE instToISSDone = null;
		ngat.message.ISS_INST.GET_FITS getFits = null;
		ngat.message.ISS_INST.GET_FITS_DONE getFitsDone = null;
		Vector list = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			  ":getFitsHeadersFromISS:Started.");
		getFits = new ngat.message.ISS_INST.GET_FITS(command.getId());
		instToISSDone = sprat.sendISSCommand(getFits,serverConnectionThread);
		if(instToISSDone.getSuccessful() == false)
		{
			sprat.error(this.getClass().getName()+":getFitsHeadersFromISS:"+
				    command.getClass().getName()+":"+instToISSDone.getErrorString());
			done.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+303);
			done.setErrorString(instToISSDone.getErrorString());
			done.setSuccessful(false);
			return false;
		}
	// Get the returned FITS header information into the FitsHeader object.
		getFitsDone = (ngat.message.ISS_INST.GET_FITS_DONE)instToISSDone;
	// extract specific FITS headers and add them to the C layers list
		list = getFitsDone.getFitsHeader();
		try
		{
			addISSFitsHeaderList(list);
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+
				    ":getFitsHeadersFromISS:addISSFitsHeaderList failed.",e);
			done.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+304);
			done.setErrorString(this.getClass().getName()+
					    ":getFitsHeadersFromISS:addISSFitsHeaderList failed:"+e);
			done.setSuccessful(false);
			return false;
		}
		// retrieve the OBJECT FITS header card image
		// we will need to modify this in odd ways for ARC obs/calBefore/calAfter
		sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			  ":getFitsHeadersFromISS:retrieving OBJECT value.");
		getObjectName(list);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			  ":getFitsHeadersFromISS:finished.");
		return true;
	}

	/**
	 * Pass the GET_FITS headers returned from the ISS to the C layer.
	 * @param list A Vector of FitsHeaderCardImage instances to pass into the C layer.
	 * @exception Exception Thrown if addFitsHeader fails.
	 * @see #addFitsHeader
	 * @see ngat.fits.FitsHeaderCardImageKeywordComparator
	 * @see ngat.fits.FitsHeaderCardImage
	 */
	protected void addISSFitsHeaderList(List list) throws Exception
	{
		FitsHeaderCardImage cardImage = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":addISSFitsHeaderList:started.");
		// iterate over keywords to copy
		for(int index = 0; index < list.size(); index ++)
		{
			cardImage = (FitsHeaderCardImage)(list.get(index));
			sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				  ":addISSFitsHeaderList:Adding "+cardImage.getKeyword()+" to C layer.");
			addFitsHeader(cardImage.getKeyword(),cardImage.getValue());
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":addISSFitsHeaderList:finished.");
	}

	/**
	 * Method to add the specified FITS header to the C layers list of
	 * FITS headers. The C layer machine/port specification is retrieved from the "sprat.ccd.c.hostname"
	 * and "sprat.ccd.c.port_number" properties. An instance of FitsHeaderAddCommand is used to transmit the data.
	 * @param keyword The FITS headers keyword.
	 * @param value The FITS headers value - an object of class String,Integer,Float,Double,Boolean,Date.
	 * @exception Exception Thrown if the FitsHeaderAddCommand internally errors, or the return code indicates a
	 *            failure.
	 * @see #status
	 * @see #dateFitsFieldToString
	 * @see ngat.sprat.SpratStatus#getProperty
	 * @see ngat.sprat.SpratStatus#getPropertyInteger
	 * @see ngat.sprat.ccd.command.FitsHeaderAddCommand
	 * @see ngat.sprat.ccd.command.FitsHeaderAddCommand#setAddress
	 * @see ngat.sprat.ccd.command.FitsHeaderAddCommand#setPortNumber
	 * @see ngat.sprat.ccd.command.FitsHeaderAddCommand#setCommand
	 * @see ngat.sprat.ccd.command.FitsHeaderAddCommand#getParsedReplyOK
	 * @see ngat.sprat.ccd.command.FitsHeaderAddCommand#getReturnCode
	 * @see ngat.sprat.ccd.command.FitsHeaderAddCommand#getParsedReply
	 */
	protected void addFitsHeader(String keyword,Object value) throws Exception
	{
		FitsHeaderAddCommand addCommand = null;
		int returnCode;
		String errorString = null;

		if(keyword == null)
		{
			throw new NullPointerException(this.getClass().getName()+":addFitsHeader:keyword was null.");
		}
		if(value == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":addFitsHeader:value was null for keyword:"+keyword);
		}
		addCommand = new FitsHeaderAddCommand();
		// configure C comms
		addCommand.setAddress(ccdCLayerHostname);
		addCommand.setPortNumber(ccdCLayerPortNumber);
		// set command parameters
		if(value instanceof String)
		{
			sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				  ":addFitsHeader:Adding keyword "+keyword+" with String value "+value+".");
			addCommand.setCommand(keyword,(String)value);
		}
		else if(value instanceof Integer)
		{
			sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				  ":addFitsHeader:Adding keyword "+keyword+" with integer value "+
				  ((Integer)value).intValue()+".");
			addCommand.setCommand(keyword,((Integer)value).intValue());
		}
		else if(value instanceof Float)
		{
			sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				  ":addFitsHeader:Adding keyword "+keyword+" with float value "+
				  ((Float)value).doubleValue()+".");
			addCommand.setCommand(keyword,((Float)value).doubleValue());
		}
		else if(value instanceof Double)
		{
			sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				  ":addFitsHeader:Adding keyword "+keyword+" with double value "+
				  ((Double)value).doubleValue()+".");
		        addCommand.setCommand(keyword,((Double)value).doubleValue());
		}
		else if(value instanceof Boolean)
		{
			sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				  ":addFitsHeader:Adding keyword "+keyword+" with boolean value "+
				  ((Boolean)value).booleanValue()+".");
			addCommand.setCommand(keyword,((Boolean)value).booleanValue());
		}
		else if(value instanceof Date)
		{
			sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				  ":addFitsHeader:Adding keyword "+keyword+" with date value "+
				  dateFitsFieldToString((Date)value)+".");
			addCommand.setCommand(keyword,dateFitsFieldToString((Date)value));
		}
		else
		{
			throw new IllegalArgumentException(this.getClass().getName()+
							   ":addFitsHeader:value had illegal class:"+
							   value.getClass().getName());
		}
		// actually send the command to the C layer
		addCommand.sendCommand();
		// check the parsed reply
		if(addCommand.getParsedReplyOK() == false)
		{
			returnCode = addCommand.getReturnCode();
			errorString = addCommand.getParsedReply();
			sprat.log(Logging.VERBOSITY_TERSE,"addFitsHeader:Command failed with return code "+
				  returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
					    ":addFitsHeader:Command failed with return code "+returnCode+
					    " and error string:"+errorString);
		}
	}

	/**
	 * Retrieve the value of the "OBJECT" keyword from a List (Vector) instance containing
	 * FitsHeaderCardImage instances.
	 * @param list A list/vector containing instances of FitsHeaderCardImage.
	 * @see #objectName
	 */
	protected void getObjectName(List list)
	{
		FitsHeaderCardImage cardImage = null;
		boolean found;
		int index;

		index = 0;
		found = false;
		while((index < list.size())&&(found == false))
		{
			cardImage = (FitsHeaderCardImage)(list.get(index));
			if(cardImage != null)
			{
				if(cardImage.getKeyword().equals("OBJECT"))
				{
					objectName = (String)(cardImage.getValue());
					found = true;
				}
			}
			index++;
		}
	}

	/**
	 * Get the X binning factor used for Multruns.. 
	 * An instance of StatusMultrunBinXCommand is used to send the command
	 * to the CCD C layer, using ccdCLayerHostname and ccdCLayerPortNumber. 
	 * @return The X binning factor is returned.
	 * @exception Exception Thrown if an error occurs.
	 * @see HardwareImplementation#ccdCLayerHostname
	 * @see HardwareImplementation#ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.StatusMultrunBinXCommand
	 */
	protected int getStatusMultrunBinX() throws Exception
	{
		StatusMultrunBinXCommand statusCommand = null;
		int returnCode,bin;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getStatusMultrunBinX:started.");
		statusCommand = new StatusMultrunBinXCommand();
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
				  "getStatusMultrunBinX:command failed with return code "+
				  returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
				 ":getStatusMultrunBinX:command failed with return code "+
					    returnCode+" and error string:"+errorString);
		}
		bin = statusCommand.getBinX();
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getStatusMultrunBinX:finished with X binning:"+bin);
		return bin;
	}

	/**
	 * Get the Y binning factor used for Multruns.. 
	 * An instance of StatusMultrunBinYCommand is used to send the command
	 * to the CCD C layer, using ccdCLayerHostname and ccdCLayerPortNumber. 
	 * @return The Y binning factor is returned.
	 * @exception Exception Thrown if an error occurs.
	 * @see HardwareImplementation#ccdCLayerHostname
	 * @see HardwareImplementation#ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.StatusMultrunBinYCommand
	 */
	protected int getStatusMultrunBinY() throws Exception
	{
		StatusMultrunBinYCommand statusCommand = null;
		int returnCode,bin;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getStatusMultrunBinY:started.");
		statusCommand = new StatusMultrunBinYCommand();
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
				  "getStatusMultrunBinY:command failed with return code "+
				  returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
					    ":getStatusMultrunBinY:command failed with return code "+
					    returnCode+" and error string:"+errorString);
		}
		bin = statusCommand.getBinY();
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"getStatusMultrunBinY:finished with Y binning:"+bin);
		return bin;
	}


}

