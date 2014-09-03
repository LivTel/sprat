/* ccd_fits_filename.c
** CCD fits filename routines
** $HeadURL$
*/
/**
 * Routines to look after fits filename generation.
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
/**
 * Define this to enable scandir and alphasort in 'dirent.h', which are BSD 4.3 prototypes.
 */
#define _BSD_SOURCE 1

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifndef _POSIX_TIMERS
#include <sys/time.h>
#endif
#include <time.h>
#include <unistd.h>
#include <dirent.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#include "log_udp.h"
#include "ccd_fits_filename.h"
#include "ccd_global.h"

/* hash defines */

/* structure declarations */
/**
 * Data type holding local data about fits filenames associated with one camera. 
 * This consists of the following:
 * <dl>
 * <dt>Data_Dir</dt> <dd>Directory containing FITS images.</dd>
 * <dt>Instrument_Code</dt> <dd>Character at start of FITS filenames representing instrument.</dd>
 * <dt>Current_Date_Number</dt> <dd>Current date number of the last file produced, of the form yyyymmdd.</dd>
 * <dt>Current_Multrun_Number</dt> <dd>Current MULTRUN number.</dd>
 * <dt>Current_Run_Number</dt> <dd>Current Run number.</dd>
 * <dt>Current_Window_Number</dt> <dd>Current Window number.</dd>
 * </dl>
 * @see #CCD_GLOBAL_ERROR_STRING_LENGTH
 */
struct Fits_Filename_Struct
{
	char Data_Dir[CCD_GLOBAL_ERROR_STRING_LENGTH];
	char Instrument_Code;
	int Current_Date_Number;
	int Current_Multrun_Number;
	int Current_Run_Number;
	int Current_Window_Number;
};

/* internal data */
/**
 * Revision Control System identifier.
 */
static char rcsid[] = "$Id$";
/**
 * Variable holding error code of last operation performed by the fits filename routines.
 */
static int Fits_Filename_Error_Number = 0;
/**
 * Local variable holding description of the last error that occured.
 */
static char Fits_Filename_Error_String[CCD_GLOBAL_ERROR_STRING_LENGTH] = "";
/**
 * Fits filename data.
 * @see #Fits_Filename_Struct
 * @see #DEFAULT_INSTRUMENT_CODE
 */
static struct Fits_Filename_Struct Fits_Filename_Data = 
{
	"",CCD_FITS_FILENAME_DEFAULT_INSTRUMENT_CODE,0,0,0,0
};

/* internal functions */
static int Fits_Filename_Get_Date_Number(int *date_number);
static int Fits_Filename_File_Select(const struct dirent *entry);
static int Fits_Filename_Lock_Filename_Get(char *filename,char *lock_filename);
static int fexist(char *filename);

/* ----------------------------------------------------------------------------
** 		external functions 
** ---------------------------------------------------------------------------- */
/**
 * Initialise FITS filename data, using the given data directory and the current (astronomical) day of year.
 * Retrieves current FITS images in directory, find the one with the highest multrun number, and sets
 * the current multrun number to it.
 * @param instrument_code A character describing which instrument code to associate with this camera, which appears in
 *        the resulting FITS filenames..
 * @param data_dir A string containing the directory name containing FITS images.
 * @return Returns TRUE if the routine succeeds and returns FALSE if an error occurs.
 * @see #Fits_Filename_Data
 * @see #Fits_Filename_File_Select
 */
