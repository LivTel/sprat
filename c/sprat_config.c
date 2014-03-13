/* sprat_config.c
** Sprat config routines
** $HeadURL$
*/
/**
 * Config routines for the sprat system.
 * Just a a wrapper  for the eSTAR_Config routines at the moment.
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

#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include "estar_config.h"
#include "log_udp.h"
#include "sprat_global.h"
#include "sprat_config.h"

/* data types */
/* internal data */
/**
 * Revision Control System identifier.
 */
static char rcsid[] = "$Id$";
/**
 * eSTAR config properties.
 * @see ../../estar/config/estar_config.html#eSTAR_Config_Properties_t
 */
static eSTAR_Config_Properties_t Config_Properties;

/* ----------------------------------------------------------------------------
** 		external functions 
** ---------------------------------------------------------------------------- */
/**
 * Load the configuration file. Calls eSTAR_Config_Parse_File.
 * @param filename The filename to load from.
 * @return The routine returns TRUE on sucess, FALSE on failure.
 * @see ../../estar/config/estar_config.html#eSTAR_Config_Parse_File
 * @see #Config_Properties
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 */
int Sprat_Config_Load(char *filename)
{
	int retval;

	if(filename == NULL)
	{
		Sprat_Global_Error_Number = 300;
		sprintf(Sprat_Global_Error_String,"Sprat_Config_Load failed: filename was NULL.");
		return FALSE;
	}
#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Load",LOG_VERBOSITY_INTERMEDIATE,NULL,
				   "started(%s).",filename);
#endif
	retval = eSTAR_Config_Parse_File(filename,&Config_Properties);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 301;
		sprintf(Sprat_Global_Error_String,"Sprat_Config_Load failed:");
		eSTAR_Config_Error_To_String(Sprat_Global_Error_String+strlen(Sprat_Global_Error_String));
		return FALSE;
	}
#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Load",LOG_VERBOSITY_INTERMEDIATE,NULL,
			       "(%s) returned %d.",filename,retval);
#endif
	return retval;
}

/**
 * Shutdown anything associated with config. Calls eSTAR_Config_Destroy_Properties.
 * @see ../../../estar/config/estar_config.html#eSTAR_Config_Destroy_Properties
 * @see #Config_Properties
 */
int Sprat_Config_Shutdown(void)
{
	int retval;

#if SPRAT_DEBUG > 0
	Sprat_Global_Log("sprat","sprat_config.c","Sprat_Config_Shutdown",LOG_VERBOSITY_VERBOSE,NULL,
			"started: About to call eSTAR_Config_Destroy_Properties.");
#endif
	eSTAR_Config_Destroy_Properties(&Config_Properties);
#if SPRAT_DEBUG > 0
	Sprat_Global_Log("sprat","sprat_config.c","Sprat_Config_Shutdown",LOG_VERBOSITY_VERBOSE,NULL,
			    "finished.");
#endif
	return retval;
}

/**
 * Get a string value from the configuration file. Calls eSTAR_Config_Get_String.
 * @param key The config keyword.
 * @param value The address of a string pointer to hold the returned string. The returned value is allocated
 *        memory by eSTAR_Config_Get_String, which <b>should be freed</b> after use.
 * @return The routine returns TRUE on sucess, FALSE on failure.
 * @see ../../../estar/config/estar_config.html#eSTAR_Config_Get_String
 * @see #Config_Properties
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 */
int Sprat_Config_Get_String(char *key, char **value)
{
	int retval;

#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Get_String",LOG_VERBOSITY_VERBOSE,NULL,
			       "started(%s,%p).",key,value);
#endif
	retval = eSTAR_Config_Get_String(&Config_Properties,key,value);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 302;
		sprintf(Sprat_Global_Error_String,"Sprat_Config_Get_String(%s) failed:",key);
		eSTAR_Config_Error_To_String(Sprat_Global_Error_String+strlen(Sprat_Global_Error_String));
		return FALSE;
	}
#if SPRAT_DEBUG > 0
	/* we may have to re-think this if keyword value strings are too long */
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Get_String",LOG_VERBOSITY_VERBOSE,NULL,
			       "(%s) returned %s (%d).",key,(*value),retval);
#endif
	return retval;
}

