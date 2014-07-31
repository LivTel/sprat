/* sprat_multrun.c
** Sprat multrun routines
** $HeadURL$
*/
/**
 * Multrun routines for the sprat program.
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

#include "ccd_fits_filename.h"
#include "ccd_fits_header.h"

#include "sprat_global.h"
#include "sprat_multrun.h"

/* enums */
/**
 * Exposure type enumeration.
 * <ul>
 * <li>MULTRUN_OBSTYPE_BIAS
 * <li>MULTRUN_OBSTYPE_EXPOSE
 * <li>MULTRUN_OBSTYPE_DARK
 * </ul>
 */
enum MULTRUN_OBSTYPE
{
	MULTRUN_OBSTYPE_EXPOSE=0,MULTRUN_OBSTYPE_BIAS=1,MULTRUN_OBSTYPE_DARK=2
};

/* structures */
/**
 * Data type holding local data to sprat_multrun. This consists of the following:
 * <dl>
 * <dt>Unbinned_NCols</dt> <dd>Number of unbinned columns in multrun images.</dd>
 * <dt>Unbinned_NRows</dt> <dd>Number of unbinned rows in multrun images.</dd>
 * <dt>Bin_X</dt> <dd>X binning in multrun images.</dd>
 * <dt>Bin_Y</dt> <dd>Y binning in multrun images.</dd>
 * <dt>Binned_NCols</dt> <dd>Number of binned columns in multrun images.</dd>
 * <dt>Binned_NRows</dt> <dd>Number of binned rows in multrun images.</dd>
 * <dt>Exposure_Length</dt> <dd>The exposure length in milliseconds.</dd>
 * <dt>Exposure_Index</dt> <dd>Which image we are doing in a Multrun.</dd>
 * <dt>Exposure_Count</dt> <dd>The number of images to do in the current Multrun.</dd>
 * <dt>Is_Active</dt> <dd>Boolean determining whether a multrun is ongoing is running.</dd>
 * <dt>Fits_Header</dt> <dd>Fits_Header_Struct structure containing FITS header data to write into FITS images.</dd>
 * </dl>
 * @see ../ccd/cdocs/ccd_fits_header.html#Fits_Header_Struct
 */
struct Multrun_Struct
{
	int Unbinned_NCols;
	int Unbinned_NRows;
	int Bin_X;
	int Bin_Y;
	int Binned_NCols;
	int Binned_NRows;
	int Exposure_Length;
	int Exposure_Index;
	int Exposure_Count;
	int Is_Active;
	struct Fits_Header_Struct Fits_Header;
};

/* internal data */
/**
 * Revision Control System identifier.
 */
static char rcsid[] = "$Id$";
/**
 * Instance of multrun data.
 * @see #Multrun_Struct
 * @see ../ccd/cdocs/ccd_fits_header.html#Fits_Header_Struct
 */
static struct Multrun_Struct Multrun_Data = 
{
	0,0,1,1,0,0,
	-1,-1,-1,
	FALSE,
	{NULL,0,0}
};

/* internal functions */
static void Multrun_Fits_Headers_Set(int exp_type);

/* ----------------------------------------------------------------------------
** 		external functions 
** ---------------------------------------------------------------------------- */
/**
 * Routine to set multrun dimensions into the Multrun_Data.
 * Also configures the CCD library/head.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see #Multrun_Data
 * @see #Multrun_Struct
 * @see ../ccd/cdocs/ccd_setup.html#CCD_Setup_Dimensions
 * @see ../ccd/cdocs/ccd_setup.html#CCD_Setup_Window_Struct
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 */
int Sprat_Multrun_Dimensions_Set(int ncols,int nrows,int xbin,int ybin,
				 int use_window,struct CCD_Setup_Window_Struct window)
{
	int retval;

#if SPRAT_DEBUG > 3
	Sprat_Global_Log("multrun","sprat_multrun.c","Sprat_Multrun_Dimensions_Set",LOG_VERBOSITY_VERBOSE,
			 "MULTRUN","started.");
#endif
	if(use_window)
	{
		Multrun_Data.Unbinned_NCols = (window.X_End-window.X_Start)+1;
		Multrun_Data.Unbinned_NRows = (window.Y_End-window.Y_Start)+1;
		Multrun_Data.Bin_X = xbin;
		Multrun_Data.Bin_Y = ybin;		
	}
	else
	{
		Multrun_Data.Unbinned_NCols = ncols;
		Multrun_Data.Unbinned_NRows = nrows;
		Multrun_Data.Bin_X = xbin;
		Multrun_Data.Bin_Y = ybin;
	}
	Multrun_Data.Binned_NCols = Multrun_Data.Unbinned_NCols / Multrun_Data.Bin_X;
	Multrun_Data.Binned_NRows = Multrun_Data.Unbinned_NRows / Multrun_Data.Bin_Y;
	/* call CCD library setup */
	retval = CCD_Setup_Dimensions(ncols,nrows,xbin,ybin,use_window,window);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 400;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Dimensions_Set:CCD_Setup_Dimensions failed.");
		return FALSE;
	}
