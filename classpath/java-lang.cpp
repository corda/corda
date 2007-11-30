#include "math.h"
#include "stdlib.h"
#include "sys/time.h"
#include "time.h"
#include "time.h"
#include "string.h"
#include "stdio.h"
#include "stdint.h"
#include "jni.h"
#include "jni-util.h"

#ifdef WIN32
#  include "windows.h"
#  include "winbase.h"
#  include "io.h"
#  include "tchar.h"
#  define SO_PREFIX ""
#else
#  define SO_PREFIX "lib"
#endif

#ifdef __APPLE__
#  define SO_SUFFIX ".jnilib"
#elif defined WIN32
#  define SO_SUFFIX ".dll"
#else
#  define SO_SUFFIX ".so"
#endif

namespace {
#ifdef WIN32
  char* getErrorStr(DWORD err){
    // The poor man's error string, just print the error code 
    char * errStr = (char*) malloc(9 * sizeof(char));
    snprintf(errStr, 9, "%d", (int) err);
    return errStr;
    
    // The better way to do this, if I could figure out how to convert LPTSTR to char*
    //char* errStr;
    //LPTSTR s;
    //if(FormatMessage(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM |
    //                 FORMAT_MESSAGE_IGNORE_INSERTS, NULL, err, 0, &s, 0, NULL) == 0)
    //{
    //  errStr.Format("Unknown error occurred (%08x)", err);
    //} else {
    //  errStr = s;
    //}
    //return errStr;
  }

  void makePipe(JNIEnv* e, HANDLE p[2])
  {
    SECURITY_ATTRIBUTES sa;
    sa.nLength = sizeof(sa);
    sa.bInheritHandle = 1;
    sa.lpSecurityDescriptor = 0;
  
    BOOL success = CreatePipe(p, p + 1, &sa, 0);
    if (not success) {
      throwNew(e, "java/io/IOException", getErrorStr(GetLastError()));
    }
  }
  
  int descriptor(JNIEnv* e, HANDLE h)
  {
    int fd = _open_osfhandle(reinterpret_cast<long>(h), 0);
    if (fd == -1) {
      throwNew(e, "java/io/IOException", strerror(errno));
    }
    return fd;
  }
#endif  
}

#ifdef WIN32
extern "C" JNIEXPORT void JNICALL 
Java_java_lang_Runtime_exec(JNIEnv* e, jclass, 
                            jobjectArray command, jlongArray process)
{
  
  int size = 0;
  for (int i = 0; i < e->GetArrayLength(command); ++i){
    jstring element = (jstring) e->GetObjectArrayElement(command, i);
    size += e->GetStringUTFLength(element) + 1;
  } 
   
  char line[size];
  char* linep = line;
  for (int i = 0; i < e->GetArrayLength(command); ++i) {
    if (i) *(linep++) = _T(' ');
    jstring element = (jstring) e->GetObjectArrayElement(command, i);
    const char* s =  e->GetStringUTFChars(element, 0);
    _tcscpy(linep, s);
    e->ReleaseStringUTFChars(element, s);
    linep += e->GetStringUTFLength(element);
  }
  *(linep++) = _T('\0');
 
  HANDLE in[] = { 0, 0 };
  HANDLE out[] = { 0, 0 };
  HANDLE err[] = { 0, 0 };
  
  makePipe(e, in);
  SetHandleInformation(in[0], HANDLE_FLAG_INHERIT, 0);
  jlong inDescriptor = static_cast<jlong>(descriptor(e, in[0]));
  if(e->ExceptionOccurred()) return;
  e->SetLongArrayRegion(process, 1, 1, &inDescriptor);
  makePipe(e, out);
  SetHandleInformation(out[1], HANDLE_FLAG_INHERIT, 0);
  jlong outDescriptor = static_cast<jlong>(descriptor(e, out[1]));
  if(e->ExceptionOccurred()) return;
  e->SetLongArrayRegion(process, 2, 1, &outDescriptor);
  makePipe(e, err);
  SetHandleInformation(err[0], HANDLE_FLAG_INHERIT, 0);
  jlong errDescriptor = static_cast<jlong>(descriptor(e, err[0]));
  if(e->ExceptionOccurred()) return;
  e->SetLongArrayRegion(process, 3, 1, &errDescriptor);
  
  PROCESS_INFORMATION pi;
  ZeroMemory(&pi, sizeof(pi));
 
  STARTUPINFO si;
  ZeroMemory(&si, sizeof(si));
  si.cb = sizeof(si);
  si.dwFlags = STARTF_USESTDHANDLES;
  si.hStdOutput = in[1];
  si.hStdInput = out[0];
  si.hStdError = err[1];
 
  BOOL success = CreateProcess(0, (LPSTR) line, 0, 0, 1,
                               CREATE_NO_WINDOW | CREATE_UNICODE_ENVIRONMENT,
                               0, 0, &si, &pi);
  
  if (not success) {
    throwNew(e, "java/io/IOException", getErrorStr(GetLastError()));
    return;
  }
  
  jlong pid = reinterpret_cast<jlong>(pi.hProcess);
  e->SetLongArrayRegion(process, 0, 1, &pid);
  
}

extern "C" JNIEXPORT jint JNICALL 
Java_java_lang_Runtime_exitValue(JNIEnv* e, jclass, jlong pid)
{
  DWORD exitCode;
  BOOL success = GetExitCodeProcess(reinterpret_cast<HANDLE>(pid), &exitCode);
  if(not success){
    throwNew(e, "java/lang/Exception", getErrorStr(GetLastError()));
  } else if(exitCode == STILL_ACTIVE){
    throwNew(e, "java/lang/IllegalThreadStateException", "Process is still active");
  }
  return exitCode;
}