/**
 * Get a character value from the configuration file. Calls eSTAR_Config_Get_String to get a string,
 * we then check it's non-null, of length 1, as extract the first character.
 * @param key The config keyword.
 * @param value The address of a char to hold the returned character.
 * @return The routine returns TRUE on sucess, FALSE on failure.
 * @see ../../../estar/config/estar_config.html#eSTAR_Config_Get_String
 * @see #Config_Properties
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 */
int Sprat_Config_Get_Character(char *key, char *value)
{
	char *string_value = NULL;
	int retval;

#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Get_Character",LOG_VERBOSITY_VERBOSE,NULL,
			       "started(%s,%p).",key,value);
#endif
	retval = eSTAR_Config_Get_String(&Config_Properties,key,&string_value);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 309;
		sprintf(Sprat_Global_Error_String,"Sprat_Config_Get_Character(%s) failed:",key);
		eSTAR_Config_Error_To_String(Sprat_Global_Error_String+strlen(Sprat_Global_Error_String));
		return FALSE;
	}
	if(string_value == NULL)
	{
		Sprat_Global_Error_Number = 310;
		sprintf(Sprat_Global_Error_String,
			"Sprat_Config_Get_Character(%s) failed:returned string was NULL.",key);
		eSTAR_Config_Error_To_String(Sprat_Global_Error_String+strlen(Sprat_Global_Error_String));
		return FALSE;
	}
	if(strlen(string_value) != 1)
	{
		Sprat_Global_Error_Number = 311;
		sprintf(Sprat_Global_Error_String,
			"Sprat_Config_Get_Character(%s) failed:returned string '%s' was too long.",key,string_value);
		eSTAR_Config_Error_To_String(Sprat_Global_Error_String+strlen(Sprat_Global_Error_String));
		return FALSE;
	}
	(*value) = string_value[0];
	free(string_value);
#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Get_Character",LOG_VERBOSITY_VERBOSE,NULL,
			       "(%s) returned %c (%d).",key,(*value),retval);
#endif
	return retval;
}

/**
 * Get a integer value from the configuration file. Calls eSTAR_Config_Get_Int.
 * @param key The config keyword.
 * @param value The address of an integer to hold the returned value. 
 * @return The routine returns TRUE on sucess, FALSE on failure.
 * @see ../../../estar/config/estar_config.html#eSTAR_Config_Get_Int
 * @see #Config_Properties
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 */
int Sprat_Config_Get_Integer(char *key, int *i)
{
	int retval;

#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Get_Integer",LOG_VERBOSITY_VERBOSE,
				   NULL,"started(%s,%p).",key,i);
#endif
	retval = eSTAR_Config_Get_Int(&Config_Properties,key,i);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 303;
		sprintf(Sprat_Global_Error_String,"Sprat_Config_Get_Integer(%s) failed:",key);
		eSTAR_Config_Error_To_String(Sprat_Global_Error_String+strlen(Sprat_Global_Error_String));
		return FALSE;
	}
#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Get_Integer",LOG_VERBOSITY_VERBOSE,
				   NULL,"(%s) returned %d.",key,*i);
#endif
	return retval;
}

/**
 * Get a long value from the configuration file. Calls eSTAR_Config_Get_Long.
 * @param key The config keyword.
 * @param value The address of a long to hold the returned value. 
 * @return The routine returns TRUE on sucess, FALSE on failure.
 * @see ../../../estar/config/estar_config.html#eSTAR_Config_Get_Long
 * @see #Config_Properties
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 */
int Sprat_Config_Get_Long(char *key, long *l)
{
	int retval;

#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Get_Long",LOG_VERBOSITY_VERBOSE,NULL,
			       "started(%s,%p).",key,l);
#endif
	retval = eSTAR_Config_Get_Long(&Config_Properties,key,l);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 304;
		sprintf(Sprat_Global_Error_String,"Sprat_Config_Get_Long(%s) failed:",key);
		eSTAR_Config_Error_To_String(Sprat_Global_Error_String+strlen(Sprat_Global_Error_String));
		return FALSE;
	}
#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Get_Long",LOG_VERBOSITY_VERBOSE,NULL,
			       "(%s) returned %ld.",key,*l);
#endif
	return retval;
}

/**
 * Get an unsigned short value from the configuration file. Calls eSTAR_Config_Get_Unsigned_Short.
 * @param key The config keyword.
 * @param value The address of a unsigned short to hold the returned value. 
 * @return The routine returns TRUE on sucess, FALSE on failure.
 * @see ../../../estar/config/estar_config.html#eSTAR_Config_Get_Unsigned_Short
 * @see #Config_Properties
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 */
int Sprat_Config_Get_Unsigned_Short(char *key,unsigned short *us)
{
	int retval;

#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Get_Unsigned_Short",
				   LOG_VERBOSITY_VERBOSE,NULL,"started(%s,%p).",key,us);
