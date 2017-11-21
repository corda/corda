/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "stdlib.h"
#include "time.h"
#include "string.h"
#include "stdio.h"
#include "jni.h"
#include "jni-util.h"
#include "errno.h"
#include "fcntl.h"
#include "ctype.h"

// Make sure M_* constants (in particular M_E) are exposed in math.h.
// This was a problem on the default mingw install on ubuntu precise
#undef __STRICT_ANSI__
#include "math.h"

#ifdef PLATFORM_WINDOWS

#include "windows.h"
#include "winbase.h"
#include "io.h"
#include "tchar.h"
#include "float.h"
#include "sys/types.h"
#include "sys/timeb.h"
#define SO_PREFIX ""
#define SO_SUFFIX ".dll"

#ifdef _MSC_VER
#define snprintf sprintf_s
#define isnan _isnan
#define isfinite _finite
#define strtof strtod
#endif

#else  // not PLATFORM_WINDOWS

#define SO_PREFIX "lib"
#ifdef __APPLE__
#define SO_SUFFIX ".dylib"
#include <TargetConditionals.h>
#if !TARGET_IPHONE_SIMULATOR && !TARGET_OS_IPHONE
#include <CoreServices/CoreServices.h>
#endif
#else
#define SO_SUFFIX ".so"
#endif
#include "unistd.h"
#include "limits.h"
#include "signal.h"
#include "sys/time.h"
#include "sys/types.h"
#ifndef __ANDROID__
#include "sys/sysctl.h"
#endif
#include "sys/utsname.h"
#include "sys/wait.h"

#endif  // not PLATFORM_WINDOWS

#ifndef WINAPI_FAMILY
#ifndef WINAPI_PARTITION_DESKTOP
#define WINAPI_PARTITION_DESKTOP 1
#endif

#ifndef WINAPI_FAMILY_PARTITION
#define WINAPI_FAMILY_PARTITION(x) (x)
#endif
#else
#if !WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)

#include "avian-interop.h"

#endif
#endif  // WINAPI_FAMILY

#ifndef M_E
// in new C++-11 standard math.h doesn't have M_E, at least on MinGW, so define it manually
#define M_E		2.7182818284590452354
#endif  // M_E

namespace {

void add(JNIEnv* e, jobjectArray array, unsigned index, const char* format, ...)
{
  int size = 256;
  while (true) {
    va_list a;
    va_start(a, format);
    RUNTIME_ARRAY(char, buffer, size);
    int r = vsnprintf(RUNTIME_ARRAY_BODY(buffer), size - 1, format, a);
    va_end(a);
    if (r >= 0 and r < size - 1) {
      e->SetObjectArrayElement(
          array, index++, e->NewStringUTF(RUNTIME_ARRAY_BODY(buffer)));
      return;
    }

    size *= 2;
  }
}

#ifdef PLATFORM_WINDOWS

void add(JNIEnv* e,
         jobjectArray array,
         unsigned index,
         const WCHAR* format,
         ...)
{
  int size = 256;
  while (true) {
    va_list a;
    va_start(a, format);
    RUNTIME_ARRAY(WCHAR, buffer, size);
    int r = _vsnwprintf(RUNTIME_ARRAY_BODY(buffer), size - 1, format, a);
    va_end(a);
    if (r >= 0 and r < size - 1) {
      e->SetObjectArrayElement(
          array,
          index++,
          e->NewString(reinterpret_cast<jchar*>(RUNTIME_ARRAY_BODY(buffer)),
                       r));
      return;
    }

    size *= 2;
  }
}

#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
char* getErrorStr(DWORD err)
{
  LPSTR errorStr = 0;
  if (!FormatMessageA(FORMAT_MESSAGE_ALLOCATE_BUFFER
                      | FORMAT_MESSAGE_FROM_SYSTEM
                      | FORMAT_MESSAGE_IGNORE_INSERTS,
                      0,
                      err,
                      LANG_SYSTEM_DEFAULT,
                      (LPSTR)&errorStr,
                      0,
                      0)) {
    char* errStr = (char*)malloc(9 * sizeof(char));
    snprintf(errStr, 9, "%d", (int)err);
    return errStr;
  }
  char* errStr = strdup(errorStr);
  LocalFree(errorStr);
  return errStr;
}
#else
char* getErrorStr(DWORD err)
{
  LPSTR errorStr = (LPSTR)malloc(4096);  // NOTE: something constant
  if (!FormatMessageA(
          FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
          0,
          err,
          LANG_SYSTEM_DEFAULT,
          errorStr,
          0,
          0)) {
    free(errorStr);

    char* errStr = (char*)malloc(9 * sizeof(char));
    snprintf(errStr, 9, "%d", (int)err);
    return errStr;
  }
  char* errStr = strdup(errorStr);
  free(errorStr);
  return errStr;
}
#endif

#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
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
#endif

int descriptor(JNIEnv* e, HANDLE h)
{
  int fd = _open_osfhandle(reinterpret_cast<intptr_t>(h), 0);
  if (fd == -1) {
    throwNewErrno(e, "java/io/IOException");
  }
  return fd;
}
#else
void makePipe(JNIEnv* e, int p[2])
{
  if (pipe(p) != 0) {
    throwNewErrno(e, "java/io/IOException");
  }
}

void safeClose(int& fd)
{
  if (fd != -1)
    close(fd);
  fd = -1;
}

void close(int p[2])
{
  ::close(p[0]);
  ::close(p[1]);
}

void clean(JNIEnv* e, jobjectArray command, char** p)
{
  int i = 0;
  for (char** x = p; *x; ++x, ++i) {
    jstring element = (jstring)e->GetObjectArrayElement(command, i);
    e->ReleaseStringUTFChars(element, *x);
  }
  free(p);
}
#endif
}

