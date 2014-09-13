// CALIBRATEImplementation.java
// $HeadURL$
package ngat.sprat;

import java.lang.String;
import java.io.IOException;
import java.util.List;

import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.message.INST_DP.*;
import ngat.sprat.ccd.command.*;
import ngat.util.logging.*;

/**
 * This class provides the generic implementation for CALIBRATE commands sent to a server using the
 * Java Message System. It extends FITSImplementation, as CALIBRATE commands needs access to
 * resources to move mechanisms and write FITS images.
 * @see FITSImplementation
 * @author Chris Mottram
 * @version $Revision$
 */
public class CALIBRATEImplementation extends FITSImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The list of FITS filenames returned by the multbias / multdark command.
	 */
	protected List<String> filenameList = null;
	/**
	 * The multrun number used for FITS filenames for this command.
	 */
	protected int multrunNumber;

	/**
	 * This method gets the CALIBRATE command's acknowledge time. It returns the server connection 
	 * threads min acknowledge time. This method should be over-written in sub-classes.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see SpratTCPServerConnectionThread#getMinAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(serverConnectionThread.getMinAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method is a generic implementation for the EXPOSE command, that does nothing.
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
	       	// do nothing 
		CALIBRATE_DONE calibrateDone = new CALIBRATE_DONE(command.getId());

		calibrateDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
		calibrateDone.setErrorString("");
		calibrateDone.setSuccessful(true);
		return calibrateDone;
	}

	/**
	 * Send the multbias command to the C layer.
	 * @param exposureCount The number of bias frames to generate.
	 * @exception Exception Thrown if an error occurs.
	 * @see #filenameList
	 * @see #multrunNumber
	 * @see HardwareImplementation#ccdCLayerHostname
	 * @see HardwareImplementation#ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.MultBiasCommand
	 * @see ngat.sprat.ccd.command.MultBiasCommand#setAddress
	 * @see ngat.sprat.ccd.command.MultBiasCommand#setPortNumber
	 * @see ngat.sprat.ccd.command.MultBiasCommand#setCommand
	 * @see ngat.sprat.ccd.command.MultBiasCommand#sendCommand
	 * @see ngat.sprat.ccd.command.MultBiasCommand#getParsedReplyOK
	 * @see ngat.sprat.ccd.command.MultBiasCommand#getReturnCode
	 * @see ngat.sprat.ccd.command.MultBiasCommand#getParsedReply
	 * @see ngat.sprat.ccd.command.MultBiasCommand#getMultrunNumber
	 * @see ngat.sprat.ccd.command.MultBiasCommand#getFilename
	 */
	protected void sendMultBiasCommand(int exposureCount) throws Exception
	{
		MultBiasCommand command = null;
		int returnCode;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendBiasCommand:Started.");
		command = new MultBiasCommand();
		// configure C comms
		command.setAddress(ccdCLayerHostname);
		command.setPortNumber(ccdCLayerPortNumber);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendBiasCommand:hostname = "+ccdCLayerHostname+
			   " :port number = "+ccdCLayerPortNumber+".");
		command.setCommand(exposureCount);
		// actually send the command to the C layer
		command.sendCommand();
		// check the parsed reply
		if(command.getParsedReplyOK() == false)
		{
			returnCode = command.getReturnCode();
			errorString = command.getParsedReply();
			sprat.log(Logging.VERBOSITY_TERSE,
				   "sendMultBiasCommand:bias command failed with return code "+
				   returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
					    ":sendMultBiasCommand:Command failed with return code "+returnCode+
					    " and error string:"+errorString);
		}
		// extract data from successful reply.
		multrunNumber = command.getMultrunNumber();
		filenameList = command.getFilenameList();
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultBiasCommand:Finished.");
	}

	/**
	 * Send the multdark command to the C layer.
	 * @param exposureLength The exposure length of each dark frame in milliseconds.
	 * @param exposureCount The number of dark frames to generate.
	 * @exception Exception Thrown if an error occurs.
	 * @see #filenameList
	 * @see #multrunNumber
	 * @see HardwareImplementation#ccdCLayerHostname
	 * @see HardwareImplementation#ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.MultDarkCommand
	 * @see ngat.sprat.ccd.command.MultDarkCommand#setAddress
	 * @see ngat.sprat.ccd.command.MultDarkCommand#setPortNumber
	 * @see ngat.sprat.ccd.command.MultDarkCommand#setCommand
	 * @see ngat.sprat.ccd.command.MultDarkCommand#sendCommand
	 * @see ngat.sprat.ccd.command.MultDarkCommand#getParsedReplyOK
	 * @see ngat.sprat.ccd.command.MultDarkCommand#getReturnCode
	 * @see ngat.sprat.ccd.command.MultDarkCommand#getParsedReply
	 * @see ngat.sprat.ccd.command.MultDarkCommand#getMultrunNumber
	 * @see ngat.sprat.ccd.command.MultDarkCommand#getFilename
	 */
	protected void sendMultDarkCommand(int exposureLength,int exposureCount) throws Exception
	{
		MultDarkCommand command = null;
		int returnCode;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendDarkCommand:Started.");
		command = new MultDarkCommand();
		// configure C comms
		command.setAddress(ccdCLayerHostname);
		command.setPortNumber(ccdCLayerPortNumber);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendDarkCommand:hostname = "+ccdCLayerHostname+
			   " :port number = "+ccdCLayerPortNumber+".");
		command.setCommand(exposureLength,exposureCount);
		// actually send the command to the C layer
		command.sendCommand();
		// check the parsed reply
		if(command.getParsedReplyOK() == false)
		{
			returnCode = command.getReturnCode();
			errorString = command.getParsedReply();
			sprat.log(Logging.VERBOSITY_TERSE,
				   "sendMultDarkCommand:multdark command failed with return code "+
				   returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
					    ":sendMultDarkCommand:Command failed with return code "+returnCode+
					    " and error string:"+errorString);
		}
		// extract data from successful reply.
		multrunNumber = command.getMultrunNumber();
		filenameList = command.getFilenameList();
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendMultDarkCommand:Finished.");
	}

	/**
	 * This routine calls the Real Time Data Pipeline to process the calibration FITS image we have just captured.
	 * If an error occurs the done objects field's are set accordingly. If the operation succeeds, and the
	 * done object is of class CALIBRATE_DONE, the done object is filled with data returned from the 
	 * reduction command.
	 * @param command The command being implemented that made this call to the DP(RT). This is used
	 * 	for error logging.
	 * @param done A COMMAND_DONE subclass specific to the command being implemented. If an
	 * 	error occurs the relevant fields are filled in with the error.
	 * @return The routine returns a boolean to indicate whether the operation was completed
	 *  	successfully.
	 * @see Sprat#sendDpRtCommand
	 */
	public boolean reduceCalibrate(COMMAND command,COMMAND_DONE done,String filename)
	{
		CALIBRATE_REDUCE reduce = new CALIBRATE_REDUCE(command.getId());
		INST_TO_DP_DONE instToDPDone = null;
		CALIBRATE_REDUCE_DONE reduceDone = null;
		CALIBRATE_DONE calibrateDone = null;

		reduce.setFilename(filename);
		instToDPDone = sprat.sendDpRtCommand(reduce,serverConnectionThread);
		if(instToDPDone.getSuccessful() == false)
		{
			sprat.error(this.getClass().getName()+":reduce:"+
				    command+":"+instToDPDone.getErrorNum()+":"+instToDPDone.getErrorString());
			done.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+500);
			done.setErrorString(instToDPDone.getErrorString());
			done.setSuccessful(false);
			return false;
		}
		// Copy the DP REDUCE DONE parameters to the CALIBRATE DONE parameters
		if(instToDPDone instanceof CALIBRATE_REDUCE_DONE)
		{
			reduceDone = (CALIBRATE_REDUCE_DONE)instToDPDone;
			if(done instanceof CALIBRATE_DONE)
			{
				calibrateDone = (CALIBRATE_DONE)done;
				calibrateDone.setFilename(reduceDone.getFilename());
				calibrateDone.setMeanCounts(reduceDone.getMeanCounts());
				calibrateDone.setPeakCounts(reduceDone.getPeakCounts());
			}
		}
		return true;
	}
}
