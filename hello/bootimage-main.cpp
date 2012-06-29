#include "stdint.h"
#include "jni.h"

#if (defined __MINGW32__) || (defined _MSC_VER)
#  define EXPORT __declspec(dllexport)
#else
#  define EXPORT __attribute__ ((visibility("default")))
#endif

#if (! defined __x86_64__) && ((defined __MINGW32__) || (defined _MSC_VER))
#  define BOOTIMAGE_BIN(x) binary_bootimage_bin_##x
#  define CODEIMAGE_BIN(x) binary_codeimage_bin_##x
#else
#  define BOOTIMAGE_BIN(x) _binary_bootimage_bin_##x
#  define CODEIMAGE_BIN(x) _binary_codeimage_bin_##x
#endif

extern "C" {

  extern const uint8_t BOOTIMAGE_BIN(start)[];
  extern const uint8_t BOOTIMAGE_BIN(end)[];

  EXPORT const uint8_t*
  bootimageBin(unsigned* size)
  {
    *size = BOOTIMAGE_BIN(end) - BOOTIMAGE_BIN(start);
    return BOOTIMAGE_BIN(start);
  }

  extern const uint8_t CODEIMAGE_BIN(start)[];
  extern const uint8_t CODEIMAGE_BIN(end)[];

  EXPORT const uint8_t*
  codeimageBin(unsigned* size)
  {
    *size = CODEIMAGE_BIN(end) - CODEIMAGE_BIN(start);
    return CODEIMAGE_BIN(start);
  }

} // extern "C"

int
main(int ac, const char** av)
{
  JavaVMInitArgs vmArgs;
  vmArgs.version = JNI_VERSION_1_2;
  vmArgs.nOptions = 2;
  vmArgs.ignoreUnrecognized = JNI_TRUE;

  JavaVMOption options[vmArgs.nOptions];
  vmArgs.options = options;

  options[0].optionString
    = const_cast<char*>("-Davian.bootimage=lzma:bootimageBin");

  options[1].optionString
    = const_cast<char*>("-Davian.codeimage=codeimageBin");

  JavaVM* vm;
  void* env;
  JNI_CreateJavaVM(&vm, &env, &vmArgs);
  JNIEnv* e = static_cast<JNIEnv*>(env);

  jclass c = e->FindClass("Hello");
  if (not e->ExceptionCheck()) {
    jmethodID m = e->GetStaticMethodID(c, "main", "([Ljava/lang/String;)V");
    if (not e->ExceptionCheck()) {
      jclass stringClass = e->FindClass("java/lang/String");
      if (not e->ExceptionCheck()) {
        jobjectArray a = e->NewObjectArray(ac-1, stringClass, 0);
        if (not e->ExceptionCheck()) {
          for (int i = 1; i < ac; ++i) {
            e->SetObjectArrayElement(a, i-1, e->NewStringUTF(av[i]));
          }
          
          e->CallStaticVoidMethod(c, m, a);
        }
      }
    }
  }

  int exitCode = 0;
  if (e->ExceptionCheck()) {
    exitCode = -1;
    e->ExceptionDescribe();
  }

  vm->DestroyJavaVM();

  return exitCode;
}
