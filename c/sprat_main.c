/* sprat_main.c
** $HeadURL$
*/
/**
 * SPRAT C server main program.
 * @author $Author$
 * @version $Revision$
 */
#include <signal.h> /* signal handling */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "log_udp.h"

#include "command_server.h"

#include "ccd_exposure.h"
#include "ccd_fits_filename.h"
#include "ccd_global.h"
#include "ccd_setup.h"
#include "ccd_temperature.h"

#include "sprat_config.h"
#include "sprat_global.h"
#include "sprat_server.h"

/* internal variables */
/**
 * Revision control system identifier.
 */
static char rcsid[] = "$Id$";

/* internal routines */
static int Sprat_Initialise_Signal(void);
static int Sprat_Initialise_Logging(void);
static int Sprat_Startup_CCD(void);
static int Sprat_Shutdown_CCD(void);
static int Parse_Arguments(int argc, char *argv[]);
static void Help(void);

/* ------------------------------------------------------------------
** External functions 
** ------------------------------------------------------------------ */
/**
 * Main program.
 * @param argc The number of arguments to the program.
 * @param argv An array of argument strings.
 * @return This function returns 0 if the program succeeds, and a positive integer if it fails.
 */
int main(int argc, char *argv[])
{
	int retval;

/* parse arguments */
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("main","sprat_main.c","main",LOG_VERBOSITY_VERY_TERSE,"STARTUP","Parsing Arguments.");
#endif
	if(!Parse_Arguments(argc,argv))
		return 1;
	/* initialise signal handling */
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("main","sprat_main.c","main",LOG_VERBOSITY_VERY_TERSE,"STARTUP","Sprat_Initialise_Signal.");
#endif
	retval = Sprat_Initialise_Signal();
	if(retval == FALSE)
	{
		Sprat_Global_Error("main","sprat_main.c","main",LOG_VERBOSITY_VERY_TERSE,"STARTUP");
		return 4;
	}
	/* initialise/load configuration */
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("main","sprat_main.c","main",LOG_VERBOSITY_VERY_TERSE,"STARTUP","Sprat_Config_Load.");
#endif
	retval = Sprat_Config_Load(Sprat_Global_Get_Config_Filename());
	if(retval == FALSE)
	{
		Sprat_Global_Error("main","sprat_main.c","main",LOG_VERBOSITY_VERY_TERSE,"STARTUP");
		return 2;
	}
	/* set logging options */
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("main","sprat_main.c","main",LOG_VERBOSITY_VERY_TERSE,"STARTUP",
			       "Sprat_Initialise_Logging.");
#endif
	retval = Sprat_Initialise_Logging();
	if(retval == FALSE)
	{
		Sprat_Global_Error("main","sprat_main.c","main",LOG_VERBOSITY_VERY_TERSE,"STARTUP");
		return 4;
	}
	/* initialise connection to the CCD */
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("main","sprat_main.c","main",LOG_VERBOSITY_VERY_TERSE,"STARTUP",
			       "Sprat_Startup_CCD.");
#endif
	retval = Sprat_Startup_CCD();
	if(retval == FALSE)
	{
		Sprat_Global_Error("main","sprat_main.c","main",LOG_VERBOSITY_VERY_TERSE,"STARTUP");
		return 3;
	}
	/* initialise command server */
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("main","sprat_main.c","main",LOG_VERBOSITY_VERY_TERSE,"STARTUP",
			       "Sprat_Server_Initialise.");
#endif
	retval = Sprat_Server_Initialise();
	if(retval == FALSE)
	{
		Sprat_Global_Error("main","sprat_main.c","main",LOG_VERBOSITY_VERY_TERSE,"STARTUP");
		/* ensure CCD is warmed up */
		Sprat_Shutdown_CCD();
		return 4;
	}
	/* start server */
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("main","sprat_main.c","main",LOG_VERBOSITY_VERY_TERSE,"STARTUP",
			       "Sprat_Server_Start.");
#endif
	retval = Sprat_Server_Start();
	if(retval == FALSE)
	{
		Sprat_Global_Error("main","sprat_main.c","main",LOG_VERBOSITY_VERY_TERSE,"STARTUP");
		/* ensure CCD is warmed up */
		Sprat_Shutdown_CCD();
		return 4;
	}
	/* shutdown */
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("main","sprat_main.c","main",LOG_VERBOSITY_VERY_TERSE,"STARTUP",
			       "Sprat_Shutdown_CCD");
#endif
	retval = Sprat_Shutdown_CCD();
	if(retval == FALSE)
	{
		Sprat_Global_Error("main","sprat_main.c","main",LOG_VERBOSITY_VERY_TERSE,"STARTUP");
		return 2;
	}
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("main","sprat_main.c","main",LOG_VERBOSITY_VERY_TERSE,"STARTUP",
			       "sprat completed.");
#endif
	return 0;
}

