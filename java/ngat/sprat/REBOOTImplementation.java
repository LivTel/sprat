// REBOOTImplementation.java
// $HeadURL$
package ngat.sprat;

import java.lang.*;
import java.util.*;

import ngat.message.base.*;
import ngat.message.ISS_INST.REBOOT;
import ngat.message.ISS_INST.REBOOT_DONE;
import ngat.sprat.ccd.command.*;
import ngat.sprat.mechanism.command.*;
import ngat.util.logging.*;
import ngat.util.*;

/**
 * This class provides the implementation for the REBOOT command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision: 1.1 $
 */
public class REBOOTImplementation extends HardwareImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Class constant used in calculating acknowledge times, when the acknowledge time connot be found in the
	 * configuration file.
	 */
	public final static int DEFAULT_ACKNOWLEDGE_TIME = 		60000;
	/**
	 * String representing the root part of the property key used to get the acknowledge time for 
	 * a certain level of reboot.
	 */
	public final static String ACK_TIME_PROPERTY_KEY_ROOT =	    "sptat.reboot.acknowledge_time.";
	/**
	 * String representing the root part of the property key used to decide whether a certain level of reboot
	 * is enabled.
	 */
	public final static String ENABLE_PROPERTY_KEY_ROOT =       "sprat.reboot.enable.";
	/**
	 * Set of constant strings representing levels of reboot. The levels currently start at 1, so index
	 * 0 is currently "NONE". These strings need to be kept in line with level constants defined in
	 * ngat.message.ISS_INST.REBOOT.
	 */
	public final static String REBOOT_LEVEL_LIST[] =  {"NONE","REDATUM","SOFTWARE","HARDWARE","POWER_OFF"};

	/**
	 * Constructor.
	 */
	public REBOOTImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.REBOOT&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.REBOOT";
	}

	/**
	 * This method gets the REBOOT command's acknowledge time. This time is dependant on the level.
	 * This is calculated as follows:
	 * <ul>
	 * <li>If the level is LEVEL_REDATUM, the number stored in &quot; 
	 * sprat.reboot.acknowledge_time.REDATUM &quot; in the Sprat properties file is the timeToComplete.
	 * <li>If the level is LEVEL_SOFTWARE, the number stored in &quot; 
	 * sprat.reboot.acknowledge_time.SOFTWARE &quot; in the Sprat properties file is the timeToComplete.
	 * <li>If the level is LEVEL_HARDWARE, the number stored in &quot; 
	 * sprat.reboot.acknowledge_time.HARDWARE &quot; in the Sprat properties file is the timeToComplete.
	 * <li>If the level is LEVEL_POWER_OFF, the number stored in &quot; 
	 * sprat.reboot.acknowledge_time.POWER_OFF &quot; in the Sprat properties file is the timeToComplete.
	 * </ul>
	 * If these numbers cannot be found, the default number DEFAULT_ACKNOWLEDGE_TIME is used instead.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set to a time (in milliseconds).
	 * @see #DEFAULT_ACKNOWLEDGE_TIME
	 * @see #ACK_TIME_PROPERTY_KEY_ROOT
	 * @see #REBOOT_LEVEL_LIST
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see SpratStatus#getPropertyInteger
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		REBOOT rebootCommand = (REBOOT)command;
		ACK acknowledge = null;
		int timeToComplete = 0;

		acknowledge = new ACK(command.getId()); 
		try
		{
			timeToComplete = status.getPropertyInteger(ACK_TIME_PROPERTY_KEY_ROOT+
								   REBOOT_LEVEL_LIST[rebootCommand.getLevel()]);
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":calculateAcknowledgeTime:"+
				    rebootCommand.getLevel(),e);
			timeToComplete = DEFAULT_ACKNOWLEDGE_TIME;
		}
	//set time and return
		acknowledge.setTimeToComplete(timeToComplete);
		return acknowledge;
	}

	/**
	 * This method implements the REBOOT command. 
	 * An object of class REBOOT_DONE is returned.
	 * The <i>sprat.reboot.enable.&lt;level&gt;</i> property is checked to see to whether to really
	 * do the specified level of reboot. This enables us to say, disbale to POWER_OFF reboot, if the
	 * instrument control computer is not connected to an addressable power supply.
	 * The following four levels of reboot are recognised:
	 * <ul>
	 * <li>REDATUM. This shuts down the connection to the controller, and then
	 * 	restarts it. It reloads the non-network property files.
	 * <li>SOFTWARE. <ul>
	 *               <li>It sends the REBOOT command onto the DpRt.
	 *               <li>It sends the "shutdown" command to the C layer, which stops 
	 *                   (and via the autobooter, restarts) the C layer software. 
	 *               <li>It then closes the server socket using the Sprat close method. 
	 *               <li>It then exits the Sprat control software.
	 *               </ul>
	 * <li>HARDWARE. 
	 *               <ul>
	 *               <li>It sends the REBOOT command onto the DpRt.
	 *               <li>It closes the server socket using the Sprat close method. 
	 *               <li>It then issues a reboot command to the underlying operating system, 
	 *                   to restart the instrument computer.
	 *               </ul>
	 * <li>POWER_OFF. 
	 *               <ul>
	 *               <li>It  sends a "temperature set" to the C layer using sendTemperatureSetCommand, to
	 *                   tell the CCD to warm to an ambient temperature 
	 *                   (defined in the "sprat.reboot.ccd.temperature.warm" property).
	 *               <li>It closes the server socket using the Sprat close method. It sets the exit value
	 *                   to 127 so the Autobooter does not restart the software.
	 *               <li>It then issues a shutdown command to the underlying operating system using 
	 *                   ICSDShutdownCommand, to put the instrument computer into a state where 
	 *                   power can be switched off. 
	 *               </ul>
	 * </ul>
	 * Note: You need to perform at least a SOFTWARE level reboot to re-read the Sprat configuration file,
	 * as it contains information such as server ports.
	 * @param command The command instance we are implementing.
	 * @return An instance of REBOOT_DONE. Note this is only returned on a REDATUM level reboot,
	 *         all other levels cause the Sprat to terminate (either directly or indirectly) and a DONE
	 *         message cannot be returned.
	 * @see ngat.message.ISS_INST.REBOOT#LEVEL_REDATUM
	 * @see ngat.message.ISS_INST.REBOOT#LEVEL_SOFTWARE
	 * @see ngat.message.ISS_INST.REBOOT#LEVEL_HARDWARE
	 * @see ngat.message.ISS_INST.REBOOT#LEVEL_POWER_OFF
	 * @see #ENABLE_PROPERTY_KEY_ROOT
	 * @see #REBOOT_LEVEL_LIST
	 * @see #status
	 * @see #sendShutdownCommand
	 * @see #sendTemperatureSetCommand
	 * @see Sprat#close
	 * @see Sprat#reInit
	 * @see Sprat#sendDpRtCommand
	 * @see SpratStatus#getPropertyBoolean
	 * @see SpratStatus#getPropertyDouble
	 * @see ngat.sprat.SpratREBOOTQuitThread
	 * @see ngat.util.ICSDRebootCommand
	 * @see ngat.util.ICSDShutdownCommand
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		REBOOT rebootCommand = (ngat.message.ISS_INST.REBOOT)command;
		REBOOT_DONE rebootDone = new ngat.message.ISS_INST.REBOOT_DONE(command.getId());
		ngat.message.INST_DP.REBOOT dprtReboot = new ngat.message.INST_DP.REBOOT(command.getId());
		ICSDRebootCommand icsdRebootCommand = null;
		ICSDShutdownCommand icsdShutdownCommand = null;
		SpratREBOOTQuitThread quitThread = null;
		double warmCCDTemperature;
		boolean enable = false;

		try
		{
			// is reboot enabled at this level
			enable = status.getPropertyBoolean(ENABLE_PROPERTY_KEY_ROOT+
							   REBOOT_LEVEL_LIST[rebootCommand.getLevel()]);
			// if not enabled return OK
			if(enable == false)
			{
				sprat.log(Logging.VERBOSITY_VERY_TERSE,"Command:"+
					  rebootCommand.getClass().getName()+":Level:"+rebootCommand.getLevel()+
					  " is not enabled.");
				rebootDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
				rebootDone.setErrorString("");
				rebootDone.setSuccessful(true);
				return rebootDone;
			}
			// do relevent reboot based on level
			switch(rebootCommand.getLevel())
			{
				case REBOOT.LEVEL_REDATUM:
					sprat.reInit();
					break;
				case REBOOT.LEVEL_SOFTWARE:
					// send REBOOT to the data pipeline
					dprtReboot.setLevel(rebootCommand.getLevel());
					sprat.sendDpRtCommand(dprtReboot,serverConnectionThread);
					// send software restart onto c layer.
					// send shutdown command to C Layer
					sendShutdownCommand();
					sprat.close();
					quitThread = new SpratREBOOTQuitThread("quit:"+rebootCommand.getId());
					quitThread.setSprat(sprat);
					quitThread.setWaitThread(serverConnectionThread);
					// software will quit with exit value 0 as normal,
					// This will cause the autobooter to restart it.
					quitThread.start();
					break;
				case REBOOT.LEVEL_HARDWARE:
					// send REBOOT to the data pipeline
					dprtReboot.setLevel(rebootCommand.getLevel());
					sprat.sendDpRtCommand(dprtReboot,serverConnectionThread);
					sprat.close();
					quitThread = new SpratREBOOTQuitThread("quit:"+rebootCommand.getId());
					quitThread.setSprat(sprat);
					quitThread.setWaitThread(serverConnectionThread);
					// tell the autobooter not to restart the control system
					quitThread.setExitValue(127);
					quitThread.start();
				// send reboot to the icsd_inet
					icsdRebootCommand = new ICSDRebootCommand();
					icsdRebootCommand.send();
					break;
				case REBOOT.LEVEL_POWER_OFF:
					// Warm up the CCD, before he computer is shutdown
					warmCCDTemperature = status.getPropertyDouble(
								    "sprat.reboot.ccd.temperature.warm");
					sendTemperatureSetCommand(warmCCDTemperature);
					// stop the Java layer
					sprat.close();
					quitThread = new SpratREBOOTQuitThread("quit:"+rebootCommand.getId());
					quitThread.setSprat(sprat);
					quitThread.setWaitThread(serverConnectionThread);
					// tell the autobooter not to restart the control system
					quitThread.setExitValue(127);
					quitThread.start();
				// send shutdown to the icsd_inet
					icsdShutdownCommand = new ICSDShutdownCommand();
					icsdShutdownCommand.send();
					break;
				default:
					sprat.error(this.getClass().getName()+
					       ":processCommand:"+command+":Illegal level:"+rebootCommand.getLevel());
					rebootDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1400);
					rebootDone.setErrorString("Illegal level:"+rebootCommand.getLevel());
					rebootDone.setSuccessful(false);
					return rebootDone;
			};// end switch
		}
		catch(Exception e)
		{
			sprat.error(this.getClass().getName()+":processCommand:"+command+":",e);
			rebootDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1401);
			rebootDone.setErrorString(e.toString());
			rebootDone.setSuccessful(false);
			return rebootDone;
		}
	// return done object.
		rebootDone.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
		rebootDone.setErrorString("");
		rebootDone.setSuccessful(true);
		return rebootDone;
	}

	/**
	 * Send a "shutdown" command to the C layer.
	 * @exception Exception Thrown if an error occurs.
	 * @see HardwareImplementation#ccdCLayerHostname
	 * @see HardwareImplementation#ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.ShutdownCommand
	 * @see ngat.sprat.ccd.command.ShutdownCommand#setAddress
	 * @see ngat.sprat.ccd.command.ShutdownCommand#setPortNumber
	 * @see ngat.sprat.ccd.command.ShutdownCommand#setCommand
	 * @see ngat.sprat.ccd.command.ShutdownCommand#sendCommand
	 * @see ngat.sprat.ccd.command.ShutdownCommand#getParsedReplyOK
	 * @see ngat.sprat.ccd.command.ShutdownCommand#getReturnCode
	 * @see ngat.sprat.ccd.command.ShutdownCommand#getParsedReply
	 */
	protected void sendShutdownCommand() throws Exception
	{
		ShutdownCommand command = null;
		int portNumber,returnCode;
		String hostname = null;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendShutdownCommand:Started.");
		command = new ShutdownCommand();
		// configure C comms
		command.setAddress(ccdCLayerHostname);
		command.setPortNumber(ccdCLayerPortNumber);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendShutdownCommand:hostname = "+ccdCLayerHostname+
			  " :port number = "+ccdCLayerPortNumber+".");
		// actually send the command to the C layer
		command.sendCommand();
		// check the parsed reply
		if(command.getParsedReplyOK() == false)
		{
			returnCode = command.getReturnCode();
			errorString = command.getParsedReply();
			sprat.log(Logging.VERBOSITY_TERSE,
				   "sendShutdownCommand:shutdown command failed with return code "+
				   returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
					    ":sendShutdownCommand:Command failed with return code "+returnCode+
					    " and error string:"+errorString);
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendShutdownCommand:Finished.");
	}

	/**
	 * Send a "temperature set" command to the C layer.
	 * @exception Exception Thrown if an error occurs.
	 * @param targetTemperature The target temperature to tell the CCD to ramp to, in degrees centigrade.
	 * @see HardwareImplementation#ccdCLayerHostname
	 * @see HardwareImplementation#ccdCLayerPortNumber
	 * @see ngat.sprat.ccd.command.TemperatureSetCommand
	 * @see ngat.sprat.ccd.command.TemperatureSetCommand#setAddress
	 * @see ngat.sprat.ccd.command.TemperatureSetCommand#setPortNumber
	 * @see ngat.sprat.ccd.command.TemperatureSetCommand#setCommand
	 * @see ngat.sprat.ccd.command.TemperatureSetCommand#sendCommand
	 * @see ngat.sprat.ccd.command.TemperatureSetCommand#getParsedReplyOK
	 * @see ngat.sprat.ccd.command.TemperatureSetCommand#getReturnCode
	 * @see ngat.sprat.ccd.command.TemperatureSetCommand#getParsedReply
	 */
	protected void sendTemperatureSetCommand(double targetTemperature) throws Exception
	{
		TemperatureSetCommand command = null;
		int portNumber,returnCode;
		String hostname = null;
		String errorString = null;

		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendTemperatureSetCommand(target temperature= "+
			  targetTemperature+" C):Started.");
		command = new TemperatureSetCommand();
		// configure C comms
		command.setAddress(ccdCLayerHostname);
		command.setPortNumber(ccdCLayerPortNumber);
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendTemperatureSetCommand:hostname = "+ccdCLayerHostname+
			  " :port number = "+ccdCLayerPortNumber+".");
		command.setCommand(targetTemperature);
		// actually send the command to the C layer
		command.sendCommand();
		// check the parsed reply
		if(command.getParsedReplyOK() == false)
		{
			returnCode = command.getReturnCode();
			errorString = command.getParsedReply();
			sprat.log(Logging.VERBOSITY_TERSE,
				   "sendTemperatureSetCommand:temperature set command failed with return code "+
				   returnCode+" and error string:"+errorString);
			throw new Exception(this.getClass().getName()+
					    ":sendTemperatureSetCommand:Command failed with return code "+returnCode+
					    " and error string:"+errorString);
		}
		sprat.log(Logging.VERBOSITY_INTERMEDIATE,"sendTemperatureSetCommand:Finished.");
	}
}