int CCD_Fits_Filename_Initialise(char instrument_code,char *data_dir)
{
	struct dirent **name_list = NULL;
	int name_list_count,i,retval,date_number,multrun_number,fully_parsed;
	char *chptr = NULL;
	char inst_code[5] = "";
	char exposure_type[5] = "";
	char date_string[17] = "";
	char multrun_string[9] = "";
	char run_string[9] = "";
	char window_string[5] = "";
	char pipeline_string[5] = "";

#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_filename.c","CCD_Fits_Filename_Initialise",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","Started.");
#endif
	if(data_dir == NULL)
	{
		Fits_Filename_Error_Number = 1;
		sprintf(Fits_Filename_Error_String,"CCD_Fits_Filename_Initialise:data_dir was NULL.");
		return FALSE;
	}
	if(strlen(data_dir) > (CCD_GLOBAL_ERROR_STRING_LENGTH-1))
	{
		Fits_Filename_Error_Number = 2;
		sprintf(Fits_Filename_Error_String,"CCD_Fits_Filename_Initialise:data_dir was too long(%d).",
			strlen(data_dir));
		return FALSE;

	}
	/* instrument code */
	Fits_Filename_Data.Instrument_Code = instrument_code;
	/* setup data_dir */
	strcpy(Fits_Filename_Data.Data_Dir,data_dir);
#if LOGGING > 5
	CCD_Global_Log_Format("ccd","ccd_fits_filename.c","CCD_Fits_Filename_Initialise",
			      LOG_VERBOSITY_VERY_VERBOSE,"FITS","Data Dir set to %s.",
			      Fits_Filename_Data.Data_Dir);
#endif
	if(!Fits_Filename_Get_Date_Number(&(Fits_Filename_Data.Current_Date_Number)))
		return FALSE;
#if LOGGING > 5
	CCD_Global_Log_Format("ccd","ccd_fits_filename.c","CCD_Fits_Filename_Initialise",
			      LOG_VERBOSITY_VERY_VERBOSE,"FITS","Current Date Number is %d.",
			      Fits_Filename_Data.Current_Date_Number);
#endif
	Fits_Filename_Data.Current_Multrun_Number = 0;
	Fits_Filename_Data.Current_Run_Number = 0;
	name_list_count = scandir(Fits_Filename_Data.Data_Dir,&name_list,
				  Fits_Filename_File_Select,alphasort);
	for(i=0; i< name_list_count;i++)
	{
#if LOGGING > 9
		CCD_Global_Log_Format("ccd","ccd_fits_filename.c","CCD_Fits_Filename_Initialise",
				      LOG_VERBOSITY_VERY_VERBOSE,"FITS","Filename %d is %s.",i,name_list[i]->d_name);
#endif
		fully_parsed = FALSE;
		chptr = strtok(name_list[i]->d_name,"_");
		if(chptr != NULL)
		{
			strncpy(inst_code,chptr,4);
			inst_code[4] = '\0';
			chptr = strtok(NULL,"_");
			if(chptr != NULL)
			{
				strncpy(exposure_type,chptr,4);
				exposure_type[4] = '\0';
				chptr = strtok(NULL,"_");
				if(chptr != NULL)
				{
					strncpy(date_string,chptr,16);
					date_string[16] = '\0';
					chptr = strtok(NULL,"_");
					if(chptr != NULL)
					{
						strncpy(multrun_string,chptr,8);
						multrun_string[8] = '\0';
						chptr = strtok(NULL,"_");
						if(chptr != NULL)
						{
							strncpy(run_string,chptr,8);
							run_string[8] = '\0';
							chptr = strtok(NULL,"_");
							if(chptr != NULL)
							{
								strncpy(window_string,chptr,4);
								window_string[4] = '\0';
								chptr = strtok(NULL,".");
								if(chptr != NULL)
								{
									strncpy(pipeline_string,chptr,4);
									pipeline_string[4] = '\0';
									fully_parsed = TRUE;
								}
							}
						}
					}
				}
			}
		}
		if(fully_parsed)
		{
#if LOGGING > 9
			CCD_Global_Log_Format("ccd","ccd_fits_filename.c","CCD_Fits_Filename_Initialise",
					      LOG_VERBOSITY_VERY_VERBOSE,"FITS","Filename %s parsed OK.",
					      name_list[i]->d_name);
#endif
			/* check filename is for the right instrument (camera_index) */
			if(inst_code[0] == Fits_Filename_Data.Instrument_Code)
			{
				retval = sscanf(date_string,"%d",&date_number);
#if LOGGING > 9
				CCD_Global_Log_Format("ccd","ccd_fits_filename.c","CCD_Fits_Filename_Initialise",
						      LOG_VERBOSITY_VERY_VERBOSE,"FITS",
						      "Filename %s has date number %d.",
						      name_list[i]->d_name,date_number);
#endif
				/* check filename has right date number */
				if((retval == 1)&&
				   (date_number == Fits_Filename_Data.Current_Date_Number))
				{
					retval = sscanf(multrun_string,"%d",&multrun_number);
#if LOGGING > 9
					CCD_Global_Log_Format("ccd","ccd_fits_filename.c",
							      "CCD_Fits_Filename_Initialise",
							      LOG_VERBOSITY_VERY_VERBOSE,"FITS",
							      "Filename %s has multrun number %d.",
							      name_list[i]->d_name,multrun_number);
#endif
					/* check if multrun number is highest yet found */
					if((retval == 1)&&
				       (multrun_number > Fits_Filename_Data.Current_Multrun_Number))
					{
						Fits_Filename_Data.Current_Multrun_Number = multrun_number;
#if LOGGING > 9
						CCD_Global_Log_Format("ccd","ccd_fits_filename.c",
								      "CCD_Fits_Filename_Initialise",
								      LOG_VERBOSITY_VERY_VERBOSE,"FITS",
								      "Current multrun number now %d.",
								      Fits_Filename_Data.Current_Multrun_Number);
#endif
					}/* end if multrun_number > Current_Multrun_Number */
				}/* end if filename has right date number */
			}/* end if instrument has right instrument code */
		}/* end if fully_parsed */
		else
		{
#if LOGGING > 9
			CCD_Global_Log_Format("ccd","ccd_fits_filename.c","CCD_Fits_Filename_Initialise",
					      LOG_VERBOSITY_VERY_VERBOSE,"FITS","Failed to parse filename %s: "
					      "inst_code = %s,exposure_type = %s,date_string = %s,"
					      "multrun_string = %s, run_string = %s, window_string = %s,"
					      "pipeline_string = %s.",name_list[i]->d_name,inst_code,
					      exposure_type,date_string,multrun_string,run_string,window_string,
					      pipeline_string);
#endif

		}
		free(name_list[i]);
	}
	free(name_list);
	Fits_Filename_Data.Current_Run_Number = 1;
	Fits_Filename_Data.Current_Window_Number = 1;
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_filename.c","CCD_Fits_Filename_Initialise",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","Finished.");
#endif
	return TRUE;
}