/* -----------------------------------------------------------------------------
**      Internal routines
** ----------------------------------------------------------------------------- */
/**
 * Initialise signal handling. Switches off default "Broken pipe" error, so client
 * crashes do NOT kill the AG control system.
 * Don't use Logging here, this is called pre-logging.
 */
static int Sprat_Initialise_Signal(void)
{
	struct sigaction sig_action;

	/* old code
	signal(SIGPIPE, SIG_IGN);
	*/
	sig_action.sa_handler = SIG_IGN;
	sig_action.sa_flags = 0;
	sigemptyset(&sig_action.sa_mask);
	sigaction(SIGPIPE,&sig_action,NULL);
	return TRUE;
}

/**
 * Setup logging. Get directory name from config "logging.directory_name".
 * Get UDP logging config. Setup log handlers for Sprat software and subsystems.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see sprat_global.html#Sprat_Global_Log_Set_Directory
 * @see sprat_global.html#Sprat_Global_Log_Set_Root
 * @see sprat_global.html#Sprat_Global_Log_Set_Error_Root
 * @see sprat_global.html#Sprat_Global_Log_Set_UDP
 * @see sprat_global.html#Sprat_Global_Add_Log_Handler_Function
 * @see sprat_global.html#Sprat_Global_Log_Handler_Log_Hourly_File
 * @see sprat_global.html#Sprat_Global_Log_Handler_Log_UDP
 * @see sprat_global.html#Sprat_Global_Call_Log_Handlers
 * @see sprat_global.html#Sprat_Global_Set_Log_Filter_Function
 * @see sprat_global.html#Sprat_Global_Log_Filter_Level_Absolute
 * @see sprat_config.html#Sprat_Config_Get_Boolean
 * @see sprat_config.html#Sprat_Config_Get_Integer
 * @see sprat_config.html#Sprat_Config_Get_String
 * @see ../ccd/cdocs/ccd_global.html#CCD_Global_Set_Log_Handler_Function
 * @see ../ccd/cdocs/ccd_global.html#CCD_Global_Set_Log_Filter_Function
 * @see ../ccd/cdocs/ccd_global.html#CCD_Global_Log_Filter_Level_Absolute
 * @see ../../commandserver/cdocs/command_server.html#Command_Server_Set_Log_Handler_Function
 * @see ../../commandserver/cdocs/command_server.html#Command_Server_Set_Log_Filter_Function
 * @see ../../commandserver/cdocs/command_server.html#Command_Server_Log_Filter_Level_Absolute
 */