class Locale {  // represents an ISO two-char language/country pair
  static const unsigned FIELDLEN = 2;
  static const unsigned FIELDSIZE = FIELDLEN + 1;

  static const char* DEFAULT_LANGUAGE;
  static const char* DEFAULT_REGION;

  char language[FIELDSIZE];
  char region[FIELDSIZE];

  bool isLanguage(const char* language)
  {
    if (!language)
      return false;
    unsigned len = strlen(language);
    if (len != FIELDLEN)
      return false;
    const char* p = language - 1;
    while (islower(*++p))
      ;
    if (*p != '\0')
      return false;
    return true;
  }

  bool isRegion(const char* region)
  {
    if (!region)
      return false;
    unsigned len = strlen(region);
    if (len != FIELDLEN)
      return false;
    const char* p = region - 1;
    while (isupper(*++p))
      ;
    if (*p != '\0')
      return false;
    return true;
  }

 public:
  Locale(const char* language = "")
  {
    Locale l(language, "");
    *this = l;
  }

  Locale(const char* language, const char* region)
  {
    language = isLanguage(language) ? language : DEFAULT_LANGUAGE;
    region = isRegion(region) ? region : DEFAULT_REGION;
    memcpy(this->language, language, FIELDSIZE);
    memcpy(this->region, region, FIELDSIZE);
  }

  Locale& operator=(const Locale& l)
  {
    memcpy(language, l.language, FIELDSIZE);
    memcpy(region, l.region, FIELDSIZE);
    return *this;
  }

  const char* getLanguage()
  {
    return reinterpret_cast<const char*>(language);
  }
  const char* getRegion()
  {
    return reinterpret_cast<const char*>(region);
  }
};
const char* Locale::DEFAULT_LANGUAGE = "en";
const char* Locale::DEFAULT_REGION = "";

#ifdef PLATFORM_WINDOWS

void appendN(char** dest, char ch, size_t length)
{
  for (size_t i = 0; i < length; i++) {
    *((*dest)++) = ch;
  }
}

bool needsEscape(const char* src, size_t length)
{
  const char* end = src + length;
  for (const char* ptr = src; ptr < end; ptr++) {
    switch (*ptr) {
    case ' ':
    case '\t':
    case '\n':
    case '\v':
    case '"':
      return true;
    }
  }

  return false;
}

