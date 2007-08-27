#ifndef JNI_UTIL
#define JNI_UTIL

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
