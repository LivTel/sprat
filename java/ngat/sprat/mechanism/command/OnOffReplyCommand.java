// OnOffReplyCommand.java
// $HeadURL$
package ngat.sprat.mechanism.command;

import java.io.*;
import java.lang.*;
import java.net.*;

import ngat.net.TelnetConnection;
import ngat.net.TelnetConnectionListener;
import ngat.util.logging.*;

/**
 * The OnOffReplyCommand is an extension of the base Command class for sending a command and getting a 
 * reply from the Sprat mechanism Arduino. This is a telnet - type socket interaction. 
 * OnOffReplyCommand expects the reply to be one of 'on|off|unknown|error [error description]'.
 * @author Chris Mottram
 * @version $Revision: 13 $
 */
public class OnOffReplyCommand extends Command implements Runnable, TelnetConnectionListener
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * State - in this case, an error has occured whilst determining whether the item is on or off.
	 */
	public final static int STATE_ERROR = -1;
	/**
	 * State - in this case, the state could not be determined.
	 */
	public final static int STATE_UNKNOWN = 0;
	/**
	 * State - in this case, the hardware is ON.
	 */
	public final static int STATE_ON = 1;
	/**
	 * State - in this case, the hardware is OFF.
	 */
	public final static int STATE_OFF = 2;
	/**
	 * The logger to log messages to.
	 */
	protected Logger logger = null;
	/**
	 * The current state returned from the Arduino.
	 */
	protected int currentState = STATE_UNKNOWN;

	/**
	 * Default constructor.
	 * @see #logger
	 * @see Command
	 */
	public OnOffReplyCommand()
	{
		super();
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
	public OnOffReplyCommand(String address,int portNumber,String commandString) throws UnknownHostException
	{
		super(address,portNumber,commandString);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Parse the string returned from the Arduino over the telnet connection (stored in the variable replyString).
	 * We expect  the reply to be one of 'on|off|unknown|error [error description]'.
	 * @exception Exception Thrown if a parse error occurs.
	 * @see #STATE_ERROR
	 * @see #STATE_UNKNOWN
	 * @see #STATE_ON
	 * @see #STATE_OFF
	 * @see #replyString
	 * @see #currentState
	 * @see #stateToString
	 */
	public void parseReplyString() throws Exception
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.mechanism.command.OnOffReplyCommand:"+
			   "parseReplyString:Started.");
		super.parseReplyString();
		if(isError == false)
		{
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.mechanism.command.OnOffReplyCommand:"+
				   "parseReplyString:Parsing reply string '"+replyString+"'.");
			if(replyString.equals("on"))
			{
				currentState = STATE_ON;
			}
			else if(replyString.equals("off"))
			{
				currentState = STATE_OFF;
			}
			else if(replyString.equals("unknown"))
			{
				currentState = STATE_UNKNOWN;
			}
			else
			{
				currentState = STATE_ERROR;
			}
		}
		else
		{
			// isError is true.
			currentState = STATE_ERROR;
		}
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.mechanism.command.OnOffReplyCommand:"+
			   "parseReplyString:Finished parsing reply string '"+replyString+
			   "' and got current state '"+currentState+"' / "+stateToString(currentState)+".");
	}

	/**
	 * Return the current state of the mechanism returned from Arduino as the command reply.
	 * @return The current state expressed as an integer.
	 * @see #currentState
	 * @see #STATE_ERROR
	 * @see #STATE_UNKNOWN
	 * @see #STATE_ON
	 * @see #STATE_OFF
	 */
	public int getCurrentState()
	{
		return currentState;
	}

	/**
	 * Return a descriptive string describing the state described by the parameter.
	 * @param state An integer respresenting the state: one of STATE_ERROR|STATE_UNKNOWN|STATE_ON|STATE_OFF.
	 * @return A String decribing the state of the mechanism: one of "ERROR"|"UNKNOWN"|"ON"|"OFF".
	 * @see #STATE_ERROR
	 * @see #STATE_UNKNOWN
	 * @see #STATE_ON
	 * @see #STATE_OFF
	 */
	public static String stateToString(int state)
	{
		switch(state)
		{
			case STATE_ERROR:
				return "ERROR";
			case STATE_UNKNOWN:
				return "UNKNOWN";
			case STATE_ON:
				return "ON";
			case STATE_OFF:
				return "OFF";
			default:
				return "ERROR";
		}
	}

	/**
	 * Return a descriptive lower case string describing the state described by the parameter.
	 * @param state An integer respresenting the state: one of STATE_ERROR|STATE_UNKNOWN|STATE_ON|STATE_OFF.
	 * @return A String decribing the state of the mechanism: one of "error"|"unknown"|"on"|"off".
	 * @see #STATE_ERROR
	 * @see #STATE_UNKNOWN
	 * @see #STATE_ON
	 * @see #STATE_OFF
	 */
	public static String stateToLowerCaseString(int state)
	{
		switch(state)
		{
			case STATE_ERROR:
				return "error";
			case STATE_UNKNOWN:
				return "unknown";
			case STATE_ON:
				return "on";
			case STATE_OFF:
				return "off";
			default:
				return "error";
		}
	}
}
