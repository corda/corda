void
register_libcore_icu_ICU(JNIEnv* e)
{
  UErrorCode status = U_ZERO_ERROR;
  udata_setFileAccess(UDATA_NO_FILES, &status);
  if (status != U_ZERO_ERROR) abort();

  u_init(&status);
  if (status != U_ZERO_ERROR) abort();

  jniRegisterNativeMethods(e, "libcore/icu/ICU", gMethods, NELEM(gMethods));
}
