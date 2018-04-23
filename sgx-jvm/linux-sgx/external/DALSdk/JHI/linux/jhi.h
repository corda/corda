/*
   Copyright 2010-2016 Intel Corporation

   This software is licensed to you in accordance
   with the agreement between you and Intel Corporation.

   Alternatively, you can use this file in compliance
   with the Apache license, Version 2.


   Apache License, Version 2.0

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

/**                                                                            
********************************************************************************
**
**    @file jhi.h
**
**    @brief  Defines exported interfaces for JHI.DLL
**
**    @author Elad Dabool
**
********************************************************************************
*/

#ifndef __JHI_H__
#define __JHI_H__

#ifdef __cplusplus
extern "C" {
#endif

#include "typedefs.h"

// Format: Major.Minor.ReverseDate
#define JHI_VERSION "1.13.20161115"

//------------------------------------------------------------
// Common & External Interfaces
//------------------------------------------------------------

typedef  PVOID   JHI_HANDLE ;
typedef  UINT32  JHI_RET ;
typedef  PVOID   JHI_SESSION_HANDLE;

//------------------------------------------------------------
// Define Linkage for the functions
//------------------------------------------------------------

#define JHI_EXPORT __declspec(dllexport) JHI_RET


//----------------------------------------------------------------------------------------------------------------
//	JHI 7.1 return codes - not used in 8.0 and above (backward compatibility only), JHI 8.0 return codes are listed below
//----------------------------------------------------------------------------------------------------------------
#define JHI_FILE_MISSING_SRC					0x101		// Source File not found in install/uninstall or unable to load
															// file in SendAndRecv
#define JHI_FILE_ERROR_AUTH						0x102		// Attempted to load the file, but FW returned back 
															// a manifest failure check and rejected it
#define JHI_FILE_ERROR_DELETE					0x104		// Unable to remove file corresponding to the UUID in uninstall
															// Maybe permission issues
#define JHI_FILE_INVALID						0x105		// Invalid file - bad characters or larger than 64K
#define JHI_FILE_ERROR_OPEN						0x106		// Unable to open file. Maybe permission issues
#define JHI_FILE_UUID_MISMATCH					0x107		// UUIDs dont match between applet file and function input
#define JHI_FILE_IDENTICAL						0x108		// downloaded applet matches existing one in Jom	
	
#define JHI_INVALID_COMMAND						0x202		// invalid JHI interface command
#define JHI_ILLEGAL_VALUE						0x204	    // validation failed on input parameters

#define JHI_COMMS_ERROR							0x300		// Communications error due to HECI timeouts
															// or ME auto resets or any other COMMS error
#define JHI_SERVICE_INVALID_GUID				0x302		// Invalid COM guid (from DLL)
	
#define JHI_APPLET_TIMEOUT						0x401		// This may be a result of a Java code in VM in an infinite loop. 
															// TL will kill applet in JOM and return error code
#define JHI_APPID_NOT_EXIST						0x402		// If appid is not present in app table
#define JHI_JOM_FATAL							0x403		//JOM fatal error
#define JHI_JOM_OVERFLOW						0x404		//exceeds max installed applets or active sessions in JOM
#define JHI_JOM_ERROR_DOWNLOAD					0x405		//JOM download error
#define JHI_JOM_ERROR_UNLOAD					0x406		//JOM unload error	

#define JHI_ERROR_LOGGING						0x500		// Error in logging

#define JHI_UNKNOWN_ERROR						0x600		// Any other error

//----------------------------------------------------------------------------------------------------------------
//	JHI 8.0 return codes
//----------------------------------------------------------------------------------------------------------------

// General JHI Return Code			
#define JHI_SUCCESS								0x00					// general success response
#define JHI_INVALID_HANDLE						0x201					// invalid JHI handle
#define JHI_INVALID_PARAMS						0x203					// passed a null pointer to a required argument / illegal arguments passed to API function
#define JHI_INVALID_APPLET_GUID					JHI_ILLEGAL_VALUE		// the applet UUID is invalid
#define JHI_SERVICE_UNAVAILABLE					0x301					// there is no connection to JHI service
#define JHI_ERROR_REGISTRY						0x501					// error for any registry based access or registry corruption
#define JHI_ERROR_REPOSITORY_NOT_FOUND			0x1000					// when cannot find applets repository directory
#define JHI_INTERNAL_ERROR						0x601					// an unexpected internal error happened.
#define JHI_INVALID_BUFFER_SIZE					0x1001					// used a buffer that is larger than JHI_BUFFER_MAX
#define JHI_INVALID_COMM_BUFFER					0x1002					// JVM_COMM_BUFFER passed to function is invalid

// Install errors
#define JHI_INVALID_INSTALL_FILE				0x1003					// the dalp file path is invalid
#define JHI_READ_FROM_FILE_FAILED				0x1004					// failed to read DALP file											
#define JHI_INVALID_PACKAGE_FORMAT				0x1005					// dalp file format is not a valid
#define JHI_FILE_ERROR_COPY						0x103					// applet file could not be copied to repository
#define JHI_INVALID_INIT_BUFFER					0x1006					// passed an invalid init buffer to the function
#define JHI_FILE_NOT_FOUND						JHI_FILE_MISSING_SRC	// could not find the specified dalp file
#define JHI_INVALID_FILE_EXTENSION				0x1007					// applets package file must end with .dalp extension.
#define JHI_MAX_INSTALLED_APPLETS_REACHED		JHI_JOM_OVERFLOW		// exceeds max applets allowed, need to uninstall an applet.
#define JHI_INSTALL_FAILURE_SESSIONS_EXISTS		0x1008					// could not install because there are open sessions.
#define JHI_INSTALL_FAILED						0x1009					// no compatible applet was found in the DALP file 
#define JHI_SVL_CHECK_FAIL						0x1040					// install failed due to an svl check 
#define JHI_ILLEGAL_PLATFORM_ID					0x1041					// install failed due to an illegal platform id

// Uninstall errors
#define JHI_DELETE_FROM_REPOSITORY_FAILURE		JHI_FILE_ERROR_DELETE   // unable to delete applet DALP file from repository
#define JHI_UNINSTALL_FAILURE_SESSIONS_EXISTS	0x100A					// for app uninstallation errors

// Create Session errors
#define JHI_APPLET_NOT_INSTALLED				JHI_APPID_NOT_EXIST		// trying to create a session of uninstalled applet
#define JHI_MAX_SESSIONS_REACHED				0x100C					// exceeds max sessions allowed, need to close a session.
#define JHI_SHARED_SESSION_NOT_SUPPORTED		0x100D					// the applet does not support shared sessions.
#define JHI_MAX_SHARED_SESSION_REACHED			0x100E					// failed to get session handle due to maximun handles limit.
#define JHI_FIRMWARE_OUT_OF_RESOURCES			0x1018					// request causes the VM to exceed its memory quota
#define JHI_ONLY_SINGLE_INSTANCE_ALLOWED		0x1019					// trying to create more than a single instance of an applet
#define JHI_OPERATION_NOT_PERMITTED				0x101A					// userspace tried to perform a kernel space operation

// Close Session errors
#define JHI_INVALID_SESSION_HANDLE				0x100F					// the session handle is not of an active session.

// Send And Recieve errors
#define JHI_INSUFFICIENT_BUFFER					0x200					// buffer overflow - response greater than supplied Rx buffer
#define JHI_APPLET_FATAL						0x400					// This may be a result of uncaught exception or unusual applet 
																		// error that results in applet being terminated by TL VM. 
#define JHI_APPLET_BAD_STATE					0x407					// Applet in bad state, need to reopen the session

// Register/Unregister session events
#define JHI_SESSION_NOT_REGISTERED				0x1010					// trying to unregister a session that is not registered for events.
#define JHI_SESSION_ALREADY_REGSITERED			0x1011					// Registration to an event is done only once. 
#define JHI_EVENTS_NOT_SUPPORTED				0x1012					// events are not supported for this type of session

// Get Applet Property errors:			
#define JHI_APPLET_PROPERTY_NOT_SUPPORTED		0x1013					// Returned when calling GetAppletProperty with invalid property

// Init errors
#define JHI_SPOOLER_NOT_FOUND					0x1014					// cannot find the spooler file
#define JHI_INVALID_SPOOLER						0x1015					// cannot download spooler / create an instance of the spooler 
#define JHI_NO_CONNECTION_TO_FIRMWARE			JHI_COMMS_ERROR			// JHI has no connection to the VM

// DLL errors
#define JHI_VM_DLL_FILE_NOT_FOUND				0x1016					// VM DLL is missing from the exe path
#define JHI_VM_DLL_VERIFY_FAILED				0x1017					// DLL Signature or Publisher name are not valid.

// IAC errors
#define JHI_IAC_SERVER_SESSION_EXIST			0x1020					// May occur when trying to create two sessions on an IAC server applet
#define JHI_IAC_SERVER_INTERNAL_SESSIONS_EXIST	0x1021					// May occur when trying to close an IAC server applet session that has internal sessions

// Access control errors
#define JHI_MISSING_ACCESS_CONTROL				0x1030					// Will return from install when trying to install an applet which uses an API that it is not permitted to.

// SD session errors
#define JHI_ERROR_OEM_SIGNING_DISABLED			0x1050					// May occur if DAL OEAM signing is disabled
#define JHI_ERROR_SD_PUBLICKEY_HASH_FAILED		0x1051					// May occur if there is a mismatch in the public key hash of an SD
#define	JHI_ERROR_SD_DB_NO_FREE_SLOT			0x1052					// In case reached max installed SDs in DB
#define	JHI_ERROR_SD_TA_INSTALLATION_UNALLOWED	0x1053					// TA installation is not allowed for SD

// -----------------------------------------------------------
// Buffer size limitation is 2MB
// JHI will not accept any buffer with greater size.
//
// Note that this size limitiation does not mark the maximum buffer size an applet can recieve,
// applet max buffer size changes from one applet to another.
//
// This applies for all JHI API function that use buffers such as: 
// SendAndRecieve, CreateSession, GetAppletProperty.
// -----------------------------------------------------------
#define JHI_BUFFER_MAX 2097152	 


//------------------------------------------------------------
// Applet version macros
//------------------------------------------------------------
// While applet version is represented in a Major.Minor format (i.e. 1.0)
// the VM repersntation of an applet version (that can be obtained using JHI_GetAppletProperty) is as an integer that combine both major and minor version.
// in order to perform the transition between to two representation we offer the following macros:

/*
         Make VM Applet Version (32bit) from a Major.Minor format
         
		 Bits:
                 00-07 - Major
                 08-15 - Minor
                 15-31 - Reserved (All Zero)
*/
#define MK_APPLET_VER(maj, min) ( (UINT32) \
                                   (maj              & 0x000000FFUL) |  \
                                   ((min << 8)      & 0x0000FF00UL) &  \
                                   (0x0000FFFFUL) )

