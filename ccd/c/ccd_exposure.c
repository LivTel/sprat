/* ccd_exposure.c
** Low level ccd library
** $HeadeURL$
*/
/**
 * ccd_exposure.c contains routines for performing an exposure with the Andor CCD Controller.
 * @author Chris Mottram
 * @version $Revision$
 */
/**
 * This hash define is needed before including source files give us POSIX.4/IEEE1003.1b-1993 prototypes
 * for time.
 */
#define _POSIX_SOURCE 1
/**
 * This hash define is needed before including source files give us POSIX.4/IEEE1003.1b-1993 prototypes
 * for time.
 */
#define _POSIX_C_SOURCE 199309L
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <signal.h>
#include <errno.h>
#include <math.h>
#include <unistd.h>
#include <sys/types.h>
#ifndef _POSIX_TIMERS
#include <sys/time.h>
#endif
#include <time.h>
#include "atmcdLXd.h"
#include "fitsio.h"
#include "log_udp.h"
#include "ccd_global.h"
#include "ccd_exposure.h"
#include "ccd_fits_filename.h"
#include "ccd_fits_header.h"
#include "ccd_setup.h"
#include "ccd_temperature.h"
#include "ngat_astro.h"
#include "ngat_astro_mjd.h"

/* hash definitions */
/**
 * Number of seconds to wait after an exposure is meant to have finished, before we abort with a timeout signal.
 * Only used for internal triggers at the moment - with an active status loop.
 */
#define EXPOSURE_TIMEOUT_SECS     (30.0)
/**
 * Convertion factor - degrees to Kelvin.
 */
#define CENTIGRADE_TO_KELVIN      (273.15)

/* data types */
/**
 * Structure used to hold local data to ccd_exposure.
 * <dl>
 * <dt>Exposure_Status</dt> <dd>Whether an operation is being performed to CLEAR, EXPOSE or READOUT the CCD.</dd>
 * <dt>Start_Time</dt> <dd>The time stamp when an exposure was started.</dd>
 * <dt>Exposure_Length</dt> <dd>The last exposure length to be set (ms).</dd>
 * <dt>Elapsed_Exposure_Time</dt> <dd>Calculated elapsed exposure length (in the C layer).</dd>
 * <dt>Abort</dt> <dd>Whether to abort an exposure.</dd>
 * <dt>Accumulation</dt> <dd>The current Andor accumulation.</dd>
 * <dt>Series</dt> <dd>The current Andor series.</dd>
 * <dt>Exposure_Loop_Pause_Length</dt> <dd>An amount of time to pause/sleep, in milliseconds, each time
 *     round the loop whilst waiting for an exposure to be done (DRV_ACQUIRING -> DRV_IDLE).
 * </dl>
 * @see #CCD_EXPOSURE_STATUS
 */
struct Exposure_Struct
{
	enum CCD_EXPOSURE_STATUS Exposure_Status;
	struct timespec Start_Time;
	volatile int Exposure_Length;
	volatile int Elapsed_Exposure_Time;
	volatile int Abort;/* This is volatile as a different thread may change this variable. */
	volatile long Accumulation;
	volatile long Series;
	int Exposure_Loop_Pause_Length;
};

/* external variables */

/* internal variables */
/**
 * Revision Control System identifier.
 */
static char rcsid[] = "$Id$";
/**
 * Data holding the current status of ccd_exposure.
 * @see #Exposure_Struct
 * @see #CCD_EXPOSURE_STATUS
 */
static struct Exposure_Struct Exposure_Data = 
{
	CCD_EXPOSURE_STATUS_NONE,
	{0L,0L},
	0,0,FALSE,
	-1L,-1L,
	1
};

/**
 * Variable holding error code of last operation performed by ccd_exposure.
 */
static int Exposure_Error_Number = 0;
/**
 * Local variable holding description of the last error that occured.
 * @see ccd_global.html#CCD_GLOBAL_ERROR_STRING_LENGTH
 */
static char Exposure_Error_String[CCD_GLOBAL_ERROR_STRING_LENGTH] = "";

/* internal functions */
static int Exposure_Wait_For_Start_Time(struct timespec start_time);
static void Exposure_Debug_Buffer(char *description,unsigned short *buffer,size_t buffer_length);
static int Exposure_Save(void *buffer,size_t buffer_length,struct Fits_Header_Struct header,char *filename);
static int fexist(char *filename);

/* ----------------------------------------------------------------------------
** 		external functions 
** ---------------------------------------------------------------------------- */
/**
 * This routine sets up ccd_exposure internal variables.
 * It should be called at startup.
 */
void CCD_Exposure_Initialise(void)
{
	Exposure_Error_Number = 0;
/* print some compile time information to stdout */
	fprintf(stdout,"CCD_Exposure_Initialise:%s.\n",rcsid);
}

/**
 * Do an exposure.
 * @param open_shutter A boolean, TRUE to open the shutter, FALSE to leav it closed (dark).
 * @param start_time The time to start the exposure. If both the fields in the <i>struct timespec</i> are zero,
 * 	the exposure can be started at any convenient time.
 * @param exposure_length_ms The length of time to open the shutter for in milliseconds. 
 *        This must be greater than zero.
 * @param header A list of FITS header keywords and values to be written into the FITS headers of the resulting
 *        image data.
 * @param filename A NULL terminated string containing the filename to put the FITS image into.
 * @return Returns TRUE if the exposure succeeds and the data read out into the FITS image, returns FALSE if an error
 *	occurs or the exposure is aborted.
 * @see #Exposure_Error_Number
 * @see #Exposure_Error_String
 * @see #Exposure_Wait_For_Start_Time
 * @see #Exposure_Save
 * @see ccd_global.html#CCD_Global_Log
 * @see ccd_global.html#CCD_Global_Andor_ErrorCode_To_String
 * @see ccd_setup.html#CCD_Setup_Allocate_Image_Buffer
 */
int CCD_Exposure_Expose(int open_shutter,struct timespec start_time,int exposure_length_ms,
			struct Fits_Header_Struct header,char *filename)
{
	struct timespec sleep_time,current_time;
#ifndef _POSIX_TIMERS
	struct timeval gtod_current_time;
#endif
	unsigned short *image_data = NULL;
	unsigned int andor_retval;
	size_t image_data_length;
	long accumulation,series;
	int exposure_status,acquisition_counter;

	Exposure_Error_Number = 0;
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_exposure.c","CCD_Exposure_Expose",LOG_VERBOSITY_INTERMEDIATE,NULL,
			"CCD_Exposure_Expose started.");
#endif
#if LOGGING > 5
	CCD_Global_Log_Format("ccd","ccd_exposure.c","CCD_Exposure_Expose",LOG_VERBOSITY_INTERMEDIATE,NULL,
			      "CCD_Exposure_Expose(open_shutter=%d,start_time=%ld,exposure_length=%d ms,filename=%s).",
			      open_shutter,start_time.tv_sec,exposure_length_ms,filename);
