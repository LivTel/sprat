/* sprat_command.c
** Sprat command routines
** $HeadURL$
*/
/**
 * Command routines for the sprat program.
 * @author Chris Mottram
 * @version $Revision: 1.2 $
 */
/**
 * This hash define is needed before including source files give us POSIX.4/IEEE1003.1b-1993 prototypes.
 */
#define _POSIX_SOURCE 1
/**
 * This hash define is needed before including source files give us POSIX.4/IEEE1003.1b-1993 prototypes.
 */
#define _POSIX_C_SOURCE 199309L

#include <errno.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include "log_udp.h"

#include "command_server.h"

#include "ccd_setup.h"

#include "sprat_command.h"
/* diddly
#include "sprat_fits_filename.h"
#include "sprat_fits_header.h"
*/
#include "sprat_global.h"

/**
 * Length of internal error string.
 */
#define COMMAND_ERROR_STRING_LENGTH (1024)

/* internal data */
/**
 * Revision Control System identifier.
 */
static char rcsid[] = "$Id$";
/**
 * Internal error string used for storing errors to be returned in reply strings to the client.
 * Allocated module wide.
 * @see #COMMAND_ERROR_STRING_LENGTH
 */
static char Command_Error_String[COMMAND_ERROR_STRING_LENGTH];
static int Command_Status_Exposure(char *request_string,char **reply_string);
static int Command_Status_Multrun(char *request_string,char **reply_string);
static int Command_Parse_Date(char *time_string,int *time_secs);

/* ----------------------------------------------------------------------------
** 		external functions 
** ---------------------------------------------------------------------------- */
/**
 * Handle a command of the form: "abort". Tries to abort CCD exposures.
 * @param command_string The command. This is not changed during this routine.
 * @param reply_string The address of a pointer to allocate and set the reply string.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see sprat_global.html#Sprat_Global_Add_String
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see ../ccd/cdocs/ccd_exposure.html#CCD_Exposure_Abort
 */
int Sprat_Command_Abort(char *command_string,char **reply_string)
{
	char *config_filename = NULL;
	int retval;

#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Abort",LOG_VERBOSITY_TERSE,
			       "COMMAND","started.");
#endif
	retval = CCD_Exposure_Abort();
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 646;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_Abort:CCD_Exposure_Abort failed.");
		Sprat_Global_Error("command","sprat_command.c","Sprat_Command_Abort",LOG_VERBOSITY_TERSE,"COMMAND");
	}
	if(!Sprat_Global_Add_String(reply_string,"0 Abort suceeded."))
		return FALSE;
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Abort",LOG_VERBOSITY_TERSE,
			 "COMMAND","finished.");
#endif
	return TRUE;
}

/**
 * Handle a sprat "config" command that configures the CCD camera: of the form:
 * "config &lt;xbin&gt; &lt;ybin&gt; [&lt;startx&gt; &lt;endx&gt; &lt;starty&gt; &lt;endy&gt;]"
 * The window coordinates are in binned pixels.
 * <ul>
 * <li>The config command is parsed to get the config values.
 * <li>The property "ccd.ncols" is retrieved to get the number of columns.
 * <li>The property "ccd.nrows" is retrieved to get the number of rows.
 * <li>Sprat_Multrun_Dimensions_Set is called to setup the multrun image dimensions. This also calls
 *     the CCD library to configure the head dimensions.
 * </ul>
 * @param command_string The command. This is not changed during this routine.
 * @param reply_string The address of a pointer to allocate and set the reply string.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see #COMMAND_ERROR_STRING_LENGTH
 * @see #Command_Error_String
 * @see sprat_config.html#Sprat_Config_Get_Integer
 * @see sprat_global.html#Sprat_Global_Add_String
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see sprat_global.html#Sprat_Global_Error_And_String
 * @see sprat_multrun.html#Sprat_Multrun_Dimensions_Set
 * @see ../ccd/cdocs/ccd_setup.html#CCD_Setup_Window_Struct
 */
