/* ccd_temperature.h
** $HeadURL$
*/
#ifndef CCD_TEMPERATURE_H
#define CCD_TEMPERATURE_H
#include <time.h> /* struct timespec */

/**
 * Type describing what the current status of the temperature subsystem is.
 * <ul>
 * <li><b>CCD_TEMPERATURE_STATUS_OFF</b>
 * <li><b>CCD_TEMPERATURE_STATUS_AMBIENT</b>
 * <li><b>CCD_TEMPERATURE_STATUS_OK</b>
 * <li><b>CCD_TEMPERATURE_STATUS_RAMPING</b>
 * <li><b>CCD_TEMPERATURE_STATUS_UNKNOWN</b> The camera is in the middle of an acquisition and can't measure the
 *                                        current temperature.
 * </ul>
 * @see #CCD_Temperature_Get
 */
enum CCD_TEMPERATURE_STATUS
{
	CCD_TEMPERATURE_STATUS_OFF, CCD_TEMPERATURE_STATUS_AMBIENT, CCD_TEMPERATURE_STATUS_OK,
	CCD_TEMPERATURE_STATUS_RAMPING, CCD_TEMPERATURE_STATUS_UNKNOWN
};

extern int CCD_Temperature_Get(double *temperature,enum CCD_TEMPERATURE_STATUS *temperature_status);
extern int CCD_Temperature_Set(double target_temperature);
extern int CCD_Temperature_Cooler_On(void);
extern int CCD_Temperature_Cooler_Off(void);
extern int CCD_Temperature_Get_Cached_Temperature(double *temperature,
						  enum CCD_TEMPERATURE_STATUS *temperature_status,
						  struct timespec *cache_date_stamp);
extern int CCD_Temperature_Target_Temperature_Get(double *target_temperature);
extern char *CCD_Temperature_Status_To_String(enum CCD_TEMPERATURE_STATUS temperature_status);
extern int CCD_Temperature_Get_Error_Number(void);
extern void CCD_Temperature_Error(void);
extern void CCD_Temperature_Error_String(char *error_string);

#endif
