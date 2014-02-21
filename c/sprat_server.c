/* sprat_server.c
** Sprat server routines
** $HeadURL$
*/
/**
 * Command Server routines for the sprat program.
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

#include "command_server.h"

#include "sprat_command.h"
#include "sprat_global.h"
#include "sprat_server.h"

/* internal data */
/**
 * Revision Control System identifier.
 */
static char rcsid[] = "$Id$";
/**
 * The server context to use for this server.
 * @see ../../command_server/cdocs/command_server.html#Command_Server_Server_Context_T
 */
static Command_Server_Server_Context_T Command_Server_Context = NULL;
/**
 * Command server port number.
 */
static unsigned short Command_Server_Port_Number = 1234;

/* internal functions */
static void Server_Connection_Callback(Command_Server_Handle_T connection_handle);
static int Send_Reply(Command_Server_Handle_T connection_handle,char *reply_message);
static int Send_Binary_Reply(Command_Server_Handle_T connection_handle,void *buffer_ptr,size_t buffer_length);
static int Send_Binary_Reply_Error(Command_Server_Handle_T connection_handle);

/* ----------------------------------------------------------------------------
** 		external functions 
** ---------------------------------------------------------------------------- */
/**
 * Sprat server initialisation routine. Assumes Sprat_Config_Load has previously been called
 * to load the configuration file.
 * It loads the unsigned short with key "command.server.port_number" into the Command_Server_Port_Number variable
 * for use in Sprat_Server_Start.
 * @return The routine returns TRUE if successfull, and FALSE if an error occurs.
 * @see #Sprat_Server_Start
 * @see #Command_Server_Port_Number
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see sprat_config.html#Sprat_Config_Get_Unsigned_Short
 */
int Sprat_Server_Initialise(void)
{
	int retval;

#if SPRAT_DEBUG > 1
	Sprat_Global_Log_Format("server","sprat_server.c","Sprat_Server_Initialise",
				LOG_VERBOSITY_TERSE,"SERVER","started.");
#endif
	/* get port number from config */
	retval = Sprat_Config_Get_Unsigned_Short("command.server.port_number",&Command_Server_Port_Number);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 200;
		sprintf(Sprat_Global_Error_String,"Failed to find port number in config file.");
		return FALSE;
	}
#if SPRAT_DEBUG > 1
	Sprat_Global_Log_Format("server","sprat_server.c","Sprat_Server_Initialise",LOG_VERBOSITY_TERSE,"SERVER",
				"finished.");
#endif
	return TRUE;
}

/**
 * Sprat server start routine.
 * This routine starts the server. It does not return until the server is stopped using Sprat_Server_Stop.
 * @return The routine returns TRUE if successfull, and FALSE if an error occurs.
 * @see #Command_Server_Port_Number
 * @see #Server_Connection_Callback
 * @see #Command_Server_Context
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see ../../command_server/cdocs/command_server.html#Command_Server_Start_Server
 */
int Sprat_Server_Start(void)
{
	int retval;

#if SPRAT_DEBUG > 1
	Sprat_Global_Log("server","sprat_server.c","Sprat_Server_Start",LOG_VERBOSITY_VERY_TERSE,"SERVER","started.");
#endif
#if SPRAT_DEBUG > 2
	Sprat_Global_Log_Format("server","sprat_server.c","Sprat_Server_Start",LOG_VERBOSITY_VERY_TERSE,"SERVER",
				"Starting multi-threaded server on port %hu.",Command_Server_Port_Number);
#endif
	retval = Command_Server_Start_Server(&Command_Server_Port_Number,Server_Connection_Callback,
					     &Command_Server_Context);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 201;
		sprintf(Sprat_Global_Error_String,"Sprat_Server_Start:"
			"Command_Server_Start_Server returned FALSE.");
		return FALSE;
	}
#if SPRAT_DEBUG > 1
	Sprat_Global_Log("server","sprat_server.c","Sprat_Server_Start",
			    LOG_VERBOSITY_VERY_TERSE,"SERVER","finished.");
#endif
	return TRUE;
}

/**
 * Sprat server stop routine.
 * @return The routine returns TRUE if successfull, and FALSE if an error occurs.
 * @see #Command_Server_Context
 * @see sprat_global.html#Sprat_Global_Log
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see ../../command_server/cdocs/command_server.html#Command_Server_Close_Server
 */
