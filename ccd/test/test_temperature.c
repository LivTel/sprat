/* test_temperature.c
** $HeadURL$
** Test temperature.
*/
/**
 * This program tests the temperature code.
 * @author $Author$
 * @version $Revision$
 */
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>
#include "ccd_global.h"
#include "ccd_temperature.h"

/* hash definitions */
/**
 * Default temperature to set the CCD to.
 */
#define DEFAULT_TEMPERATURE	(0.0)

/* enums */
/**
 * Enumeration determining which command this program executes. One of:
 * <ul>
 * <li>COMMAND_ID_NONE
 * <li>COMMAND_ID_GET
 * <li>COMMAND_ID_ON
 * <li>COMMAND_ID_OFF
 * <li>COMMAND_ID_SET
 * </ul>
 */
enum COMMAND_ID
{
	COMMAND_ID_NONE=0,COMMAND_ID_GET,COMMAND_ID_ON,COMMAND_ID_OFF,COMMAND_ID_SET
};

/* internal variables */
/**
 * Revision control system identifier.
 */
static char rcsid[] = "$Id$";
/**
 * Temperature to set the CCD to.
 * @see #DEFAULT_TEMPERATURE
 */
static double Temperature = DEFAULT_TEMPERATURE;
/**
 * Which command to call.
 * @see #COMMAND_ID
 */
static enum COMMAND_ID Command = COMMAND_ID_NONE;
/**
 * Filename for configuration dir. 
 */
static char *Config_Dir = "/usr/local/andor/etc";
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

/**
 * Main program.
 * @param argc The number of arguments to the program.
 * @param argv An array of argument strings.
 * @return This function returns 0 if the program succeeds, and a positive integer if it fails.
 * @see #Call_Setup_Shutdown
 * @see #Call_Setup_Startup
 * @see #Command
 * @see #COMMAND_ID
 * @see #Config_Dir
 * @see ../cdocs/ccd_global.html#CCD_Global_Error
 * @see ../cdocs/ccd_setup.html#CCD_Setup_Startup
 * @see ../cdocs/ccd_setup.html#CCD_Setup_Shutdown
 * @see ../cdocs/ccd_setup.html#CCD_Setup_Config_Directory_Set
 * @see ../cdocs/ccd_temperature.html#CCD_Temperature_Cooler_On
 * @see ../cdocs/ccd_temperature.html#CCD_Temperature_Cooler_Off
 * @see ../cdocs/ccd_temperature.html#CCD_Temperature_Set
 * @see ../cdocs/ccd_temperature.html#CCD_Temperature_Get
 * @see ../cdocs/ccd_temperature.html#CCD_Temperature_Status_To_String
 * @see ../../cdocs/tiptilt_ccd_driver.html#TIPTILT_CCD_TEMPERATURE_STATUS
 */
int main(int argc, char *argv[])
{
	int i,retval,value;
	double temperature;
	enum CCD_TEMPERATURE_STATUS temperature_status;

/* parse arguments */
	fprintf(stdout,"Parsing Arguments.\n");
	if(!Parse_Arguments(argc,argv))
		return 1;
/* set text/interface options */
	CCD_Global_Set_Log_Handler_Function(CCD_Global_Log_Handler_Stdout);
	if(!CCD_Setup_Config_Directory_Set(Config_Dir))
	{
		CCD_Global_Error();
		return 2;
	}
	if(Call_Setup_Startup)
	{
		fprintf(stdout,"CCD_Setup_Startup\n");
		retval = CCD_Setup_Startup(0,TRUE,0);
		if(retval == FALSE)
		{
			CCD_Global_Error();
			return 2;
		}
	}
	/* do command */
	switch(Command)
	{
		case COMMAND_ID_ON:
			retval = CCD_Temperature_Cooler_On();
			break;
		case COMMAND_ID_OFF:
			retval = CCD_Temperature_Cooler_Off();
			break;
		case COMMAND_ID_SET:
			retval = CCD_Temperature_Set(Temperature);
			break;
		case COMMAND_ID_GET:
			retval = CCD_Temperature_Get(&temperature,&temperature_status);
			if(retval == TRUE)
			{
				fprintf(stdout,"Temperature:%lf.\n",temperature);
				fprintf(stdout,"Temperature Status:%s (%d).\n",
					CCD_Temperature_Status_To_String(temperature_status),temperature_status);
				
			}
			break;
		default:
			fprintf(stderr,"Unknown/No command specified (%d).\n",Command);
			return 3;
			break;
	}
	if(retval == FALSE)
	{
		CCD_Global_Error();
		return 2;
	}
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
	fprintf(stdout,"test_temperature completed.\n");
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
	fprintf(stdout,"Test Temperature:Help.\n");
	fprintf(stdout,"This program calls Andor_Setup_Startup to set up the controller dimensions.\n");
	fprintf(stdout,"test_temperature \n");
	fprintf(stdout,"\t[-co[nfig_dir] <directory>]\n");
	fprintf(stdout,"\t[-l[og_level] <verbosity>][-h[elp]]\n");
	fprintf(stdout,"\t[-s[et_temperature] <temperature>]\n");
	fprintf(stdout,"\t[-g[et_temperature]]\n");
	fprintf(stdout,"\t[-on]\n");
	fprintf(stdout,"\t[-off]\n");
	fprintf(stdout,"\t[-nostartup][-noshutdown]\n");
	fprintf(stdout,"\n");
	fprintf(stdout,"\t-help prints out this message and stops the program.\n");
	fprintf(stdout,"\n");
	fprintf(stdout,"\t<config dir> should be a valid configuration directory.\n");
	fprintf(stdout,"\t-noshutdown stops this invocation calling Andor_Setup_Shutdown,\n"
		"\t\twhich may close down the temperature control sub-system..\n");
}

/**
 * Routine to parse command line arguments.
 * @param argc The number of arguments sent to the program.
 * @param argv An array of argument strings.
 * @see #Help
 * @see #Temperature
 * @see #Config_Dir
 * @see #Command
 * @see #Call_Setup_Shutdown
 * @see #Call_Setup_Startup
 * @see ../cdocs/ccd_global.html#CCD_Global_Set_Log_Filter_Function
 * @see ../cdocs/ccd_global.html#CCD_Global_Set_Log_Filter_Level
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
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:config filename required.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-get")==0)||(strcmp(argv[i],"-g")==0))
		{
			Command = COMMAND_ID_GET;
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
		else if((strcmp(argv[i],"-on")==0))
		{
			Command = COMMAND_ID_ON;
		}
		else if((strcmp(argv[i],"-off")==0))
		{
			Command = COMMAND_ID_OFF;
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
		else if((strcmp(argv[i],"-set_temperature")==0)||(strcmp(argv[i],"-s")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%lf",&Temperature);
				if(retval != 1)
				{
					fprintf(stderr,
						"Parse_Arguments:Parsing temperature %s failed.\n",
						argv[i+1]);
					return FALSE;
				}
				Command = COMMAND_ID_SET;
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
