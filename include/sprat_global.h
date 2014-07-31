/* sprat_global.h
** $HeadURL$
*/
#ifndef SPRAT_GLOBAL_H
#define SPRAT_GLOBAL_H

#include <pthread.h>

/* hash defines */
/**
 * TRUE is the value usually returned from routines to indicate success.
 */
#ifndef TRUE
#define TRUE 1
#endif
/**
 * FALSE is the value usually returned from routines to indicate failure.
 */
#ifndef FALSE
#define FALSE 0
#endif

/**
 * Macro to check whether the parameter is either TRUE or FALSE.
 */
#define SPRAT_GLOBAL_IS_BOOLEAN(value)	        (((value) == TRUE)||((value) == FALSE))


/**
 * This is the length of error string of modules in the library.
 */
#define SPRAT_GLOBAL_ERROR_STRING_LENGTH	(1024)

/**
 * The number of nanoseconds in one second. A struct timespec has fields in nanoseconds.
 */
#define SPRAT_GLOBAL_ONE_SECOND_NS	        (1000000000)
/**
 * The number of nanoseconds in one millisecond. A struct timespec has fields in nanoseconds.
 */
#define SPRAT_GLOBAL_ONE_MILLISECOND_NS	        (1000000)
/**
 * The number of milliseconds in one second.
 */
#define SPRAT_GLOBAL_ONE_SECOND_MS	        (1000)
/**
 * The number of nanoseconds in one microsecond.
 */
#define SPRAT_GLOBAL_ONE_MICROSECOND_NS	        (1000)

#ifndef fdifftime
/**
 * Return double difference (in seconds) between two struct timespec's.
 * @param t0 A struct timespec.
 * @param t1 A struct timespec.
 * @return A double, in seconds, representing the time elapsed from t0 to t1.
 * @see #SPRAT_GLOBAL_ONE_SECOND_NS
 */
#define fdifftime(t1, t0) (((double)(((t1).tv_sec)-((t0).tv_sec))+(double)(((t1).tv_nsec)-((t0).tv_nsec))/SPRAT_GLOBAL_ONE_SECOND_NS))
#endif

/* external variabless */
extern int Sprat_Global_Error_Number;
extern char Sprat_Global_Error_String[];

/* external functions */
extern void Sprat_Global_Error(char *sub_system,char *source_filename,char *function,int level,char *category);
extern void Sprat_Global_Error_To_String(char *sub_system,char *source_filename,char *function,int level,
					       char *category,char *error_string);
extern void Sprat_Global_Error_And_String(char *sub_system,char *source_filename,char *function,int level,
					     char *category,char *error_string,int string_length);

/* routine used by other modules error code */
extern void Sprat_Global_Get_Current_Time_String(char *time_string,int string_length);
extern void Sprat_Global_Get_Time_String(struct timespec time,char *time_string,int string_length);

/* logging routines */
extern void Sprat_Global_Log_Format(char *sub_system,char *source_filename,char *function,int level,
				    char *category,char *format,...);
extern void Sprat_Global_Log(char *sub_system,char *source_filename,char *function,int level,char *category,
			     char *string);
extern void Sprat_Global_Call_Log_Handlers(char *sub_system,char *source_filename,char *function,int level,
					   char *category,char *message);
extern int Sprat_Global_Add_Log_Handler_Function(void (*log_fn)(char *sub_system,char *source_filename,
							char *function,int level,char *category,char *message));
extern void Sprat_Global_Set_Log_Filter_Function(int (*filter_fn)(char *sub_system,char *source_filename,
							 char *function,int level,char *category,char *message));
extern int Sprat_Global_Log_Set_Directory(char *directory);
extern int Sprat_Global_Log_Set_Root(char *filename_root);
extern int Sprat_Global_Log_Set_Error_Root(char *filename_root);

extern int Sprat_Global_Log_Set_UDP(int active,char *hostname,int port_number);
extern void Sprat_Global_Log_Handler_Stdout(char *sub_system,char *source_filename,char *function,int level,
					    char *category,char *message);
extern void Sprat_Global_Log_Handler_Log_Fp(char *sub_system,char *source_filename,char *function,int level,
					    char *category,char *message);
extern void Sprat_Global_Log_Handler_Log_Hourly_File(char *sub_system,char *source_filename,char *function,
						     int level,char *category,char *message);
extern void Sprat_Global_Log_Handler_Log_UDP(char *sub_system,char *source_filename,char *function,
					     int level,char *category,char *message);
extern void Sprat_Global_Set_Log_Filter_Level(int level);
extern int Sprat_Global_Log_Filter_Level_Absolute(char *sub_system,char *source_filename,char *function,
						  int level,char *category,char *message);
extern int Sprat_Global_Log_Filter_Level_Bitwise(char *sub_system,char *source_filename,char *function,
						 int level,char *category,char *message);

/* utility routines */
extern int Sprat_Global_Add_String(char **string,char *add);
extern int Sprat_Global_Int_List_Add(int add,int **list,int *count);
extern int Sprat_Global_Int_List_Sort(const void *f,const void *s);
extern int Sprat_Global_String_List_Add(char **string_list[],int *string_list_count,char *string);
extern int Sprat_Global_String_List_Free(char **string_list[],int *string_list_count);

extern int Sprat_Global_Set_Config_Filename(char *filename);
extern char *Sprat_Global_Get_Config_Filename(void);

#endif
