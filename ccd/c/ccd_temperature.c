/* ccd_temperature.c
** low level ccd library
** $HeadURL$
*/

/**
 * ccd_temperature holds the routines for calulating the current CCD temperature and setting the CCDs
 * temperature.
 * @author Chris Mottram
 * @version $Revision: 1.1 $
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
#include <string.h>
#include <math.h>
#include <time.h>
#include "atmcdLXd.h"
#include "log_udp.h"
#include "ccd_global.h"
#include "ccd_setup.h"
#include "ccd_temperature.h"

/**
 * Revision Control System identifier.
 */
static char rcsid[] = "$Id: ccd_temperature.c,v 1.1 2012/11/12 16:29:03 cjm Exp $";

/* data types */
/**
 * Internal temperature Data structure. Contains cached temperature data.
 * <dl>
 * <dt>Target_Temperature</dt> <dd>Cached target temperature, in degrees C</dd>
 * <dt>Cached_Temperature</dt> <dd>Cached temperature, in degrees C</dd>
 * <dt>Cached_Temperature_Status</dt> <dd>Cached Status of type CCD_TEMPERATURE_STATUS.</dd>
 * <dt>Cache_Date_Stamp</dt> <dd>Date cached data was acquired, of type struct timespec.</dd>
 * </dl>
 * @see #CCD_TEMPERATURE_STATUS
 */
struct Temperature_Struct
{
	double Target_Temperature;
	double Cached_Temperature;
	enum CCD_TEMPERATURE_STATUS Cached_Temperature_Status;
	struct timespec Cache_Date_Stamp;
};

/* external variables */

/* internal variables */
/**
 * Internal cached temperature Data. 
 * @see #Temperature_Struct
 */
static struct Temperature_Struct Temperature_Data = 
{
	0.0,0.0,CCD_TEMPERATURE_STATUS_UNKNOWN,{0,0L}
};
/**
 * Variable holding error code of last operation performed by ccd_temperature.
 */
static int Temperature_Error_Number = 0;
/**
 * Local variable holding description of the last error that occured.
 */
static char Temperature_Error_String[CCD_GLOBAL_ERROR_STRING_LENGTH] = "";

/* --------------------------------------------------------------------------
**       External functions
** -------------------------------------------------------------------------- */

/**
 * Get the current temperature of the CCD. If successfull, the local temperature cache is updated.
 * @param temperature The address of a double to return the temperature in, in degrees centigrade.
 * @param temperature_status The address of a enum CCD_TEMPERATURE_STATUS to store the temperature status.
 * @return Returns TRUE on success, and FALSE if an error occurs.
 * @see #Temperature_Error_Number
 * @see #Temperature_Error_String
 * @see #Temperature_Data
 * @see ccd_global.html#CCD_Global_Andor_ErrorCode_To_String
 * @see #CCD_TEMPERATURE_STATUS
 */
