/* ccd_fits_header.c
** CCD fits header list handling routines
** $HeadURL$
*/
/**
 * Routines to look after lists of FITS headers to go into images.
 * @author Chris Mottram
 * @version $Revision: 1.1 $
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
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include "fitsio.h"

#include "log_udp.h"

#include "ccd_fits_header.h"
#include "ccd_global.h"

/* hash defines */
/**
 * Maximum length of FITS header keyword (can go from column 1 to column 8 inclusive), plus a '\0' terminator.
 */
#define FITS_HEADER_KEYWORD_STRING_LENGTH (9)
/**
 * Maximum length of FITS header string value (can go from column 11 to column 80 inclusive), plus a '\0' terminator.
 */
#define FITS_HEADER_VALUE_STRING_LENGTH   (71)
/**
 * Maximum length of FITS header comment (can go from column 10 to column 80 inclusive), plus a '\0' terminator.
 */
#define FITS_HEADER_COMMENT_STRING_LENGTH (72) 

/* data types */
/**
 * Enumeration describing the type of data contained in a FITS header value.
 * <dl>
 * <dt>FITS_HEADER_TYPE_STRING</dt> <dd>String</dd>
 * <dt>FITS_HEADER_TYPE_INTEGER</dt> <dd>Integer</dd>
 * <dt>FITS_HEADER_TYPE_FLOAT</dt> <dd>Floating point (double).</dd>
 * <dt>FITS_HEADER_TYPE_LOGICAL</dt> <dd>Boolean (integer having value 1 (TRUE) or 0 (FALSE).</dd>
 * </dl>
 */
enum Fits_Header_Type_Enum
{
	FITS_HEADER_TYPE_STRING,
	FITS_HEADER_TYPE_INTEGER,
	FITS_HEADER_TYPE_FLOAT,
	FITS_HEADER_TYPE_LOGICAL
};

/**
 * Structure containing information on a FITS header entry.
 * <dl>
 * <dt>Keyword</dt> <dd>Keyword string of length FITS_HEADER_KEYWORD_STRING_LENGTH.</dd>
 * <dt>Type</dt> <dd>Which value in the value union to use.</dd>
 * <dt>Value</dt> <dd>Union containing the following elements:
 *                    <ul>
 *                    <li>String (of length FITS_HEADER_VALUE_STRING_LENGTH).
 *                    <li>Int
 *                    <li>Float (of type double).
 *                    <li>Boolean (an integer, should be 0 (FALSE) or 1 (TRUE)).
 *                    </ul>
 *                </dd>
 * <dt>Comment</dt> <dd>String of length FITS_HEADER_COMMENT_STRING_LENGTH.</dd>
 * </dl>
 * @see #FITS_HEADER_VALUE_STRING_LENGTH
 * @see #FITS_HEADER_KEYWORD_STRING_LENGTH
 * @see #FITS_HEADER_COMMENT_STRING_LENGTH
 */
struct Fits_Header_Card_Struct
{
	char Keyword[FITS_HEADER_KEYWORD_STRING_LENGTH]; /* columns 1-8 */
	enum Fits_Header_Type_Enum Type;
	union
	{
		char String[FITS_HEADER_VALUE_STRING_LENGTH]; /* columns 11-80 */
		int Int;
		double Float;
		int Boolean;
	} Value;
	char Comment[FITS_HEADER_COMMENT_STRING_LENGTH]; /* columns 10-80 */
};

/* internal data */
/**
 * Revision Control System identifier.
 */
static char rcsid[] = "$Id$";
/**
 * Variable holding error code of last operation performed by the fits header routines.
 */
static int Fits_Header_Error_Number = 0;
/**
 * Local variable holding description of the last error that occured.
 */
static char Fits_Header_Error_String[CCD_GLOBAL_ERROR_STRING_LENGTH] = "";

/* internal functions */
static int Fits_Header_Add_Card(struct Fits_Header_Struct *header,struct Fits_Header_Card_Struct card);

/* ----------------------------------------------------------------------------
** 		external functions 
** ---------------------------------------------------------------------------- */
/**
 * Routine to initialise the fits header of cards. This does <b>not</b> free the card list memory.
 * @param header The address of a Fits_Header_Struct structure to modify.
 * @return The routine returns TRUE on success, and FALSE on failure. On failure, Fits_Header_Error_Number
 *         and Fits_Header_Error_String should be filled in with suitable values.
 * @see ccd_global.html#CCD_Global_Log
 * @see ccd_global.html#CCD_Global_Log_Format
 * @see #Fits_Header_Error_Number
 * @see #Fits_Header_Error_String
 */
