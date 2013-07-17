/* test_camera_select.c
** $Header$
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

static char* Andor_Error_Code_To_String(unsigned int error_code);

/**
 * Main program. Test camera selection.
 * @param argc The number of arguments to the program.
 * @param argv An array of argument strings.
 * @return This function returns 0 if the program succeeds, and a positive integer if it fails.
 */
int main(int argc, char *argv[])
{
	at_32 camera_count,camera_handle;
	unsigned long andor_retval;
	int retval, camera_index;

	GetAvailableCameras(&camera_count);
	fprintf(stdout,"Andor library has found %d cameras.\n",camera_count);
	if(argc == 2)
	{
		retval = sscanf(argv[1],"%d",&camera_index);
		if(retval != 1)
		{
			fprintf(stderr,"Failed to parse camera index %s.\n",argv[1]);
			return 1;
		}
		andor_retval = GetCameraHandle(camera_index,&camera_handle);
		if(andor_retval != DRV_SUCCESS)
		{
			fprintf(stderr,"GetCameraHandle failed : %u : %s.\n",andor_retval,
				Andor_Error_Code_To_String(andor_retval));
			return 1;
		}
		fprintf(stdout,"GetCameraHandle(camera_index=%d) returned camera_handle %d.\n",camera_index,
			camera_handle);
		fprintf(stdout,"Calling SetCurrentCamera(camera_handle=%d).\n",camera_handle);
		SetCurrentCamera(camera_handle);
	}
	andor_retval = Initialize("/usr/local/etc/andor");
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"Initialize failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	ShutDown();	
	return 0;
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
** $Log$
*/
