// FitsHeaderAddCommand.java
// $HeadURL$
package ngat.sprat.ccd.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;

/**
 * The "fitsheader add <keyword> <boolean|float|integer|string> <value>" command is an extension of the Command, and 
 * adds a keyword value combination to a list of keywords to be included in the header of saved images.
 * @author Chris Mottram
 * @version $Revision$
 */
public class FitsHeaderAddCommand extends Command implements Runnable
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
	public FitsHeaderAddCommand()
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
	public FitsHeaderAddCommand(String address,int portNumber) throws UnknownHostException
	{
		super();
		super.setAddress(address);
		super.setPortNumber(portNumber);
	}

	/**
	 * Setup the fitsheader add command, to add a keyword with an integer value.
	 * @param keyword The FITS header keyword - only the first 11 or so letters are used.
	 * @param value The integer value to give the keyword.
	 * @see #commandString
	 */
	public void setCommand(String keyword,int value)
	{
		commandString = new String("fitsheader add "+keyword+" integer "+value);
	}

	/**
	 * Setup the fitsheader add command, to add a keyword with an string value.
	 * @param keyword The FITS header keyword - only the first 11 or so letters are used.
	 * @param value The string value to give the keyword - only about the first 60 odd characters are used.
	 * @see #commandString
	 */
	public void setCommand(String keyword,String value)
	{
		commandString = new String("fitsheader add "+keyword+" string "+value);
	}

	/**
	 * Setup the fitsheader add command, to add a keyword with an float/double value.
	 * @param keyword The FITS header keyword - only the first 11 or so letters are used.
	 * @param value The double value to give the keyword - this is FLOAT in CFITSIO parlance.
	 * @see #commandString
	 */
	public void setCommand(String keyword,double value)
	{
		commandString = new String("fitsheader add "+keyword+" float "+value);
	}

	/**
	 * Setup the fitsheader add command, to add a keyword with an logical/boolean value.
	 * @param keyword The FITS header keyword - only the first 11 or so letters are used.
	 * @param value The logical/boolean value to give the keyword.
	 * @see #commandString
	 */
	public void setCommand(String keyword,boolean value)
	{
		commandString = new String("fitsheader add "+keyword+" boolean "+value);
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		FitsHeaderAddCommand command = null;
		String hostname = null;
		String keyword = null;
		String svalue = null;
		int portNumber = 8367;
		int ivalue;
		double dvalue;
		boolean bvalue;

		if(args.length < 5)
		{
			System.out.println("java ngat.sprat.ccd.command.FitsHeaderAddCommand <hostname> <port number> <keyword> <boolean|float|integer|string> <value> [<string value> ...]");
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
			command = new FitsHeaderAddCommand(hostname,portNumber);
			if(args[3].equals("boolean"))
			{
				if(args[4].equals("true"))
					bvalue = true;
				else if(args[4].equals("false"))
					bvalue = false;
				else
					throw new IllegalArgumentException("FitsHeaderAddCommand:"+
									   "bvalue must be true or false,"+
									   " actual value found:"+args[4]);
				command.setCommand(keyword,bvalue);
			}
			else if(args[3].equals("float"))
			{
				dvalue = Double.parseDouble(args[4]);
				command.setCommand(keyword,dvalue);
			}
			else if(args[3].equals("integer"))
			{
				ivalue = Integer.parseInt(args[4]);
				command.setCommand(keyword,ivalue);
			}
			else if(args[3].equals("string"))
			{
				svalue = args[4];
				// add any extra arguments as part of the string
				for(int i=5;i < args.length;i++)
				{
					svalue = new String(svalue+" "+args[i]);
				}
				command.setCommand(keyword,svalue);
			}
			else
			{
				throw new IllegalArgumentException("FitsHeaderAddCommand:"+
								   "Unknown FITS header value type:"+args[3]);
			}
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("FitsHeaderAddCommand: Command failed.");
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
