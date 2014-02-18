/* test_exposure_andor_lowlevel.c
** $Header: /home/dev/src/sprat/ccd/test/RCS/test_exposure_andor_lowlevel.c,v 1.1 2012/08/14 11:26:32 cjm Exp $
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
#include "fitsio.h"

/**
 * Low level exposure test. Just uses Andor library directly and CFITSIO for saving data.
 * Used to explore Horizontal/Vertical Speed timing options.
 * @author $Author$
 * @version $Revision$
 */
/* hash definitions */
/**
 * Maximum length of some of the strings in this program.
 */
#define MAX_STRING_LENGTH	(256)
/**
 * The number of nanoseconds in one millisecond. A struct timespec has fields in nanoseconds.
 */
#define ONE_MILLISECOND_NS	(1000000)
/**
 * Convertion factor - degrees to Kelvin.
 */
#define CENTIGRADE_TO_KELVIN      (273.15)
/**
 * Default Andor installation dir to pass to Initialize.
 */
#define DEFAULT_ANDOR_DIR       ("/usr/local/etc/andor")
/**
 * Default data directory to put output images.
 */
#define DEFAULT_DATA_DIR       (".")
/**
 * Default camera index (0).
 */
#define DEFAULT_CAMERA_INDEX   (0)
/**
 * Default gain for the EMCCD (1).
 */
#define DEFAULT_GAIN           (1)
/**
 * Default temperature to set the CCD to (-60.0).
 */
#define DEFAULT_TEMPERATURE    (-60.0)
/**
 * Default number of columns in the CCD (1024).
 */
#define DEFAULT_SIZE_X	       (1024)
/**
 * Default number of rows in the CCD (255).
 */
#define DEFAULT_SIZE_Y	       (255)
/**
 * False.
 */
/*#define FALSE (0)*/
/**
 * True.
 */
/*#define TRUE (1)*/

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
 * What gain to use for the EMCCD.
 * @see #DEFAULT_GAIN
 */
/* diddly static int Gain = DEFAULT_GAIN;*/
/**
 * Data directory to put output FITS images.
 * @see #DEFAULT_DATA_DIR
 */
static char Data_Dir[MAX_STRING_LENGTH] = DEFAULT_DATA_DIR;
/**
 * The number of columns in the CCD.
 * @see #DEFAULT_SIZE_X
 */
static int Size_X = DEFAULT_SIZE_X;
/**
 * The number of rows in the CCD.
 * @see #DEFAULT_SIZE_Y
 */
static int Size_Y = DEFAULT_SIZE_Y;
/**
 * The number binning factor in columns.
 */
static int Bin_X = 1;
/**
 * The number binning factor in rows.
 */
static int Bin_Y = 1;
/**
 * Whether to do an exposure.
 * @see #Do_Dark
 */
static int Do_Exposure = FALSE;
/**
 * Whether to do a dark.
 * @see #Do_Exposure
 */
static int Do_Dark = FALSE;
/**
 * If doing a dark or exposure, the exposure length, in ms.
 */
static int Exposure_Length = 0;
/**
 * Vertical Shift clock voltage amplitude, 0 is normal, 1..4 increased for high speed readout.
 */
static int VS_Amplitude = 0;
/**
 * Index into the vertical shift speed table.
 */
static int VS_Speed_Index = 1;
/**
 * Index into the horizontal shift speed table.
 */
static int HS_Speed_Index = 0;
/**
 * Which amplifier to use.
 */
static int Amplifier = 0;
/**
 * Which A/D channel to use.
 */
static int AD_Channel = 0;
/**
 * Frame transfer mode: 1 is on.
 */
static int Frame_Transfer = 0;
/**
 * Whether to turn baseline clamp on. 0=off, 1=on.
 */
static int Baseline_Clamp = 0;
/**
 * FITS image filename to save acquired data in.
 * @see #MAX_STRING_LENGTH
 */
static char Filename[MAX_STRING_LENGTH];
/**
 * The current CCD temperature in degrees C.
 */
static double Current_Temperature;

/* internal routines */
static int Parse_Arguments(int argc, char *argv[]);
static void Help(void);
static char* Andor_Error_Code_To_String(unsigned int error_code);
static int Save(int do_dark,int do_exposure,unsigned short *image_data,int pixel_count);

