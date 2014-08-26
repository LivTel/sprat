/* ccd_setup.h
** $HeadURL$
*/
#ifndef CCD_SETUP_H
#define CCD_SETUP_H

/**
 * Structure holding position information for one window on the CCD. Fields are:
 * <dl>
 * <dt>X_Start</dt> <dd>The pixel number of the X start position of the window (upper left corner).</dd>
 * <dt>Y_Start</dt> <dd>The pixel number of the Y start position of the window (upper left corner).</dd>
 * <dt>X_End</dt> <dd>The pixel number of the X end position of the window (lower right corner).</dd>
 * <dt>Y_End</dt> <dd>The pixel number of the Y end position of the window (lower right corner).</dd>
 * </dl>
 * @see #CCD_Setup_Dimensions
 */
struct CCD_Setup_Window_Struct
{
	int X_Start;
	int Y_Start;
	int X_End;
	int Y_End;
};

extern void CCD_Setup_Initialise(void);
extern int CCD_Setup_Config_Directory_Set(char *directory);
extern int CCD_Setup_Startup(int use_recommended_vs,int vs_speed_index,int hs_speed_index,int preamp_gain_index);
extern int CCD_Setup_Shutdown(void);
extern int CCD_Setup_Dimensions(int ncols,int nrows,int hbin,int vbin,
				int use_window,struct CCD_Setup_Window_Struct window);
extern int CCD_Setup_Get_NCols(void);
extern int CCD_Setup_Get_NRows(void);
extern int CCD_Setup_Get_Bin_X(void);
extern int CCD_Setup_Get_Bin_Y(void);
extern int CCD_Setup_Is_Window(void);
extern int CCD_Setup_Get_Horizontal_Start(void);
extern int CCD_Setup_Get_Horizontal_End(void);
extern int CCD_Setup_Get_Vertical_Start(void);
extern int CCD_Setup_Get_Vertical_End(void);
extern int CCD_Setup_Get_Detector_Pixel_Count_X(void);
extern int CCD_Setup_Get_Detector_Pixel_Count_Y(void);
extern float CCD_Setup_Get_VSSpeed(void);
extern float CCD_Setup_Get_HSSpeed(void);
extern int CCD_Setup_Get_Buffer_Length(size_t *buffer_length);
extern int CCD_Setup_Get_Camera_Identification(char *head_model_name,int *serial_number);
extern int CCD_Setup_Get_Cached_Camera_Identification(char *head_model_name,int *serial_number);
extern int CCD_Setup_Allocate_Image_Buffer(void **buffer,size_t *buffer_length);

extern int CCD_Setup_Get_Error_Number(void);
extern void CCD_Setup_Error(void);
extern void CCD_Setup_Error_String(char *error_string);
extern void CCD_Setup_Warning(void);
#endif
