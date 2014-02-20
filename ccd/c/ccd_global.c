/* ccd_global.c
** low level ccd library
** $HeadURL$
*/
/**
 * ccd_global.c contains routines that tie together all the modules that make up libsprat_ccd.
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

#include <stdio.h>
#include <errno.h>
#include <limits.h>
#include <sys/types.h>
#include <string.h>
#ifndef _POSIX_TIMERS
#include <sys/time.h>
#endif
#include <time.h>
#include <stdarg.h>
#include <unistd.h>
#include "atmcdLXd.h"
#include "log_udp.h"
#include "ccd_exposure.h"
#include "ccd_fits_filename.h"
#include "ccd_fits_header.h"
#include "ccd_global.h"
#include "ccd_setup.h"
#include "ccd_temperature.h"

/* data types */
/**
 * Data type holding local data to ccd_global. This consists of the following:
 * <dl>
 * <dt>Global_Log_Handler</dt> <dd>Function pointer to the routine that will log messages passed to it.</dd>
 * <dt>Global_Log_Filter</dt> <dd>Function pointer to the routine that will filter log messages passed to it.
 * 		The funtion will return TRUE if the message should be logged, and FALSE if it shouldn't.</dd>
 * <dt>Global_Log_Filter_Level</dt> <dd>A globally maintained log filter level. 
 * 		This is set using CCD_Global_Set_Log_Filter_Level.
 * 		CCD_Global_Log_Filter_Level_Absolute and CCD_Global_Log_Filter_Level_Bitwise test it against
 * 		message levels to determine whether to log messages.</dd>
 * </dl>
 * @see #CCD_Global_Log
 * @see #CCD_Global_Set_Log_Filter_Level
 * @see #CCD_Global_Log_Filter_Level_Absolute
 * @see #CCD_Global_Log_Filter_Level_Bitwise
 */
struct Global_Struct
{
	void (*Global_Log_Handler)(char *sub_system,char *source_filename,char *function,int level,char *category,
				   char *string);
	int (*Global_Log_Filter)(char *sub_system,char *source_filename,char *function,int level,char *category,
				 char *string);
	int Global_Log_Filter_Level;
};

/* external data */

/* internal data */
/**
 * Revision Control System identifier.
 */
static char rcsid[] = "$Id$";
/**
 * Variable holding error code of last operation performed by ccd_dsp.
 */
static int Global_Error_Number = 0;
/**
 * Internal variable holding description of the last error that occured.
 */
static char Global_Error_String[CCD_GLOBAL_ERROR_STRING_LENGTH] = "";
/**
 * The instance of Global_Struct that contains local data for this module.
 * This is statically initialised to the following:
 * <dl>
 * <dt>Global_Log_Handler</dt> <dd>NULL</dd>
 * <dt>Global_Log_Filter</dt> <dd>NULL</dd>
 * <dt>Global_Log_Filter_Level</dt> <dd>0</dd>
 * </dl>
 * @see #Global_Struct
 */
static struct Global_Struct Global_Data = 
{
	NULL,NULL,0
};

/* ----------------------------------------------------------------------------
** 		external functions 
** ---------------------------------------------------------------------------- */
/**
 * This routine calls all initialisation routines for modules in libccd. These routines are generally used to
 * initialise parts of the library. This routine should be called at the start of a program.
 * <i>
 * Note some of the initialisation can produce errors. Currently these are just printed out, this should
 * perhaps return an error code when this occurs.
 * </i>
 * @see #Global_Data
 * @see ccd_exposure.html#CCD_Exposure_Initialise
 * @see ccd_setup.html#CCD_Setup_Initialise
 */
void CCD_Global_Initialise(void)
{
	CCD_Exposure_Initialise();
	CCD_Setup_Initialise();
/* print some compile time information to stdout */
	fprintf(stdout,"CCD_Global_Initialise:%s.\n",rcsid);
	fflush(stdout);
}

/**
 * Check whether a CCD error currently exists.
 * @return Returns TRUE if and error is found, FALSE otherwise.
 * @see ccd_setup.html#CCD_Setup_Get_Error_Number
 * @see ccd_exposure.html#CCD_Exposure_Get_Error_Number
 * @see ccd_temperature.html#CCD_Temperature_Get_Error_Number
 * @see ccd_fits_header.html#CCD_Fits_Header_Get_Error_Number
 * @see ccd_fits_filename.html#CCD_Fits_Filename_Get_Error_Number
 * @see #Global_Error_Number
 */
