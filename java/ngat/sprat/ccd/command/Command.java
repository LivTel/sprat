// Command.java
// $HeadURL$
package ngat.sprat.ccd.command;

import java.io.*;
import java.lang.*;
import java.net.*;

import ngat.net.TelnetConnection;
import ngat.net.TelnetConnectionListener;

/**
 * The Command class is the base class for sending a command and getting a reply from the
 * Sprat control system C layer. This is a telnet - type socket interaction.
 * @author Chris Mottram
 * @version $Revision: 1.1 $
 */
public class Command implements Runnable, TelnetConnectionListener
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * ngat.net.TelnetConnection instance.
	 */
	protected TelnetConnection telnetConnection = null;
	/**
	 * The command to send to the Sprat.
	 */
	protected String commandString = null;
	/**
	 * Exception generated by errors generated in sendCommand, if called via the run method.
	 * @see #sendCommand
	 * @see #run
	 */
	protected Exception runException = null;
	/**
	 * Boolean set to true, when a command has been sent to the server and
	 * a reply string has been sent.
	 * @see #sendCommand
	 */
	protected boolean commandFinished = false;
	/**
	 * A string containing the reply from the C layer.
	 */
	protected String replyString = null;
	/**
	 * The parsed reply string, this is the reply with the initial number and space stripped off.
	 */
	protected String parsedReplyString = null;
	/**
	 * Whether the reply string started with a number, and that number was '0', indicating the
	 * command suceeded in some sense.
	 */
	protected boolean parsedReplyOk = false;
	/**
	 * The return code of the reply string, parsed as an integer.
	 */
	protected int returnCode = 0;

	/**
	 * Default constructor. Construct the TelnetConnection and set this object to be the listener.
	 * @see #telnetConnection
	 */
	public Command()
	{
		super();
		telnetConnection = new TelnetConnection();
		telnetConnection.setListener(this);
	}

	/**
	 * Constructor. Construct the TelnetConnection and set this object to be the listener.
	 * Setup the commandString.
	 * @param address A string representing the address of the C layer, i.e. "sprat1",
	 *     "localhost", "192.168.1.41"
	 * @param portNumber An integer representing the port number the C layer is receiving command on.
	 * @param commandString The string to send to the C layer as a command.
	 * @see #telnetConnection
	 * @see #commandString
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public Command(String address,int portNumber,String commandString) throws UnknownHostException
	{
		super();
		telnetConnection = new TelnetConnection(address,portNumber);
		telnetConnection.setListener(this);
		this.commandString = commandString;
	}

	/**
	 * Set the address.
	 * @param address A string representing the address of the server, i.e. "sprat1",
	 *     "localhost", "192.168.1.4"
	 * @exception UnknownHostException Thrown if the address in unknown.
	 * @see #telnetConnection
	 * @see ngat.net.TelnetConnection#setAddress
	 */
	public void setAddress(String address) throws UnknownHostException
	{
		telnetConnection.setAddress(address);
	}

	/**
	 * Set the address.
	 * @param address A instance of InetAddress representing the address of the server.
	 * @see #telnetConnection
	 * @see ngat.net.TelnetConnection#setAddress
	 */
	public void setAddress(InetAddress address)
	{
		telnetConnection.setAddress(address);
	}

	/**
	 * Set the port number.
	 * @param portNumber An integer representing the port number the server is receiving command on.
	 * @see #telnetConnection
	 * @see ngat.net.TelnetConnection#setPortNumber
	 */
	public void setPortNumber(int portNumber)
	{
		telnetConnection.setPortNumber(portNumber);
	}

	/**
	 * Set the command.
	 * @param command The string to send to the server as a command.
	 * @see #commandString
	 */
	public void setCommand(String command)
	{
		commandString = command;
	}

	/**
	 * Run thread. Uses sendCommand to send the specified command over a telnet connection to the specified
	 * address and port number.
	 * Catches any errors and puts them into runException. commandFinished indicates when the command
	 * has finished processing, replyString, parsedReplyString, parsedReplyOk contain the server replies.
	 * @see #commandString
	 * @see #sendCommand
	 * @see #replyString
	 * @see #parsedReplyString
	 * @see #parsedReplyOk
	 * @see #runException
	 * @see #commandFinished
	 */
	public void run()
	{
		try
		{
			sendCommand();
		}
		catch(Exception e)
		{
			runException = e;
			commandFinished = true;
		}
	}

	/**
	 * Routine to send the specified command over a telnet connection to the specified
	 * address and port number, wait for a reply from the server, and try to parse the reply.
	 * @exception Exception Thrown if an error occurs.
	 * @see #telnetConnection
	 * @see #commandString
	 * @see #commandFinished
	 * @see #parseReplyString
	 */
	public void sendCommand() throws Exception
	{
		Thread thread = null;

		commandFinished = false;
		telnetConnection.open();
		thread = new Thread(telnetConnection,"Reader thread");
		thread.start();
		telnetConnection.sendLine(commandString);
		thread.join();
		telnetConnection.close();
		parseReplyString();
		commandFinished = true;
	}

	/**
	 * TelnetConnectionListener interface implementation.
	 * Called for each line of text read by the TelnetConnection instance.
	 * The string is copied/appended to the replyString.
	 * @param line The string read from the TelnetConnection.
	 * @see #replyString
	 */
	public void lineRead(String line)
	{
		if(replyString == null)
			replyString = line;
		else
			replyString = new String(replyString+line);
	}

	/**
	 * Parse a string returned from the server over the telnet connection.
	 * @exception Exception Thrown if a parse error occurs.
	 * @see #replyString
	 * @see #parsedReplyString
	 * @see #parsedReplyOk
	 * @see #returnCode
	 */
	public void parseReplyString() throws Exception
	{
		int sindex;
		String okString = null;

		if(replyString == null)
		{
			throw new Exception(this.getClass().getName()+
					    ":parseReplyString:Reply string to command '"+commandString+"'was null.");
		}
		sindex = replyString.indexOf(' ');
		if(sindex < 0)
		{
			parsedReplyString = null;
			parsedReplyOk = false;
			throw new Exception(this.getClass().getName()+
					    ":parseReplyString:Failed to detect space between error code and data in:"+
					    replyString);
		}
		okString = replyString.substring(0,sindex);
		returnCode = Integer.parseInt(okString);
		parsedReplyString = replyString.substring(sindex+1);
		if(okString.equals("0"))
			parsedReplyOk = true;
		else
			parsedReplyOk = false;
	}

	/**
	 * Return the reply string
	 * @return The FULL string returned from the server.
	 * @see #replyString
	 */
	public String getReply()
	{
		return replyString;
	}

	/**
	 * Return the reply string.
	 * @return The PARSED string returned from the server. i.e. the reply with the initial return code 
	 *          stripped off.
	 * @see #parsedReplyString
	 */
	public String getParsedReply()
	{
		return parsedReplyString;
	}

	/**
	 * Return the reply string was parsed OK.
	 * @return A boolean, true if the reply started with a '0'.
	 * @see #parsedReplyOk
	 */
	public boolean getParsedReplyOK()
	{
		return parsedReplyOk;
	}

	/**
	 * Return the numeric value of the return code in the reply.
	 * @return The return code. 0 usually means success, non-zero is a failure.
	 * @see #returnCode
	 */
	public int getReturnCode()
	{
		return returnCode;
	}

	/**
	 * Get any exception resulating from running the command.
	 * This is only filled in if the command was sent using the run method, rather than the sendCommand method.
	 * @return An exception if the command failed in some way, or null if no error occured.
	 * @see #run
	 * @see #sendCommand
	 * @see #runException
	 */
	public Exception getRunException()
	{
		return runException;
	}

	/**
	 * Get whether the command has been completed.
	 * @return A Boolean, true if a command has been sent, and a reply received and parsed. false if the
	 *     command has not been sent yet, or we are still waiting for a reply.
	 * @see #commandFinished
	 */
	public boolean getCommandFinished()
	{
		return commandFinished;
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		Command command = null;
		int portNumber = 1111;

		if(args.length != 3)
		{
			System.out.println("java ngat.sprat.ccd.command.Command <hostname> <port number> <command>");
			System.exit(1);
		}
		try
		{
			portNumber = Integer.parseInt(args[1]);
			command = new Command(args[0],portNumber,args[2]);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("Command: Command "+args[2]+" failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Reply:"+command.getReply());
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Return Code:"+command.getReturnCode());
			System.out.println("Reply Parsed OK:"+command.getParsedReplyOK());
			System.out.println("Parsed Reply:"+command.getParsedReply());
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);
	}
}
