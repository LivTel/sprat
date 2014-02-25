/* sprat_command.c
** Sprat command routines
** $HeadURL$
*/
/**
 * Command routines for the sprat program.
 * @author Chris Mottram
 * @version $Revision$
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

#include "ccd_exposure.h"
#include "ccd_fits_filename.h"
#include "ccd_fits_header.h"
#include "ccd_setup.h"
#include "ccd_temperature.h"

#include "sprat_command.h"
#include "sprat_global.h"
#include "sprat_multrun.h"

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
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Abort",LOG_VERBOSITY_TERSE,"COMMAND","started.");
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
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Abort",LOG_VERBOSITY_TERSE,"COMMAND","finished.");
#endif
	return TRUE;
}

/**
 * Handle a CCD exposure command of the form: bias.
 * @param command_string The command. This is not changed during this routine.
 * @param reply_string The address of a pointer to allocate and set the reply string.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see #COMMAND_ERROR_STRING_LENGTH
 * @see #Command_Error_String
 * @see sprat_multrun.html#Sprat_Multrun_Bias
 * @see sprat_global.html#Sprat_Global_Add_String
 * @see sprat_global.html#Sprat_Global_Error_And_String
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see sprat_global.html#Sprat_Global_Log
 */
int Sprat_Command_Bias(char *command_string,char **reply_string)
{
	char filename[256];
	char multrun_number_string[16];
	int retval,multrun_number;

#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Bias",LOG_VERBOSITY_TERSE,
			    "COMMAND","started.");
#endif
	/* do bias */
	retval = Sprat_Multrun_Bias(1,&multrun_number,filename,256);
	if(retval == FALSE)
	{
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Bias",
						 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
						 COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 Bias failed:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return TRUE;
	}
	if(!Sprat_Global_Add_String(reply_string,"0 "))
		return FALSE;
	sprintf(multrun_number_string,"%d ",multrun_number);
	if(!Sprat_Global_Add_String(reply_string,multrun_number_string))
		return FALSE;
	if(!Sprat_Global_Add_String(reply_string,filename))
		return FALSE;
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Bias",LOG_VERBOSITY_TERSE,
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
		if(!Sprat_Global_Add_String(reply_string,"1 Failed to get nrows config:"))
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
	char multrun_number_string[16];
	int retval,exposure_length,multrun_number;

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
	retval = Sprat_Multrun_Dark(exposure_length,1,&multrun_number,filename,256);
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
	sprintf(multrun_number_string,"%d ",multrun_number);
	if(!Sprat_Global_Add_String(reply_string,multrun_number_string))
		return FALSE;
	if(!Sprat_Global_Add_String(reply_string,filename))
		return FALSE;
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Dark",LOG_VERBOSITY_TERSE,"COMMAND","finished.");
#endif
	return TRUE;
}

/**
 * Handle a CCD exposure command of the form: expose &lt;ms&gt;.
 * @param command_string The command. This is not changed during this routine.
 * @param reply_string The address of a pointer to allocate and set the reply string.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see #COMMAND_ERROR_STRING_LENGTH
 * @see #Command_Error_String
 * @see sprat_multrun.html#Sprat_Multrun_Multrun
 * @see sprat_global.html#Sprat_Global_Add_String
 * @see sprat_global.html#Sprat_Global_Error_And_String
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see sprat_global.html#Sprat_Global_Log
 */
int Sprat_Command_Expose(char *command_string,char **reply_string)
{
	char filename[256];
	char multrun_number_string[16];
	int retval,exposure_length,multrun_number;

#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Expose",LOG_VERBOSITY_TERSE,"COMMAND","started.");
#endif
	/* parse command */
	retval = sscanf(command_string,"expose %d",&exposure_length);
	if(retval != 1)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Expose",LOG_VERBOSITY_TERSE,
				 "COMMAND","finished (command parse failed).");
#endif
		Sprat_Global_Error_Number = 614;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_Expose:"
			"Failed to parse command %s (%d).",command_string,retval);
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Expose",
					      LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
					      COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 Parsing expose command failed:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return FALSE;
	}
	/* do exposure */
	retval = Sprat_Multrun_Multrun(exposure_length,1,FALSE,&multrun_number,filename,256);
	if(retval == FALSE)
	{
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Expose",
						 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
						 COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 Expose failed:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return TRUE;
	}
	if(!Sprat_Global_Add_String(reply_string,"0 "))
		return FALSE;
	sprintf(multrun_number_string,"%d ",multrun_number);
	if(!Sprat_Global_Add_String(reply_string,multrun_number_string))
		return FALSE;
	if(!Sprat_Global_Add_String(reply_string,filename))
		return FALSE;
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Expose",LOG_VERBOSITY_TERSE,"COMMAND",
			 "finished.");
#endif
	return TRUE;
}

/**
 * Implementation of FITS Header commands.
 * @param command_string The command. This is not changed during this routine.
 * @param reply_string The address of a pointer to allocate and set the reply string.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see #COMMAND_ERROR_STRING_LENGTH
 * @see #Command_Error_String
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Error_And_String
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see sprat_global.html#Sprat_Global_Add_String
 * @see ../ccd/cdocs/ccd_fits_header.html#Fits_Header_Struct
 * @see ../ccd/cdocs/ccd_fits_header.html#CCD_Fits_Header_Add_Logical
 * @see ../ccd/cdocs/ccd_fits_header.html#CCD_Fits_Header_Add_Float
 * @see ../ccd/cdocs/ccd_fits_header.html#CCD_Fits_Header_Add_Int
 * @see ../ccd/cdocs/ccd_fits_header.html#CCD_Fits_Header_Add_String
 * @see ../ccd/cdocs/ccd_fits_header.html#CCD_Fits_Header_Clear
 * @see ../ccd/cdocs/ccd_fits_header.html#CCD_Fits_Header_Delete
 */
int Sprat_Command_Fits_Header(char *command_string,char **reply_string)
{
	struct Fits_Header_Struct *fits_header = NULL;
	char operation_string[8];
	char keyword_string[13];
	char type_string[8];
	char value_string[80];
	int retval,command_string_index,ivalue,value_index;
	double dvalue;

	/* parse command to retrieve operation*/
	retval = sscanf(command_string,"fitsheader %6s %n",operation_string,&command_string_index);
	if((retval != 1)&&(retval != 2)) /* sscanf ism't sure whether %n increments returned value! */
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Fits_Header",
				       LOG_VERBOSITY_TERSE,"COMMAND","finished (command parse failed).");