int CCD_Fits_Header_Initialise(struct Fits_Header_Struct *header)
{
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Initialise",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","started.");
#endif
	if(header == NULL)
	{
		Fits_Header_Error_Number = 1;
		sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Initialise:Header was NULL.");
		return FALSE;
	}
	header->Card_List = NULL;
	header->Allocated_Card_Count = 0;
	header->Card_Count = 0;
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Initialise",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","finished.");
#endif
	return TRUE;
}

/**
 * Routine to clear the fits header of cards. This does <b>not</b> free the card list memory.
 * @param header The address of a Fits_Header_Struct structure to modify.
 * @return The routine returns TRUE on success, and FALSE on failure. On failure, Fits_Header_Error_Number
 *         and Fits_Header_Error_String should be filled in with suitable values.
 * @see ccd_global.html#CCD_Global_Log
 * @see ccd_global.html#CCD_Global_Log_Format
 * @see #Fits_Header_Error_Number
 * @see #Fits_Header_Error_String
 */
int CCD_Fits_Header_Clear(struct Fits_Header_Struct *header)
{
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Clear",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","started.");
#endif
	if(header == NULL)
	{
		Fits_Header_Error_Number = 2;
		sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Clear:Header was NULL.");
		return FALSE;
	}
	/* reset number of cards, without resetting allocated cards */
	header->Card_Count = 0;
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Clear",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","finished.");
#endif
	return TRUE;
}

/**
 * Routine to delete the specified keyword from the FITS header. 
 * The list is not reallocated, CCD_Fits_Header_Free will eventually free the allocated memory.
 * The routine fails (returns FALSE) if a card with the specified keyword is NOT in the list.
 * @param header The address of a Fits_Header_Struct structure to modify.
 * @param keyword The keyword of the FITS header card to remove from the list.
 * @return The routine returns TRUE on success, and FALSE on failure. On failure, Fits_Header_Error_Number
 *         and Fits_Header_Error_String should be filled in with suitable values.
 * @see ccd_global.html#CCD_Global_Log
 * @see ccd_global.html#CCD_Global_Log_Format
 * @see #Fits_Header_Error_Number
 * @see #Fits_Header_Error_String
 */
int CCD_Fits_Header_Delete(struct Fits_Header_Struct *header,char *keyword)
{
	int found_index,index,done;

#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Delete",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","started.");
#endif
	/* check parameters */
	if(header == NULL)
	{
		Fits_Header_Error_Number = 3;
		sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Delete:Header was NULL.");
		return FALSE;
	}
	if(keyword == NULL)
	{
		Fits_Header_Error_Number = 4;
		sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Delete:Keyword is NULL.");
		return FALSE;
	}
	/* find keyword in header */
	found_index = 0;
	done  = FALSE;
	while((found_index < header->Card_Count) && (done == FALSE))
	{
		if(strcmp(header->Card_List[found_index].Keyword,keyword) == 0)
		{
			done = TRUE;
		}
		else
			found_index++;
	}
	/* if we failed to find the header, then error */
	if(done == FALSE)
	{
		Fits_Header_Error_Number = 5;
		sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Delete:"
			"Failed to find Keyword '%s' in header of %d cards.",keyword,header->Card_Count);
		return FALSE;
	}
	/* if we found a card with this keyword, delete it. 
	** Move all cards beyond index down by one. */
	for(index=found_index; index < (header->Card_Count-1); index++)
	{
		header->Card_List[index] = header->Card_List[index+1];
	}
	/* decrement headers in this list */
	header->Card_Count--;
	/* leave memory allocated for reuse - this is deleted in CCD_Fits_Header_Free */
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Delete",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","finished.");
#endif
	return TRUE;
}

