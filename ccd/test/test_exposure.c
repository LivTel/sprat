/* test_exposure.c
 * $HeadURL$
 */
/**
 * This program tests the exposure code.
 * <pre>
 * </pre>
 * @author $Author$
 * @version $Revision$
 */

#include <stdio.h>
#include <string.h>
#include <time.h>
#include "fitsio.h"
#include "ccd_exposure.h"
#include "ccd_global.h"
#include "ccd_setup.h"

/* hash definitions */
/**
 * Default temperature to set the CCD to.
 */
#define DEFAULT_TEMPERATURE	(-20.0)
/**
 * Default number of columns in the CCD.
 */
#define DEFAULT_SIZE_X		(1024)
/**
 * Default number of rows in the CCD.
 */
#define DEFAULT_SIZE_Y		(255)
/**
 * Default Andor config directory used by Andor installer.
 */
#define DEFAULT_CONFIG_DIR      "/usr/local/etc/andor"

/* enums */
/**
 * Enumeration determining which command this program executes. One of:
 * <ul>
 * <li>COMMAND_ID_NONE
 * <li>COMMAND_ID_BIAS
 * <li>COMMAND_ID_DARK
 * <li>COMMAND_ID_EXPOSURE
 * </ul>
 */
enum COMMAND_ID
{
	COMMAND_ID_NONE=0,COMMAND_ID_BIAS,COMMAND_ID_DARK,COMMAND_ID_EXPOSURE
};

/* internal variables */
/**
 * Revision control system identifier.
 */
static char rcsid[] = "$Id$";
/**
 * Whether to set the temperature or not.
 */
static int Set_Temperature = FALSE;
/**
 * Temperature to set the CCD to.
 * @see #DEFAULT_TEMPERATURE
 */
static double Temperature = DEFAULT_TEMPERATURE;
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
 * Window flags specifying which window to use.
 */
static int Window_Flags = 0;
/**
 * Window data.
 * @see ../../cdocs/ccd_setup.html#CCD_Setup_Window_Struct
 */
static struct CCD_Setup_Window_Struct Window;

/**
 * Which type ef exposure command to call.
 * @see #COMMAND_ID
 */
static enum COMMAND_ID Command = COMMAND_ID_NONE;
/**
 * If doing a dark or exposure, the exposure length.
 */
static int Exposure_Length = 0;
/**
 * Boolean, whether to use the recomended fastest vertical shift speed index (TRUE), or try and override
 * using VS_Speed_Index.
 * @see #VS_Speed_Index
 */
static int Use_Recommended_VS = TRUE;
/**
 * The vertical shift speed index to use, if Use_Recommended_VS is FALSE.
 * @see #Use_Recommended_VS
 */
static int VS_Speed_Index = 0;
/**
 * Filename to store resultant fits image in. 
 */
static char *Fits_Filename = NULL;
/**
 * Filename for configuration directory. 
 */
static char *Config_Dir = NULL;
/**
 * Boolean whether to call CCD_Setup_Shutdown at the end of the test.
 * This may switch off the cooler. Default value is TRUE.
 */
static int Call_Setup_Shutdown = TRUE;
/**
 * Boolean whether to call CCD_Setup_Startup at the start of the test.
 * This may switch on the cooler. Default value is TRUE.
 */
static int Call_Setup_Startup = TRUE;

/* internal routines */
static int Parse_Arguments(int argc, char *argv[]);
static void Help(void);
static int Test_Save_Fits_Headers(int exposure_time,int ncols,int nrows,char *filename);
static void Test_Fits_Header_Error(int status);

/**
 * Main program.
 * @param argc The number of arguments to the program.
 * @param argv An array of argument strings.
 * @return This function returns 0 if the program succeeds, and a positive integer if it fails.
 * @see #Temperature
 * @see #Set_Temperature
 * @see #Call_Setup_Shutdown
 * @see #Call_Setup_Startup
 * @see #Size_X
 * @see #Size_Y
 * @see #Bin_X
 * @see #Bin_Y
 * @see #Window_Flags
 * @see #Window
 * @see #Command
 * @see #Exposure_Length
 * @see #Fits_Filename
 * @see #Config_Dir
 * @see #Use_Recommended_VS
 * @see #VS_Speed_Index
 * @see #DEFAULT_CONFIG_DIR
 * @see ../cdocs/ccd_setup.html#CCD_Setup_Config_Directory_Set
 */
