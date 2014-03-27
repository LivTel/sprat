// Command.java
// $HeadURL$
package ngat.sprat.ccd.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.text.*;

import ngat.net.TelnetConnection;
import ngat.net.TelnetConnectionListener;
import ngat.util.logging.*;


/**
 * The Command class is the base class for sending a command and getting a reply from the
 * Sprat control system C layer. This is a telnet - type socket interaction.
 * @author Chris Mottram
 * @version $Revision$
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
	 * The logger to log messages to.
	 */
	protected Logger logger = null;

	/**
	 * Default constructor. Construct the TelnetConnection and set this object to be the listener.
	 * @see #logger
	 * @see #telnetConnection
	 */
	public Command()
	{
		super();
		telnetConnection = new TelnetConnection();
		telnetConnection.setListener(this);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor. Construct the TelnetConnection and set this object to be the listener.
	 * Setup the commandString.
	 * @param address A string representing the address of the C layer, i.e. "sprat1",
	 *     "localhost", "192.168.1.41"
	 * @param portNumber An integer representing the port number the C layer is receiving command on.
	 * @param commandString The string to send to the C layer as a command.
	 * @see #logger
	 * @see #telnetConnection
	 * @see #commandString
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public Command(String address,int portNumber,String commandString) throws UnknownHostException
	{
		super();
		logger = LogManager.getLogger(this);
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,
			   "ngat.sprat.ccd.command.Command:Constructor:Setting telnet connection to "+
			   address+":"+portNumber+".");
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
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,
			   "ngat.sprat.ccd.command.Command:setAddress:Setting telnet connection address to "+
			   address+".");
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
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,
			   "ngat.sprat.ccd.command.Command:setAddress:Setting telnet connection address to "+
			   address+".");
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
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,
			   "ngat.sprat.ccd.command.Command:setAddress:Setting telnet connection port to "+
			   portNumber+".");
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

		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.ccd.command.Command:sendCommand:Started.");
		commandFinished = false;
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,
			   "ngat.sprat.ccd.command.Command:sendCommand:Opening the Telnet Connection.");
		telnetConnection.open();
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.ccd.command.Command:sendCommand:"+
			   "Starting a reader thread to deal with the reply.");
		thread = new Thread(telnetConnection,"Reader thread");
		thread.start();
		logger.log(Logging.VERBOSITY_INTERMEDIATE,
			   "ngat.sprat.ccd.command.Command:sendCommand:Sending Command:"+commandString);
		telnetConnection.sendLine(commandString);
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.ccd.command.Command:sendCommand:"+
			   "Waiting for reader thread to finish (join).");
		thread.join();
		telnetConnection.close();
		parseReplyString();
		commandFinished = true;
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,
			   "ngat.sprat.ccd.command.Command:sendCommand:Finished with reply string '"+replyString+"'.");
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

		logger.log(Logging.VERBOSITY_VERY_VERBOSE,
			   "ngat.sprat.ccd.command.Command:parseReplyString:Started.");
		if(replyString == null)
		{
			throw new Exception(this.getClass().getName()+
					    ":parseReplyString:Reply string to command '"+commandString+"'was null.");
		}
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,
			   "ngat.sprat.ccd.command.Command:parseReplyString:Looking for space.");
		sindex = replyString.indexOf(' ');
		if(sindex < 0)
		{
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.ccd.command.Command:parseReplyString:"+
				   "Failed to find space between return code and rest of reply. "+
				   "About to throw an exception.");
			parsedReplyString = null;
			parsedReplyOk = false;
			throw new Exception(this.getClass().getName()+
					    ":parseReplyString:Failed to detect space between error code and data in:"+
					    replyString);
		}
		okString = replyString.substring(0,sindex);
		returnCode = Integer.parseInt(okString);
		parsedReplyString = replyString.substring(sindex+1);
		logger.log(Logging.VERBOSITY_INTERMEDIATE,"ngat.sprat.ccd.command.Command:parseReplyString:"+
			   "Command '"+commandString+"' returned return code '"+returnCode+
			   "' and string '"+parsedReplyString+"'.");
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
			// setup some console logging
			initialiseLogging();
			// parse arguments
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

	/**
	 * A simple class method to setup console logging for testing the ngat.sprat.mechanism package from the 
	 * command line.
	 */
	public static void initialiseLogging()
	{
		Logger l = null;
		LogHandler handler = null;
		BogstanLogFormatter blf = null;
		String loggerNameStringArray[] = {"ngat.sprat.ccd.command.AbortCommand",
						  "ngat.sprat.ccd.command.BiasCommand",
						  "ngat.sprat.ccd.command.Command",
						  "ngat.sprat.ccd.command.ConfigCommand",
						  "ngat.sprat.ccd.command.DarkCommand",
						  "ngat.sprat.ccd.command.FitsHeaderAddCommand",
						  "ngat.sprat.ccd.command.FitsHeaderClearCommand",
						  "ngat.sprat.ccd.command.FitsHeaderDeleteCommand",
						  "ngat.sprat.ccd.command.IntegerReplyCommand",
						  "ngat.sprat.ccd.command.MultBiasCommand",
						  "ngat.sprat.ccd.command.MultDarkCommand",
						  "ngat.sprat.ccd.command.MultrunCommand",
						  "ngat.sprat.ccd.command.MultrunFilenameReplyCommand",
						  "ngat.sprat.ccd.command.ShutdownCommand",
						  "ngat.sprat.ccd.command.StatusExposureLengthCommand",
						  "ngat.sprat.ccd.command.StatusExposureMultrunCommand",
						  "ngat.sprat.ccd.command.StatusExposureRunCommand",
						  "ngat.sprat.ccd.command.StatusExposureStartTimeCommand",
						  "ngat.sprat.ccd.command.StatusExposureStatusCommand",
						  "ngat.sprat.ccd.command.StatusMultrunCountCommand",
						  "ngat.sprat.ccd.command.StatusMultrunIndexCommand",
						  "ngat.sprat.ccd.command.StatusTemperatureGetCommand",
						  "ngat.sprat.ccd.command.StatusTemperatureStatusCommand"};
		blf = new BogstanLogFormatter();
		blf.setDateFormat(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS z"));
		handler = new ConsoleLogHandler(blf);
		handler.setLogLevel(Logging.ALL);
		for(int index = 0; index < loggerNameStringArray.length; index ++)
		{
			l = LogManager.getLogger(loggerNameStringArray[index]);
			l.setLogLevel(Logging.ALL);
			l.addHandler(handler);
		}
	}
}
