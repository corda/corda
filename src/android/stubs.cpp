struct JavaVM;

extern "C" int JNI_OnLoad(JavaVM*, void*)
{
  return 0;
}

struct _JNIEnv;

struct JniConstants {
  static void init(_JNIEnv* env);
};

void
JniConstants::init(_JNIEnv*)
{
  // ignore
}
