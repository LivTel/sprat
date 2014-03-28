// MoveRotationMechanism.java
// $HeadURL$
package ngat.sprat.mechanism;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.text.*;

import ngat.sprat.mechanism.command.*;
import ngat.util.logging.*;

/**
 * MoveRotationMechanism is a class that moves the grism rotation mechanism. 
 * The rotation mechanism command class is used to send underlying
 * commands to the mechanism Arduino. First a command is sent with the correct command line argument to command the
 * mechanism to move to the new state. Then the status version of the command is sent to monitor and 
 * ensure the mechanism moves to it's commanded new position. There is timeout code that causes an exception 
 * if the mechanism gets stuck /  takes too long.
 * @author Chris Mottram
 * @version $Revision$
 */
public class MoveRotationMechanism implements Runnable
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
	 * The command controlling the grism rotation mechanism.
	 */
	protected RotationCommand command = null;
	/**
	 * The position we are trying to move the mechanism to.
	 */
	protected int targetPosition = -1;
	/**
	 * Whether an error occured whilst trying to move the mechanism.
	 * Used when running as a Runnable/Thread via the run method.
	 * @see #run
	 */
	protected boolean isError = false;
	/**
	 * An Exception thrown by moveRotationMechanism and caught in the run method (which cannot throw exceptions).
	 * @see #run
	 */
	protected Exception runException = null;
	/**
	 * The length of time to sleep between calls to the Arduino to check whether the mechanism has finished moving,
	 * in milliseconds.
	 */
	protected long sleepTime = 100;
	/**
	 * The length of time in milliseconds to wait for the mechanism to attain it's new position before
	 * timing out and throwing an error.
	 */
	protected long timeoutTime = 10000;

	/**
	 * Default constructor.
	 * @see #logger
	 */
	public MoveRotationMechanism()
	{
		super();
		logger = LogManager.getLogger(this);
	}

	/**
	 * Set the command instance we are using to move the mechanism, and the target position.
	 * @param c A valid constructed instance of RotationCommand.
	 * @param pos The target position to move to. Valid target positions are 0|1.
	 * @see #command
	 * @see #targetPosition
	 */
	public void setCommand(RotationCommand c,int pos)
	{
		command = c;
		targetPosition = pos;
	}

	/**
	 * Set the amount of time to pause at the end of the loop which is waiting for the mechanism to attain 
	 * it's new position. This pause stops us continuously querying the Arduino.
	 * @param ms The length of time to pause, in milliseconds.
	 * @see #sleepTime
	 */
	public void setSleepTime(long ms)
	{
		sleepTime = ms;
	}

	/**
	 * Set the length of time we stay in the loop awaiting the mechanism to attain it's new target. If this
	 * length of time is exceeded it is assumed ther mechanism has stuck or gone wrong in some manner 
	 * and an exception is thrown.
	 * @param ms The length of time to wait for the mechanism to successfuly complete it's move, in milliseconds.
	 * @see #timeoutTime
	 */
	public void setTimeoutTime(long ms)
	{
		timeoutTime = ms;
	}

	/**
	 * Run method. Calls moveRotationMechanism and catches exceptions. isError is used to tell
	 * client's that the mechanism move failed, and runException stores the exception generated.
	 * @see #isError
	 * @see #logger
	 * @see #runException
	 * @see #moveRotationMechanism
	 */
	public void run()
	{
		logger.log(Logging.VERBOSITY_VERBOSE,"ngat.sprat.mechanism.MoveRotationMechanism:run:Started.");
		isError = false;
		runException = null;
		try
		{
			moveRotationMechanism();
		}
		catch(Exception e)
		{
			runException = e;
			isError = true;
		}
		logger.log(Logging.VERBOSITY_VERBOSE,"ngat.sprat.mechanism.MoveRotationMechanism:run:Finished.");
	}

	/**
	 * Move the mechanism to a new position.
	 * <ul>
	 * <li>The command's command string argument is set to the target position, to tell the
	 *     Sprat mechanism Arduino to start moving the mechanism.
	 * <li>The command is sent to the Sprat mechanism Arduino.
	 * <li>A loop is entered waiting for the mechanism to attain it's new position:
	 *     <ul>
	 *     <li>The command string is set to the command only, so the mechanism position status is queried.
	 *     <li>The command is sent to the Sprat mechanism Arduino.
	 *     <li>We check whether the current mechanism position is the requested target, 
	 *         if so exit the loop successfully.
	 *     <li>Otherwise we check if the mechanism has taken longer than timeoutTime to attain the target position,
	 *         if so we throw an exception noting the timeout.
	 *     <li>Otherwise, we aleep for a bit (sleepTime) until re-querying the mechanism.
	 *     </ul>
	 * </ul>
	 * @see #logger
	 * @see #command
	 * @see #targetPosition
	 * @see #sleepTime
	 * @see #timeoutTime
	 */
	public void moveRotationMechanism() throws Exception
	{
		boolean finishedMove;
		long startTime,nowTime;

		logger.log(Logging.VERBOSITY_VERBOSE,
			   "ngat.sprat.mechanism.MoveRotationMechanism:moveRotationMechanism:Started.");
		// send command with parameter to command mechanism to move.
		command.setCommandArguments(""+targetPosition);
		logger.log(Logging.VERBOSITY_VERBOSE,
			   "ngat.sprat.mechanism.MoveRotationMechanism:moveRotationMechanism:Sending command:"+
			   command.getCommand());
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":moveRotationMechanism:Command threw an exception:",
					    command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+":moveRotationMechanism:Command failed:"+
					    command.getErrorString());
		}
		logger.log(Logging.VERBOSITY_VERBOSE,
			   "ngat.sprat.mechanism.MoveRotationMechanism:moveRotationMechanism:Returned position:"+
			   command.getCurrentPosition());
		// enter loop, awaiting mechanism to move into target position.
		finishedMove = false;
		startTime = System.currentTimeMillis();
		while(finishedMove == false)
		{
			// query mechanism position
			command.setCommandArguments(null);
			logger.log(Logging.VERBOSITY_VERBOSE,"ngat.sprat.mechanism.MoveRotationMechanism:"+
				   "moveRotationMechanism:Querying current position by sending command:"+
				   command.getCommand());
			command.run();
			if(command.getRunException() != null)
			{
				throw new Exception(this.getClass().getName()+":moveRotationMechanism:"+
						    "Command threw an exception:",command.getRunException());
			}
			if(command.getIsError())
			{
				throw new Exception(this.getClass().getName()+":moveRotationMechanism:Command failed:"+
						    command.getErrorString());
			}
			logger.log(Logging.VERBOSITY_VERBOSE,
				 "ngat.sprat.mechanism.MoveRotationMechanism:moveRotationMechanism:Returned position:"+
				   command.getCurrentPosition());
			// if we have finished moving, exit the loop
			if(command.getCurrentPosition() == targetPosition)
			{
				logger.log(Logging.VERBOSITY_VERBOSE,"ngat.sprat.mechanism.MoveRotationMechanism:"+
					 "moveRotationMechanism:Moved to target position:Finished move successfully.");
				finishedMove = true;
			}
			else
			{
				// check for timeout
				nowTime = System.currentTimeMillis();
				if((nowTime-startTime) > timeoutTime)
				{
					logger.log(Logging.VERBOSITY_VERBOSE,
						   "ngat.sprat.mechanism.MoveRotationMechanism:moveRotationMechanism:"+
						   "Timed out waiting for mechanism to move to "+targetPosition+
						   " after "+(nowTime-startTime)+" milliseconds: "+
						   "Current position is: "+command.getCurrentPosition()+".");
					throw new Exception("ngat.sprat.mechanism.MoveRotationMechanism:"+
						  "moveRotationMechanism:Timed out waiting for mechanism to move to "+
							    targetPosition+" after "+(nowTime-startTime)+
							    " milliseconds:Current position is: "+
							    command.getCurrentPosition());
				}
				// sleep a bit
				logger.log(Logging.VERBOSITY_VERBOSE,"ngat.sprat.mechanism.MoveRotationMechanism:"+
					   "moveRotationMechanism:Sleeping for "+
					   sleepTime+" milliseconds before rechecking position.");
				Thread.sleep(sleepTime);
			}
		}
		logger.log(Logging.VERBOSITY_VERBOSE,
			   "ngat.sprat.mechanism.MoveRotationMechanism:moveRotationMechanism:Finished.");
	}

	/**
	 * Return whether the run method captured an error (exception)
	 * @return A boolean, true if an error string was detected, false otherwise.
	 * @see #isError
	 */
	public boolean getIsError()
	{
		return isError;
	}

	/**
	 * Get any exception thrown during the execution of the run method.
	 * This is only filled in if the command was sent using the run method, 
	 * rather than the moveRotationMechanism method.
	 * @return An exception if the command failed in some way, or null if no error occured.
	 * @see #run
	 * @see #moveRotationMechanism
	 * @see #runException
	 */
	public Exception getRunException()
	{
		return runException;
	}

	/**
	 * Main test program.
	 * @param args The argument list.
	 * @see #initialiseLogging
	 */
	public static void main(String args[])
	{
		MoveRotationMechanism mechanismMover = null;
		RotationCommand command = null;
		String hostname = null;
		String positionString = null;
		int portNumber = 23;
		int targetPosition;

		if(args.length != 3)
		{
			System.out.println("java ngat.sprat.mechanism.MoveRotationMechanism <hostname> <port number> <0|1>");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			positionString = args[2];
			command = new RotationCommand(hostname,portNumber);
			targetPosition = Integer.parseInt(positionString);
			mechanismMover = new MoveRotationMechanism();
			mechanismMover.setCommand(command,targetPosition);
			mechanismMover.setSleepTime(100);
			mechanismMover.setTimeoutTime(10000);
			mechanismMover.run();
			if(mechanismMover.getIsError())
			{
				System.err.println("MoveRotationMechanism:main:Moving the mechanism failed.");
			}
			if(mechanismMover.getRunException() != null)
			{
				System.err.println("MoveRotationMechanism:main:"+
						   "Moving the mechanism produced the following exception:"+
						   mechanismMover.getRunException());
				mechanismMover.getRunException().printStackTrace(System.err);
			}
		}
		catch(Exception e)
		{
			if(command != null)
			{
				if(command.getIsError())
					System.out.println("Error String:"+command.getErrorString());
			}
			e.printStackTrace(System.err);
			System.exit(3);
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
		String loggerNameStringArray[] = {"ngat.sprat.mechanism.MoveRotationMechanism"};

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
		ngat.sprat.mechanism.command.Command.initialiseLogging();
	}
}
