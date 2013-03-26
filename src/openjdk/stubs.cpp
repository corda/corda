struct JavaVM;

extern "C" int net_JNI_OnLoad(JavaVM*, void*)
{
  return 0;
}

extern "C" int management_JNI_OnLoad(JavaVM*, void*)
{
  return 0;
}