static int Sprat_Initialise_Logging(void)
{
	char *log_directory = NULL;
	char *filename_root = NULL;
	char *hostname = NULL;
	int retval,port_number,active;

	/* don't log yet - not fully setup yet */
	/* log directory */
	if(!Sprat_Config_Get_String("logging.directory_name",&log_directory))
	{
		Sprat_Global_Error_Number = 17;
		sprintf(Sprat_Global_Error_String,"Sprat_Initialise_Logging:"
			"Failed to get logging directory (logging.directory_name).");
		return FALSE;
	}
	if(!Sprat_Global_Log_Set_Directory(log_directory))
	{
		if(log_directory != NULL)
			free(log_directory);
		return FALSE;
	}
	if(log_directory != NULL)
		free(log_directory);
	/* log filename root */
	if(!Sprat_Config_Get_String("logging.root.log",&filename_root))
	{
		Sprat_Global_Error_Number = 19;
		sprintf(Sprat_Global_Error_String,"Sprat_Initialise_Logging:"
			"Failed to get log root filename (logging.root.log).");
		return FALSE;
	}
	if(!Sprat_Global_Log_Set_Root(filename_root))
	{
		if(filename_root != NULL)
			free(filename_root);
		return FALSE;
	}
	if(filename_root != NULL)
		free(filename_root);
	/* error filename root */
	if(!Sprat_Config_Get_String("logging.root.error",&filename_root))
	{
		Sprat_Global_Error_Number = 23;
		sprintf(Sprat_Global_Error_String,"Sprat_Initialise_Logging:"
			"Failed to get error root filename.");
		return FALSE;
	}
	if(!Sprat_Global_Log_Set_Error_Root(filename_root))
	{
		if(filename_root != NULL)
			free(filename_root);
		return FALSE;
	}
	if(filename_root != NULL)
		free(filename_root);
	/* setup log_udp */
	if(!Sprat_Config_Get_Boolean("logging.udp.active",&active))
	{
		Sprat_Global_Error_Number = 20;
		sprintf(Sprat_Global_Error_String,"Sprat_Initialise_Logging:"
			"Failed to get log_udp active.");
		return FALSE;
	}
	if(!Sprat_Config_Get_Integer("logging.udp.port_number",&port_number))
	{
		Sprat_Global_Error_Number = 21;
		sprintf(Sprat_Global_Error_String,"Sprat_Initialise_Logging:"
			"Failed to get log_udp port_number.");
		return FALSE;
	}
	if(!Sprat_Config_Get_String("logging.udp.hostname",&hostname))
	{
		Sprat_Global_Error_Number = 22;
		sprintf(Sprat_Global_Error_String,"Sprat_Initialise_Logging:"
			"Failed to get log_udp hostname.");
		return FALSE;
	}
	if(!Sprat_Global_Log_Set_UDP(active,hostname,port_number))
	{
		if(hostname != NULL)
			free(hostname);
		return FALSE;
	}
	if(hostname != NULL)
		free(hostname);
	/* Sprat */
	if(!Sprat_Global_Add_Log_Handler_Function(Sprat_Global_Log_Handler_Log_Hourly_File))
		return FALSE;
	if(!Sprat_Global_Add_Log_Handler_Function(Sprat_Global_Log_Handler_Log_UDP))
		return FALSE;
	Sprat_Global_Set_Log_Filter_Function(Sprat_Global_Log_Filter_Level_Absolute);
	/* CCD */
	CCD_Global_Set_Log_Handler_Function(Sprat_Global_Call_Log_Handlers);
	CCD_Global_Set_Log_Filter_Function(CCD_Global_Log_Filter_Level_Absolute);
	/* setup command server logging */
	Command_Server_Set_Log_Handler_Function(Sprat_Global_Call_Log_Handlers);
	Command_Server_Set_Log_Filter_Function(Command_Server_Log_Filter_Level_Absolute);
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("main","sprat_main.c","Sprat_Initialise_Logging",LOG_VERBOSITY_VERY_TERSE,"STARTUP",
			 "Logging initialised.");
#endif
	return TRUE;
}