int CCD_Global_Is_Error(void)
{
	int found = FALSE;

	if(CCD_Setup_Get_Error_Number() != 0)
	{
		found = TRUE;
	}
	if(CCD_Exposure_Get_Error_Number() != 0)
	{
		found = TRUE;
	}
	if(CCD_Temperature_Get_Error_Number() != 0)
	{
		found = TRUE;
	}
	if(CCD_Fits_Header_Get_Error_Number() != 0)
	{
		found = TRUE;
	}
	if(CCD_Fits_Filename_Get_Error_Number() != 0)
	{
		found = TRUE;
	}
	if(Global_Error_Number != 0)
	{
		found = TRUE;
	}
	return found;
}

/**
 * A general error routine. This checks the error numbers for all the modules that make up the library, and
 * for any non-zero numbers prints out the error message to stderr.
 * <b>Note</b> you cannot call both CCD_Global_Error and CCD_Global_Error_To_String to print the error string and 
 * get a string copy of it, only one of the error routines can be called after libccd has generated an error.
 * A second call to one of these routines will generate a 'Error not found' error!.
 * @see ccd_setup.html#CCD_Setup_Get_Error_Number
 * @see ccd_setup.html#CCD_Setup_Error
 * @see ccd_exposure.html#CCD_Exposure_Get_Error_Number
 * @see ccd_exposure.html#CCD_Exposure_Error
 * @see ccd_temperature.html#CCD_Temperature_Get_Error_Number
 * @see ccd_temperature.html#CCD_Temperature_Error
 * @see ccd_fits_header.html#CCD_Fits_Header_Get_Error_Number
 * @see ccd_fits_header.html#CCD_Fits_Header_Error
 * @see ccd_fits_filename.html#CCD_Fits_Filename_Get_Error_Number
 * @see ccd_fits_filename.html#CCD_Fits_Filename_Error
 * @see #CCD_Global_Get_Current_Time_String
 * @see #Global_Error_Number
 * @see #Global_Error_String
 */
void CCD_Global_Error(void)
{
	char time_string[32];
	int found = FALSE;

	if(CCD_Setup_Get_Error_Number() != 0)
	{
		found = TRUE;
		CCD_Setup_Error();
	}
	if(CCD_Exposure_Get_Error_Number() != 0)
	{
		found = TRUE;
		CCD_Exposure_Error();
	}
	if(CCD_Temperature_Get_Error_Number() != 0)
	{
		found = TRUE;
		fprintf(stderr,"\t");
		CCD_Temperature_Error();
	}
	if(CCD_Fits_Header_Get_Error_Number() != 0)
	{
		found = TRUE;
		fprintf(stderr,"\t");
		CCD_Fits_Header_Error();
	}
	if(CCD_Fits_Filename_Get_Error_Number() != 0)
	{
		found = TRUE;
		fprintf(stderr,"\t");
		CCD_Fits_Filename_Error();
	}
	if(Global_Error_Number != 0)
	{
		found = TRUE;
		fprintf(stderr,"\t\t\t");
		CCD_Global_Get_Current_Time_String(time_string,32);
		fprintf(stderr,"%s CCD_Global:Error(%d) : %s\n",time_string,Global_Error_Number,Global_Error_String);
	}
	if(!found)
	{
		fprintf(stderr,"Error:CCD_Global_Error:Error not found\n");
	}
}

