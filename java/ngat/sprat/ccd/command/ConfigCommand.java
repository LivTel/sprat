// ConfigCommand.java
// $HeadURL$
package ngat.sprat.ccd.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;

/**
 * The "config" command is an extension of the Command, and configures the instrument for exposures.
 * @author Chris Mottram
 * @version $Revision$
 */
public class ConfigCommand extends Command implements Runnable
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
	public ConfigCommand()
	{
		super();
		commandString = null;
	}

	/**
	 * Constructor.
	 * @param address A string representing the address of the server, i.e. "sprat1",
	 *     "localhost", "192.168.1.4"
	 * @param portNumber An integer representing the port number the server is receiving command on.
	 * @see Command
	 * @see Command#setAddress
	 * @see Command#setPortNumber
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public ConfigCommand(String address,int portNumber) throws UnknownHostException
	{
		super();
		super.setAddress(address);
		super.setPortNumber(portNumber);
	}

	/**
	 * Setup the command to send to the server.
	 * @param xbin The x binning of the CCD - should normally be 1.
	 * @param ybin The y binning of the CCD - should normally be 1.
	 * @see #commandString
	 */
	public void setCommand(int xbin,int ybin)
	{
		commandString = new String("config "+xbin+" "+ybin);
	}

	/**
	 * Setup the command to send to the server. This one allows specification of a window.
	 * @param xbin The x binning of the CCD - should normally be 1.
	 * @param ybin The y binning of the CCD - should normally be 1.
	 * @param startx The start X position of the window.
	 * @param endx The end X position of the window.
	 * @param starty The start Y position of the window.
	 * @param endy The end Y position of the window.
	 */
	public void setCommand(int xbin,int ybin,int startx,int endx,int starty,int endy)
	{
		commandString = new String("config "+xbin+" "+ybin+" "+startx+" "+endx+" "+starty+" "+endy);
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		ConfigCommand command = null;
		String hostname = null;
		int portNumber = 8367;
		int xbin=1,ybin=1,startx=0,endx=0,starty=0,endy=0;

		if((args.length != 4)&&(args.length != 8))
		{
			System.out.println("java ngat.sprat.ccd.command.ConfigCommand <hostname> <port number> <xbin> <ybin> [<startx> <endx> <starty> <endy>]");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			xbin = Integer.parseInt(args[2]);
			ybin = Integer.parseInt(args[3]);
			if(args.length  == 10)
			{
				startx = Integer.parseInt(args[4]);
				endx = Integer.parseInt(args[5]);
				starty = Integer.parseInt(args[6]);
				endy = Integer.parseInt(args[7]);
			}
			command = new ConfigCommand(hostname,portNumber);
			if(args.length == 4)
				command.setCommand(xbin,ybin);
			else if(args.length  == 8)
				command.setCommand(xbin,ybin,startx,endx,starty,endy);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("ConfigCommand: Command failed.");
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