void copyAndEscape(char** dest, const char* src, size_t length)
{
  char* destp = *dest;
  const char* end = src + length;

  if (length != 0 && !needsEscape(src, length)) {
    for (const char* ptr = src; ptr < end; ptr++) {
      *(destp++) = *ptr;
    }
  } else {
    *(destp++) = '"';

    for (const char* ptr = src;; ptr++) {
      unsigned numBackslashes = 0;

      while (ptr < end && *ptr == '\\') {
        ptr++;
        numBackslashes++;
      }

      if (ptr == end) {
        appendN(&destp, '\\', 2 * numBackslashes);
        break;
      } else if (*ptr == '"') {
        appendN(&destp, '\\', 2 * numBackslashes + 1);
        *(destp++) = *ptr;
      } else {
        appendN(&destp, '\\', numBackslashes);
        *(destp++) = *ptr;
      }
    }

    *(destp++) = '"';
  }

  *dest = destp;
}

extern "C" JNIEXPORT jint JNICALL
    Java_java_lang_Runtime_waitFor(JNIEnv* e, jclass, jlong pid, jlong tid)
{
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
  DWORD exitCode;
  WaitForSingleObject(reinterpret_cast<HANDLE>(pid), INFINITE);
  BOOL success = GetExitCodeProcess(reinterpret_cast<HANDLE>(pid), &exitCode);
  if (not success) {
    throwNew(e, "java/lang/Exception", getErrorStr(GetLastError()));
  }

  CloseHandle(reinterpret_cast<HANDLE>(pid));
  CloseHandle(reinterpret_cast<HANDLE>(tid));

  return exitCode;
#else
  throwNew(e, "java/io/Exception", strdup("Not supported on WinRT/WinPhone8"));
  return -1;
#endif
}

extern "C" JNIEXPORT void JNICALL
    Java_java_lang_Runtime_kill(JNIEnv* e UNUSED, jclass, jlong pid)
{
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
  TerminateProcess(reinterpret_cast<HANDLE>(pid), 1);
#else
  throwNew(e, "java/io/Exception", strdup("Not supported on WinRT/WinPhone8"));
#endif
}