#endif
		Sprat_Global_Error_Number = 620;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_Fits_Header:"
			"Failed to parse command %s (%d).",command_string,retval);
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Fits_Header",
						 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
						 COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 Failed to parse fitsheader command:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return TRUE;
	}
	/* get pointer to fits header structure to manipulate */
	if(!Sprat_Multrun_Fits_Header_Get(&fits_header))
	{
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Fits_Header",
					      LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
					      COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 fitsheader command failed:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return TRUE;
	}
	/* do operation */
	if(strncmp(operation_string,"add",3) == 0)
	{
		retval = sscanf(command_string+command_string_index,"%12s %7s %n",keyword_string,type_string,
				&value_index);
		if((retval != 3)&&(retval != 2)) /* %n may or may not increment retval */
		{
#if SPRAT_DEBUG > 1
			Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Fits_Header",
					   LOG_VERBOSITY_TERSE,"COMMAND","finished (add command parse failed).");
#endif
			Sprat_Global_Error_Number = 621;
			sprintf(Sprat_Global_Error_String,"Sprat_Command_Fits_Header:"
				"Failed to parse add command %s (%d).",command_string,retval);
			Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Fits_Header",
							 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
							 COMMAND_ERROR_STRING_LENGTH);
			if(!Sprat_Global_Add_String(reply_string,"1 Failed to parse fitsheader add command:"))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
				return FALSE;
			return TRUE;
		}
		strncpy(value_string,command_string+command_string_index+value_index,79);
		value_string[79] = '\0';
		if(strncmp(type_string,"boolean",7)==0)
		{
			/* parse value */
			if(strncmp(value_string,"true",4) == 0)
				ivalue = TRUE;
			else if(strncmp(value_string,"false",5) == 0)
				ivalue = FALSE;
			else
			{
#if SPRAT_DEBUG > 1
				Sprat_Global_Log_Format("command","sprat_command.c","Sprat_Command_Fits_Header",
							  LOG_VERBOSITY_TERSE,"COMMAND",
							  "Add boolean command had unknown value %s.",value_string);
#endif
				Sprat_Global_Error_Number = 622;
				sprintf(Sprat_Global_Error_String,"Sprat_Command_Fits_Header:"
					"Add boolean command had unknown value %s.",value_string);
				Sprat_Global_Error_And_String("command","sprat_command.c",
								 "Sprat_Command_Fits_Header",
								 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
								 COMMAND_ERROR_STRING_LENGTH);
				if(!Sprat_Global_Add_String(reply_string,
							   "1 Failed to parse fitsheader add boolean command value:"))
					return FALSE;
				if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
					return FALSE;
				return TRUE;
			}
			/* do operation */
			if(!CCD_Fits_Header_Add_Logical(fits_header,keyword_string,ivalue,NULL))
			{
#if SPRAT_DEBUG > 1
				Sprat_Global_Log_Format("command","sprat_command.c","Sprat_Command_Fits_Header",
							LOG_VERBOSITY_TERSE,"COMMAND",
							"Failed to add boolean to FITS header.");
#endif
				Sprat_Global_Error_Number = 600;
				sprintf(Sprat_Global_Error_String,"Sprat_Command_Fits_Header:"
					"Failed to add logical FITS header.");
				Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Fits_Header",
							      LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
							      COMMAND_ERROR_STRING_LENGTH);
				if(!Sprat_Global_Add_String(reply_string,"1 Failed to add boolean fits header:"))
					return FALSE;
				if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
					return FALSE;
				return TRUE;
			}
		}
		else if(strncmp(type_string,"float",5)==0)
		{
			/* parse value */
			retval = sscanf(value_string,"%lf",&dvalue);
			if(retval != 1)
			{
#if SPRAT_DEBUG > 1
				Sprat_Global_Log_Format("command","sprat_command.c","Sprat_Command_Fits_Header",
							  LOG_VERBOSITY_TERSE,"COMMAND",
							  "Add float command had unknown value %s.",value_string);
#endif
				Sprat_Global_Error_Number = 623;
				sprintf(Sprat_Global_Error_String,"Sprat_Command_Fits_Header:"
					"Add float command had unknown value %s.",value_string);
				Sprat_Global_Error_And_String("command","sprat_command.c",
								 "Sprat_Command_Fits_Header",
								 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
								 COMMAND_ERROR_STRING_LENGTH);
				if(!Sprat_Global_Add_String(reply_string,
							   "1 Failed to parse fitsheader add float command value:"))
					return FALSE;
				if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
					return FALSE;
				return TRUE;
			}
			/* do operation */
			if(!CCD_Fits_Header_Add_Float(fits_header,keyword_string,dvalue,NULL))
			{
#if SPRAT_DEBUG > 1
				Sprat_Global_Log_Format("command","sprat_command.c","Sprat_Command_Fits_Header",
							  LOG_VERBOSITY_TERSE,"COMMAND",
							  "Failed to add float to FITS header.");
#endif
				Sprat_Global_Error_Number = 601;
				sprintf(Sprat_Global_Error_String,"Sprat_Command_Fits_Header:"
					"Failed to add float FITS header.");
				Sprat_Global_Error_And_String("command","sprat_command.c",
								 "Sprat_Command_Fits_Header",
								 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
								 COMMAND_ERROR_STRING_LENGTH);
				if(!Sprat_Global_Add_String(reply_string,"1 Failed to add float fits header:"))
					return FALSE;
				if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
					return FALSE;
				return TRUE;
			}
		}
		else if(strncmp(type_string,"integer",7)==0)
		{
			/* parse value */
			retval = sscanf(value_string,"%d",&ivalue);
			if(retval != 1)
			{
				Sprat_Global_Error_Number = 624;
				sprintf(Sprat_Global_Error_String,"Sprat_Command_Fits_Header:"
					"Add integer command had unknown value %s.",value_string);
#if SPRAT_DEBUG > 1
				Sprat_Global_Log_Format("command","sprat_command.c","Sprat_Command_Fits_Header",
							LOG_VERBOSITY_TERSE,"COMMAND",
							"Add integer command had unknown value %s.",value_string);
#endif
				Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Fits_Header",
							      LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
							      COMMAND_ERROR_STRING_LENGTH);
				if(!Sprat_Global_Add_String(reply_string,
							    "1 Failed to parse fitsheader add integer command value:"))
					return FALSE;
				if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
					return FALSE;
				return TRUE;
			}
			/* do operation */
			if(!CCD_Fits_Header_Add_Int(fits_header,keyword_string,ivalue,NULL))
			{
#if SPRAT_DEBUG > 1
				Sprat_Global_Log_Format("command","sprat_command.c","Sprat_Command_Fits_Header",
							  LOG_VERBOSITY_TERSE,"COMMAND",
							  "Failed to add integer to FITS header.");
#endif
				Sprat_Global_Error_Number = 602;
				sprintf(Sprat_Global_Error_String,"Sprat_Command_Fits_Header:"
					"Failed to add integer FITS header.");
				Sprat_Global_Error_And_String("command","sprat_command.c",
								 "Sprat_Command_Fits_Header",
								 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
								 COMMAND_ERROR_STRING_LENGTH);
				if(!Sprat_Global_Add_String(reply_string,"1 Failed to add integer fits header:"))
					return FALSE;
				if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
					return FALSE;
				return TRUE;
			}
		}
		else if(strncmp(type_string,"string",6)==0)
		{
			/* do operation */
			if(!CCD_Fits_Header_Add_String(fits_header,keyword_string,value_string,NULL))
			{
#if SPRAT_DEBUG > 1
				Sprat_Global_Log_Format("command","sprat_command.c","Sprat_Command_Fits_Header",
							  LOG_VERBOSITY_TERSE,"COMMAND",
							  "Failed to add string to FITS header.");
#endif
				Sprat_Global_Error_Number = 603;
				sprintf(Sprat_Global_Error_String,"Sprat_Command_Fits_Header:"
					"Failed to add string FITS header.");
				Sprat_Global_Error_And_String("command","sprat_command.c",
								 "Sprat_Command_Fits_Header",
								 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
								 COMMAND_ERROR_STRING_LENGTH);
				if(!Sprat_Global_Add_String(reply_string,"1 Failed to add string fits header:"))
					return FALSE;
				if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
					return FALSE;
				return TRUE;
			}
		}
		else
		{
			Sprat_Global_Error_Number = 625;
			sprintf(Sprat_Global_Error_String,"Sprat_Command_Fits_Header:"
				"Add command had unknown type %s.",type_string);
#if SPRAT_DEBUG > 1
			Sprat_Global_Log_Format("command","sprat_command.c","Sprat_Command_Fits_Header",
				       LOG_VERBOSITY_TERSE,"COMMAND","Add command had unknown type %s.",type_string);
#endif
			Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Fits_Header",
						      LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
						      COMMAND_ERROR_STRING_LENGTH);
			if(!Sprat_Global_Add_String(reply_string,"1 Failed to parse fitsheader add command type:"))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
				return FALSE;
			return TRUE;
		}
	}
	else if(strncmp(operation_string,"delete",6) == 0)
	{
		retval = sscanf(command_string+command_string_index,"%12s",keyword_string);
		if(retval != 1)
		{
			Sprat_Global_Error_Number = 626;
			sprintf(Sprat_Global_Error_String,"Sprat_Command_Fits_Header:"
				"Failed to parse delete command %s (%d).",command_string,retval);
#if SPRAT_DEBUG > 1
			Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Fits_Header",
					   LOG_VERBOSITY_TERSE,"COMMAND","finished (delete command parse failed).");
#endif
			Sprat_Global_Error_And_String("command","sprat_command.c",
							 "Sprat_Command_Fits_Header",
							 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
							 COMMAND_ERROR_STRING_LENGTH);
			if(!Sprat_Global_Add_String(reply_string,"1 Failed to parse fitsheader delete command:"))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
				return FALSE;
			return TRUE;
		}
		/* do delete */
		if(!CCD_Fits_Header_Delete(fits_header,keyword_string))
		{
#if SPRAT_DEBUG > 1
			Sprat_Global_Log_Format("command","sprat_command.c","Sprat_Command_Fits_Header",
						  LOG_VERBOSITY_TERSE,"COMMAND",
						  "Failed to delete FITS header with keyword '%s'.",keyword_string);
#endif
			Sprat_Global_Error_Number = 604;
			sprintf(Sprat_Global_Error_String,"Sprat_Command_Fits_Header:"
				"Failed to delete FITS header %s.",keyword_string);
			Sprat_Global_Error_And_String("command","sprat_command.c",
							 "Sprat_Command_Fits_Header",
							 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
							 COMMAND_ERROR_STRING_LENGTH);
			if(!Sprat_Global_Add_String(reply_string,"1 Failed to delete fits header:"))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
				return FALSE;
			return TRUE;
		}
	}
	else if(strncmp(operation_string,"clear",5) == 0)
	{
		/* do clear */
		if(!CCD_Fits_Header_Clear(fits_header))
		{
#if SPRAT_DEBUG > 1
			Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Fits_Header",
					   LOG_VERBOSITY_TERSE,"COMMAND","Failed to clear FITS header.");
#endif
			Sprat_Global_Error_Number = 605;
			sprintf(Sprat_Global_Error_String,"Sprat_Command_Fits_Header:Failed to clear FITS headers.");
			Sprat_Global_Error_And_String("command","sprat_command.c",
							 "Sprat_Command_Fits_Header",
							 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
							 COMMAND_ERROR_STRING_LENGTH);
			if(!Sprat_Global_Add_String(reply_string,"1 Failed to clear fits header:"))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
				return FALSE;
			return TRUE;
		}
	}
	else
	{
		Sprat_Global_Error_Number = 627;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_Fits_Header:"
			"Unknown operation %s:Failed to parse command %s.",operation_string,command_string);