/**
 * Initialise the CCD connection, initialise the CCD and set the temperature.
 * <ul>
 * <li>We retrieve the andor config directory from the "ccd.andor.setup.config_directory" config property.
 * <li>We retrieve the FITS data directory from the "file.fits.path" config property.
 * <li>We retrieve the horizontal shift speed index from the "ccd.andor.setup.hs_speed_index" config property.
 * <li>We retrieve whether to clamp the bias level from the "ccd.andor.setup.baseline_clamp" config property.
 * <li>We retrieve the preamp gain index from the "ccd.andor.setup.preamp_gain_index" config property.
 * <li>We call CCD_Setup_Config_Directory_Set to setup where the Andor library config files are.
 * <li>We call CCD_Setup_Startup to initialise the Andor library and CCD.
 * <li>We call CCD_Setup_Get_Camera_Identification to retrieve Model and Serial number data to log.
 * <li>We retrieve the shutter output configuration from the "ccd.andor.exposure.shutter.output" config property.
 * <li>We call CCD_Exposure_Set_Shutter_Type to configure the shutter output to TTL low or TTL high.
 * <li>We retrieve the target temperature from the "ccd.temperature.target" config property.
 * <li>We call CCD_Temperature_Set to set the CCD temperature.
 * <li>We retrieve whether to turn the cooler on from the "ccd.temperature.cooler.on" config property.
 * <li>If we want to turn the coller on, we call CCD_Temperature_Cooler_On to turn the cooler on.
 * <li>We retrieve the FITS file instrument code from the "file.fits.instrument_code" config property.
 * <li>We call CCD_Fits_Filename_Initialise to initialise FITS filename handling.
 * </ul>
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see sprat_config.html#Sprat_Config_Get_Boolean
 * @see sprat_config.html#Sprat_Config_Get_Integer
 * @see sprat_config.html#Sprat_Config_Get_Double
 * @see sprat_config.html#Sprat_Config_Get_String
 * @see sprat_config.html#Sprat_Config_Get_Character
 * @see ../ccd/cdocs/ccd_exposure.html#CCD_Exposure_Set_Shutter_Type
 * @see ../ccd/cdocs/ccd_setup.html#CCD_Setup_Startup
 * @see ../ccd/cdocs/ccd_setup.html#CCD_Setup_Config_Directory_Set
 * @see ../ccd/cdocs/ccd_setup.html#CCD_Setup_Get_Camera_Identification
 * @see ../ccd/cdocs/ccd_temperature.html#CCD_Temperature_Set
 * @see ../ccd/cdocs/ccd_temperature.html#CCD_Temperature_Cooler_On
 * @see ../ccd/cdocs/ccd_fits_filename.html#CCD_Fits_Filename_Initialise
 */