int main(int argc, char *argv[])
{
	struct Fits_Header_Struct fits_header;
	struct timespec start_time;
	unsigned short *image_buffer = NULL;
	size_t image_buffer_length = 0;
	char command_buffer[256];
	char fits_filename[256];
	int i,retval,value;

/* parse arguments */
	fprintf(stdout,"Setting up default config dir.\n");
	if(!CCD_Setup_Config_Directory_Set(DEFAULT_CONFIG_DIR))
		return 1;
	fprintf(stdout,"Parsing Arguments.\n");
	if(!Parse_Arguments(argc,argv))
		return 1;
/* set text/interface options */
	CCD_Global_Set_Log_Handler_Function(CCD_Global_Log_Handler_Stdout);
	if(Call_Setup_Startup)
	{
		fprintf(stdout,"Calling CCD_Setup_Startup:Use_Recommended_VS = %d, VS_Speed_Index = %d\n",
			Use_Recommended_VS,VS_Speed_Index);
		retval = CCD_Setup_Startup(Use_Recommended_VS,VS_Speed_Index);
		if(retval == FALSE)
		{
			CCD_Global_Error();
			return 2;
		}
	}
	/* temperature control */
	if(Set_Temperature)
	{
		retval = CCD_Temperature_Set(Temperature);
		if(retval == FALSE)
		{
			CCD_Global_Error();
			return 2;
		}
	}
/* call CCD_Setup_Dimensions */
	fprintf(stdout,"Calling CCD_Setup_Dimensions:\n");
	fprintf(stdout,"Chip Size:(%d,%d)\n",Size_X,Size_Y);
	fprintf(stdout,"Binning:(%d,%d)\n",Bin_X,Bin_Y);
	fprintf(stdout,"Window Flags:%d\n",Window_Flags);
	if(Window_Flags > 0)
	{
		fprintf(stdout,"Window:[xs=%d,xe=%d,ys=%d,ye=%d]\n",Window.X_Start,Window.X_End,
			Window.Y_Start,Window.Y_End);
	}
	if(!CCD_Setup_Dimensions(Size_X,Size_Y,Bin_X,Bin_Y,Window_Flags,Window))
	{
		CCD_Global_Error();
		return 3;
	}
	fprintf(stdout,"CCD_Setup_Dimensions completed\n");
	/* allocate buffer for image */
	image_buffer_length = CCD_Setup_Get_NCols()*CCD_Setup_Get_NRows();
	image_buffer = (unsigned short*)malloc(image_buffer_length*sizeof(unsigned short));
	if(image_buffer == NULL)
	{
		fprintf(stderr,"Failed to allocate image buffer of length %ld.\n",image_buffer_length);
		return 4;
	}
	/* fits header setup */
	fprintf(stdout,"Setting up FITS headers.\n");
	if(!CCD_Fits_Header_Initialise(&fits_header))
	{
		CCD_Global_Error();
		return 3;
	}
	fprintf(stdout,"CCD_Fits_Header_Initialise completed.\n");
/* do command */
	start_time.tv_sec = 0;
	start_time.tv_nsec = 0;
	switch(Command)
	{
		case COMMAND_ID_BIAS:
			fprintf(stdout,"Calling CCD_Exposure_Bias.\n");
			retval = CCD_Exposure_Bias(fits_header,Fits_Filename);
			break;
		case COMMAND_ID_DARK:
			fprintf(stdout,"Calling CCD_Exposure_Expose with open_shutter FALSE.\n");
			retval = CCD_Exposure_Expose(FALSE,start_time,Exposure_Length,
						     fits_header,Fits_Filename);
			break;
		case COMMAND_ID_EXPOSURE:
			fprintf(stdout,"Calling CCD_Exposure_Expose with open_shutter TRUE.\n");
			retval = CCD_Exposure_Expose(TRUE,start_time,Exposure_Length,
						     fits_header,Fits_Filename);
			break;
		case COMMAND_ID_NONE:
			fprintf(stdout,"Please select a command to execute (-bias | -dark | -expose).\n");
			Help();
			return 5;
	}
	if(retval == FALSE)
	{
		CCD_Global_Error();
		return 6;
	}
	fprintf(stdout,"Command Completed.\n");
/* close  */
	if(Call_Setup_Shutdown)
	{
		fprintf(stdout,"CCD_Setup_Shutdown\n");
		retval = CCD_Setup_Shutdown();
		if(retval == FALSE)
		{
			CCD_Global_Error();
			return 2;
		}
	}
	return 0;
}