#if SPRAT_DEBUG > 1
		Sprat_Global_Log_Format("command","sprat_command.c","Sprat_Command_Fits_Header",
					  LOG_VERBOSITY_TERSE,
					  "COMMAND","Unknown operation %s:Failed to parse command %s.",
					  operation_string,command_string);
#endif
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Fits_Header",
						 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
						 COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,
					       "1 Failed to parse fitsheader command: Unknown operation:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return TRUE;
	}
	if(!Sprat_Global_Add_String(reply_string,"0 FITS Header command succeeded."))
		return FALSE;
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Fits_Header",LOG_VERBOSITY_TERSE,
			   "COMMAND","finished.");
#endif
	return TRUE;
}

/**
 * Handle a CCD exposure command of the form: multbias &lt;count&gt;.
 * @param command_string The command. This is not changed during this routine.
 * @param reply_string The address of a pointer to allocate and set the reply string.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see #COMMAND_ERROR_STRING_LENGTH
 * @see #Command_Error_String
 * @see sprat_multrun.html#Sprat_Multrun_Bias
 * @see sprat_global.html#Sprat_Global_Add_String
 * @see sprat_global.html#Sprat_Global_Error_And_String
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see sprat_global.html#Sprat_Global_Log
 */
int Sprat_Command_MultBias(char *command_string,char **reply_string)
{
	char filename[256];
	char multrun_number_string[16];
	int retval,exposure_count,multrun_number;

#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_MultBias",LOG_VERBOSITY_TERSE,
			    "COMMAND","started.");