#endif
	/* set shutter */
	if(open_shutter)
	{
#if LOGGING > 5
		CCD_Global_Log("ccd","ccd_exposure.c","CCD_Exposure_Expose",LOG_VERBOSITY_INTERMEDIATE,"ANDOR",
				"SetShutter(1,0,0,0).");
#endif
		andor_retval = SetShutter(1,0,0,0);
		if(andor_retval != DRV_SUCCESS)
		{
			Exposure_Error_Number = 1;
			sprintf(Exposure_Error_String,"CCD_Exposure_Expose: SetShutter() failed %s(%u).",
				CCD_Global_Andor_ErrorCode_To_String(andor_retval),andor_retval);
			return FALSE;
		}
	}
	else
	{
#if LOGGING > 5
		CCD_Global_Log("ccd","ccd_exposure.c","CCD_Exposure_Expose",LOG_VERBOSITY_INTERMEDIATE,"ANDOR",
				"SetShutter(1,2,0,0).");
#endif
		andor_retval = SetShutter(1,2,0,0);/* 2 means close */
		if(andor_retval != DRV_SUCCESS)
		{
			Exposure_Error_Number = 2;
			sprintf(Exposure_Error_String,"CCD_Exposure_Expose: SetShutter() failed %s(%u).",
				CCD_Global_Andor_ErrorCode_To_String(andor_retval),andor_retval);
			return FALSE;
		}
	}
	/* set exposure length */
	Exposure_Data.Exposure_Length = exposure_length_ms;
	/* set other parameters used for status reporting */
	Exposure_Data.Accumulation = -1;
	Exposure_Data.Series = -1;
#if LOGGING > 5
	CCD_Global_Log_Format("ccd","ccd_exposure.c","CCD_Exposure_Expose",LOG_VERBOSITY_INTERMEDIATE,"ANDOR",
			"SetExposureTime(%.2f).",((float)exposure_length_ms)/1000.0f);
#endif
	andor_retval = SetExposureTime(((float)exposure_length_ms)/1000.0f);/* in seconds */
	if(andor_retval != DRV_SUCCESS)
	{
		Exposure_Error_Number = 3;
		sprintf(Exposure_Error_String,"CCD_Exposure_Expose: SetExposureTime(%f) failed %s(%u).",
			(((float)exposure_length_ms)/1000.0f),CCD_Global_Andor_ErrorCode_To_String(andor_retval),
			andor_retval);
		return FALSE;
	}
	/* allocate image */
#if LOGGING > 5
	CCD_Global_Log_Format("ccd","ccd_exposure.c","CCD_Exposure_Expose",LOG_VERBOSITY_VERBOSE,NULL,
			      "Allocating image buffer.");
#endif
	if(!CCD_Setup_Allocate_Image_Buffer(((void **)&image_data),&image_data_length))
	{
		Exposure_Error_Number = 4;
		sprintf(Exposure_Error_String,"CCD_Exposure_Expose:CCD_Setup_Allocate_Image_Buffer failed.");
		return FALSE;
	}
#if LOGGING > 5
	CCD_Global_Log_Format("ccd","ccd_exposure.c","CCD_Exposure_Expose",LOG_VERBOSITY_VERBOSE,NULL,
			      "Allocated image buffer of length %ld.",image_data_length);
#endif
	/* reset abort */
	Exposure_Data.Abort = FALSE;
	/* wait for start_time, if applicable */
	if(!Exposure_Wait_For_Start_Time(start_time))
		return FALSE;
	Exposure_Data.Elapsed_Exposure_Time = 0;
	Exposure_Data.Exposure_Status = CCD_EXPOSURE_STATUS_EXPOSE;
#if LOGGING > 5
	CCD_Global_Log("ccd","ccd_exposure.c","CCD_Exposure_Expose",LOG_VERBOSITY_INTERMEDIATE,"ANDOR",
			"StartAcquisition().");