/**
 * Main program.
 * @param argc The number of arguments to the program.
 * @param argv An array of argument strings.
 * @return This function returns 0 if the program succeeds, and a positive integer if it fails.
 * @see #Andor_Dir
 * @see #Camera_Index
 * @see #Temperature_Set
 * @see #Target_Temperature
 * @see #Gain
 * @see #Size_X
 * @see #Size_Y
 * @see #Bin_X
 * @see #Bin_Y
 * @see #Exposure_Length
 * @see #Do_Dark
 * @see #Do_Exposure
 * @see #VS_Speed_Index
 * @see #VS_Amplitude
 * @see #Amplifier
 * @see #HS_Speed_Index
 * @see #Baseline_Clamp
 * @see #Current_Temperature
 */
int main(int argc, char *argv[])
{
	unsigned short *image_data = NULL;
	struct timespec start_time,sleep_time;
	long camera_handle,first_new_image_index,last_new_image_index;
	unsigned int andor_retval;
	int i,retval,speed_count,count,min_gain,max_gain,exposure_status;
	int pixel_count,detector_size_x,detector_size_y;
	float speed,temperature_float;

/* parse arguments */
	fprintf(stdout,"Parsing Arguments.\n");
	if(!Parse_Arguments(argc,argv))
		return 1;
	if((Do_Exposure == FALSE)&&(Do_Dark == FALSE))
	{
		fprintf(stderr,"You must select either -dark or -expose.\n");
		return 1;
	}
	if((Do_Exposure == TRUE)&&(Do_Dark == TRUE))
	{
		fprintf(stderr,"You must select one only of -dark or -expose.\n");
		return 1;
	}
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
	fprintf(stdout,"SetReadMode(4):Setting read mode to full image.\n");
	andor_retval = SetReadMode(4);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"SetReadMode failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	andor_retval = GetDetector(&detector_size_x,&detector_size_y);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"GetDetector failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	fprintf(stdout,"GetDetector(size_x=%d,size_y=%d).\n",detector_size_x,detector_size_y);
	fprintf(stdout,"SetFrameTransferMode(%d):Set frame transfer mode.\n",Frame_Transfer);
	andor_retval = SetFrameTransferMode(Frame_Transfer);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"SetFrameTransferMode failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	fprintf(stdout,"SetKineticCycleTime(0):Set kinetic cycle time to the minimum.\n");
	andor_retval = SetKineticCycleTime(0);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"SetKineticCycleTime failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	fprintf(stdout,"GetNumberAmp().\n");
	andor_retval = GetNumberAmp(&count);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"GetNumberAmp failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	fprintf(stdout,"GetNumberAmp() returned %d amplifiers.\n",count);
	fprintf(stdout,"GetNumberADChannels().\n");
	andor_retval = GetNumberADChannels(&count);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"GetNumberADChannels failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	fprintf(stdout,"GetNumberADChannels() returned %d A/D channels.\n",count);
	fprintf(stdout,"SetADChannel(channel=%d).\n",AD_Channel);
	/* log vertical speed data */
	fprintf(stdout,"GetNumberVSSpeeds().\n");
	andor_retval = GetNumberVSSpeeds(&speed_count);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"GetNumberVSSpeeds failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	fprintf(stdout,"GetNumberVSSpeeds() returned %d speeds.\n",speed_count);
	for(i=0;i < speed_count; i++)
	{
		fprintf(stdout,"GetVSSpeed(index=%d).\n",i);
		andor_retval = GetVSSpeed(i,&speed);
		if(andor_retval != DRV_SUCCESS)
		{
			fprintf(stderr,"GetVSSpeed failed : %u : %s.\n",andor_retval,
				Andor_Error_Code_To_String(andor_retval));
			return 1;
		}
		fprintf(stdout,"GetVSSpeed(index=%d) returned speed %.2f microseconds/pixel shift.\n",i,speed);
	}/* for on vertical speeds */
	fprintf(stdout,"GetFastestRecommendedVSSpeed().\n");
	andor_retval = GetFastestRecommendedVSSpeed(&i,&speed);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"GetFastestRecommendedVSSpeed failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	fprintf(stdout,"GetFastestRecommendedVSSpeed: Index %d, speed %.2f microseconds per pixel shift.\n",
		i,speed);
	fprintf(stdout,"SetVSSpeed(index=%d).\n",VS_Speed_Index);
	andor_retval = SetVSSpeed(VS_Speed_Index); 
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"SetVSSpeed failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	/* log horizontal speed data */
	fprintf(stdout,"GetNumberHSSpeeds(channel=%d,amplifier=%d).\n",AD_Channel,Amplifier);
	andor_retval = GetNumberHSSpeeds(AD_Channel,Amplifier,&speed_count);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"GetNumberHSSpeeds failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	fprintf(stdout,"GetNumberHSSpeeds(channel=%d,amplifier=%d) returned %d speeds.\n",
		AD_Channel,Amplifier,speed_count);
	for(i=0;i < speed_count; i++)
	{
		fprintf(stdout,"GetHSSpeed(channel=%d,amplifier=%d,index=%d).\n",AD_Channel,Amplifier,i);
		andor_retval = GetHSSpeed(AD_Channel,Amplifier,i,&speed);
		if(andor_retval != DRV_SUCCESS)
		{
			fprintf(stderr,"GetHSSpeed failed : %u : %s.\n",andor_retval,
				Andor_Error_Code_To_String(andor_retval));
			return 1;
		}
		fprintf(stdout,"GetHSSpeed(channel=%d,amplifier=%d,index=%d) returned speed %.2f MHz.\n",
			AD_Channel,Amplifier,i,speed);
	}/* for on vertical speeds */
	fprintf(stdout,"SetHSSpeed(amplifier=%d,index=%d).\n",Amplifier,HS_Speed_Index);
	andor_retval = SetHSSpeed(Amplifier,HS_Speed_Index);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"SetHSSpeed failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	fprintf(stdout,"SetBaselineClamp(active=%d).\n",Baseline_Clamp);
	retval = SetBaselineClamp(Baseline_Clamp); 
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"SetBaselineClamp failed : %u : %s.\n",andor_retval,
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
	fprintf(stdout,"SetAcquisitionMode(1):Single scan.\n");
	andor_retval = SetAcquisitionMode(1);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"SetAcquisitionMode failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	if(Do_Dark)
	{
		fprintf(stdout,"SetShutter(1,2,0,0) : shutter closed.\n");
		andor_retval = SetShutter(1,2,0,0);
		if(andor_retval != DRV_SUCCESS)
		{
			fprintf(stderr,"SetShutter (closed) failed : %u : %s.\n",andor_retval,
				Andor_Error_Code_To_String(andor_retval));
			return 1;
		}
	}
	if(Do_Exposure)
	{
		fprintf(stdout,"SetShutter(1,0,0,0) : shutter auto.\n");
		andor_retval = SetShutter(1,0,0,0);
		if(andor_retval != DRV_SUCCESS)
		{
			fprintf(stderr,"SetShutter failed : %u : %s.\n",andor_retval,
				Andor_Error_Code_To_String(andor_retval));
			return 1;
		}
	}
	fprintf(stdout,"SetImage(binx=%d,biny=%d,xstart=%d,xend=%d,ystart=%d,yend=%d).\n",
		Bin_X,Bin_Y,1,Size_X,1,Size_Y);
	andor_retval = SetImage(Bin_X,Bin_Y,1,Size_X,1,Size_Y);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"SetImage failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
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
	fprintf(stdout,"Allocate image buffer.\n");
	pixel_count = (Size_X*Size_Y)/(Bin_X*Bin_Y);
	image_data = (unsigned short *)malloc(pixel_count*sizeof(unsigned short));
	if(image_data == NULL)
	{
		fprintf(stderr,"Image Buffer malloc failed : (xsize %d x ysize %d) / (binx %d x biny%d).\n",
			Size_X,Size_Y,Bin_X,Bin_Y);
		return 1;
	}
	fprintf(stdout,"SetAcquisitionMode(1):single scan.\n");
	andor_retval = SetAcquisitionMode(1);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"SetAcquisitionMode failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	fprintf(stdout,"SetTriggerMode(0):internal trigger.\n");
	andor_retval = SetTriggerMode(0); 
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"SetTriggerMode failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	fprintf(stdout,"SetExposureTime(%.2f secs).\n",((float)Exposure_Length)/1000.0f);
	andor_retval = SetExposureTime(((float)Exposure_Length)/1000.0f);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"SetExposureTime failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	/* diddly
	fprintf(stdout,"SetShutterEx(1,0,0,0,0):internal auto, external auto.\n");
	andor_retval = SetShutterEx(1,0,0,0,0);*/ /* internal auto, external auto */
	/* diddly
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"SetShutterEx failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	*/
	fprintf(stdout,"StartAcquisition().\n");
	andor_retval = StartAcquisition();
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"StartAcquisition failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		return 1;
	}
	do
	{
		/* sleep a (very small (configurable)) bit */
		sleep_time.tv_sec = 0;
		sleep_time.tv_nsec = ONE_MILLISECOND_NS;
		nanosleep(&sleep_time,NULL);
		fprintf(stdout,"GetStatus().\n");
		andor_retval = GetStatus(&exposure_status);
		if(andor_retval != DRV_SUCCESS)
		{
			fprintf(stderr,"GetStatus failed : %u : %s.\n",andor_retval,
				Andor_Error_Code_To_String(andor_retval));
			/* diddly SetShutterEx(1,2,0,0,2); */ /* internal close, external close */
			SetShutter(1,2,0,0); /* close shutter */
			return 1;
		}
		fprintf(stdout,"GetStatus() returned exposure status %d.\n",exposure_status);		
	} while(exposure_status==DRV_ACQUIRING);
	fprintf(stdout,"GetNumberNewImages().\n");
	andor_retval = GetNumberNewImages(&first_new_image_index,&last_new_image_index);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"GetNumberNewImages failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		if(image_data != NULL)
			free(image_data);
		return 1;
	}
	fprintf(stdout,"GetNumberNewImages():first_new_image_index = %ld, last_new_image_index = %ld.\n",
		first_new_image_index,last_new_image_index);
	if(first_new_image_index != last_new_image_index)
	{
		fprintf(stderr,"Wrong number of new images(first = %ld, last = %ld).\n",first_new_image_index,
			last_new_image_index);
		AbortAcquisition(); /* abort started acquisition */
		if(image_data != NULL)
			free(image_data);
		return 1;
	}
	/* get data */
	fprintf(stdout,"GetAcquiredData16().\n");
	andor_retval = GetAcquiredData16((unsigned short*)image_data,pixel_count);
	if(andor_retval != DRV_SUCCESS)
	{
		fprintf(stderr,"GetAcquiredData16 failed : %u : %s.\n",andor_retval,
			Andor_Error_Code_To_String(andor_retval));
		if(image_data != NULL)
			free(image_data);
		return 1;
	}
	fprintf(stdout,"Save Data.\n");
	if(!Save(Do_Dark,Do_Exposure,image_data,pixel_count))
		fprintf(stderr,"Save failed.\n");
	fprintf(stdout,"Free Image Data.\n");
	if(image_data != NULL)
		free(image_data);
	fprintf(stdout,"Finished.\n");
	return 0;
}

