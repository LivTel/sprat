// Sprat.java
// $HeadURL$
package ngat.sprat;

import java.lang.*;
import java.lang.reflect.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

import ngat.fits.*;
import ngat.net.*;
import ngat.util.*;
import ngat.util.logging.*;
import ngat.sprat.ccd.command.*;
import ngat.sprat.mechanism.command.*;
import ngat.message.ISS_INST.*;
import ngat.message.INST_DP.*;


/**
 * This class is the start point for the Sprat Control System.
 * @author Chris Mottram
 * @version $Revision$
 */
public class Sprat
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Logger channel id.
	 */
	public final static String LOGGER_CHANNEL_ID = new String("SPRAT");
	/**
	 * Internal constant used when converting temperatures in centigrade to Kelvin.
	 */
	public final static double CENTIGRADE_TO_KELVIN = 273.15;
	/**
	 * The minimum port number to listen for connections on.
	 */
	static final int MINIMUM_PORT_NUMBER = 1025;
	/**
	 * The maximum port number to send ISS commands on.
	 */
	static final int MAXIMUM_PORT_NUMBER = 65535;
	/**
	 * The server class that listens for connections.
	 */
	private SpratTCPServer server = null;
	/**
	 * The server class that listens for Telescope Image Transfer request connections.
	 */
	private TitServer titServer = null;
	/**
	 * Status object.
	 */
	private SpratStatus status = null;
	/**
	 * This hashtable holds the map between COMMAND sub-class names and their implementations, which
	 * are stored as the Hashtable data values as class objects of sub-classes of CommandImplementation.
	 * When Sprat gets a COMMAND from a client it can query this Hashtable to find it's implementation class.
	 */
	private Hashtable<String,Class> implementationList = null;
	/**
	 * Command line argument. The level of logging to perform in Sprat.
	 */
	private int logLevel = 0;
	/**
	 * The port number to listen for connections from clients on.
	 */
	private int spratPortNumber = 0;
	/**
	 * The ip address of the machine the ISS is running on, to send ISS commands to.
	 */
	private InetAddress issAddress = null;
	/**
	 * The port number to send iss commands to.
	 */
	private int issPortNumber = 0;
	/**
	 * The ip address of the machine the DP(RT) is running on, to send Data Pipeline (Real Time) commands to.
	 */
	private InetAddress dprtAddress = null;
	/**
	 * The port number to send DP(RT) commands to.
	 */
	private int dprtPortNumber = 0;
	/**
	 * The port number to listen for Telescope Image Transfer requests.
	 */
	private int titPortNumber = 0;
	/**
	 * The logging logger.
	 */
	protected Logger logLogger = null;
	/**
	 * The error logger.
	 */
	protected Logger errorLogger = null;

	/**
	 * init method.
	 * <ul>
	 * <li>Create status object, and load property file.
	 * <li>Initialise loggers.
	 * <li>Initialise implementation list.
	 * <li>Retrieve port numbers from properties.
	 * <li>Get ISS address from properties.
	 * <li>Configure acknowledge time from properties.
	 * </ul>
	 * @see #error
	 * @see #initLoggers
	 * @see #setLogLevel
	 * @see #initImplementationList
	 * @see #spratPortNumber
	 * @see #issPortNumber
	 * @see #dprtPortNumber
	 * @see #titPortNumber
	 * @see #issAddress
	 * @see #dprtAddress
	 * @see ngat.sprat.SpratStatus
	 * @see ngat.sprat.SpratStatus#load
	 * @see ngat.sprat.SpratStatus#getPropertyInteger
	 * @see ngat.sprat.SpratStatus#getProperty
	 * @see ngat.sprat.SpratTCPServerConnectionThread#setDefaultAcknowledgeTime
	 * @see ngat.sprat.SpratTCPServerConnectionThread#setMinAcknowledgeTime
	 */
	private void init() throws FileNotFoundException,IOException,
		NumberFormatException,Exception
	{
		int time;

	// create status object and load ioi properties into it
		status = new SpratStatus();
		try
		{
			status.load();
		}
		catch(FileNotFoundException e)
		{
			error(this.getClass().getName()+":init:loading properties:",e);
			throw e;
		}
		catch(IOException e)
		{
			error(this.getClass().getName()+":init:loading properties:",e);
			throw e;
		}
	// Logging
		initLoggers();
	// initialise sub-system loggers, after creating status, hardware control objects
		setLogLevel(logLevel);
	// Create and initialise the implementationList
		initImplementationList();
	// initialise port numbers from properties file/ command line arguments
		try
		{
			spratPortNumber = status.getPropertyInteger("sprat.net.sprat.port_number");
			issPortNumber = status.getPropertyInteger("sprat.net.iss.port_number");
			dprtPortNumber = status.getPropertyInteger("sprat.net.dprt.port_number");
			titPortNumber = status.getPropertyInteger("sprat.net.tit.port_number");
		}
		catch(NumberFormatException e)
		{
			error(this.getClass().getName()+":init:initialsing port number:",e);
			throw e;
		}
	// initialise address's from properties file
		try
		{
			issAddress = InetAddress.getByName(status.getProperty("sprat.net.iss.address"));
			dprtAddress = InetAddress.getByName(status.getProperty("sprat.net.dprt.address"));
		}
		catch(UnknownHostException e)
		{
			error(this.getClass().getName()+":illegal internet address:",e);
			throw e;
		}
	// initialise default connection response times from properties file
		try
		{
			time = status.getPropertyInteger("sprat.server_connection.default_acknowledge_time");
			SpratTCPServerConnectionThread.setDefaultAcknowledgeTime(time);
			time = status.getPropertyInteger("sprat.server_connection.min_acknowledge_time");
			SpratTCPServerConnectionThread.setMinAcknowledgeTime(time);
		}
		catch(NumberFormatException e)
		{
			error(this.getClass().getName()+":init:initialsing server connection thread times:",e);
			// don't throw the error - failing to get this property is not 'vital' to Sprat.
		}		
	}

	/**
	 * Initialise log handlers. Called from init only, not re-configured on a REDATUM level reboot.
	 * @see #LOGGER_CHANNEL_ID
	 * @see #init
	 * @see #initLogHandlers
	 * @see #copyLogHandlers
	 * @see #errorLogger
	 * @see #logLogger
	 */
	protected void initLoggers()
	{
		String loggerNameStringArray[] = {"ngat.sprat.ccd.command.AbortCommand",
						  "ngat.sprat.ccd.command.BiasCommand",
						  "ngat.sprat.ccd.command.Command",
						  "ngat.sprat.ccd.command.ConfigCommand",
						  "ngat.sprat.ccd.command.DarkCommand",
						  "ngat.sprat.ccd.command.FitsHeaderAddCommand",
						  "ngat.sprat.ccd.command.FitsHeaderClearCommand",
						  "ngat.sprat.ccd.command.FitsHeaderDeleteCommand",
						  "ngat.sprat.ccd.command.IntegerReplyCommand",
						  "ngat.sprat.ccd.command.MultBiasCommand",
						  "ngat.sprat.ccd.command.MultDarkCommand",
						  "ngat.sprat.ccd.command.MultrunCommand",
						  "ngat.sprat.ccd.command.MultrunFilenameReplyCommand",
						  "ngat.sprat.ccd.command.ShutdownCommand",
						  "ngat.sprat.ccd.command.StatusExposureLengthCommand",
						  "ngat.sprat.ccd.command.StatusExposureMultrunCommand",
						  "ngat.sprat.ccd.command.StatusExposureRunCommand",
						  "ngat.sprat.ccd.command.StatusExposureStartTimeCommand",
						  "ngat.sprat.ccd.command.StatusExposureStatusCommand",
						  "ngat.sprat.ccd.command.StatusMultrunCountCommand",
						  "ngat.sprat.ccd.command.StatusMultrunIndexCommand",
						  "ngat.sprat.ccd.command.StatusTemperatureGetCommand",
						  "ngat.sprat.ccd.command.StatusTemperatureStatusCommand",
						  "ngat.sprat.mechanism.command.ArcLampCommand",
						  "ngat.sprat.mechanism.command.Command",
						  "ngat.sprat.mechanism.command.DoubleReplyCommand",
						  "ngat.sprat.mechanism.command.GrismCommand",
						  "ngat.sprat.mechanism.command.GyroCommand",
						  "ngat.sprat.mechanism.command.HumidityCommand",
						  "ngat.sprat.mechanism.command.InOutReplyCommand",
						  "ngat.sprat.mechanism.command.MirrorCommand",
						  "ngat.sprat.mechanism.command.OnOffReplyCommand",
						  "ngat.sprat.mechanism.command.RotationCommand",
						  "ngat.sprat.mechanism.command.SlitCommand",
						  "ngat.sprat.mechanism.command.TemperatureCommand",
						  "ngat.sprat.mechanism.command.WLampCommand",
						  "ngat.sprat.mechanism.MoveInOutMechanism",
						  "ngat.sprat.mechanism.MoveRotationMechanism"};

	// errorLogger setup
		errorLogger = LogManager.getLogger("error");
		errorLogger.setChannelID(LOGGER_CHANNEL_ID+"-ERROR");
		initLogHandlers(errorLogger);
		errorLogger.setLogLevel(Logging.ALL);
	// ngat.net error loggers
		copyLogHandlers(errorLogger,LogManager.getLogger("ngat.net.TCPServer"),null,Logging.ALL);
		copyLogHandlers(errorLogger,LogManager.getLogger("ngat.net.TCPServerConnectionThread"),null,
				Logging.ALL);
		copyLogHandlers(errorLogger,LogManager.getLogger("ngat.net.TCPClientConnectionThreadMA"),null,
				Logging.ALL);
	// logLogger setup
		logLogger = LogManager.getLogger("log");
		logLogger.setChannelID(LOGGER_CHANNEL_ID);
		initLogHandlers(logLogger);
		logLogger.setLogLevel(status.getLogLevel());
	// library logging loggers
		copyLogHandlers(logLogger,LogManager.getLogger("ngat.net.TitServer"),null,Logging.ALL);
	// Sprat command handlers
		for(int index = 0; index < loggerNameStringArray.length; index ++)
		{
			copyLogHandlers(logLogger,LogManager.getLogger(loggerNameStringArray[index]),null,Logging.ALL);
		}
	}

	/**
	 * Method to create and add all the handlers for the specified logger.
	 * These handlers are in the status properties:
	 * "sprat.log."+l.getName()+".handler."+index+".name" retrieves the relevant class name
	 * for each handler.
	 * @param l The logger.
	 * @see #initFileLogHandler
	 * @see #initConsoleLogHandler
	 * @see #initDatagramLogHandler
	 */
	protected void initLogHandlers(Logger l)
	{
		LogHandler handler = null;
		String handlerName = null;
		int index = 0;

		do
		{
			handlerName = status.getProperty("sprat.log."+l.getName()+".handler."+index+".name");
			if(handlerName != null)
			{
				try
				{
					handler = null;
					if(handlerName.equals("ngat.util.logging.FileLogHandler"))
					{
						handler = initFileLogHandler(l,index);
					}
					else if(handlerName.equals("ngat.util.logging.ConsoleLogHandler"))
					{
						handler = initConsoleLogHandler(l,index);
					}
					else if(handlerName.equals("ngat.util.logging.MulticastLogHandler"))
					{
						handler = initMulticastLogHandler(l,index);
					}
					else if(handlerName.equals("ngat.util.logging.MulticastLogRelay"))
					{
						handler = initMulticastLogRelay(l,index);
					}
					else if(handlerName.equals("ngat.util.logging.DatagramLogHandler"))
					{
						handler = initDatagramLogHandler(l,index);
					}
					else
					{
						error("initLogHandlers:Unknown handler:"+handlerName);
					}
					if(handler != null)
					{
						handler.setLogLevel(Logging.ALL);
						l.addHandler(handler);
					}
				}
				catch(Exception e)
				{
					error("initLogHandlers:Adding Handler failed:",e);
				}
				index++;
			}
		}
		while(handlerName != null);
	}

	/**
	 * Routine to add a FileLogHandler to the specified logger.
	 * This method expects either 3 or 6 constructor parameters to be in the status properties.
	 * If there are 6 parameters, we create a record limited file log handler with parameters:
	 * <ul>
	 * <li><b>param.0</b> is the filename.
	 * <li><b>param.1</b> is the formatter class name.
	 * <li><b>param.2</b> is the record limit in each file.
	 * <li><b>param.3</b> is the start index for file suffixes.
	 * <li><b>param.4</b> is the end index for file suffixes.
	 * <li><b>param.5</b> is a boolean saying whether to append to files.
	 * </ul>
	 * If there are 3 parameters, we create a time period file log handler with parameters:
	 * <ul>
	 * <li><b>param.0</b> is the filename.
	 * <li><b>param.1</b> is the formatter class name.
	 * <li><b>param.2</b> is the time period, either 'HOURLY_ROTATION','DAILY_ROTATION' or 'WEEKLY_ROTATION'.
	 * </ul>
	 * @param l The logger to add the handler to.
	 * @param index The index in the property file of the handler we are adding.
	 * @return A LogHandler of the relevant class is returned, if no exception occurs.
	 * @exception NumberFormatException Thrown if the numeric parameters in the properties
	 * 	file are not valid numbers.
	 * @exception FileNotFoundException Thrown if the specified filename is not valid in some way.
	 * @see #status
	 * @see #initLogFormatter
	 * @see SpratStatus#getProperty
	 * @see SpratStatus#getPropertyInteger
	 * @see SpratStatus#getPropertyBoolean
	 * @see SpratStatus#propertyContainsKey
	 * @see SpratStatus#getPropertyLogHandlerTimePeriod
	 */
	protected LogHandler initFileLogHandler(Logger l,int index) throws NumberFormatException,
		FileNotFoundException
	{
		LogFormatter formatter = null;
		LogHandler handler = null;
		String fileName;
		int recordLimit,fileStart,fileLimit,timePeriod;
		boolean append;

		fileName = status.getProperty("sprat.log."+l.getName()+".handler."+index+".param.0");
		formatter = initLogFormatter("sprat.log."+l.getName()+".handler."+index+".param.1");
		// if we have more then 3 parameters, we are using a recordLimit FileLogHandler
		// rather than a time period log handler.
		if(status.propertyContainsKey("sprat.log."+l.getName()+".handler."+index+".param.3"))
		{
			recordLimit = status.getPropertyInteger("sprat.log."+l.getName()+".handler."+index+
								".param.2");
			fileStart = status.getPropertyInteger("sprat.log."+l.getName()+".handler."+index+".param.3");
			fileLimit = status.getPropertyInteger("sprat.log."+l.getName()+".handler."+index+".param.4");
			append = status.getPropertyBoolean("sprat.log."+l.getName()+".handler."+index+".param.5");
			handler = new FileLogHandler(fileName,formatter,recordLimit,fileStart,fileLimit,append);
		}
		else
		{
			// This is a time period log handler.
			timePeriod = status.getPropertyLogHandlerTimePeriod("sprat.log."+l.getName()+".handler."+
									    index+".param.2");
			handler = new FileLogHandler(fileName,formatter,timePeriod);
		}
		return handler;
	}

	/**
	 * Routine to add a MulticastLogHandler to the specified logger.
	 * The parameters to the constructor are stored in the status properties:
	 * <ul>
	 * <li>param.0 is the multicast group name i.e. "228.0.0.1".
	 * <li>param.1 is the port number i.e. 5000.
	 * <li>param.2 is the formatter class name.
	 * </ul>
	 * @param l The logger to add the handler to.
	 * @param index The index in the property file of the handler we are adding.
	 * @return A LogHandler of the relevant class is returned, if no exception occurs.
	 * @exception IOException Thrown if the multicast socket cannot be created for some reason.
	 */
	protected LogHandler initMulticastLogHandler(Logger l,int index) throws IOException
	{
		LogFormatter formatter = null;
		LogHandler handler = null;
		String groupName = null;
		int portNumber;

		groupName = status.getProperty("sprat.log."+l.getName()+".handler."+index+".param.0");
		portNumber = status.getPropertyInteger("sprat.log."+l.getName()+".handler."+index+".param.1");
		formatter = initLogFormatter("sprat.log."+l.getName()+".handler."+index+".param.2");
		handler = new MulticastLogHandler(groupName,portNumber,formatter);
		return handler;
	}

	/**
	 * Routine to add a MulticastLogRelay to the specified logger.
	 * The parameters to the constructor are stored in the status properties:
	 * <ul>
	 * <li>param.0 is the multicast group name i.e. "228.0.0.1".
	 * <li>param.1 is the port number i.e. 5000.
	 * </ul>
	 * @param l The logger to add the handler to.
	 * @param index The index in the property file of the handler we are adding.
	 * @return A LogHandler of the relevant class is returned, if no exception occurs.
	 * @exception IOException Thrown if the multicast socket cannot be created for some reason.
	 */
	protected LogHandler initMulticastLogRelay(Logger l,int index) throws IOException
	{
		LogHandler handler = null;
		String groupName = null;
		int portNumber;

		groupName = status.getProperty("sprat.log."+l.getName()+".handler."+index+".param.0");
		portNumber = status.getPropertyInteger("sprat.log."+l.getName()+".handler."+index+".param.1");
		handler = new MulticastLogRelay(groupName,portNumber);
		return handler;
	}

	/**
	 * Routine to add a DatagramLogHandler to the specified logger.
	 * The parameters to the constructor are stored in the status properties:
	 * <ul>
	 * <li>param.0 is the hostname i.e. "ltproxy".
	 * <li>param.1 is the port number i.e. 2371.
	 * </ul>
	 * @param l The logger to add the handler to.
	 * @param index The index in the property file of the handler we are adding.
	 * @return A LogHandler of the relevant class is returned, if no exception occurs.
	 * @exception IOException Thrown if the multicast socket cannot be created for some reason.
	 */
	protected LogHandler initDatagramLogHandler(Logger l,int index) throws IOException
	{
		LogHandler handler = null;
		String hostname = null;
		int portNumber;

		hostname = status.getProperty("sprat.log."+l.getName()+".handler."+index+".param.0");
		portNumber = status.getPropertyInteger("sprat.log."+l.getName()+".handler."+index+".param.1");
		handler = new DatagramLogHandler(hostname,portNumber);
		return handler;
	}

	/**
	 * Routine to add a ConsoleLogHandler to the specified logger.
	 * The parameters to the constructor are stored in the status properties:
	 * <ul>
	 * <li>param.0 is the formatter class name.
	 * </ul>
	 * @param l The logger to add the handler to.
	 * @param index The index in the property file of the handler we are adding.
	 * @return A LogHandler of class FileLogHandler is returned, if no exception occurs.
	 */
	protected LogHandler initConsoleLogHandler(Logger l,int index)
	{
		LogFormatter formatter = null;
		LogHandler handler = null;

		formatter = initLogFormatter("sprat.log."+l.getName()+".handler."+index+".param.0");
		handler = new ConsoleLogHandler(formatter);
		return handler;
	}

	/**
	 * Method to create an instance of a LogFormatter, given a property name
	 * to retrieve it's details from. If the property does not exist, or the class does not exist
	 * or an instance cannot be instansiated we try to return a ngat.util.logging.BogstanLogFormatter.
	 * @param propertyName A property name, present in the status's properties, 
	 * 	which has a value of a valid LogFormatter sub-class name. i.e.
	 * 	<pre>sprat.log.log.handler.0.param.1 =ngat.util.logging.BogstanLogFormatter</pre>
	 * @return An instance of LogFormatter is returned.
	 */
	protected LogFormatter initLogFormatter(String propertyName)
	{
		LogFormatter formatter = null;
		String formatterName = null;
		Class formatterClass = null;

		formatterName = status.getProperty(propertyName);
		if(formatterName == null)
		{
			error("initLogFormatter:NULL formatter for:"+propertyName);
			formatterName = "ngat.util.logging.BogstanLogFormatter";
		}
		try
		{
			formatterClass = Class.forName(formatterName);
		}
		catch(ClassNotFoundException e)
		{
			error("initLogFormatter:Unknown class formatter:"+formatterName+
				" from property "+propertyName);
			formatterClass = BogstanLogFormatter.class;
		}
		try
		{
			formatter = (LogFormatter)formatterClass.newInstance();
		}
		catch(Exception e)
		{
			error("initLogFormatter:Cannot create instance of formatter:"+formatterName+
				" from property "+propertyName);
			formatter = (LogFormatter)new BogstanLogFormatter();
		}
	// set better date format if formatter allows this.
	// Note we really need LogFormatter to generically allow us to do this
		if(formatter instanceof BogstanLogFormatter)
		{
			BogstanLogFormatter blf = (BogstanLogFormatter)formatter;

			blf.setDateFormat(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS z"));
		}
		if(formatter instanceof SimpleLogFormatter)
		{
			SimpleLogFormatter slf = (SimpleLogFormatter)formatter;

			slf.setDateFormat(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS z"));
		}
		return formatter;
	}

	/**
	 * Method to copy handlers from one logger to another. The outputLogger's channel ID is also
	 * copied from the input logger.
	 * @param inputLogger The logger to copy handlers from.
	 * @param outputLogger The logger to copy handlers to.
	 * @param lf The log filter to apply to the output logger. If this is null, the filter is not set.
	 * @param logLevel The log level to set the logger to filter against.
	 */
	protected void copyLogHandlers(Logger inputLogger,Logger outputLogger,LogFilter lf,int logLevel)
	{
		LogHandler handlerList[] = null;
		LogHandler handler = null;

		handlerList = inputLogger.getHandlers();
		for(int i = 0; i < handlerList.length; i++)
		{
			handler = handlerList[i];
			outputLogger.addHandler(handler);
		}
		outputLogger.setLogLevel(inputLogger.getLogLevel());
		if(lf != null)
			outputLogger.setFilter(lf);
		outputLogger.setChannelID(inputLogger.getChannelID());
		outputLogger.setLogLevel(logLevel);
	}

	/**
	 * This is the re-initialisation routine. This is called on a REDATUM level reboot, and
	 * does some of the operations in the init routine. It re-loads the Sprat configuration
	 * files, but NOT the network one. 
	 * It re-initialises default connection response times from properties file.
	 * The init method must be kept up to date with respect to this method.
	 * @exception FileNotFoundException Thrown if the property file cannot be found.
	 * @exception IOException Thrown if the property file cannot be accessed and the properties cannot
	 * 	be loaded for some reason.
	 * @exception Exception Thrown from FitsFilename.initialise, if the directory listing failed.
	 * @see #status
	 * @see #init
	 * @see #setLogLevel
	 */
	public void reInit() throws FileNotFoundException,IOException,NumberFormatException,Exception
	{
		int time;

	// reload properties into the status object
		try
		{
			status.reload();
		}
		catch(FileNotFoundException e)
		{
			error(this.getClass().getName()+":reinit:loading properties:",e);
			throw e;
		}
		catch(IOException e)
		{
			error(this.getClass().getName()+":reinit:loading properties:",e);
			throw e;
		}
	// don't change errorLogger to files defined in loaded properties
	// don't change logLogger to files defined in loaded properties
	// initialise sub-system loggers
		setLogLevel(logLevel);
	// initialise default connection response times from properties file
		try
		{
			time = status.getPropertyInteger("sprat.server_connection.default_acknowledge_time");
			SpratTCPServerConnectionThread.setDefaultAcknowledgeTime(time);
			time = status.getPropertyInteger("sprat.server_connection.min_acknowledge_time");
			SpratTCPServerConnectionThread.setMinAcknowledgeTime(time);
		}
		catch(NumberFormatException e)
		{
			error(this.getClass().getName()+":reinit:initialsing server connection thread times:",e);
			// don't throw the error - failing to get this property is not 'vital' to IO:I.
		}
	}

	/**
	 * This method creates the implementationList, and fills it with Class objects of sub-classes
	 * of CommandImplementation. The command implementation namess are retrieved from the Sprat property files,
	 * using keys of the form <b>sprat.command.implmentation.&lt;<i>N</i>&gt;</b>, where <i>N</i> is
	 * an integer is incremented. It puts the class object reference in the Hashtable with the 
	 * results of it's getImplementString static method as the key. 
	 * If an implementation object class fails to be put in the hashtable for some reason it ignores it and 
	 * continues for the next object in the list.
	 * @see #implementationList
	 * @see CommandImplementation#getImplementString
	 */
	private void initImplementationList()
	{
		Class cl = null;
		Class oldClass = null;
		Method method = null;
		Class methodClassParameterList[] = {};
		Object methodParameterList[] = {};
		String implementString = null;
		String className = null;
		int index;
		boolean done;

		implementationList = new Hashtable<String,Class>();
		index = 0;
		done = false;
		while(done == false)
		{
			className = status.getProperty("sprat.command.implmentation."+index);
			if(className != null)
			{
				try
				{
				// get Class object associated with class name
					cl = Class.forName(className);
				// get method object associated with getImplementString method of cl.
					method = cl.getDeclaredMethod("getImplementString",methodClassParameterList);
				// invoke getImplementString class method to get ngat.message class name it implements
					implementString = (String)method.invoke(null,methodParameterList);
				// put key and class into implementationList
					oldClass = (Class)implementationList.put(implementString,cl);
					if(oldClass != null)// the put returned another class with the same key.
					{
						error(this.getClass().getName()+":initImplementationList:Classes "+
							oldClass.getName()+" and "+cl.getName()+
							" both implement command:"+implementString);
					}
				}
				catch(ClassNotFoundException e)//Class.forName exception
				{
					error(this.getClass().getName()+":initImplementationList:Class "+
						className+":ClassNotFoundException:",e);
					// keep trying for next implementation in the list
				}
				catch(NoSuchMethodException e)//Class.getDeclaredMethod exception
				{
					error(this.getClass().getName()+":initImplementationList:Class "+
						className+":NoSuchMethodException:",e);
					// keep trying for next implementation in the list
				}
				catch(SecurityException e)//Class.getDeclaredMethod exception
				{
					error(this.getClass().getName()+":initImplementationList:Class "+
						className+":SecurityException:",e);
					// keep trying for next implementation in the list
				}
				catch(NullPointerException e)// Hashtable.put exception - null key.
				{
					error(this.getClass().getName()+":initImplementationList:Class "+
						cl.getName()+" implement string is null?:",e);
					// keep trying for next implementation in the list
				}
				catch(IllegalAccessException e)// Method.invoke exception
				{
					error(this.getClass().getName()+":initImplementationList:Class "+
						cl.getName()+":IllegalAccessException:",e);
					// keep trying for next implementation in the list
				}
				catch(IllegalArgumentException e)// Method.invoke exception
				{
					error(this.getClass().getName()+":initImplementationList:Class "+
						cl.getName()+":IllegalArgumentException:",e);
					// keep trying for next implementation in the list
				}
				catch(InvocationTargetException e)// Method.invoke exception
				{
					error(this.getClass().getName()+":initImplementationList:Class "+
						cl.getName()+":InvocationTargetException:",e);
					// keep trying for next implementation in the list
				}
			// try next class name in list
				index++;
			}
			else
				done = true;
		}// end while not done
	}

	/**
	 * Method to initialise the mechanisms and CCD control C layer.
	 * <ul>
	 * <li>
	 * </ul>
	 * @exception Exception Thrown if an error occurs
	 * @see #status
	 * @see SpratStatus#getProperty
	 * @see SpratStatus#getPropertyBoolean
	 * @see SpratStatus#getPropertyInteger
	 */
	public void startupController() throws Exception
	{
	}

	/**
	 * Method to shutdown the low level software.
	 */
	public void shutdownController() throws Exception
	{
	}

	/**
	 * This is the run routine. It starts a new server to handle incoming requests, and waits for the
	 * server to terminate.
	 * @see #server
	 * @see #spratPortNumber
	 * @see #titServer
	 * @see #titPortNumber
	 */
	private void run()
	{
		Date nowDate = null;

		server = new SpratTCPServer("SPRAT",spratPortNumber);
		server.setSprat(this);
		server.setPriority(status.getThreadPriorityServer());
		titServer = new TitServer("TitServer on port "+titPortNumber,titPortNumber);
		titServer.setPriority(status.getThreadPriorityTIT());
		nowDate = new Date();
		log(Logging.VERBOSITY_VERY_TERSE,
			this.getClass().getName()+":run:server started at:"+nowDate.toString());
		log(Logging.VERBOSITY_VERY_TERSE,
			this.getClass().getName()+":run:server started on port:"+spratPortNumber);
		error(this.getClass().getName()+":run:server started at:"+nowDate.toString());
		error(this.getClass().getName()+":run:server started on port:"+spratPortNumber);
		server.start();
		titServer.start();
		try
		{
			log(Logging.VERBOSITY_VERY_TERSE,
			    this.getClass().getName()+":run:about to wait on server join.");
			server.join();
			log(Logging.VERBOSITY_VERY_TERSE,
			    this.getClass().getName()+":run:server has joined.");
		}
		catch(InterruptedException e)
		{
			error(this.getClass().getName()+":run:",e);
		}
	}

	/**
	 * Routine to be called at the end of execution of Sprat to close down communications.
	 * Currently calls shutdownController, and then closes SpratTCPServer and TitServer.
	 * @see SpratTCPServer#close
	 * @see #shutdownController
	 * @see #server
	 * @see TitServer#close
	 * @see #titServer
	 */
	public void close()
	{
		try
		{
			shutdownController();
		}
		catch(Exception e)
		{
			error(this.getClass().getName()+":close:",e);
		}
		server.close();
		titServer.close();
	}

	/**
	 * Get Socket Server instance.
	 * @return The server instance.
	 * @see #server
	 */
	public SpratTCPServer getServer()
	{
		return server;
	}

	/**
	 * Get status instance.
	 * @return The status instance.
	 * @see #status
	 */
	public SpratStatus getStatus()
	{
		return status;
	}

	/**
	 * This routine returns an instance of the sub-class of CommandImplementation that
	 * implements the command with class name commandClassName. If an implementation is
	 * not found or an instance cannot be created, an instance of UnknownCommandImplementation is returned instead.
	 * The instance is constructed 
	 * using a null argument constructor, from the Class object stored in the implementationList.
	 * @param commandClassName The class-name of a COMMAND sub-class.
	 * @return A new instance of a sub-class of CommandImplementation that implements the 
	 * 	command, or an instance of UnknownCommandImplementation.
	 */
	public JMSCommandImplementation getImplementation(String commandClassName)
	{
		JMSCommandImplementation unknownCommandImplementation = new UnknownCommandImplementation();
		JMSCommandImplementation object = null;
		Class cl = null;

		cl = (Class)implementationList.get(commandClassName);
		if(cl != null)
		{
			try
			{
				object = (JMSCommandImplementation)cl.newInstance();
			}
			catch(InstantiationException e)//Class.newInstance exception
			{
				error(this.getClass().getName()+":getImplementation:Class "+
				      cl.getName()+":InstantiationException:",e);
				object = null;
			}
			catch(IllegalAccessException e)//Class.newInstance exception
			{
				error(this.getClass().getName()+":getImplementation:Class "+
				      cl.getName()+":IllegalAccessException:",e);
				object = null;
			}
		}// end if found class
		if(object != null)
			return object;
		else
			return unknownCommandImplementation;
	}

	/**
	 * Method to set the level of logging filtered. The status, log and error loggers have their filters set.
	 * @param level An integer, used as an absolute value (0..5).
	 * @see #status
	 * @see #logLogger
	 * @see #errorLogger
	 * @see ngat.sprat.SpratStatus#setLogLevel
	 */
	public void setLogLevel(int level)
	{
		status.setLogLevel(level);
		logLogger.setLogLevel(level);
		errorLogger.setLogLevel(level);
	}

	/**
	 * Routine to send a command from the instrument (this application/Sprat) to the ISS. The routine
	 * waits until the command's done message has been returned from the ISS and returns this.
	 * If the commandThread is aborted this also stops waiting for the done message to be returned.
	 * @param command The command to send to the ISS.
	 * @param commandThread The thread the passed in command (and this method) is running on.
	 * @return The done message returned from te ISS, or an error message created by this routine
	 * 	if the done was null.
	 * @see #issAddress
	 * @see #issPortNumber
	 * @see #sendISSCommand(INST_TO_ISS,SpratTCPServerConnectionThread,boolean,boolean)
	 * @see SpratTCPClientConnectionThread
	 * @see SpratTCPServerConnectionThread#getAbortProcessCommand
	 */
	public INST_TO_ISS_DONE sendISSCommand(INST_TO_ISS command,SpratTCPServerConnectionThread commandThread)
	{
		return sendISSCommand(command,commandThread,true,true);
	}

	/**
	 * Routine to send a command from the instrument (this application/Sprat) to the ISS. If waitForDone is true,
	 * the method waits until the command's done message has been returned from the ISS and returns this.
	 * If checkAbort is set and the commandThread is aborted this stops waiting for the 
	 * done message to be returned and an error is returned. 
	 * If waitForDone is false, a fake DONE is created and returned.
	 * @param command The command to send to the ISS.
	 * @param commandThread The thread the passed in command (and this method) is running on.
	 * @param checkAbort A boolean, set to true if we want to check for commandThread aborting.
	 * 	This should be set to false when the command is being sent to the ISS in response
	 * 	to an abort occuring.
	 * @param waitForDone A boolean. If true we do NOT wait for the spawned ISS command to return a DONE
	 *        message. This allows us to reduce overheads, for instance when sending OFFSET_RA_DEC commands.
	 * @return The done message returned from the ISS, a fake DONE if waitForDone is true,
	 *      or an error message created by this routine if the done was null.
	 * @see #issAddress
	 * @see #issPortNumber
	 * @see SpratTCPClientConnectionThread
	 * @see SpratTCPServerConnectionThread#getAbortProcessCommand
	 */
	public INST_TO_ISS_DONE sendISSCommand(INST_TO_ISS command,SpratTCPServerConnectionThread commandThread,
					       boolean checkAbort,boolean waitForDone)
	{
		SpratTCPClientConnectionThread thread = null;
		INST_TO_ISS_DONE done = null;
		boolean finished = false;

		log(Logging.VERBOSITY_VERY_TERSE,
			this.getClass().getName()+":sendISSCommand:"+command.getClass().getName());
		thread = new SpratTCPClientConnectionThread(issAddress,issPortNumber,command,commandThread);
		thread.setSprat(this);
		thread.start();
		if(waitForDone)
		{
			finished = false;
			while(finished == false)
			{
				try
				{
					thread.join(100);// wait 100 millis for the thread to finish
				}
				catch(InterruptedException e)
				{
					error("run:join interrupted:",e);
				}
				// If the thread has finished so has this loop
				finished = (thread.isAlive() == false);
				// check if the thread has been aborted, if checkAbort has been set.
				if(checkAbort)
				{
					// If the commandThread has been aborted, stop processing this thread
					if(commandThread.getAbortProcessCommand())
						finished = true;
				}
			}// end while
			done = (INST_TO_ISS_DONE)thread.getDone();
			if(done == null)
			{
				// one reason the done is null is if we escaped from the loop
				// because the IO:I server thread was aborted.
				if(commandThread.getAbortProcessCommand())
				{
					done = new INST_TO_ISS_DONE(command.getId());
					error(this.getClass().getName()+":sendISSCommand:"+
					      command.getClass().getName()+":Server thread Aborted");
					done.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+1);
					done.setErrorString("sendISSCommand:Server thread Aborted:"+
							    command.getClass().getName());
					done.setSuccessful(false);		
				}
				else // a communication failure occured
				{
					done = new INST_TO_ISS_DONE(command.getId());
					error(this.getClass().getName()+":sendISSCommand:"+
					      command.getClass().getName()+":Getting Done failed");
					done.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+2);
					done.setErrorString("sendISSCommand:Getting Done failed:"+
							    command.getClass().getName());
					done.setSuccessful(false);
				}
			}// end if done is null
		}
		else // waitForDone is false, return a fake DONE
		{
			done = new INST_TO_ISS_DONE(command.getId());
			log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":sendISSCommand:"+
			      command.getClass().getName()+":Wait for done was false, faking a successful DONE.");
			done.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_NO_ERROR);
			done.setErrorString("sendISSCommand:Wait for Done was false, faking successful DONE for:"+
					    command.getClass().getName());
			done.setSuccessful(true);
		}
		log(Logging.VERBOSITY_TERSE,"Done:"+done.getClass().getName()+":successful:"+done.getSuccessful()+
		    ":error number:"+done.getErrorNum()+":error string:"+done.getErrorString());
		return done;
	}

	/**
	 * Routine to send a command from the instrument (this application/Sprat) to the DP(RT). The routine
	 * waits until the command's done message has been returned from the DP(RT) and returns this.
	 * If the commandThread is aborted this also stops waiting for the done message to be returned.
	 * @param command The command to send to the DP(RT).
	 * @param commandThread The thread the passed in command (and this method) is running on.
	 * @return The done message returned from te DP(RT), or an error message created by this routine
	 * 	if the done was null.
	 * @see #dprtAddress
	 * @see #dprtPortNumber
	 * @see SpratTCPClientConnectionThread
	 * @see SpratTCPServerConnectionThread#getAbortProcessCommand
	 */
	public INST_TO_DP_DONE sendDpRtCommand(INST_TO_DP command,SpratTCPServerConnectionThread commandThread)
	{
		SpratTCPClientConnectionThread thread = null;
		INST_TO_DP_DONE done = null;
		boolean finished = false;

		log(Logging.VERBOSITY_VERY_TERSE,
			this.getClass().getName()+":sendDpRtCommand:"+command.getClass().getName());
		thread = new SpratTCPClientConnectionThread(dprtAddress,dprtPortNumber,command,commandThread);
		thread.setSprat(this);
		thread.start();
		finished = false;
		while(finished == false)
		{
			try
			{
				thread.join(100);// wait 100 millis for the thread to finish
			}
			catch(InterruptedException e)
			{
				error("run:join interrupted:",e);
			}
		// If the thread has finished so has this loop
			finished = (thread.isAlive() == false);
		// If the commandThread has been aborted, stop processing this thread
			if(commandThread.getAbortProcessCommand())
				finished = true;
		}
		done = (INST_TO_DP_DONE)thread.getDone();
		if(done == null)
		{
			// one reason the done is null is if we escaped from the loop
			// because the Sprat server thread was aborted.
			if(commandThread.getAbortProcessCommand())
			{
				done = new INST_TO_DP_DONE(command.getId());
				error(this.getClass().getName()+":sendDpRtCommand:"+
					command.getClass().getName()+":Server thread Aborted");
				done.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+5);
				done.setErrorString("sendDpRtCommand:Server thread Aborted:"+
					command.getClass().getName());
				done.setSuccessful(false);
			}
			else // a communication failure occured
			{
				done = new INST_TO_DP_DONE(command.getId());
				error(this.getClass().getName()+":sendDpRtCommand:"+
					command.getClass().getName()+":Getting Done failed");
				done.setErrorNum(SpratConstants.SPRAT_ERROR_CODE_BASE+6);
				done.setErrorString("sendDpRtCommand:Getting Done failed:"+
					command.getClass().getName());
				done.setSuccessful(false);
			}
		}
		log(Logging.VERBOSITY_VERY_TERSE,
			"Done:"+done.getClass().getName()+":successful:"+done.getSuccessful()+
			":error number:"+done.getErrorNum()+":error string:"+done.getErrorString());
		return done;
	}

	/**
	 * Routine to write the string to the relevant logger. If the relevant logger has not been
	 * created yet the error gets written to System.out.
	 * @param level The level of logging this message belongs to.
	 * @param s The string to write.
	 * @see #logLogger
	 */
	public void log(int level,String s)
	{
		if(logLogger != null)
			logLogger.log(level,s);
		else
		{
			if((status.getLogLevel()&level) > 0)
				System.out.println(s);
		}
	}

	/**
	 * Routine to write the string to the relevant logger. If the relevant logger has not been
	 * created yet the error gets written to System.err.
	 * @param s The string to write.
	 * @see #errorLogger
	 */
	public void error(String s)
	{
		if(errorLogger != null)
			errorLogger.log(Logging.VERBOSITY_VERY_TERSE,s);
		else
			System.err.println(s);
	}

	/**
	 * Routine to write the string to the relevant logger. If the relevant logger has not been
	 * created yet the error gets written to System.err.
	 * @param s The string to write.
	 * @param e An exception that caused the error to occur.
	 * @see #errorLogger
	 */
	public void error(String s,Exception e)
	{
		if(errorLogger != null)
		{
			errorLogger.log(Logging.VERBOSITY_VERY_TERSE,s,e);
			errorLogger.dumpStack(Logging.VERBOSITY_VERY_TERSE,e);
		}
		else
		{
			System.err.println(s);
			e.printStackTrace(System.err);
		}
	}


	/**
	 * Help message routine. Prints all command line arguments.
	 */
	public void help()
	{
		System.out.println("Sprat Help:");
		System.out.println("Arguments are:");
		System.out.println("\t-l[og] <log level> - log level.");
	}

	/**
	 * Parse the arguments.
	 * @param args The string list of arguments.
	 * @see #logLevel
	 */
	private void parseArguments(String args[])
	{
		for(int i = 0; i < args.length;i++)
		{
			if(args[i].equals("-l")||args[i].equals("-log"))
			{
				if((i+1)< args.length)
				{
					logLevel = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					System.err.println("-log requires a log level");
			}
			else if(args[i].equals("-h")||args[i].equals("-help"))
			{
				help();
				System.exit(0);
			}
			else
				System.err.println("Sprat '"+args[i]+"' not a recognised option");
		}// end for
	}

	/**
	 * The main routine, called when Sprat is executed. This creates a new instance of the Sprat class.
	 * It calls the following methods:
	 * <ul>
	 * <li>Calls parseArguments.
	 * <li>init.
	 * <li>startupController.
	 * <li>run.
	 * </ul>
	 * @see #init
	 * @see #parseArguments
	 * @see #run
	 */
	public static void main(String[] args)
	{
		Sprat sprat = new Sprat();

		try
		{
			sprat.parseArguments(args);
			sprat.init();
		}
		catch(Exception e)
		{
 			sprat.error("main:init failed:",e);
			System.exit(1);
		}
		try
		{
			sprat.startupController();
		}
		catch(Exception e)
		{
 			sprat.error("main:startupController failed:",e);
			System.exit(1);
		}
		sprat.run();
	// We get here if the server thread has terminated. If it has been quit
	// this is a successfull termination, otherwise an error has occured.
	// Note the program can also be terminated from within a REBOOT call.
		if(sprat.server.getQuit() == false)
			System.exit(1);
	}
}
