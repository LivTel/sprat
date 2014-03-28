// TemperatureCommand.java
// $HeadURL$
package ngat.sprat.mechanism.command;

import java.io.*;
import java.lang.*;
import java.net.*;

import ngat.net.TelnetConnectionListener;
import ngat.util.logging.*;

/**
 * The TemperatureCommand  is an extension of the DoubleReplyCommand for sending a temperature command
 * to the Sprat mechanism Arduino and receiving a temperature reply. This is a telnet - type socket interaction. 
 * @author Chris Mottram
 * @version $Revision$
 */
public class TemperatureCommand extends DoubleReplyCommand implements Runnable, TelnetConnectionListener
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
	 * @see DoubleReplyCommand
	 */
	public TemperatureCommand()
	{
		super();
		BASE_COMMAND_STRING = new String("temperature");
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor.
	 * @param address A string representing the IP address of the Arduino, i.e. "spratmechanism", "192.168.1.77".
	 * @param portNumber An integer representing the port number the Arduino is receiving command on.
	 * @param sensorNumber Which temperature sensor to query, a number from 0 to the number of sensors.
	 * @see #logger
	 * @see #setCommand
	 * @see #BASE_COMMAND_STRING
	 * @see Command
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public TemperatureCommand(String address,int portNumber,int sensorNumber) throws UnknownHostException
	{
		super(address,portNumber);
		BASE_COMMAND_STRING = new String("temperature");
		setCommand(BASE_COMMAND_STRING+" "+sensorNumber);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Get the temperature of the specified sensor.
	 * @return A double, the temperature (in degrees centigrade?).
	 * @see #getParsedReplyDouble
	 */
	public double getTemperature()
	{
		return getParsedReplyDouble();
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 * @see #initialiseLogging
	 */
	public static void main(String args[])
	{
		TemperatureCommand command = null;
		int portNumber = 23;
		int sensorNumber = -1;

		if(args.length != 3)
		{
			System.out.println("java ngat.sprat.mechanism.command.TemperatureCommand <hostname> <port number> <sensor number>");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			portNumber = Integer.parseInt(args[1]);
			sensorNumber = Integer.parseInt(args[2]);
			command = new TemperatureCommand(args[0],portNumber,sensorNumber);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("TemperatureCommand: Command failed.");
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
				System.out.println("Temperature:"+command.getTemperature());
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