/**
 * Save the acquired image.
 * @param do_dark A boolean, true if the data is a dark.
 * @param do_exposure A boolean, true if the data is an exposure.
 * @param image_data The image data, an allocated memory block containing pixel_count unsigned shorts.
 * @param pixel_count The number of pixels in image_data.
 * @see #Filename
 * @see #Current_Temperature
 * @see #CENTIGRADE_TO_KELVIN
 * @see #Size_X
 * @see #Bin_X
 * @see #Size_Y
 * @see #Bin_Y
 * @see #Gain
 * @see #Exposure_Length
 */
static int Save(int do_dark,int do_exposure,unsigned short *image_data,int pixel_count)
{
	fitsfile *fp = NULL;
	char buff[32]; /* fits_get_errstatus returns 30 chars max */
	long axes[2];
	double exposure_length_s;
	char exposure_start_time_string[64];
	int status=0,ncols,nrows,retval;

	retval = fits_create_file(&fp,Filename,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		return FALSE;
	}
	/* basic dimensions */
	ncols = Size_X/Bin_X;
	nrows = Size_Y/Bin_Y;
	axes[0] = ncols;
	axes[1] = nrows;
	retval = fits_create_img(fp,USHORT_IMG,2,axes,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fp,&status);
		return FALSE;
	}
	retval = fits_write_img(fp,TUSHORT,1,ncols*nrows,image_data,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fp,&status);
		return FALSE;
	}
	retval = fits_update_key(fp,TSTRING,"TRIGGER","INTERNAL",NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fp,&status);
		return FALSE;
	}
	if(do_dark)
	{
			retval = fits_update_key(fp,TSTRING,"OBSTYPE","DARK",NULL,&status);
			if(retval)
			{
				fits_get_errstatus(status,buff);
				fits_report_error(stderr,status);
				fits_close_file(fp,&status);
				return FALSE;
			}
	}
	if(do_exposure)
	{
			retval = fits_update_key(fp,TSTRING,"OBSTYPE","EXPOSE",NULL,&status);
			if(retval)
			{
				fits_get_errstatus(status,buff);
				fits_report_error(stderr,status);
				fits_close_file(fp,&status);
				return FALSE;
			}
	}
	/* diddly
	retval = fits_update_key(fp,TINT,"EMGAIN",&Gain,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fp,&status);
		return FALSE;
	}
	*/
	/* ccdxbin */
	retval = fits_update_key(fp,TINT,"CCDXBIN",&Bin_X,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fp,&status);
		return FALSE;
	}
	/* ccdybin */
	retval = fits_update_key(fp,TINT,"CCDYBIN",&Bin_Y,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fp,&status);
		return FALSE;
	}
	exposure_length_s = ((double)Exposure_Length)/1000.0;
	retval = fits_update_key_fixdbl(fp,"EXPTIME",exposure_length_s,6,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fp,&status);
		return FALSE;
	}
	retval = fits_update_key(fp,TINT,"VSSPEEDI",&VS_Speed_Index,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fp,&status);
		return FALSE;
	}
	retval = fits_update_key(fp,TINT,"HSSPEEDI",&HS_Speed_Index,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fp,&status);
		return FALSE;
	}
	retval = fits_update_key(fp,TINT,"ADCHANNE",&AD_Channel,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fp,&status);
		return FALSE;
	}
	retval = fits_update_key(fp,TINT,"FRMTRANS",&Frame_Transfer,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fp,&status);
		return FALSE;
	}
	retval = fits_update_key(fp,TINT,"BASECLMP",&Baseline_Clamp,NULL,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fp,&status);
		return FALSE;
	}
	retval = fits_update_key_fixdbl(fp,"CCDATEMP",Current_Temperature+CENTIGRADE_TO_KELVIN,6,
					"Cached temperature of CCD",&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		fits_close_file(fp,&status);
		return FALSE;
	}
