/* ccd_exposure.h
** $HeadURL$
*/
#ifndef CCD_EXPOSURE_H
#define CCD_EXPOSURE_H
#ifndef _POSIX_SOURCE
/**
 * This hash define is needed before including source files give us POSIX.4/IEEE1003.1b-1993 prototypes
 * for time.
 */
#define _POSIX_SOURCE 1
#endif
#ifndef _POSIX_C_SOURCE
/**
 * This hash define is needed before including source files give us POSIX.4/IEEE1003.1b-1993 prototypes
 * for time.
 */
#define _POSIX_C_SOURCE 199309L
#endif

#include <time.h>
#include "ccd_fits_header.h"

/**
 * Enum describing what TTL signal to send to the external shutter when it is commanded to open. 
 * This is the typ parameter to SetShutter.
 * <dl>
 * <dt>CCD_EXPOSURE_SHUTTER_OUTPUT_TTL_LOW</dt> <dd>Set the shutter to output a TTL low signal 
 *                                                  when the shutter is commanded to open.</dd>
 * <dt>CCD_EXPOSURE_SHUTTER_OUTPUT_TTL_HIGH</dt> <dd>Set the shutter to output a TTL high signal 
 *                                                   when the shutter is commanded to open.</dd>
 * </dl>
 */
enum CCD_EXPOSURE_SHUTTER_OUTPUT
{
	CCD_EXPOSURE_SHUTTER_OUTPUT_TTL_LOW = 0,
	CCD_EXPOSURE_SHUTTER_OUTPUT_TTL_HIGH = 1
	
};

/**
 * Macro to check whether the shutter output is a legal value.
 * @see #CCD_EXPOSURE_SHUTTER_OUTPUT
 */
#define CCD_EXPOSURE_IS_SHUTTER_OUTPUT(output)	(((output) == CCD_EXPOSURE_SHUTTER_OUTPUT_TTL_LOW)|| \
						 ((output) == CCD_EXPOSURE_SHUTTER_OUTPUT_TTL_HIGH))

/**
 * Return value from CCD_Exposure_Get_Exposure_Status. 
 * <ul>
 * <li>CCD_EXPOSURE_STATUS_NONE means the library is not currently performing an exposure.
 * <li>CCD_EXPOSURE_STATUS_WAIT_START means the library is waiting for the correct moment to open the shutter.
 * <li>CCD_EXPOSURE_STATUS_CLEAR means the library is currently clearing the ccd.
 * <li>CCD_EXPOSURE_STATUS_EXPOSE means the library is currently performing an exposure.
 * <li>CCD_EXPOSURE_STATUS_PRE_READOUT means the library is currently exposing, but is about
 * 	to start reading out data from the ccd (so don't start any commands that won't work in readout). 
 * <li>CCD_EXPOSURE_STATUS_READOUT means the library is currently reading out data from the ccd.
 * <li>CCD_EXPOSURE_STATUS_POST_READOUT means the library has finished reading out, but is post processing
 * 	the data (byte swap/de-interlacing/saving to disk).
 * </ul>
 * @see #CCD_DSP_Get_Exposure_Status
 */
enum CCD_EXPOSURE_STATUS
{
	CCD_EXPOSURE_STATUS_NONE,CCD_EXPOSURE_STATUS_WAIT_START,CCD_EXPOSURE_STATUS_CLEAR,
	CCD_EXPOSURE_STATUS_EXPOSE,CCD_EXPOSURE_STATUS_PRE_READOUT,CCD_EXPOSURE_STATUS_READOUT,
	CCD_EXPOSURE_STATUS_POST_READOUT
};

/**
 * Macro to check whether the exposure status is a legal value.
 * @see #CCD_EXPOSURE_STATUS
 */
#define CCD_EXPOSURE_IS_STATUS(status)	(((status) == CCD_EXPOSURE_STATUS_NONE)|| \
        ((status) == CCD_EXPOSURE_STATUS_WAIT_START)|| \
	((status) == CCD_EXPOSURE_STATUS_CLEAR)||((status) == CCD_EXPOSURE_STATUS_EXPOSE)|| \
        ((status) == CCD_EXPOSURE_STATUS_READOUT)||((status) == CCD_EXPOSURE_STATUS_POST_READOUT))


extern void CCD_Exposure_Initialise(void);
extern int CCD_Exposure_Set_Shutter_Type(enum CCD_EXPOSURE_SHUTTER_OUTPUT set_shutter_type);
extern int CCD_Exposure_Expose(int open_shutter,struct timespec start_time,int exposure_length_ms,
			       struct Fits_Header_Struct header,char *filename);
extern int CCD_Exposure_Bias(struct Fits_Header_Struct header,char *filename);
extern int CCD_Exposure_Abort(void);
extern enum CCD_EXPOSURE_STATUS CCD_Exposure_Status_Get(void);
extern int CCD_Exposure_Accumulation_Get(void);
extern int CCD_Exposure_Series_Get(void);
extern int CCD_Exposure_Length_Get(void);
extern struct timespec CCD_Exposure_Start_Time_Get(void);
extern int CCD_Exposure_Elapsed_Exposure_Time_Get(void);
extern char* CCD_Exposure_Status_To_String(enum CCD_EXPOSURE_STATUS status);
/* utility routines now used by ccd_exposure and ccd_multi_exposure */
extern int CCD_Exposure_Flip_X(int ncols,int nrows,unsigned short *exposure_data);
extern int CCD_Exposure_Flip_Y(int ncols,int nrows,unsigned short *exposure_data);
extern void CCD_Exposure_TimeSpec_To_Date_String(struct timespec time,char *time_string);
extern void CCD_Exposure_TimeSpec_To_Date_Obs_String(struct timespec time,char *time_string);
extern void CCD_Exposure_TimeSpec_To_UtStart_String(struct timespec time,char *time_string);
extern int CCD_Exposure_TimeSpec_To_Mjd(struct timespec time,int leap_second_correction,double *mjd);
extern int CCD_Exposure_Get_Error_Number(void);
extern void CCD_Exposure_Error(void);
extern void CCD_Exposure_Error_String(char *error_string);
extern void CCD_Exposure_Warning(void);
#endif