#if SPRAT_DEBUG > 3
	Sprat_Global_Log("multrun","sprat_multrun.c","Sprat_Multrun_Dimensions_Set",
			 LOG_VERBOSITY_VERBOSE,"MULTRUN","finished.");
#endif
	return TRUE;
}

/**
 * Perform multiple bias exposure operation. Uses the multrun configuration.
 * @param exposure_count The number of biases.
 * @param multrun_number The address of an integer to store the multrun number for this multrun.
 * @param filename_list The address of an array of strings to hold a created filenames. These are the FITS images
 *        created as part of this process.
 * @param filename_count The address of an integer to fill in with the number of filenames in the list.
 * @return The routine returns TRUE on success, and FALSE on failure.
 * @see #Multrun_Data
 * @see #Multrun_Fits_Headers_Set
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see sprat_global.html#Sprat_Global_String_List_Add
 * @see sprat_config.html#Sprat_Config_Get_Integer
 * @see sprat_config.html#Sprat_Config_Get_Double
 * @see ../ccd/cdocs/ccd_setup.html#CCD_Setup_Dimensions
 * @see ../ccd/cdocs/ccd_exposure.html#CCD_Exposure_Expose
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_FITS_FILENAME_EXPOSURE_TYPE
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_FITS_FILENAME_PIPELINE_FLAG
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_Fits_Filename_Next_Multrun
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_Fits_Filename_Next_Run
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_Fits_Filename_Multrun_Get
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_Fits_Filename_Get_Filename
 * @see ../ccd/cdocs/ccd_fits_header.html#Fits_Header_Struct
 * @see ../ccd/cdocs/ccd_temperature.html#CCD_Temperature_Get
 * @see ../ccd/cdocs/ccd_temperature.html#CCD_TEMPERATURE_STATUS
 */
