// MoveBrokenSlitMechanism.java
// $HeadURL: svn://ltdevsrv/sprat/java/ngat/sprat/mechanism/MoveInOutMechanism.java $
package ngat.sprat.mechanism;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.text.*;

import ngat.sprat.mechanism.command.*;
import ngat.util.logging.*;

/**
 * MoveBrokenSlitMechanism is a class that moves a mechanism that moves in and out of the beam.
 * Normally MoveInOutMechanism is used, which issues the move, and waits until the mechanism
 * has attained the new position.
 * However the Slit mechanism in sensor is currently not working, so the slit gets stuck in "unknown" and
 * returns an error. So the plan is this works the same as MoveInOutMechanism when moving "out". When moving "in"
 * we wait until the slit position is "unknown", pause for a while, and then pretend the move has worked.
 * The Specified mechanism command class is used to send underlying
 * commands to the mechanism Arduino. First a command is sent with the correct command line argument to command the
 * mechanism to move to the new state.
 * @author Chris Mottram
 * @version $Revision: 18 $
 */
public class MoveBrokenSlitMechanism implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id: MoveBrokenSlitMechanism.java 18 2014-03-28 11:35:58Z cjm $");
	/**
	 * The logger to log messages to.
	 */
	protected Logger logger = null;
	/**
	 * The command controlling the mechanism we are trying to move.
	 */
	protected InOutReplyCommand command = null;
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
	 * An Exception thrown by moveBrokenSlitMechanism and caught in the run method (which cannot throw exceptions).
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
	public MoveBrokenSlitMechanism()
	{
		super();
		logger = LogManager.getLogger(this);
	}

	/**
	 * Set the command instance we are using to move the mechanism, and the target position.
	 * @param c A valid constructed instance of a subclass of InOutReplyCommand.
	 * @param pos The target position to move to. Valid target positions are POSITION_IN|POSITION_OUT.
	 * @see InOutReplyCommand#POSITION_IN
	 * @see InOutReplyCommand#POSITION_OUT
	 * @see #command
	 * @see #targetPosition
	 */
	public void setCommand(InOutReplyCommand c,int pos)
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
	 * Run method. Calls moveBrokenSlitMechanism and catches exceptions. isError is used to tell
	 * client's that the mechanism move failed, and runException stores the exception generated.
	 * @see #isError
	 * @see #logger
	 * @see #runException
	 * @see #moveBrokenSlitMechanism
	 */
	public void run()
	{
		logger.log(Logging.VERBOSITY_VERBOSE,"ngat.sprat.mechanism.MoveBrokenSlitMechanism:run:Started.");
		isError = false;
		runException = null;
		try
		{
			moveBrokenSlitMechanism();
		}
		catch(Exception e)
		{
			runException = e;
			isError = true;
		}
		logger.log(Logging.VERBOSITY_VERBOSE,"ngat.sprat.mechanism.MoveBrokenSlitMechanism:run:Finished.");
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
	 *     <li>As the "in" sensor is broken, When moving "in" we wait until the slit position is "unknown", 
	 *         pause for a while, and then pretend the move has worked.
	 *     <li>Otherwise we check if the mechanism has taken longer than timeoutTime to attain the target position,
	 *         if so we throw an exception noting the timeout.
	 *     <li>Otherwise, we sleep for a bit (sleepTime) until re-querying the mechanism.
	 *     </ul>
	 * </ul>
	 * @exception Exception Thrown when an error occurs.
	 * @see #logger
	 * @see #command
	 * @see #targetPosition
	 * @see #sleepTime
	 * @see #timeoutTime
	 * @see InOutReplyCommand#POSITION_OUT
	 */
	public void moveBrokenSlitMechanism() throws Exception
	{
		boolean finishedMove;
		long startTime,nowTime;

		logger.log(Logging.VERBOSITY_VERBOSE,
			   "ngat.sprat.mechanism.MoveBrokenSlitMechanism:moveBrokenSlitMechanism:Started.");
		// send command with parameter to command mechanism to move.
		command.setCommandArguments(command.positionToLowerCaseString(targetPosition));
		logger.log(Logging.VERBOSITY_VERBOSE,
			   "ngat.sprat.mechanism.MoveBrokenSlitMechanism:moveBrokenSlitMechanism:Sending command:"+
			   command.getCommand());
		command.run();
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":moveBrokenSlitMechanism:Command threw an exception:",
					    command.getRunException());
		}
		if(command.getIsError())
		{
			throw new Exception(this.getClass().getName()+":moveBrokenSlitMechanism:Command failed:"+
					    command.getErrorString());
		}
		logger.log(Logging.VERBOSITY_VERBOSE,
			   "ngat.sprat.mechanism.MoveBrokenSlitMechanism:moveBrokenSlitMechanism:Returned position:"+
			   command.positionToString(command.getCurrentPosition()));
		// enter loop, awaiting mechanism to move into target position.
		finishedMove = false;
		startTime = System.currentTimeMillis();
		while(finishedMove == false)
		{
			// query mechanism position
			command.setCommandArguments(null);
			logger.log(Logging.VERBOSITY_VERBOSE,"ngat.sprat.mechanism.MoveBrokenSlitMechanism:"+
				   "moveBrokenSlitMechanism:Querying current position by sending command:"+
				   command.getCommand());
			command.run();
			if(command.getRunException() != null)
			{
				throw new Exception(this.getClass().getName()+":moveBrokenSlitMechanism:"+
						    "Command threw an exception:",command.getRunException());
			}
			if(command.getIsError())
			{
				throw new Exception(this.getClass().getName()+":moveBrokenSlitMechanism:Command failed:"+
						    command.getErrorString());
			}
			logger.log(Logging.VERBOSITY_VERBOSE,
				   "ngat.sprat.mechanism.MoveBrokenSlitMechanism:moveBrokenSlitMechanism:Returned position:"+
				   command.positionToString(command.getCurrentPosition()));
			// if we have finished moving, exit the loop
			if(command.getCurrentPosition() == targetPosition)
			{
				logger.log(Logging.VERBOSITY_VERBOSE,"ngat.sprat.mechanism.MoveBrokenSlitMechanism:"+
					   "moveBrokenSlitMechanism:Moved to target position:Finished move successfully.");
				finishedMove = true;
			}
			// new bit of code
			// if we are moving in, but have got as far as unknown, sleep a bit and assume done
			// This because the slit in sensor is not working
			else if((targetPosition == InOutReplyCommand.POSITION_IN)&&
				(command.getCurrentPosition() == InOutReplyCommand.POSITION_UNKNOWN))
			{
				logger.log(Logging.VERBOSITY_VERBOSE,"ngat.sprat.mechanism.MoveBrokenSlitMechanism:"+
					   "moveBrokenSlitMechanism:We are tring to move the slit in and it's now "+
					   "in an unknown position, lets sleep for 5 seconds.");
				Thread.sleep(5000);
				logger.log(Logging.VERBOSITY_VERBOSE,"ngat.sprat.mechanism.MoveBrokenSlitMechanism:"+
					   "moveBrokenSlitMechanism:We are tring to move the slit in and it's "+
					 "now in an unknown position, now lets assume it's got to the right position.");
				finishedMove = true;
			}
			else
			{
				// check for timeout
				nowTime = System.currentTimeMillis();
				if((nowTime-startTime) > timeoutTime)
				{
					logger.log(Logging.VERBOSITY_VERBOSE,
						   "ngat.sprat.mechanism.MoveBrokenSlitMechanism:moveBrokenSlitMechanism:"+
						   "Timed out waiting for mechanism to move to "+
						   command.positionToString(targetPosition)+" after "+
						   (nowTime-startTime)+" milliseconds: "+"Current position is: "+
						   command.positionToString(command.getCurrentPosition())+".");
					throw new Exception("ngat.sprat.mechanism.MoveBrokenSlitMechanism:"+
						  "moveBrokenSlitMechanism:Timed out waiting for mechanism to move to "+
							    command.positionToString(targetPosition)+
							    " after "+(nowTime-startTime)+" milliseconds: "+
							    "Current position is: "+
							    command.positionToString(command.getCurrentPosition()));
				}
				// sleep a bit
				logger.log(Logging.VERBOSITY_VERBOSE,
					   "ngat.sprat.mechanism.MoveBrokenSlitMechanism:moveBrokenSlitMechanism:Sleeping for "+
					   sleepTime+" milliseconds before rechecking position.");
				Thread.sleep(sleepTime);
			}
		}
		logger.log(Logging.VERBOSITY_VERBOSE,
			   "ngat.sprat.mechanism.MoveBrokenSlitMechanism:moveBrokenSlitMechanism:Finished.");
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
	 * rather than the moveBrokenSlitMechanism method.
	 * @return An exception if the command failed in some way, or null if no error occured.
	 * @see #run
	 * @see #moveBrokenSlitMechanism
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
		MoveBrokenSlitMechanism mechanismMover = null;
		InOutReplyCommand command = null;
		String hostname = null;
		String mechanismString = null;
		String inOutString = null;
		int portNumber = 23;
		int targetPosition;

		if(args.length != 4)
		{
			System.out.println("java ngat.sprat.mechanism.MoveBrokenSlitMechanism <hostname> <port number> <grism|mirror|slit> <in|out>");
			System.exit(1);
		}
		try
		{
			// setup some console logging
			initialiseLogging();
			// parse arguments
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			mechanismString = args[2];
			inOutString = args[3];
			if(mechanismString.equals("grism"))
			{
				command = new GrismCommand(hostname,portNumber);
			}
			else if(mechanismString.equals("mirror"))
			{
				command = new MirrorCommand(hostname,portNumber);
			}
			else if(mechanismString.equals("slit"))
			{
				command = new SlitCommand(hostname,portNumber);
			}
			else
			{
				System.err.println("MoveBrokenSlitMechanism:main:Mechanism '"+mechanismString+
						   "' not recognised.");
				System.exit(2);
			}
			targetPosition = command.parsePositionString(inOutString);
			mechanismMover = new MoveBrokenSlitMechanism();
			mechanismMover.setCommand(command,targetPosition);
			mechanismMover.setSleepTime(100);
			mechanismMover.setTimeoutTime(10000);
			mechanismMover.run();
			if(mechanismMover.getIsError())
			{
				System.err.println("MoveBrokenSlitMechanism:main:Moving the mechanism failed.");
			}
			if(mechanismMover.getRunException() != null)
			{
				System.err.println("MoveBrokenSlitMechanism:main:"+
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
		String loggerNameStringArray[] = {"ngat.sprat.mechanism.MoveBrokenSlitMechanism"};

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
