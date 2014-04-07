// SpratStatus.java
// $HeadURL$
package ngat.sprat;

import java.lang.*;
import java.io.*;
import java.util.*;

import ngat.message.ISS_INST.*;
import ngat.phase2.*;
import ngat.util.PersistentUniqueInteger;
import ngat.util.FileUtilitiesNativeException;
import ngat.util.logging.FileLogHandler;

/**
 * This class holds status information for the Sprat program.
 * @author Chris Mottram
 * @version $Revision: 1.1 $
 */
public class SpratStatus
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Default filename containing network properties for Sprat.
	 */
	private final static String DEFAULT_NET_PROPERTY_FILE_NAME = "./sprat.net.properties";
	/**
	 * Default filename containing properties for Sprat.
	 */
	private final static String DEFAULT_PROPERTY_FILE_NAME = "./sprat.properties";
	/**
	 * Default filename containing FITS properties for Sprat.
	 */
	private final static String DEFAULT_FITS_PROPERTY_FILE_NAME = "./fits.properties";
	/**
	 * The logging level. An absolute filter is used by the loggers.
	 * @link http://ltdevsrv.livjm.ac.uk/~dev/log_udp/cdocs/log_udp.html#LOG_VERBOSITY
	 * @link http://ltdevsrv.livjm.ac.uk/~dev/ngat/javadocs/ngat/util/logging/ngat/util/logging/Logging.html#VERBOSITY_VERY_TERSE
	 * @link http://ltdevsrv.livjm.ac.uk/~dev/ngat/javadocs/ngat/util/logging/ngat/util/logging/Logging.html#VERBOSITY_TERSE
	 * @link http://ltdevsrv.livjm.ac.uk/~dev/ngat/javadocs/ngat/util/logging/ngat/util/logging/Logging.html#VERBOSITY_INTERMEDIATE
	 * @link http://ltdevsrv.livjm.ac.uk/~dev/ngat/javadocs/ngat/util/logging/ngat/util/logging/Logging.html#VERBOSITY_VERBOSE
	 * @link http://ltdevsrv.livjm.ac.uk/~dev/ngat/javadocs/ngat/util/logging/ngat/util/logging/Logging.html#VERBOSITY_VERY_VERBOSE
	 */
	private int logLevel = 0;
	/**
	 * The current thread that the Sprat Control System is using to process the
	 * <a href="#currentCommand">currentCommand</a>. This does not get set for
	 * commands that can be sent while others are in operation, such as Abort and get status comamnds.
	 * This can be null when no command is currently being processed.
	 */
	private Thread currentThread = null;
	/**
	 * The current command that the Sprat Control System is working on. This does not get set for
	 * commands that can be sent while others are in operation, such as Abort and get status comamnds.
	 * This can be null when no command is currently being processed.
	 */
	private ISS_TO_INST currentCommand = null;
	/**
	 * A list of properties held in the properties file. This contains configuration information in Sprat
	 * that needs to be changed irregularily.
	 */
	private Properties properties = null;
	/**
	 * The current unique config ID, held on disc over reboots.
	 * Incremented each time a new configuration is attained,
	 * and stored in the FITS header.
	 */
	private PersistentUniqueInteger configId = null;
	/**
	 * The name of the ngat.phase2.SpratConfig object instance that was last used	
	 * to configure the instrument (via an ngat.message.ISS_INST.CONFIG message).
	 * Used for the CONFNAME FITS keyword value.
	 * Initialised to 'UNKNOWN', so that if we try to take a frame before configuring the Sprat
	 * we get an error about setup not being complete, rather than an error about NULL FITS values.
	 */
	private String configName = "UNKNOWN";
	/**
	 * The count of the number of exposures needed for the current command to be implemented.
	 */
	private int exposureCount = 0;
	/**
	 * The number of the current exposure being taken.
	 */
	private int exposureNumber = 0;
	/**
	 * The exposure start time, in milliseconds since the epoch.
	 */
	private long exposureStartTime = 0;
	/**
	 * The exposure length in milliseconds.
	 */
	private int exposureLength = 0;
	private Date getConfigCommandCacheTimestamp = null;

	/**
	 * Default constructor. Initialises the properties.
	 * @see #properties
	 */
	public SpratStatus()
	{
		properties = new Properties();
	}

	/**
	 * The load method for the class. This loads the property file from disc, using the specified
	 * filename. Any old properties are first cleared.
	 * The configId unique persistent integer is then initialised, using a filename stored in the properties.
	 * @see #properties
	 * @see #initialiseConfigId
	 * @see #DEFAULT_NET_PROPERTY_FILE_NAME
	 * @see #DEFAULT_PROPERTY_FILE_NAME
	 * @see #DEFAULT_FITS_PROPERTY_FILE_NAME
	 * @exception FileNotFoundException Thrown if a configuration file is not found.
	 * @exception IOException Thrown if an IO error occurs whilst loading a configuration file.
	 */
	public void load()  throws FileNotFoundException, IOException
	{
		String netFilename = null;
		String filename = null;
		FileInputStream fileInputStream = null;

	// clear old properties
		properties.clear();
	// network properties load
		netFilename = DEFAULT_NET_PROPERTY_FILE_NAME;
		fileInputStream = new FileInputStream(netFilename);
		properties.load(fileInputStream);
		fileInputStream.close();
	// normal properties load
		filename = DEFAULT_PROPERTY_FILE_NAME;
		fileInputStream = new FileInputStream(filename);
		properties.load(fileInputStream);
		fileInputStream.close();
	// fits properties load
		filename = DEFAULT_FITS_PROPERTY_FILE_NAME;
		fileInputStream = new FileInputStream(filename);
		properties.load(fileInputStream);
		fileInputStream.close();
	// initialise configId
		initialiseConfigId();
	}

	/**
	 * The reload method for the class. This reloads the specified property files from disc.
	 * The current properties are not cleared, as network properties are not re-loaded, as this would
	 * involve resetting up the server connection thread which may be in use. If properties have been
	 * deleted from the loaded files, reload does not clear these properties. Any new properties or
	 * ones where the values have changed will change.
	 * The configId unique persistent integer is then initialised, using a filename stored in the properties.
	 * @see #properties
	 * @see #initialiseConfigId
	 * @see #DEFAULT_NET_PROPERTY_FILE_NAME
	 * @see #DEFAULT_PROPERTY_FILE_NAME
	 * @see #DEFAULT_FITS_PROPERTY_FILE_NAME
	 * @exception FileNotFoundException Thrown if a configuration file is not found.
	 * @exception IOException Thrown if an IO error occurs whilst loading a configuration file.
	 */
	public void reload() throws FileNotFoundException,IOException
	{
		String filename = null;
		FileInputStream fileInputStream = null;

	// don't clear old properties, the network properties are not re-loaded
	// normal properties load
		filename = DEFAULT_PROPERTY_FILE_NAME;
		fileInputStream = new FileInputStream(filename);
		properties.load(fileInputStream);
		fileInputStream.close();
	// fits properties load
		filename = DEFAULT_FITS_PROPERTY_FILE_NAME;
		fileInputStream = new FileInputStream(filename);
		properties.load(fileInputStream);
		fileInputStream.close();
	// initialise configId
		initialiseConfigId();
	}

	/**
	 * Set the logging level for Sprat.
	 * @param level The level of logging.
	 */
	public synchronized void setLogLevel(int level)
	{
		logLevel = level;
	}

	/**
	 * Get the logging level for Sprat.
	 * @return The current log level.
	 */	
	public synchronized int getLogLevel()
	{
		return logLevel;
	}

	/**
	 * Set the command that is currently executing.
	 * @param command The command that is currently executing.
	 */
	public synchronized void setCurrentCommand(ISS_TO_INST command)
	{
		currentCommand = command;
	}

	/**
	 * Get the the command the Sprat is currently processing.
	 * @return The command currently being processed.
	 */
	public synchronized ISS_TO_INST getCurrentCommand()
	{
		return currentCommand;
	}

	/**
	 * Set the thread that is currently executing the <a href="#currentCommand">currentCommand</a>.
	 * @param thread The thread that is currently executing.
	 * @see #currentThread
	 */
	public synchronized void setCurrentThread(Thread thread)
	{
		currentThread = thread;
	}

	/**
	 * Get the the thread currently executing to process the <a href="#currentCommand">currentCommand</a>.
	 * @return The thread currently being executed.
	 * @see #currentThread
	 */
	public synchronized Thread getCurrentThread()
	{
		return currentThread;
	}

	/**
	 * Set the number of exposures needed to complete the current command implementation.
	 * @param c The total number of exposures needed.
	 * @see #exposureCount
	 */
	public synchronized void setExposureCount(int c)
	{
		exposureCount = c;
	}

	/**
	 * Get the number of exposures needed to complete the current command implementation.
	 * @return Returns the number of exposures needed.
	 * @see #exposureCount
	 */
	public synchronized int getExposureCount()
	{
		return exposureCount;
	}

	/**
	 * Set the current exposure number the current command implementation is on.
	 * @param n The current exposure number.
	 * @see #exposureNumber
	 */
	public synchronized void setExposureNumber(int n)
	{
		exposureNumber = n;
	}

	/**
	 * Get the current exposure number the current command implementation is on.
	 * @return Returns the current exposure number.
	 * @see #exposureNumber
	 */
	public synchronized int getExposureNumber()
	{
		return exposureNumber;
	}

	/**
	 * Set the exposure start time.
	 * @param est The exposure starttime, in milliseconds since the epoch.
	 * @see #exposureStartTime
	 */
	public synchronized void setExposureStartTime(long est)
	{
		exposureStartTime = est;
	}

	/**
	 * Get the exposure start time.
	 * @return The exposure start time, in milliseconds since the epoch.
	 * @see #exposureStartTime
	 */
	public synchronized long getExposureStartTime()
	{
		return exposureStartTime;
	}

	/**
	 * Set the exposure length.
	 * @param ms The exposure length, in milliseconds.
	 * @see #exposureLength
	 */
	public synchronized void setExposureLength(int ms)
	{
		exposureLength = ms;
	}

	/**
	 * Get the exposure length.
	 * @return The exposure length, in milliseconds.
	 * @see #exposureLength
	 */
	public synchronized int getExposureLength()
	{
		return exposureLength;
	}

	/**
	 * Method to change (increment) the unique ID number of the last ngat.phase2.SpratConfig instance to 
	 * successfully configure the Sprat camera.
	 * This is done by calling <i>configId.increment()</i>.
	 * @see #configId
	 * @see ngat.util.PersistentUniqueInteger#increment
	 * @exception FileUtilitiesNativeException Thrown if <i>PersistentUniqueInteger.increment()</i> fails.
	 * @exception NumberFormatException Thrown if <i>PersistentUniqueInteger.increment()</i> fails.
	 * @exception Exception Thrown if <i>PersistentUniqueInteger.increment()</i> fails.
	 */
	public synchronized void incConfigId() throws FileUtilitiesNativeException,
		NumberFormatException, Exception
	{
		configId.increment();
	}

	/**
	 * Method to get the unique config ID number of the last
	 * ngat.phase2.SpratConfig instance to successfully configure the Sprat camera.
	 * @return The unique config ID number.
	 * This is done by calling <i>configId.get()</i>.
	 * @see #configId
	 * @see ngat.util.PersistentUniqueInteger#get
	 * @exception FileUtilitiesNativeException Thrown if <i>PersistentUniqueInteger.get()</i> fails.
	 * @exception NumberFormatException Thrown if <i>PersistentUniqueInteger.get()</i> fails.
	 * @exception Exception Thrown if <i>PersistentUniqueInteger.get()</i> fails.
	 */
	public synchronized int getConfigId() throws FileUtilitiesNativeException,
		NumberFormatException, Exception
	{
		return configId.get();
	}

	/**
	 * Method to set our reference to the string identifier of the last
	 * ngat.phase2.SpratConfig instance to successfully configure the Sprat camera.
	 * @param s The string from the configuration object instance.
	 * @see #configName
	 */
	public synchronized void setConfigName(String s)
	{
		configName = s;
	}

	/**
	 * Method to get the string identifier of the last
	 * ngat.phase2.SpratConfig instance to successfully configure the Sprat camera.
	 * @return The string identifier, or null if the Sprat camera has not been configured
	 * 	since the Sprat started.
	 * @see #configName
	 */
	public synchronized String getConfigName()
	{
		return configName;
	}

	/**
	 * Method to return whether the loaded properties contain the specified keyword.
	 * Calls the proprties object containsKey method. Note assumes the properties object has been initialised.
	 * @param p The property key we wish to test exists.
	 * @return The method returnd true if the specified key is a key in out list of properties,
	 *         otherwise it returns false.
	 * @see #properties
	 */
	public boolean propertyContainsKey(String p)
	{
		return properties.containsKey(p);
	}

	/**
	 * Routine to get a properties value, given a key. Just calls the properties object getProperty routine.
	 * @param p The property key we want the value for.
	 * @return The properties value, as a string object. If the key is not found, Properties.getProperty
	 *         will return null.
	 * @see #properties
	 */
	public String getProperty(String p)
	{
		return properties.getProperty(p);
	}

	/**
	 * Routine to get a properties value, given a key. The value must be a valid integer, else a 
	 * NumberFormatException is thrown.
	 * @param p The property key we want the value for.
	 * @return The properties value, as an integer.
	 * @exception NumberFormatException If the properties value string is not a valid integer, this
	 * 	exception will be thrown when the Integer.parseInt routine is called.
	 * @see #properties
	 */
	public int getPropertyInteger(String p) throws NumberFormatException
	{
		String valueString = null;
		int returnValue = 0;

		valueString = properties.getProperty(p);
		try
		{
			returnValue = Integer.parseInt(valueString);
		}
		catch(NumberFormatException e)
		{
			// re-throw exception with more information e.g. keyword
			throw new NumberFormatException(this.getClass().getName()+":getPropertyInteger:keyword:"+
				p+":valueString:"+valueString);
		}
		return returnValue;
	}

	/**
	 * Routine to get a properties value, given a key. The value must be a valid long, else a 
	 * NumberFormatException is thrown.
	 * @param p The property key we want the value for.
	 * @return The properties value, as a long.
	 * @exception NumberFormatException If the properties value string is not a valid long, this
	 * 	exception will be thrown when the Long.parseLong routine is called.
	 * @see #properties
	 */
	public long getPropertyLong(String p) throws NumberFormatException
	{
		String valueString = null;
		long returnValue = 0;

		valueString = properties.getProperty(p);
		try
		{
			returnValue = Long.parseLong(valueString);
		}
		catch(NumberFormatException e)
		{
			// re-throw exception with more information e.g. keyword
			throw new NumberFormatException(this.getClass().getName()+":getPropertyLong:keyword:"+
				p+":valueString:"+valueString);
		}
		return returnValue;
	}

	/**
	 * Routine to get a properties value, given a key. The value must be a valid short, else a 
	 * NumberFormatException is thrown.
	 * @param p The property key we want the value for.
	 * @return The properties value, as a short.
	 * @exception NumberFormatException If the properties value string is not a valid short, this
	 * 	exception will be thrown when the Short.parseShort routine is called.
	 * @see #properties
	 */
	public short getPropertyShort(String p) throws NumberFormatException
	{
		String valueString = null;
		short returnValue = 0;

		valueString = properties.getProperty(p);
		try
		{
			returnValue = Short.parseShort(valueString);
		}
		catch(NumberFormatException e)
		{
			// re-throw exception with more information e.g. keyword
			throw new NumberFormatException(this.getClass().getName()+":getPropertyShort:keyword:"+
				p+":valueString:"+valueString);
		}
		return returnValue;
	}

	/**
	 * Routine to get a properties value, given a key. The value must be a valid double, else a 
	 * NumberFormatException is thrown.
	 * @param p The property key we want the value for.
	 * @return The properties value, as an double.
	 * @exception NumberFormatException If the properties value string is not a valid double, this
	 * 	exception will be thrown when the Double.valueOf routine is called.
	 * @see #properties
	 */
	public double getPropertyDouble(String p) throws NumberFormatException
	{
		String valueString = null;
		Double returnValue = null;

		valueString = properties.getProperty(p);
		try
		{
			returnValue = Double.valueOf(valueString);
		}
		catch(NumberFormatException e)
		{
			// re-throw exception with more information e.g. keyword
			throw new NumberFormatException(this.getClass().getName()+":getPropertyDouble:keyword:"+
				p+":valueString:"+valueString);
		}
		return returnValue.doubleValue();
	}

	/**
	 * Routine to get a properties value, given a key. The value must be a valid float, else a 
	 * NumberFormatException is thrown.
	 * @param p The property key we want the value for.
	 * @return The properties value, as a float.
	 * @exception NumberFormatException If the properties value string is not a valid float, this
	 * 	exception will be thrown.
	 * @see #properties
	 */
	public float getPropertyFloat(String p) throws NumberFormatException
	{
		String valueString = null;
		Float returnValue = null;

		valueString = properties.getProperty(p);
		try
		{
			returnValue = Float.valueOf(valueString);
		}
		catch(NumberFormatException e)
		{
			// re-throw exception with more information e.g. keyword
			throw new NumberFormatException(this.getClass().getName()+":getPropertyFloat:keyword:"+
				p+":valueString:"+valueString);
		}
		return returnValue.floatValue();
	}

	/**
	 * Routine to get a properties boolean value, given a key. The properties value should be either 
	 * "true" or "false".
	 * Boolean.valueOf is used to convert the string to a boolean value.
	 * @param p The property key we want the boolean value for.
	 * @return The properties value, as an boolean.
	 * @exception NullPointerException If the properties value string is null, this
	 * 	exception will be thrown.
	 * @see #properties
	 */
	public boolean getPropertyBoolean(String p) throws NullPointerException
	{
		String valueString = null;
		Boolean b = null;

		valueString = properties.getProperty(p);
		if(valueString == null)
		{
			throw new NullPointerException(this.getClass().getName()+":getPropertyBoolean:keyword:"+
				p+":Value was null.");
		}
		b = Boolean.valueOf(valueString);
		return b.booleanValue();
	}

	/**
	 * Routine to get a properties character value, given a key. The properties value should be a 1 letter string.
	 * @param p The property key we want the character value for.
	 * @return The properties value, as a character.
	 * @exception NullPointerException If the properties value string is null, this
	 * 	exception will be thrown.
	 * @exception Exception Thrown if the properties value string is not of length 1.
	 * @see #properties
	 */
	public char getPropertyChar(String p) throws NullPointerException, Exception
	{
		String valueString = null;
		char ch;

		valueString = properties.getProperty(p);
		if(valueString == null)
		{
			throw new NullPointerException(this.getClass().getName()+":getPropertyChar:keyword:"+
				p+":Value was null.");
		}
		if(valueString.length() != 1)
		{
			throw new Exception(this.getClass().getName()+":getPropertyChar:keyword:"+
					    p+":Value not of length 1, had length "+valueString.length());
		}
		ch = valueString.charAt(0);
		return ch;
	}

	/**
	 * Routine to get an integer representing a ngat.util.logging.FileLogHandler time period.
	 * The value of the specified property should contain either:'HOURLY_ROTATION', 'DAILY_ROTATION' or
	 * 'WEEKLY_ROTATION'.
	 * @param p The property key we want the time period value for.
	 * @return The properties value, as an FileLogHandler time period (actually an integer).
	 * @exception NullPointerException If the properties value string is null an exception is thrown.
	 * @exception IllegalArgumentException If the properties value string is not a valid time period,
	 *            an exception is thrown.
	 * @see #properties
	 */
	public int getPropertyLogHandlerTimePeriod(String p) throws NullPointerException, IllegalArgumentException
	{
		String valueString = null;
		int timePeriod = 0;
 
		valueString = properties.getProperty(p);
		if(valueString == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":getPropertyLogHandlerTimePeriod:keyword:"+
						       p+":Value was null.");
		}
		if(valueString.equals("HOURLY_ROTATION"))
			timePeriod = FileLogHandler.HOURLY_ROTATION;
		else if(valueString.equals("DAILY_ROTATION"))
			timePeriod = FileLogHandler.DAILY_ROTATION;
		else if(valueString.equals("WEEKLY_ROTATION"))
			timePeriod = FileLogHandler.WEEKLY_ROTATION;
		else
		{
			throw new IllegalArgumentException(this.getClass().getName()+
							   ":getPropertyLogHandlerTimePeriod:keyword:"+
							   p+":Illegal value:"+valueString+".");
		}
		return timePeriod;
	}

	/**
	 * Method to get the thread priority to run the server thread at.
	 * The value is retrieved from the <b>sprat.thread.priority.server</b> property.
	 * If this fails the default SPRAT_DEFAULT_THREAD_PRIORITY_SERVER is returned.
	 * @return A valid thread priority between threads MIN_PRIORITY and MAX_PRIORITY.
	 * @see SpratConstants#SPRAT_DEFAULT_THREAD_PRIORITY_SERVER
	 */
	public int getThreadPriorityServer()
	{
		int retval;

		try
		{
			retval = getPropertyInteger("sprat.thread.priority.server");
			if(retval < Thread.MIN_PRIORITY)
				retval = Thread.MIN_PRIORITY;
			if(retval > Thread.MAX_PRIORITY)
				retval = Thread.MAX_PRIORITY;
		}
		catch(NumberFormatException e)
		{
			retval = SpratConstants.SPRAT_DEFAULT_THREAD_PRIORITY_SERVER;
		}
		return retval;
	}

	/**
	 * Method to get the thread priority to run interrupt threads at.
	 * The value is retrieved from the <b>sprat.thread.priority.interrupt</b> property.
	 * If this fails the default SPRAT_DEFAULT_THREAD_PRIORITY_INTERRUPT is returned.
	 * @return A valid thread priority between threads MIN_PRIORITY and MAX_PRIORITY.
	 * @see SpratConstants#SPRAT_DEFAULT_THREAD_PRIORITY_INTERRUPT
	 */
	public int getThreadPriorityInterrupt()
	{
		int retval;

		try
		{
			retval = getPropertyInteger("sprat.thread.priority.interrupt");
			if(retval < Thread.MIN_PRIORITY)
				retval = Thread.MIN_PRIORITY;
			if(retval > Thread.MAX_PRIORITY)
				retval = Thread.MAX_PRIORITY;
		}
		catch(NumberFormatException e)
		{
			retval = SpratConstants.SPRAT_DEFAULT_THREAD_PRIORITY_INTERRUPT;
		}
		return retval;
	}

	/**
	 * Method to get the thread priority to run normal threads at.
	 * The value is retrieved from the <b>sprat.thread.priority.normal</b> property.
	 * If this fails the default SPRAT_DEFAULT_THREAD_PRIORITY_NORMAL is returned.
	 * @return A valid thread priority between threads MIN_PRIORITY and MAX_PRIORITY.
	 * @see SpratConstants#SPRAT_DEFAULT_THREAD_PRIORITY_NORMAL
	 */
	public int getThreadPriorityNormal()
	{
		int retval;

		try
		{
			retval = getPropertyInteger("sprat.thread.priority.normal");
			if(retval < Thread.MIN_PRIORITY)
				retval = Thread.MIN_PRIORITY;
			if(retval > Thread.MAX_PRIORITY)
				retval = Thread.MAX_PRIORITY;
		}
		catch(NumberFormatException e)
		{
			retval = SpratConstants.SPRAT_DEFAULT_THREAD_PRIORITY_NORMAL;
		}
		return retval;
	}

	/**
	 * Method to get the thread priority to run the Telescope Image Transfer server and client 
	 * connection threads at.
	 * The value is retrieved from the <b>sprat.thread.priority.tit</b> property.
	 * If this fails the default SPRAT_DEFAULT_THREAD_PRIORITY_TIT is returned.
	 * @return A valid thread priority between threads MIN_PRIORITY and MAX_PRIORITY.
	 * @see SpratConstants#SPRAT_DEFAULT_THREAD_PRIORITY_TIT
	 */
	public int getThreadPriorityTIT()
	{
		int retval;

		try
		{
			retval = getPropertyInteger("sprat.thread.priority.tit");
			if(retval < Thread.MIN_PRIORITY)
				retval = Thread.MIN_PRIORITY;
			if(retval > Thread.MAX_PRIORITY)
				retval = Thread.MAX_PRIORITY;
		}
		catch(NumberFormatException e)
		{
			retval = SpratConstants.SPRAT_DEFAULT_THREAD_PRIORITY_TIT;
		}
		return retval;
	}

	/**
	 * Internal method to initialise the configId field. This is not done during construction
	 * as the property files need to be loaded to determine the filename to use.
	 * This is got from the <i>sprat.config.unique_id_filename</i> property.
	 * The configId field is then constructed.
	 * @see #configId
	 */
	private void initialiseConfigId()
	{
		String fileName = null;

		fileName = getProperty("sprat.config.unique_id_filename");
		configId = new PersistentUniqueInteger(fileName);
	}
}