int Sprat_Server_Stop(void)
{
	int retval;
#if SPRAT_DEBUG > 1
	Sprat_Global_Log_Format("server","sprat_server.c","Sprat_Server_Stop",
				   LOG_VERBOSITY_VERY_TERSE,"SERVER","started.");
#endif
	retval = Command_Server_Close_Server(&Command_Server_Context);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 202;
		sprintf(Sprat_Global_Error_String,"Sprat_Server_Stop:"
			"Command_Server_Close_Server returned FALSE.");
		return FALSE;
	}
#if SPRAT_DEBUG > 1
	Sprat_Global_Log_Format("server","sprat_server.c","Sprat_Server_Stop",
				   LOG_VERBOSITY_VERY_TERSE,"SERVER","finished.");
#endif
	return TRUE;
}

/* ----------------------------------------------------------------------------
** 		internal functions 
** ---------------------------------------------------------------------------- */
/**
 * Server connection thread, invoked whenever a new command comes in.
 * @param connection_handle Connection handle for this thread.
 * @see #Send_Reply
 * @see #Send_Binary_Reply
 * @see #Send_Binary_Reply_Error
 * @see #Sprat_Server_Stop
 * @see sprat_command.html#Sprat_Command_Abort
 * @see sprat_command.html#Sprat_Command_Config
 * @see sprat_command.html#Sprat_Command_Dark
 * @see sprat_command.html#Sprat_Command_Expose
 * @see sprat_command.html#Sprat_Command_Fits_Header
 * @see sprat_command.html#Sprat_Command_Status
 * @see sprat_command.html#Sprat_Command_Temperature
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see ../command_server/cdocs/command_server.html#Command_Server_Read_Message
 */
static void Server_Connection_Callback(Command_Server_Handle_T connection_handle)
{
	void *buffer_ptr = NULL;
	size_t buffer_length = 0;
	char *reply_string = NULL;
	char *client_message = NULL;
	int retval;
	int seconds,i;

	/* get message from client */
	retval = Command_Server_Read_Message(connection_handle, &client_message);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 203;
		sprintf(Sprat_Global_Error_String,"Server_Connection_Callback:"
			"Failed to read message.");
		Sprat_Global_Error("server","sprat_server.c","Server_Connection_Callback",
					 LOG_VERBOSITY_VERY_TERSE,"SERVER");
		return;
	}
#if SPRAT_DEBUG > 1
	Sprat_Global_Log_Format("server","sprat_server.c","Server_Connection_Callback",
				      LOG_VERBOSITY_VERY_TERSE,"SERVER","received '%s'",client_message);
#endif
	/* do something with message */
	if(strncmp(client_message,"abort",5) == 0)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("server","sprat_server.c","Server_Connection_Callback",
				       LOG_VERBOSITY_VERY_TERSE,"SERVER","abort detected.");