/**
 * Routine to add a keyword with a string value to a FITS header.
 * @param header The address of a Fits_Header_Struct structure to modify.
 * @param keyword The keyword string, must be at least 1 character less in length than 
 *        FITS_HEADER_KEYWORD_STRING_LENGTH.
 * @param value The value string, which if longer than FITS_HEADER_VALUE_STRING_LENGTH-1 characters will be truncated.
 * @param comment The comment string, which if longer than FITS_HEADER_COMMENT_STRING_LENGTH-1 
 *        characters will be truncated. This parameter can also be NULL.
 * @return The routine returns TRUE on success, and FALSE on failure. On failure, Fits_Header_Error_Number
 *         and Fits_Header_Error_String should be filled in with suitable values.
 * @see #Fits_Header_Struct
 * @see #Fits_Header_Type_Enum
 * @see #FITS_HEADER_KEYWORD_STRING_LENGTH
 * @see #FITS_HEADER_VALUE_STRING_LENGTH
 * @see #FITS_HEADER_COMMENT_STRING_LENGTH
 * @see #Fits_Header_Add_Card
 * @see ccd_global.html#CCD_Global_Log
 * @see ccd_global.html#CCD_Global_Log_Format
 * @see #Fits_Header_Error_Number
 * @see #Fits_Header_Error_String
 */
int CCD_Fits_Header_Add_String(struct Fits_Header_Struct *header,char *keyword,char *value,char *comment)
{
	struct Fits_Header_Card_Struct card;

#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Add_String",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","started.");
#endif
	if(keyword == NULL)
	{
		Fits_Header_Error_Number = 6;
		sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Add_String:"
			"Keyword is NULL.");
		return FALSE;
	}
	if(strlen(keyword) > (FITS_HEADER_KEYWORD_STRING_LENGTH-1))
	{
		Fits_Header_Error_Number = 7;
		sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Add_String:"
			"Keyword %s (%d) was too long.",keyword,strlen(keyword));
		return FALSE;
	}
	if(value == NULL)
	{
		Fits_Header_Error_Number = 8;
		sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Add_String:"
			"Value string is NULL.");
		return FALSE;
	}
#if LOGGING > 5
	CCD_Global_Log_Format("ccd","ccd_fits_header.c","CCD_Fits_Header_Add_String",
			      LOG_VERBOSITY_INTERMEDIATE,"FITS","Adding keyword %s with value %s of length %d.",
			      keyword,value,strlen(value));
#endif
	strcpy(card.Keyword,keyword);
	card.Type = FITS_HEADER_TYPE_STRING;
	/* the value will be truncated to FITS_HEADER_VALUE_STRING_LENGTH-1 */
	strncpy(card.Value.String,value,FITS_HEADER_VALUE_STRING_LENGTH-1);
	card.Value.String[FITS_HEADER_VALUE_STRING_LENGTH-1] = '\0';
	/* the comment will be truncated to FITS_HEADER_COMMENT_STRING_LENGTH-1 */
	if(comment != NULL)
	{
		strncpy(card.Comment,comment,FITS_HEADER_COMMENT_STRING_LENGTH-1);
		card.Comment[FITS_HEADER_COMMENT_STRING_LENGTH-1] = '\0';
	}
	else
	{
		strcpy(card.Comment,"");
	}
	if(!Fits_Header_Add_Card(header,card))
		return FALSE;
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Add_String",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","finished.");
#endif
	return TRUE;
}

/**
 * Routine to add a keyword with an integer value to a FITS header.
 * @param header The address of a Fits_Header_Struct structure to modify.
 * @param keyword The keyword string, must be at least 1 character less in length than 
 *        FITS_HEADER_KEYWORD_STRING_LENGTH.
 * @param value The integer value.
 * @param comment The comment string, which if longer than FITS_HEADER_COMMENT_STRING_LENGTH-1 
 *        characters will be truncated. This parameter can also be NULL.
 * @return The routine returns TRUE on success, and FALSE on failure. On failure, Fits_Header_Error_Number
 *         and Fits_Header_Error_String should be filled in with suitable values.
 * @see #Fits_Header_Struct
 * @see #Fits_Header_Type_Enum
 * @see #FITS_HEADER_KEYWORD_STRING_LENGTH
 * @see #FITS_HEADER_COMMENT_STRING_LENGTH
 * @see #Fits_Header_Add_Card
 * @see ccd_global.html#CCD_Global_Log
 * @see ccd_global.html#CCD_Global_Log_Format
 * @see #Fits_Header_Error_Number
 * @see #Fits_Header_Error_String
 */
