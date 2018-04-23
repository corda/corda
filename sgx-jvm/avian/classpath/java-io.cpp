/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>

#include "jni.h"
#include "jni-util.h"

#ifdef PLATFORM_WINDOWS

#define UNICODE

#include <windows.h>
#include <io.h>
#include <direct.h>
#include <share.h>

#define ACCESS _waccess
#define CLOSE _close
#define READ _read
#define WRITE _write
#define STAT _wstat
#define STRUCT_STAT struct _stat
#define MKDIR(path, mode) _wmkdir(path)
#define CHMOD(path, mode) _wchmod(path, mode)
#define REMOVE _wremove
#define RENAME _wrename
#define OPEN_MASK O_BINARY

#define CHECK_X_OK R_OK

#ifdef _MSC_VER
#define S_ISREG(x) ((x)&_S_IFREG)
#define S_ISDIR(x) ((x)&_S_IFDIR)
#define S_IRUSR _S_IREAD
#define S_IWUSR _S_IWRITE
#define W_OK 2
#define R_OK 4
#else
#define OPEN _wopen
#endif

#define GET_CHARS GetStringChars
#define RELEASE_CHARS(path, chars) \
  ReleaseStringChars(path, reinterpret_cast<const jchar*>(chars))

typedef wchar_t char_t;

#if defined(WINAPI_FAMILY)
#if !WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)

#include "avian-interop.h"
#define SKIP_OPERATOR_NEW

#endif
#endif

#else  // not PLATFORM_WINDOWS

#include <dirent.h>
#include <unistd.h>
#include "sys/mman.h"

#define ACCESS access
#define OPEN open
#define CLOSE close
#define READ read
#define WRITE write
#define STAT stat
#define STRUCT_STAT struct stat
#define MKDIR mkdir
#define CHMOD chmod
#define REMOVE remove
#define RENAME rename
#define OPEN_MASK 0

#define CHECK_X_OK X_OK

#define GET_CHARS GetStringUTFChars
#define RELEASE_CHARS ReleaseStringUTFChars

typedef char char_t;

#endif  // not PLATFORM_WINDOWS

#ifndef WINAPI_FAMILY
#ifndef WINAPI_PARTITION_DESKTOP
#define WINAPI_PARTITION_DESKTOP 1
#endif

#ifndef WINAPI_FAMILY_PARTITION
#define WINAPI_FAMILY_PARTITION(x) (x)
#endif
#endif  // WINAPI_FAMILY

#if !defined(SKIP_OPERATOR_NEW)
inline void* operator new(size_t, void* p) throw()
{
  return p;
}
#endif

typedef const char_t* string_t;

namespace {

#ifdef _MSC_VER
inline int OPEN(string_t path, int mask, int mode)
{
  int fd;
  if (_wsopen_s(&fd, path, mask, _SH_DENYNO, mode) == 0) {
    return fd;
  } else {
    return -1;
  }
}
#endif

inline bool exists(string_t path)
{
#ifdef PLATFORM_WINDOWS
  return GetFileAttributesW(path) != INVALID_FILE_ATTRIBUTES;
#else
  STRUCT_STAT s;
  return STAT(path, &s) == 0;
#endif
}

inline int doOpen(JNIEnv* e, string_t path, int mask)
{
  int fd = OPEN(path, mask | OPEN_MASK, S_IRUSR | S_IWUSR);
  if (fd == -1) {
    if (errno == ENOENT) {
      throwNewErrno(e, "java/io/FileNotFoundException");
    } else {
      throwNewErrno(e, "java/io/IOException");
    }
  }
  return fd;
}

inline void doClose(JNIEnv* e, jint fd)
{
  int r = CLOSE(fd);
  if (r == -1) {
    throwNewErrno(e, "java/io/IOException");
  }
}

inline int doRead(JNIEnv* e, jint fd, jbyte* data, jint length)
{
  int r = READ(fd, data, length);
  if (r > 0) {
    return r;
  } else if (r == 0) {
    return -1;
  } else {
    throwNewErrno(e, "java/io/IOException");
    return 0;
  }
}

inline void doWrite(JNIEnv* e, jint fd, const jbyte* data, jint length)
{
  int r = WRITE(fd, data, length);
  if (r != length) {
    throwNewErrno(e, "java/io/IOException");
  }
}

#ifdef PLATFORM_WINDOWS

class Directory {
 public:
  Directory() : handle(0), findNext(false)
  {
  }