int Sprat_Multrun_Bias(int exposure_count,int *multrun_number,char **filename_list[],int *filename_count)
{
	struct CCD_Setup_Window_Struct window;
	struct timespec start_time;
	char filename[256];
	int retval,i;

#if SPRAT_DEBUG > 1
	Sprat_Global_Log("multrun","sprat_multrun.c","Sprat_Multrun_Bias",LOG_VERBOSITY_TERSE,"MULTRUN","started.");
#endif
	if(Multrun_Data.Is_Active)
	{
		Sprat_Global_Error_Number = 401;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Bias:Already doing multrun.");
		return FALSE;
	}
	if(exposure_count < 1)
	{
		Sprat_Global_Error_Number = 402;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Bias:Exposure Count too small(%d).",exposure_count);
		return FALSE;
	}
	if(multrun_number == NULL)
	{
		Sprat_Global_Error_Number = 411;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Bias:multrun_number was NULL.");
		return FALSE;
	}
	if(filename_list == NULL)
	{
		Sprat_Global_Error_Number = 416;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Bias:filename_list was NULL.");
		return FALSE;
	}
	if(filename_count == NULL)
	{
		Sprat_Global_Error_Number = 425;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Bias:filename_count was NULL.");
		return FALSE;
	}
	Sprat_Global_Error_Number = 0;
	Multrun_Data.Is_Active = TRUE;
	Multrun_Data.Exposure_Index = 0;
	Multrun_Data.Exposure_Count = exposure_count;
	/* CCD is setup in CCD_Multrun_Dimensions_Set. */
	/* exposure length */
	Multrun_Data.Exposure_Length = 0;
	/* increment filename */
	if(!CCD_Fits_Filename_Next_Multrun())
	{
		/* reset active flag */
		Multrun_Data.Is_Active = FALSE;
		Sprat_Global_Error_Number = 403;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Bias:CCD_Fits_Filename_Next_Multrun failed.");
		return FALSE;
	}
	/* set multrun number */
	(*multrun_number) = CCD_Fits_Filename_Multrun_Get();
	/* loop over biases to take */
	for(i=0;i<Multrun_Data.Exposure_Count;i++)
	{
		if(!CCD_Fits_Filename_Next_Run())
		{
			/* reset active flag */
			Multrun_Data.Is_Active = FALSE;
			Sprat_Global_Error_Number = 404;
			sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Bias:CCD_Fits_Filename_Next_Run failed.");
			return FALSE;
		}
		/* get fits filename */
		if(!CCD_Fits_Filename_Get_Filename(CCD_FITS_FILENAME_EXPOSURE_TYPE_BIAS,
						   CCD_FITS_FILENAME_PIPELINE_FLAG_UNREDUCED,
						   filename,256))
		{
			/* reset active flag */
			Multrun_Data.Is_Active = FALSE;
			Sprat_Global_Error_Number = 405;
			sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Bias:CCD_Fits_Filename_Get_Filename failed.");
			return FALSE;
		}
		Multrun_Fits_Headers_Set(MULTRUN_OBSTYPE_BIAS);
		/* do an exposure */
		start_time.tv_sec = 0;
		start_time.tv_nsec = 0;
#if SPRAT_DEBUG > 5
		Sprat_Global_Log_Format("multrun","sprat_multrun.c","Sprat_Multrun_Bias",LOG_VERBOSITY_VERBOSE,
					"MULTRUN","Calling CCD_Exposure_Expose with exposure length %d ms.",
					Multrun_Data.Exposure_Length);
#endif
		retval = CCD_Exposure_Bias(Multrun_Data.Fits_Header,filename);
		if(retval == FALSE)
		{
			/* reset active flag */
			Multrun_Data.Is_Active = FALSE;
			Sprat_Global_Error_Number = 406;
			sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Bias:CCD_Exposure_Bias failed.");
			return FALSE;
		}
#if SPRAT_DEBUG > 7
		Sprat_Global_Log("multrun","sprat_multrun.c","Sprat_Multrun_Bias",LOG_VERBOSITY_VERBOSE,"MULTRUN",
				 "Exposure completed.");
#endif
		retval = Sprat_Global_String_List_Add(filename_list,filename_count,filename);
		if(retval == FALSE)
		{
			/* reset active flag */
			Multrun_Data.Is_Active = FALSE;
			Sprat_Global_Error_Number = 426;
			sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Bias:Failed to add filename to list(%d).",
				(*filename_count));
			return FALSE;
		}
		Multrun_Data.Exposure_Index++;
	}/* end for on count */
	/* reset active flag */
	Multrun_Data.Is_Active = FALSE;
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("multrun","sprat_multrun.c","Sprat_Multrun_Bias",LOG_VERBOSITY_TERSE,"MULTRUN","finished.");
#endif
	return TRUE;
}

/**
 * Take some dark exposures. Uses the multrun configuration.
 * @param exposure_length The length of the dark, in milliseconds.
 * @param exposure_count The number of darks to do.
 * @param multrun_number The address of an integer to store the multrun number for this multrun.
 * @param filename_list The address of an array of strings to hold a created filenames. These are the FITS images
 *        created as part of this process.
 * @param filename_count The address of an integer to fill in with the number of filenames in the list.
 * @return The routine returns TRUE on success, and FALSE on failure.
 * @see #Multrun_Data
 * @see #Multrun_Fits_Headers_Set
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see sprat_global.html#Sprat_Global_String_List_Add
 * @see sprat_config.html#Sprat_Config_Get_Integer
 * @see sprat_config.html#Sprat_Config_Get_Double
 * @see ../ccd/cdocs/ccd_exposure.html#CCD_Exposure_Expose
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_Fits_Filename_Next_Multrun
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_Fits_Filename_Next_Run
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_Fits_Filename_Multrun_Get
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_Fits_Filename_Get_Filename
 * @see ../ccd/cdocs/ccd_setup.html#CCD_Setup_Dimensions
 * @see ../ccd/cdocs/ccd_temperature.html#CCD_TEMPERATURE_STATUS
 * @see ../ccd/cdocs/ccd_temperature.html#CCD_Temperature_Get
 */