#endif
	/* parse command */
	retval = sscanf(command_string,"multbias %d",&exposure_count);
	if(retval != 1)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("command","sprat_command.c","Sprat_Command_MultBias",
				    LOG_VERBOSITY_TERSE,"COMMAND","finished (command parse failed).");
#endif
		Sprat_Global_Error_Number = 114;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_MultBias:"
			"Failed to parse command %s (%d).",command_string,retval);
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_MultBias",
						 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
						 COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 MultBias failed:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return FALSE;
	}
	/* do bias */
	retval = Sprat_Multrun_Bias(exposure_count,&multrun_number,filename,256);
	if(retval == FALSE)
	{
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_MultBias",
						 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
						 COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 MultBias failed:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return TRUE;
	}
	if(!Sprat_Global_Add_String(reply_string,"0 "))
		return FALSE;
	sprintf(multrun_number_string,"%d ",multrun_number);
	if(!Sprat_Global_Add_String(reply_string,multrun_number_string))
		return FALSE;
	if(!Sprat_Global_Add_String(reply_string,filename))
		return FALSE;
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_MultBias",LOG_VERBOSITY_TERSE,
			       "COMMAND","finished.");
#endif
	return TRUE;
}

/**
 * Handle a CCD exposure command of the form: multdark &lt;length ms&gt; &lt;count&gt;.
 * @param command_string The command. This is not changed during this routine.
 * @param reply_string The address of a pointer to allocate and set the reply string.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see #COMMAND_ERROR_STRING_LENGTH
 * @see #Command_Error_String
 * @see sprat_multrun.html#Sprat_Multrun_Dark
 * @see sprat_global.html#Sprat_Global_Add_String
 * @see sprat_global.html#Sprat_Global_Error_And_String
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see sprat_global.html#Sprat_Global_Log
 */
int Sprat_Command_MultDark(char *command_string,char **reply_string)
{
	char filename[256];
	char multrun_number_string[16];
	int retval,exposure_length,exposure_count,multrun_number,standard;

#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_MultDark",LOG_VERBOSITY_TERSE,
			    "COMMAND","started.");
#endif
	/* parse command */
	retval = sscanf(command_string,"multdark %d %d",&exposure_length,&exposure_count);
	if(retval != 2)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("command","sprat_command.c","Sprat_Command_MultDark",
				    LOG_VERBOSITY_TERSE,"COMMAND","finished (command parse failed).");
#endif
		Sprat_Global_Error_Number = 606;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_MultDark:"
			"Failed to parse command %s (%d).",command_string,retval);
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_MultDark",
						 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
						 COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 MultDark failed:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return FALSE;
	}
	/* do exposure */
	retval = Sprat_Multrun_Dark(exposure_length,exposure_count,&multrun_number,filename,256);
	if(retval == FALSE)
	{
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_MultDark",
						 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
						 COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 MultDark failed:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return TRUE;
	}
	if(!Sprat_Global_Add_String(reply_string,"0 "))
		return FALSE;
	sprintf(multrun_number_string,"%d ",multrun_number);
	if(!Sprat_Global_Add_String(reply_string,multrun_number_string))
		return FALSE;
	if(!Sprat_Global_Add_String(reply_string,filename))
		return FALSE;
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_MultDark",LOG_VERBOSITY_TERSE,
			 "COMMAND","finished.");
#endif
	return TRUE;
}

/**
 * Handle a CCD exposure command of the form: multrun &lt;length ms&gt; &lt;count&gt; &lt;standard (true|false)&gt;.
 * @param command_string The command. This is not changed during this routine.
 * @param reply_string The address of a pointer to allocate and set the reply string.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see #COMMAND_ERROR_STRING_LENGTH
 * @see #Command_Error_String
 * @see sprat_multrun.html#Sprat_Multrun_Multrun
 * @see sprat_global.html#Sprat_Global_Add_String
 * @see sprat_global.html#Sprat_Global_Error_And_String
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see sprat_global.html#Sprat_Global_Log
 */
int Sprat_Command_Multrun(char *command_string,char **reply_string)
{
	char filename[256];
	char standard_string[8];
	char multrun_number_string[16];
	int retval,exposure_length,exposure_count,multrun_number,standard;

#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Multrun",LOG_VERBOSITY_TERSE,"COMMAND","started.");
#endif
	/* parse command */
	retval = sscanf(command_string,"multrun %d %d %8s",&exposure_length,&exposure_count,standard_string);
	if(retval != 3)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Multrun",
				 LOG_VERBOSITY_TERSE,"COMMAND","finished (command parse failed).");
#endif
		Sprat_Global_Error_Number = 641;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_Multrun:"
			"Failed to parse command %s (%d).",command_string,retval);
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Multrun",
					      LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
					      COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 Multrun failed:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return FALSE;
	}
	if(strcmp(standard_string,"true") == 0)
	{
		standard = TRUE;
	}
	else if(strcmp(standard_string,"false") == 0)
	{
		standard = FALSE;
	}
	else
	{
		if(!Sprat_Global_Add_String(reply_string,"1 Illegal Standard string:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,standard_string))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,"."))
			return FALSE;
		return TRUE;
	}
	/* do exposure */
	retval = Sprat_Multrun_Multrun(exposure_length,exposure_count,standard,&multrun_number,filename,256);
	if(retval == FALSE)
	{
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Multrun",
						 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
						 COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 Multrun failed:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return TRUE;
	}
	if(!Sprat_Global_Add_String(reply_string,"0 "))
		return FALSE;
	sprintf(multrun_number_string,"%d ",multrun_number);
	if(!Sprat_Global_Add_String(reply_string,multrun_number_string))
		return FALSE;
	if(!Sprat_Global_Add_String(reply_string,filename))
		return FALSE;
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Multrun",LOG_VERBOSITY_TERSE,
			       "COMMAND","finished.");
#endif
	return TRUE;
}

