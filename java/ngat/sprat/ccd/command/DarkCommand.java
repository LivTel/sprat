// DarkCommand.java
// $HeadURL$
package ngat.sprat.ccd.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;

/**
 * The "dark" command is an extension of the MultrunFilenameReplyCommand, and takes a dark exposure.
 * @author Chris Mottram
 * @version $Revision$
 * @see ngat.sprat.ccd.command.MultrunFilenameReplyCommand
 */
public class DarkCommand extends MultrunFilenameReplyCommand implements Runnable
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
	public DarkCommand()
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
	public DarkCommand(String address,int portNumber) throws UnknownHostException
	{
		super();
		super.setAddress(address);
		super.setPortNumber(portNumber);
	}

	/**
	 * Setup the Dark command. 
	 * @param exposureLength Set the length of the exposure in milliseconds.
	 * @see #commandString
	 */
	public void setCommand(int exposureLength)
	{
		commandString = new String("dark "+exposureLength);
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		DarkCommand command = null;
		String hostname = null;
		int portNumber = 8367;
		int exposureLength;
		boolean standard;

		if(args.length != 3)
		{
			System.out.println("java ngat.sprat.ccd.command.DarkCommand <hostname> <port number> <exposure length>");
			System.exit(1);
		}
		try
		{
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			exposureLength = Integer.parseInt(args[2]);
			command = new DarkCommand(hostname,portNumber);
			command.setCommand(exposureLength);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("DarkCommand: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Reply Parsed OK:"+command.getParsedReplyOK());
			System.out.println("Return Code:"+command.getReturnCode());
			System.out.println("Reply String:"+command.getParsedReply());
			System.out.println("Return Multrun Number:"+command.getMultrunNumber());
			System.out.println("Return Filename:"+command.getFilename());
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);

	}
}