static int Sprat_Startup_CCD(void)
{
	enum CCD_EXPOSURE_SHUTTER_OUTPUT set_shutter_type;
	char *shutter_output_string = NULL;
	char *andor_dir = NULL;
	char *data_dir = NULL;
	char key_string[32];
	char head_model_name[256];
	char instrument_code;
	double target_temperature;
	int index,done,serial_number,cooler_on,hs_speed_index,baseline_clamp,preamp_gain_index;

	/* get config */
	if(!Sprat_Config_Get_String("ccd.andor.setup.config_directory",&andor_dir))
	{
		Sprat_Global_Error_Number = 2;
		sprintf(Sprat_Global_Error_String,"Sprat_Startup_CCD:Failed to get Andor config directory.");
		return FALSE;
	}
	if(!Sprat_Config_Get_String("file.fits.path",&data_dir))
	{
		Sprat_Global_Error_Number = 5;
		sprintf(Sprat_Global_Error_String,"Sprat_Startup_CCD:Failed to get config for data directory.");
		return FALSE;
	}
	if(!Sprat_Config_Get_Integer("ccd.andor.setup.hs_speed_index",&hs_speed_index))
	{
		Sprat_Global_Error_Number = 1;
		sprintf(Sprat_Global_Error_String,"Sprat_Startup_CCD:Failed to get Andor horizontal speed index.");
		return FALSE;
	}
	if(!Sprat_Config_Get_Boolean("ccd.andor.setup.baseline_clamp",&baseline_clamp))
	{
		Sprat_Global_Error_Number = 12;
		sprintf(Sprat_Global_Error_String,"Sprat_Startup_CCD:Failed to get Andor baseline clamp.");
		return FALSE;
	}
	if(!Sprat_Config_Get_Integer("ccd.andor.setup.preamp_gain_index",&preamp_gain_index))
	{
		Sprat_Global_Error_Number = 11;
		sprintf(Sprat_Global_Error_String,"Sprat_Startup_CCD:Failed to get Andor preamp gain index.");
		return FALSE;
	}
	/* now actually configure the CCD and FITS library */
	/* initialise ccd library/andor library/ccd camera connection */
	if(!CCD_Setup_Config_Directory_Set(andor_dir))
	{
		Sprat_Global_Error_Number = 8;
		sprintf(Sprat_Global_Error_String,"Sprat_Startup_CCD:Failed to set Andor config directory to '%s'.",
			andor_dir);
		return FALSE;
	}
	if(andor_dir != NULL)
		free(andor_dir);
	if(!CCD_Setup_Startup(TRUE,0,hs_speed_index,baseline_clamp,preamp_gain_index))
	{
		Sprat_Global_Error_Number = 9;
		sprintf(Sprat_Global_Error_String,"Sprat_Startup_CCD:Failed to Initialise camera.");
		return FALSE;
	}
	/* examine andor serial number/head model name */
	if(!CCD_Setup_Get_Camera_Identification(head_model_name,&serial_number))
	{
		Sprat_Global_Error_Number = 26;
		sprintf(Sprat_Global_Error_String,
			"Sprat_Startup_CCD:Failed to get camera indentification for Andor camera.");
		return FALSE;
	}
#if SPRAT_DEBUG > 1
	Sprat_Global_Log_Format("CCD","sprat_main.c","Sprat_Startup_CCD",LOG_VERBOSITY_VERBOSE,
				 "STARTUP","SPRAT using andor CCD Model '%s' serial number %d.",
				 head_model_name,serial_number);
#endif
	/* exposure shutter output type */
	if(!Sprat_Config_Get_String("ccd.andor.exposure.shutter.output",&shutter_output_string))
	{
		Sprat_Global_Error_Number = 14;
		sprintf(Sprat_Global_Error_String,"Sprat_Startup_CCD:Failed to get exposure shutter output type.");
		return FALSE;
	}
	if(strcmp(shutter_output_string,"low") == 0)
	{
		set_shutter_type = CCD_EXPOSURE_SHUTTER_OUTPUT_TTL_LOW;
	}
	else if(strcmp(shutter_output_string,"high") == 0)
	{
		set_shutter_type = CCD_EXPOSURE_SHUTTER_OUTPUT_TTL_HIGH;
	}
	else
	{
		Sprat_Global_Error_Number = 15;
		sprintf(Sprat_Global_Error_String,"Sprat_Startup_CCD:Illegal exposure shutter output type '%s'.",
			shutter_output_string);
		return FALSE;
	}
	if(shutter_output_string != NULL)
		free(shutter_output_string);
	if(!CCD_Exposure_Set_Shutter_Type(set_shutter_type))
	{
		Sprat_Global_Error_Number = 16;
		sprintf(Sprat_Global_Error_String,
			"Sprat_Startup_CCD:Failed to set exposure shutter output type to '%d'.",set_shutter_type);
		return FALSE;
	}
	/* set CCD target temperature */
	if(!Sprat_Config_Get_Double("ccd.temperature.target",&target_temperature))
	{
		Sprat_Global_Error_Number = 3;
		sprintf(Sprat_Global_Error_String,
			"Sprat_Startup_CCD:Failed to get target temperature config for Andor camera.");
		return FALSE;
	}
	if(!CCD_Temperature_Set(target_temperature))
	{
		Sprat_Global_Error_Number = 4;
		sprintf(Sprat_Global_Error_String,
			"Sprat_Startup_CCD:Failed to set temperature for Andor camera.");
		return FALSE;
	}
	/* turn cooler on? */
	if(!Sprat_Config_Get_Boolean("ccd.temperature.cooler.on",&cooler_on))
	{
		Sprat_Global_Error_Number = 6;
		sprintf(Sprat_Global_Error_String,"Sprat_Startup_CCD:Failed to get temperature cooler on.");
		return FALSE;
	}
	if(cooler_on)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("CCD","sprat_main.c","Sprat_Startup_CCD",LOG_VERBOSITY_VERBOSE,
				    "STARTUP","Turning cooler on.");
#endif
		if(!CCD_Temperature_Cooler_On())
		{
			Sprat_Global_Error_Number = 7;
			sprintf(Sprat_Global_Error_String,"Sprat_Startup_CCD:Failed to turn temperature cooler on.");
			return FALSE;
		}
	}
	else
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("CCD","sprat_main.c","Sprat_Initialise_CCD",LOG_VERBOSITY_VERBOSE,
				    "STARTUP","NOT turning cooler on.");
#endif
	}
	/* initialise FITS filename */
	if(!Sprat_Config_Get_Character("file.fits.instrument_code",&instrument_code))
	{
		Sprat_Global_Error_Number = 18;
		sprintf(Sprat_Global_Error_String,
			"Sprat_Startup_CCD:Failed to get instrument code config for Andor camera.");
		return FALSE;
	}
	if(!CCD_Fits_Filename_Initialise(instrument_code,data_dir))
	{
		Sprat_Global_Error_Number = 10;
		sprintf(Sprat_Global_Error_String,"Sprat_Startup_CCD:Failed to Initialise FITS filenames for camera.");
		return FALSE;
	}
	if(data_dir != NULL)
		free(data_dir);
	return TRUE;
}