#endif
		retval = Sprat_Command_Abort(client_message,&reply_string);
		if(retval == TRUE)
		{
			retval = Send_Reply(connection_handle,reply_string);
			if(reply_string != NULL)
				free(reply_string);
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c",
							 "Server_Connection_Callback",
							 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
		else
		{
			Sprat_Global_Error("server","sprat_server.c",
						 "Server_Connection_Callback",
						 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			retval = Send_Reply(connection_handle, "1 Sprat_Command_Abort failed.");
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c",
							 "Server_Connection_Callback",
							 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
	}
	else if(strncmp(client_message,"config",6) == 0)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("server","sprat_server.c","Server_Connection_Callback",
				       LOG_VERBOSITY_VERY_TERSE,"SERVER","config detected.");
#endif
		retval = Sprat_Command_Config(client_message,&reply_string);
		if(retval == TRUE)
		{
			retval = Send_Reply(connection_handle,reply_string);
			if(reply_string != NULL)
				free(reply_string);
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c",
							 "Server_Connection_Callback",
							 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
		else
		{
			Sprat_Global_Error("server","sprat_server.c",
						 "Server_Connection_Callback",
						 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			retval = Send_Reply(connection_handle, "1 Sprat_Command_Config failed.");
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c",
							 "Server_Connection_Callback",
							 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
	}
	else if(strncmp(client_message,"dark",4) == 0)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("server","sprat_server.c","Server_Connection_Callback",
				       LOG_VERBOSITY_VERY_TERSE,"SERVER","dark detected.");
#endif
		/* diddly 
		retval = Sprat_Command_Dark(client_message,&reply_string);
		if(retval == TRUE)
		{
			retval = Send_Reply(connection_handle,reply_string);
			if(reply_string != NULL)
				free(reply_string);
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c",
							 "Server_Connection_Callback",
							 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
		else
		{
			Sprat_Global_Error("server","sprat_server.c",
						 "Server_Connection_Callback",
						 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			retval = Send_Reply(connection_handle, "1 Sprat_Command_Dark failed.");
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c",
							 "Server_Connection_Callback",
							 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
		*/
	}
	else if(strncmp(client_message,"expose",6) == 0)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("server","sprat_server.c","Server_Connection_Callback",
				       LOG_VERBOSITY_VERY_TERSE,"SERVER","expose detected.");
#endif
		/* diddly
		retval = Sprat_Command_Expose(client_message,&reply_string);
		if(retval == TRUE)
		{
			retval = Send_Reply(connection_handle,reply_string);
			if(reply_string != NULL)
				free(reply_string);
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c",
							 "Server_Connection_Callback",
							 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
		else
		{
			Sprat_Global_Error("server","sprat_server.c",
						 "Server_Connection_Callback",
						 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			retval = Send_Reply(connection_handle, "1 Sprat_Command_Expose failed.");
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c",
							 "Server_Connection_Callback",
							 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
		*/
	}
	else if(strncmp(client_message,"fitsheader",10) == 0)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("server","sprat_server.c","Server_Connection_Callback",
				    LOG_VERBOSITY_VERY_TERSE,"SERVER","fitsheader detected.");
#endif
		/* diddly
		retval = Sprat_Command_Fits_Header(client_message,&reply_string);
		if(retval == TRUE)
		{
			retval = Send_Reply(connection_handle,reply_string);
			if(reply_string != NULL)
				free(reply_string);
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c",
						      "Server_Connection_Callback",
						      LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
		else
		{
			Sprat_Global_Error("server","sprat_server.c",
					      "Server_Connection_Callback",
					      LOG_VERBOSITY_VERY_TERSE,"SERVER");
			retval = Send_Reply(connection_handle, "1 Sprat_Command_Fits_Header failed.");
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c",
						      "Server_Connection_Callback",
						      LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
		*/
	}
	else if(strcmp(client_message, "help") == 0)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("server","sprat_server.c","Sprat_Server_Connection_Callback",
				   LOG_VERBOSITY_VERY_TERSE,"SERVER","help detected.");
#endif
		Send_Reply(connection_handle, "help:\n"
			   "\tabort\n"
			   "\tconfig <xbin> <ybin> <emgain> [<startx> <endx> <starty> <endy>]\n"
			   "\tfitsheader add <keyword> <boolean|float|integer|string> <value>\n"
			   "\tfitsheader delete <keyword>\n"
			   "\tfitsheader clear\n"
			   "\tdark <ms>\n"
			   "\texpose <ms>\n"
			   "\thelp\n"
/*			   "\tlog_level <sprat|ccd|command_server|object|ngatcil> <n>\n"*/
			   "\tmultbias <count>\n"
			   "\tmultrun <length> <count> <standard>\n"
			   "\tstatus temperature [get|status]\n"
			   "\tstatus exposure [status|accumulation|series|index|count|length]\n"
			   "\tstatus exposure [start_time|multrun|run]\n"
			   "\tstatus multrun [index|count]\n"
			   "\tshutdown\n"
			   "\ttemperature [set <C>|cooler [on|off]]\n"
			   );
	}
	else if(strncmp(client_message,"multbias",8) == 0)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("server","sprat_server.c","Server_Connection_Callback",
				       LOG_VERBOSITY_VERY_TERSE,"SERVER","multbias detected.");
#endif
		/* diddly
		retval = Sprat_Command_MultBias(client_message,&reply_string);
		if(retval == TRUE)
		{
			retval = Send_Reply(connection_handle,reply_string);
			if(reply_string != NULL)
				free(reply_string);
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c",
							 "Server_Connection_Callback",
							 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
		else
		{
			Sprat_Global_Error("server","sprat_server.c","Server_Connection_Callback",
					      LOG_VERBOSITY_VERY_TERSE,"SERVER");
			retval = Send_Reply(connection_handle, "1 Sprat_Command_MultBias failed.");
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c",
						      "Server_Connection_Callback",
						      LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
		*/
	}
	else if(strncmp(client_message,"multrun",7) == 0)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("server","sprat_server.c","Server_Connection_Callback",
				       LOG_VERBOSITY_VERY_TERSE,"SERVER","multrun command detected.");