  virtual string_t next()
  {
    if (handle and handle != INVALID_HANDLE_VALUE) {
      if (findNext) {
        if (FindNextFileW(handle, &data)) {
          return data.cFileName;
        }
      } else {
        findNext = true;
        return data.cFileName;
      }
    }
    return 0;
  }

  virtual void dispose()
  {
    if (handle and handle != INVALID_HANDLE_VALUE) {
      FindClose(handle);
    }
    free(this);
  }

  HANDLE handle;
  WIN32_FIND_DATAW data;
  bool findNext;
};

#else  // not PLATFORM_WINDOWS

#endif  // not PLATFORM_WINDOWS

}  // namespace

static inline string_t getChars(JNIEnv* e, jstring path)
{
  return reinterpret_cast<string_t>(e->GET_CHARS(path, 0));
}

static inline void releaseChars(JNIEnv* e, jstring path, string_t chars)
{
  e->RELEASE_CHARS(path, chars);
}

#ifndef SGX

extern "C" JNIEXPORT jstring JNICALL
    Java_java_io_File_toCanonicalPath(JNIEnv* /*e*/, jclass, jstring path)
{
  // todo
  return path;
}

extern "C" JNIEXPORT jstring JNICALL
    Java_java_io_File_toAbsolutePath(JNIEnv* e UNUSED, jclass, jstring path)
{
#ifdef PLATFORM_WINDOWS
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
  string_t chars = getChars(e, path);
  if (chars) {
    const unsigned BufferSize = MAX_PATH;
    char_t buffer[BufferSize];
    DWORD success = GetFullPathNameW(chars, BufferSize, buffer, 0);
    releaseChars(e, path, chars);

    if (success) {
      return e->NewString(reinterpret_cast<const jchar*>(buffer),
                          wcslen(buffer));
    }
  }

  return path;
#else
  string_t chars = getChars(e, path);
  if (chars) {
    std::wstring partialPath = chars;
    releaseChars(e, path, chars);

    std::wstring fullPath = AvianInterop::GetFullPath(partialPath);

    return e->NewString(reinterpret_cast<const jchar*>(fullPath.c_str()),
                        fullPath.length());
  }
  return path;
#endif
#else
  jstring result = path;
  string_t chars = getChars(e, path);
  if (chars) {
    if (chars[0] != '/') {
      char* cwd = getcwd(NULL, 0);
      if (cwd) {
        unsigned size = strlen(cwd) + strlen(chars) + 2;
        RUNTIME_ARRAY(char, buffer, size);
        snprintf(RUNTIME_ARRAY_BODY(buffer), size, "%s/%s", cwd, chars);
        result = e->NewStringUTF(RUNTIME_ARRAY_BODY(buffer));
        free(cwd);
      }
    }
    releaseChars(e, path, chars);
  }
  return result;
#endif
}

extern "C" JNIEXPORT jlong JNICALL
    Java_java_io_File_length(JNIEnv* e, jclass, jstring path)
{
#ifdef PLATFORM_WINDOWS
  // Option: without opening file
  // http://msdn.microsoft.com/en-us/library/windows/desktop/aa364946(v=vs.85).aspx
  string_t chars = getChars(e, path);
  if (chars) {
    LARGE_INTEGER fileSize;
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
    HANDLE file = CreateFileW(
        chars, FILE_READ_DATA, FILE_SHARE_READ, 0, OPEN_EXISTING, 0, 0);
#else
    HANDLE file = CreateFile2(
        chars, GENERIC_READ, FILE_SHARE_READ, OPEN_EXISTING, nullptr);
#endif
    releaseChars(e, path, chars);
    if (file == INVALID_HANDLE_VALUE)
      return 0;
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
    if (!GetFileSizeEx(file, &fileSize)) {
      CloseHandle(file);
      return 0;
    }
#else
    FILE_STANDARD_INFO info;
    if (!GetFileInformationByHandleEx(
            file, FileStandardInfo, &info, sizeof(info))) {
      CloseHandle(file);
      return 0;
    }
    fileSize = info.EndOfFile;
#endif

    CloseHandle(file);
    return static_cast<jlong>(fileSize.QuadPart);
  }
#else

  string_t chars = getChars(e, path);
  if (chars) {
    STRUCT_STAT s;
    int r = STAT(chars, &s);
    releaseChars(e, path, chars);
    if (r == 0) {
      return s.st_size;
    }
  }

#endif

  return 0;
}

