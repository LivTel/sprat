// SpratConstants.java
// $HeadURL$
package ngat.sprat;

import java.lang.*;
import java.io.*;

/**
 * This class holds some constant values for the Sprat program. 
 * @author Chris Mottram
 * @version $Revision: 35 $
 */
public class SpratConstants
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Error code. No error.
	 */
	public final static int SPRAT_ERROR_CODE_NO_ERROR 		= 0;
	/**
	 * The base Error number, for all Sprat error codes. 
	 * See http://ltdevsrv.livjm.ac.uk/~dev/errorcodes.html for details.
	 */
	public final static int SPRAT_ERROR_CODE_BASE 			= 1600000;
	/**
	 * Default thread priority level. This is for the server thread. Currently this has the highest priority,
	 * so that new connections are always immediately accepted.
	 * This number is the default for the <b>sprat.thread.priority.server</b> property, if it does not exist.
	 */
	public final static int SPRAT_DEFAULT_THREAD_PRIORITY_SERVER      = Thread.NORM_PRIORITY+2;
	/**
	 * Default thread priority level. 
	 * This is for server connection threads dealing with sub-classes of the INTERRUPT
	 * class. Currently these have a higher priority than other server connection threads,
	 * so that INTERRUPT commands are always responded to even when another command is being dealt with.
	 * This number is the default for the <b>sprat.thread.priority.interrupt</b> property, if it does not exist.
	 */
	public final static int SPRAT_DEFAULT_THREAD_PRIORITY_INTERRUPT	= Thread.NORM_PRIORITY+1;
	/**
	 * Default thread priority level. This is for most server connection threads. 
	 * Currently this has a normal priority.
	 * This number is the default for the <b>sprat.thread.priority.normal</b> property, if it does not exist.
	 */
	public final static int SPRAT_DEFAULT_THREAD_PRIORITY_NORMAL	= Thread.NORM_PRIORITY;
	/**
	 * Default thread priority level. This is for the Telescope Image Transfer server/client threads. 
	 * Currently this has the lowest priority, so that the camera control is not interrupted by image
	 * transfer requests.
	 * This number is the default for the <b>sprat.thread.priority.tit</b> property, if it does not exist.
	 */
	public final static int SPRAT_DEFAULT_THREAD_PRIORITY_TIT	= Thread.MIN_PRIORITY;
}