#endif
	andor_retval = StartAcquisition();
	if(andor_retval != DRV_SUCCESS)
	{
		Exposure_Data.Exposure_Status = CCD_EXPOSURE_STATUS_NONE;
		Exposure_Error_Number = 5;
		sprintf(Exposure_Error_String,"CCD_Exposure_Expose: StartAcquisition() failed %s(%u).",
			CCD_Global_Andor_ErrorCode_To_String(andor_retval),andor_retval);
		return FALSE;
	}
	/* wait until acquisition complete */
	acquisition_counter = 0;
	do
	{
		/* sleep a (very small (configurable)) bit */
		sleep_time.tv_sec = 0;
		sleep_time.tv_nsec = Exposure_Data.Exposure_Loop_Pause_Length*CCD_GLOBAL_ONE_MILLISECOND_NS;
		nanosleep(&sleep_time,NULL);
		/* get the status */
		andor_retval = GetStatus(&exposure_status);
		if(andor_retval != DRV_SUCCESS)
		{
			Exposure_Data.Exposure_Status = CCD_EXPOSURE_STATUS_NONE;
			Exposure_Data.Elapsed_Exposure_Time = 0;
			Exposure_Error_Number = 6;
			sprintf(Exposure_Error_String,"CCD_Exposure_Expose: GetStatus() failed %s(%u).",
				CCD_Global_Andor_ErrorCode_To_String(andor_retval),andor_retval);
			return FALSE;
		}
#if LOGGING > 3
		if((acquisition_counter%100)==0)
		{
			CCD_Global_Log_Format("ccd","ccd_exposure.c","CCD_Exposure_Expose",
					       LOG_VERBOSITY_VERBOSE,NULL,
					       "Current Acquisition Status after %d loops is %s(%u).",
					       acquisition_counter,
					       CCD_Global_Andor_ErrorCode_To_String(exposure_status),
					       exposure_status);
		}
#endif
		acquisition_counter++;
		/* check - have we been aborted? */
		if(Exposure_Data.Abort)
	        {
			CCD_Global_Log_Format("ccd","ccd_exposure.c","CCD_Exposure_Expose",
					       LOG_VERBOSITY_VERBOSE,"ANDOR",
					       "Abort detected, attempting Andor AbortAcquisition.");
			andor_retval = AbortAcquisition();
			CCD_Global_Log_Format("ccd","ccd_exposure.c","CCD_Exposure_Expose",
					       LOG_VERBOSITY_VERBOSE,"ANDOR",
					       "AbortAcquisition() return %u.",andor_retval);
			Exposure_Data.Exposure_Status = CCD_EXPOSURE_STATUS_NONE;
			Exposure_Data.Elapsed_Exposure_Time = 0;
			Exposure_Error_Number = 7;
			sprintf(Exposure_Error_String,"CCD_Exposure_Expose:Aborted.");
			return FALSE;
		}
		/* timeout */
#ifdef _POSIX_TIMERS
		clock_gettime(CLOCK_REALTIME,&current_time);
#else
		gettimeofday(&gtod_current_time,NULL);
		current_time.tv_sec = gtod_current_time.tv_sec;
		current_time.tv_nsec = gtod_current_time.tv_usec*CCD_GLOBAL_ONE_MICROSECOND_NS;
#endif
#if LOGGING > 3
		if((acquisition_counter%100)==0)
		{
			CCD_Global_Log_Format("ccd","ccd_exposure.c","CCD_Exposure_Expose",
					       LOG_VERBOSITY_VERBOSE,NULL,
					       "Start_Time = %s, current_time = %s, fdifftime = %.2f s,"
					       "difftime = %.2f s, exposure length = %d ms, timeout length = %.2f s, "
					       "is a timeout = %d.",ctime(&(Exposure_Data.Start_Time.tv_sec)),
					       ctime(&(current_time.tv_sec)),
					       fdifftime(current_time,Exposure_Data.Start_Time),
					       difftime(current_time.tv_sec,Exposure_Data.Start_Time.tv_sec),
					       Exposure_Data.Exposure_Length,
					       ((((double)Exposure_Data.Exposure_Length)/1000.0)+
						EXPOSURE_TIMEOUT_SECS),
					       fdifftime(current_time,Exposure_Data.Start_Time) > 
					       ((((double)Exposure_Data.Exposure_Length)/1000.0)+
						EXPOSURE_TIMEOUT_SECS));
		}
#endif
		Exposure_Data.Elapsed_Exposure_Time = (int)(fdifftime(current_time,Exposure_Data.Start_Time)*
							    (double)CCD_GLOBAL_ONE_SECOND_MS);
		if(fdifftime(current_time,Exposure_Data.Start_Time) >
		   ((((double)Exposure_Data.Exposure_Length)/1000.0)+EXPOSURE_TIMEOUT_SECS))
		{
			CCD_Global_Log_Format("ccd","ccd_exposure.c","CCD_Exposure_Expose",
					       LOG_VERBOSITY_VERBOSE,"ANDOR",
					       "Timeout detected, attempting Andor AbortAcquisition.");
			andor_retval = AbortAcquisition();
			CCD_Global_Log_Format("ccd","ccd_exposure.c","CCD_Exposure_Expose",
					       LOG_VERBOSITY_VERBOSE,"ANDOR",
					       "AbortAcquisition() return %u.",andor_retval);
			Exposure_Data.Elapsed_Exposure_Time = 0;
			Exposure_Data.Exposure_Status = CCD_EXPOSURE_STATUS_NONE;
			Exposure_Error_Number = 8;
			sprintf(Exposure_Error_String,"CCD_Exposure_Expose:"
				"Timeout (Andor library stuck in DRV_ACQUIRING).");
			CCD_Global_Log_Format("ccd","ccd_exposure.c","CCD_Exposure_Expose",
					       LOG_VERBOSITY_VERY_TERSE,NULL,
					       "Timeout (Andor library stuck in DRV_ACQUIRING).");
			return FALSE;
		}
	}
	while(exposure_status==DRV_ACQUIRING);
#if LOGGING > 3
	CCD_Global_Log_Format("ccd","ccd_exposure.c","CCD_Exposure_Expose",LOG_VERBOSITY_VERBOSE,NULL,
			       "Acquisition Status after %d loops is %s(%u).",acquisition_counter,
			       CCD_Global_Andor_ErrorCode_To_String(exposure_status),exposure_status);
#endif
	/* diddly check exposure_status is correct (DRV_IDLE?) */
	/* get data */
#if LOGGING > 3
	CCD_Global_Log_Format("ccd","ccd_exposure.c","CCD_Exposure_Expose",LOG_VERBOSITY_VERBOSE,"ANDOR",
			       "Calling GetAcquiredData16(%p,%lu).",image_data,image_data_length);
#endif
	Exposure_Data.Elapsed_Exposure_Time = 0;
	Exposure_Data.Exposure_Status = CCD_EXPOSURE_STATUS_READOUT;
	andor_retval = GetAcquiredData16((unsigned short*)image_data,image_data_length);
	if(andor_retval != DRV_SUCCESS)
	{
		Exposure_Data.Exposure_Status = CCD_EXPOSURE_STATUS_NONE;
		Exposure_Error_Number = 9;
		sprintf(Exposure_Error_String,"CCD_Exposure_Expose: GetAcquiredData16(%p,%lu) failed %s(%u).",
			(void*)image_data,(long unsigned int)image_data_length,
			CCD_Global_Andor_ErrorCode_To_String(andor_retval),andor_retval);
		return FALSE;
	}
	Exposure_Data.Exposure_Status = CCD_EXPOSURE_STATUS_NONE;
	andor_retval = GetAcquisitionProgress(&(accumulation),&(series));
	if(andor_retval == DRV_SUCCESS)
	{
		Exposure_Data.Accumulation = accumulation;
		Exposure_Data.Series = series;
	}
	if(!Exposure_Save(image_data,image_data_length,header,filename))
	{
		return FALSE;
	}
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_exposure.c","CCD_Exposure_Expose",LOG_VERBOSITY_INTERMEDIATE,NULL,
			"CCD_Exposure_Expose finished.");
#endif
	return TRUE;
}

/**
 * Take a bias frame.
 * @param header A list of FITS header keywords and values to be written into the FITS headers of the resulting
 *        image data.
 * @param filename A NULL terminated string containing the filename to put the FITS image into.
 * @return Returns TRUE if the exposure succeeds and the data read out into the buffer, returns FALSE if an error
 *	occurs or the exposure is aborted.
 * @see #CCD_Exposure_Expose
 */
int CCD_Exposure_Bias(struct Fits_Header_Struct header,char *filename)
{
	struct timespec start_time = {0,0};
	int retval;

	Exposure_Error_Number = 0;
	retval = CCD_Exposure_Expose(FALSE,start_time,0,header,filename);
	return retval;
}

/**
 * This routine aborts an exposure currently underway, whether it is reading out or not.
 * @return Returns TRUE if the abort succeeds  returns FALSE if an error occurs.
 * @see #Exposure_Data
 */
int CCD_Exposure_Abort(void)
{
	Exposure_Error_Number = 0;
	Exposure_Data.Abort = TRUE;
	return TRUE;
}

/**
 * Get the current exposure status.
 * @return The current exposure status.
 * @see #CCD_EXPOSURE_STATUS
 * @see #Exposure_Data
 */
enum CCD_EXPOSURE_STATUS CCD_Exposure_Status_Get(void)
{
	return Exposure_Data.Exposure_Status;
}

