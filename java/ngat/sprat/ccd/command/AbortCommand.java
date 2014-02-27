// AbortCommand.java
// $HeadURL$
package ngat.sprat.ccd.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;

/**
 * The "abort" command is an extension of the Command, and aborts any Multrun underway.
 * @author Chris Mottram
 * @version $Revision$
 */
public class AbortCommand extends Command implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The command to send to the server.
	 */
	public final static String COMMAND_STRING = new String("abort");

	/**
	 * Default constructor.
	 * @see Command
	 * @see #commandString
	 * @see #COMMAND_STRING
	 */
	public AbortCommand()
	{
		super();
		commandString = COMMAND_STRING;
	}

	/**
	 * Constructor.
	 * @param address A string representing the address of the server, i.e. "sprat1",
	 *     "localhost", "192.168.1.4"
	 * @param portNumber An integer representing the port number the server is receiving command on.
	 * @see Command
	 * @see #COMMAND_STRING
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public AbortCommand(String address,int portNumber) throws UnknownHostException
	{
		super(address,portNumber,COMMAND_STRING);
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		AbortCommand command = null;
		int portNumber = 8367;

		if(args.length != 2)
		{
			System.out.println("java ngat.sprat.ccd.command.AbortCommand <hostname> <port number>");
			System.exit(1);
		}
		try
		{
			portNumber = Integer.parseInt(args[1]);
			command = new AbortCommand(args[0],portNumber);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("AbortCommand: Command failed.");
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
