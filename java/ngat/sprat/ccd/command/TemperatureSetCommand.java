// TemperatureSetCommand.java
// $HeadURL$
package ngat.sprat.ccd.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;

import ngat.util.logging.*;

/**
 * The "temperature set <c>" command is an extension of the Command, and sets the 
 * current target temperature of the CCD camera.
 * @author Chris Mottram
 * @version $Revision: 17 $
 */
public class TemperatureSetCommand extends Command implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Default constructor.
	 * @see Command
	 * @see #commandString
	 */
	public TemperatureSetCommand()
	{
		super();
		commandString = null;
	}

	/**
	 * Constructor.
	 * @param address A string representing the address of the server, i.e. "sprat",
	 *     "localhost", "192.168.1.62"
	 * @param portNumber An integer representing the port number the server is receiving command on.
	 * @see Command
	 * @see Command#setAddress
	 * @see Command#setPortNumber
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public TemperatureSetCommand(String address,int portNumber) throws UnknownHostException
	{
		super();
		super.setAddress(address);
		super.setPortNumber(portNumber);
	}

	/**
	 * Setup the "temperature set" command. 
	 * @param targetTemperature The target temperature the CCD is to attain, in degrees centigrade.
	 * @see #commandString
	 */
	public void setCommand(double targetTemperature)
	{
		commandString = new String("temperature set "+targetTemperature);
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		TemperatureSetCommand command = null;
		String hostname = null;
		int portNumber = 8367;
		double targetTemperature;

		if(args.length != 3)
		{
			System.out.println("java ngat.sprat.ccd.command.TemperatureSetCommand <hostname> <port number> <target temperature>");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			targetTemperature = Double.parseDouble(args[2]);
			command = new TemperatureSetCommand(hostname,portNumber);
			command.setCommand(targetTemperature);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("TemperatureSetCommand: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Reply Parsed OK:"+command.getParsedReplyOK());
			System.out.println("Return Code:"+command.getReturnCode());
			System.out.println("Reply String:"+command.getParsedReply());
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);
	}
}
