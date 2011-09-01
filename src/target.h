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

const unsigned TargetThreadTailAddress = 2272;
const unsigned TargetThreadStackLimit = 2336;
const unsigned TargetThreadStack = 2224;
const unsigned TargetThreadIp = 2216;
const unsigned TargetThreadVirtualCallTarget = 2280;
const unsigned TargetThreadVirtualCallIndex = 2288;

const unsigned TargetClassFixedSize = 12;
const unsigned TargetClassArrayElementSize = 14;
const unsigned TargetClassVtable = 128;

const unsigned TargetFieldOffset = 12;

#  elif (TARGET_BYTES_PER_WORD == 4)
#    define TARGET_VW(v) TARGET_V4(v)

typedef uint32_t target_uintptr_t;
typedef int32_t target_intptr_t;

const unsigned TargetBytesPerWord = 4;

const unsigned TargetThreadTailAddress = 2172;
const unsigned TargetThreadStackLimit = 2204;
const unsigned TargetThreadStack = 2148;
const unsigned TargetThreadIp = 2144;
const unsigned TargetThreadVirtualCallTarget = 2176;
const unsigned TargetThreadVirtualCallIndex = 2180;

const unsigned TargetClassFixedSize = 8;
const unsigned TargetClassArrayElementSize = 10;
const unsigned TargetClassVtable = 68;

const unsigned TargetFieldOffset = 8;

#  else
#    error
#  endif
#else
#  error
#endif

const unsigned TargetBitsPerWord = TargetBytesPerWord * 8;

const uintptr_t TargetPointerMask
= ((~static_cast<target_uintptr_t>(0)) / TargetBytesPerWord)
  * TargetBytesPerWord;

const unsigned TargetArrayLength = TargetBytesPerWord;
const unsigned TargetArrayBody = TargetBytesPerWord * 2;

} // namespace vm

#endif//TARGET_H