extern "C" JNIEXPORT jboolean JNICALL
    Java_java_io_File_canRead(JNIEnv* e, jclass, jstring path)
{
  string_t chars = getChars(e, path);
  if (chars) {
    int r = ACCESS(chars, R_OK);
    releaseChars(e, path, chars);
    return (r == 0);
  }
  return false;
}

extern "C" JNIEXPORT jboolean JNICALL
    Java_java_io_File_canWrite(JNIEnv* e, jclass, jstring path)
{
  string_t chars = getChars(e, path);
  if (chars) {
    int r = ACCESS(chars, W_OK);
    releaseChars(e, path, chars);
    return (r == 0);
  }
  return false;
}

extern "C" JNIEXPORT jboolean JNICALL
    Java_java_io_File_canExecute(JNIEnv* e, jclass, jstring path)
{
  string_t chars = getChars(e, path);
  if (chars) {
    int r = ACCESS(chars, CHECK_X_OK);
    releaseChars(e, path, chars);
    return (r == 0);
  }
  return false;
}

#ifndef PLATFORM_WINDOWS
extern "C" JNIEXPORT jboolean JNICALL
    Java_java_io_File_setExecutable(JNIEnv* e,
                                    jclass,
                                    jstring path,
                                    jboolean executable,
                                    jboolean ownerOnly)
{
  string_t chars = getChars(e, path);
  if (chars) {
    jboolean v;
    int mask;
    if (ownerOnly) {
      mask = S_IXUSR;
    } else {
      mask = S_IXUSR | S_IXGRP | S_IXOTH;
    }

    STRUCT_STAT s;
    int r = STAT(chars, &s);
    if (r == 0) {
      int mode = s.st_mode;
      if (executable) {
        mode |= mask;
      } else {
        mode &= ~mask;
      }
      if (CHMOD(chars, mode) != 0) {
        v = false;
      } else {
        v = true;
      }
    } else {
      v = false;
    }
    releaseChars(e, path, chars);
    return v;
  }
  return false;
}

#else  // ifndef PLATFORM_WINDOWS

extern "C" JNIEXPORT jboolean JNICALL
    Java_java_io_File_setExecutable(JNIEnv*,
                                    jclass,
                                    jstring,
                                    jboolean executable,
                                    jboolean)
{
  return executable;
}

#endif

extern "C" JNIEXPORT jboolean JNICALL
    Java_java_io_File_isDirectory(JNIEnv* e, jclass, jstring path)
{
  string_t chars = getChars(e, path);
  if (chars) {
    STRUCT_STAT s;
    int r = STAT(chars, &s);
    bool v = (r == 0 and S_ISDIR(s.st_mode));
    releaseChars(e, path, chars);
    return v;
  } else {
    return false;
  }
}

extern "C" JNIEXPORT jboolean JNICALL
    Java_java_io_File_isFile(JNIEnv* e, jclass, jstring path)
{
  string_t chars = getChars(e, path);
  if (chars) {
    STRUCT_STAT s;
    int r = STAT(chars, &s);
    bool v = (r == 0 and S_ISREG(s.st_mode));
    releaseChars(e, path, chars);
    return v;
  } else {
    return false;
  }
}

extern "C" JNIEXPORT jboolean JNICALL
    Java_java_io_File_exists(JNIEnv* e, jclass, jstring path)
{
  string_t chars = getChars(e, path);
  if (chars) {
    bool v = exists(chars);
    releaseChars(e, path, chars);
    return v;
  } else {
    return false;
  }
}