/* -----------------------------------------------------------------------------
**      Internal routines
** ----------------------------------------------------------------------------- */
/**
 * Help routine.
 */
static void Help(void)
{
	fprintf(stdout,"Test Exposure:Help.\n");
	fprintf(stdout,"This program calls CCD_Setup_Dimensions to set up the controller dimensions.\n");
	fprintf(stdout,"It then calls either CCD_Exposure_Bias or CCD_Exposure_Expose to perform an exposure.\n");
	fprintf(stdout,"test_exposure \n");
	fprintf(stdout,"\t[-co[nfig_dir] <directory>]\n");
	fprintf(stdout,"\t[-l[og_level] <verbosity>][-h[elp]]\n");
	fprintf(stdout,"\t[-temperature <temperature>]\n");
	fprintf(stdout,"\t[-xs[ize] <no. of pixels>][-ys[ize] <no. of pixels>]\n");
	fprintf(stdout,"\t[-xb[in] <binning factor>][-yb[in] <binning factor>]\n");
	fprintf(stdout,"\t[-w[indow] <xstart> <ystart> <xend> <yend>]\n");
	fprintf(stdout,"\t[-f[its_filename] <filename>]\n");
	fprintf(stdout,"\t[-b[ias]][-d[ark] <exposure length>][-e[xpose] <exposure length>]\n");
	fprintf(stdout,"\t[-vs_speed_index|-vsi <vs speed index>]\n");
	fprintf(stdout,"\t[-nostartup][-noshutdown]\n");
	fprintf(stdout,"\n");
	fprintf(stdout,"\t-help prints out this message and stops the program.\n");
	fprintf(stdout,"\n");
	fprintf(stdout,"\t<filename> should be a valid filename.\n");
	fprintf(stdout,"\t<temperature> should be a valid double, a temperature in degrees Celcius.\n");
	fprintf(stdout,"\t<exposure length> is a positive integer in milliseconds.\n");
	fprintf(stdout,"\t<no. of pixels> and <binning factor> is a positive integer.\n");
	fprintf(stdout,"\t<vs speed index> is a index into the list of vertical shift speeds.\n");
	fprintf(stdout,"\t-noshutdown stops this invocation calling Andor_Setup_Shutdown,\n"
		"\t\twhich may close down the temperature control sub-system..\n");
}

/**
 * Routine to parse command line arguments.
 * @param argc The number of arguments sent to the program.
 * @param argv An array of argument strings.
 * @see #Help
 * @see #Call_Setup_Shutdown
 * @see #Call_Setup_Startup
 * @see #Set_Temperature
 * @see #Temperature
 * @see #Size_X
 * @see #Size_Y
 * @see #Bin_X
 * @see #Bin_Y
 * @see #Window_Flags
 * @see #Window
 * @see #Command
 * @see #Exposure_Length
 * @see #Fits_Filename
 * @see #Config_Dir
 * @see #Use_Recommended_VS
 * @see #VS_Speed_Index
 * @see ../cdocs/ccd_global.html#CCD_Global_Set_Log_Filter_Function
 * @see ../cdocs/ccd_global.html#CCD_Global_Set_Log_Filter_Level
 * @see ../cdocs/ccd_setup.html#CCD_Setup_Config_Directory_Set
 */