Locale getLocale()
{
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
  const char* lang = "";
  const char* reg = "";
  unsigned langid = GetUserDefaultUILanguage();
  unsigned prilang = langid & 0x3ff;
  unsigned sublang = langid >> 10;

  switch (prilang) {
  case 0x004: {
    lang = "zh";
    switch (sublang) {
    case 0x01:
      reg = "CN";
      break;
    case 0x02:
      reg = "TW";
      break;
    case 0x03:
      reg = "HK";
      break;
    case 0x04:
      reg = "SG";
      break;
    }
  } break;
  case 0x006:
    lang = "da";
    reg = "DK";
    break;
  case 0x007:
    lang = "de";
    reg = "DE";
    break;
  case 0x009: {
    lang = "en";
    switch (sublang) {
    case 0x01:
      reg = "US";
      break;
    case 0x02:
      reg = "GB";
      break;
    case 0x03:
      reg = "AU";
      break;
    case 0x04:
      reg = "CA";
      break;
    case 0x05:
      reg = "NZ";
      break;
    case 0x06:
      reg = "IE";
      break;
    case 0x07:
      reg = "ZA";
      break;
    case 0x10:
      reg = "IN";
      break;
    }
  } break;
  case 0x00a: {
    lang = "es";
    switch (sublang) {
    case 0x01:
    case 0x03:
      reg = "ES";
      break;
    case 0x02:
      reg = "MX";
      break;
    }
  } break;
  case 0x00c: {
    lang = "fr";
    switch (sublang) {
    case 0x01:
      reg = "FR";
      break;
    case 0x02:
      reg = "BE";
      break;
    case 0x03:
      reg = "CA";
      break;
    }
  } break;
  case 0x010:
    lang = "it";
    reg = "IT";
    break;
  case 0x011:
    lang = "ja";
    reg = "JP";
    break;
  case 0x012:
    lang = "ko";
    reg = "KR";
    break;
  case 0x013: {
    lang = "nl";
    switch (sublang) {
    case 0x01:
      reg = "NL";
      break;
    case 0x02:
      reg = "BE";
      break;
    }
  } break;
  case 0x014:
    lang = "no";
    reg = "NO";
    break;
  case 0x015:
    lang = "pl";
    reg = "PL";
    break;
  case 0x016: {
    lang = "pt";
    switch (sublang) {
    case 0x01:
      reg = "BR";
      break;
    case 0x02:
      reg = "PT";
      break;
    }
  } break;
  case 0x018:
    lang = "ro";
    reg = "RO";
    break;
  case 0x019:
    lang = "ru";
    reg = "RU";
    break;
  case 0x01d:
    lang = "sv";
    reg = "SE";
    break;
  default:
    lang = "en";
  }

  return Locale(lang, reg);
#else
  std::wstring culture = AvianInterop::GetCurrentUICulture();
  char* cultureName
      = strdup(std::string(culture.begin(), culture.end()).c_str());
  char* delimiter = strchr(cultureName, '-');
  if (!delimiter) {
    free(cultureName);
    return Locale("en", "US");
  }
  const char* lang = cultureName;
  const char* reg = delimiter + 1;
  *delimiter = 0;
  Locale locale(lang, reg);
  free(cultureName);
  return locale;
#endif
}
#else
extern "C" JNIEXPORT void JNICALL
    Java_java_lang_Runtime_exec(JNIEnv* e,
                                jclass,
                                jobjectArray command,
                                jlongArray process)
{
  char** argv = static_cast<char**>(
      malloc((e->GetArrayLength(command) + 1) * sizeof(char*)));
  int i;
  for (i = 0; i < e->GetArrayLength(command); i++) {
    jstring element = (jstring)e->GetObjectArrayElement(command, i);
    char* s = const_cast<char*>(e->GetStringUTFChars(element, 0));
    argv[i] = s;
  }
  argv[i] = 0;

  int in[] = {-1, -1};
  int out[] = {-1, -1};
  int err[] = {-1, -1};
  int msg[] = {-1, -1};

  makePipe(e, in);
  if (e->ExceptionCheck())
    return;
  jlong inDescriptor = static_cast<jlong>(in[0]);
  e->SetLongArrayRegion(process, 2, 1, &inDescriptor);
  makePipe(e, out);
  if (e->ExceptionCheck())
    return;
  jlong outDescriptor = static_cast<jlong>(out[1]);
  e->SetLongArrayRegion(process, 3, 1, &outDescriptor);
  makePipe(e, err);
  if (e->ExceptionCheck())
    return;
  jlong errDescriptor = static_cast<jlong>(err[0]);
  e->SetLongArrayRegion(process, 4, 1, &errDescriptor);
  makePipe(e, msg);
  if (e->ExceptionCheck())
    return;
  if (fcntl(msg[1], F_SETFD, FD_CLOEXEC) != 0) {
    throwNewErrno(e, "java/io/IOException");
    return;
  }

#ifdef __QNX__
  // fork(2) doesn't work in multithreaded QNX programs.  See
  // http://www.qnx.com/developers/docs/6.4.1/neutrino/getting_started/s1_procs.html
  pid_t pid = vfork();
#else
  // We might be able to just use vfork on all UNIX-style systems, but
  // the manual makes it sound dangerous due to the shared
  // parent/child address space, so we use fork if we can.
  pid_t pid = fork();
#endif
  switch (pid) {
  case -1:  // error
    throwNewErrno(e, "java/io/IOException");
    return;
  case 0: {  // child
    // Setup stdin, stdout and stderr
    dup2(in[1], 1);
    close(in);
    dup2(out[0], 0);
    close(out);
    dup2(err[1], 2);
    close(err);
    close(msg[0]);

    execvp(argv[0], argv);

    // Error if here
    int val = errno;
    ssize_t rv UNUSED = write(msg[1], &val, sizeof(val));
    exit(127);
  } break;

  default: {  // parent
    jlong JNIPid = static_cast<jlong>(pid);
    e->SetLongArrayRegion(process, 0, 1, &JNIPid);

    safeClose(in[1]);
    safeClose(out[0]);
    safeClose(err[1]);
    safeClose(msg[1]);

    int val;
    int r = read(msg[0], &val, sizeof(val));
    if (r == -1) {
      throwNewErrno(e, "java/io/IOException");
      return;
    } else if (r) {
      errno = val;
      throwNewErrno(e, "java/io/IOException");
      return;
    }
  } break;
  }

  safeClose(msg[0]);
  clean(e, command, argv);

  fcntl(in[0], F_SETFD, FD_CLOEXEC);
  fcntl(out[1], F_SETFD, FD_CLOEXEC);
  fcntl(err[0], F_SETFD, FD_CLOEXEC);
}

