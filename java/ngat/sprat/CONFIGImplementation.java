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
 * @version $Revision$
 */
public class CONFIGImplementation extends HardwareImplementation implements JMSCommandImplementation
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
	 * @see #sprat
	 * @see #status
	 * @see #moveSlit
	 * @see #rotateGrism
	 * @see #moveGrism
	 * @see HardwareImplementation#setFocusOffset
	 * @see HardwareImplementation#sendConfigCommand
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
}
