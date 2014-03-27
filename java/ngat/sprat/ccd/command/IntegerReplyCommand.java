// IntegerReplyCommand.java
// $HeadURL$
package ngat.sprat.ccd.command;

import java.io.*;
import java.lang.*;
import java.net.*;

import ngat.net.TelnetConnection;
import ngat.net.TelnetConnectionListener;
import ngat.util.logging.*;

/**
 * The IntegerReplyCommand class is an extension of the base Command class for sending a command and getting a 
 * reply from the Sprat C layer. This is a telnet - type socket interaction. IntegerReplyCommand expects the reply
 * to be '&lt;n&gt; &lt;m&gt;' where &lt;n&gt; is the reply status and &lt;m&gt; is an integer value. 
 * @author Chris Mottram
 * @version $Revision$
 */
public class IntegerReplyCommand extends Command implements Runnable, TelnetConnectionListener
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
	 * The parsed reply string, parsed into an integer.
	 */
	protected int parsedReplyInteger = 0;

	/**
	 * Default constructor.
	 * @see #logger
	 * @see Command
	 */
	public IntegerReplyCommand()
	{
		super();
		logger = LogManager.getLogger(this);
	}

	/**
	 * Constructor.
	 * @param address A string representing the address of the server, i.e. "sprat1",
	 *     "localhost", "192.168.1.4"
	 * @param portNumber An integer representing the port number the server is receiving command on.
	 * @param commandString The string to send to the server as a command.
	 * @see #logger
	 * @see Command
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public IntegerReplyCommand(String address,int portNumber,String commandString) throws UnknownHostException
	{
		super(address,portNumber,commandString);
		logger = LogManager.getLogger(this);
	}

	/**
	 * Parse a string returned from the server over the telnet connection.
	 * @exception Exception Thrown if a parse error occurs.
	 * @see #replyString
	 * @see #parsedReplyString
	 * @see #parsedReplyOk
	 */
	public void parseReplyString() throws Exception
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,
			   "ngat.sprat.ccd.command.IntegerReplyCommand:parseReplyString:Started.");
		super.parseReplyString();
		if(parsedReplyOk == false)
		{
			parsedReplyInteger = 0;
			return;
		}
		try
		{
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.ccd.command.IntegerReplyCommand:"+
				   "parseReplyString:Parsing '"+parsedReplyString+"' as an integer.");
			parsedReplyInteger = Integer.parseInt(parsedReplyString);
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.ccd.command.IntegerReplyCommand:"+
				   "parseReplyString:Parsed '"+parsedReplyString+"' as integer '"+
				   parsedReplyInteger+"'.");
		}
		catch(Exception e)
		{
			logger.log(Logging.VERBOSITY_VERY_VERBOSE,"ngat.sprat.ccd.command.IntegerReplyCommand:"+
				   "parseReplyString:Failed to parse '"+parsedReplyString+
				   "' as an integer: About to throw an exception.");
			parsedReplyOk = false;
			parsedReplyInteger = 0;
			throw new Exception(this.getClass().getName()+
					    ":parseReplyString:Failed to parse integer data:"+parsedReplyString);
		}
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,
			   "ngat.sprat.ccd.command.IntegerReplyCommand:parseReplyString:Finished.");
	}

	/**
	 * Return the parsed reply.
	 * @return The parsed integer.
	 * @see #parsedReplyInteger
	 */
	public int getParsedReplyInteger()
	{
		return parsedReplyInteger;
	}
}
