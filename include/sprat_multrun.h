/* sprat_multrun.h
** $HeadURL$
*/
#ifndef SPRAT_MULTRUN_H
#define SPRAT_MULTRUN_H

#include <time.h> /* struct timespec */
#include "ccd_setup.h" /* CCD_Setup_Window_Struct */
#include "ccd_fits_header.h" /* Fits_Header_Struct */

extern int Sprat_Multrun_Dimensions_Set(int ncols,int nrows,int xbin,int ybin,
					int use_window,struct CCD_Setup_Window_Struct window);
extern int Sprat_Multrun_Bias(int exposure_count,char *filename,int filename_length);
extern int Sprat_Multrun_Dark(int exposure_length,int exposure_count,char *filename,int filename_length);
extern int Sprat_Multrun_Multrun(int exposure_length,int exposure_count,int standard,int *multrun_number,
				 char *filename,int filename_length);
extern int Sprat_Multrun_Fits_Header_Get(struct Fits_Header_Struct **fits_header);
extern int Sprat_Multrun_Is_Active(void);
extern int Sprat_Multrun_Get_Unbinned_NCols(void);
extern int Sprat_Multrun_Get_Unbinned_NRows(void);
extern int Sprat_Multrun_Get_Bin_X(void);
extern int Sprat_Multrun_Get_Bin_Y(void);
extern int Sprat_Multrun_Exposure_Index_Get(void);
extern int Sprat_Multrun_Exposure_Count_Get(void);
#endif
