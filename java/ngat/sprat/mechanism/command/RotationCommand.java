// RotationCommand.java
// $HeadURL$
package ngat.sprat.mechanism.command;

import java.io.*;
import java.lang.*;
import java.net.*;

import ngat.net.TelnetConnectionListener;
import ngat.util.logging.*;

/**
 * The RotationCommand  is an extension of the Command class for sending a rotation command
 * to the Sprat mechanism Arduino and receiving a reply containing the current rotation position status.
 * This is a telnet - type socket interaction. 
 * @author Chris Mottram
 * @version $Revision$
 */
public class RotationCommand extends Command implements Runnable, TelnetConnectionListener
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
	 * The logger to log messages to.
	 */
	protected Logger logger = null;
	/**
	 * The current position returned from the Arduino. One of: POSITION_ERROR | POSITION_UNKNOWN | 0 | 1. 
	 */
	protected int currentPosition = POSITION_UNKNOWN;

	/**
	 * Default constructor.
	 * @see #logger
	 * @see #BASE_COMMAND_STRING
	 * @see Command
	 */
	public RotationCommand()
	{
		super();
		BASE_COMMAND_STRING = new String("rotation");
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor. Used to request the current position of the rotation stage, rather than command it to move.
	 * @param address A string representing the IP address of the Arduino, i.e. "spratmechanism", "192.168.1.77".
	 * @param portNumber An integer representing the port number the Arduino is receiving command on.
	 * @exception UnknownHostException Thrown if the address is unknown.
	 * @see #logger
	 * @see #setCommand
	 * @see #BASE_COMMAND_STRING
	 * @see Command
	 */
	public RotationCommand(String address,int portNumber) throws UnknownHostException
	{
		super(address,portNumber);
		BASE_COMMAND_STRING = new String("rotation");
		setCommand(BASE_COMMAND_STRING);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor.
	 * @param address A string representing the IP address of the Arduino, i.e. "spratmechanism", "192.168.1.77".
	 * @param portNumber An integer representing the port number the Arduino is receiving command on.
	 * @param position Which position to move the grism rotation mechanism to, one of: 0|1.
	 * @exception UnknownHostException Thrown if the address is unknown.
	 * @see #logger
	 * @see #setCommand
	 * @see #BASE_COMMAND_STRING
	 * @see Command
	 */
	public RotationCommand(String address,int portNumber,int position) throws UnknownHostException
	{
		super(address,portNumber);
		BASE_COMMAND_STRING = new String("rotation");
		setCommand(BASE_COMMAND_STRING+" "+position);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor.
	 * @param address A string representing the IP address of the Arduino, i.e. "spratmechanism", "192.168.1.77".
	 * @param portNumber An integer representing the port number the Arduino is receiving command on.
	 * @param position Which position to move the grism rotation mechanism to, as a string, one of: "0"|"1".
	 * @exception UnknownHostException Thrown if the address is unknown.
	 * @see #logger
	 * @see #setCommand
	 * @see #BASE_COMMAND_STRING
	 * @see Command
	 */
	public RotationCommand(String address,int portNumber,String position) throws UnknownHostException
	{
		super(address,portNumber);
		BASE_COMMAND_STRING = new String("rotation");
		setCommand(BASE_COMMAND_STRING+" "+position);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Parse the string returned from the Arduino over the telnet connection (stored in the variable replyString).
	 * We expect  the reply to be one of '0|1|unknown|error [error description]'.
	 * @exception Exception Thrown if a parse error occurs.
	 * @see #POSITION_ERROR
	 * @see #POSITION_UNKNOWN
	 * @see #replyString
	 * @see #currentPosition
	 */
	public void parseReplyString() throws Exception
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.mechanism.command.RotationCommand:"+
			   "parseReplyString:Started.");
		super.parseReplyString();
		if(isError == false)
		{
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.mechanism.command.RotationCommand:"+
				   "parseReplyString:Parsing reply string '"+replyString+"'.");
			if(replyString.equals("unknown"))
			{
				currentPosition = POSITION_UNKNOWN;
			}
			else if(replyString.equals("error"))
			{
				// We should probably never get here as isError is false
				currentPosition = POSITION_ERROR;
			}
			else
			{
				// try to parse reply as integer
				try
				{
					currentPosition = Integer.parseInt(replyString);
				}
				catch(Exception e)
				{
					isError = true;
					errorString = new String("Failed to parse reply string '"+replyString+
								 "' from command '"+commandString+"' as an integer.");
					currentPosition = POSITION_ERROR;
					throw new Exception(this.getClass().getName()+":parseReplyString:"+
							    errorString);
				}
			}
		}
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.mechanism.command.RotationCommand:"+
			   "parseReplyString:Finished.");
	}

	/**
	 * Return the current position of the mechanism returned from Arduino as the command reply.
	 * @return The current position expressed as an integer, on success either 0|1, 
	 *         on failure POSITION_ERROR|POSITION_UNKNOWN.
	 * @see #currentPosition
	 * @see #POSITION_ERROR
	 * @see #POSITION_UNKNOWN
	 */
	public int getCurrentPosition()
	{
		return currentPosition;
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 * @see #initialiseLogging
	 */
	public static void main(String args[])
	{
		RotationCommand command = null;
		String hostname = null;
		String positionString = null;
		int portNumber = 23;

		if((args.length < 2)||(args.length > 3))
		{
			System.out.println("java ngat.sprat.mechanism.command.RotationCommand <hostname> <port number> [0|1]");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			if(args.length == 2)
			{
				System.out.println("main:Only two arguments specified:"+
						   "Retrieving current position only.");
				command = new RotationCommand(hostname,portNumber);
			}
			else
			{
				positionString = args[2];
				System.out.println("main:Three arguments specified: "+
					   "Trying to move the grism rotation to position  '"+positionString+"'.");
				command = new RotationCommand(hostname,portNumber,positionString);
			}
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("RotationCommand: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Finished:"+command.getCommandFinished());
			if(command.getIsError())
			{
				System.out.println("Error String:"+command.getErrorString());
			}
			else
			{
				System.out.println("Rotation Position:"+command.getCurrentPosition()+".");
			}
		}
		catch(Exception e)
		{
			if(command != null)
			{
				if(command.getIsError())
					System.out.println("Error String:"+command.getErrorString());
			}
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);
	}
}