/**
 * Start a new Multrun. Increments Current Multrun number, unless date has changed since last multrun,
 * when the date is changed and multrun number set to 1. Current run and Window number reset to 1.
 * @return Returns TRUE if the routine succeeds and returns FALSE if an error occurs.
 * @see #Fits_Filename_Get_Date_Number
 * @see #Fits_Filename_Data
 */
int CCD_Fits_Filename_Next_Multrun(void)
{
	int date_number;

	if(!Fits_Filename_Get_Date_Number(&date_number))
		return FALSE;
	if(date_number != Fits_Filename_Data.Current_Date_Number)
	{
		if(!Fits_Filename_Get_Date_Number(&Fits_Filename_Data.Current_Date_Number))
			return FALSE;
		Fits_Filename_Data.Current_Multrun_Number = 0;/* incremented to 1 at end of routine */		
	}
	Fits_Filename_Data.Current_Multrun_Number++;
	/* this should get incremented to 1 before the filename is generated */
	Fits_Filename_Data.Current_Run_Number = 0; 
	Fits_Filename_Data.Current_Window_Number = 0;
	return TRUE;
}

/**
 * Increments Run within a Multrun. Increments current run number and resets window number.
 * @return Returns TRUE if the routine succeeds and returns FALSE if an error occurs.
 * @see #Fits_Filename_Data
 */
int CCD_Fits_Filename_Next_Run(void)
{
	Fits_Filename_Data.Current_Run_Number++;
	Fits_Filename_Data.Current_Window_Number = 0;
	return TRUE;
}