/* Extract Applet Major Version from a VM integer representation (num) */
#define MAJOR_APPLET_VER(num)  ((UINT8)  (num & 0x000000FFUL))

/* Extract Applet Minor Version from a VM integer representation (num) */
#define MINOR_APPLET_VER(num)  ((UINT8) ((num & 0x0000FF00UL) >> 8))


//------------------------------------------------------------
// JHI Events
//------------------------------------------------------------

// this enum lists the types of data received by JHI event
typedef enum _JHI_EVENT_DATA_TYPE
{
	JHI_DATA_FROM_APPLET = 0,		// the event raised by an applet session
	JHI_DATA_FROM_SERVICE = 1		// the event raised by JHI service
}JHI_EVENT_DATA_TYPE;

//this struct repersents the data received upon a JHI event 
typedef struct {
	UINT32 datalen;					// byte length of the event data
	UINT8* data;					// the buffer that contains the event data
	JHI_EVENT_DATA_TYPE dataType;	// the event type
}JHI_EVENT_DATA;

// This is the format for a callback function that is used in order to 
// receive session events.
typedef void (*JHI_EventFunc)(JHI_SESSION_HANDLE SessionHandle, JHI_EVENT_DATA EventData);


//------------------------------------------------------------
// JHI Version info.
//------------------------------------------------------------

