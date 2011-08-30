/* Copyright (c) 2011, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef TARGET_H
#define TARGET_H

#define TARGET_V1(v) (v)

#ifdef TARGET_OPPOSITE_ENDIAN
#  define TARGET_V2(v) \
  ((((v) >> 8) & 0xFF) | \
   (((v) << 8)))
#  define TARGET_V4(v) \
  ((((v) >> 24) & 0x000000FF) | \
   (((v) >>  8) & 0x0000FF00) | \
   (((v) <<  8) & 0x00FF0000) | \
   (((v) << 24)))
#  define TARGET_V8(v) \
  (((static_cast<uint64_t>(v) >> 56) & UINT64_C(0x00000000000000FF)) | \
   ((static_cast<uint64_t>(v) >> 40) & UINT64_C(0x000000000000FF00)) | \
   ((static_cast<uint64_t>(v) >> 24) & UINT64_C(0x0000000000FF0000)) | \
   ((static_cast<uint64_t>(v) >>  8) & UINT64_C(0x00000000FF000000)) | \
   ((static_cast<uint64_t>(v) <<  8) & UINT64_C(0x000000FF00000000)) | \
   ((static_cast<uint64_t>(v) << 24) & UINT64_C(0x0000FF0000000000)) | \
   ((static_cast<uint64_t>(v) << 40) & UINT64_C(0x00FF000000000000)) | \
   ((static_cast<uint64_t>(v) << 56)))
#else
#  define TARGET_V2(v) (v)
#  define TARGET_V4(v) (v)
#  define TARGET_V8(v) (v)
#endif

namespace vm {

#ifdef TARGET_BYTES_PER_WORD
#  if (TARGET_BYTES_PER_WORD == 8)
#    define TARGET_VW(v) TARGET_V8(v)
typedef uint64_t target_uintptr_t;
typedef int64_t target_intptr_t;
const unsigned TargetBytesPerWord = 8;
#  elif (TARGET_BYTES_PER_WORD == 4)
#    define TARGET_VW(v) TARGET_V4(v)
typedef uint32_t target_uintptr_t;
typedef int32_t target_intptr_t;
const unsigned TargetBytesPerWord = 4;
#  else
#    error
#  endif
#else
typedef uintptr_t target_uintptr_t;
typedef intptr_t target_intptr_t;
const unsigned TargetBytesPerWord = BytesPerWord;
#endif

const unsigned TargetBitsPerWord = TargetBytesPerWord * 8;

} // namespace vm

#endif//TARGET_H
