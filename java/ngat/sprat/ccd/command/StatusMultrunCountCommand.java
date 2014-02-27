// StatusMultrunCountCommand.java
// $HeadURL$
package ngat.sprat.ccd.command;

import java.io.*;
import java.lang.*;
import java.net.*;

/**
 * The "status multrun count" command is an extension of the IntegerReplyCommand, and returns the 
 * exposure count in the current multrun. 
 * @author Chris Mottram
 * @version $Revision$
 */
public class StatusMultrunCountCommand extends IntegerReplyCommand implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The command to send to the server.
	 */
	public final static String COMMAND_STRING = new String("status multrun count");

	/**
	 * Default constructor.
	 * @see IntegerReplyCommand
	 * @see #commandString
	 * @see #COMMAND_STRING
	 */
	public StatusMultrunCountCommand()
	{
		super();
		commandString = COMMAND_STRING;
	}

	/**
	 * Constructor.
	 * @param address A string representing the address of the server, i.e. "sprat1",
	 *     "localhost", "192.168.1.62"
	 * @param portNumber An integer representing the port number the server is receiving command on.
	 * @see IntegerReplyCommand
	 * @see #COMMAND_STRING
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public StatusMultrunCountCommand(String address,int portNumber) throws UnknownHostException
	{
		super(address,portNumber,COMMAND_STRING);
	}

	/**
	 * Get the current multrun exposure count.
	 * @return An integer, the multrun exposure count.
	 * @exception Exception Thrown if getting the data fails, either the run method failed to communicate
	 *         with the server in some way, or the method was called before the command had completed.
	 */
	public int getExposureCount() throws Exception
	{
		if(parsedReplyOk)
			return super.getParsedReplyInteger();
		else
		{
			if(runException != null)
				throw runException;
			else
				throw new Exception(this.getClass().getName()+":getExposureCount:Unknown Error.");
		}
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		StatusMultrunCountCommand command = null;
		int portNumber = 8367;

		if(args.length != 2)
		{
			System.out.println("java ngat.sprat.ccd.command.StatusMultrunCountCommand <hostname> <port number>");
			System.exit(1);
		}
		try
		{
			portNumber = Integer.parseInt(args[1]);
			command = new StatusMultrunCountCommand(args[0],portNumber);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("StatusMultrunCountCommand: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Reply Parsed OK:"+command.getParsedReplyOK());
			System.out.println("Multrun Exposure Count:"+command.getExposureCount());
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);
	}
}
