// StatusTemperatureStatusCommand.java
// $HeadURL$
package ngat.sprat.ccd.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;

import ngat.util.logging.*;

/**
 * The "status temperature status" command is an extension of the Command, and returns the 
 * current temperature status, and a timestamp stating when the temperature was measured.
 * @author Chris Mottram
 * @version $Revision$
 */
public class StatusTemperatureStatusCommand extends Command implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The command to send to the server.
	 */
	public final static String COMMAND_STRING = new String("status temperature status");
	/**
	 * Temperature status.
	 */
	public final static int CCD_TEMPERATURE_STATUS_OFF = 0;
	/**
	 * Temperature status.
	 */
	public final static int CCD_TEMPERATURE_STATUS_AMBIENT = 1;
	/**
	 * Temperature status.
	 */
	public final static int CCD_TEMPERATURE_STATUS_OK = 2;
	/**
	 * Temperature status.
	 */
	public final static int CCD_TEMPERATURE_STATUS_RAMPING = 3;
	/**
	 * Temperature status.
	 */
	public final static int CCD_TEMPERATURE_STATUS_UNKNOWN = 4;
	/**
	 * The logger to log messages to.
	 */
	protected Logger logger = null;
	/**
	 * The parsed reply timestamp.
	 */
	protected Date parsedReplyTimestamp = null;
	/**
	 * The parsed reply temperature status string.
	 */
	protected String parsedReplyTemperatureStatusString = null;
	/**
	 * The parsed reply temperature status.
	 * @see #CCD_TEMPERATURE_STATUS_UNKNOWN
	 */
	protected int parsedReplyTemperatureStatus = CCD_TEMPERATURE_STATUS_UNKNOWN;

	/**
	 * Default constructor.
	 * @see #logger
	 * @see Command
	 * @see #commandString
	 * @see #COMMAND_STRING
	 */
	public StatusTemperatureStatusCommand()
	{
		super();
		commandString = COMMAND_STRING;
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor.
	 * @param address A string representing the address of the server, i.e. "sprat1",
	 *     "localhost", "192.168.1.62"
	 * @param portNumber An integer representing the port number the server is receiving command on.
	 * @see #logger
	 * @see Command
	 * @see #COMMAND_STRING
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public StatusTemperatureStatusCommand(String address,int portNumber) throws UnknownHostException
	{
		super(address,portNumber,COMMAND_STRING);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Parse a string returned from the server over the telnet connection.
	 * In this case it is of the form: '<n> %Y-%m-%dT%H:%M:%S.sss <status>'
	 * The first number is a success failure code, if it is zero a timestamp and temperature status follows.
	 * @exception Exception Thrown if a parse error occurs.
	 * @see #logger
	 * @see #replyString
	 * @see #parsedReplyString
	 * @see #parsedReplyOk
	 * @see #parsedReplyTimestamp
	 * @see #parsedReplyTemperatureStatusString
	 * @see #parsedReplyTemperatureStatus
	 * @see #CCD_TEMPERATURE_STATUS_OFF
	 * @see #CCD_TEMPERATURE_STATUS_AMBIENT
	 * @see #CCD_TEMPERATURE_STATUS_OK
	 * @see #CCD_TEMPERATURE_STATUS_RAMPING
	 * @see #CCD_TEMPERATURE_STATUS_UNKNOWN
	 */
	public void parseReplyString() throws Exception
	{
		Calendar calendar = null;
		StringTokenizer st = null;
		String timeStampString = null;
		double second=0.0;
		int sindex,tokenIndex,day=0,month=0,year=0,hour=0,minute=0;
		
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,
			   "ngat.sprat.ccd.command.StatusTemperatureStatusCommand:parseReplyString:Started.");
		super.parseReplyString();
		if(parsedReplyOk == false)
		{
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,
				   "ngat.sprat.ccd.command.StatusTemperatureStatusCommand:parseReplyString:"+
				   "Superclass failed to parse reply successfully.");
			parsedReplyTimestamp = null;
			parsedReplyTemperatureStatus = 0;
			parsedReplyTemperatureStatusString = null;
			return;
		}
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,
			   "ngat.sprat.ccd.command.StatusTemperatureStatusCommand:parseReplyString:Parsing '"+
			   parsedReplyString+"'.");
		st = new StringTokenizer(parsedReplyString," ");
		tokenIndex = 0;
		while(st.hasMoreTokens())
		{
			if(tokenIndex == 0)
				timeStampString = st.nextToken();
			else if(tokenIndex == 1)
				parsedReplyTemperatureStatusString = st.nextToken();
			tokenIndex++;
		}// end while
		// timeStampString should be of the form: %Y-%m-%dT%H:%M:%S.sss
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.ccd.command.StatusTemperatureStatusCommand:"+
			   "parseReplyString:Parsing timestamp string '"+timeStampString+"'.");
		st = new StringTokenizer(timeStampString,"-T:");
		tokenIndex = 0;
		while(st.hasMoreTokens())
		{
			if(tokenIndex == 0)
				year = Integer.parseInt(st.nextToken());// year including century
			else if(tokenIndex == 1)
				month = Integer.parseInt(st.nextToken());// 01..12
			else if(tokenIndex == 2)
				day = Integer.parseInt(st.nextToken());// 0..31
			else if(tokenIndex == 3)
				hour = Integer.parseInt(st.nextToken());// 0..23
			else if(tokenIndex == 4)
				minute = Integer.parseInt(st.nextToken());// 00..59
			else if(tokenIndex == 5)
				second = Double.parseDouble(st.nextToken());// 00..61 + milliseconds as decimal
			tokenIndex++;
		}// end while
		// create calendar
		calendar = Calendar.getInstance();
		// set calendar
		calendar.set(year,month-1,day,hour,minute,(int)second);// month is zero-based.
		// get timestamp from calendar 
		parsedReplyTimestamp = calendar.getTime();
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.ccd.command.StatusTemperatureStatusCommand:"+
			   "parseReplyString:Parsed timestamp: '"+parsedReplyTimestamp+"'.");
		// parse temperature status string into temperature status
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.ccd.command.StatusTemperatureGetCommand:"+
			   "parseReplyString:Parsing temperature status string: '"+
			   parsedReplyTemperatureStatusString+"'.");
		if(parsedReplyTemperatureStatusString.equals("OFF"))
			parsedReplyTemperatureStatus = CCD_TEMPERATURE_STATUS_OFF;
		else if(parsedReplyTemperatureStatusString.equals("AMBIENT"))
			parsedReplyTemperatureStatus = CCD_TEMPERATURE_STATUS_AMBIENT;
		else if(parsedReplyTemperatureStatusString.equals("OK"))
			parsedReplyTemperatureStatus = CCD_TEMPERATURE_STATUS_OK;
		else if(parsedReplyTemperatureStatusString.equals("RAMPING"))
			parsedReplyTemperatureStatus = CCD_TEMPERATURE_STATUS_RAMPING;
		else if(parsedReplyTemperatureStatusString.equals("UNKNOWN"))
			parsedReplyTemperatureStatus = CCD_TEMPERATURE_STATUS_UNKNOWN;
		else
			parsedReplyTemperatureStatus = CCD_TEMPERATURE_STATUS_UNKNOWN;
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.ccd.command.StatusTemperatureGetCommand:"+
			   "parseReplyString:Parsed temperature status string: '"+
			   parsedReplyTemperatureStatusString+"' with status value "+parsedReplyTemperatureStatus+".");
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,
			   "ngat.sprat.ccd.command.StatusTemperatureStatusCommand:parseReplyString:Finished.");
	}

	/**
	 * Get the temperature status of the CCD at the specified timestamp.
	 * @return An integer, a temperature status.
	 * @exception Exception Thrown if getting the data fails, either the run method failed to communicate
	 *         with the server in some way, or the method was called before the command had completed.
	 * @see #parsedReplyOk
	 * @see #runException
	 * @see #parsedReplyTemperatureStatus
	 * @see #CCD_TEMPERATURE_STATUS_OFF
	 * @see #CCD_TEMPERATURE_STATUS_AMBIENT
	 * @see #CCD_TEMPERATURE_STATUS_OK
	 * @see #CCD_TEMPERATURE_STATUS_RAMPING
	 * @see #CCD_TEMPERATURE_STATUS_UNKNOWN
	 */
	public int getTemperatureStatus() throws Exception
	{
		if(parsedReplyOk)
		{
			return parsedReplyTemperatureStatus;
		}
		else
		{
			if(runException != null)
				throw runException;
			else
				throw new Exception(this.getClass().getName()+":getTemperatureStatus:Unknown Error.");
		}
	}

	/**
	 * Get the temperature status of the CCD at the specified timestamp.
	 * @return An string representing a temperature status. One of: OFF, AMBIENT, OK, RAMPING, UNKNOWN.
	 * @exception Exception Thrown if getting the data fails, either the run method failed to communicate
	 *         with the server in some way, or the method was called before the command had completed.
	 * @see #parsedReplyOk
	 * @see #runException
	 * @see #parsedReplyTemperatureStatusString
	 */
	public String getTemperatureStatusString() throws Exception
	{
		if(parsedReplyOk)
		{
			return parsedReplyTemperatureStatusString;
		}
		else
		{
			if(runException != null)
				throw runException;
			else
				throw new Exception(this.getClass().getName()+
						    ":getTemperatureStatusString:Unknown Error.");
		}
	}

	/**
	 * Get the timestamp representing the time the temperature of the CCD was measured.
	 * @return A date, the time the temperature of the CCD was measured.
	 * @exception Exception Thrown if getting the data fails, either the run method failed to communicate
	 *         with the server in some way, or the method was called before the command had completed.
	 * @see #parsedReplyOk
	 * @see #runException
	 * @see #parsedReplyTimestamp
	 */
	public Date getTimestamp() throws Exception
	{
		if(parsedReplyOk)
		{
			return parsedReplyTimestamp;
		}
		else
		{
			if(runException != null)
				throw runException;
			else
				throw new Exception(this.getClass().getName()+":getTimestamp:Unknown Error.");
		}
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		StatusTemperatureStatusCommand command = null;
		int portNumber = 8367;

		if(args.length != 2)
		{
			System.out.println("java ngat.sprat.ccd.command.StatusTemperatureStatusCommand <hostname> <port number>");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			portNumber = Integer.parseInt(args[1]);
			command = new StatusTemperatureStatusCommand(args[0],portNumber);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("StatusTemperatureStatusCommand: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Reply Parsed OK:"+command.getParsedReplyOK());
			System.out.println("Temperature Status:"+command.getTemperatureStatus());
			System.out.println("Temperature Status String:"+command.getTemperatureStatusString());
			System.out.println("At Timestamp:"+command.getTimestamp());
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);
	}
}