int CCD_Fits_Header_Add_Int(struct Fits_Header_Struct *header,char *keyword,int value,char *comment)
{
	struct Fits_Header_Card_Struct card;

#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Add_Int",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","started.");
#endif
	if(keyword == NULL)
	{
		Fits_Header_Error_Number = 9;
		sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Add_Int:Keyword is NULL.");
		return FALSE;
	}
	if(strlen(keyword) > (FITS_HEADER_KEYWORD_STRING_LENGTH-1))
	{
		Fits_Header_Error_Number = 10;
		sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Add_Int:"
			"Keyword %s (%d) was too long.",keyword,strlen(keyword));
		return FALSE;
	}
	strcpy(card.Keyword,keyword);
	card.Type = FITS_HEADER_TYPE_INTEGER;
	card.Value.Int = value;
	/* the comment will be truncated to FITS_HEADER_COMMENT_STRING_LENGTH-1 */
	if(comment != NULL)
	{
		strncpy(card.Comment,comment,FITS_HEADER_COMMENT_STRING_LENGTH-1);
		card.Comment[FITS_HEADER_COMMENT_STRING_LENGTH-1] = '\0';
	}
	else
	{
		strcpy(card.Comment,"");
	}
	if(!Fits_Header_Add_Card(header,card))
		return FALSE;
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Add_Int",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","finished.");
#endif
	return TRUE;
}

/**
 * Routine to add a keyword with an float (double) value to a FITS header.
 * @param header The address of a Fits_Header_Struct structure to modify.
 * @param keyword The keyword string, must be at least 1 character less in length than 
 *        FITS_HEADER_KEYWORD_STRING_LENGTH.
 * @param value The float value of type double.
 * @param comment The comment string, which if longer than FITS_HEADER_COMMENT_STRING_LENGTH-1 
 *        characters will be truncated. This parameter can also be NULL.
 * @return The routine returns TRUE on success, and FALSE on failure. On failure, Fits_Header_Error_Number
 *         and Fits_Header_Error_String should be filled in with suitable values.
 * @see #Fits_Header_Struct
 * @see #Fits_Header_Type_Enum
 * @see #FITS_HEADER_KEYWORD_STRING_LENGTH
 * @see #FITS_HEADER_COMMENT_STRING_LENGTH
 * @see #Fits_Header_Add_Card
 * @see ccd_global.html#CCD_Global_Log
 * @see ccd_global.html#CCD_Global_Log_Format
 * @see #Fits_Header_Error_Number
 * @see #Fits_Header_Error_String
 */
int CCD_Fits_Header_Add_Float(struct Fits_Header_Struct *header,char *keyword,double value,char *comment)
{
	struct Fits_Header_Card_Struct card;

#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Add_Float",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","started.");
#endif
	if(keyword == NULL)
	{
		Fits_Header_Error_Number = 11;
		sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Add_Float:Keyword is NULL.");
		return FALSE;
	}
	if(strlen(keyword) > (FITS_HEADER_KEYWORD_STRING_LENGTH-1))
	{
		Fits_Header_Error_Number = 12;
		sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Add_Float:"
			"Keyword %s (%d) was too long.",keyword,strlen(keyword));
		return FALSE;
	}
	strcpy(card.Keyword,keyword);
	card.Type = FITS_HEADER_TYPE_FLOAT;
	card.Value.Float = value;
	/* the comment will be truncated to FITS_HEADER_COMMENT_STRING_LENGTH-1 */
	if(comment != NULL)
	{
		strncpy(card.Comment,comment,FITS_HEADER_COMMENT_STRING_LENGTH-1);
		card.Comment[FITS_HEADER_COMMENT_STRING_LENGTH-1] = '\0';
	}
	else
	{
		strcpy(card.Comment,"");
	}
	if(!Fits_Header_Add_Card(header,card))
		return FALSE;
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Add_Float",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","finished.");
#endif
	return TRUE;
}

/**
 * Routine to add a keyword with a boolean value to a FITS header.
 * @param header The address of a Fits_Header_Struct structure to modify.
 * @param keyword The keyword string, must be at least 1 character less in length than 
 *        FITS_HEADER_KEYWORD_STRING_LENGTH.
 * @param value The boolean value, an integer with value 0 (FALSE) or 1 (TRUE).
 * @param comment The comment string, which if longer than FITS_HEADER_COMMENT_STRING_LENGTH-1 
 *        characters will be truncated. This parameter can also be NULL.
 * @return The routine returns TRUE on success, and FALSE on failure. On failure, Fits_Header_Error_Number
 *         and Fits_Header_Error_String should be filled in with suitable values.
 * @see #Fits_Header_Struct
 * @see #Fits_Header_Type_Enum
 * @see #FITS_HEADER_KEYWORD_STRING_LENGTH
 * @see #FITS_HEADER_COMMENT_STRING_LENGTH
 * @see #Fits_Header_Add_Card
 * @see ccd_global.html#CCD_Global_Log
 * @see ccd_global.html#CCD_Global_Log_Format
 * @see #Fits_Header_Error_Number
 * @see #Fits_Header_Error_String
 */
