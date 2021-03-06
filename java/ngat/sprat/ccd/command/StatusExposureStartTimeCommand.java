// StatusExposureStartTimeCommand.java
// $HeadURL$
package ngat.sprat.ccd.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;

import ngat.util.logging.*;

/**
 * The "status exposure start_time" command is an extension of the Command, and returns the 
 * start_time of the current/last exposure as a timestamp.
 * @author Chris Mottram
 * @version $Revision$
 */
public class StatusExposureStartTimeCommand extends Command implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The command to send to the server.
	 */
	public final static String COMMAND_STRING = new String("status exposure start_time");
	/**
	 * The logger to log messages to.
	 */
	protected Logger logger = null;
	/**
	 * The parsed reply timestamp.
	 */
	protected Date parsedReplyTimestamp = null;

	/**
	 * Default constructor.
	 * @see #logger
	 * @see Command
	 * @see #commandString
	 * @see #COMMAND_STRING
	 */
	public StatusExposureStartTimeCommand()
	{
		super();
		logger = LogManager.getLogger(this);
		commandString = COMMAND_STRING;
	}

	/**
	 * Constructor.
	 * @param address A string representing the address of the server, i.e. "sprat1",
	 *     "localhost", "192.168.1.62"
	 * @param portNumber An integer representing the port number the server is receiving command on.
	 * @see Command
	 * @see #logger
	 * @see #COMMAND_STRING
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public StatusExposureStartTimeCommand(String address,int portNumber) throws UnknownHostException
	{
		super(address,portNumber,COMMAND_STRING);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Parse a string returned from the server over the telnet connection.
	 * In this case it is of the form: '&lt;n&gt; %Y-%m-%dT%H:%M:%S.sss'
	 * The first number is a success failure code, if it is zero a timestamp follows.
	 * @exception Exception Thrown if a parse error occurs.
	 * @see #replyString
	 * @see #parsedReplyString
	 * @see #parsedReplyOk
	 * @see #parsedReplyTimestamp
	 */
	public void parseReplyString() throws Exception
	{
		TimeZone timeZone = null;
		Calendar calendar = null;
		StringTokenizer st = null;
		String timeStampString = null;
		String temperatureString = null;
		double second=0.0;
		int sindex,tokenIndex,day=0,month=0,year=0,hour=0,minute=0;
		
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,
			   "ngat.sprat.ccd.command.StatusExposureStartTimeCommand:Started.");
		super.parseReplyString();
		if(parsedReplyOk == false)
		{
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,
				   "ngat.sprat.ccd.command.StatusExposureStartTimeCommand:"+
				   "Superclass failed to parse, returning null.");
			parsedReplyTimestamp = null;
			return;
		}
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,
			   "ngat.sprat.ccd.command.StatusExposureStartTimeCommand:Parsing:'"+parsedReplyString+"'.");
		st = new StringTokenizer(parsedReplyString," ");
		tokenIndex = 0;
		while(st.hasMoreTokens())
		{
			if(tokenIndex == 0)
				timeStampString = st.nextToken();
			else
				st.nextToken();
			tokenIndex++;
		}// end while
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.ccd.command.StatusExposureStartTimeCommand:"+
			   "Time stamp string:'"+timeStampString+"'.");
		// timeStampString should be of the form: %Y-%m-%dT%H:%M:%S.sss
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
			else
				st.nextToken();
			tokenIndex++;
		}// end while
		// create calendar - ensure we get a UTC/GMT one
		timeZone = TimeZone.getTimeZone("GMT+00");
		calendar = Calendar.getInstance(timeZone);
		// set calendar
		calendar.set(year,month-1,day,hour,minute,(int)second);// month is zero-based.
		// get timestamp from calendar 
		parsedReplyTimestamp = calendar.getTime();
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.ccd.command.StatusExposureStartTimeCommand:"+
			   "Finished:Parsed time stamp:'"+parsedReplyTimestamp+"'.");
	}

	/**
	 * Get the timestamp representing the start time of the current/last 
	 * exposure (internal trigger) / multrun (external trigger).
	 * @return A date.
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
		StatusExposureStartTimeCommand command = null;
		int portNumber = 8367;

		if(args.length != 2)
		{
			System.out.println("java ngat.sprat.ccd.command.StatusExposureStartTimeCommand <hostname> <port number>");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			portNumber = Integer.parseInt(args[1]);
			command = new StatusExposureStartTimeCommand(args[0],portNumber);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("StatusExposureStartTimeCommand: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Reply Parsed OK:"+command.getParsedReplyOK());
			System.out.println("Start Time stamp:"+command.getTimestamp());
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);

	}
}