/**
 * Increments Window within a Run. Increments window number.
 * @return Returns TRUE if the routine succeeds and returns FALSE if an error occurs.
 * @see #Fits_Filename_Data
 */
int CCD_Fits_Filename_Next_Window(void)
{
	Fits_Filename_Data.Current_Window_Number++;
	return TRUE;
}

/**
 * Returns a filename based on the current filename data.
 * @param exposure_type What sort of exposure the filename will contain (exposure/bias/dark etc).
 * @param pipeline_flag Pipeline processing level.
 * @param filename Pointer to an array of characters filename_length long to store the filename.
 * @param filename_length The length of the filename array.
 * @return Returns TRUE if the routine succeeds and returns FALSE if an error occurs.
 * @see #Fits_Filename_Data
 * @see #CCD_FITS_FILENAME_IS_EXPOSURE_TYPE
 * @see #CCD_FITS_FILENAME_EXPOSURE_TYPE
 */
int CCD_Fits_Filename_Get_Filename(enum CCD_FITS_FILENAME_EXPOSURE_TYPE exposure_type,
				   enum CCD_FITS_FILENAME_PIPELINE_FLAG pipeline_flag,
				   char *filename,int filename_length)
{
	char tmp_buff[256];
	char exposure_type_string[8] = {'q','a','b','d','e','f','s','w'};

	if(filename == NULL)
	{
		Fits_Filename_Error_Number = 3;
		sprintf(Fits_Filename_Error_String,"CCD_Fits_Filename_Get_Filename:filename was NULL.");
		return FALSE;
	}
	if(!CCD_FITS_FILENAME_IS_EXPOSURE_TYPE(exposure_type))
	{
		Fits_Filename_Error_Number = 6;
		sprintf(Fits_Filename_Error_String,"CCD_Fits_Filename_Get_Filename:Illegal exposure type '%d'.",
			exposure_type);
		return FALSE;
	}
	if(!CCD_FITS_FILENAME_IS_PIPELINE_FLAG(pipeline_flag))
	{
		Fits_Filename_Error_Number = 7;
		sprintf(Fits_Filename_Error_String,"CCD_Fits_Filename_Get_Filename:Illegal pipeline flag '%d'.",
			pipeline_flag);
		return FALSE;
	}
	/* check data dir is not too long : 256 is length of tmp_buff, 37 is approx length of filename itself */
	if(strlen(Fits_Filename_Data.Data_Dir) > (256-37))
	{
		Fits_Filename_Error_Number = 8;
		sprintf(Fits_Filename_Error_String,"CCD_Fits_Filename_Get_Filename:Data Dir too long (%d).",
			strlen(Fits_Filename_Data.Data_Dir));
		return FALSE;
	}
	sprintf(tmp_buff,"%s/%c_%c_%d_%d_%d_%d_%d.fits",Fits_Filename_Data.Data_Dir,
		Fits_Filename_Data.Instrument_Code,exposure_type_string[exposure_type],
		Fits_Filename_Data.Current_Date_Number,
		Fits_Filename_Data.Current_Multrun_Number,
		Fits_Filename_Data.Current_Run_Number,
		Fits_Filename_Data.Current_Window_Number,pipeline_flag);
	if(strlen(tmp_buff) > (filename_length-1))
	{
		Fits_Filename_Error_Number = 4;
		sprintf(Fits_Filename_Error_String,"CCD_Fits_Filename_Get_Filename:"
			"Generated filename was too long(%d).",strlen(tmp_buff));
		return FALSE;
	}
	strcpy(filename,tmp_buff);
	return TRUE;
}

/**
 * Get the current multrun number.
 * @return The current multrun number. 
 * @see #Fits_Filename_Data
 */
int CCD_Fits_Filename_Multrun_Get(void)
{
	return Fits_Filename_Data.Current_Multrun_Number;
}

/**
 * Get the current run number.
 * @return The current run number. 
 * @see #Fits_Filename_Data
 */
int CCD_Fits_Filename_Run_Get(void)
{
	return Fits_Filename_Data.Current_Run_Number;
}

