/*	$OpenBSD: locks.c,v 1.1 2008/09/07 20:36:08 martynas Exp $	*/

/* Written by Martynas Venckus.  Public Domain. */

#include <sgx_spinlock.h>

sgx_spinlock_t __dtoa_locks[] = { SGX_SPINLOCK_INITIALIZER, SGX_SPINLOCK_INITIALIZER };