#endif
		/* diddly
		retval = Sprat_Command_Multrun(client_message,&reply_string);
		if(retval == TRUE)
		{
			retval = Send_Reply(connection_handle,reply_string);
			if(reply_string != NULL)
				free(reply_string);
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c","Server_Connection_Callback",
						      LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
		else
		{
			Sprat_Global_Error("server","sprat_server.c","Server_Connection_Callback",
					      LOG_VERBOSITY_VERY_TERSE,"SERVER");
			retval = Send_Reply(connection_handle, "1 Sprat_Command_Multrun failed.");
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c","Server_Connection_Callback",
						      LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
		*/
	}
	else if(strncmp(client_message,"status",6) == 0)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("server","sprat_server.c","Sprat_Server_Connection_Callback",
				       LOG_VERBOSITY_VERY_TERSE,"SERVER","status detected.");
#endif
		/* diddly
		retval = Sprat_Command_Status(client_message,&reply_string);
		if(retval == TRUE)
		{
			retval = Send_Reply(connection_handle,reply_string);
			if(reply_string != NULL)
				free(reply_string);
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c",
							 "Sprat_Server_Connection_Callback",
							 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
		else
		{
			Sprat_Global_Error("server","sprat_server.c",
						 "Sprat_Server_Connection_Callback",
						 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			retval = Send_Reply(connection_handle, "1 Sprat_Command_Status failed.");
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c",
							 "Sprat_Server_Connection_Callback",
							 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
		*/
	}
	else if(strcmp(client_message, "shutdown") == 0)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("server","sprat_server.c","Sprat_Server_Connection_Callback",
				       LOG_VERBOSITY_VERY_TERSE,"SERVER","shutdown detected:about to stop.");
#endif
		retval = Send_Reply(connection_handle, "0 ok");
		if(retval == FALSE)
			Sprat_Global_Error("server","sprat_server.c",
						 "Sprat_Server_Connection_Callback",
						 LOG_VERBOSITY_VERY_TERSE,"SERVER");
		retval = Sprat_Server_Stop();
		if(retval == FALSE)
		{
			Sprat_Global_Error("server","sprat_server.c",
						 "Sprat_Server_Connection_Callback",
						 LOG_VERBOSITY_VERY_TERSE,"SERVER");
		}
	}
	else if(strncmp(client_message,"temperature",11) == 0)
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log("server","sprat_server.c","Sprat_Server_Connection_Callback",
				       LOG_VERBOSITY_VERY_TERSE,"SERVER","temperature detected.");
#endif
		/* diddly
		retval = Sprat_Command_Temperature(client_message,&reply_string);
		if(retval == TRUE)
		{
			retval = Send_Reply(connection_handle,reply_string);
			if(reply_string != NULL)
				free(reply_string);
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c","Sprat_Server_Connection_Callback",
						      LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
		else
		{
			Sprat_Global_Error("server","sprat_server.c","Sprat_Server_Connection_Callback",
						 LOG_VERBOSITY_VERY_TERSE,"SERVER");
			retval = Send_Reply(connection_handle, "1 Sprat_Command_Temperature failed.");
			if(retval == FALSE)
			{
				Sprat_Global_Error("server","sprat_server.c","Sprat_Server_Connection_Callback",
						      LOG_VERBOSITY_VERY_TERSE,"SERVER");
			}
		}
		*/
	}
	else
	{
#if SPRAT_DEBUG > 1
		Sprat_Global_Log_Format("server","sprat_server.c","Sprat_Server_Connection_Callback",
					      LOG_VERBOSITY_VERY_TERSE,"SERVER","message unknown: '%s'\n",
					      client_message);
#endif
		retval = Send_Reply(connection_handle, "1 failed message unknown");
		if(retval == FALSE)
		{
			Sprat_Global_Error("server","sprat_server.c",
						 "Sprat_Server_Connection_Callback",
						 LOG_VERBOSITY_VERY_TERSE,"SERVER");
		}
	}
	/* free message */
	free(client_message);
}