/**
 * Get the current Andor accumulation.
 * @return The current Andor accumulation.
 * @see #Exposure_Data
 */
int CCD_Exposure_Accumulation_Get(void)
{
	return Exposure_Data.Accumulation;
}

/**
 * Get the current Andor series.
 * @return The current Andor series.
 * @see #Exposure_Data
 */
int CCD_Exposure_Series_Get(void)
{
	return Exposure_Data.Series;
}

/**
 * Get the current exposure length.
 * @return The current exposure length, in milliseconds. 
 * @see #Exposure_Data
 */
int CCD_Exposure_Length_Get(void)
{
	return Exposure_Data.Exposure_Length;
}

/**
 * This routine gets the time stamp for the start of the exposure.
 * @param timespec The time stamp for the start of the exposure.
 * @return Returns TRUE on success, and FALSE if an error occurs.
 * @see #Exposure_Data
 */
struct timespec CCD_Exposure_Start_Time_Get(void)
{
	return Exposure_Data.Start_Time;
}

/**
 * Get a fake measure of the time elapsed since the start of the exposure. In milliseconds.
 * @return A fake measure of the time elapsed since the start of the exposure. In milliseconds.
 * @see #Exposure_Data
 */
int CCD_Exposure_Elapsed_Exposure_Time_Get(void)
{
	return Exposure_Data.Elapsed_Exposure_Time;
}

/**
 * Convert the exposure status to a string. This code should match the string->number convertion in
 * the Java ngat.sprat.command.StatusExposureStatusCommand command, getExposureStatus method.
 * @param status The exposure status to convert.
 * @return A string.
 * @see #CCD_EXPOSURE_STATUS
 */
char* CCD_Exposure_Status_To_String(enum CCD_EXPOSURE_STATUS status)
{
	switch(status)
	{
		case CCD_EXPOSURE_STATUS_NONE:
			return "NONE";
		case CCD_EXPOSURE_STATUS_WAIT_START:
			return "WAIT_START";
		case CCD_EXPOSURE_STATUS_CLEAR:
			return "CLEAR";
		case CCD_EXPOSURE_STATUS_EXPOSE:
			return "EXPOSE";
		case CCD_EXPOSURE_STATUS_PRE_READOUT:
			return "PRE_READOUT";
		case CCD_EXPOSURE_STATUS_READOUT:
			return "READOUT";
		case CCD_EXPOSURE_STATUS_POST_READOUT:
			return "POST_READOUT";
		default:
			return "UNKNOWN";
	}
	return "UNKNOWN";
}

/**
 * Flip the image data in the X direction.
 * @param ncols The number of columns on the CCD.
 * @param nrows The number of rows on the CCD.
 * @param exposure_data The image data received from the CCD. The data in this array is flipped in the X direction.
 * @return If everything was successful TRUE is returned, otherwise FALSE is returned.
 */
int CCD_Exposure_Flip_X(int ncols,int nrows,unsigned short *exposure_data)
{
	int x,y;
	unsigned short int tempval;

	/* for each row */
	for(y=0;y<nrows;y++)
	{
		/* for the first half of the columns.
		** Note the middle column will be missed, this is OK as it
		** does not need to be flipped if it is in the middle */
		for(x=0;x<(ncols/2);x++)
		{
			/* Copy exposure_data[x,y] to tempval */
			tempval = *(exposure_data+(y*ncols)+x);
			/* Copy exposure_data[ncols-(x+1),y] to exposure_data[x,y] */
			*(exposure_data+(y*ncols)+x) = *(exposure_data+(y*ncols)+(ncols-(x+1)));
			/* Copy tempval = exposure_data[ncols-(x+1),y] */
			*(exposure_data+(y*ncols)+(ncols-(x+1))) = tempval;
		}
	}
	return TRUE;
}

/**
 * Flip the image data in the Y direction.
 * @param ncols The number of columns on the CCD.
 * @param nrows The number of rows on the CCD.
 * @param exposure_data The image data received from the CCD. The data in this array is flipped in the Y direction.
 * @return If everything was successful TRUE is returned, otherwise FALSE is returned.
 */
int CCD_Exposure_Flip_Y(int ncols,int nrows,unsigned short *exposure_data)
{
	int x,y;
	unsigned short int tempval;

	/* for the first half of the rows.
	** Note the middle row will be missed, this is OK as it
	** does not need to be flipped if it is in the middle */
	for(y=0;y<(nrows/2);y++)
	{
		/* for each column */
		for(x=0;x<ncols;x++)
		{
			/* Copy exposure_data[x,y] to tempval */
			tempval = *(exposure_data+(y*ncols)+x);
			/* Copy exposure_data[x,nrows-(y+1)] to exposure_data[x,y] */
			*(exposure_data+(y*ncols)+x) = *(exposure_data+(((nrows-(y+1))*ncols)+x));
			/* Copy tempval = exposure_data[x,nrows-(y+1)] */
			*(exposure_data+(((nrows-(y+1))*ncols)+x)) = tempval;
		}
	}
	return TRUE;
}

/**
 * Routine to convert a timespec structure to a DATE sytle string to put into a FITS header.
 * This uses gmtime and strftime to format the string. The resultant string is of the form:
 * <b>CCYY-MM-DD</b>, which is equivalent to %Y-%m-%d passed to strftime.
 * @param time The time to convert.
 * @param time_string The string to put the time representation in. The string must be at least
 * 	12 characters long.
 */
void CCD_Exposure_TimeSpec_To_Date_String(struct timespec time,char *time_string)
{
	struct tm *tm_time = NULL;

	tm_time = gmtime(&(time.tv_sec));
	strftime(time_string,12,"%Y-%m-%d",tm_time);
}

/**
 * Routine to convert a timespec structure to a DATE-OBS sytle string to put into a FITS header.
 * This uses gmtime and strftime to format most of the string, and tags the milliseconds on the end.
 * The resultant form of the string is <b>CCYY-MM-DDTHH:MM:SS.sss</b>.
 * @param time The time to convert.
 * @param time_string The string to put the time representation in. The string must be at least
 * 	24 characters long.
 * @see ccd_global.html#CCD_GLOBAL_ONE_MILLISECOND_NS
 */
void CCD_Exposure_TimeSpec_To_Date_Obs_String(struct timespec time,char *time_string)
{
	struct tm *tm_time = NULL;
	char buff[32];
	int milliseconds;

	tm_time = gmtime(&(time.tv_sec));
	strftime(buff,32,"%Y-%m-%dT%H:%M:%S.",tm_time);
	milliseconds = (((double)time.tv_nsec)/((double)CCD_GLOBAL_ONE_MILLISECOND_NS));
	sprintf(time_string,"%s%03d",buff,milliseconds);
}