int CCD_Fits_Header_Add_Logical(struct Fits_Header_Struct *header,char *keyword,int value,char *comment)
{
	struct Fits_Header_Card_Struct card;

#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Add_Logical",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","started.");
#endif
	if(keyword == NULL)
	{
		Fits_Header_Error_Number = 13;
		sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Add_Logical:Keyword is NULL.");
		return FALSE;
	}
	if(strlen(keyword) > (FITS_HEADER_KEYWORD_STRING_LENGTH-1))
	{
		Fits_Header_Error_Number = 14;
		sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Add_Logical:"
			"Keyword %s (%d) was too long.",keyword,strlen(keyword));
		return FALSE;
	}
	if(!CCD_GLOBAL_IS_BOOLEAN(value))
	{
		Fits_Header_Error_Number = 15;
		sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Add_Logical:"
			"Value (%d) was not a boolean.",value);
		return FALSE;
	}
	strcpy(card.Keyword,keyword);
	card.Type = FITS_HEADER_TYPE_LOGICAL;
	card.Value.Boolean = value;
	/* the comment will be truncated to FITS_HEADER_COMMENT_STRING_LENGTH-1 */
	if(comment != NULL)
	{
		strncpy(card.Comment,comment,FITS_HEADER_COMMENT_STRING_LENGTH-1);
		card.Comment[FITS_HEADER_COMMENT_STRING_LENGTH-1] = '\0';
	}
	else
	{
		strcpy(card.Comment,"");
	}
	if(!Fits_Header_Add_Card(header,card))
		return FALSE;
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Add_Logical",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","CCD_Fits_Header_Add_Logical:finished.");
#endif
	return TRUE;
}

/**
 * Routine to free an allocated FITS header list.
 * @param header The address of a Fits_Header_Struct structure to modify.
 * @return The routine returns TRUE on success, and FALSE on failure. On failure, Fits_Header_Error_Number
 *         and Fits_Header_Error_String should be filled in with suitable values.
 * @see ccd_global.html#CCD_Global_Log
 * @see ccd_global.html#CCD_Global_Log_Format
 * @see #Fits_Header_Error_Number
 * @see #Fits_Header_Error_String
 */
int CCD_Fits_Header_Free(struct Fits_Header_Struct *header)
{
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Free",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","started.");
#endif
	if(header == NULL)
	{
		Fits_Header_Error_Number = 16;
		sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Free:Header was NULL.");
		return FALSE;
	}
	if(header->Card_List != NULL)
		free(header->Card_List);
	header->Card_List = NULL;
	header->Card_Count = 0;
	header->Allocated_Card_Count = 0;
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Free",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","finished.");
#endif
	return TRUE;
}

/**
 * Write the information contained in the header structure to the specified fitsfile.
 * @param header The Fits_Header_Struct structure containing the headers to insert.
 * @param fits_fp A previously created CFITSIO file pointer to write the headers into.
 * @return The routine returns TRUE on success, and FALSE on failure. On failure, Fits_Header_Error_Number
 *         and Fits_Header_Error_String should be filled in with suitable values.
 * @see ccd_global.html#CCD_Global_Log
 * @see ccd_global.html#CCD_Global_Log_Format
 * @see #Fits_Header_Error_Number
 * @see #Fits_Header_Error_String
 */
int CCD_Fits_Header_Write_To_Fits(struct Fits_Header_Struct header,fitsfile *fits_fp)
{
	int i,status,retval;
	char buff[32]; /* fits_get_errstatus returns 30 chars max */

#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Write_To_Fits",
		       LOG_VERBOSITY_INTERMEDIATE,"FITS","started.");