/**
 * Handle a command of the forms: 
 * <ul>
 * <li>status &lt;exposure&gt [status]
 * <li>status &lt;multrun&gt [index|count]
 * <li>status &lt;temperature&gt [get|status]
 * </ul>
 * <ul>
 * <li>The status command is parsed to retrieve the subsystem (1st parameter).
 * <li>Based on the subsystem, further parsing occurs.
 * <li>The relevant status is retrieved, and a suitable reply constructed.
 * </ul>
 * @param command_string The command. This is not changed during this routine.
 * @param reply_string The address of a pointer to allocate and set the reply string.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see #Command_Status_Exposure
 * @see #Command_Status_Multrun
 * @see #COMMAND_ERROR_STRING_LENGTH
 * @see #Command_Error_String
 * @see sprat_global.html#SPRAT_GLOBAL_ONE_MICROSECOND_NS
 * @see sprat_global.html#Sprat_Global_Add_String
 * @see sprat_global.html#Sprat_Global_Error_And_String
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see sprat_global.html#Sprat_Global_Get_Time_String
 * @see sprat_global.html#Sprat_Global_Log
 * @see ../ccd/cdocs/ccd_exposure.html#CCD_Exposure_Status_Get
 * @see ../ccd/cdocs/ccd_temperature.html#CCD_Temperature_Get
 * @see ../ccd/cdocs/ccd_temperature.html#CCD_Temperature_Status_To_String
 */
int Sprat_Command_Status(char *command_string,char **reply_string)
{
	struct timespec status_time;
#ifndef _POSIX_TIMERS
	struct timeval gtod_status_time;
#endif
	char subsystem_string[16];
	char time_string[32];
	char buff[128];
	enum CCD_EXPOSURE_STATUS exposure_status;
	enum CCD_TEMPERATURE_STATUS temperature_status;
	int retval,command_string_index;
	double temperature;

#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Status",LOG_VERBOSITY_TERSE,"COMMAND","started.");
#endif
	if(command_string == NULL)
	{
		Sprat_Global_Error_Number = 607;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_Status:command_string was NULL.");
		return FALSE;
	}
	if(reply_string == NULL)
	{
		Sprat_Global_Error_Number = 608;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_Status:reply_string was NULL.");
		return FALSE;
	}
#if SPRAT_DEBUG > 5
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Status",LOG_VERBOSITY_TERSE,
			       "COMMAND","Parsing command string.");
#endif
	retval = sscanf(command_string,"status %15s %n",subsystem_string,&command_string_index);
	if((retval != 1)&&(retval != 2)) /* sscanf isn't sure whether %n increments returned value! */
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Status",
				 LOG_VERBOSITY_TERSE,"COMMAND","finished (command parse failed).");
#endif
		Sprat_Global_Error_Number = 613;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_Status:"
			"Failed to parse status command %s (%d).",command_string,retval);
		Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Status",LOG_VERBOSITY_TERSE,
					      "COMMAND",Command_Error_String,COMMAND_ERROR_STRING_LENGTH);
		if(!Sprat_Global_Add_String(reply_string,"1 Failed to parse status command:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
			return FALSE;
		return TRUE;
	}
	if(strcmp(subsystem_string,"exposure") == 0)
	{
		retval = Command_Status_Exposure(command_string+command_string_index,reply_string);
		if(retval == FALSE)
		{
			Sprat_Global_Error_Number = 647;
			sprintf(Sprat_Global_Error_String,"Sprat_Command_Status:Command_Status_Exposure failed.");
			Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Status",
						      LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
						      COMMAND_ERROR_STRING_LENGTH);
			if(!Sprat_Global_Add_String(reply_string,"1 Failed to get exposure status:"))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
				return FALSE;
			return TRUE;
		}
	}
	else if(strcmp(subsystem_string,"multrun") == 0)
	{
		retval = Command_Status_Multrun(command_string+command_string_index,reply_string);
		if(retval == FALSE)
		{
			Sprat_Global_Error_Number = 653;
			sprintf(Sprat_Global_Error_String,"Sprat_Command_Status:"
				"Command_Status_Multrun failed.");
			Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Status",
							 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
							 COMMAND_ERROR_STRING_LENGTH);
			if(!Sprat_Global_Add_String(reply_string,"1 Failed to get multrun status:"))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
				return FALSE;
			return TRUE;
		}
	}
	else if(strcmp(subsystem_string,"temperature") == 0)
	{
		/* get exposure status */
		exposure_status = CCD_Exposure_Status_Get();
		/* if no exposure ongoing, get actual status */
		if(exposure_status == CCD_EXPOSURE_STATUS_NONE)
		{
			if(!CCD_Temperature_Get(&temperature,&temperature_status))
			{
#if SPRAT_DEBUG > 1
				Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Status",
						 LOG_VERBOSITY_TERSE,"COMMAND","Failed to get temperature.");
#endif
				Sprat_Global_Error_Number = 617;
				sprintf(Sprat_Global_Error_String,
					"Sprat_Command_Status:Failed to get temperature.");
				Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Status",
							      LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
							      COMMAND_ERROR_STRING_LENGTH);
				if(!Sprat_Global_Add_String(reply_string,"1 Failed to get temperature:"))
					return FALSE;
				if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
					return FALSE;
				return TRUE;
			}
			/* set status_time to now */
#ifdef _POSIX_TIMERS
			clock_gettime(CLOCK_REALTIME,&status_time);
#else
			gettimeofday(&gtod_status_time,NULL);
			status_time.tv_sec = gtod_status_time.tv_sec;
			status_time.tv_nsec = gtod_status_time.tv_usec*SPRAT_GLOBAL_ONE_MICROSECOND_NS;