extern "C" JNIEXPORT jlong JNICALL
    Java_java_io_File_lastModified(JNIEnv* e, jclass, jstring path)
{
  string_t chars = getChars(e, path);
  if (chars) {
#ifdef PLATFORM_WINDOWS
// Option: without opening file
// http://msdn.microsoft.com/en-us/library/windows/desktop/aa364946(v=vs.85).aspx
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
    HANDLE hFile = CreateFileW(
        chars, FILE_READ_DATA, FILE_SHARE_READ, 0, OPEN_EXISTING, 0, 0);
#else
    HANDLE hFile = CreateFile2(
        chars, GENERIC_READ, FILE_SHARE_READ, OPEN_EXISTING, nullptr);
#endif
    releaseChars(e, path, chars);
    if (hFile == INVALID_HANDLE_VALUE)
      return 0;
    LARGE_INTEGER fileDate, filetimeToUnixEpochAdjustment;
    filetimeToUnixEpochAdjustment.QuadPart = 11644473600000L * 10000L;
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
    FILETIME fileLastWriteTime;
    if (!GetFileTime(hFile, 0, 0, &fileLastWriteTime)) {
      CloseHandle(hFile);
      return 0;
    }
    fileDate.HighPart = fileLastWriteTime.dwHighDateTime;
    fileDate.LowPart = fileLastWriteTime.dwLowDateTime;
#else
    FILE_BASIC_INFO fileInfo;
    if (!GetFileInformationByHandleEx(
            hFile, FileBasicInfo, &fileInfo, sizeof(fileInfo))) {
      CloseHandle(hFile);
      return 0;
    }
    fileDate = fileInfo.ChangeTime;
#endif
    CloseHandle(hFile);
    fileDate.QuadPart -= filetimeToUnixEpochAdjustment.QuadPart;
    return fileDate.QuadPart / 10000000L;
#else
    struct stat fileStat;
    int res = stat(chars, &fileStat);
    releaseChars(e, path, chars);

    if (res == -1) {
      return 0;
    }
#ifdef __APPLE__
#define MTIME st_mtimespec
#else
#define MTIME st_mtim
#endif
    return (static_cast<jlong>(fileStat.MTIME.tv_sec) * 1000)
           + (static_cast<jlong>(fileStat.MTIME.tv_nsec) / (1000 * 1000));
#endif
  }

  return 0;
}

#ifdef PLATFORM_WINDOWS

extern "C" JNIEXPORT jlong JNICALL
    Java_java_io_File_openDir(JNIEnv* e, jclass, jstring path)
{
  string_t chars = getChars(e, path);
  if (chars) {
    unsigned length = wcslen(chars);
    unsigned size = length * sizeof(char_t);

    RUNTIME_ARRAY(char_t, buffer, length + 3);
    memcpy(RUNTIME_ARRAY_BODY(buffer), chars, size);
    memcpy(RUNTIME_ARRAY_BODY(buffer) + length, L"\\*", 6);

    releaseChars(e, path, chars);

    Directory* d = new (malloc(sizeof(Directory))) Directory;
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
    d->handle = FindFirstFileW(RUNTIME_ARRAY_BODY(buffer), &(d->data));
#else
    d->handle = FindFirstFileExW(RUNTIME_ARRAY_BODY(buffer),
                                 FindExInfoStandard,
                                 &(d->data),
                                 FindExSearchNameMatch,
                                 NULL,
                                 0);
#endif
    if (d->handle == INVALID_HANDLE_VALUE) {
      d->dispose();
      d = 0;
    }

    return reinterpret_cast<jlong>(d);
  } else {
    return 0;
  }
}

extern "C" JNIEXPORT jstring JNICALL
    Java_java_io_File_readDir(JNIEnv* e, jclass, jlong handle)
{
  Directory* d = reinterpret_cast<Directory*>(handle);

  while (true) {
    string_t s = d->next();
    if (s) {
      if (wcscmp(s, L".") == 0 || wcscmp(s, L"..") == 0) {
        // skip . or .. and try again
      } else {
        return e->NewString(reinterpret_cast<const jchar*>(s), wcslen(s));
      }
    } else {
      return 0;
    }
  }
}

extern "C" JNIEXPORT void JNICALL
    Java_java_io_File_closeDir(JNIEnv*, jclass, jlong handle)
{
  reinterpret_cast<Directory*>(handle)->dispose();
}

#else  // not PLATFORM_WINDOWS

extern "C" JNIEXPORT jlong JNICALL
    Java_java_io_File_openDir(JNIEnv* e, jclass, jstring path)
{
  string_t chars = getChars(e, path);
  if (chars) {
    jlong handle = reinterpret_cast<jlong>(opendir(chars));
    releaseChars(e, path, chars);
    return handle;
  } else {
    return 0;
  }
}

extern "C" JNIEXPORT jstring JNICALL
    Java_java_io_File_readDir(JNIEnv* e, jclass, jlong handle)
{
  struct dirent* directoryEntry;

  if (handle != 0) {
    while (true) {
      directoryEntry = readdir(reinterpret_cast<DIR*>(handle));
      if (directoryEntry == NULL) {
        return NULL;
      } else if (strcmp(directoryEntry->d_name, ".") == 0
                 || strcmp(directoryEntry->d_name, "..") == 0) {
        // skip . or .. and try again
      } else {
        return e->NewStringUTF(directoryEntry->d_name);
      }
    }
  }
  return NULL;
}

