// HumidityCommand.java
// $HeadURL$
package ngat.sprat.mechanism.command;

import java.io.*;
import java.lang.*;
import java.net.*;

import ngat.net.TelnetConnectionListener;
import ngat.util.logging.*;

/**
 * The HumidityCommand  is an extension of the DoubleReplyCommand for sending a humidity command
 * to the Sprat mechanism Arduino and receiving a humidity reply. This is a telnet - type socket interaction. 
 * @author Chris Mottram
 * @version $Revision$
 */
public class HumidityCommand extends DoubleReplyCommand implements Runnable, TelnetConnectionListener
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
	public HumidityCommand()
	{
		super();
		BASE_COMMAND_STRING = new String("humidity");
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor.
	 * @param address A string representing the IP address of the Arduino, i.e. "spratmechanism", "192.168.1.77".
	 * @param portNumber An integer representing the port number the Arduino is receiving command on.
	 * @param sensorNumber Which humidity sensor to query, a number from 0 to the number of sensors.
	 * @see #logger
	 * @see #setCommand
	 * @see #BASE_COMMAND_STRING
	 * @see Command
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public HumidityCommand(String address,int portNumber,int sensorNumber) throws UnknownHostException
	{
		super(address,portNumber);
		BASE_COMMAND_STRING = new String("humidity");
		setCommand(BASE_COMMAND_STRING+" "+sensorNumber);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Get the humidity of the specified sensor.
	 * @return A double, the relative humidity (0..100%).
	 * @see #getParsedReplyDouble
	 */
	public double getHumidity()
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
		HumidityCommand command = null;
		int portNumber = 23;
		int sensorNumber = -1;

		if(args.length != 3)
		{
			System.out.println("java ngat.sprat.mechanism.command.HumidityCommand <hostname> <port number> <sensor number>");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			portNumber = Integer.parseInt(args[1]);
			sensorNumber = Integer.parseInt(args[2]);
			command = new HumidityCommand(args[0],portNumber,sensorNumber);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("HumidityCommand: Command failed.");
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
				System.out.println("Humidity:"+command.getHumidity());
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