/**
 * Get the current window number.
 * @return The current window number.
 * @see #Fits_Filename_Data
 */
int CCD_Fits_Filename_Window_Get(void)
{
	return Fits_Filename_Data.Current_Window_Number;
}

/**
 * Lock the FITS file specified, by creating a '.lock' file based on it's filename.
 * This allows interaction with the data transfer processes, so the FITS image will not be
 * data transferred until the lock file is removed.
 * @param filename The filename of the '.fits' FITS filename.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see #Fits_Filename_Lock_Filename_Get
 * @see #CCD_GLOBAL_ERROR_STRING_LENGTH
 */
int CCD_Fits_Filename_Lock(char *filename)
{
	char lock_filename[CCD_GLOBAL_ERROR_STRING_LENGTH];
	int fd,open_errno;

	/* check arguments */
	if(filename == NULL)
	{
		Fits_Filename_Error_Number = 14;
		sprintf(Fits_Filename_Error_String,"CCD_Fits_Filename_Lock:filename was NULL.");
		return FALSE;
	}
	if(strlen(filename) >= CCD_GLOBAL_ERROR_STRING_LENGTH)
	{
		Fits_Filename_Error_Number = 17;
		sprintf(Fits_Filename_Error_String,"CCD_Fits_Filename_Lock:FITS filename was too long(%d).",
			strlen(filename));
		return FALSE;
	}
	/* get lock filename */
	if(!Fits_Filename_Lock_Filename_Get(filename,lock_filename))
		return FALSE;
#if LOGGING > 9
	CCD_Global_Log_Format("ccd","ccd_fits_filename.c","CCD_Fits_Filename_Lock",LOG_VERBOSITY_VERY_VERBOSE,
			      "FILELOCK","Creating lock file %s.",lock_filename);
#endif
	/* try to open lock file. */
	/* O_CREAT|O_WRONLY|O_EXCL : create file, O_EXCL means the call will fail if the file already exists. 
	** Note atomic creation probably fails on NFS systems. */
	fd = open((const char*)lock_filename,O_CREAT|O_WRONLY|O_EXCL);
	if(fd == -1)
	{
		open_errno = errno;
		Fits_Filename_Error_Number = 18;
		sprintf(Fits_Filename_Error_String,
			"CCD_Fits_Filename_Lock:Failed to create lock filename(%s):error %d.",
			lock_filename,open_errno);
		return FALSE;
	}
	/* close created file */
	close(fd);
#if LOGGING > 9
	CCD_Global_Log_Format("ccd","ccd_fits_filename.c","CCD_Fits_Filename_Lock",LOG_VERBOSITY_VERY_VERBOSE,
			      "FILELOCK","Lock file %s created.",lock_filename);
#endif
	return TRUE;
}

/**
 * UnLock the FITS file specified, by removing the previously created '.lock' file based on it's filename.
 * This allows interaction with the data transfer processes, so the FITS image will not be
 * data transferred until the lock file is removed.
 * @param filename The filename of the '.fits' FITS filename.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see #Fits_Filename_Lock_Filename_Get
 * @see #fexist
 */