#endif
		}
		else /* get cached temperature status */
		{
			if(!CCD_Temperature_Get_Cached_Temperature(&temperature,&temperature_status,&status_time))
			{
#if SPRAT_DEBUG > 1
				Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Status",
						 LOG_VERBOSITY_TERSE,"COMMAND","Failed to get cached temperature.");
#endif
				Sprat_Global_Error_Number = 651;
				sprintf(Sprat_Global_Error_String,"Sprat_Command_Status:"
					"Failed to get cached temperature.");
				Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Status",
							      LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
							      COMMAND_ERROR_STRING_LENGTH);
				if(!Sprat_Global_Add_String(reply_string,"1 Failed to get cached temperature:"))
					return FALSE;
				if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
					return FALSE;
				return TRUE;
			}
		}/* end if exposure_status is not NONE */
		/* check subcommand */
		if(strncmp(command_string+command_string_index,"get",3)==0)
		{
			Sprat_Global_Get_Time_String(status_time,time_string,31);
			if(!Sprat_Global_Add_String(reply_string,"0 "))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string,time_string))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string," "))
				return FALSE;
			sprintf(buff,"%.2f",temperature);
			if(!Sprat_Global_Add_String(reply_string,buff))
				return FALSE;
		}
		else if(strncmp(command_string+command_string_index,"status",6)==0)
		{
			Sprat_Global_Get_Time_String(status_time,time_string,31);
			if(!Sprat_Global_Add_String(reply_string,"0 "))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string,time_string))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string," "))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string,CCD_Temperature_Status_To_String(temperature_status)))
			{
				return FALSE;
			}
		}
		else
		{
#if SPRAT_DEBUG > 1
			Sprat_Global_Log_Format("command","sprat_command.c","Sprat_Command_Status",
						  LOG_VERBOSITY_TERSE,"COMMAND",
						  "Failed to parse temperature command %s from %d.",
						  command_string,command_string_index);
#endif
			Sprat_Global_Error_Number = 618;
			sprintf(Sprat_Global_Error_String,"Sprat_Command_Status:"
				"Failed to parse temperature command %s from %d.",command_string,command_string_index);
			Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Status",
							 LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
							 COMMAND_ERROR_STRING_LENGTH);
			if(!Sprat_Global_Add_String(reply_string,"1 Failed to parse temperature status command:"))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
				return FALSE;
			return TRUE;
		}
	}
	else
	{
		if(!Sprat_Global_Add_String(reply_string,"1 Unknown subsystem:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,subsystem_string))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,"."))
			return FALSE;
		return TRUE;
	}
	return TRUE;
}

/**
 * Parse a temperature control command of the form: "temperature [set &lt;C&gt;|cooler [on|off]]"
 * @param command_string The command. This is not changed during this routine.
 * @param reply_string The address of a pointer to allocate and set the reply string.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see #COMMAND_ERROR_STRING_LENGTH
 * @see #Command_Error_String
 * @see sprat_global.html#Sprat_Global_Add_String
 * @see sprat_global.html#Sprat_Global_Error_And_String
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see sprat_global.html#Sprat_Global_Log
 * @see ../ccd/cdocs/ccd_temperature.html#CCD_Temperature_Set
 * @see ../ccd/cdocs/ccd_temperature.html#CCD_Temperature_Cooler_On
 * @see ../ccd/cdocs/ccd_temperature.html#CCD_Temperature_Cooler_Off
 */
int Sprat_Command_Temperature(char *command_string,char **reply_string)
{
	double target_temperature;
	char type_string[64];
	char parameter_string[64];
	char buff[256];
	int retval;

#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Temperature",LOG_VERBOSITY_TERSE,"COMMAND",
			 "started.");
#endif
	if(command_string == NULL)
	{
		Sprat_Global_Error_Number = 615;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_Temperature:command_string was NULL.");
		return FALSE;
	}
	if(reply_string == NULL)
	{
		Sprat_Global_Error_Number = 616;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_Temperature:reply_string was NULL.");
		return FALSE;
	}
	retval = sscanf(command_string,"temperature %64s %64s",type_string,parameter_string);
	if(retval != 2)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Temperature",LOG_VERBOSITY_TERSE,"COMMAND",
				 "finished (command parse failed).");
#endif
		Sprat_Global_Error_Number = 642;
		sprintf(Sprat_Global_Error_String,"Sprat_Command_Temperature:"
			"Failed to parse temperature command %s (%d).",command_string,retval);
		Sprat_Global_Error("command","sprat_command.c","Sprat_Command_Temperature",
			       LOG_VERBOSITY_TERSE,"COMMAND");
		if(!Sprat_Global_Add_String(reply_string,"1 Failed to parse command string:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,command_string))
			return FALSE;
		return TRUE;
	}
	if(strcmp(type_string,"set") == 0)
	{
		retval = sscanf(parameter_string,"%lf",&target_temperature);
		if(retval != 1)
		{
#if SPRAT_DEBUG > 1
			Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Temperature",
					       LOG_VERBOSITY_TERSE,"COMMAND","finished.");
#endif
			if(!Sprat_Global_Add_String(reply_string,"1 Failed to parse target temperature:"))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string,parameter_string))
				return FALSE;
			return TRUE;
		}
#if SPRAT_DEBUG > 3
		Sprat_Global_Log_Format("command","sprat_command.c","Sprat_Command_Temperature",
					   LOG_VERBOSITY_TERSE,"COMMAND","Setting target temperature to %lf.",
					   target_temperature);
#endif
		retval = CCD_Temperature_Set(target_temperature);
		if(retval == FALSE)
		{
#if SPRAT_DEBUG > 1
			Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Temperature",
					    LOG_VERBOSITY_TERSE,"COMMAND","finished.");