int Sprat_Multrun_Dark(int exposure_length,int exposure_count,int *multrun_number,
		       char **filename_list[],int *filename_count)
{
	struct CCD_Setup_Window_Struct window;
	struct timespec start_time;
	char filename[256];
	unsigned short *buffer_ptr = NULL;
	int retval,i;

#if SPRAT_DEBUG > 1
	Sprat_Global_Log("multrun","sprat_multrun.c","Sprat_Multrun_Dark",LOG_VERBOSITY_TERSE,"MULTRUN","started.");
#endif
	if(Multrun_Data.Is_Active)
	{
		Sprat_Global_Error_Number = 407;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Dark:Already doing multrun.");
		return FALSE;
	}
	if(multrun_number == NULL)
	{
		Sprat_Global_Error_Number = 417;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Dark:multrun_number was NULL.");
		return FALSE;
	}
	if(exposure_count < 1)
	{
		Sprat_Global_Error_Number = 418;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Dark:Exposure Count too small(%d).",exposure_count);
		return FALSE;
	}
	if(filename_list == NULL)
	{
		Sprat_Global_Error_Number = 419;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Dark:filename_list was NULL.");
		return FALSE;
	}
	if(filename_count == NULL)
	{
		Sprat_Global_Error_Number = 427;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Dark:filename_count was NULL.");
		return FALSE;
	}
	Sprat_Global_Error_Number = 0;
	Multrun_Data.Is_Active = TRUE;
	Multrun_Data.Exposure_Count = exposure_count;
	Multrun_Data.Exposure_Index = 0;
	/* CCD is setup in Sprat_Multrun_Dimensions_Set. */
	/* exposure length */
	Multrun_Data.Exposure_Length = exposure_length;
	if(Multrun_Data.Exposure_Length < 0)
	{
		/* reset active flag */
		Multrun_Data.Is_Active = FALSE;
		Sprat_Global_Error_Number = 408;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Dark:No exposure length set.");
		return FALSE;
	}
#if SPRAT_DEBUG > 5
	Sprat_Global_Log_Format("multrun","sprat_multrun.c","Sprat_Multrun_Dark",
				LOG_VERBOSITY_VERBOSE,"MULTRUN",
				"Using current Exposure Length:%d ms.",
				Multrun_Data.Exposure_Length);
#endif
	/* increment filename */
	if(!CCD_Fits_Filename_Next_Multrun())
	{
		/* reset active flag */
		Multrun_Data.Is_Active = FALSE;
		return FALSE;
	}
	/* set multrun number */
	(*multrun_number) = CCD_Fits_Filename_Multrun_Get();
	/* loop over number of darks to do */
	for(i = 0; i < exposure_count; i++)
	{
		if(!CCD_Fits_Filename_Next_Run())
		{
			/* reset active flag */
			Multrun_Data.Is_Active = FALSE;
			return FALSE;
		}
		/* get fits filename */
		if(!CCD_Fits_Filename_Get_Filename(CCD_FITS_FILENAME_EXPOSURE_TYPE_DARK,
						   CCD_FITS_FILENAME_PIPELINE_FLAG_UNREDUCED,filename,256))
		{
			/* reset active flag */
			Multrun_Data.Is_Active = FALSE;
			return FALSE;
		}
		Multrun_Fits_Headers_Set(MULTRUN_OBSTYPE_DARK);
		/* do an exposure */
		start_time.tv_sec = 0;
		start_time.tv_nsec = 0;
#if SPRAT_DEBUG > 5
		Sprat_Global_Log_Format("multrun","sprat_multrun.c","Sprat_Multrun_Dark",LOG_VERBOSITY_VERBOSE,
					"MULTRUN","Calling CCD_Exposure_Expose with exposure length %d ms.",
					Multrun_Data.Exposure_Length);
#endif
		retval = CCD_Exposure_Expose(FALSE,start_time,Multrun_Data.Exposure_Length,Multrun_Data.Fits_Header,
					     filename);
		if(retval == FALSE)
		{
			/* reset active flag */
			Multrun_Data.Is_Active = FALSE;
			Sprat_Global_Error_Number = 409;
			sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Dark:CCD_Exposure_Expose failed.");
			return FALSE;
		}
#if SPRAT_DEBUG > 7
		Sprat_Global_Log("multrun","sprat_multrun.c","Sprat_Multrun_Dark",LOG_VERBOSITY_VERBOSE,"MULTRUN",
				 "Exposure completed.");
#endif
		retval = Sprat_Global_String_List_Add(filename_list,filename_count,filename);
		if(retval == FALSE)
		{
			/* reset active flag */
			Multrun_Data.Is_Active = FALSE;
			Sprat_Global_Error_Number = 428;
			sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Dark:Failed to add filename to list(%d).",
				(*filename_count));
			return FALSE;
		}
		/* increment exposure index */
		Multrun_Data.Exposure_Index++;
	}/* exposure count */
	/* reset active flag */
	Multrun_Data.Is_Active = FALSE;
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("multrun","sprat_multrun.c","Sprat_Multrun_Dark",LOG_VERBOSITY_TERSE,"MULTRUN","finished.");
#endif
	return TRUE;
}

