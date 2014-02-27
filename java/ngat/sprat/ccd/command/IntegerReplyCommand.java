// IntegerReplyCommand.java
// $HeadURL$
package ngat.sprat.ccd.command;

import java.io.*;
import java.lang.*;
import java.net.*;

import ngat.net.TelnetConnection;
import ngat.net.TelnetConnectionListener;

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
	 * The parsed reply string, parsed into an integer.
	 */
	protected int parsedReplyInteger = 0;

	/**
	 * Default constructor.
	 * @see Command
	 */
	public IntegerReplyCommand()
	{
		super();
	}

	/**
	 * Constructor.
	 * @param address A string representing the address of the server, i.e. "sprat1",
	 *     "localhost", "192.168.1.4"
	 * @param portNumber An integer representing the port number the server is receiving command on.
	 * @param commandString The string to send to the server as a command.
	 * @see Command
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public IntegerReplyCommand(String address,int portNumber,String commandString) throws UnknownHostException
	{
		super(address,portNumber,commandString);
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
		super.parseReplyString();
		if(parsedReplyOk == false)
		{
			parsedReplyInteger = 0;
			return;
		}
		try
		{
			parsedReplyInteger = Integer.parseInt(parsedReplyString);
		}
		catch(Exception e)
		{
			parsedReplyOk = false;
			parsedReplyInteger = 0;
			throw new Exception(this.getClass().getName()+
					    ":parseReplyString:Failed to parse integer data:"+parsedReplyString);
		}
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