#endif
	retval = eSTAR_Config_Get_Unsigned_Short(&Config_Properties,key,us);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 305;
		sprintf(Sprat_Global_Error_String,"Sprat_Config_Get_Unsigned_Short(%s) failed:",key);
		eSTAR_Config_Error_To_String(Sprat_Global_Error_String+strlen(Sprat_Global_Error_String));
		return FALSE;
	}
#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Get_Unsigned_Short",
				   LOG_VERBOSITY_VERBOSE,NULL,"(%s) returned %hu.",key,*us);
#endif
	return retval;
}

/**
 * Get a double value from the configuration file. Calls eSTAR_Config_Get_Double.
 * @param key The config keyword.
 * @param value The address of a double to hold the returned value. 
 * @return The routine returns TRUE on sucess, FALSE on failure.
 * @see ../../../estar/config/estar_config.html#eSTAR_Config_Get_Double
 * @see #Config_Properties
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 */
int Sprat_Config_Get_Double(char *key, double *d)
{
	int retval;

#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Get_Double",LOG_VERBOSITY_VERBOSE,NULL,
			       "started(%s,%p).",key,d);
#endif
	retval = eSTAR_Config_Get_Double(&Config_Properties,key,d);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 306;
		sprintf(Sprat_Global_Error_String,"Sprat_Config_Get_Double(%s) failed:",key);
		eSTAR_Config_Error_To_String(Sprat_Global_Error_String+strlen(Sprat_Global_Error_String));
		return FALSE;
	}
#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Get_Double",LOG_VERBOSITY_VERBOSE,NULL,
			       "(%s) returned %.2f.",key,*d);
#endif
	return retval;
}

/**
 * Get a float value from the configuration file. Calls eSTAR_Config_Get_Float.
 * @param key The config keyword.
 * @param value The address of a float to hold the returned value. 
 * @return The routine returns TRUE on sucess, FALSE on failure.
 * @see ../../../estar/config/estar_config.html#eSTAR_Config_Get_Float
 * @see #Config_Properties
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 */
int Sprat_Config_Get_Float(char *key,float *f)
{
	int retval;

#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Get_Float",LOG_VERBOSITY_VERBOSE,NULL,
			       "started(%s,%p).",key,f);
#endif
	retval = eSTAR_Config_Get_Float(&Config_Properties,key,f);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 307;
		sprintf(Sprat_Global_Error_String,"Sprat_Config_Get_Float(%s) failed:",key);
		eSTAR_Config_Error_To_String(Sprat_Global_Error_String+strlen(Sprat_Global_Error_String));
		return FALSE;
	}
#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Get_Float",LOG_VERBOSITY_VERBOSE,NULL,
			       "(%s) returned %.2f.",key,*f);
#endif
	return retval;
}

/**
 * Get a boolean value from the configuration file. Calls eSTAR_Config_Get_Boolean.
 * @param key The config keyword.
 * @param value The address of an integer to hold the returned boolean value. 
 *             The keyword value should have "true/TRUE/True" for TRUE and "false/FALSE/False" for FALSE.
 * @return The routine returns TRUE on sucess, FALSE on failure.
 * @see ../../../estar/config/estar_config.html#eSTAR_Config_Get_Boolean
 * @see #Config_Properties
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 */
int Sprat_Config_Get_Boolean(char *key, int *boolean)
{
	int retval;

#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Get_Boolean",LOG_VERBOSITY_VERBOSE,
				   NULL,"started(%s,%p).",key,boolean);
#endif
	retval = eSTAR_Config_Get_Boolean(&Config_Properties,key,boolean);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 308;
		sprintf(Sprat_Global_Error_String,"Sprat_Config_Get_Boolean(%s) failed:",key);
		eSTAR_Config_Error_To_String(Sprat_Global_Error_String+strlen(Sprat_Global_Error_String));
		return FALSE;
	}
#if SPRAT_DEBUG > 0
	Sprat_Global_Log_Format("sprat","sprat_config.c","Sprat_Config_Get_Boolean",LOG_VERBOSITY_VERBOSE,
				   NULL,"(%s) returned %d.",key,*boolean);
#endif
	return retval;
}