/**
 * Routine to convert a timespec structure to a UTSTART sytle string to put into a FITS header.
 * This uses gmtime and strftime to format most of the string, and tags the milliseconds on the end.
 * @param time The time to convert.
 * @param time_string The string to put the time representation in. The string must be at least
 * 	14 characters long.
 * @see ccd_global.html#CCD_GLOBAL_ONE_MILLISECOND_NS
 */
void CCD_Exposure_TimeSpec_To_UtStart_String(struct timespec time,char *time_string)
{
	struct tm *tm_time = NULL;
	char buff[16];
	int milliseconds;

	tm_time = gmtime(&(time.tv_sec));
	strftime(buff,16,"%H:%M:%S.",tm_time);
	milliseconds = (((double)time.tv_nsec)/((double)CCD_GLOBAL_ONE_MILLISECOND_NS));
	sprintf(time_string,"%s%03d",buff,milliseconds);
}

/**
 * Routine to convert a timespec structure to a Modified Julian Date (decimal days) to put into a FITS header.
 * This uses NGAT_Astro_Timespec_To_MJD to get the MJD.
 * <p>This routine is still wrong for last second of the leap day, as gmtime will return 1st second of the next day.
 * Also note the passed in leap_second_correction should change at midnight, when the leap second occurs.
 * None of this should really matter, 1 second will not affect the MJD for several decimal places.
 * @param time The time to convert.
 * @param leap_second_correction A number representing whether a leap second will occur. This is normally zero,
 * 	which means no leap second will occur. It can be 1, which means the last minute of the day has 61 seconds,
 *	i.e. there are 86401 seconds in the day. It can be -1,which means the last minute of the day has 59 seconds,
 *	i.e. there are 86399 seconds in the day.
 * @param mjd The address of a double to store the calculated MJD.
 * @return The routine returns TRUE if it succeeded, FALSE if it fails. 
 *         slaCldj and NGAT_Astro_Timespec_To_MJD can fail.
 */
int CCD_Exposure_TimeSpec_To_Mjd(struct timespec time,int leap_second_correction,double *mjd)
{
	int retval;

	retval = NGAT_Astro_Timespec_To_MJD(time,leap_second_correction,mjd);
	if(retval == FALSE)
	{
		Exposure_Error_Number = 10;
		sprintf(Exposure_Error_String,"CCD_Exposure_TimeSpec_To_Mjd:NGAT_Astro_Timespec_To_MJD failed.\n");
		/* concatenate NGAT Astro library error onto Exposure_Error_String */
		NGAT_Astro_Error_String(Exposure_Error_String+strlen(Exposure_Error_String));
		return FALSE;
	}
	return TRUE;
}

/**
 * Get the current value of ccd_exposures's error number.
 * @return The current value of ccd_exposure's error number.
 * @see #Exposure_Error_Number
 */
int CCD_Exposure_Get_Error_Number(void)
{
	return Exposure_Error_Number;
}

/**
 * The error routine that reports any errors occuring in ccd_exposure in a standard way.
 * @see ccd_global.html#CCD_Global_Get_Current_Time_String
 * @see #Exposure_Error_Number
 * @see #Exposure_Error_String
 */
void CCD_Exposure_Error(void)
{
	char time_string[32];

	CCD_Global_Get_Current_Time_String(time_string,32);
	/* if the error number is zero an error message has not been set up
	** This is in itself an error as we should not be calling this routine
	** without there being an error to display */
	if(Exposure_Error_Number == 0)
		sprintf(Exposure_Error_String,"Logic Error:No Error defined");
	fprintf(stderr,"%s CCD_Exposure:Error(%d) : %s\n",time_string,Exposure_Error_Number,Exposure_Error_String);
}

/**
 * The error routine that reports any errors occuring in ccd_exposure in a standard way. This routine places the
 * generated error string at the end of a passed in string argument.
 * @param error_string A string to put the generated error in. This string should be initialised before
 * being passed to this routine. The routine will try to concatenate it's error string onto the end
 * of any string already in existance.
 * @see ccd_global.html#CCD_Global_Get_Current_Time_String
 * @see #Exposure_Error_Number
 * @see #Exposure_Error_String
 */
void CCD_Exposure_Error_String(char *error_string)
{
	char time_string[32];

	CCD_Global_Get_Current_Time_String(time_string,32);
	/* if the error number is zero an error message has not been set up
	** This is in itself an error as we should not be calling this routine
	** without there being an error to display */
	if(Exposure_Error_Number == 0)
		sprintf(Exposure_Error_String,"Logic Error:No Error defined");
	sprintf(error_string+strlen(error_string),"%s CCD_Exposure:Error(%d) : %s\n",time_string,
		Exposure_Error_Number,Exposure_Error_String);
}

/* ----------------------------------------------------------------------------
** 		internal functions 
** ---------------------------------------------------------------------------- */
/**
 * Routine to wait until the specified start time.
 * @param start_time The start time.
 * @return The routine returns TRUE on success and FALSE for failure.
 * @see #Exposure_Data
 * @see #CCD_EXPOSURE_STATUS
 * @see ccd_global.html#CCD_GLOBAL_ONE_SECOND_NS
 * @see ccd_global.html#CCD_GLOBAL_ONE_MICROSECOND_NS
 */
static int Exposure_Wait_For_Start_Time(struct timespec start_time)
{
	struct timespec sleep_time,current_time;
#ifndef _POSIX_TIMERS
	struct timeval gtod_current_time;
#endif
	long remaining_sec,remaining_ns;
	int done;

	if(start_time.tv_sec > 0)
	{
		Exposure_Data.Exposure_Status = CCD_EXPOSURE_STATUS_WAIT_START;
		done = FALSE;
		while(done == FALSE)
		{
#ifdef _POSIX_TIMERS
			clock_gettime(CLOCK_REALTIME,&current_time);
#else
			gettimeofday(&gtod_current_time,NULL);
			current_time.tv_sec = gtod_current_time.tv_sec;
			current_time.tv_nsec = gtod_current_time.tv_usec*CCD_GLOBAL_ONE_MICROSECOND_NS;
#endif
			remaining_sec = start_time.tv_sec - current_time.tv_sec;
#if LOGGING > 4
			CCD_Global_Log_Format("ccd","ccd_exposure.c","Exposure_Wait_For_Start_Time",
					      LOG_VERBOSITY_INTERMEDIATE,"TIMING","Exposure_Wait_For_Start_Time:"
					      "Waiting for exposure start time (%ld,%ld) = %ld secs.",
					      current_time.tv_sec,start_time.tv_sec,remaining_sec);
#endif
		/* if we have over a second before wait_time, sleep for a second. */
			if(remaining_sec > 1)
			{
				sleep_time.tv_sec = 1;
				sleep_time.tv_nsec = 0;
				nanosleep(&sleep_time,NULL);
			}
			else if(remaining_sec > -1)
			{
				remaining_ns = (start_time.tv_nsec - current_time.tv_nsec);
				if(remaining_ns < 0)
				{
					remaining_sec--;
					remaining_ns += CCD_GLOBAL_ONE_SECOND_NS;
				}
				done = TRUE;
				if(remaining_sec > -1)
				{
					sleep_time.tv_sec = remaining_sec;
					sleep_time.tv_nsec = remaining_ns;
					nanosleep(&sleep_time,NULL);
				}
			}
			else
				done = TRUE;
		/* if an abort has occured, stop sleeping. The following command send will catch the abort. */
			if(Exposure_Data.Abort)
				done = TRUE;
		}/* end while */
	}/* end if */
	if(Exposure_Data.Abort)
	{
		Exposure_Error_Number = 11;
		sprintf(Exposure_Error_String,"Exposure_Wait_For_Start_Time:Operation was aborted.");
		return FALSE;
	}
/* switch status to exposing and store the actual time the exposure is going to start */
#ifdef _POSIX_TIMERS
	clock_gettime(CLOCK_REALTIME,&(Exposure_Data.Start_Time));
#else
	gettimeofday(&gtod_current_time,NULL);
	Exposure_Data.Start_Time.tv_sec = gtod_current_time.tv_sec;
	Exposure_Data.Start_Time.tv_nsec = gtod_current_time.tv_usec*CCD_GENERAL_ONE_MICROSECOND_NS;
#endif
	return TRUE;
}