/**
 * A general error routine. This checks the error numbers for all the modules that make up the library, and
 * for any non-zero numbers adds the error message to a passed in string. The string parameter is set to the
 * blank string initially.
 * <b>Note</b> you cannot call both CCD_Global_Error and CCD_Global_Error_To_String to print the error string and 
 * get a string copy of it, only one of the error routines can be called after libccd has generated an error.
 * A second call to one of these routines will generate a 'Error not found' error!.
 * @param error_string A character buffer big enough to store the longest possible error message. It is
 * recomended that it is at least 1024 bytes in size.
 * @see ccd_setup.html#CCD_Setup_Get_Error_Number
 * @see ccd_setup.html#CCD_Setup_Error_String
 * @see ccd_exposure.html#CCD_Exposure_Get_Error_Number
 * @see ccd_exposure.html#CCD_Exposure_Error_String
 * @see ccd_temperature.html#CCD_Temperature_Get_Error_Number
 * @see ccd_temperature.html#CCD_Temperature_Error_String
 * @see ccd_fits_header.html#CCD_Fits_Header_Get_Error_Number
 * @see ccd_fits_header.html#CCD_Fits_Header_Error_String
 * @see ccd_fits_filename.html#CCD_Fits_Filename_Get_Error_Number
 * @see ccd_fits_filename.html#CCD_Fits_Filename_Error_String
 * @see #CCD_Global_Get_Current_Time_String
 * @see #Global_Error_Number
 * @see #Global_Error_String
 */
void CCD_Global_Error_To_String(char *error_string)
{
	char time_string[32];

	strcpy(error_string,"");
	if(CCD_Setup_Get_Error_Number() != 0)
	{
		CCD_Setup_Error_String(error_string);
	}
	if(CCD_Exposure_Get_Error_Number() != 0)
	{
		CCD_Exposure_Error_String(error_string);
	}
	if(CCD_Temperature_Get_Error_Number() != 0)
	{
		strcat(error_string,"\t");
		CCD_Temperature_Error_String(error_string);
	}
	if(CCD_Fits_Header_Get_Error_Number() != 0)
	{
		strcat(error_string,"\t");
		CCD_Fits_Header_Error_String(error_string);
	}
	if(CCD_Fits_Filename_Get_Error_Number() != 0)
	{
		strcat(error_string,"\t");
		CCD_Fits_Filename_Error_String(error_string);
	}
	if(Global_Error_Number != 0)
	{
		CCD_Global_Get_Current_Time_String(time_string,32);
		sprintf(error_string+strlen(error_string),"%s CCD_Global:Error(%d) : %s\n",time_string,
			Global_Error_Number,Global_Error_String);
	}
	if(strlen(error_string) == 0)
	{
		strcat(error_string,"Error:CCD_Global_Error:Error not found\n");
	}
}

/**
 * Routine to get the current time in a string. The string is returned in the format
 * '01/01/2000 13:59:59.123', or the string "Unknown time" if the routine failed.
 * The time is in UTC.
 * @param time_string The string to fill with the current time.
 * @param string_length The length of the buffer passed in. It is recommended the length is at least 20 characters.
 */
void CCD_Global_Get_Current_Time_String(char *time_string,int string_length)
{
	struct timespec current_time;
#ifndef _POSIX_TIMERS
	struct timeval gtod_current_time;
#endif
	struct tm *utc_time = NULL;
	char ms_buff[5];

#ifdef _POSIX_TIMERS
	clock_gettime(CLOCK_REALTIME,&current_time);
#else
	gettimeofday(&gtod_current_time,NULL);
	current_time.tv_sec = gtod_current_time.tv_sec;
	current_time.tv_nsec = gtod_current_time.tv_usec*CCD_GLOBAL_ONE_MICROSECOND_NS;
#endif
	utc_time = gmtime(&(current_time.tv_sec));
	strftime(time_string,string_length,"%d/%m/%Y %H:%M:%S",utc_time);
	/* get fractional ms from nsec */
	sprintf(ms_buff,".%03ld",(current_time.tv_nsec/CCD_GLOBAL_ONE_MILLISECOND_NS));
	/* if we have room in the buffer, concatenate */
	if((strlen(time_string)+strlen(ms_buff)+1) < string_length)
	{
		strcat(time_string,ms_buff);
	}
}

/**
 * Convert the specified time to a string. The string is returned in the format
 * '01/01/2000 13:59:59.123', or the string "Unknown time" if the routine failed.
 * The time is in UTC.
 * @param time_string The string to fill with the current time.
 * @param string_length The length of the buffer passed in. It is recommended the length is at least 20 characters.
 * @see #CCD_GLOBAL_ONE_MILLISECOND_NS
 */
