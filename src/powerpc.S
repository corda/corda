/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "types.h"

.text

#ifdef __APPLE__
.globl _vmNativeCall
_vmNativeCall: 
#else
.globl vmNativeCall
vmNativeCall:
#endif
;;    mflr r0                                         ; extract return address
;;    stw  r0,8(r1)                                   ; save the return address
;;    stwu r1,-spaceToSaveAligned(r1)                 ; skip over caller save area

;;    ; todo

;;    lwz  r0,spaceToSaveAligned + 8(r1)       ; get the return address
;;    mtlr r0                                  ;    into the link register
;;    addi r1,r1,spaceToSaveAligned            ; restore stack pointer
   blr                                      ;    and branch to the return address
