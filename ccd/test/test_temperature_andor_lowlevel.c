/* test_temperature_andor_lowlevel.c
** $Header: /home/dev/src/sprat/ccd/test/RCS/test_temperature_andor_lowlevel.c,v 1.1 2012/08/14 11:26:43 cjm Exp $
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
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <signal.h>
#include <errno.h>
#include <math.h>
#include <time.h>
#include <unistd.h>
#ifndef _POSIX_TIMERS
#include <sys/time.h>
#endif
#include "atmcdLXd.h"

/**
 * Low level temperature control/status.
 * @author $Author$
 * @version $Revision$
 */
/* hash definitions */
/**
 * False.
 */
#define FALSE (0)
/**
 * True.
 */
#define TRUE (1)
/**
 * Maximum length of some of the strings in this program.
 */
#define MAX_STRING_LENGTH	(256)
/**
 * Convertion factor - degrees to Kelvin.
 */
#define CENTIGRADE_TO_KELVIN      (273.15)
/**
 * Default Andor installation dir to pass to Initialize.
 */
#define DEFAULT_ANDOR_DIR       ("/usr/local/etc/andor")
/**
 * Default camera index (0).
 */
#define DEFAULT_CAMERA_INDEX   (0)
/**
 * Default temperature to set the CCD to (-60.0).
 */
#define DEFAULT_TEMPERATURE    (-60.0)
/* internal variables */
/**
 * Revision control system identifier.
 */
static char rcsid[] = "$Id$";
/**
 * Andor installation dir to pass to Initialize.
 * @see #DEFAULT_ANDOR_DIR
 */
static char Andor_Dir[MAX_STRING_LENGTH] = DEFAULT_ANDOR_DIR;
/**
 * Boolean to determine whether to set the target temperature.
 */
static int Temperature_Set = FALSE;
/**
 * Target_Temperature to set the CCD to.
 * @see #DEFAULT_TEMPERATURE
 */
static double Target_Temperature = DEFAULT_TEMPERATURE;
/**
 * What camera to use.
 * @see #DEFAULT_CAMERA_INDEX
 */
static int Camera_Index = DEFAULT_CAMERA_INDEX;
/**
 * The current CCD temperature in degrees C.
 */
static double Current_Temperature;
/* internal routines */
static int Parse_Arguments(int argc, char *argv[]);
static void Help(void);
static char* Andor_Error_Code_To_String(unsigned int error_code);

/**
 * Main program.
 * @param argc The number of arguments to the program.
 * @param argv An array of argument strings.
 * @return This function returns 0 if the program succeeds, and a positive integer if it fails.
 * @see #Andor_Dir
 * @see #Camera_Index
 * @see #Temperature_Set
 * @see #Target_Temperature
 * @see #Current_Temperature
 */
int main(int argc, char *argv[])
{
	long camera_handle;
	unsigned int andor_retval;
	int i,retval;
	float temperature_float;
/* parse arguments */
	fprintf(stdout,"Parsing Arguments.\n");
	if(!Parse_Arguments(argc,argv))
		return 1;
	fprintf(stdout,"Initialising Camera:\n");
	fprintf(stdout,"Camera Index:%d\n",Camera_Index);
	fprintf(stdout,"Andor Dir:%s\n",Andor_Dir);
	fprintf(stdout,"GetCameraHandle(camera_index=%ld)\n",Camera_Index);
	andor_retval = GetCameraHandle(Camera_Index,&camera_handle);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"GetCameraHandle failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	fprintf(stdout,"SetCurrentCamera(camera_handle=%ld)\n",camera_handle);
	andor_retval = SetCurrentCamera(camera_handle);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"SetCurrentCamera failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	fprintf(stdout,"Initialize(init_dir=%s)\n",Andor_Dir);
	andor_retval = Initialize(Andor_Dir);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"Initialize failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	if(Temperature_Set)
	{
		fprintf(stdout,"SetTemperature(%.2f).\n",Target_Temperature);
		andor_retval = SetTemperature(Target_Temperature);
		if(andor_retval != DRV_SUCCESS)
		{
			fprintf(stderr,"SetTemperature failed : %u : %s.\n",andor_retval,
				Andor_Error_Code_To_String(andor_retval));
			return 1;
		}
		fprintf(stdout,"CoolerON().\n");
		andor_retval = CoolerON();
		if(andor_retval != DRV_SUCCESS)
		{
			fprintf(stderr,"CoolerON failed : %u : %s.\n",andor_retval,
				Andor_Error_Code_To_String(andor_retval));
			return 1;
		}
		fprintf(stdout,"SetCoolerMode(1):Setting cooler to maintain temperature on shutdown.\n");
		andor_retval = SetCoolerMode(1);
		if(andor_retval != DRV_SUCCESS)
		{
			fprintf(stderr,"SetCoolerMode failed : %u : %s.\n",andor_retval,
				Andor_Error_Code_To_String(andor_retval));
			return 1;
		}
	}
	fprintf(stdout,"GetTemperatureF().\n");
	andor_retval = GetTemperatureF(&temperature_float);
	switch(andor_retval)
	{
		case DRV_NOT_INITIALIZED:
			fprintf(stderr,"Temperature Status: Not Initialised.\n");
			break;
		case DRV_ACQUIRING:
			fprintf(stderr,"Temperature Status: DRV_ACQUIRING.\n");
			break;
		case DRV_ERROR_ACK:
			fprintf(stderr,"Temperature Status: DRV_ERROR_ACK.\n");
			break;
		case DRV_TEMP_OFF:
			fprintf(stderr,"Temperature Status: DRV_TEMP_OFF.\n");
			break;
		case DRV_TEMP_STABILIZED:
			fprintf(stderr,"Temperature Status: DRV_TEMP_STABILIZED.\n");
			break;
		case DRV_TEMP_NOT_STABILIZED:
			fprintf(stderr,"Temperature Status: DRV_TEMP_NOT_STABILIZED.\n");
			break;
		case DRV_TEMP_NOT_REACHED:
			fprintf(stderr,"Temperature Status: DRV_TEMP_NOT_REACHED.\n");
			break;
		case DRV_TEMP_DRIFT:
			fprintf(stderr,"Temperature Status: DRV_TEMP_DRIFT.\n");
			break;
		default:
			fprintf(stderr,"Temperature Status: Unknown %u.\n",andor_retval);
			break;
	}
	Current_Temperature = (double)temperature_float;
	fprintf(stdout,"GetTemperatureF() returned current temperature %.2f.\n",Current_Temperature);
	fprintf(stdout,"Finished.\n");
	return 0;
}

