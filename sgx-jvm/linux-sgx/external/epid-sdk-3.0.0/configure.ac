AC_INIT([Intel(R) EPID SDK],[ ])

# save flags
CFLAGS_OLD="$CFLAGS"
CPPFLAGS_OLD="$CPPFLAGS"

#c compiler priority
AC_PROG_CC([gcc icc cc])
#c++ compiler priority
AC_PROG_CXX([g++ icpc c++])

# restore flags
CFLAGS="$CFLAGS_OLD"
CPPFLAGS="$CPPFLAGS_OLD"

# default install path is current directory
AC_PREFIX_DEFAULT([`pwd`/_install])
AC_CONFIG_FILES([Makefile])
AC_OUTPUT

