/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <avian/common.h>

namespace avian {
namespace system {

NO_RETURN void crash()
{
  // trigger an EXCEPTION_ACCESS_VIOLATION, which we will catch and
  // generate a debug dump for
  *static_cast<volatile int*>(0) = 0;

  // Some (all?) compilers don't realize that we can't possibly continue past
  // the above statement.
  abort();
}

}  // namespace system
}  // namespace avian