#endif
			Sprat_Global_Error_Number = 643;
			sprintf(Sprat_Global_Error_String,"Sprat_Command_Temperature:Failed to set temperature.");
			Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Temperature",
						      LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
						      COMMAND_ERROR_STRING_LENGTH);
			if(!Sprat_Global_Add_String(reply_string,"1 Failed to set target temperature:"))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
				return FALSE;
			return TRUE;
		}
		/* reply ok */
		if(!Sprat_Global_Add_String(reply_string,"0 target temperature now:"))
			return FALSE;
		sprintf(buff,"%lf",target_temperature);
		if(!Sprat_Global_Add_String(reply_string,buff))
			return FALSE;
	}
	else if(strcmp(type_string,"cooler") == 0)
	{
		if(strcmp(parameter_string,"on")==0)
		{
#if SPRAT_DEBUG > 3
			Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Temperature",
					    LOG_VERBOSITY_TERSE,"COMMAND","Turning cooler on.");
#endif
			retval = CCD_Temperature_Cooler_On();
			if(retval == FALSE)
			{
#if SPRAT_DEBUG > 1
				Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Temperature",
						 LOG_VERBOSITY_TERSE,"COMMAND","finished.");
#endif
				Sprat_Global_Error_Number = 644;
				sprintf(Sprat_Global_Error_String,"Sprat_Command_Temperature:"
					"Failed to turn cooler on.");
				Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Temperature",
							      LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
							      COMMAND_ERROR_STRING_LENGTH);
				if(!Sprat_Global_Add_String(reply_string,"1 Failed to turn cooler on:"))
					return FALSE;
				if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
					return FALSE;
				return TRUE;
			}
			if(!Sprat_Global_Add_String(reply_string,"0 cooler on"))
				return FALSE;
		}
		else if(strcmp(parameter_string,"off")==0)
		{
#if SPRAT_DEBUG > 3
			Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Temperature",
					    LOG_VERBOSITY_TERSE,"COMMAND","Turning cooler off.");
#endif
			retval = CCD_Temperature_Cooler_Off();
			if(retval == FALSE)
			{
#if SPRAT_DEBUG > 1
				Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Temperature",
						 LOG_VERBOSITY_TERSE,"COMMAND","finished.");
#endif
				Sprat_Global_Error_Number = 645;
				sprintf(Sprat_Global_Error_String,"Sprat_Command_Temperature:"
					"Failed to turn cooler on.");
				Sprat_Global_Error_And_String("command","sprat_command.c","Sprat_Command_Temperature",
							      LOG_VERBOSITY_TERSE,"COMMAND",Command_Error_String,
							      COMMAND_ERROR_STRING_LENGTH);
				if(!Sprat_Global_Add_String(reply_string,"1 Failed to turn cooler off:"))
					return FALSE;
				if(!Sprat_Global_Add_String(reply_string,Command_Error_String))
					return FALSE;
				return TRUE;
			}
			if(!Sprat_Global_Add_String(reply_string,"0 cooler off"))
				return FALSE;
		}
		else
		{
			if(!Sprat_Global_Add_String(reply_string,"1 Unknown Parameter:"))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string,parameter_string))
				return FALSE;
			if(!Sprat_Global_Add_String(reply_string,"."))
				return FALSE;
		}
	}
	else
	{
		if(!Sprat_Global_Add_String(reply_string,"1 Unknown type:"))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,type_string))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,"."))
			return FALSE;
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Temperature",
				    LOG_VERBOSITY_TERSE,"COMMAND","finished.");
#endif
		return TRUE;
	}
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("command","sprat_command.c","Sprat_Command_Temperature",LOG_VERBOSITY_TERSE,
			    "COMMAND","finished.");
#endif
	return TRUE;
}

/* ----------------------------------------------------------------------------
** 		internal functions 
** ---------------------------------------------------------------------------- */
/**
 * Internal function dealing with "status exposure <request string>" command.
 * @param request_string What status exposure is being requested.
 * @param reply_string The address of a reallocatable string to store the reply.
 * @return The routine returns TRUE on success, and FALSE on failure.
 * @see sprat_global.html#Sprat_Global_Get_Time_String
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_global.html#Sprat_Global_Add_String
 * @see ../ccd/cdocs/ccd_exposure.html#CCD_EXPOSURE_STATUS
 * @see ../ccd/cdocs/ccd_exposure.html#CCD_Exposure_Status_Get
 * @see ../ccd/cdocs/ccd_exposure.html#CCD_Exposure_Status_To_String
 * @see ../ccd/cdocs/ccd_exposure.html#CCD_Exposure_Length_Get
 * @see ../ccd/cdocs/ccd_exposure.html#CCD_Exposure_Start_Time_Get
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_Fits_Filename_Multrun_Get
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_Fits_Filename_Run_Get
 */
static int Command_Status_Exposure(char *request_string,char **reply_string)
{
	struct timespec status_time;
	enum CCD_EXPOSURE_STATUS exposure_status;
	char time_string[32];
	char value_string[32];
	int ivalue,retval;

	if(strncmp(request_string,"status",6)==0)
	{
		exposure_status = CCD_Exposure_Status_Get();
		if(!Sprat_Global_Add_String(reply_string,"0 "))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,CCD_Exposure_Status_To_String(exposure_status)))
			return FALSE;
	}
	else if(strncmp(request_string,"multrun",7)==0)
	{
		ivalue = CCD_Fits_Filename_Multrun_Get();
		if(!Sprat_Global_Add_String(reply_string,"0 "))
			return FALSE;
		sprintf(value_string,"%d",ivalue);
		if(!Sprat_Global_Add_String(reply_string,value_string))
			return FALSE;
	}
	else if(strncmp(request_string,"run",3)==0)
	{
		ivalue = CCD_Fits_Filename_Run_Get();
		if(!Sprat_Global_Add_String(reply_string,"0 "))
			return FALSE;
		sprintf(value_string,"%d",ivalue);
		if(!Sprat_Global_Add_String(reply_string,value_string))
			return FALSE;
	}
	else if(strncmp(request_string,"length",6)==0)
	{
		ivalue = CCD_Exposure_Length_Get();
		if(!Sprat_Global_Add_String(reply_string,"0 "))
			return FALSE;
		sprintf(value_string,"%d",ivalue);
		if(!Sprat_Global_Add_String(reply_string,value_string))
			return FALSE;
	}
	else if(strncmp(request_string,"start_time",10)==0)
	{
		status_time = CCD_Exposure_Start_Time_Get();
		if(!Sprat_Global_Add_String(reply_string,"0 "))
			return FALSE;
		Sprat_Global_Get_Time_String(status_time,time_string,31);
		if(!Sprat_Global_Add_String(reply_string,time_string))
			return FALSE;
	}
	else
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log_Format("command","sprat_command.c","Sprat_Command_Status",
					  LOG_VERBOSITY_TERSE,"COMMAND","Failed to parse exposure command %s.",
					  request_string);
