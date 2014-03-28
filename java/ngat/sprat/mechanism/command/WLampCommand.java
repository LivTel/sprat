// WLampCommand.java
// $HeadURL$
package ngat.sprat.mechanism.command;

import java.io.*;
import java.lang.*;
import java.net.*;

import ngat.net.TelnetConnectionListener;
import ngat.util.logging.*;

/**
 * The WLampCommand  is an extension of the OnOffReplyCommand class for sending a wlamp command
 * to the Sprat mechanism Arduino and receiving a reply containing the current wlamp status.
 * This is a telnet - type socket interaction. 
 * @author Chris Mottram
 * @version $Revision$
 */
public class WLampCommand extends OnOffReplyCommand  implements Runnable, TelnetConnectionListener
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
	 * @see OnOffReplyCommand
	 */
	public WLampCommand()
	{
		super();
		BASE_COMMAND_STRING = new String("wlamp");
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor. Used to request the current on/off state of the wlamp, rather than command it to turn on/off.
	 * @param address A string representing the IP address of the Arduino, i.e. "spratmechanism", "192.168.1.77".
	 * @param portNumber An integer representing the port number the Arduino is receiving command on.
	 * @exception UnknownHostException Thrown if the address is unknown.
	 * @see #logger
	 * @see #setCommand
	 * @see #BASE_COMMAND_STRING
	 * @see Command
	 */
	public WLampCommand(String address,int portNumber) throws UnknownHostException
	{
		super(address,portNumber);
		BASE_COMMAND_STRING = new String("wlamp");
		setCommand(BASE_COMMAND_STRING);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor.
	 * @param address A string representing the IP address of the Arduino, i.e. "spratmechanism", "192.168.1.77".
	 * @param portNumber An integer representing the port number the Arduino is receiving command on.
	 * @param state Whether to turn the wlamp on or off, one off: STATE_ON | STATE_OFF.
	 * @exception UnknownHostException Thrown if the address is unknown.
	 * @see #logger
	 * @see #setCommand
	 * @see #BASE_COMMAND_STRING
	 * @see Command
	 * @see ngat.sprat.mechanism.command.OnOffReplyCommand#stateToLowerCaseString
	 * @see ngat.sprat.mechanism.command.OnOffReplyCommand#STATE_ON
	 * @see ngat.sprat.mechanism.command.OnOffReplyCommand#STATE_OFF
	 */
	public WLampCommand(String address,int portNumber,int state) throws UnknownHostException
	{
		super(address,portNumber);
		BASE_COMMAND_STRING = new String("wlamp");
		setCommand(BASE_COMMAND_STRING+" "+stateToLowerCaseString(state));
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor.
	 * @param address A string representing the IP address of the Arduino, i.e. "spratmechanism", "192.168.1.77".
	 * @param portNumber An integer representing the port number the Arduino is receiving command on.
	 * @param state Which state to drive the wlamp to, as a string. 
	 *        The string should be one off: "on" | "off".
	 * @exception UnknownHostException Thrown if the address is unknown.
	 * @see #logger
	 * @see #BASE_COMMAND_STRING
	 * @see #setCommand
	 * @see Command
	 */
	public WLampCommand(String address,int portNumber,String state) throws UnknownHostException
	{
		super(address,portNumber);
		BASE_COMMAND_STRING = new String("wlamp");
		setCommand(BASE_COMMAND_STRING+" "+state);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 * @see #initialiseLogging
	 */
	public static void main(String args[])
	{
		WLampCommand command = null;
		String hostname = null;
		String onOffString = null;
		int portNumber = 23;

		if((args.length < 2)||(args.length > 3))
		{
			System.out.println("java ngat.sprat.mechanism.command.WLampCommand <hostname> <port number> [on|off]");
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
						   "Retrieving current state only.");
				command = new WLampCommand(hostname,portNumber);
			}
			else
			{
				onOffString = args[2];
				System.out.println("main:Three arguments specified: Trying to turn '"+
						   onOffString+"' the wlamp.");
				command = new WLampCommand(hostname,portNumber,onOffString);
			}
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("WLampCommand: Command failed.");
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
				System.out.println("WLamp State:"+command.getCurrentState()+" = "+
						   WLampCommand.stateToString(command.getCurrentState()));
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