extern "C" JNIEXPORT jint JNICALL
    Java_java_lang_Runtime_waitFor(JNIEnv*, jclass, jlong pid, jlong)
{
  bool finished = false;
  int status;
  int exitCode;
  while (!finished) {
    waitpid(pid, &status, 0);
    if (WIFEXITED(status)) {
      finished = true;
      exitCode = WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
      finished = true;
      exitCode = -1;
    }
  }

  return exitCode;
}

extern "C" JNIEXPORT void JNICALL
    Java_java_lang_Runtime_kill(JNIEnv*, jclass, jlong pid)
{
  kill((pid_t)pid, SIGTERM);
}

Locale getLocale()
{
  Locale fallback;

  const char* LANG = getenv("LANG");
  if (!LANG || strcmp(LANG, "C") == 0)
    return fallback;

  int len = strlen(LANG);
  char buf[len + 1];  // + 1 for the '\0' char
  memcpy(buf, LANG, len + 1);

  char* tracer = buf;
  const char* reg;

  while (*tracer && *tracer != '_')
    ++tracer;
  if (!*tracer)
    return fallback;
  *tracer = '\0';
  reg = ++tracer;

  while (*tracer && *tracer != '.')
    ++tracer;
  if (tracer == reg)
    return fallback;
  *tracer = '\0';

  Locale locale(buf, reg);
  return locale;
}
#endif

