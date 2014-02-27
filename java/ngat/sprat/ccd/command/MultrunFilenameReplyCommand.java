// MultrunFilenameReplyCommand.java
// $HeadURL$
package ngat.sprat.ccd.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.StringTokenizer;

import ngat.net.TelnetConnection;
import ngat.net.TelnetConnectionListener;
import ngat.util.logging.*;

/**
 * The MultrunFilenameReplyCommand class is an extension of the base Command class for sending a command and getting a 
 * reply from the Sprat C layer. This is a telnet - type socket interaction. IntegerReplyCommand expects the reply
 * to be '&lt;n&gt; &lt;multrun number&gt; &lt;filename&gt;' where &lt;n&gt; is the reply status, 
 * &lt;multrun number&gt; is an integer value and &lt;filename&gt; a string. 
 * @author Chris Mottram
 * @version $Revision: 1.1 $
 */
public class MultrunFilenameReplyCommand extends Command implements Runnable, TelnetConnectionListener
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The parsed multrun number from the reply.
	 */
	protected int multrunNumber;
	/**
	 * The parsed filename from the reply.
	 */
	protected String filename = null;
	/**
	 * The logger to log messages to.
	 */
	protected Logger logger = null;

	/**
	 * Default constructor. The logger is initialised.
	 * @see Command
	 * @see #logger
	 */
	public MultrunFilenameReplyCommand()
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
	 * @exception UnknownHostException Thrown if the address in unknown.
	 * @see Command
	 * @see #logger
	 */
	public MultrunFilenameReplyCommand(String address,int portNumber,String commandString) 
		throws UnknownHostException
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
	 * @see #multrunNumber
	 * @see #filename
	 * @see #logger
	 */
	public void parseReplyString() throws Exception
	{
		String token = null;
		StringTokenizer st = null;
		int tokenIndex;

		super.parseReplyString();
		st = new StringTokenizer(parsedReplyString," ");
		if(parsedReplyOk == false)
		{
			multrunNumber = 0;
			filename = null;
			return;
		}
		try
		{
			tokenIndex = 0;
			while(st.hasMoreTokens())
			{
				// get next token 
				token = st.nextToken();
				logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
					   ":parseReplyString:token "+tokenIndex+" = "+token+".");
				if(tokenIndex == 0)
				{
					multrunNumber = Integer.parseInt(token);
				}
				else if(tokenIndex == 1)
				{
					filename = token;
				}
				else
				{
					logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
						   ":parseReplyString:unknown token index "+
						   tokenIndex+" = "+token+".");
				}
				// increment index
				tokenIndex++;
			}// end while we have more tokens
			logger.log(Logging.VERBOSITY_VERBOSE,"parseReplyString:finished.");
		}// end try
		catch(Exception e)
		{
			parsedReplyOk = false;
			multrunNumber = 0;
			filename = null;
			logger.log(Logging.VERBOSITY_TERSE,
				   "parseReplyString:Failed to parse multrun filename data with exception:",e);
			throw new Exception(this.getClass().getName()+
					    ":parseReplyString:Failed to parse multrun filename data:"+
					    parsedReplyString+":"+e);
		}
	}

	/**
	 * Return the parsed multrun number.
	 * @return The parsed multrun number.
	 * @see #multrunNumber
	 */
	public int getMultrunNumber()
	{
		return multrunNumber;
	}

	/**
	 * Return the parsed filename.
	 * @return The parsed filename.
	 * @see #filename
	 */
	public String getFilename()
	{
		return filename;
	}
}