/**
 * Send a message back to the client.
 * @param connection_handle Globus_io connection handle for this thread.
 * @param reply_message The message to send.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see ../../command_server/cdocs/command_server.html#Command_Server_Write_Message
 */
static int Send_Reply(Command_Server_Handle_T connection_handle,char *reply_message)
{
	int retval;

	/* send something back to the client */
#if SPRAT_DEBUG > 5
	Sprat_Global_Log_Format("server","sprat_server.c","Send_Reply",LOG_VERBOSITY_TERSE,"SERVER",
				      "about to send '%.80s'...",reply_message);
#endif
	retval = Command_Server_Write_Message(connection_handle, reply_message);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 204;
		sprintf(Sprat_Global_Error_String,"Send_Reply:"
			"Writing message to connection failed.");
		return FALSE;
	}
#if SPRAT_DEBUG > 5
	Sprat_Global_Log_Format("server","sprat_server.c","Send_Reply",LOG_VERBOSITY_TERSE,"SERVER",
				      "sent '%.80s'...",reply_message);
#endif
	return TRUE;
}

/**
 * Send a binary message back to the client.
 * @param connection_handle Globus_io connection handle for this thread.
 * @param buffer_ptr A pointer to the binary data to send.
 * @param buffer_length The number of bytes in the binary buffer.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see sprat_global.html#Sprat_Global_Error_Number
 * @see sprat_global.html#Sprat_Global_Error_String
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see ../../command_server/cdocs/command_server.html#Command_Server_Write_Binary_Message
 */
static int Send_Binary_Reply(Command_Server_Handle_T connection_handle,void *buffer_ptr,size_t buffer_length)
{
	int retval;

#if SPRAT_DEBUG > 5
	Sprat_Global_Log_Format("server","sprat_server.c","Send_Binary_Reply",
				      LOG_VERBOSITY_INTERMEDIATE,"SERVER",
				      "about to send %ld bytes.",buffer_length);
#endif
	retval = Command_Server_Write_Binary_Message(connection_handle,buffer_ptr,buffer_length);
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 206;
		sprintf(Sprat_Global_Error_String,"Send_Binary_Reply:"
			"Writing binary message of length %ld to connection failed.",buffer_length);
		return FALSE;
	}
#if SPRAT_DEBUG > 5
	Sprat_Global_Log_Format("server","sprat_server.c","Send_Binary_Reply",
				      LOG_VERBOSITY_INTERMEDIATE,"SERVER","sent %ld bytes.",buffer_length);
#endif
	return TRUE;
}

/**
 * Send a binary message back to the client, after something went wrong to stops ending a FITS image back.
 * This involves putting the error string into a buffer and sending that back as the binary data, the client
 * end should realise this is not a FITS image!
 * @param connection_handle Globus_io connection handle for this thread.
 * @return The routine returns TRUE on success and FALSE on failure.
 * @see sprat_global.html#Sprat_Global_Error_To_String
 * @see sprat_global.html#Sprat_Global_Log_Format
 * @see ../../command_server/cdocs/command_server.html#Command_Server_Write_Binary_Message
 */
static int Send_Binary_Reply_Error(Command_Server_Handle_T connection_handle)
{
	char error_buff[1024];
	int retval;

	Sprat_Global_Error_To_String("server","sprat_server.c","Send_Binary_Reply_Error",
				      LOG_VERBOSITY_INTERMEDIATE,"SERVER",error_buff);
#if SPRAT_DEBUG > 5
	Sprat_Global_Log_Format("server","sprat_server.c","Send_Binary_Reply_Error",
				      LOG_VERBOSITY_INTERMEDIATE,"SERVER",
				      "about to send error '%s' : Length %ld bytes.",
				      error_buff,strlen(error_buff));
#endif
	retval = Command_Server_Write_Binary_Message(connection_handle,error_buff,strlen(error_buff));
	if(retval == FALSE)
	{
		Sprat_Global_Error_Number = 205;
		sprintf(Sprat_Global_Error_String,"Send_Binary_Reply_Error:"
			"Writing binary error message '%s' of length %ld to connection failed.",
			error_buff,strlen(error_buff));
		return FALSE;
	}
#if SPRAT_DEBUG > 5
	Sprat_Global_Log_Format("server","sprat_server.c","Send_Binary_Reply_Error",
				      LOG_VERBOSITY_INTERMEDIATE,"SERVER","sent %ld bytes.",strlen(error_buff));
#endif
	return TRUE;
}

