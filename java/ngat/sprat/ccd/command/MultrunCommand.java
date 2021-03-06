// MultrunCommand.java
// $HeadURL$
package ngat.sprat.ccd.command;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;

/**
 * The "multrun" command is an extension of the MultrunFilenameReplyCommand, and takes a series of exposures.
 * @author Chris Mottram
 * @version $Revision$
 * @see ngat.sprat.ccd.command.MultrunFilenameReplyCommand
 */
public class MultrunCommand extends MultrunFilenameReplyCommand implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * A string describing a type of exposure that the multrun command can perform. This affects
	 * the FITS image filename that the acquired data is saved into.
	 */
	public final static String EXPOSURE_TYPE_ACQUIRE = new String("acquire");
	/**
	 * A string describing a type of exposure that the multrun command can perform. This affects
	 * the FITS image filename that the acquired data is saved into.
	 */
	public final static String EXPOSURE_TYPE_ARC = new String("arc");
	/**
	 * A string describing a type of exposure that the multrun command can perform. This affects
	 * the FITS image filename that the acquired data is saved into.
	 */
	public final static String EXPOSURE_TYPE_EXPOSURE = new String("exposure");
	/**
	 * A string describing a type of exposure that the multrun command can perform. This affects
	 * the FITS image filename that the acquired data is saved into.
	 */
	public final static String EXPOSURE_TYPE_SKYFLAT = new String("skyflat");
	/**
	 * A string describing a type of exposure that the multrun command can perform. This affects
	 * the FITS image filename that the acquired data is saved into.
	 */
	public final static String EXPOSURE_TYPE_STANDARD = new String("standard");
	/**
	 * A string describing a type of exposure that the multrun command can perform. This affects
	 * the FITS image filename that the acquired data is saved into.
	 */
	public final static String EXPOSURE_TYPE_LAMPFLAT = new String("lampflat");
	/**
	 * A string describing a type of exposure that the multrun command can perform. This affects
	 * the FITS image filename that the acquired data is saved into.
	 */
	public final static String EXPOSURE_TYPE_BIAS = new String("bias");
	/**
	 * A string describing a type of exposure that the multrun command can perform. This affects
	 * the FITS image filename that the acquired data is saved into.
	 */
	public final static String EXPOSURE_TYPE_DARK = new String("dark");

	/**
	 * Default constructor.
	 * @see Command
	 * @see #commandString
	 */
	public MultrunCommand()
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
	public MultrunCommand(String address,int portNumber) throws UnknownHostException
	{
		super();
		super.setAddress(address);
		super.setPortNumber(portNumber);
	}

	/**
	 * Setup the Multrun command. 
	 * @param exposureLength Set the length of the exposure (or exposures) in milliseconds
	 * @param exposureCount Set the number of frames to take in the Multrun. 
	 * @param exposureType A string, describing the exposure type. One of:
	 *       arc|exposure|skyflat|standard|lampflat|bias|dark
	 * @see #commandString
	 */
	public void setCommand(int exposureLength,int exposureCount,String exposureType)
	{
		commandString = new String("multrun "+exposureLength+" "+exposureCount+" "+exposureType);
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		MultrunCommand command = null;
		String hostname = null;
		String exposureTypeString = null;
		int portNumber = 8367;
		int exposureLength,exposureCount;
		boolean standard;

		if(args.length != 5)
		{
			System.out.println("java ngat.sprat.ccd.command.MultrunCommand <hostname> <port number> <exposure length> <exposure count> <exposure type>");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			exposureLength = Integer.parseInt(args[2]);
			exposureCount = Integer.parseInt(args[3]);
			exposureTypeString = args[4];
			command = new MultrunCommand(hostname,portNumber);
			command.setCommand(exposureLength,exposureCount,exposureTypeString);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("MultrunCommand: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Reply Parsed OK:"+command.getParsedReplyOK());
			System.out.println("Return Code:"+command.getReturnCode());
			System.out.println("Reply String:"+command.getParsedReply());
			System.out.println("Return Multrun Number:"+command.getMultrunNumber());
			for(int i = 0; i < command.getFilenameListCount(); i++)
				System.out.println("Return Filename "+i+":"+command.getFilename(i));
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);
	}
}