#endif
		Sprat_Global_Error_Number = 648;
		sprintf(Sprat_Global_Error_String,"Command_Status_Exposure:"
			"Failed to parse exposure command %s.",request_string);
		Sprat_Global_Error("command","sprat_command.c","Command_Status_Exposure",
				     LOG_VERBOSITY_TERSE,"COMMAND");
		sprintf(value_string,"%d ",Sprat_Global_Error_Number);
		if(!Sprat_Global_Add_String(reply_string,value_string))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Sprat_Global_Error_String))
			return FALSE;
		return TRUE;
	}
	return TRUE;
}

/**
 * Internal function dealing with "status multrun <request string>" command.
 * @param request_string What status exposure is being requested.
 * @param reply_string The address of a reallocatable string to store the reply.
 * @return The routine returns TRUE on success, and FALSE on failure.
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_global.html#Sprat_Global_Add_String
 * @see sprat_multrun.html#Sprat_Multrun_Exposure_Index_Get
 * @see sprat_multrun.html#Sprat_Multrun_Exposure_Count_Get
 */
static int Command_Status_Multrun(char *request_string,char **reply_string)
{
	char value_string[32];
	int ivalue,retval;

	if(strncmp(request_string,"index",5)==0)
	{
		ivalue = Sprat_Multrun_Exposure_Index_Get();
		if(!Sprat_Global_Add_String(reply_string,"0 "))
			return FALSE;
		sprintf(value_string,"%d",ivalue);
		if(!Sprat_Global_Add_String(reply_string,value_string))
			return FALSE;
	}
	else if(strncmp(request_string,"count",5)==0)
	{
		ivalue = Sprat_Multrun_Exposure_Count_Get();
		if(!Sprat_Global_Add_String(reply_string,"0 "))
			return FALSE;
		sprintf(value_string,"%d",ivalue);
		if(!Sprat_Global_Add_String(reply_string,value_string))
			return FALSE;
	}
	else
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log_Format("command","sprat_command.c","Sprat_Command_Multrun",
					  LOG_VERBOSITY_TERSE,"COMMAND","Failed to parse multrun command %s.",
					  request_string);
#endif
		Sprat_Global_Error_Number = 654;
		sprintf(Sprat_Global_Error_String,"Command_Status_Multrun:"
			"Failed to parse multrun command %s.",request_string);
		Sprat_Global_Error("command","sprat_command.c","Command_Status_Multrun",
				     LOG_VERBOSITY_TERSE,"COMMAND");
		sprintf(value_string,"%d ",Sprat_Global_Error_Number);
		if(!Sprat_Global_Add_String(reply_string,value_string))
			return FALSE;
		if(!Sprat_Global_Add_String(reply_string,Sprat_Global_Error_String))
			return FALSE;
		return TRUE;
	}
	return TRUE;
}

/**
 * Parse a date of the form "2007-05-03T07:38:48.099 UTC" into number of seconds since 1970 (unix time).
 * @param time_string The string.
 * @param time_secs The address of an integer to store the number of seconds.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see #TIMEZONE_OFFSET_BST
 * @see #TIMEZONE_OFFSET_HST
 */
static int Command_Parse_Date(char *time_string,int *time_secs)
{
	struct tm time_data;
	int year,month,day,hours,minutes,retval;
	double seconds;
	char timezone_string[16];
	time_t time_in_secs;

	/* check parameters */
	if(time_string == NULL)
	{
		Sprat_Global_Error_Number = 655;
		sprintf(Sprat_Global_Error_String,"Command_Parse_Date:time_string was NULL.");
		return FALSE;
	}
	if(time_secs == NULL)
	{
		Sprat_Global_Error_Number = 656;
		sprintf(Sprat_Global_Error_String,"Command_Parse_Date:time_secs was NULL.");
		return FALSE;
	}
#if SPRAT_DEBUG > 9
	Sprat_Global_Log_Format("command","sprat_command.c","Command_Parse_Date",LOG_VERBOSITY_TERSE,
				   "COMMAND","Parsing date/time '%s'.",time_string);
#endif
	/* parse time_string into fields */
	strcpy(timezone_string,"UTC");
	retval = sscanf(time_string,"%d-%d-%d T %d:%d:%lf %15s",&year,&month,&day,
			&hours,&minutes,&seconds,timezone_string);
	if(retval < 6)
	{
		Sprat_Global_Error_Number = 657;
		sprintf(Sprat_Global_Error_String,
			"Command_Parse_Date:Failed to parse '%s', only parsed %d fields: year=%d,month=%d,day=%d,"
			"hour=%d,minute=%d,second=%.2f,timezone_string=%s.",time_string,retval,year,month,day,
			hours,minutes,seconds,timezone_string);
		return FALSE;
	}
#if SPRAT_DEBUG > 9
	Sprat_Global_Log_Format("command","sprat_command.c","Command_Parse_Date",LOG_VERBOSITY_TERSE,"COMMAND",
			    "Date/time '%s' has year=%d,month=%d,day=%d,hour=%d,minute=%d,seconds=%.2lf,timezone=%s.",
				time_string,year,month,day,hours,minutes,seconds,timezone_string);
#endif
	/* construct tm */
	time_data.tm_year  = year-1900; /* years since 1900 */
	time_data.tm_mon = month-1; /* 0..11 */
	time_data.tm_mday  = day; /* 1..31 */
	time_data.tm_hour  = hours; /* 0..23 */
	time_data.tm_min   = minutes;
	time_data.tm_sec   = seconds;
	time_data.tm_wday  = 0;
	time_data.tm_yday  = 0;
	time_data.tm_isdst = 0;
	/* BSD extension stuff */
	/*
	time_data.tm_gmtoff = 0;
	time_data.tm_zone = strdup(timezone_string);
	*/
	/* create time in UTC */
	time_in_secs = mktime(&time_data);
	if(time_in_secs < 0)
	{
		Sprat_Global_Error_Number = 658;
		sprintf(Sprat_Global_Error_String,"Command_Parse_Date:mktime failed.",timezone_string);
		return FALSE;
	}
	(*time_secs) = (int)time_in_secs;
	if(strcmp(timezone_string,"UTC") == 0)
	{
		/* do nothing */
		(*time_secs) = (*time_secs);
	}
	else if(strcmp(timezone_string,"GMT") == 0)
	{
		/* do nothing */
		(*time_secs) = (*time_secs);
	}
	else
	{
		Sprat_Global_Error_Number = 659;
		sprintf(Sprat_Global_Error_String,"Command_Parse_Date:Unknown timezone '%s'.",timezone_string);
		return FALSE;
	}
	return TRUE;
}

