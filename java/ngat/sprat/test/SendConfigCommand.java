// SendConfigCommand.java
// $HeadURL$
package ngat.sprat.test;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.util.*;

import ngat.sprat.test.*;
import ngat.message.ISS_INST.*;
import ngat.phase2.*;
import ngat.util.*;

/**
 * This class send an Sprat camera configuration to Sprat. 
 * @author Chris Mottram
 * @version $Revision$
 */
public class SendConfigCommand
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The default port number to send ISS_INST commands to.
	 */
	static final int DEFAULT_SPRAT_PORT_NUMBER = 8374;
	/**
	 * The default port number for the (fake ISS) server, to get commands from the Sprat from.
	 */
	static final int DEFAULT_SERVER_PORT_NUMBER = 7383;
	/**
	 * The ip address to send the messages read from file to, this should be the machine the FrodoSpec is on.
	 */
	private InetAddress address = null;
	/**
	 * The port number to send ISS_INST commands to Sprat.
	 */
	private int spratPortNumber = DEFAULT_SPRAT_PORT_NUMBER;
	/**
	 * The port number for the ISS server, to recieve commands from Sprat.
	 */
	private int serverPortNumber = DEFAULT_SERVER_PORT_NUMBER;
	/**
	 * The server class that listens for connections from Sprat.
	 */
	private SicfTCPServer server = null;
	/**
	 * The stream to write error messages to - defaults to System.err.
	 */
	private PrintStream errorStream = System.err;
	/**
	 * X Binning of configuration. Defaults to 1.
	 */
	private int xBin = 1;
	/**
	 * Y Binning of configuration. Defaults to 1.
	 */
	private int yBin = 1;
	/**
	 * The position of the slit, either IN or OUT of the beam.
	 * @see ngat.phase2.SpratConfig#POSITION_OUT
	 * @see ngat.phase2.SpratConfig#POSITION_IN
	 */
	private int slitPosition = SpratConfig.POSITION_OUT;
	/**
	 * The position of the grism, either IN or OUT of the beam.
	 * @see ngat.phase2.SpratConfig#POSITION_OUT
	 * @see ngat.phase2.SpratConfig#POSITION_IN
	 */
	private int grismPosition = SpratConfig.POSITION_OUT;
	/**
	 * The rotation position of the grism, either 0 or 1.
	 */
	private int grismRotation = 0;
	/**
	 * Whether exposures taken using this configuration, should do a calibration (dark) frame
	 * before the exposure.
	 */
	private boolean calibrateBefore = false;
	/**
	 * Whether exposures taken using this configuration, should do a calibration (dark) frame
	 * after the exposure.
	 */
	private boolean calibrateAfter = false;

	/**
	 * This is the initialisation routine. This starts the server thread.
	 */
	private void init()
	{
		server = new SicfTCPServer(this.getClass().getName(),serverPortNumber);
		server.setController(this);
		server.start();
	}

	/**
	 * This routine creates a CONFIG command. This object
	 * has a SpratConfig phase2 object with it, this is created and it's fields initialised.
	 * @return An instance of CONFIG.
	 * @see #xBin
	 * @see #yBin
	 * @see #slitPosition
	 * @see #grismPosition
	 * @see #grismRotation
	 * @see #calibrateBefore
	 * @see #calibrateAfter
	 */
	private CONFIG createConfig()
	{
		String string = null;
		CONFIG configCommand = null;
		SpratConfig spratConfig = null;
		SpratDetector detector = null;

		configCommand = new CONFIG("Object Id");
		spratConfig = new SpratConfig("Object Id");
	// detector for config
		detector = new SpratDetector();
		detector.setXBin(xBin);
		detector.setYBin(yBin);
		detector.setWindowFlags(0);
		spratConfig.setDetector(0,detector);
	// mechanisms
		spratConfig.setSlitPosition(slitPosition);
		spratConfig.setGrismPosition(grismPosition);
		spratConfig.setGrismRotation(grismRotation);
	// InstrumentConfig fields.
		spratConfig.setCalibrateBefore(calibrateBefore);
		spratConfig.setCalibrateAfter(calibrateAfter);
	// CONFIG command fields
		configCommand.setConfig(spratConfig);
		return configCommand;
	}

	/**
	 * This is the run routine. It creates a CONFIG object and sends it to the using a 
	 * SicfTCPClientConnectionThread, and awaiting the thread termination to signify message
	 * completion. 
	 * @return The routine returns true if the command succeeded, false if it failed.
	 * @exception Exception Thrown if an exception occurs.
	 * @see #createConfig
	 * @see SicfTCPClientConnectionThread
	 * @see #getThreadResult
	 */
	private boolean run() throws Exception
	{
		ISS_TO_INST issCommand = null;
		SicfTCPClientConnectionThread thread = null;
		boolean retval;

		issCommand = (ISS_TO_INST)(createConfig());
		if(issCommand instanceof CONFIG)
		{
			CONFIG configCommand = (CONFIG)issCommand;
			SpratConfig spratConfig = (SpratConfig)(configCommand.getConfig());
			System.err.println("CONFIG:"+
					   "Slit:"+spratConfig.slitPositionToString()+":"+
					   "Grism:"+spratConfig.grismPositionToString()+":"+
					   "Grism Rotation:"+spratConfig.getGrismRotation()+":"+
					   "X Bin:"+spratConfig.getDetector(0).getXBin()+":"+
					   "Y Bin:"+spratConfig.getDetector(0).getYBin()+".");
		}
		thread = new SicfTCPClientConnectionThread(address,spratPortNumber,issCommand);
		thread.start();
		while(thread.isAlive())
		{
			try
			{
				thread.join();
			}
			catch(InterruptedException e)
			{
				System.err.println("run:join interrupted:"+e);
			}
		}// end while isAlive
		retval = getThreadResult(thread);
		return retval;
	}

	/**
	 * Find out the completion status of the thread and print out the final status of some variables.
	 * @param thread The Thread to print some information for.
	 * @return The routine returns true if the thread completed successfully,
	 * 	false if some error occured.
	 */
	private boolean getThreadResult(SicfTCPClientConnectionThread thread)
	{
		boolean retval;

		if(thread.getAcknowledge() == null)
			System.err.println("Acknowledge was null");
		else
			System.err.println("Acknowledge with timeToComplete:"+
				thread.getAcknowledge().getTimeToComplete());
		if(thread.getDone() == null)
		{
			System.err.println("Done was null");
			retval = false;
		}
		else
		{
			if(thread.getDone().getSuccessful())
			{
				System.err.println("Done was successful");
				retval = true;
			}
			else
			{
				System.err.println("Done returned error("+thread.getDone().getErrorNum()+
					"): "+thread.getDone().getErrorString());
				retval = false;
			}
		}
		return retval;
	}

	/**
	 * This routine parses arguments passed into SendConfigCommand.
	 * @see #spratPortNumber
	 * @see #address
	 * @see #slitPosition
	 * @see #grismPosition
	 * @see #grismRotation
	 * @see #xBin
	 * @see #yBin
	 * @see #help
	 */
	private void parseArgs(String[] args)
	{
		for(int i = 0; i < args.length;i++)
		{

			if(args[i].equals("-g")||args[i].equals("-grism"))
			{
				if((i+1)< args.length)
				{
					try
					{
						grismPosition = SpratConfig.parsePosition(args[i+1]);
					}
					catch(IllegalArgumentException e)
					{
						System.err.println(this.getClass().getName()+":illegal position:"+
							args[i+1]+":"+e);
					}
					i++;
				}
				else
					errorStream.println("-grism requires a position:in or out.");
			}
			else if(args[i].equals("-h")||args[i].equals("-help"))
			{
				help();
				System.exit(0);
			}
			else if(args[i].equals("-ip")||args[i].equals("-address"))
			{
				if((i+1)< args.length)
				{
					try
					{
						address = InetAddress.getByName(args[i+1]);
					}
					catch(UnknownHostException e)
					{
						System.err.println(this.getClass().getName()+":illegal address:"+
							args[i+1]+":"+e);
					}
					i++;
				}
				else
					errorStream.println("-address requires an address");
			}
			else if(args[i].equals("-p")||args[i].equals("-port"))
			{
				if((i+1)< args.length)
				{
					spratPortNumber = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					errorStream.println("-port requires a port number");
			}
			else if(args[i].equals("-r")||args[i].equals("-rotation"))
			{
				if((i+1)< args.length)
				{
					grismRotation = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					errorStream.println("-rotation requires a position:0|1.");
			}
			else if(args[i].equals("-s")||args[i].equals("-serverport"))
			{
				if((i+1)< args.length)
				{
					serverPortNumber = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					errorStream.println("-serverport requires a port number");
			}
			else if(args[i].equals("-slit"))
			{
				if((i+1)< args.length)
				{
					try
					{
						slitPosition = SpratConfig.parsePosition(args[i+1]);
					}
					catch(IllegalArgumentException e)
					{
						System.err.println(this.getClass().getName()+":illegal position:"+
							args[i+1]+":"+e);
					}
					i++;
				}
				else
					errorStream.println("-slit requires a position:in or out.");
			}
			else if(args[i].equals("-x")||args[i].equals("-xBin"))
			{
				if((i+1)< args.length)
				{
					xBin = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					errorStream.println("-xBin requires a valid number.");
			}
			else if(args[i].equals("-y")||args[i].equals("-yBin"))
			{
				if((i+1)< args.length)
				{
					yBin = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					errorStream.println("-yBin requires a valid number.");
			}
			else
				System.out.println(this.getClass().getName()+":Option not supported:"+args[i]);
		}
	}

	/**
	 * Help message routine.
	 */
	private void help()
	{
		System.out.println(this.getClass().getName()+" Help:");
		System.out.println("Options are:");
		System.out.println("\t-g[rism] <in|out> - Grism Position.");
		System.out.println("\t-[ip]|[address] <address> - Address to send commands to.");
		System.out.println("\t-p[ort] <port number> - Port to send commands to.");
		System.out.println("\t-r[otation] <0|1> - Rotation Position.");
		System.out.println("\t-slit <in|out> - Slit Position.");
		System.out.println("\t-s[erverport] <port number> - Port for the CCS to send commands back.");
		System.out.println("\t-x[Bin] <binning factor> - X readout binning factor the CCD.");
		System.out.println("\t-y[Bin] <binning factor> - Y readout binning factor the CCD.");
		System.out.println("The default server port is "+DEFAULT_SERVER_PORT_NUMBER+".");
		System.out.println("The default Sprat port is "+DEFAULT_SPRAT_PORT_NUMBER+".");
	}

	/**
	 * The main routine, called when SendConfigCommand is executed. This initialises the object, parses
	 * it's arguments, opens the filename, runs the run routine, and then closes the file.
	 * @see #parseArgs
	 * @see #init
	 * @see #run
	 */
	public static void main(String[] args)
	{
		boolean retval;
		SendConfigCommand scc = new SendConfigCommand();

		scc.parseArgs(args);
		scc.init();
		if(scc.address == null)
		{
			System.err.println("No Sprat Address Specified.");
			scc.help();
			System.exit(1);
		}
		try
		{
			retval = scc.run();
		}
		catch (Exception e)
		{
			retval = false;
			System.err.println("run failed:"+e);
		}
		if(retval)
			System.exit(0);
		else
			System.exit(2);
	}
}