/**
 * Perform a multiple exposure. Uses the multrun configuration. The raw image is saved.
 * @param exposure_length The length of the dark, in milliseconds.
 * @param exposure_count The number of exposures to do.
 * @param standard A boolean, if TRUE the MULTRUN is a standard otherwise it is a science exposure.
 * @param multrun_number The address of an integer to store the multrun number for this multrun.
 * @param filename_list The address of an array of strings to hold a created filenames. These are the FITS images
 *        created as part of this process.
 * @param filename_count The address of an integer to fill in with the number of filenames in the list.
 * @return The routine returns TRUE on success, and FALSE on failure.
 * @see #Multrun_Data
 * @see #Multrun_Fits_Headers_Set
 * @see sprat_config.html#Sprat_Config_Get_Integer
 * @see sprat_config.html#Sprat_Config_Get_Double
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see sprat_global.html#Sprat_Global_String_List_Add
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_Fits_Filename_Next_Multrun
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_Fits_Filename_Next_Run
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_Fits_Filename_Get_Filename
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_Fits_Filename_Multrun_Get
 * @see ../ccd/cdocs/ccd_setup.html#CCD_Setup_Dimensions
 * @see ../ccd/cdocs/ccd_exposure.html#CCD_Exposure_Expose
 * @see ../ccd/cdocs/ccd_temperature.html#CCD_Temperature_Get
 * @see ../ccd/cdocs/ccd_temperature.html#CCD_TEMPERATURE_STATUS
 */
int Sprat_Multrun_Multrun(int exposure_length,int exposure_count,int standard,int *multrun_number,
			  char **filename_list[],int *filename_count)
{
	struct CCD_Setup_Window_Struct window;
	struct timespec start_time;
	char filename[256];
	unsigned short *buffer_ptr = NULL;
	int retval,i,exposure_type;

#if SPRAT_DEBUG > 1
	Sprat_Global_Log("multrun","sprat_multrun.c","Sprat_Multrun_Multrun",LOG_VERBOSITY_TERSE,"MULTRUN",
			 "started.");
#endif
	if(Multrun_Data.Is_Active)
	{
		Sprat_Global_Error_Number = 410;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Multrun:Already doing multrun.");
		return FALSE;
	}
	if(multrun_number == NULL)
	{
		Sprat_Global_Error_Number = 420;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Multrun:multrun_number was NULL.");
		return FALSE;
	}
	if(exposure_count < 1)
	{
		Sprat_Global_Error_Number = 421;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Multrun:Exposure Count too small(%d).",
			exposure_count);
		return FALSE;
	}
	if(filename_list == NULL)
	{
		Sprat_Global_Error_Number = 422;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Multrun:filename_list was NULL.");
		return FALSE;
	}
	if(filename_count == NULL)
	{
		Sprat_Global_Error_Number = 423;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Multrun:filename_count was NULL.");
		return FALSE;
	}
	Sprat_Global_Error_Number = 0;
	Multrun_Data.Is_Active = TRUE;
	Multrun_Data.Exposure_Count = exposure_count;
	Multrun_Data.Exposure_Index = 0;
	(*filename_list) = NULL;
	(*filename_count) = 0;
	/* CCD is setup in Sprat_Multrun_Dimensions_Set. */
	/* exposure length */
	Multrun_Data.Exposure_Length = exposure_length;
	if(Multrun_Data.Exposure_Length < 0)
	{
		/* reset active flag */
		Multrun_Data.Is_Active = FALSE;
		Sprat_Global_Error_Number = 412;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Multrun:No exposure length set.");
		return FALSE;
	}