int CCD_Temperature_Get(double *temperature,enum CCD_TEMPERATURE_STATUS *temperature_status)
{
	unsigned int andor_retval;
	float temperature_f;

	Temperature_Error_Number = 0;
#if LOGGING > 1
	CCD_Global_Log("temperature","ccd_temperature.c","CCD_Temperature_Get",LOG_VERBOSITY_TERSE,"CCD",
			"CCD_Temperature_Get Started.");
#endif /* LOGGING */
	if(temperature == NULL)
	{
		Temperature_Error_Number = 1;
		sprintf(Temperature_Error_String,"CCD_Temperature_Get:temperature pointer was NULL.");
		return FALSE;
	}
	if(temperature_status == NULL)
	{
		Temperature_Error_Number = 2;
		sprintf(Temperature_Error_String,"CCD_Temperature_Get:temperature_status pointer was NULL.");
		return FALSE;
	}
	andor_retval = GetTemperatureF(&temperature_f);
	(*temperature) = (double)temperature_f;
#if LOGGING > 5
	CCD_Global_Log_Format("temperature","ccd_temperature.c","CCD_Temperature_Get",LOG_VERBOSITY_VERBOSE,"CCD",
			      "GetTemperatureF returned (%.2f,%d).",(*temperature),andor_retval);
#endif /* LOGGING */
	switch(andor_retval)
	{
		case DRV_NOT_INITIALIZED:
			(*temperature_status) = CCD_TEMPERATURE_STATUS_UNKNOWN;
			Temperature_Error_Number = 3;
			sprintf(Temperature_Error_String,"CCD_Temperature_Get:GetTemperatureF failed %d(%s).",
				andor_retval,CCD_Global_Andor_ErrorCode_To_String(andor_retval));
			return FALSE;
		case DRV_ACQUIRING:
			(*temperature_status) = CCD_TEMPERATURE_STATUS_UNKNOWN;
			break;
		case DRV_ERROR_ACK:
			(*temperature_status) = CCD_TEMPERATURE_STATUS_UNKNOWN;
			Temperature_Error_Number = 4;
			sprintf(Temperature_Error_String,"CCD_Temperature_Get: GetTemperatureF failed %s(%u).",
				CCD_Global_Andor_ErrorCode_To_String(andor_retval),andor_retval);
			return FALSE;
		case DRV_TEMP_OFF:
			(*temperature_status) = CCD_TEMPERATURE_STATUS_OFF;
			break;
		case DRV_TEMP_STABILIZED:
			(*temperature_status) = CCD_TEMPERATURE_STATUS_OK;
			break;
		case DRV_TEMP_NOT_STABILIZED:
			(*temperature_status) = CCD_TEMPERATURE_STATUS_RAMPING;
			break;
		case DRV_TEMP_NOT_REACHED:
			(*temperature_status) = CCD_TEMPERATURE_STATUS_RAMPING;
			break;
		case DRV_TEMP_DRIFT:
			(*temperature_status) = CCD_TEMPERATURE_STATUS_RAMPING;
			break;
		default:
			Temperature_Error_Number = 5;
			sprintf(Temperature_Error_String,
				"CCD_Temperature_Get:GetTemperatureF returned odd error code %s(%u).",
				CCD_Global_Andor_ErrorCode_To_String(andor_retval),andor_retval);
			return FALSE;
	}
	/* update cached copy */
	Temperature_Data.Cached_Temperature = (*temperature);
	Temperature_Data.Cached_Temperature_Status = (*temperature_status);
	clock_gettime(CLOCK_REALTIME,&(Temperature_Data.Cache_Date_Stamp));
#if LOGGING > 1
	CCD_Global_Log("temperature","ccd_temperature.c","CCD_Temperature_Get",LOG_VERBOSITY_TERSE,"CCD",
		       "CCD_Temperature_Get Finished.");
#endif /* LOGGING */
	return TRUE;
}

/**
 * Routine to set the target temperature the CCD Controller will try to keep the CCD at during
 * operation of the camera. The cooler is turned on, and the cooler mode set to maintain
 * CCD temperature at andor library exit. The cached target temperature is also set.
 * @param target_temperature The temperature we want the CCD cooled to, in degrees centigrade.
 * @return TRUE if the target temperature was set, FALSE if an error occured.
 * @see #Temperature_Data
 * @see #Temperature_Error_Number
 * @see #Temperature_Error_String
 * @see ccd_global.html#CCD_Global_Log
 * @see ccd_global.html#CCD_Global_Log_Format
 * @see ccd_global.html#CCD_Global_Andor_ErrorCode_To_String
 */
