// GyroCommand.java
// $HeadURL$
package ngat.sprat.mechanism.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;

import ngat.net.TelnetConnectionListener;
import ngat.util.logging.*;

/**
 * The GyroCommand  is an extension of the Command class for sending a gyro command
 * to the Sprat mechanism Arduino and receiving a reply containing the current position.
 * This is a telnet - type socket interaction. 
 * @author Chris Mottram
 * @version $Revision: 13 $
 */
public class GyroCommand extends Command  implements Runnable, TelnetConnectionListener
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The base command string to be sent to the Arduino. 
	 */
	public final static String COMMAND_STRING = new String("gyro");
	/**
	 * The logger to log messages to.
	 */
	protected Logger logger = null;
	/**
	 * The X angle position of the gyro in degress.
	 */
	protected double positionX = 0.0;
	/**
	 * The Y angle position of the gyro in degress.
	 */
	protected double positionY = 0.0;
	/**
	 * The Z angle position of the gyro in degress.
	 */
	protected double positionZ = 0.0;

	/**
	 * Default constructor.
	 * @see #logger
	 * @see Command
	 */
	public GyroCommand()
	{
		super();
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor.
	 * @param address A string representing the IP address of the Arduino, i.e. "spratmechanism", "192.168.1.77".
	 * @param portNumber An integer representing the port number the Arduino is receiving command on.
	 * @see #logger
	 * @see #COMMAND_STRING
	 * @see Command
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public GyroCommand(String address,int portNumber) throws UnknownHostException
	{
		super(address,portNumber,COMMAND_STRING);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Parse the string returned from the Arduino over the telnet connection (stored in the variable replyString).
	 * We expect  the reply to be one of 'ok x.xx y.yy z.zz' or 'error [error description]'.
	 * @exception Exception Thrown if a parse error occurs.
	 * @see #replyString
	 * @see #positionX
	 * @see #positionY
	 * @see #positionZ
	 */
	public void parseReplyString() throws Exception
	{
		StringTokenizer st = null;
		String okString = null;
		String extraString = null;
		int tokenIndex;

		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.mechanism.command.GyroCommand:"+
			   "parseReplyString:Started.");
		super.parseReplyString();
		if(isError == false)
		{
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.mechanism.command.GyroCommand:"+
				   "parseReplyString:Parsing reply string '"+replyString+"'.");
			if(replyString.startsWith("ok"))
			{
				st = new StringTokenizer(replyString," ");
				tokenIndex = 0;
				while(st.hasMoreTokens())
				{
					if(tokenIndex == 0)
					{
						okString = st.nextToken();
						logger.log(Logging.VERBOSITY_VERY_VERBOSE,
							   "ngat.sprat.mechanism.command.GyroCommand:"+
							   "parseReplyString:ok string '"+okString+"'.");
					}
					else if(tokenIndex == 1)
					{
						positionX = Double.parseDouble(st.nextToken());
						logger.log(Logging.VERBOSITY_VERY_VERBOSE,
							   "ngat.sprat.mechanism.command.GyroCommand:"+
							   "parseReplyString:positionX = '"+positionX+"'.");
					}
					else if(tokenIndex == 2)
					{
						positionY = Double.parseDouble(st.nextToken());
						logger.log(Logging.VERBOSITY_VERY_VERBOSE,
							   "ngat.sprat.mechanism.command.GyroCommand:"+
							   "parseReplyString:positionY = '"+positionY+"'.");
					}
					else if(tokenIndex == 3)
					{
						positionZ = Double.parseDouble(st.nextToken());
						logger.log(Logging.VERBOSITY_VERY_VERBOSE,
							   "ngat.sprat.mechanism.command.GyroCommand:"+
							   "parseReplyString:positionZ = '"+positionZ+"'.");
					}
					else
					{
						extraString = st.nextToken();
						logger.log(Logging.VERBOSITY_VERY_VERBOSE,
							   "ngat.sprat.mechanism.command.GyroCommand:"+
							   "parseReplyString:Unknown extra string = '"+extraString+
							   "'.");
					}
					tokenIndex++;
				}// end while
			}
			else
			{
				logger.log(Logging.VERBOSITY_VERY_VERBOSE,
					   "ngat.sprat.mechanism.command.GyroCommand:parseReplyString:"+
					   "Command '"+commandString+"' produced a reply '"+replyString+
					   "' that does not start with 'ok', about to throw an exception.");
				isError = true;
				errorString = new String("Reply string from command '"+commandString+
							 "' did not start with ok:"+replyString);
				throw new Exception(this.getClass().getName()+":parseReplyString:"+errorString);
			}
		}
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.mechanism.command.GyroCommand:"+
			   "parseReplyString:Finished with position (x = "+positionX+",y = "+positionY+
			   ",z = "+positionZ+").");
	}

	/**
	 * Get the X position of the gyroscope.
	 * @return The X position of the gyro as a double angle in degrees.
	 * @see #positionX
	 */
	public double getPositionX()
	{
		return positionX;
	}

	/**
	 * Get the Y position of the gyroscope.
	 * @return The Y position of the gyro as a double angle in degrees.
	 * @see #positionY
	 */
	public double getPositionY()
	{
		return positionY;
	}

	/**
	 * Get the Z position of the gyroscope.
	 * @return The Z position of the gyro as a double angle in degrees.
	 * @see #positionZ
	 */
	public double getPositionZ()
	{
		return positionZ;
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		GyroCommand command = null;
		String hostname = null;
		String positionString = null;
		int portNumber = 23;

		if(args.length != 2)
		{
			System.out.println("java ngat.sprat.mechanism.command.GyroCommand <hostname> <port number>");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			command = new GyroCommand(hostname,portNumber);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("GyroCommand: Command failed.");
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
				System.out.println("Gyro Position: ( X = "+command.getPositionX()+
					   ", Y = "+command.getPositionY()+", Z = "+command.getPositionZ()+" ).");
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