#endif
	status = 0;
	for(i=0;i<header.Card_Count;i++)
	{
		switch(header.Card_List[i].Type)
		{
			case FITS_HEADER_TYPE_STRING:
#if LOGGING > 9
				CCD_Global_Log_Format("ccd","ccd_fits_header.c",
							      "CCD_Fits_Header_Write_To_Fits",
							      LOG_VERBOSITY_VERBOSE,"FITS",
							      "%d: %s = %s.",i,
						       header.Card_List[i].Keyword,header.Card_List[i].Value.String);
#endif
				retval = fits_update_key(fits_fp,TSTRING,header.Card_List[i].Keyword,
							 header.Card_List[i].Value.String,NULL,&status);
				break;
			case FITS_HEADER_TYPE_INTEGER:
#if LOGGING > 9
				CCD_Global_Log_Format("ccd","ccd_fits_header.c",
							      "CCD_Fits_Header_Write_To_Fits",
							      LOG_VERBOSITY_VERBOSE,"FITS",
							      "%d: %s = %d.",i,
						       header.Card_List[i].Keyword,header.Card_List[i].Value.Int);
#endif
				retval = fits_update_key(fits_fp,TINT,header.Card_List[i].Keyword,
							 &(header.Card_List[i].Value.Int),NULL,&status);
				break;
			case FITS_HEADER_TYPE_FLOAT:
#if LOGGING > 9
				CCD_Global_Log_Format("ccd","ccd_fits_header.c","CCD_Fits_Header_Write_To_Fits",
						      LOG_VERBOSITY_VERBOSE,"FITS","%d: %s = %.2f.",i,
						      header.Card_List[i].Keyword,header.Card_List[i].Value.Float);
#endif
				retval = fits_update_key_fixdbl(fits_fp,header.Card_List[i].Keyword,
								header.Card_List[i].Value.Float,6,NULL,&status);
				/*retval = fits_update_key(fits_fp,TDOUBLE,header.Keyword,header.Value.Float,
				**NULL,&status);*/
				break;
			case FITS_HEADER_TYPE_LOGICAL:
#if LOGGING > 9
				CCD_Global_Log_Format("ccd","ccd_fits_header.c",
							      "CCD_Fits_Header_Write_To_Fits",
							      LOG_VERBOSITY_VERBOSE,"FITS",
							      "%d: %s = %d.",i,
						       header.Card_List[i].Keyword,header.Card_List[i].Value.Boolean);
#endif
				retval = fits_update_key(fits_fp,TLOGICAL,header.Card_List[i].Keyword,
							 &(header.Card_List[i].Value.Boolean),NULL,&status);
				break;
			default:
				Fits_Header_Error_Number = 17;
				sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Write_To_Fits:"
					"Card %d (Keyword %s) has unknown type %d.",i,header.Card_List[i].Keyword,
					header.Card_List[i].Type);
				return FALSE;
				break;
		}
		if(retval)
		{
			fits_get_errstatus(status,buff);
			Fits_Header_Error_Number = 18;
			sprintf(Fits_Header_Error_String,"CCD_Fits_Header_Write_To_Fits:"
				"Failed to update %d %s (%s).",i,header.Card_List[i].Keyword,buff);
			return FALSE;
		}
		/* diddly comment */
	}
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","CCD_Fits_Header_Write_To_Fits",
			       LOG_VERBOSITY_INTERMEDIATE,"FITS","finished.");
#endif
	return TRUE;
}

/**
 * Get the current value of ccd_fits_header's error number.
 * @return The current value of ccd_fits_header's error number.
 */
int CCD_Fits_Header_Get_Error_Number(void)
{
	return Fits_Header_Error_Number;
}

/**
 * The error routine that reports any errors occuring in ccd_fits_header in a standard way.
 * @see ccd_global.html#CCD_Global_Get_Current_Time_String
 */
void CCD_Fits_Header_Error(void)
{
	char time_string[32];

	CCD_Global_Get_Current_Time_String(time_string,32);
	/* if the error number is zero an error message has not been set up
	** This is in itself an error as we should not be calling this routine
	** without there being an error to display */
	if(Fits_Header_Error_Number == 0)
		sprintf(Fits_Header_Error_String,"Logic Error:No Error defined");
	fprintf(stderr,"%s CCD_Fits_Header:Error(%d) : %s\n",time_string,Fits_Header_Error_Number,
		Fits_Header_Error_String);
}

