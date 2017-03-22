#include "java_props_macosx.h"

PreferredToolkit
getPreferredToolkit()
{
  return XToolkit;
}

void
setOSNameAndVersion(java_props_t* props)
{
  props->os_name = strdup("iOS");
  props->os_version = strdup("Unknown");
}

void
setProxyProperties(java_props_t* props)
{
  // ignore
}

void
setUserHome(java_props_t* props)
{
  // ignore
}

char*
setupMacOSXLocale(int cat)
{
  return 0;
}

char* environ[0];