void CCD_Global_Get_Time_String(struct timespec time,char *time_string,int string_length)
{
	struct tm *utc_time = NULL;
	char ms_buff[5];

	/* get utc time from time seconds */
	utc_time = gmtime(&(time.tv_sec));
	strftime(time_string,string_length,"%d/%m/%Y %H:%M:%S",utc_time);
	/* get fractional ms from nsec */
	sprintf(ms_buff,".%03ld",(time.tv_nsec/CCD_GLOBAL_ONE_MILLISECOND_NS));
	/* if we have room in the buffer, concatenate */
	if((strlen(time_string)+strlen(ms_buff)+1) < string_length)
	{
		strcat(time_string,ms_buff);
	}
}

/**
 * Routine to log a message to a defined logging mechanism. This routine has an arbitary number of arguments,
 * and uses vsprintf to format them i.e. like fprintf. A buffer is used to hold the created string,
 * therefore the total length of the generated string should not be longer than CCD_GLOBAL_ERROR_STRING_LENGTH.
 * CCD_Global_Log is then called to handle the log message.
 * @param sub_system The sub system. Can be NULL.
 * @param source_file The source filename. Can be NULL.
 * @param function The function calling the log. Can be NULL.
 * @param level At what level is the log message (TERSE/high level or VERBOSE/low level), 
 *         a valid member of LOG_VERBOSITY.
 * @param category What sort of information is the message. Designed to be used as a filter. Can be NULL.
 * @param format A string, with formatting statements the same as fprintf would use to determine the type
 * 	of the following arguments.
 * @see #CCD_Global_Log
 * @see #CCD_GLOBAL_ERROR_STRING_LENGTH
 */
void CCD_Global_Log_Format(char *sub_system,char *source_filename,char *function,int level,char *category,
			   char *format,...)
{
	char buff[512];
	va_list ap;

/* format the arguments */
	va_start(ap,format);
	vsprintf(buff,format,ap);
	va_end(ap);
/* call the log routine to log the results */
	CCD_Global_Log(sub_system,source_filename,function,level,category,buff);
}

/**
 * Routine to log a message to a defined logging mechanism. If the string or Global_Data.Global_Log_Handler are NULL
 * the routine does not log the message. If the Global_Data.Global_Log_Filter function pointer is non-NULL, the
 * message is passed to it to determine whether to log the message.
 * @param sub_system The sub system. Can be NULL.
 * @param source_file The source filename. Can be NULL.
 * @param function The function calling the log. Can be NULL.
 * @param level At what level is the log message (TERSE/high level or VERBOSE/low level), 
 *         a valid member of LOG_VERBOSITY.
 * @param category What sort of information is the message. Designed to be used as a filter. Can be NULL.
 * @param string The message to log.
 * @see #Global_Data
 */
void CCD_Global_Log(char *sub_system,char *source_filename,char *function,int level,char *category,char *string)
{
/* If the string is NULL, don't log. */
	if(string == NULL)
		return;
/* If there is no log handler, return */
	if(Global_Data.Global_Log_Handler == NULL)
		return;
/* If there's a log filter, check it returns TRUE for this message */
	if(Global_Data.Global_Log_Filter != NULL)
	{
		if(Global_Data.Global_Log_Filter(sub_system,source_filename,function,level,category,string) == FALSE)
			return;
	}
/* We can log the message */
	(*Global_Data.Global_Log_Handler)(sub_system,source_filename,function,level,category,string);
}

/**
 * Routine to set the Global_Data.Global_Log_Handler used by CCD_Global_Log.
 * @param log_fn A function pointer to a suitable handler.
 * @see #Global_Data
 * @see #CCD_Global_Log
 */
void CCD_Global_Set_Log_Handler_Function(void (*log_fn)(char *sub_system,char *source_filename,char *function,
							int level,char *category,char *string))
{
	Global_Data.Global_Log_Handler = log_fn;
}

/**
 * Routine to set the Global_Data.Global_Log_Filter used by CCD_Global_Log.
 * @param log_fn A function pointer to a suitable filter function.
 * @see #Global_Data
 * @see #CCD_Global_Log
 */
void CCD_Global_Set_Log_Filter_Function(int (*filter_fn)(char *sub_system,char *source_filename,char *function,
							 int level,char *category,char *string))
{
	Global_Data.Global_Log_Filter = filter_fn;
}