#if SPRAT_DEBUG > 5
	Sprat_Global_Log_Format("multrun","sprat_multrun.c","Sprat_Multrun_Multrun",LOG_VERBOSITY_VERBOSE,"MULTRUN",
				"Using current Exposure Length:%d ms.",Multrun_Data.Exposure_Length);
#endif
	/* standard flag / exposure type */
	if(!SPRAT_GLOBAL_IS_BOOLEAN(standard))
	{
		/* reset active flag */
		Multrun_Data.Is_Active = FALSE;
		Sprat_Global_Error_Number = 413;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Multrun:Standard flag has illegal value %d.",
			standard);
		return FALSE;
	}
	if(standard)
		exposure_type = CCD_FITS_FILENAME_EXPOSURE_TYPE_STANDARD;
	else
		exposure_type = CCD_FITS_FILENAME_EXPOSURE_TYPE_EXPOSURE;
	/* increment filename */
	if(!CCD_Fits_Filename_Next_Multrun())
	{
		/* reset active flag */
		Multrun_Data.Is_Active = FALSE;
		return FALSE;
	}
	/* set multrun number */
	(*multrun_number) = CCD_Fits_Filename_Multrun_Get();
	/* start multrun */
	for(i=0;i<exposure_count;i++)
	{
#if SPRAT_DEBUG > 9
		Sprat_Global_Log_Format("multrun","sprat_multrun.c","Sprat_Multrun_Multrun",
					LOG_VERBOSITY_VERBOSE,"MULTRUN","Starting multrun exposure %d.",i);
#endif
		if(!CCD_Fits_Filename_Next_Run())
		{
			/* reset active flag */
			Multrun_Data.Is_Active = FALSE;
			return FALSE;
		}
		/* get fits filename */
		if(!CCD_Fits_Filename_Get_Filename(exposure_type,CCD_FITS_FILENAME_PIPELINE_FLAG_UNREDUCED,
						   filename,256))
		{
			/* reset active flag */
			Multrun_Data.Is_Active = FALSE;
			return FALSE;
		}
		Multrun_Fits_Headers_Set(MULTRUN_OBSTYPE_EXPOSE);
		/* do an exposure */
		start_time.tv_sec = 0;
		start_time.tv_nsec = 0;
#if SPRAT_DEBUG > 5
		Sprat_Global_Log_Format("multrun","sprat_multrun.c","Sprat_Multrun_Multrun",
					LOG_VERBOSITY_VERBOSE,"MULTRUN",
					"Calling CCD_Exposure_Expose with exposure length %d ms.",
					Multrun_Data.Exposure_Length);
#endif
		retval = CCD_Exposure_Expose(TRUE,start_time,Multrun_Data.Exposure_Length,Multrun_Data.Fits_Header,
					     filename);
		if(retval == FALSE)
		{
			/* reset active flag */
			Multrun_Data.Is_Active = FALSE;
			Sprat_Global_Error_Number = 414;
			sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Multrun:CCD_Exposure_Expose failed.");
			return FALSE;
		}
		retval = Sprat_Global_String_List_Add(filename_list,filename_count,filename);
		if(retval == FALSE)
		{
			/* reset active flag */
			Multrun_Data.Is_Active = FALSE;
			Sprat_Global_Error_Number = 424;
			sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Multrun:Failed to add filename to list(%d).",
				(*filename_count));
			return FALSE;
		}