extern "C" JNIEXPORT jint JNICALL 
Java_java_lang_Runtime_waitFor(JNIEnv* e, jclass, jlong pid)
{
  DWORD exitCode;
  WaitForSingleObject(reinterpret_cast<HANDLE>(pid), INFINITE);
  BOOL success = GetExitCodeProcess(reinterpret_cast<HANDLE>(pid), &exitCode);
  if(not success){
    throwNew(e, "java/lang/Exception", getErrorStr(GetLastError()));
  }
  return exitCode;
}
#endif

extern "C" JNIEXPORT jstring JNICALL
Java_java_lang_System_getProperty(JNIEnv* e, jclass, jstring name,
                                  jbooleanArray found)
{
  jstring r = 0;
  const char* chars = e->GetStringUTFChars(name, 0);
  if (chars) {
#ifdef WIN32 
    if (strcmp(chars, "line.separator") == 0) {
      r = e->NewStringUTF("\r\n");
    } else if (strcmp(chars, "file.separator") == 0) {
      r = e->NewStringUTF("\\");
    } else if (strcmp(chars, "os.name") == 0) {
      r = e->NewStringUTF("windows");
    } else if (strcmp(chars, "java.io.tmpdir") == 0) {
      TCHAR buffer[MAX_PATH];
      GetTempPath(MAX_PATH, buffer);
      r = e->NewStringUTF(buffer);
    } else if (strcmp(chars, "user.home") == 0) {
      LPWSTR home = _wgetenv(L"USERPROFILE");
      r = e->NewString(reinterpret_cast<jchar*>(home), lstrlenW(home));
    }
#else
    if (strcmp(chars, "line.separator") == 0) {
      r = e->NewStringUTF("\n");
    } else if (strcmp(chars, "file.separator") == 0) {
      r = e->NewStringUTF("/");
    } else if (strcmp(chars, "os.name") == 0) {
      r = e->NewStringUTF("posix");
    } else if (strcmp(chars, "java.io.tmpdir") == 0) {
      r = e->NewStringUTF("/tmp");
    } else if (strcmp(chars, "user.home") == 0) {
      r = e->NewStringUTF(getenv("HOME"));      
    }
#endif

    e->ReleaseStringUTFChars(name, chars);
  }

  if (r) {
    jboolean v = true;
    e->SetBooleanArrayRegion(found, 0, 1, &v);
  }

  return r;
}

extern "C" JNIEXPORT jlong JNICALL
Java_java_lang_System_currentTimeMillis(JNIEnv*, jclass)
{
#ifdef WIN32
  static LARGE_INTEGER frequency;
  static LARGE_INTEGER time;
  static bool init = true;

  if (init) {
    QueryPerformanceFrequency(&frequency);

    if (frequency.QuadPart == 0) {
      return 0;      
    }

    init = false;
  }

  QueryPerformanceCounter(&time);
  return static_cast<int64_t>
    (((static_cast<double>(time.QuadPart)) * 1000.0) /
     (static_cast<double>(frequency.QuadPart)));
#else
  timeval tv = { 0, 0 };
  gettimeofday(&tv, 0);
  return (static_cast<jlong>(tv.tv_sec) * 1000) +
    (static_cast<jlong>(tv.tv_usec) / 1000);
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_java_lang_System_doMapLibraryName(JNIEnv* e, jclass, jstring name)
{
  jstring r = 0;
  const char* chars = e->GetStringUTFChars(name, 0);
  if (chars) {
    unsigned nameLength = strlen(chars);
    unsigned size = sizeof(SO_PREFIX) + nameLength + sizeof(SO_SUFFIX);
    char buffer[size];
    snprintf(buffer, size, SO_PREFIX "%s" SO_SUFFIX, chars);
    r = e->NewStringUTF(buffer);

    e->ReleaseStringUTFChars(name, chars);
  }
  return r;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_java_lang_Math_sin(JNIEnv*, jclass, jdouble val)
{
  return sin(val);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_java_lang_Math_sqrt(JNIEnv*, jclass, jdouble val)
{
  return sqrt(val);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_java_lang_Math_pow(JNIEnv*, jclass, jdouble val, jdouble exp)
{
  return pow(val, exp);
}

extern "C" JNIEXPORT void JNICALL
Java_java_lang_Math_natRandomInitialize(JNIEnv*, jclass, jlong val)
{
#ifdef WIN32
  srand(val);
#else
  srand48(val);
#endif
}

extern "C" JNIEXPORT jdouble JNICALL
Java_java_lang_Math_natRandom(JNIEnv*, jclass)
{
#ifdef WIN32
  return rand();
#else
  return drand48();
#endif
}

extern "C" JNIEXPORT jdouble JNICALL
Java_java_lang_Math_floor(JNIEnv*, jclass, jdouble val)
{
  return floor(val);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_java_lang_Math_ceil(JNIEnv*, jclass, jdouble val)
{
  return ceil(val);
}

extern "C" JNIEXPORT jint JNICALL
Java_java_lang_Double_fillBufferWithDouble(JNIEnv* e, jclass, jdouble val,
					   jbyteArray buffer, jint bufferSize) {
  jboolean isCopy;
  jbyte* buf = e->GetByteArrayElements(buffer, &isCopy);
  jint count = snprintf(reinterpret_cast<char*>(buf), bufferSize, "%g", val);
  e->ReleaseByteArrayElements(buffer, buf, 0);
  return count;
}