int CCD_Temperature_Set(double target_temperature)
{
	unsigned int andor_retval;

	Temperature_Error_Number = 0;
#if LOGGING > 0
	CCD_Global_Log_Format("ccd","ccd_temperature.c","CCD_Temperature_Set",LOG_VERBOSITY_VERBOSE,"CCD",
			      "CCD_Temperature_Set(temperature=%.2f) started.",target_temperature);
#endif
	andor_retval = SetTemperature(target_temperature);
	if(andor_retval != DRV_SUCCESS)
	{
		Temperature_Error_Number = 6;
		sprintf(Temperature_Error_String,"CCD_Temperature_Set:SetTemperature(%.2f) failed %d(%s).",
			target_temperature,andor_retval,CCD_Global_Andor_ErrorCode_To_String(andor_retval));
		return FALSE;
	}
       	/* save target temperature to put into FITS headers later. */
	Temperature_Data.Target_Temperature = target_temperature;
#if LOGGING > 3
	CCD_Global_Log("ccd","ccd_temperature.c","CCD_Temperature_Set",LOG_VERBOSITY_VERBOSE,"CCD",
		       "CCD_Temperature_Set:Turning on cooler.");
#endif
	andor_retval = CoolerON();
	if(andor_retval != DRV_SUCCESS)
	{
		Temperature_Error_Number = 7;
		sprintf(Temperature_Error_String,"CCD_Temperature_Set:CoolerON failed %d(%s).",andor_retval,
			CCD_Global_Andor_ErrorCode_To_String(andor_retval));
		return FALSE;
	}
#if LOGGING > 3
	CCD_Global_Log("ccd","ccd_temperature.c","CCD_Temperature_Set",LOG_VERBOSITY_VERBOSE,"CCD",
		       "CCD_Temperature_Set:Setting cooler to maintain temperature on shutdown.");
#endif
	andor_retval = SetCoolerMode(1);
	if(andor_retval != DRV_SUCCESS)
	{
		Temperature_Error_Number = 8;
		sprintf(Temperature_Error_String,"CCD_Temperature_Set:SetCoolerMode(1) failed %d(%s).",
			andor_retval,CCD_Global_Andor_ErrorCode_To_String(andor_retval));
		return FALSE;
	}
#if LOGGING > 0
	CCD_Global_Log("ccd","ccd_temperature.c","CCD_Temperature_Set",LOG_VERBOSITY_VERBOSE,"CCD",
		       "CCD_Temperature_Set() returned TRUE.");
#endif
	return TRUE;
}

/**
 * Turn the cooler on. Uses the Andor library CoolerON.
 * This returns immediately, but slowly ramps the temperaure to the temperature set value.
 * @return Returns TRUE on success, and FALSE if an error occurs.
 * @see #Temperature_Error_Number
 * @see #Temperature_Error_String
 * @see ccd_global.html#CCD_Global_Log
 * @see ccd_global.html#CCD_Global_Andor_ErrorCode_To_String
 */
int CCD_Temperature_Cooler_On(void)
{
	unsigned int andor_retval;

	Temperature_Error_Number = 0;
#if LOGGING > 3
	CCD_Global_Log("ccd","ccd_temperature.c","CCD_Temperature_Cooler_On",LOG_VERBOSITY_VERBOSE,NULL,"started.");
#endif
	andor_retval = CoolerON();
	if(andor_retval != DRV_SUCCESS)
	{
		Temperature_Error_Number = 9;
		sprintf(Temperature_Error_String,"CCD_Temperature_Cooler_On: CoolerON() failed %s(%d).",
			CCD_Global_Andor_ErrorCode_To_String(andor_retval),andor_retval);
		return FALSE;
	}
#if LOGGING > 3
	CCD_Global_Log("ccd","ccd_temperature.c","CCD_Temperature_Cooler_On",LOG_VERBOSITY_VERBOSE,NULL,"finished.");
#endif
	return TRUE;
}

/**
 * Turn the cooler off. Uses the Andor library CoolerOFF.
 * This returns immediately, but slowly ramps the temperaure to 0C.
 * @return Returns TRUE on success, and FALSE if an error occurs.
 * @see #Temperature_Error_Number
 * @see #Temperature_Error_String
 * @see ccd_global.html#CCD_Global_Log
 * @see ccd_global.html#CCD_Global_Andor_ErrorCode_To_String
 */