/* close file */
	retval = fits_close_file(fp,&status);
	if(retval)
	{
		fits_get_errstatus(status,buff);
		fits_report_error(stderr,status);
		return FALSE;
	}
	return TRUE;
}

/**
 * Routine to parse command line arguments.
 * @param argc The number of arguments sent to the program.
 * @param argv An array of argument strings.
 * @see #Help
 * @see #Camera_Index
 * @see #Andor_Dir
 * @see #Data_Dir
 * @see #Temperature_Set
 * @see #Target_Temperature
 * @see #Size_X
 * @see #Size_Y
 * @see #Bin_X
 * @see #Bin_Y
 * @see #Do_Dark
 * @see #Do_Exposure
 * @see #Exposure_Length
 * @see #VS_Amplitude
 * @see #VS_Speed_Index
 * @see #Amplifier
 * @see #HS_Speed_Index
 * @see #AD_Channel
 * @see #Frame_Transfer
 * @see #Baseline_Clamp
 * @see #Filename
 */
static int Parse_Arguments(int argc, char *argv[])
{
	int i,retval;

	for(i=1;i<argc;i++)
	{
		if((strcmp(argv[i],"-ad_channel")==0)||(strcmp(argv[i],"-adc")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&AD_Channel);
				if(retval != 1)
				{
					fprintf(stderr,
						"Parse_Arguments:Parsing A/D channel %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:A/D Channel required (0=normal).\n");
				return FALSE;
			}
		}
		else if(strcmp(argv[i],"-amplifier")==0)
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&Amplifier);
				if(retval != 1)
				{
					fprintf(stderr,
						"Parse_Arguments:Parsing Amplifier %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:Amplifier required (0=EMCCD,1=CCD).\n");
				return FALSE;
			}
		}
	        else if((strcmp(argv[i],"-andor_dir")==0))
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
		else if((strcmp(argv[i],"-baseline_clamp")==0)||(strcmp(argv[i],"-bc")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&Baseline_Clamp);
				if(retval != 1)
				{
					fprintf(stderr,
						"Parse_Arguments:Parsing baseline clamp %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:baseline clamp requires an integer (0=on, 1=off).\n");
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
		else if((strcmp(argv[i],"-data_dir")==0))
		{
			if((i+1)<argc)
			{
				strncpy(Data_Dir,argv[i+1],MAX_STRING_LENGTH-1);
				Data_Dir[MAX_STRING_LENGTH-1] = '\0';
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:data_dir requires a directory.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-dark")==0)||(strcmp(argv[i],"-d")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&Exposure_Length);
				if(retval != 1)
				{
					fprintf(stderr,
						"Parse_Arguments:Parsing dark exposure length %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				Do_Dark = TRUE;
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:dark requires exposure length.\n");
				return FALSE;
			}

		}
		else if((strcmp(argv[i],"-expose")==0)||(strcmp(argv[i],"-e")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&Exposure_Length);
				if(retval != 1)
				{
					fprintf(stderr,
						"Parse_Arguments:Parsing exposure length %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				Do_Exposure = TRUE;
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:exposure requires exposure length.\n");
				return FALSE;
			}

		}
	        else if((strcmp(argv[i],"-filename")==0)||(strcmp(argv[i],"-f")==0))
		{
			if((i+1)<argc)
			{
				strncpy(Filename,argv[i+1],MAX_STRING_LENGTH-1);
				Filename[MAX_STRING_LENGTH-1] = '\0';
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:filename requires a string.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-frame_transfer_mode")==0)||(strcmp(argv[i],"-ft")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&Frame_Transfer);
				if(retval != 1)
				{
					fprintf(stderr,
						"Parse_Arguments:Parsing frame transfer mode %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:frame transfer mode requires a number:0=off, 1=on.\n");
				return FALSE;
			}

		}
		else if((strcmp(argv[i],"-help")==0)||(strcmp(argv[i],"-h")==0))
		{
			Help();
			exit(0);
		}
		else if((strcmp(argv[i],"-horizontal_shift_speed_index")==0)||(strcmp(argv[i],"-hssi")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&HS_Speed_Index);
				if(retval != 1)
				{
					fprintf(stderr,
						"Parse_Arguments:Parsing Horizontal Shift Speed Index %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:Horizontal Shift Speed Index required (0=fastest).\n");
				return FALSE;
			}
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
		else if((strcmp(argv[i],"-vertical_shift_amplitude")==0)||(strcmp(argv[i],"-vsa")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&VS_Amplitude);
				if(retval != 1)
				{
					fprintf(stderr,
						"Parse_Arguments:Parsing VS Amplitude  %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:VS Amplitude required (0=normal, 1..4 enhanced).\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-vertical_shift_speed_index")==0)||(strcmp(argv[i],"-vssi")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&VS_Speed_Index);
				if(retval != 1)
				{
					fprintf(stderr,
						"Parse_Arguments:Parsing Vertical Shift Speed Index %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:Vertical Shift Speed Index required (0=fastest).\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-xsize")==0)||(strcmp(argv[i],"-xs")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&Size_X);
				if(retval != 1)
				{
					fprintf(stderr,
						"Parse_Arguments:Parsing X Size %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:size required.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-ysize")==0)||(strcmp(argv[i],"-ys")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&Size_Y);
				if(retval != 1)
				{
					fprintf(stderr,
						"Parse_Arguments:Parsing Y Size %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:size required.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-xbin")==0)||(strcmp(argv[i],"-xb")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&Bin_X);
				if(retval != 1)
				{
					fprintf(stderr,
						"Parse_Arguments:Parsing X Bin %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:bin required.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-ybin")==0)||(strcmp(argv[i],"-yb")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&Bin_Y);
				if(retval != 1)
				{
					fprintf(stderr,
						"Parse_Arguments:Parsing Y Bin %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:bin required.\n");
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
	fprintf(stdout,"Test Exposure:Help.\n");
	fprintf(stdout,"This program calls Andor functions to take an exposure.\n");
	fprintf(stdout,"The exposure is saved to disk using CFITSIO.\n");
	fprintf(stdout,"\t[-target_temperature <temperature>][-gain <gain>][-camera_index <0..n>]\n");
	fprintf(stdout,"\t[-xs[ize] <no. of pixels>][-ys[ize] <no. of pixels>]\n");
	fprintf(stdout,"\t[-xb[in] <binning factor>][-yb[in] <binning factor>]\n");
	fprintf(stdout,"\t-e[xpose] <exposure length ms>\n");
	fprintf(stdout,"\t-f[ilename] <FITS filename>\n");
	fprintf(stdout,"\t[-andor_dir <directory>][-data_dir <directory>]\n");
	fprintf(stdout,"\t[-vsa|-vertical_shift_amplitude <0..4>]\n");
	fprintf(stdout,"\t[-vssi|-vertical_shift_speed_index <n>]\n");
	fprintf(stdout,"\t[-hssi|-horizontal_shift_speed_index <n>]\n");
	fprintf(stdout,"\t[-frame_transfer_mode|-ft <0|1>]\n");
	fprintf(stdout,"\t[-baseline_clamp|-bc <0|1>]\n");
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
** $Log: test_exposure_andor_lowlevel.c,v $
** Revision 1.1  2012/08/14 11:26:32  cjm
** Initial revision
**
*/