/**
 * Debug routine to print out part of the readout buffer.
 * @param description A string containing a description of the buffer.
 * @param buffer The readout buffer.
 * @param buffer_length The length of the readout buffer in pixels.
 * @see ccd_global.html#CCD_Global_Log
 */
static void Exposure_Debug_Buffer(char *description,unsigned short *buffer,size_t buffer_length)
{
	char buff[1024];
	int i;

	strcpy(buff,description);
	strcat(buff," : ");
	for(i=0; i < 10; i++)
	{
		sprintf(buff+strlen(buff),"[%d] = %hu,",i,buffer[i]);
	}
	strcat(buff," ... ");
	for(i=(buffer_length-10); i < buffer_length; i++)
	{
		sprintf(buff+strlen(buff),"[%d] = %hu,",i,buffer[i]);
	}
#if LOGGING > 9
	CCD_Global_Log("ccd","ccd_exposure.c","Exposure_Debug_Buffer",LOG_VERBOSITY_INTERMEDIATE,NULL,buff);
#endif	
}

/**
 * Save the exposure to disk.
 * @param buffer Pointer to a previously allocated array of unsigned shorts containing the image pixel values.
 * @param buffer_length The length of the buffer in bytes.
 * @param header A list of FITS headers to wrire into the FITS image.
 * @param filename The name of the file to save the image into. If it does not exist, it is created.
 * @return Returns TRUE on success, and FALSE if an error occurs.
 * @see #Exposure_Error_Number
 * @see #Exposure_Error_String
 * @see #Exposure_Debug_Buffer
 * @see #CCD_Exposure_TimeSpec_To_Date_String
 * @see #CCD_Exposure_TimeSpec_To_Date_Obs_String
 * @see #CCD_Exposure_TimeSpec_To_UtStart_String
 * @see #CCD_Exposure_TimeSpec_To_Mjd
 * @see ccd_fits_filename.html#CCD_Fits_Filename_Lock
 * @see ccd_fits_filename.html#CCD_Fits_Filename_UnLock
 * @see ccd_global.html#CCD_Global_Log
 * @see ccd_global.html#CCD_Global_Log_Format
 * @see ccd_setup.html#CCD_Setup_Get_Bin_X
 * @see ccd_setup.html#CCD_Setup_Get_Bin_Y
 * @see ccd_setup.html#CCD_Setup_Get_NCols
 * @see ccd_setup.html#CCD_Setup_Get_NRows
 * @see ccd_setup.html#CCD_Setup_Is_Window
 * @see ccd_setup.html#CCD_Setup_Get_Horizontal_Start
 * @see ccd_setup.html#CCD_Setup_Get_Horizontal_End
 * @see ccd_setup.html#CCD_Setup_Get_Vertical_End
 * @see ccd_setup.html#CCD_Setup_Get_Detector_Pixel_Count_X
 * @see ccd_setup.html#CCD_Setup_Get_Detector_Pixel_Count_Y
 * @see #fexist
 */