int CCD_Temperature_Cooler_Off(void)
{
	unsigned int andor_retval;

	Temperature_Error_Number = 0;
#if LOGGING > 3
	CCD_Global_Log("ccd","ccd_temperature.c","CCD_Temperature_Cooler_Off",LOG_VERBOSITY_VERBOSE,NULL,"started.");
#endif
	andor_retval = CoolerOFF();
	if(andor_retval != DRV_SUCCESS)
	{
		Temperature_Error_Number = 10;
		sprintf(Temperature_Error_String,"CCD_Temperature_Cooler_Off: CoolerOFF() failed %s(%d).",
			CCD_Global_Andor_ErrorCode_To_String(andor_retval),andor_retval);
		return FALSE;
	}
#if LOGGING > 3
	CCD_Global_Log("ccd","ccd_temperature.c","CCD_Temperature_Cooler_Off",LOG_VERBOSITY_VERBOSE,NULL,"finished.");
#endif
	return TRUE;
}

/**
 * Get the cached temperature data from the last CCD_Temperature_Get call.
 * @param temperature A pointer to a double. If non-null, on return filled with the cached temperature in C.
 * @param temperature_status A pointer to a enum CCD_TEMPERATURE_STATUS. If non-null, on return filled with the cached 
 *        temperature status.
 * @param cache_date_stamp A pointer to a struct timespec. If non-null, on return filled with the cache date stamp.
 * @return The routine returns TRUE if successful, and FALSE if it fails.
 * @see #Temperature_Data
 * @see #CCD_TEMPERATURE_STATUS
 */
int CCD_Temperature_Get_Cached_Temperature(double *temperature,enum CCD_TEMPERATURE_STATUS *temperature_status,
					   struct timespec *cache_date_stamp)
{
	char time_buff[32];

#if LOGGING > 0
	CCD_Global_Log("ccd","ccd_temperature.c","CCD_Temperature_Get_Cached_Temperature",LOG_VERBOSITY_VERBOSE,NULL,
		       "CCD_Temperature_Get_Cached_Temperature() started.");
#endif
	if(temperature != NULL)
	{
		(*temperature) = Temperature_Data.Cached_Temperature;
#if LOGGING > 5
		CCD_Global_Log_Format("ccd","ccd_temperature.c","CCD_Temperature_Get_Cached_Temperature",
				      LOG_VERBOSITY_VERBOSE,NULL,
				      "CCD_Temperature_Get_Cached_Temperature() found cached temperature %.2f.",
				      Temperature_Data.Cached_Temperature);
#endif
	}
	if(temperature_status != NULL)
	{
		(*temperature_status) = Temperature_Data.Cached_Temperature_Status;
#if LOGGING > 5
		CCD_Global_Log_Format("ccd","ccd_temperature.c","CCD_Temperature_Get_Cached_Temperature",
				      LOG_VERBOSITY_VERBOSE,NULL,
				   "CCD_Temperature_Get_Cached_Temperature() found cached temperature status %d(%s).",
				      Temperature_Data.Cached_Temperature_Status,
				      CCD_Temperature_Status_To_String(Temperature_Data.Cached_Temperature_Status));
#endif
	}
	if(cache_date_stamp != NULL)
	{
		(*cache_date_stamp) = Temperature_Data.Cache_Date_Stamp;
#if LOGGING > 5
		CCD_Global_Get_Time_String(Temperature_Data.Cache_Date_Stamp,time_buff,31);
		CCD_Global_Log_Format("ccd","ccd_temperature.c","CCD_Temperature_Get_Cached_Temperature",
				      LOG_VERBOSITY_VERBOSE,NULL,
				      "CCD_Temperature_Get_Cached_Temperature() found cache date stamp %d(%s).",
				      Temperature_Data.Cache_Date_Stamp.tv_sec,time_buff);
#endif
	}
#if LOGGING > 0
	CCD_Global_Log("ccd","ccd_temperature.c","CCD_Temperature_Get_Cached_Temperature",LOG_VERBOSITY_VERBOSE,NULL,
		       "CCD_Temperature_Get_Cached_Temperature() returned TRUE.");
#endif
	return TRUE;
}

