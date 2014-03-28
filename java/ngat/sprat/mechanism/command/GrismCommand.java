// GrismCommand.java
// $HeadURL$
package ngat.sprat.mechanism.command;

import java.io.*;
import java.lang.*;
import java.net.*;

import ngat.net.TelnetConnectionListener;
import ngat.util.logging.*;

/**
 * The GrismCommand  is an extension of the InOutReplyCommand class for sending a grism command
 * to the Sprat mechanism Arduino and receiving a reply containing the current grism position status.
 * This is a telnet - type socket interaction. 
 * @author Chris Mottram
 * @version $Revision$
 */
public class GrismCommand extends InOutReplyCommand  implements Runnable, TelnetConnectionListener
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The logger to log messages to.
	 */
	protected Logger logger = null;

	/**
	 * Default constructor.
	 * @see #logger
	 * @see #BASE_COMMAND_STRING
	 * @see InOutReplyCommand
	 */
	public GrismCommand()
	{
		super();
		BASE_COMMAND_STRING = new String("grism");
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor. Used to request the current position of the grism, rather than command it to a new position.
	 * @param address A string representing the IP address of the Arduino, i.e. "spratmechanism", "192.168.1.77".
	 * @param portNumber An integer representing the port number the Arduino is receiving command on.
	 * @exception UnknownHostException Thrown if the address is unknown.
	 * @see #logger
	 * @see #setCommand
	 * @see #BASE_COMMAND_STRING
	 * @see Command
	 */
	public GrismCommand(String address,int portNumber) throws UnknownHostException
	{
		super(address,portNumber);
		BASE_COMMAND_STRING = new String("grism");
		setCommand(BASE_COMMAND_STRING);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor.
	 * @param address A string representing the IP address of the Arduino, i.e. "spratmechanism", "192.168.1.77".
	 * @param portNumber An integer representing the port number the Arduino is receiving command on.
	 * @param position Which position to drive the grism to, one off: POSITION_IN | POSITION_OUT.
	 * @exception UnknownHostException Thrown if the address is unknown.
	 * @see #logger
	 * @see #BASE_COMMAND_STRING
	 * @see #setCommand
	 * @see Command
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#positionToLowerCaseString
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#POSITION_IN
	 * @see ngat.sprat.mechanism.command.InOutReplyCommand#POSITION_OUT
	 */
	public GrismCommand(String address,int portNumber,int position) throws UnknownHostException
	{
		super(address,portNumber);
		BASE_COMMAND_STRING = new String("grism");
		setCommand(BASE_COMMAND_STRING+" "+positionToLowerCaseString(position));
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor.
	 * @param address A string representing the IP address of the Arduino, i.e. "spratmechanism", "192.168.1.77".
	 * @param portNumber An integer representing the port number the Arduino is receiving command on.
	 * @param position Which position to drive the grism to, as a string. 
	 *        The string should be one off: "in" | "out".
	 * @exception UnknownHostException Thrown if the address is unknown.
	 * @see #logger
	 * @see #BASE_COMMAND_STRING
	 * @see #setCommand
	 * @see Command
	 */
	public GrismCommand(String address,int portNumber,String position) throws UnknownHostException
	{
		super(address,portNumber);
		BASE_COMMAND_STRING = new String("grism");
		setCommand(BASE_COMMAND_STRING+" "+position);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 * @see #initialiseLogging
	 */
	public static void main(String args[])
	{
		GrismCommand command = null;
		String hostname = null;
		String inOutString = null;
		int portNumber = 23;

		if((args.length < 2)||(args.length > 3))
		{
			System.out.println("java ngat.sprat.mechanism.command.GrismCommand <hostname> <port number> [in|out]");
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
				command = new GrismCommand(hostname,portNumber);
			}
			else
			{
				inOutString = args[2];
				System.out.println("main:Three arguments specified: Trying to move grism to '"+
						   inOutString+"'.");
				command = new GrismCommand(hostname,portNumber,inOutString);
			}
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("GrismCommand: Command failed.");
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
				System.out.println("Grism Position:"+command.getCurrentPosition()+" = "+
						   GrismCommand.positionToString(command.getCurrentPosition()));
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