static int Exposure_Save(void *buffer,size_t buffer_length,struct Fits_Header_Struct header,char *filename)
{
	enum CCD_TEMPERATURE_STATUS temperature_status;
	struct timespec temperature_time_stamp;
	fitsfile *fits_fp = NULL;
	char exposure_start_time_string[64];
	char buff[32]; /* fits_get_errstatus returns 30 chars max */
	long axes[2];
	int status = 0,retval,ivalue,ncols,nrows,bin_x,bin_y,xstart,xend,ystart,yend;
	int ccdxbin,is_window,detector_x_pixel_count,detector_y_pixel_count;
	double exposure_length,mjd,current_temperature,target_temperature;

#if LOGGING > 5
	CCD_Global_Log("ccd","ccd_exposure.c","CCD_Exposure_Save",LOG_VERBOSITY_INTERMEDIATE,NULL,"started.");
#endif
#if LOGGING > 5
	CCD_Global_Log_Format("ccd","ccd_exposure.c","CCD_Exposure_Save",LOG_VERBOSITY_INTERMEDIATE,NULL,
			       "Saving to '%s', buffer of length %ld .",filename,buffer_length);
#endif
	/* get dimensions */
	bin_x = CCD_Setup_Get_Bin_X();
	bin_y = CCD_Setup_Get_Bin_Y();
	if((bin_x < 1)||(bin_y < 1))
	{
		Exposure_Error_Number = 12;
		sprintf(Exposure_Error_String,"CCD_Exposure_Save: Binning out of range (x=%d,y=%d).",bin_x,bin_y);
		return FALSE;
	}
	ncols = CCD_Setup_Get_NCols()/bin_x;
	nrows = CCD_Setup_Get_NRows()/bin_y;
#if LOGGING > 5
	CCD_Global_Log_Format("ccd","ccd_exposure.c","CCD_Exposure_Save",LOG_VERBOSITY_INTERMEDIATE,NULL,
			       "Binned pixel dimensions (%dx%d), buffer length %ld.",bin_x,bin_y,buffer_length);
#endif
	if((ncols*nrows) != buffer_length)
	{
		Exposure_Error_Number = 13;
		sprintf(Exposure_Error_String,"CCD_Exposure_Save: Dimension mismatch: "
			"Binned dimensions (x=%d,y=%d) = %d do not equal buffer length %ld.",
			ncols,nrows,(ncols*nrows),(long int)buffer_length);
		return FALSE;
	}
#if LOGGING > 5
	CCD_Global_Log_Format("ccd","ccd_exposure.c","Exposure_Save",LOG_VERBOSITY_INTERMEDIATE,NULL,
			      "Locking FITS filename %s.",filename);
#endif
	if(!CCD_Fits_Filename_Lock(filename))
	{
		Exposure_Error_Number = 14;
		sprintf(Exposure_Error_String,"Exposure_Save:Failed to lock '%s'.",filename);
		return FALSE;				
	}
	/* check existence of FITS image and create or append as appropriate? */
	if(fexist(filename))
	{
		retval = fits_open_file(&fits_fp,filename,READWRITE,&status);
		if(retval)
		{
			fits_get_errstatus(status,buff);
			fits_report_error(stderr,status);
			CCD_Fits_Filename_UnLock(filename);
			Exposure_Error_Number = 15;
			sprintf(Exposure_Error_String,"CCD_Exposure_Save: File open failed(%s,%d,%s).",
				filename,status,buff);
			return FALSE;
		}
	}
	else
	{
		/* open file */
		if(fits_create_file(&fits_fp,filename,&status))
		{
			fits_get_errstatus(status,buff);
			fits_report_error(stderr,status);
			CCD_Fits_Filename_UnLock(filename);
			Exposure_Error_Number = 16;
			sprintf(Exposure_Error_String,"CCD_Exposure_Save: File create failed(%s,%d,%s).",
				filename,status,buff);
			return FALSE;
		}
		/* create image block */
		axes[0] = nrows;
		axes[1] = ncols;
		retval = fits_create_img(fits_fp,USHORT_IMG,2,axes,&status);
		if(retval)
		{
			fits_get_errstatus(status,buff);
			fits_report_error(stderr,status);
			fits_close_file(fits_fp,&status);
			CCD_Fits_Filename_UnLock(filename);
			Exposure_Error_Number = 17;
			sprintf(Exposure_Error_String,"CCD_Exposure_Save: Create image failed(%s,%d,%s).",
				filename,status,buff);
			return FALSE;
		}
	}
	/* debug whats in the buffer */
#if LOGGING > 9
	Exposure_Debug_Buffer("Exposure_Save",(unsigned short*)buffer,buffer_length);
#endif
	/* write the data */
	retval = fits_write_img(fits_fp,TUSHORT,1,ncols*nrows,buffer,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 18;
		sprintf(Exposure_Error_String,"CCD_Exposure_Save: File write image failed(%s,%d,%s).",
			filename,status,buff);
		return FALSE;
	}
	/* save FITS headers to filename */
	if(!CCD_Fits_Header_Write_To_Fits(header,fits_fp))
	{
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 19;
		sprintf(Exposure_Error_String,"Exposure_Save:CCD_Fits_Header_Write_To_Fits failed.");
		return FALSE;
	}
/* update DATE keyword */
	CCD_Exposure_TimeSpec_To_Date_String(Exposure_Data.Start_Time,exposure_start_time_string);
	retval = fits_update_key(fits_fp,TSTRING,"DATE",exposure_start_time_string,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 20;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating DATE failed(%s,%d,%s).",filename,status,buff);
		return FALSE;
	}
/* update DATE-OBS keyword */
	CCD_Exposure_TimeSpec_To_Date_Obs_String(Exposure_Data.Start_Time,exposure_start_time_string);
	retval = fits_update_key(fits_fp,TSTRING,"DATE-OBS",exposure_start_time_string,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 21;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating DATE-OBS failed(%s,%d,%s).",filename,
			status,buff);
		return FALSE;
	}
/* update UTSTART keyword */
	CCD_Exposure_TimeSpec_To_UtStart_String(Exposure_Data.Start_Time,exposure_start_time_string);
	retval = fits_update_key(fits_fp,TSTRING,"UTSTART",exposure_start_time_string,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 22;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating UTSTART failed(%s,%d,%s).",filename,
			status,buff);
		return FALSE;
	}