extern "C" JNIEXPORT jobjectArray JNICALL
    Java_java_lang_System_getNativeProperties(JNIEnv* e, jclass)
{
  jobjectArray array
      = e->NewObjectArray(32, e->FindClass("java/lang/String"), 0);

  unsigned index = 0;

#ifdef ARCH_x86_32
  e->SetObjectArrayElement(array, index++, e->NewStringUTF("os.arch=x86"));

#elif defined ARCH_x86_64
  e->SetObjectArrayElement(array, index++, e->NewStringUTF("os.arch=x86_64"));

#elif defined ARCH_arm
  e->SetObjectArrayElement(array, index++, e->NewStringUTF("os.arch=arm"));

#elif defined ARCH_arm64
  e->SetObjectArrayElement(array, index++, e->NewStringUTF("os.arch=arm64"));

#else
#error "unknown architecture"

#endif

#ifdef PLATFORM_WINDOWS
  e->SetObjectArrayElement(
      array, index++, e->NewStringUTF("line.separator=\r\n"));

  e->SetObjectArrayElement(
      array, index++, e->NewStringUTF("file.separator=\\"));

  e->SetObjectArrayElement(array, index++, e->NewStringUTF("path.separator=;"));

#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
  e->SetObjectArrayElement(array, index++, e->NewStringUTF("os.name=Windows"));

#elif WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_PHONE)
  e->SetObjectArrayElement(
      array, index++, e->NewStringUTF("os.name=Windows Phone"));

#else
  e->SetObjectArrayElement(
      array, index++, e->NewStringUTF("os.name=Windows RT"));

#endif

#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
  {
    OSVERSIONINFO OSversion;
    OSversion.dwOSVersionInfoSize = sizeof(OSVERSIONINFO);
    ::GetVersionEx(&OSversion);

    add(e,
        array,
        index++,
        "os.version=%i.%i",
        static_cast<int>(OSversion.dwMajorVersion),
        static_cast<int>(OSversion.dwMinorVersion));
  }

#else
  // Currently there is no alternative on WinRT/WP8
  e->SetObjectArrayElement(array, index++, e->NewStringUTF("os.version=8.0"));

#endif

#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
  {
    WCHAR buffer[MAX_PATH];
    GetTempPathW(MAX_PATH, buffer);
    add(e, array, index++, L"java.io.tmpdir=%ls", buffer);
  }

#else
  add(e,
      array,
      index++,
      L"java.io.tmpdir=%ls",
      AvianInterop::GetTemporaryFolder().c_str());

#endif

#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
  {
    WCHAR buffer[MAX_PATH];
    GetCurrentDirectoryW(MAX_PATH, buffer);
    add(e, array, index++, L"user.dir=%ls", buffer);
  }

#else
  add(e,
      array,
      index++,
      L"user.dir=%ls",
      AvianInterop::GetInstalledLocation().c_str());

#endif

#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
#ifdef _MSC_VER
  {
    WCHAR buffer[MAX_PATH];
    size_t needed;
    if (_wgetenv_s(&needed, buffer, MAX_PATH, L"USERPROFILE") == 0) {
      add(e, array, index++, L"user.home=%ls", buffer);
    }
  }

#else
  add(e, array, index++, L"user.home=%ls", _wgetenv(L"USERPROFILE"));

#endif
#else
  add(e,
      array,
      index++,
      L"user.home=%ls",
      AvianInterop::GetDocumentsLibraryLocation().c_str());

#endif

#else  // not Windows
  e->SetObjectArrayElement(
      array, index++, e->NewStringUTF("line.separator=\n"));

  e->SetObjectArrayElement(array, index++, e->NewStringUTF("file.separator=/"));

  e->SetObjectArrayElement(array, index++, e->NewStringUTF("path.separator=:"));

#ifdef __APPLE__
  e->SetObjectArrayElement(array, index++, e->NewStringUTF("os.name=Mac OS X"));

#elif defined __FreeBSD__
  e->SetObjectArrayElement(array, index++, e->NewStringUTF("os.name=FreeBSD"));

#else
  e->SetObjectArrayElement(array, index++, e->NewStringUTF("os.name=Linux"));

#endif
  {
    struct utsname system_id;
    uname(&system_id);
    add(e, array, index++, "os.version=%s", system_id.release);
  }

  e->SetObjectArrayElement(
      array, index++, e->NewStringUTF("java.io.tmpdir=/tmp"));

  {
    char buffer[PATH_MAX];
    add(e, array, index++, "user.dir=%s", getcwd(buffer, PATH_MAX));
  }

  add(e, array, index++, "user.home=%s", getenv("HOME"));

#endif  // not Windows

  {
    Locale locale = getLocale();
    add(e, array, index++, "user.language=%s", locale.getLanguage());
    add(e, array, index++, "user.region=%s", locale.getRegion());
  }

  return array;
}

extern "C" JNIEXPORT jstring JNICALL
    Java_java_lang_System_doMapLibraryName(JNIEnv* e, jclass, jstring name)
{
  jstring r = 0;
  const char* chars = e->GetStringUTFChars(name, 0);
  if (chars) {
    unsigned nameLength = strlen(chars);
    unsigned size = sizeof(SO_PREFIX) + nameLength + sizeof(SO_SUFFIX);
    RUNTIME_ARRAY(char, buffer, size);
    snprintf(RUNTIME_ARRAY_BODY(buffer), size, SO_PREFIX "%s" SO_SUFFIX, chars);
    r = e->NewStringUTF(RUNTIME_ARRAY_BODY(buffer));

    e->ReleaseStringUTFChars(name, chars);
  }
  return r;
}

extern "C" JNIEXPORT jboolean JNICALL
    Java_java_lang_Double_isInfinite(JNIEnv*, jclass, jdouble val)
{
  return !isfinite(val);
}

extern "C" JNIEXPORT jboolean JNICALL
    Java_java_lang_Double_isNaN(JNIEnv*, jclass, jdouble val)
{
  return isnan(val);
}

