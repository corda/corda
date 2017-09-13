//===------------------------- typeinfo.cpp -------------------------------===//
//
//                     The LLVM Compiler Infrastructure
//
// This file is dual licensed under the MIT and the University of Illinois Open
// Source Licenses. See LICENSE.TXT for details.
//
//===----------------------------------------------------------------------===//
#include <stdlib.h>

#if defined(__APPLE__) || defined(LIBCXXRT) ||                                 \
    defined(LIBCXX_BUILDING_LIBCXXABI)
#include <cxxabi.h>
#endif

#include "typeinfo"

#if !defined(LIBCXXRT) && !defined(_LIBCPPABI_VERSION)
#ifndef __GLIBCXX__

#ifdef __APPLE__
  // On Darwin, the cxa_bad_* functions cannot be in the lower level library
  // because bad_cast and bad_typeid are defined in his higher level library
  void __cxxabiv1::__cxa_bad_typeid()
  {
#ifndef _LIBCPP_NO_EXCEPTIONS
     throw std::bad_typeid();
#endif
  }
  void __cxxabiv1::__cxa_bad_cast()
  {
#ifndef _LIBCPP_NO_EXCEPTIONS
      throw std::bad_cast();
#endif
  }
#endif

#endif  // !__GLIBCXX__
#endif  // !LIBCXXRT && !_LIBCPPABI_VERSION