/* update MJD keyword */
/* note leap second correction not implemented yet (always FALSE). */
	if(!CCD_Exposure_TimeSpec_To_Mjd(Exposure_Data.Start_Time,FALSE,&mjd))
	{
		CCD_Fits_Filename_UnLock(filename);
		return FALSE;
	}
	retval = fits_update_key_fixdbl(fits_fp,"MJD",mjd,6,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 23;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating MJD failed(%.2f,%s,%d,%s).",mjd,filename,
			status,buff);
		return FALSE;
	}
	/* ccdxbin */
	ccdxbin = CCD_Setup_Get_Bin_X();
	retval = fits_update_key(fits_fp,TINT,"CCDXBIN",&ccdxbin,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 24;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating CCDXBIN failed(%s,%d,%d,%s).",filename,ccdxbin,
			status,buff);
		return FALSE;
	}
	/* ccdybin */
	ivalue = CCD_Setup_Get_Bin_Y();
	retval = fits_update_key(fits_fp,TINT,"CCDYBIN",&ivalue,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 25;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating CCDYBIN failed(%s,%d,%d,%s).",filename,ivalue,
			status,buff);
		return FALSE;
	}
	/* data for windowing keywords */
	is_window = CCD_Setup_Is_Window();
	xstart = CCD_Setup_Get_Horizontal_Start();
	xend = CCD_Setup_Get_Horizontal_End();
	ystart = CCD_Setup_Get_Vertical_Start();
	yend = CCD_Setup_Get_Vertical_End();
	detector_x_pixel_count = CCD_Setup_Get_Detector_Pixel_Count_X();
	detector_y_pixel_count = CCD_Setup_Get_Detector_Pixel_Count_Y();
	/* CCDWMODE */
	retval = fits_update_key(fits_fp,TLOGICAL,"CCDWMODE",&(is_window),NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 26;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating CCDWMODE failed(%s,%d,%d,%s).",filename,
			is_window,status,buff);
		return FALSE;
	}
	/* CCDWXOFF */
	retval = fits_update_key(fits_fp,TINT,"CCDWXOFF",&(xstart),NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 27;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating CCDWXOFF failed(%s,%d,%d,%s).",filename,xstart,
			status,buff);
		return FALSE;
	}
	/* CCDWYOFF */
	retval = fits_update_key(fits_fp,TINT,"CCDWYOFF",&(ystart),NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 28;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating CCDWYOFF failed(%s,%d,%d,%s).",filename,ystart,
			status,buff);
		return FALSE;
	}
	/* CCDWXSIZ */
	ivalue = (xend-xstart)+1;
	retval = fits_update_key(fits_fp,TINT,"CCDWXSIZ",&(ivalue),NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 29;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating CCDWXSIZ failed(%s,%d,%d,%s).",filename,ivalue,
			status,buff);
		return FALSE;
	}
	/* CCDWYSIZ */
	ivalue = (yend-ystart)+1;
	retval = fits_update_key(fits_fp,TINT,"CCDWYSIZ",&(ivalue),NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 30;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating CCDWYSIZ failed(%s,%d,%d,%s).",filename,ivalue,
			status,buff);
		return FALSE;
	}
	/* CCDXIMSI */
	retval = fits_update_key(fits_fp,TINT,"CCDXIMSI",&detector_x_pixel_count,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 31;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating CCDXIMSI failed(%s,%d,%d,%s).",filename,
			detector_x_pixel_count,status,buff);
		return FALSE;
	}
	/* CCDYIMSI */
	retval = fits_update_key(fits_fp,TINT,"CCDYIMSI",&detector_y_pixel_count,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 32;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating CCDYIMSI failed(%s,%d,%d,%s).",filename,
			detector_y_pixel_count,status,buff);
		return FALSE;
	}
	exposure_length = ((double)(Exposure_Data.Exposure_Length))/1000.0;
	retval = fits_update_key_fixdbl(fits_fp,"EXPTIME",exposure_length,6,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 33;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating exposure length failed(%.2f,%s,%d,%s).",
			exposure_length,filename,status,buff);
		return FALSE;
	}
	/* Andor Accumulation */
	ivalue = Exposure_Data.Accumulation;
	retval = fits_update_key(fits_fp,TINT,"ACCUM",&ivalue,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 34;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating ACCUM failed(%s,%d,%s).",filename,
			status,buff);
		return FALSE;
	}
	/* Andor Series */
	ivalue = Exposure_Data.Series;
	retval = fits_update_key(fits_fp,TINT,"SERIES",&ivalue,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 35;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating SERIES failed(%s,%d,%s).",filename,
			status,buff);
		return FALSE;
	}
	/* CCDSCALE */
	/* bin1 value configured in Java layer and passed into fits header list.
	** Should have been written to file in CCD_Fits_Header_Write_To_Fits.
	** So retrieve bin1 value using CFITSIO, mod by binning and update value */
	/* diddly how does this work for non-square binned spectral data? */
	/*
	if(ccdxbin != 1)
	{
		retval = fits_read_key(fits_fp,TDOUBLE,"CCDSCALE",&ccdscale,NULL,&status);
		if(retval)
		{
			fits_get_errstatus(status,buff);
			fits_report_error(stderr,status);
			fits_close_file(fits_fp,&status);
			CCD_Fits_Filename_UnLock(filename);
			Exposure_Error_Number = ;
			sprintf(Exposure_Error_String,"Exposure_Save: Retrieving ccdscale failed(%s,%d,%s).",
				filename,status,buff);
			return FALSE;
		}
	*/
		/* adjust for binning */
		/*ccdscale *= ccdxbin;*/ /* assume xbin and ybin are equal - according to CCD_Setup_Dimensions they are */
		/* write adjusted value back */
	/*
		retval = fits_update_key_fixdbl(fits_fp,"CCDSCALE",ccdscale,6,NULL,&status);
		if(retval)
		{
			fits_get_errstatus(status,buff);
			fits_report_error(stderr,status);
			fits_close_file(fits_fp,&status);
			CCD_Fits_Filename_UnLock(filename);
			Exposure_Error_Number = ;
			sprintf(Exposure_Error_String,"Exposure_Save: Updating ccdscale failed(%.2f,%s,%d,%s).",
				ccdscale,filename,status,buff);
			return FALSE;
		}
		}*//* end if ccdxbin != 1 */
	/* temperature FITS headers */
	if(!CCD_Temperature_Get_Cached_Temperature(&current_temperature,&temperature_status,
						   &temperature_time_stamp))
	{
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 36;
		sprintf(Exposure_Error_String,"Exposure_Save: CCD_Temperature_Get_Cached_Temperature failed.");
		return FALSE;
	}
	/* CCDATEMP */
	retval = fits_update_key_fixdbl(fits_fp,"CCDATEMP",current_temperature+CENTIGRADE_TO_KELVIN,6,
					"Cached temperature of CCD",&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 37;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating actual temperature failed(%s,%d,%s).",
			filename,status,buff);
		return FALSE;
	}
	/* TEMPSTAT */
	retval = fits_update_key(fits_fp,TSTRING,"TEMPSTAT",CCD_Temperature_Status_To_String(temperature_status),
				 "CCD Temperature status at cache point.",&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 38;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating temperature status failed(%s,%d,%s).",
			filename,status,buff);
		return FALSE;
	}
	/* TEMPDATE */
	CCD_Exposure_TimeSpec_To_Date_Obs_String(temperature_time_stamp,exposure_start_time_string);
	retval = fits_update_key(fits_fp,TSTRING,"TEMPDATE",exposure_start_time_string,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 39;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating TEMPDATE failed(%s,%d,%s).",filename,
			status,buff);
		return FALSE;
	}
	/* target temperature */
	if(!CCD_Temperature_Target_Temperature_Get(&target_temperature))
	{
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 40;
		sprintf(Exposure_Error_String,"Exposure_Save: CCD_Temperature_Target_Temperature_Get failed.");
		return FALSE;
	}
	/* CCDSTEMP */
	retval = fits_update_key_fixdbl(fits_fp,"CCDSTEMP",target_temperature+CENTIGRADE_TO_KELVIN,6,
					"Target temperature setpoint of CCD",&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 41;
		sprintf(Exposure_Error_String,"Exposure_Save: Updating target temperature CCDSTEMP failed(%s,%d,%s).",
			filename,status,buff);
		return FALSE;
	}
	/* ensure data we have written is in the actual data buffer, not CFITSIO's internal buffers */
	/* closing the file ensures this. */ 
	retval = fits_close_file(fits_fp,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fits_fp,&status);
		CCD_Fits_Filename_UnLock(filename);
		Exposure_Error_Number = 42;
		sprintf(Exposure_Error_String,"CCD_Exposure_Save: File close file failed(%s,%d,%s).",
			filename,status,buff);
		return FALSE;
	}
#if LOGGING > 5
	CCD_Global_Log("ccd","ccd_exposure.c","CCD_Exposure_Save",LOG_VERBOSITY_INTERMEDIATE,NULL,"finished.");
#endif
	return TRUE;
}

/**
 * Return whether the specified filename exists or not.
 * @param filename A string representing the filename to test.
 * @return The routine returns TRUE if the filename exists, and FALSE if it does not exist. 
 */
static int fexist(char *filename)
{
	FILE *fptr = NULL;

	fptr = fopen(filename,"r");
	if(fptr == NULL )
		return FALSE;
	fclose(fptr);
	return TRUE;
}