/**
 * Shutdown the CCD conenction.
 * <ul>
 * <li>We call CCD_Setup_Shutdown to close the Andor library and CCD.
 * </ul>
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see ../ccd/cdocs/ccd_setup.html#CCD_Setup_Shutdown
 */
static int Sprat_Shutdown_CCD(void)
{
	if(!CCD_Setup_Shutdown())
	{
		Sprat_Global_Error_Number = 13;
		sprintf(Sprat_Global_Error_String,"Sprat_Shutdown_CCD:Failed to Shutdown Andor CCD library.");
		return FALSE;
	}
	return TRUE;
}

/**
 * Help routine.
 */
static void Help(void)
{
	fprintf(stdout,"Sprat:Help.\n");
	fprintf(stdout,"sprat \n");
	fprintf(stdout,"\t[-co[nfig_filename] <filename>]\n");
	fprintf(stdout,"\t[-sprat_log_level|-ll <level>\n");
	fprintf(stdout,"\t[-ccd_log_level|-ccdll <level>\n");
	fprintf(stdout,"\t[-command_server_log_level|-csll <level>\n");
	fprintf(stdout,"\t<level> is an integer from 1..5.\n");
}

/**
 * Routine to parse command line arguments.
 * @param argc The number of arguments sent to the program.
 * @param argv An array of argument strings.
 * @see #Help
 * @see sprat_global.html#Sprat_Global_Set_Config_Filename
 * @see sprat_global.html#Sprat_Global_Set_Log_Filter_Level
 * @see ../ccd/cdocs/ccd_global.html#CCD_Global_Set_Log_Filter_Level
 * @see ../../autoguider/commandserver/cdocs/command_server.html#Command_Server_Set_Log_Filter_Level
 */
static int Parse_Arguments(int argc, char *argv[])
{
	int i,retval,log_level;

	for(i=1;i<argc;i++)
	{
		if((strcmp(argv[i],"-sprat_log_level")==0)||(strcmp(argv[i],"-ll")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&log_level);
				if(retval != 1)
				{
					fprintf(stderr,"Parse_Arguments:Parsing log level %s failed.\n",argv[i+1]);
					return FALSE;
				}
				Sprat_Global_Set_Log_Filter_Level(log_level);
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:Log Level requires a level.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-ccd_log_level")==0)||(strcmp(argv[i],"-ccdll")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&log_level);
				if(retval != 1)
				{
					fprintf(stderr,"Parse_Arguments:Parsing log level %s failed.\n",argv[i+1]);
					return FALSE;
				}
				CCD_Global_Set_Log_Filter_Level(log_level);
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:Log Level requires a level.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-command_server_log_level")==0)||(strcmp(argv[i],"-csll")==0))
		{
			if((i+1)<argc)
			{
				retval = sscanf(argv[i+1],"%d",&log_level);
				if(retval != 1)
				{
					fprintf(stderr,"Parse_Arguments:Parsing log level %s failed.\n",argv[i+1]);
					return FALSE;
				}
				Command_Server_Set_Log_Filter_Level(log_level);
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:Log Level requires a level.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-config_filename")==0)||(strcmp(argv[i],"-co")==0))
		{
			if((i+1)<argc)
			{
				if(!Sprat_Global_Set_Config_Filename(argv[i+1]))
				{
					fprintf(stderr,"Parse_Arguments:"
						"Sprat_Global_Set_Config_Filename failed.\n");
					return FALSE;
				}
				i++;
			}
			else
			{
				fprintf(stderr,"Parse_Arguments:config filename required.\n");
				return FALSE;
			}
		}
		else if((strcmp(argv[i],"-help")==0)||(strcmp(argv[i],"-h")==0))
		{
			Help();
			exit(0);
		}
		else
		{
			fprintf(stderr,"Parse_Arguments:argument '%s' not recognized.\n",argv[i]);
			return FALSE;
		}
	}
	return TRUE;
}