/**
 * Routine to parse command line arguments.
 * @param argc The number of arguments sent to the program.
 * @param argv An array of argument strings.
 * @see #Help
 * @see #Camera_Index
 * @see #Andor_Dir
 * @see #Temperature_Set
 * @see #Target_Temperature
 */
static int Parse_Arguments(int argc, char *argv[])
{
	int i,retval;

	for(i=1;i<argc;i++)
	{
	        if((strcmp(argv[i],"-andor_dir")==0))
		{
			if((i+1)<argc)
			{
				strncpy(Andor_Dir,argv[i+1],MAX_STRING_LENGTH-1);
				Andor_Dir[MAX_STRING_LENGTH-1] = '\0';
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:data_dir requires a directory.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-camera_index")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&Camera_Index);
				if(retval != 1)
				{
					fprintf(stderr,
						"Parse_Arguments:Parsing camera index %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:camera index requires an integer.\n");
				return FALSE;
			}

		}
		else if((strcmp(argv[i],"-help")==0)||(strcmp(argv[i],"-h")==0))
		{
			Help();
			exit(0);
		}
		else if(strcmp(argv[i],"-target_temperature")==0)
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%lf",&Target_Temperature);
				if(retval != 1)
				{
					fprintf(stderr,
						"Parse_Arguments:Parsing target temperature %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				Temperature_Set = TRUE;
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:temperature required.\n");
				return FALSE;
			}
		}
		else
		{
			fprintf(stderr,"Parse_Arguments:argument '%s' not recognized.\n",argv[i]);
			return FALSE;
		}
	}
	return TRUE;
}

/**
 * Help routine.
 */
static void Help(void)
{
	fprintf(stdout,"Test Temperature:Help.\n");
	fprintf(stdout,"This program calls Andor functions to get and set the CCD temperature.\n");
	fprintf(stdout,"\t[-andor_dir <directory>][-help]\n");
	fprintf(stdout,"\t[-target_temperature <temperature>][-camera_index <0..n>]\n");
}

/**
 * Return a string for an Andor error code. See /home/dev/src/ringo3/andor/include/atmcdLXd.h.
 * @param error_code The Andor error code.
 * @return A static string representation of the error code.
 */
static char* Andor_Error_Code_To_String(unsigned int error_code)
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
		case DRV_P5INVALID:
			return "DRV_P5INVALID";
		case DRV_P6INVALID:
			return "DRV_P6INVALID";
		case DRV_ERROR_NOCAMERA:
			return "DRV_ERROR_NOCAMERA";
		case DRV_NOT_SUPPORTED:
			return "DRV_NOT_SUPPORTED";
		case DRV_NOT_AVAILABLE:
			return "DRV_NOT_AVAILABLE";
		default:
			return "UNKNOWN";
	}
	return "UNKNOWN";
}

/*
** $Log: test_temperature_andor_lowlevel.c,v $
** Revision 1.1  2012/08/14 11:26:43  cjm
** Initial revision
**
*/
