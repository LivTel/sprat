/* sprat_command.h
** $HeadURL$
*/
#ifndef SPRAT_COMMAND_H
#define SPRAT_COMMAND_H

extern int Sprat_Command_Abort(char *command_string,char **reply_string);
extern int Sprat_Command_Bias(char *command_string,char **reply_string);
extern int Sprat_Command_Config(char *command_string,char **reply_string);
extern int Sprat_Command_Dark(char *command_string,char **reply_string);
extern int Sprat_Command_Expose(char *command_string,char **reply_string);
extern int Sprat_Command_MultBias(char *command_string,char **reply_string);
extern int Sprat_Command_MultDark(char *command_string,char **reply_string);
extern int Sprat_Command_Multrun(char *command_string,char **reply_string);
extern int Sprat_Command_Fits_Header(char *command_string,char **reply_string);
extern int Sprat_Command_Status(char *command_string,char **reply_string);
extern int Sprat_Command_Temperature(char *command_string,char **reply_string);

#endif