/**
 * The error routine that reports any errors occuring in ccd_fits_header in a standard way. This routine places the
 * generated error string at the end of a passed in string argument.
 * @param error_string A string to put the generated error in. This string should be initialised before
 * being passed to this routine. The routine will try to concatenate it's error string onto the end
 * of any string already in existance.
 * @see ccd_global.html#CCD_Global_Get_Current_Time_String
 */
void CCD_Fits_Header_Error_String(char *error_string)
{
	char time_string[32];

	CCD_Global_Get_Current_Time_String(time_string,32);
	/* if the error number is zero an error message has not been set up
	** This is in itself an error as we should not be calling this routine
	** without there being an error to display */
	if(Fits_Header_Error_Number == 0)
		sprintf(Fits_Header_Error_String,"Logic Error:No Error defined");
	sprintf(error_string+strlen(error_string),"%s CCD_Fits_Header:Error(%d) : %s\n",time_string,
		Fits_Header_Error_Number,Fits_Header_Error_String);
}

/* ----------------------------------------------------------------------------
** 		internal functions 
** ---------------------------------------------------------------------------- */
/**
 * Routine to add a card to the list. If the keyword already exists, that card will be updated with the new value,
 * otherwise a new card will be allocated (if necessary) and added to the lsit.
 * @param header The address of a Fits_Header_Struct structure to modify.
 * @return The routine returns TRUE on success, and FALSE on failure. On failure, Fits_Header_Error_Number
 *         and Fits_Header_Error_String should be filled in with suitable values.
 * @see ccd_global.html#CCD_Global_Log
 * @see ccd_global.html#CCD_Global_Log_Format
 * @see #Fits_Header_Error_Number
 * @see #Fits_Header_Error_String
 */
static int Fits_Header_Add_Card(struct Fits_Header_Struct *header,struct Fits_Header_Card_Struct card)
{
	int index,done;
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","Fits_Header_Add_Card",
			       LOG_VERBOSITY_VERBOSE,"FITS","started.");
#endif
	if(header == NULL)
	{
		Fits_Header_Error_Number = 19;
		sprintf(Fits_Header_Error_String,"Fits_Header_Add_Card:"
			"Header was NULL for keyword %s.",card.Keyword);
		return FALSE;
	}
	index = 0;
	done  = FALSE;
	while((index < header->Card_Count) && (done == FALSE))
	{
		if(strcmp(header->Card_List[index].Keyword,card.Keyword) == 0)
		{
			done = TRUE;
		}
		else
			index++;
	}
	/* if we found a card with this keyword, update it. */
	if(done == TRUE)
	{
#if LOGGING > 5
		CCD_Global_Log_Format("ccd","ccd_fits_header.c","Fits_Header_Add_Card",
					      LOG_VERBOSITY_VERY_VERBOSE,"FITS",
					      "Found keyword %s at index %d:Card updated.",card.Keyword,index);
#endif
		header->Card_List[index] = card;
		return TRUE;
	}
	/* add the card to the list */
	/* if we need to allocate more memory... */
	if((header->Card_Count+1) >= header->Allocated_Card_Count)
	{
		/* allocate card list memory to be the current card count +1 */
		if(header->Card_List == NULL)
		{
			header->Card_List = (struct Fits_Header_Card_Struct *)malloc((header->Card_Count+1)*
									  sizeof(struct Fits_Header_Card_Struct));
		}
		else
		{
			header->Card_List = (struct Fits_Header_Card_Struct *)realloc(header->Card_List,
					    (header->Card_Count+1)*sizeof(struct Fits_Header_Card_Struct));
		}
		if(header->Card_List == NULL)
		{
			header->Card_Count = 0;
			header->Allocated_Card_Count = 0;
			Fits_Header_Error_Number = 20;
			sprintf(Fits_Header_Error_String,"Fits_Header_Add_Card:"
				"Failed to reallocate card list (%d,%d).",(header->Card_Count+1),
				header->Allocated_Card_Count);
			return FALSE;
		}
		/* upcate allocated card count */
		header->Allocated_Card_Count = header->Card_Count+1;
	}/* end if more memory needed */
	/* add the card to the list */
	header->Card_List[header->Card_Count] = card;
	header->Card_Count++;
#if LOGGING > 1
	CCD_Global_Log("ccd","ccd_fits_header.c","Fits_Header_Add_Card",
			       LOG_VERBOSITY_VERBOSE,"FITS","finished.");
#endif
	return TRUE;

}
