// FitsHeaderDeleteCommand.java
// $HeadURL$
package ngat.sprat.ccd.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;

/**
 * The "fitsheader delete &lt;keyword&gt;" command is an extension of the Command, and 
 * deletes the keyword value combination from the list of keywords to be included in the header of saved images.
 * @author Chris Mottram
 * @version $Revision$
 */
public class FitsHeaderDeleteCommand extends Command implements Runnable
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
	public FitsHeaderDeleteCommand()
	{
		super();
		commandString = null;
	}

	/**
	 * Constructor.
	 * @param address A string representing the address of the server, i.e. "sprat1",
	 *     "localhost", "192.168.1.62"
	 * @param portNumber An integer representing the port number the server is receiving command on.
	 * @see Command
	 * @see Command#setAddress
	 * @see Command#setPortNumber
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public FitsHeaderDeleteCommand(String address,int portNumber) throws UnknownHostException
	{
		super();
		super.setAddress(address);
		super.setPortNumber(portNumber);
	}

	/**
	 * Setup the fitsheader delete command, to delete the keyword from the list of keywords.
	 * @param keyword The FITS header keyword - only the first 11 or so letters are used.
	 * @see #commandString
	 */
	public void setCommand(String keyword)
	{
		commandString = new String("fitsheader delete "+keyword);
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		FitsHeaderDeleteCommand command = null;
		String hostname = null;
		String keyword = null;
		int portNumber = 8367;

		if(args.length != 3)
		{
			System.out.println("java ngat.sprat.ccd.command.FitsHeaderAddCommand <hostname> <port number> <keyword>");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			keyword = args[2];
			command = new FitsHeaderDeleteCommand(hostname,portNumber);
			command.setCommand(keyword);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("FitsHeaderDeleteCommand: Command failed.");
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