int Sprat_Command_Config(char *command_string,char **reply_string)
{
	struct CCD_Setup_Window_Struct window = {0,0,0,0};
	int retval,ncols,nrows,xbin,ybin,use_window;

#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Config",LOG_VERBOSITY_TERSE,"COMMAND","started.");
#endif
	/* parse command */
	retval = sscanf(command_string,"config %d %d %d %d %d %d",&xbin,&ybin,
			&(window.X_Start),&(window.X_End),&(window.Y_Start),&(window.Y_End));
	if((retval != 2)&&(retval != 6))
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Config",
				    LOG_VERBOSITY_TERSE,"COMMAND","finished (command parse failed).");
#endif
		Sprat_Global_Error_Number = 609;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_Config:"
			"Failed to parse command %s (%d).",command_string,retval);
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Config",
						 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
						 COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 Failed to parse config command:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return TRUE;
	}
	/* setup use_window based on whether a window was supplied */
	if(retval == 6)
		use_window = TRUE;
	else
		use_window = FALSE;
	/* get config */
	if(!Sprat_Config_Get_Integer("ccd.ncols",&ncols))
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Config",
				       LOG_VERBOSITY_TERSE,"COMMAND","finished (Failed to get ncols config).");
#endif
		Sprat_Global_Error_Number = 610;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_Config:Failed to get ncols config.");
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Config",LOG_VERBOSITY_TERSE,
					       "COMMAND",Command_Error_String,COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 Failed to get ncols config:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return TRUE;
	}
	if(!Sprat_Config_Get_Integer("ccd.nrows",&nrows))
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Config",
				       LOG_VERBOSITY_TERSE,"COMMAND","finished (Failed to get nrows config).");
#endif
		Sprat_Global_Error_Number = 611;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_Config:Failed to get nrows config.");
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Config",LOG_VERBOSITY_TERSE,
					       "COMMAND",Command_Error_String,COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 Failed to get nrows config."))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return TRUE;
	}
	/* setup multrun dimensions */
	retval = Sprat_Multrun_Dimensions_Set(ncols,nrows,xbin,ybin,use_window,window);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 612;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_Config:Sprat_Multrun_Set_Dimensions failed.");
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Config",LOG_VERBOSITY_TERSE,
					       "COMMAND",Command_Error_String,COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 Config failed to set dimensions:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return TRUE;
	}
	if(!Sprat_Global_Add_String(reply_string,"0 Config suceeded."))
		return FALSE;
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Config",LOG_VERBOSITY_TERSE,
			  "COMMAND","finished.");
#endif
	return TRUE;
}

/**
 * Handle a CCD exposure command of the form: dark &lt;ms&gt;.
 * @param command_string The command. This is not changed during this routine.
 * @param reply_string The address of a pointer to allocate and set the reply string.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see #COMMAND_ERROR_STRING_LENGTH
 * @see #Command_Error_String
 * @see sprat_global.html#Sprat_Global_Error_And_String
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see sprat_global.html#Sprat_Global_Add_String
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_multrun.html#Sprat_Multrun_Dark
 */
int Sprat_Command_Dark(char *command_string,char **reply_string)
{
	char filename[256];
	int retval,exposure_length;

#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Dark",LOG_VERBOSITY_TERSE,"COMMAND","started.");
#endif
	/* parse command */
	retval = sscanf(command_string,"dark %d",&exposure_length);
	if(retval != 1)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Dark",LOG_VERBOSITY_TERSE,"COMMAND",
				 "finished (command parse failed).");
#endif
		Sprat_Global_Error_Number = 619;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_Dark:Failed to parse command %s (%d).",
			command_string,retval);
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Dark",LOG_VERBOSITY_TERSE,
					      "COMMAND",Command_Error_String,COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 Parsing dark command failed:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return FALSE;
	}
	/* do exposure */
	retval = Sprat_Multrun_Dark(exposure_length,1,filename,256);
	if(retval == FALSE)
	{
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Dark",LOG_VERBOSITY_TERSE,
					      "COMMAND",Command_Error_String,COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 Dark failed:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return TRUE;
	}
	if(!Sprat_Global_Add_String(reply_string,"0 "))
		return FALSE;
	if(!Sprat_Global_Add_String(reply_string,filename))
		return FALSE;
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Dark",LOG_VERBOSITY_TERSE,"COMMAND","finished.");
#endif
	return TRUE;
}

