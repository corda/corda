Quick Start
-----------

 $ make
 $ build/linux-i386-compile-fast/avian -cp build/test Hello


Supported Platforms
-------------------

Avian can currently target the following platforms:

  Linux (i386 and x86_64)
  Win32 (i386)
  Mac OS X (i386)

The Win32 port may be built on Linux using a MinGW cross compiler and
build environment.  Builds on MSYS or Cygwin are not yet supported,
but patches to enable them are welcome.


Building
--------

 $ make platform={linux,windows,darwin} arch={i386,x86_64} \
     process={compile,interpret} mode={debug,fast}

The default values of the build flags are as follows:

  platform=$(uname -s | tr [:upper:] [:lower:])
  arch=$(uname -m)
  mode=fast
  process=compile


Installing
----------

 $ cp build/${platform}-${arch}-${process}-${mode}/avian ~/bin/
 $ cp build/${platform}-${arch}-${process}-${mode}/libavian.a ~/lib/