/**
 * A log handler to be used for the Global_Data.Global_Log_Handler function.
 * Just prints the message to stdout, terminated by a newline.
 * Just prints the message to stdout, terminated by a newline.
 * @param sub_system The sub system. Can be NULL.
 * @param source_file The source filename. Can be NULL.
 * @param function The function calling the log. Can be NULL.
 * @param level At what level is the log message (TERSE/high level or VERBOSE/low level), 
 *         a valid member of LOG_VERBOSITY.
 * @param category What sort of information is the message. Designed to be used as a filter. Can be NULL.
 * @param string The log message to be logged. 
 */
void CCD_Global_Log_Handler_Stdout(char *sub_system,char *source_filename,char *function,int level,
				   char *category,char *string)
{
	char time_string[32];

	if(string == NULL)
		return;
	CCD_Global_Get_Current_Time_String(time_string,32);
	fprintf(stdout,"%s %s:%s:%s:%s:%s\n",time_string,sub_system,source_filename,function,category,string);
}

/**
 * Routine to set the Global_Data.Global_Log_Filter_Level.
 * @see #Global_Data
 */
void CCD_Global_Set_Log_Filter_Level(int level)
{
	Global_Data.Global_Log_Filter_Level = level;
}

/**
 * A log message filter routine, to be used for the Global_Data.Global_Log_Filter function pointer.
 * @param sub_system The sub system. Can be NULL.
 * @param source_file The source filename. Can be NULL.
 * @param function The function calling the log. Can be NULL.
 * @param level At what level is the log message (TERSE/high level or VERBOSE/low level), 
 *         a valid member of LOG_VERBOSITY.
 * @param category What sort of information is the message. Designed to be used as a filter. Can be NULL.
 * @param string The log message to be logged, not used in this filter. 
 * @return The routine returns TRUE if the level is less than or equal to the Global_Data.Global_Log_Filter_Level,
 * 	otherwise it returns FALSE.
 * @see #Global_Data
 */
int CCD_Global_Log_Filter_Level_Absolute(char *sub_system,char *source_filename,char *function,int level,
					 char *category,char *string)
{
	return (level <= Global_Data.Global_Log_Filter_Level);
}

/**
 * A log message filter routine, to be used for the Global_Data.Global_Log_Filter function pointer.
 * @param sub_system The sub system. Can be NULL.
 * @param source_file The source filename. Can be NULL.
 * @param function The function calling the log. Can be NULL.
 * @param level At what level is the log message (TERSE/high level or VERBOSE/low level), 
 *         a valid member of LOG_VERBOSITY.
 * @param category What sort of information is the message. Designed to be used as a filter. Can be NULL.
 * @param string The log message to be logged, not used in this filter. 
 * @return The routine returns TRUE if the level has bits set that are also set in the 
 * 	Global_Data.Global_Log_Filter_Level, otherwise it returns FALSE.
 * @see #Global_Data
 */
int CCD_Global_Log_Filter_Level_Bitwise(char *sub_system,char *source_filename,char *function,int level,
					char *category,char *string)
{
	return ((level & Global_Data.Global_Log_Filter_Level) > 0);
}

/**
 * Return a string for an Andor error code. See /home/dev/src/andor/andor-2.85.30000/include/atmcdLXd.h.
 * @param error_code The Andor error code.
 * @return A static string representation of the error code.
 */
char* CCD_Global_Andor_ErrorCode_To_String(unsigned int error_code)
{
	switch(error_code)
	{
		case DRV_SUCCESS:
			return "DRV_SUCCESS";
		case DRV_ACQUIRING:
			return "DRV_ACQUIRING";
		case DRV_IDLE:
			return "DRV_IDLE";
		case DRV_P1INVALID:
			return "DRV_P1INVALID";
		case DRV_P2INVALID:
			return "DRV_P2INVALID";
		case DRV_P3INVALID:
			return "DRV_P3INVALID";
		case DRV_P4INVALID:
			return "DRV_P4INVALID";
		case DRV_ERROR_NOCAMERA:
			return "DRV_ERROR_NOCAMERA";
		case DRV_NOT_AVAILABLE:
			return "DRV_NOT_AVAILABLE";
		default:
			return "UNKNOWN";
	}
	return "UNKNOWN";
}

