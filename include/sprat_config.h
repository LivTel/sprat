/* sprat_config.h
** $HeadURL$
*/
#ifndef SPRAT_CONFIG_H
#define SPRAT_CONFIG_H

extern int Sprat_Config_Load(char *filename);
extern int Sprat_Config_Shutdown(void);
extern int Sprat_Config_Get_String(char *key, char **value);
extern int Sprat_Config_Get_Character(char *key, char *value);
extern int Sprat_Config_Get_Integer(char *key, int *i);
extern int Sprat_Config_Get_Long(char *key, long *l);
extern int Sprat_Config_Get_Unsigned_Short(char *key,unsigned short *us);
extern int Sprat_Config_Get_Double(char *key, double *d);
extern int Sprat_Config_Get_Float(char *key, float *f);
extern int Sprat_Config_Get_Boolean(char *key, int *boolean);
#endif
