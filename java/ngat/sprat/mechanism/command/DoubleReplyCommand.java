// DoubleReplyCommand.java
// $HeadURL$
package ngat.sprat.mechanism.command;

import java.io.*;
import java.lang.*;
import java.net.*;

import ngat.net.TelnetConnection;
import ngat.net.TelnetConnectionListener;
import ngat.util.logging.*;

/**
 * The DoubleReplyCommand class is an extension of the base Command class for sending a command and getting a 
 * reply from the Sprat mechanism Arduino. This is a telnet - type socket interaction. 
 * DoubleReplyCommand expects the reply
 * to be 'ok &lt;[-]n.nnn&gt;' or 'error &lt;description&gt;' where &lt;[-]n.nnn&gt; is a double and 
 * &lt;description&gt; describes the error that occured. 
 * @author Chris Mottram
 * @version $Revision$
 */
public class DoubleReplyCommand extends Command implements Runnable, TelnetConnectionListener
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
	 * The parsed reply string, parsed into a double.
	 */
	protected double parsedReply = 0;

	/**
	 * Default constructor.
	 * @see #logger
	 * @see Command
	 */
	public DoubleReplyCommand()
	{
		super();
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor.
	 * @param address A string representing the IP address of the Arduino, i.e. "spratmechanism", "192.168.1.77".
	 * @param portNumber An integer representing the port number the Arduino is receiving command on.
	 * @see #logger
	 * @see Command
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public DoubleReplyCommand(String address,int portNumber) throws UnknownHostException
	{
		super(address,portNumber);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor.
	 * @param address A string representing the IP address of the Arduino, i.e. "spratmechanism", "192.168.1.77".
	 * @param portNumber An integer representing the port number the Arduino is receiving command on.
	 * @param commandString The string to send to the server as a command.
	 * @see #logger
	 * @see Command
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public DoubleReplyCommand(String address,int portNumber,String commandString) throws UnknownHostException
	{
		super(address,portNumber,commandString);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Parse the string returned from the Arduino over the telnet connection (stored in the variable replyString).
	 * @exception Exception Thrown if a parse error occurs.
	 * We expect  the reply to be 'ok &lt;[-]n.nnn&gt;' or 'error &lt;description&gt;' 
	 * where &lt;[-]n.nnn&gt; is a double and &lt;description&gt; describes the error that occured. 
	 * @see #replyString
	 * @see #parsedReply
	 */
	public void parseReplyString() throws Exception
	{
		String doubleString = null;

		logger.log(Logging.VERBOSITY_VERY_VERBOSE,
			   "ngat.sprat.mechanism.command.DoubleReplyCommand:parseReplyString:Started.");
		parsedReply = 0.0;
		super.parseReplyString();
		if(isError == false)
		{
			if(replyString.startsWith("ok"))
			{
				logger.log(Logging.VERBOSITY_VERY_VERBOSE,
					   "ngat.sprat.mechanism.command.DoubleReplyCommand:parseReplyString:"+
					   "Reply starts with ok, trying to parse double parameter.");
				// strip 'ok ' off the reply string and parse the result as a double.
				try
				{
					doubleString =  replyString.substring(3);// 3 = strlen("ok ")
					logger.log(Logging.VERBOSITY_VERY_VERBOSE,
						   "ngat.sprat.mechanism.command.DoubleReplyCommand:parseReplyString:"+
						   "Trying to parse '"+doubleString+"' as a double.");
					parsedReply = Double.parseDouble(doubleString);
				}
				catch(Exception e)
				{
					isError = true;
					errorString = new String("Failed to parse reply string '"+doubleString+
								 "' from command '"+commandString+"' as a double.");
					throw new Exception(this.getClass().getName()+":parseReplyString:"+
							    errorString);
				}
			}
			else
			{
				logger.log(Logging.VERBOSITY_VERY_VERBOSE,
					   "ngat.sprat.mechanism.command.DoubleReplyCommand:parseReplyString:"+
					   "Reply did not start with 'ok', about to throw an exception.");
				isError = true;
				errorString = new String("Reply string from command '"+commandString+
							 "' did not start with ok:"+replyString);
				throw new Exception(this.getClass().getName()+":parseReplyString:"+errorString);
			}
		}
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.mechanism.command.DoubleReplyCommand:"+
			   "parseReplyString:Finished.");
	}

	/**
	 * Return the parsed reply.
	 * @return The parsed double.
	 * @see #parsedReply
	 */
	public double getParsedReplyDouble()
	{
		return parsedReply;
	}
}