static int Parse_Arguments(int argc, char *argv[])
{
	int i,retval,log_level;

	for(i=1;i<argc;i++)
	{
		if((strcmp(argv[i],"-config_dir")==0)||(strcmp(argv[i],"-co")==0))
		{
			if((i+1)<argc)
			{
				Config_Dir = argv[i+1];
				if(!CCD_Setup_Config_Directory_Set(Config_Dir))
					return FALSE;
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:config dir name required.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-bias")==0)||(strcmp(argv[i],"-b")==0))
		{
			Command = COMMAND_ID_BIAS;
		}
		else if((strcmp(argv[i],"-dark")==0)||(strcmp(argv[i],"-d")==0))
		{
			Command = COMMAND_ID_DARK;
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&Exposure_Length);
				if(retval != 1)
				{
					fprintf(stderr,"Parse_Arguments:Parsing exposure length %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
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
			Command = COMMAND_ID_EXPOSURE;
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
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:exposure requires exposure length.\n");
				return FALSE;
			}

		}
		else if((strcmp(argv[i],"-fits_filename")==0)||(strcmp(argv[i],"-f")==0))
		{
			if((i+1)<argc)
			{
				Fits_Filename = argv[i+1];
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:filename required.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-help")==0)||(strcmp(argv[i],"-h")==0))
		{
			Help();
			exit(0);
		}
		else if((strcmp(argv[i],"-noshutdown")==0))
		{
			Call_Setup_Shutdown = FALSE;
		}
		else if((strcmp(argv[i],"-nostartup")==0))
		{
			Call_Setup_Startup = FALSE;
		}
		else if(strcmp(argv[i],"-temperature")==0)
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%lf",&Temperature);
				if(retval != 1)
				{
					fprintf(stderr,"Parse_Arguments:Parsing temperature %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				Set_Temperature = TRUE;
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:temperature required.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-log_level")==0)||(strcmp(argv[i],"-l")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&log_level);
				if(retval != 1)
				{
					fprintf(stderr,"Parse_Arguments:Parsing log level %s failed.\n",argv[i+1]);
					return FALSE;
				}
				CCD_Global_Set_Log_Filter_Function(CCD_Global_Log_Filter_Level_Absolute);
				CCD_Global_Set_Log_Filter_Level(log_level);
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:Log Level requires a level.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-vs_speed_index")==0)||(strcmp(argv[i],"-vsi")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&VS_Speed_Index);
				if(retval != 1)
				{
					fprintf(stderr,"Parse_Arguments:Parsing vertical speed index %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				i++;
				Use_Recommended_VS = FALSE;
				
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:vertical speed index requires a positive number.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-window")==0)||(strcmp(argv[i],"-w")==0))
		{
			if((i+4)<argc)
			{
				
				Window_Flags = TRUE;
				/* x start */
				retval = sscanf(argv[i+1],"%d",&(Window.X_Start));
				if(retval != 1)
				{
					fprintf(stderr,"Parse_Arguments:Parsing Window Start X (%s) failed.\n",
						argv[i+1]);
					return FALSE;
				}
				/* y start */
				retval = sscanf(argv[i+2],"%d",&(Window.Y_Start));
				if(retval != 1)
				{
					fprintf(stderr,"Parse_Arguments:Parsing Window Start Y (%s) failed.\n",
						argv[i+2]);
					return FALSE;
				}
				/* x end */
				retval = sscanf(argv[i+3],"%d",&(Window.X_End));
				if(retval != 1)
				{
					fprintf(stderr,"Parse_Arguments:Parsing Window End X (%s) failed.\n",
						argv[i+3]);
					return FALSE;
				}
				/* y end */
				retval = sscanf(argv[i+4],"%d",&(Window.Y_End));
				if(retval != 1)
				{
					fprintf(stderr,"Parse_Arguments:Parsing Window End Y (%s) failed.\n",
						argv[i+4]);
					return FALSE;
				}
				i+= 4;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:-window requires 4 argument:%d supplied.\n",argc-i);
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
					fprintf(stderr,"Parse_Arguments:Parsing X Size %s failed.\n",
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
					fprintf(stderr,"Parse_Arguments:Parsing Y Size %s failed.\n",
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
					fprintf(stderr,"Parse_Arguments:Parsing X Bin %s failed.\n",argv[i+1]);
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
					fprintf(stderr,"Parse_Arguments:Parsing Y Bin %s failed.\n",argv[i+1]);
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
