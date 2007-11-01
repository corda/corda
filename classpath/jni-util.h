#ifndef JNI_UTIL
#define JNI_UTIL

#undef JNIEXPORT
#ifdef __MINGW32__
#  define JNIEXPORT __declspec(dllexport)
#else
#ifdef __APPLE__
#  define JNIEXPORT __attribute__ ((used))
#else
#  define JNIEXPORT __attribute__ ((visibility("default")))
#endif
#endif

namespace {

inline void
throwNew(JNIEnv* e, const char* class_, const char* message)
{
  jclass c = e->FindClass(class_);
  if (c) {
    e->ThrowNew(c, message);
    e->DeleteLocalRef(c);
  }
}

} // namespace

#endif//JNI_UTIL
