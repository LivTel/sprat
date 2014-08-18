// FITSImplementation.java
// $HeadURL$
package ngat.sprat;

import java.net.*;
import java.util.*;

import ngat.fits.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.sprat.ccd.command.*;
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
		int portNumber,returnCode;
		String hostname = null;
		String errorString = null;

		clearCommand = new FitsHeaderClearCommand();
		// configure C comms
		hostname = status.getProperty("sprat.ccd.c.hostname");
		portNumber = status.getPropertyInteger("sprat.ccd.c.port_number");
		clearCommand.setAddress(hostname);
		clearCommand.setPortNumber(portNumber);
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
	 * @see #addFitsHeader
	 */
	public boolean setFitsHeaders(COMMAND command,COMMAND_DONE commandDone)
	{
		String keyword = null;
		String typeString = null;
		String valueString = null;
		boolean done;
		double dvalue;
		int index,ivalue;
		boolean bvalue;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":setFitsHeaders:Started.");
		index = 0;
		done = false;
		while(done == false)
		{
			sprat.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				   ":setFitsHeaders:Looking for keyword index "+index+" in list.");
			keyword = status.getProperty("sprat.fits.keyword."+index);
			if(keyword != null)
			{
				typeString = status.getProperty("sprat.fits.value.type."+keyword);
				if(typeString == null)
				{
					sprat.error(this.getClass().getName()+
						    ":setFitsHeaders:Failed to get value type for keyword:"+keyword);
					commandDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1203);
					commandDone.setErrorString(this.getClass().getName()+
						     ":setFitsHeaders:Failed to get value type for keyword:"+keyword);
					commandDone.setSuccessful(false);
					return false;
				}
				try
				{
					if(typeString.equals("string"))
					{
						valueString = status.getProperty("sprat.fits.value."+keyword);
						addFitsHeader(keyword,valueString);
					}
					else if(typeString.equals("integer"))
					{
						Integer iov = null;

						ivalue = status.getPropertyInteger("sprat.fits.value."+keyword);
						iov = new Integer(ivalue);
						addFitsHeader(keyword,iov);
					}
					else if(typeString.equals("float"))
					{
						Float fov = null;

						dvalue = status.getPropertyDouble("sprat.fits.value."+keyword);
						fov = new Float(dvalue);
						addFitsHeader(keyword,fov);
					}
					else if(typeString.equals("boolean"))
					{
						Boolean bov = null;

						bvalue = status.getPropertyBoolean("sprat.fits.value."+keyword);
						bov = new Boolean(bvalue);
						addFitsHeader(keyword,bov);
					}
					else
					{
						sprat.error(this.getClass().getName()+
							    ":setFitsHeaders:Unknown value type "+typeString+
							    " for keyword:"+keyword);
						commandDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1204);
						commandDone.setErrorString(this.getClass().getName()+
								    ":setFitsHeaders:Unknown value type "+typeString+
							     " for keyword:"+keyword);
						commandDone.setSuccessful(false);
						return false;
					}
				}
				catch(Exception e)
				{
					sprat.error(this.getClass().getName()+
						    ":setFitsHeaders:Failed to add value for keyword:"+keyword,e);
					commandDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1206);
					commandDone.setErrorString(this.getClass().getName()+
						     ":setFitsHeaders:Failed to add value for keyword:"+keyword+":"+e);
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
			done.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1205);
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
			done.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1207);
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
		int portNumber,returnCode;
		String hostname = null;
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
		hostname = status.getProperty("sprat.ccd.c.hostname");
		portNumber = status.getPropertyInteger("sprat.ccd.c.port_number");
		addCommand.setAddress(hostname);
		addCommand.setPortNumber(portNumber);
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
}