// this enum lists the communication types that are used
// by JHI in order to communicate with the firmware
typedef enum _JHI_COMMUNICATION_TYPE
{
	JHI_SOCKETS = 0,			// communication by sockets
	JHI_HECI	= 1				// communication by HECI
} JHI_COMMUNICATION_TYPE;

#define VERSION_BUFFER_SIZE 50

// this enum lists the platfom types that are supported by JHI
typedef enum _JHI_PLATFROM_ID
{
	ME = 0, // Intel(R) Management Engine (Intel(R) ME)
	SEC	= 1,
	CSE = 2,
	INVALID_PLATFORM_ID = -1
} JHI_PLATFROM_ID;

// This enum lists the VM types that are supported by JHI
typedef enum _JHI_VM_TYPE
{
	JHI_VM_TYPE_INVALID   = -1,
	JHI_VM_TYPE_TL        =  0,
	JHI_VM_TYPE_BEIHAI    =  1, // Alias of BHv1 for backward compatibility
	JHI_VM_TYPE_BEIHAI_V1 =  1,
	JHI_VM_TYPE_BEIHAI_V2 =  2
} JHI_VM_TYPE;

// Different VM plugin types used by JHI
typedef enum _JHI_PLUGIN_TYPE
{
	JHI_PLUGIN_TYPE_INVALID   =  0,
	JHI_PLUGIN_TYPE_TL        =  1,
	JHI_PLUGIN_TYPE_BEIHAI_V1 =  2,
	JHI_PLUGIN_TYPE_BEIHAI_V2 =  3
} JHI_PLUGIN_TYPE;