extern "C" JNIEXPORT void JNICALL
    Java_java_io_File_closeDir(JNIEnv*, jclass, jlong handle)
{
  if (handle != 0) {
    closedir(reinterpret_cast<DIR*>(handle));
  }
}

#endif  // not PLATFORM_WINDOWS

extern "C" JNIEXPORT jint JNICALL
    Java_java_io_FileInputStream_open(JNIEnv* e, jclass, jstring path)
{
  string_t chars = getChars(e, path);
  if (chars) {
    int fd = doOpen(e, chars, O_RDONLY);
    releaseChars(e, path, chars);
    return fd;
  } else {
    return -1;
  }
}

extern "C" JNIEXPORT jint JNICALL
    Java_java_io_FileInputStream_read__I(JNIEnv* e, jclass, jint fd)
{
  jbyte data;
  int r = doRead(e, fd, &data, 1);
  if (r <= 0) {
    return -1;
  } else {
    return data & 0xff;
  }
}

extern "C" JNIEXPORT jint JNICALL
    Java_java_io_FileInputStream_read__I_3BII(JNIEnv* e,
                                              jclass,
                                              jint fd,
                                              jbyteArray b,
                                              jint offset,
                                              jint length)
{
  jbyte* data = static_cast<jbyte*>(malloc(length));
  if (data == 0) {
    throwNew(e, "java/lang/OutOfMemoryError", 0);
    return 0;
  }

  int r = doRead(e, fd, data, length);

  e->SetByteArrayRegion(b, offset, length, data);

  free(data);

  return r;
}

extern "C" JNIEXPORT void JNICALL
    Java_java_io_FileInputStream_close(JNIEnv* e, jclass, jint fd)
{
  doClose(e, fd);
}

extern "C" JNIEXPORT jint JNICALL
    Java_java_io_FileOutputStream_open(JNIEnv* e,
                                       jclass,
                                       jstring path,
                                       jboolean append)
{
  string_t chars = getChars(e, path);
  if (chars) {
    int fd = doOpen(e,
                    chars,
                    append ? (O_WRONLY | O_CREAT | O_APPEND)
                           : (O_WRONLY | O_CREAT | O_TRUNC));
    releaseChars(e, path, chars);
    return fd;
  } else {
    return -1;
  }
}

#endif // !SGX

extern "C" JNIEXPORT void JNICALL
    Java_java_io_FileOutputStream_write__II(JNIEnv* e, jclass, jint fd, jint c)
{
  jbyte data = c;
  doWrite(e, fd, &data, 1);
}

extern "C" JNIEXPORT void JNICALL
    Java_java_io_FileOutputStream_write__I_3BII(JNIEnv* e,
                                                jclass,
                                                jint fd,
                                                jbyteArray b,
                                                jint offset,
                                                jint length)
{
  jbyte* data = static_cast<jbyte*>(malloc(length));

  if (data == 0) {
    throwNew(e, "java/lang/OutOfMemoryError", 0);
    return;
  }

  e->GetByteArrayRegion(b, offset, length, data);
  if (not e->ExceptionCheck()) {
    doWrite(e, fd, data, length);
  }

  free(data);
}

extern "C" JNIEXPORT void JNICALL
    Java_java_io_FileOutputStream_close(JNIEnv* e, jclass, jint fd)
{
  doClose(e, fd);
}

#ifndef SGX

extern "C" JNIEXPORT void JNICALL
    Java_java_io_RandomAccessFile_open(JNIEnv* e,
                                       jclass,
                                       jstring path,
                                       jboolean allowWrite,
                                       jlongArray result)
{
  string_t chars = getChars(e, path);
  if (chars) {
    jlong peer = 0;
    jlong length = 0;
    int flags = (allowWrite ? O_RDWR | O_CREAT : O_RDONLY) | OPEN_MASK;
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
#if defined(PLATFORM_WINDOWS)
    int fd = ::_wopen(chars, flags);
#else
    int fd = ::open((const char*)chars, flags, 0666);
#endif
    releaseChars(e, path, chars);
    if (fd == -1) {
      throwNewErrno(e, "java/io/IOException");
      return;
    }
    struct ::stat fileStats;
    if (::fstat(fd, &fileStats) == -1) {
      ::close(fd);
      throwNewErrno(e, "java/io/IOException");
      return;
    }
    peer = fd;
    length = fileStats.st_size;
#else
    HANDLE hFile = CreateFile2(
        chars, GENERIC_READ, FILE_SHARE_READ, OPEN_EXISTING, nullptr);
    if (hFile == INVALID_HANDLE_VALUE) {
      throwNewErrno(e, "java/io/IOException");
      return;
    }

    FILE_STANDARD_INFO info;
    if (!GetFileInformationByHandleEx(
            hFile, FileStandardInfo, &info, sizeof(info))) {
      CloseHandle(hFile);
      throwNewErrno(e, "java/io/IOException");
      return;
    }

    peer = (jlong)hFile;
    length = info.EndOfFile.QuadPart;
#endif

    e->SetLongArrayRegion(result, 0, 1, &peer);
    e->SetLongArrayRegion(result, 1, 1, &length);
  }
}

