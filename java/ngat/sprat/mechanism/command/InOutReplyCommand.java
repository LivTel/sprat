// InOutReplyCommand.java
// $HeadURL$
package ngat.sprat.mechanism.command;

import java.io.*;
import java.lang.*;
import java.net.*;

import ngat.net.TelnetConnection;
import ngat.net.TelnetConnectionListener;
import ngat.util.logging.*;

/**
 * The InOutReplyCommandclass is an extension of the base Command class for sending a command and getting a 
 * reply from the Sprat mechanism Arduino. This is a telnet - type socket interaction. 
 * InOutReplyCommand expects the reply to be one of 'in|out|unknown|error [error description]'.
 * @author Chris Mottram
 * @version $Revision$
 */
public class InOutReplyCommand extends Command implements Runnable, TelnetConnectionListener
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Position state - in this case, an error has occured whilst determining the position.
	 */
	public final static int POSITION_ERROR = -2;
	/**
	 * Position state - in this case, an the position could not be determined.
	 */
	public final static int POSITION_UNKNOWN = -1;
	/**
	 * Position state - in this case, the mechanism is IN the beam.
	 * This value should mirror the one in ngat.phase2.SpratConfig.
	 * @see ngat.phase2.SpratConfig#POSITION_IN
	 */
	public final static int POSITION_IN = 1;
	/**
	 * Position state - in this case, the mechanism is OUT of the beam.
	 * This value should mirror the one in ngat.phase2.SpratConfig.
	 * @see ngat.phase2.SpratConfig#POSITION_OUT
	 */
	public final static int POSITION_OUT = 2;
	/**
	 * The logger to log messages to.
	 */
	protected Logger logger = null;
	/**
	 * The current position returned from the Arduino.
	 */
	protected int currentPosition = POSITION_UNKNOWN;

	/**
	 * Default constructor.
	 * @see #logger
	 * @see Command
	 */
	public InOutReplyCommand()
	{
		super();
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor.
	 * @param address A string representing the IP address of the Arduino, i.e. "spratmechanism", "192.168.1.77".
	 * @param portNumber An integer representing the port number the Arduino is receiving command on.
	 * @see #logger
	 * @see Command
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public InOutReplyCommand(String address,int portNumber) throws UnknownHostException
	{
		super(address,portNumber);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor.
	 * @param address A string representing the IP address of the Arduino, i.e. "spratmechanism", "192.168.1.77".
	 * @param portNumber An integer representing the port number the Arduino is receiving command on.
	 * @param commandString The string to send to the server as a command.
	 * @see #logger
	 * @see Command
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public InOutReplyCommand(String address,int portNumber,String commandString) throws UnknownHostException
	{
		super(address,portNumber,commandString);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Parse the string returned from the Arduino over the telnet connection (stored in the variable replyString).
	 * We expect  the reply to be one of 'in|out|unknown|error [error description]'.
	 * @exception Exception Thrown if a parse error occurs.
	 * @see #POSITION_ERROR
	 * @see #POSITION_UNKNOWN
	 * @see #POSITION_IN
	 * @see #POSITION_OUT
	 * @see #replyString
	 * @see #currentPosition
	 * @see #positionToString
	 */
	public void parseReplyString() throws Exception
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.mechanism.command.InOutReplyCommand:"+
			   "parseReplyString:Started.");
		super.parseReplyString();
		if(isError == false)
		{
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.mechanism.command.InOutReplyCommand:"+
				   "parseReplyString:Parsing reply string '"+replyString+"'.");
			if(replyString.equals("in"))
			{
				currentPosition = POSITION_IN;
			}
			else if(replyString.equals("out"))
			{
				currentPosition = POSITION_OUT;
			}
			else if(replyString.equals("unknown"))
			{
				currentPosition = POSITION_UNKNOWN;
			}
			else
			{
				currentPosition = POSITION_ERROR;
			}
		}
		else
		{
			// isError is true.
			currentPosition = POSITION_ERROR;
		}
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.mechanism.command.InOutReplyCommand:"+
			   "parseReplyString:Finished parsing reply string '"+replyString+
			   "' and got current position '"+currentPosition+"' / "+
			   positionToString(currentPosition)+".");
	}

	/**
	 * Return the current position of the mechanism returned from Arduino as the command reply.
	 * @return The current position expressed as an integer
	 * @see #currentPosition
	 * @see #POSITION_ERROR
	 * @see #POSITION_UNKNOWN
	 * @see #POSITION_IN
	 * @see #POSITION_OUT
	 */
	public int getCurrentPosition()
	{
		return currentPosition;
	}

	/**
	 * Return a descriptive string describing the position described by the parameter.
	 * @param position An integer respresenting the position: one of POSITION_ERROR|POSITION_UNKNOWN|
	 *        POSITION_IN|POSITION_OUT.
	 * @return A String decribing the position of the mechanism: on of "ERROR"|"UNKNOWN"|"IN"|"OUT".
	 * @see #POSITION_ERROR
	 * @see #POSITION_UNKNOWN
	 * @see #POSITION_IN
	 * @see #POSITION_OUT	 
	 */
	public static String positionToString(int position)
	{
		switch(position)
		{
			case POSITION_ERROR:
				return "ERROR";
			case POSITION_UNKNOWN:
				return "UNKNOWN";
			case POSITION_IN:
				return "IN";
			case POSITION_OUT:
				return "OUT";
			default:
				return "ERROR";
		}
	}

	/**
	 * Return a lower casedescriptive string describing the position described by the parameter.
	 * @param position An integer respresenting the position: one of POSITION_ERROR|POSITION_UNKNOWN|
	 *        POSITION_IN|POSITION_OUT.
	 * @return A String decribing the position of the mechanism: on of "error"|"unknown"|"in"|"out".
	 * @see #POSITION_ERROR
	 * @see #POSITION_UNKNOWN
	 * @see #POSITION_IN
	 * @see #POSITION_OUT	 
	 */
	public static String positionToLowerCaseString(int position)
	{
		switch(position)
		{
			case POSITION_ERROR:
				return "error";
			case POSITION_UNKNOWN:
				return "unknown";
			case POSITION_IN:
				return "in";
			case POSITION_OUT:
				return "out";
			default:
				return "error";
		}
	}

	/**
	 * Parse a string into a position.
	 * @param ps A string representing a legal position, one of "in"|"out"|"unknwon"|"error".
	 * @return An integer representing the position parsed: one of: 
	 *         POSITION_IN|POSITION_OUT|POSITION_UNKNOWN|POSITION_ERROR
	 * @exception IllegalArgumentException Throwen if the position string is not legal.
	 * @see #POSITION_ERROR
	 * @see #POSITION_UNKNOWN
	 * @see #POSITION_IN
	 * @see #POSITION_OUT	 
	 */
	public static int parsePositionString(String ps) throws IllegalArgumentException
	{
		if(ps.equals("in"))
			return POSITION_IN;
		else if(ps.equals("out"))
			return POSITION_OUT;
		else if(ps.equals("unknown"))
			return POSITION_UNKNOWN;
		else if(ps.equals("error"))
			return POSITION_ERROR;
		else
		{
			throw new IllegalArgumentException("ngat.sprat.mechanism.command.InOutReplyCommand:"+
							   "parsePositionString:Illegal position string:"+ps);
		}
	}
}