#if SPRAT_DEBUG > 7
		Sprat_Global_Log("multrun","sprat_multrun.c","Sprat_Multrun_Multrun",
				 LOG_VERBOSITY_VERBOSE,"MULTRUN","exposure completed.");
#endif
		Multrun_Data.Exposure_Index++;
	}/* end for on exposure count */
	/* reset active flag */
	Multrun_Data.Is_Active = FALSE;
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("multrun","sprat_multrun.c","Sprat_Multrun_Multrun",LOG_VERBOSITY_TERSE,"MULTRUN",
			 "finished.");
#endif
	return TRUE;
}

/**
 * Get the address of the FITS header structure used for configuring and writing FITS headers to FITS images.
 * @param fits_header The address of a pointer to a struct Fits_Header_Struct. On return the pointer variable
 *        pointed to by this address will be filled with the address of Multrun_Data.Fits_Header.
 */
int Sprat_Multrun_Fits_Header_Get(struct Fits_Header_Struct **fits_header)
{
	if(fits_header == NULL)
	{
		Sprat_Global_Error_Number = 415;
		sprintf(Sprat_Global_Error_String,"Sprat_Multrun_Fits_Header_Get:fits_header was NULL.");
		return FALSE;
	}
	(*fits_header) = &(Multrun_Data.Fits_Header);
	return TRUE;
}

/**
 * Retrieve which exposure in the multrun (from 0 to Multrun_Data.Exposure_Count-1) is currently being done.
 * @return The current value of Multrun_Data.Exposure_Index, an integer from 0 to Multrun_Data.Exposure_Count-1.
 * @see #Multrun_Data
 */
int Sprat_Multrun_Exposure_Index_Get(void)
{
	return Multrun_Data.Exposure_Index;
}

/**
 * Return the number of exposures in the current Multrun.
 * @return The current value of Multrun_Data.Exposure_Count.
 * @see #Multrun_Data
 */
int Sprat_Multrun_Exposure_Count_Get(void)
{
	return Multrun_Data.Exposure_Count;
}

/* ----------------------------------------------------------------------------
** 		internal functions 
** ---------------------------------------------------------------------------- */
/**
 * Sets some FITS header values from configured CCD values.
 * @see #Multrun_Data
 * @see sprat_config.html#Sprat_Config_Get_Double
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_global.html#Sprat_Global_Error
 * @see ../ccd/cdocs/ccd_fits_header.html#CCD_Fits_Header_String_Add
 */
static void Multrun_Fits_Headers_Set(int exp_type)
{
	switch(exp_type)
	{
		case  MULTRUN_OBSTYPE_EXPOSE:
			if(!CCD_Fits_Header_Add_String(&(Multrun_Data.Fits_Header),"OBSTYPE","EXPOSE",NULL))
			{
				Sprat_Global_Error("multrun","sprat_multrun.c","Multrun_Fits_Headers_Set",
						   LOG_VERBOSITY_VERBOSE,"Failed to set OBSTYPE: EXPOSE");
			}
			break;
		case  MULTRUN_OBSTYPE_BIAS:
			if(!CCD_Fits_Header_Add_String(&(Multrun_Data.Fits_Header),"OBSTYPE","BIAS",NULL))
			{
				Sprat_Global_Error("multrun","sprat_multrun.c","Multrun_Fits_Headers_Set",
						   LOG_VERBOSITY_VERBOSE,"Failed to set OBSTYPE: BIAS");
			}
			break;
		case  MULTRUN_OBSTYPE_DARK:
			if(!CCD_Fits_Header_Add_String(&(Multrun_Data.Fits_Header),"OBSTYPE","DARK",NULL))
			{
				Sprat_Global_Error("multrun","sprat_multrun.c","Multrun_Fits_Headers_Set",
						   LOG_VERBOSITY_VERBOSE,"Failed to set OBSTYPE: DARK");
			}
			break;
	}
}