extern "C" JNIEXPORT jdouble JNICALL
    Java_java_lang_Double_doubleFromString(JNIEnv* e,
                                           jclass,
                                           jstring s,
                                           jintArray numDoublesRead)
{
  const char* chars = e->GetStringUTFChars(s, 0);
  double d = 0.0;
  jint numRead = 0;

  if (chars) {
    char* lastRead;
    d = strtod(chars, &lastRead);
    if ((lastRead != chars) && ((chars + strlen(chars)) == lastRead)) {
      numRead = 1;
    }
    e->ReleaseStringUTFChars(s, chars);
  }
  e->SetIntArrayRegion(numDoublesRead, 0, 1, &numRead);
  return d;
}

extern "C" JNIEXPORT jboolean JNICALL
    Java_java_lang_Float_isInfinite(JNIEnv*, jclass, jfloat val)
{
  return !isfinite(val);
}

extern "C" JNIEXPORT jboolean JNICALL
    Java_java_lang_Float_isNaN(JNIEnv*, jclass, jfloat val)
{
  return isnan(val);
}

extern "C" JNIEXPORT jfloat JNICALL
    Java_java_lang_Float_floatFromString(JNIEnv* e,
                                         jclass,
                                         jstring s,
                                         jintArray numFloatsRead)
{
  const char* chars = e->GetStringUTFChars(s, 0);
  float f = 0.0;
  jint numRead = 0;

  if (chars) {
    char* lastRead;
    f = strtof(chars, &lastRead);
    if ((lastRead != chars) && ((chars + strlen(chars)) == lastRead)) {
      numRead = 1;
    }
    e->ReleaseStringUTFChars(s, chars);
  }
  e->SetIntArrayRegion(numFloatsRead, 0, 1, &numRead);
  return f;
}

extern "C" JNIEXPORT jdouble JNICALL
    Java_java_lang_Math_sin(JNIEnv*, jclass, jdouble val)
{
  return sin(val);
}

extern "C" JNIEXPORT jdouble JNICALL
    Java_java_lang_Math_cos(JNIEnv*, jclass, jdouble val)
{
  return cos(val);
}

extern "C" JNIEXPORT jdouble JNICALL
    Java_java_lang_Math_tan(JNIEnv*, jclass, jdouble val)
{
  return tan(val);
}

extern "C" JNIEXPORT jdouble JNICALL
    Java_java_lang_Math_asin(JNIEnv*, jclass, jdouble val)
{
  return asin(val);
}

extern "C" JNIEXPORT jdouble JNICALL
    Java_java_lang_Math_acos(JNIEnv*, jclass, jdouble val)
{
  return acos(val);
}

extern "C" JNIEXPORT jdouble JNICALL
    Java_java_lang_Math_atan(JNIEnv*, jclass, jdouble val)
{
  return atan(val);
}

extern "C" JNIEXPORT jdouble JNICALL
    Java_java_lang_Math_atan2(JNIEnv*, jclass, jdouble y, jdouble x)
{
  return atan2(y, x);
}

extern "C" JNIEXPORT jdouble JNICALL
    Java_java_lang_Math_sinh(JNIEnv*, jclass, jdouble val)
{
  return sinh(val);
}

extern "C" JNIEXPORT jdouble JNICALL
    Java_java_lang_Math_cosh(JNIEnv*, jclass, jdouble val)
{
  return cosh(val);
}

extern "C" JNIEXPORT jdouble JNICALL
    Java_java_lang_Math_tanh(JNIEnv*, jclass, jdouble val)
{
  return tanh(val);
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

extern "C" JNIEXPORT jdouble JNICALL
    Java_java_lang_Math_log(JNIEnv*, jclass, jdouble val)
{
  return log(val);
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

extern "C" JNIEXPORT jdouble JNICALL
    Java_java_lang_Math_exp(JNIEnv*, jclass, jdouble exp)
{
  return pow(M_E, exp);
}

extern "C" JNIEXPORT jint JNICALL
    Java_java_lang_Double_fillBufferWithDouble(JNIEnv* e,
                                               jclass,
                                               jdouble val,
                                               jbyteArray buffer,
                                               jint bufferSize)
{
  jboolean isCopy;
  jbyte* buf = e->GetByteArrayElements(buffer, &isCopy);
  jint count = snprintf(reinterpret_cast<char*>(buf), bufferSize, "%g", val);
  e->ReleaseByteArrayElements(buffer, buf, 0);
  return count;
}