// this struct contains information about the JHI service and the
// firmware versions, and additional info
typedef struct
{	
	char					jhi_version[VERSION_BUFFER_SIZE];		// the version of the JHI service
	char					fw_version[VERSION_BUFFER_SIZE];		// the version of the firmware		
	JHI_COMMUNICATION_TYPE	comm_type;								// the communication type between JHI and the firmware
	JHI_PLATFROM_ID			platform_id;							// the platform supported by the JHI service
	JHI_VM_TYPE             vm_type;								// the VM type supported by the JHI service
	UINT32					reserved[19];							// reserved bits
} JHI_VERSION_INFO;


//------------------------------------------------------------
// Session info
//------------------------------------------------------------

// this enum lists the states of a session
typedef enum _JHI_SESSION_STATE
{
	JHI_SESSION_STATE_ACTIVE = 0,			// the session is active
	JHI_SESSION_STATE_NOT_EXISTS = 1		// the session does not exists
} JHI_SESSION_STATE;

// this struct contains information for a given session
typedef struct
{	
	JHI_SESSION_STATE		state;			// the session state
	UINT32					flags;			// the flags used when this session created
	UINT32					reserved[20];	// reserved bits
} JHI_SESSION_INFO;

//------------------------------------------------------------
// Create Session flags
//------------------------------------------------------------
// this enum lists the flags that used when creating a session
//
#define JHI_NO_FLAGS			0	// no flags to be used
#define JHI_SHARED_SESSION		1	// create a shared session, or receive a handle for an existing shared session

//------------------------------------------------------------
// Data Buffer 
//------------------------------------------------------------
typedef struct
{	
	PVOID  buffer;
	UINT32 length ;
} DATA_BUFFER ;


//------------------------------------------------------------
// For Tx and Rx downto the MEI routine
// DON'T ADD MEMBERS IN 
//------------------------------------------------------------
typedef struct 
{
   DATA_BUFFER TxBuf [1] ;

   //--------------------!!!!!!!!!!!--------------------//
   // Dont add members in between TxBuf[1] & RxBuf[1]   //
   // The code that uses this depends on it             //
   //--------------------!!!!!!!!!!!--------------------//

   DATA_BUFFER RxBuf [1] ;

   // You may add anything here
} JVM_COMM_BUFFER ;




//------------------------------------------------------------
// Function Prototypes


//------------------------------------------------------------
// Function: JHI_Initialize
//------------------------------------------------------------
JHI_EXPORT
JHI_Initialize (
   OUT JHI_HANDLE* ppHandle,
   IN  PVOID       context,  
   IN  UINT32      flags
) ;

//------------------------------------------------------------
// Function: JHI_Deinit
//------------------------------------------------------------
JHI_EXPORT JHI_Deinit(IN JHI_HANDLE handle) ;