int CCD_Fits_Filename_UnLock(char *filename)
{
	char lock_filename[CCD_GLOBAL_ERROR_STRING_LENGTH];
	int retval,remove_errno;

	/* check arguments */
	if(filename == NULL)
	{
		Fits_Filename_Error_Number = 19;
		sprintf(Fits_Filename_Error_String,"CCD_Fits_Filename_UnLock:filename was NULL.");
		return FALSE;
	}
	if(strlen(filename) >= CCD_GLOBAL_ERROR_STRING_LENGTH)
	{
		Fits_Filename_Error_Number = 20;
		sprintf(Fits_Filename_Error_String,"CCD_Fits_Filename_UnLock:FITS filename was too long(%d).",
			strlen(filename));
		return FALSE;
	}
	/* get lock filename */
	if(!Fits_Filename_Lock_Filename_Get(filename,lock_filename))
		return FALSE;
	/* check existence */
	if(fexist(lock_filename))
	{
#if LOGGING > 9
		CCD_Global_Log_Format("ccd","ccd_fits_filename.c","CCD_Fits_Filename_UnLock",
				      LOG_VERBOSITY_VERY_VERBOSE,"FILELOCK","Removing lock file %s.",lock_filename);
#endif
		/* remove lock file */
		retval = remove(lock_filename);
		if(retval == -1)
		{
			remove_errno = errno;
			Fits_Filename_Error_Number = 21;
			sprintf(Fits_Filename_Error_String,
				"CCD_Fits_Filename_UnLock:Failed to unlock filename '%s':(%d,%d).",
				lock_filename,retval,remove_errno);
			return FALSE;
		}
#if LOGGING > 9
		CCD_Global_Log_Format("ccd","ccd_fits_filename.c","CCD_Fits_Filename_UnLock",
				      LOG_VERBOSITY_VERY_VERBOSE,"FILELOCK","Lock file %s removed.",lock_filename);
#endif
	}
	return TRUE;
}

/**
 * Get the current value of ccd_fits_filename's error number.
 * @return The current value of ccd_fits_filename's error number.
 * @see #Fits_Filename_Error_Number
 */
int CCD_Fits_Filename_Get_Error_Number(void)
{
	return Fits_Filename_Error_Number;
}

/**
 * The error routine that reports any errors occuring in ccd_fits_filename in a standard way.
 * @see ccd_global.html#CCD_Global_Get_Current_Time_String
 * @see #Fits_Filename_Error_Number
 * @see #Fits_Filename_Error_String
 */
void CCD_Fits_Filename_Error(void)
{
	char time_string[32];

	CCD_Global_Get_Current_Time_String(time_string,32);
	/* if the error number is zero an error message has not been set up
	** This is in itself an error as we should not be calling this routine
	** without there being an error to display */
	if(Fits_Filename_Error_Number == 0)
		sprintf(Fits_Filename_Error_String,"Logic Error:No Error defined");
	fprintf(stderr,"%s CCD_Fits_Filename:Error(%d) : %s\n",time_string,Fits_Filename_Error_Number,
		Fits_Filename_Error_String);
}

/**
 * The error routine that reports any errors occuring in ccd_fits_filename in a standard way. This routine places the
 * generated error string at the end of a passed in string argument.
 * @param error_string A string to put the generated error in. This string should be initialised before
 * being passed to this routine. The routine will try to concatenate it's error string onto the end
 * of any string already in existance.
 * @see ccd_global.html#CCD_Global_Get_Current_Time_String
 * @see #Fits_Filename_Error_Number
 * @see #Fits_Filename_Error_String
 */
void CCD_Fits_Filename_Error_String(char *error_string)
{
	char time_string[32];

	CCD_Global_Get_Current_Time_String(time_string,32);
	/* if the error number is zero an error message has not been set up
	** This is in itself an error as we should not be calling this routine
	** without there being an error to display */
	if(Fits_Filename_Error_Number == 0)
		sprintf(Fits_Filename_Error_String,"Logic Error:No Error defined");
	sprintf(error_string+strlen(error_string),"%s CCD_Fits_Filename:Error(%d) : %s\n",time_string,
		Fits_Filename_Error_Number,Fits_Filename_Error_String);
}
/* ----------------------------------------------------------------------------
** 		internal functions 
** ---------------------------------------------------------------------------- */
/**
 * Get a date number. This is an integer of the form yyyymmdd, used as the date indicator
 * in a LT FITS filename. The date is for the start of night, i.e. between mignight and 12 noon the day before is used.
 * @param date_number The date number, a pointer to an integer. On successful return, an integer of the form yyyymmdd.
 * @return Returns TRUE if the routine succeeds and returns FALSE if an error occurs.
 */