/**
 * Routine to get the last temperature target sent to the temperature controller.
 * @param target_temperature The address of a double to store the last target temperature.
 * @return The routine returns TRUE if successful, and FALSE if it fails.
 * @see #Temperature_Data
 */
int CCD_Temperature_Target_Temperature_Get(double *target_temperature)
{
#if LOGGING > 0
	CCD_Global_Log("ccd","ccd_temperature.c","CCD_Temperature_Target_Temperature_Get",LOG_VERBOSITY_VERBOSE,NULL,
		       "CCD_Temperature_Target_Temperature_Get() started.");
#endif
	if(target_temperature != NULL)
		(*target_temperature) = Temperature_Data.Target_Temperature;
#if LOGGING > 0
	CCD_Global_Log("ccd","ccd_temperature.c","CCD_Temperature_Target_Temperature_Get",LOG_VERBOSITY_VERBOSE,NULL,
		       "CCD_Temperature_Target_Temperature_Get() returned TRUE.");
#endif
	return TRUE;
}

/**
 * Routine to translate the specified temperature status to a suitable string.
 * @param temperature_status The status to translate.
 * @return A string describing the specified state, or "UNKNOWN".
 * @see #CCD_Temperature_Get
 * @see #CCD_TEMPERATURE_STATUS
 */
char *CCD_Temperature_Status_To_String(enum CCD_TEMPERATURE_STATUS temperature_status)
{
	switch(temperature_status)
	{
		case CCD_TEMPERATURE_STATUS_OFF:
			return "OFF";
		case CCD_TEMPERATURE_STATUS_AMBIENT:
			return "AMBIENT";
		case CCD_TEMPERATURE_STATUS_OK:
			return "OK";
		case CCD_TEMPERATURE_STATUS_RAMPING:
			return "RAMPING";
		case CCD_TEMPERATURE_STATUS_UNKNOWN:
			return "UNKNOWN";
		default:
			return "UNKNOWN";
	}
	return "UNKNOWN";
}

/**
 * Get the current value of ccd_temperature's error number.
 * @return The current value of ccd_temperature's error number.
 * @see #Temperature_Error_Number
 */
int CCD_Temperature_Get_Error_Number(void)
{
	return Temperature_Error_Number;
}

/**
 * The error routine that reports any errors occuring in ccd_temperature in a standard way.
 * @see ccd_global.html#CCD_Global_Get_Current_Time_String
 * @see #Temperature_Error_Number
 * @see #Temperature_Error_String
 */
void CCD_Temperature_Error(void)
{
	char time_string[32];

	CCD_Global_Get_Current_Time_String(time_string,32);
	/* if the error number is zero an error message has not been set up
	** This is in itself an error as we should not be calling this routine
	** without there being an error to display */
	if(Temperature_Error_Number == 0)
		sprintf(Temperature_Error_String,"Logic Error:No Error defined");
	fprintf(stderr,"%s CCD_Temperature:Error(%d) : %s\n",time_string,
		Temperature_Error_Number,Temperature_Error_String);
}

/**
 * The error routine that reports any errors occuring in ccd_temperature in a standard way. This routine places the
 * generated error string at the end of a passed in string argument.
 * @param error_string A string to put the generated error in. This string should be initialised before
 * being passed to this routine. The routine will try to concatenate it's error string onto the end
 * of any string already in existance.
 * @see ccd_global.html#CCD_Global_Get_Current_Time_String
 * @see #Temperature_Error_Number
 * @see #Temperature_Error_String
 */
void CCD_Temperature_Error_String(char *error_string)
{
	char time_string[32];

	CCD_Global_Get_Current_Time_String(time_string,32);
	/* if the error number is zero an error message has not been set up
	** This is in itself an error as we should not be calling this routine
	** without there being an error to display */
	if(Temperature_Error_Number == 0)
		sprintf(Temperature_Error_String,"Logic Error:No Error defined");
	sprintf(error_string+strlen(error_string),"%s CCD_Temperature:Error(%d) : %s\n",time_string,
		Temperature_Error_Number,Temperature_Error_String);
}

