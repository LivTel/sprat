// StatusExposureStatusCommand.java
// $HeadURL$
package ngat.sprat.ccd.command;

import java.io.*;
import java.lang.*;
import java.net.*;

/**
 * The "status exposure status" command is an extension of the Command, and returns the 
 * exposure status of the CCD.
 * @author Chris Mottram
 * @version $Revision$
 */
public class StatusExposureStatusCommand extends Command implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Exposure status.
	 */
	public final static int CCD_EXPOSURE_STATUS_NONE = 0;
	/**
	 * Exposure status.
	 */
	public final static int CCD_EXPOSURE_STATUS_WAIT_START = 1;
	/**
	 * Exposure status.
	 */
	public final static int CCD_EXPOSURE_STATUS_CLEAR = 2;
	/**
	 * Exposure status.
	 */
	public final static int CCD_EXPOSURE_STATUS_EXPOSE = 3;
	/**
	 * Exposure status.
	 */
	public final static int CCD_EXPOSURE_STATUS_PRE_READOUT = 4;
	/**
	 * Exposure status.
	 */
	public final static int CCD_EXPOSURE_STATUS_READOUT = 5;
	/**
	 * Exposure status.
	 */
	public final static int CCD_EXPOSURE_STATUS_POST_READOUT = 6;

	/**
	 * The command to send to the server.
	 */
	public final static String COMMAND_STRING = new String("status exposure status");

	/**
	 * Default constructor.
	 * @see Command
	 * @see #commandString
	 * @see #COMMAND_STRING
	 */
	public StatusExposureStatusCommand()
	{
		super();
		commandString = COMMAND_STRING;
	}

	/**
	 * Constructor.
	 * @param address A string representing the address of the server, i.e. "sprat1",
	 *     "localhost", "192.168.1.62"
	 * @param portNumber An integer representing the port number the server is receiving command on.
	 * @see Command
	 * @see #COMMAND_STRING
	 * @exception UnknownHostException Thrown if the address in unknown.
	 */
	public StatusExposureStatusCommand(String address,int portNumber) throws UnknownHostException
	{
		super(address,portNumber,COMMAND_STRING);
	}

	/**
	 * Get the current multrun exposure status.
	 * @return An exposure status, as a string.
	 * @exception Exception Thrown if getting the data fails, either the run method failed to communicate
	 *         with the server in some way, or the method was called before the command had completed.
	 * @see #parsedReplyOk
	 * @see #runException
	 * @see #parsedReplyString
	 */
	public String getExposureStatusString() throws Exception
	{
		if(parsedReplyOk == false)
		{
			if(runException != null)
				throw runException;
			else
				throw new Exception(this.getClass().getName()+
						    ":getExposureStatusString:Parsed Reply OK was false.");
		}
		return parsedReplyString;
	}

	/**
	 * Get the current multrun exposure status.
	 * This convertion should match the one in ccd_exposure.c : CCD_Exposure_Status_To_String function.
	 * @return An exposure status, as an integer.
	 * @exception Exception Thrown if getting the data fails, either the run method failed to communicate
	 *         with the server in some way, or the method was called before the command had completed.
	 * @see #parsedReplyOk
	 * @see #runException
	 * @see #parsedReplyString
	 * @see #CCD_EXPOSURE_STATUS_NONE
	 * @see #CCD_EXPOSURE_STATUS_WAIT_START
	 * @see #CCD_EXPOSURE_STATUS_CLEAR
	 * @see #CCD_EXPOSURE_STATUS_EXPOSE
	 * @see #CCD_EXPOSURE_STATUS_PRE_READOUT
	 * @see #CCD_EXPOSURE_STATUS_READOUT
	 * @see #CCD_EXPOSURE_STATUS_POST_READOUT
	 */
	public int getExposureStatus() throws Exception
	{
		int exposureStatus;

		if(parsedReplyOk == false)
		{
			if(runException != null)
				throw runException;
			else
				throw new Exception(this.getClass().getName()+
						    ":getExposureStatus:Parsed Reply OK was false.");
		}
		if(parsedReplyString.equals("NONE"))
			return CCD_EXPOSURE_STATUS_NONE;
		else if(parsedReplyString.equals("WAIT_START"))
			return CCD_EXPOSURE_STATUS_WAIT_START;
		else if(parsedReplyString.equals("CLEAR"))
			return CCD_EXPOSURE_STATUS_CLEAR;
		else if(parsedReplyString.equals("EXPOSE"))
			return CCD_EXPOSURE_STATUS_EXPOSE;
		else if(parsedReplyString.equals("PRE_READOUT"))
			return CCD_EXPOSURE_STATUS_PRE_READOUT;
		else if(parsedReplyString.equals("READOUT"))
			return CCD_EXPOSURE_STATUS_READOUT;
		else if(parsedReplyString.equals("POST_READOUT"))
			return CCD_EXPOSURE_STATUS_POST_READOUT;
		else
		{
			throw new Exception(this.getClass().getName()+
					    ":getExposureStatus:Failed to parse status:"+parsedReplyString);
		}
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		StatusExposureStatusCommand command = null;
		int portNumber = 8367;

		if(args.length != 2)
		{
			System.out.println("java ngat.sprat.ccd.command.StatusExposureStatusCommand <hostname> <port number>");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			portNumber = Integer.parseInt(args[1]);
			command = new StatusExposureStatusCommand(args[0],portNumber);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("StatusExposureStatusCommand: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Reply Parsed OK:"+command.getParsedReplyOK());
			System.out.println("Exposure Status:"+command.getExposureStatusString());
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);

	}
}