static int Fits_Filename_Get_Date_Number(int *date_number)
{
	struct timespec current_time;
#ifndef _POSIX_TIMERS
	struct timeval gtod_current_time;
#endif
	time_t seconds_since_epoch;
	const struct tm *broken_down_time = NULL;

	if(date_number == NULL)
	{
		Fits_Filename_Error_Number = 5;
		sprintf(Fits_Filename_Error_String,"Fits_Filename_Get_Date_Number:date_number was NULL.");
		return FALSE;
	}
#ifdef _POSIX_TIMERS
	clock_gettime(CLOCK_REALTIME,&current_time);
#else
	gettimeofday(&gtod_current_time,NULL);
	current_time.tv_sec = gtod_current_time.tv_sec;
	current_time.tv_nsec = gtod_current_time.tv_usec*CCD_GLOBAL_ONE_MICROSECOND_NS;
#endif
	seconds_since_epoch = (time_t)(current_time.tv_sec);
	broken_down_time = gmtime(&(seconds_since_epoch));
	/* should we be looking at yesterdays date? If hour of day 0..12, yes */
	if(broken_down_time->tm_hour < 12)
	{
		seconds_since_epoch -= (12*60*60); /* subtract 12 hours from seconds since epoch */
		broken_down_time = gmtime(&(seconds_since_epoch));
	}
	/* compute date number */
	(*date_number) = 0;
	/* tm_year is years since 1900, add 1900 and multiply by 10000 to leave room for month/day */
	(*date_number) += (broken_down_time->tm_year+1900)*10000;
	/* tm_mon is months since January, 0..11, so add one and multiply by 100 to leave room for day */
	(*date_number) += (broken_down_time->tm_mon+1)*100;
	/* tm_mday is day of month 1..31, so just add */
	(*date_number) += broken_down_time->tm_mday;
	return TRUE;
}

/**
 * Select routine for scandir. Selects files ending in 0.fits.
 * @param entry The directory entry.
 * @return The routine returns TRUE if the file ends in '0.fits',
 *         and so is added to the list of files scandir returns. Otherwise, FALSE it returned.
 */
static int Fits_Filename_File_Select(const struct dirent *entry)
{
	if(strstr(entry->d_name,"0.fits")!=NULL)
		return (TRUE);
	return (FALSE);
}

/**
 * Given a FITS filename derive a suitable '.lock' filename.
 * @param filename The FITS filename.
 * @param lock_filename A buffer. On return, this is filled with a suitable lock filename for the FITS image.
 * @return Returns TRUE if the routine succeeds and returns FALSE if an error occurs.
 * @see #CCD_GLOBAL_ERROR_STRING_LENGTH
 */
static int Fits_Filename_Lock_Filename_Get(char *filename,char *lock_filename)
{
	char *ch_ptr = NULL;

	if(filename == NULL)
	{
		Fits_Filename_Error_Number = 22;
		sprintf(Fits_Filename_Error_String,"Fits_Filename_Lock_Filename_Get:filename was NULL.");
		return FALSE;
	}
	if(strlen(filename) >= CCD_GLOBAL_ERROR_STRING_LENGTH)
	{
		Fits_Filename_Error_Number = 23;
		sprintf(Fits_Filename_Error_String,"Fits_Filename_Lock_Filename_Get:FITS filename was too long(%d).",
			strlen(filename));
		return FALSE;
	}
	/* lock filename starts the same as FITS filename */
	strcpy(lock_filename,filename);
	/* Find FITS filename '.fits' extension in lock_filename buffer */
	/* This does allow for multiple '.fits' to be present in the FITS filename.
	** This should never occur. */
	ch_ptr = strstr(lock_filename,".fits");
	if(ch_ptr == NULL)
	{
		Fits_Filename_Error_Number = 24;
		sprintf(Fits_Filename_Error_String,"Fits_Filename_Lock_Filename_Get:'.fits' not found in filename %s.",
			filename);
		return FALSE;
	}
	/* terminate lock filename at start of '.fits' (removing '.fits') */
	(*ch_ptr) = '\0';
	/* add '.lock' to lock filename */
	strcat(lock_filename,".lock");
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
	if(fptr == NULL)
		return FALSE;
	fclose(fptr);
	return TRUE;
}

