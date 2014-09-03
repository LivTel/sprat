/* ccd_fits_filename.h
** $HeadURL$
*/
#ifndef CCD_FITS_FILENAME_H
#define CCD_FITS_FILENAME_H

/**
 * Enum defining types of exposure to put in the exposure code part of a LT FITS filename.
 * <ul>
 * <li>CCD_FITS_FILENAME_EXPOSURE_TYPE_ACQUIRE
 * <li>CCD_FITS_FILENAME_EXPOSURE_TYPE_ARC
 * <li>CCD_FITS_FILENAME_EXPOSURE_TYPE_BIAS
 * <li>CCD_FITS_FILENAME_EXPOSURE_TYPE_DARK
 * <li>CCD_FITS_FILENAME_EXPOSURE_TYPE_EXPOSURE
 * <li>CCD_FITS_FILENAME_EXPOSURE_TYPE_SKYFLAT
 * <li>CCD_FITS_FILENAME_EXPOSURE_TYPE_STANDARD
 * <li>CCD_FITS_FILENAME_EXPOSURE_TYPE_LAMPFLAT
 * </ul>
 */
enum CCD_FITS_FILENAME_EXPOSURE_TYPE
{
	CCD_FITS_FILENAME_EXPOSURE_TYPE_ACQUIRE=0,CCD_FITS_FILENAME_EXPOSURE_TYPE_ARC,
	CCD_FITS_FILENAME_EXPOSURE_TYPE_BIAS,CCD_FITS_FILENAME_EXPOSURE_TYPE_DARK,
	CCD_FITS_FILENAME_EXPOSURE_TYPE_EXPOSURE,CCD_FITS_FILENAME_EXPOSURE_TYPE_SKYFLAT,
	CCD_FITS_FILENAME_EXPOSURE_TYPE_STANDARD,CCD_FITS_FILENAME_EXPOSURE_TYPE_LAMPFLAT
};

/**
 * Macro to check whether the parameter is a valid exposure type.
 * @see #CCD_FITS_FILENAME_EXPOSURE_TYPE
 */
#define CCD_FITS_FILENAME_IS_EXPOSURE_TYPE(value)	(((value) == CCD_FITS_FILENAME_EXPOSURE_TYPE_ACQUIRE)|| \
							 ((value) == CCD_FITS_FILENAME_EXPOSURE_TYPE_ARC)|| \
							 ((value) == CCD_FITS_FILENAME_EXPOSURE_TYPE_BIAS)|| \
							 ((value) == CCD_FITS_FILENAME_EXPOSURE_TYPE_DARK)|| \
							 ((value) == CCD_FITS_FILENAME_EXPOSURE_TYPE_EXPOSURE)|| \
							 ((value) == CCD_FITS_FILENAME_EXPOSURE_TYPE_SKYFLAT)|| \
							 ((value) == CCD_FITS_FILENAME_EXPOSURE_TYPE_STANDARD)|| \
							 ((value) == CCD_FITS_FILENAME_EXPOSURE_TYPE_LAMPFLAT))
/**
 * Enum defining the pipeline processing flag to put in the pipeline flag part of a LT FITS filename.
 * <ul>
 * <li>CCD_FITS_FILENAME_PIPELINE_FLAG_UNREDUCED
 * <li>CCD_FITS_FILENAME_PIPELINE_FLAG_REALTIME
 * <li>CCD_FITS_FILENAME_PIPELINE_FLAG_OFFLINE
 * </ul>
 */
enum CCD_FITS_FILENAME_PIPELINE_FLAG
{
	CCD_FITS_FILENAME_PIPELINE_FLAG_UNREDUCED=0,
	CCD_FITS_FILENAME_PIPELINE_FLAG_REALTIME=1,
	CCD_FITS_FILENAME_PIPELINE_FLAG_OFFLINE=2
};

/**
 * Macro to check whether the parameter is a valid pipeline flag.
 * @see #CCD_FITS_FILENAME_PIPELINE_FLAG
 */
#define CCD_FITS_FILENAME_IS_PIPELINE_FLAG(value)	(((value) == CCD_FITS_FILENAME_PIPELINE_FLAG_UNREDUCED)|| \
							 ((value) == CCD_FITS_FILENAME_PIPELINE_FLAG_REALTIME)|| \
							 ((value) == CCD_FITS_FILENAME_PIPELINE_FLAG_OFFLINE))

/**
 * Default instrument code, used as first character as LT FITS filename.
 */
#define CCD_FITS_FILENAME_DEFAULT_INSTRUMENT_CODE ('v')

extern int CCD_Fits_Filename_Initialise(char instrument_code,char *data_dir);
extern int CCD_Fits_Filename_Next_Multrun(void);
extern int CCD_Fits_Filename_Next_Run(void);
extern int CCD_Fits_Filename_Next_Window(void);
extern int CCD_Fits_Filename_Get_Filename(enum CCD_FITS_FILENAME_EXPOSURE_TYPE type,
					  enum CCD_FITS_FILENAME_PIPELINE_FLAG pipeline_flag,
					  char *filename,int filename_length);
extern int CCD_Fits_Filename_Multrun_Get(void);
extern int CCD_Fits_Filename_Run_Get(void);
extern int CCD_Fits_Filename_Window_Get(void);
extern int CCD_Fits_Filename_Lock(char *filename);
extern int CCD_Fits_Filename_UnLock(char *filename);
extern int CCD_Fits_Filename_Get_Error_Number(void);
extern void CCD_Fits_Filename_Error(void);
extern void CCD_Fits_Filename_Error_String(char *error_string);

#endif
