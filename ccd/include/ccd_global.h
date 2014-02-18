/* ccd_global.h
** $HeadURL$
*/

#ifndef CCD_GLOBAL_H
#define CCD_GLOBAL_H

#include <time.h> /* struct timespec */

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
#define CCD_GLOBAL_IS_BOOLEAN(value)	(((value) == TRUE)||((value) == FALSE))

/**
 * This is the length of error string of modules in the library.
 */
#define CCD_GLOBAL_ERROR_STRING_LENGTH	256
/**
 * This is the number of bytes used to represent one pixel on the CCD. The Andor library
 * returns 16 bit values for pixels, which is 2 bytes. The library will currently only compile when this
 * is two, as some parts assume 16 bit values.
 */
#define CCD_GLOBAL_BYTES_PER_PIXEL	2

/**
 * The number of nanoseconds in one second. A struct timespec has fields in nanoseconds.
 */
#define CCD_GLOBAL_ONE_SECOND_NS	(1000000000)
/**
 * The number of nanoseconds in one millisecond. A struct timespec has fields in nanoseconds.
 */
#define CCD_GLOBAL_ONE_MILLISECOND_NS	(1000000)
/**
 * The number of milliseconds in one second.
 */
#define CCD_GLOBAL_ONE_SECOND_MS	(1000)
/**
 * The number of nanoseconds in one microsecond.
 */
#define CCD_GLOBAL_ONE_MICROSECOND_NS	(1000)

#ifndef fdifftime
/**
 * Return double difference (in seconds) between two struct timespec's.
 * @param t0 A struct timespec.
 * @param t1 A struct timespec.
 * @return A double, in seconds, representing the time elapsed from t0 to t1.
 * @see #CCD_GLOBAL_ONE_SECOND_NS
 */
#define fdifftime(t1, t0) (((double)(((t1).tv_sec)-((t0).tv_sec))+(double)(((t1).tv_nsec)-((t0).tv_nsec))/CCD_GLOBAL_ONE_SECOND_NS))
#endif

/* external functions */

extern void CCD_Global_Initialise(void);
extern int CCD_Global_Is_Error(void);
extern void CCD_Global_Error(void);
extern void CCD_Global_Error_String(char *error_string);

/* routine used by other modules error code */
extern void CCD_Global_Get_Current_Time_String(char *time_string,int string_length);
/* time handling */
extern void CCD_Global_Get_Time_String(struct timespec time,char *time_string,int string_length);

/* logging routines */
extern void CCD_Global_Log_Format(char *sub_system,char *source_filename,char *function,int level,char *category,
				  char *format,...);
extern void CCD_Global_Log(char *sub_system,char *source_filename,char *function,int level,char *category,
			   char *string);
extern void CCD_Global_Set_Log_Handler_Function(void (*log_fn)(char *sub_system,char *source_filename,char *function,
							       int level,char *category,char *string));
extern void CCD_Global_Set_Log_Filter_Function(int (*filter_fn)(char *sub_system,char *source_filename,char *function,
								int level,char *category,char *string));
extern void CCD_Global_Log_Handler_Stdout(char *sub_system,char *source_filename,char *function,int level,
					  char *category,char *string);
extern void CCD_Global_Set_Log_Filter_Level(int level);
extern int CCD_Global_Log_Filter_Level_Absolute(char *sub_system,char *source_filename,char *function,int level,
						char *category,char *string);
extern int CCD_Global_Log_Filter_Level_Bitwise(char *sub_system,char *source_filename,char *function,int level,
					       char *category,char *string);
extern char* CCD_Global_Andor_ErrorCode_To_String(unsigned int error_code);

#endif