extern "C" JNIEXPORT jint JNICALL
    Java_java_io_RandomAccessFile_readBytes(JNIEnv* e,
                                            jclass,
                                            jlong peer,
                                            jlong position,
                                            jbyteArray buffer,
                                            int offset,
                                            int length)
{
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
  int fd = (int)peer;
  if (::lseek(fd, position, SEEK_SET) == -1) {
    throwNewErrno(e, "java/io/IOException");
    return -1;
  }

  uint8_t* dst
      = reinterpret_cast<uint8_t*>(e->GetPrimitiveArrayCritical(buffer, 0));

  int64_t bytesRead = ::read(fd, dst + offset, length);
  e->ReleasePrimitiveArrayCritical(buffer, dst, 0);

  if (bytesRead == -1) {
    throwNewErrno(e, "java/io/IOException");
    return -1;
  }
#else
  HANDLE hFile = (HANDLE)peer;
  LARGE_INTEGER lPos;
  lPos.QuadPart = position;
  if (!SetFilePointerEx(hFile, lPos, nullptr, FILE_BEGIN)) {
    throwNewErrno(e, "java/io/IOException");
    return -1;
  }

  uint8_t* dst
      = reinterpret_cast<uint8_t*>(e->GetPrimitiveArrayCritical(buffer, 0));

  DWORD bytesRead = 0;
  if (!ReadFile(hFile, dst + offset, length, &bytesRead, nullptr)) {
    e->ReleasePrimitiveArrayCritical(buffer, dst, 0);
    throwNewErrno(e, "java/io/IOException");
    return -1;
  }
  e->ReleasePrimitiveArrayCritical(buffer, dst, 0);
#endif

  return (jint)bytesRead;
}

extern "C" JNIEXPORT jint JNICALL
    Java_java_io_RandomAccessFile_writeBytes(JNIEnv* e,
                                             jclass,
                                             jlong peer,
                                             jlong position,
                                             jbyteArray buffer,
                                             int offset,
                                             int length)
{
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
  int fd = (int)peer;
  if (::lseek(fd, position, SEEK_SET) == -1) {
    throwNewErrno(e, "java/io/IOException");
    return -1;
  }

  uint8_t* dst
      = reinterpret_cast<uint8_t*>(e->GetPrimitiveArrayCritical(buffer, 0));

  int64_t bytesWritten = ::write(fd, dst + offset, length);
  e->ReleasePrimitiveArrayCritical(buffer, dst, 0);

  if (bytesWritten == -1) {
    throwNewErrno(e, "java/io/IOException");
    return -1;
  }
#else
  HANDLE hFile = (HANDLE)peer;
  LARGE_INTEGER lPos;
  lPos.QuadPart = position;
  if (!SetFilePointerEx(hFile, lPos, nullptr, FILE_BEGIN)) {
    throwNewErrno(e, "java/io/IOException");
    return -1;
  }

  uint8_t* dst
      = reinterpret_cast<uint8_t*>(e->GetPrimitiveArrayCritical(buffer, 0));

  DWORD bytesWritten = 0;
  if (!WriteFile(hFile, dst + offset, length, &bytesWritten, nullptr)) {
    e->ReleasePrimitiveArrayCritical(buffer, dst, 0);
    throwNewErrno(e, "java/io/IOException");
    return -1;
  }
  e->ReleasePrimitiveArrayCritical(buffer, dst, 0);
#endif

  return (jint)bytesWritten;
}

extern "C" JNIEXPORT void JNICALL
    Java_java_io_RandomAccessFile_close(JNIEnv* /* e*/, jclass, jlong peer)
{
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
  int fd = (int)peer;
  ::close(fd);
#else
  HANDLE hFile = (HANDLE)peer;
  CloseHandle(hFile);
#endif
}

#endif  // !SGX