//------------------------------------------------------------
// Function: JHI_SendAndRecv
//------------------------------------------------------------
JHI_EXPORT   
JHI_SendAndRecv2(
	IN JHI_HANDLE       handle,
	IN JHI_SESSION_HANDLE SessionHandle,
	IN INT32			nCommandId,
	INOUT JVM_COMM_BUFFER* pComm,
	OUT INT32* responseCode);

//------------------------------------------------------------
// Function: JHI_Install
//------------------------------------------------------------
JHI_EXPORT 
JHI_Install2 (
   IN const JHI_HANDLE handle, 
   IN const char*      AppId,
   IN const FILECHAR*   srcFile 
);

//------------------------------------------------------------
// Function: JHI_Uninstall
//------------------------------------------------------------
JHI_EXPORT 
JHI_Uninstall( 
   IN JHI_HANDLE handle, 
   IN const char* AppId
);

//------------------------------------------------------------
// Function: JHI_GetAppletProperty
//------------------------------------------------------------
JHI_EXPORT 
JHI_GetAppletProperty(
   IN    JHI_HANDLE        handle,
   IN    const char*             AppId,
   INOUT JVM_COMM_BUFFER* pComm
);

//------------------------------------------------------------
// Function: JHI_CreateSession
//------------------------------------------------------------
JHI_EXPORT 
JHI_CreateSession (
	IN const JHI_HANDLE handle, 
	IN const char* AppId, 
	IN  UINT32 flags,
	IN DATA_BUFFER* initBuffer,
	OUT JHI_SESSION_HANDLE* pSessionHandle
);

//------------------------------------------------------------
// Function: JHI_GetSessionsCount
//------------------------------------------------------------
JHI_EXPORT 
JHI_GetSessionsCount(
	IN const JHI_HANDLE handle, 
	IN const char* AppId, 
	OUT UINT32* SessionsCount
);

//------------------------------------------------------------
// Function: JHI_CloseSession
//------------------------------------------------------------
JHI_EXPORT 
JHI_CloseSession(
	IN const JHI_HANDLE handle, 
	IN JHI_SESSION_HANDLE* pSessionHandle
);

//------------------------------------------------------------
// Function: JHI_ForceCloseSession
//------------------------------------------------------------
JHI_EXPORT
JHI_ForceCloseSession(
IN const JHI_HANDLE handle,
IN JHI_SESSION_HANDLE* pSessionHandle
);

//------------------------------------------------------------
// Function: JHI_GetSessionInfo
//------------------------------------------------------------
JHI_EXPORT 
JHI_GetSessionInfo(
	IN const JHI_HANDLE handle, 
	IN JHI_SESSION_HANDLE SessionHandle, 
	OUT JHI_SESSION_INFO* SessionInfo
);

#ifdef __ANDROID__
//------------------------------------------------------------
// Function: JHI_ClearSessions
//
JHI_RET
JHI_ClearSessions(
	IN const JHI_HANDLE handle,
	IN int ApplicationPid
);

//------------------------------------------------------------
// Function: JHI_GetSessionsCount
//------------------------------------------------------------
JHI_EXPORT 
JHI_CreateSessionProcess (
	IN const JHI_HANDLE handle, 
	IN const char* AppId, 
	IN int SessionPid,
	IN  UINT32 flags,
	IN DATA_BUFFER* initBuffer,
	OUT JHI_SESSION_HANDLE* pSessionHandle
);
#endif //__ANDROID__

//------------------------------------------------------------
// Function: JHI_RegisterEvent
//------------------------------------------------------------
JHI_EXPORT 
JHI_RegisterEvents(
	IN const JHI_HANDLE handle,
	IN JHI_SESSION_HANDLE SessionHandle,
	IN JHI_EventFunc pEventFunction);
 
//------------------------------------------------------------
// Function: JHI_UnRegisterEvent
//------------------------------------------------------------
JHI_EXPORT 
JHI_UnRegisterEvents(
	IN const JHI_HANDLE handle, 
	IN JHI_SESSION_HANDLE SessionHandle);

//------------------------------------------------------------
// Function: JHI_GetVersionInfo
//------------------------------------------------------------
JHI_EXPORT
JHI_GetVersionInfo (
   IN const JHI_HANDLE handle,
   OUT JHI_VERSION_INFO* pVersionInfo);

#ifdef __cplusplus
};
#endif


#endif
