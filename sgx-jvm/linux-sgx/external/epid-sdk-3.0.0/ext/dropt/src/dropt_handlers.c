/** dropt_handlers.c
  *
  * Default type handlers for dropt.
  *
  * Copyright (c) 2006-2012 James D. Lin <jameslin@cal.berkeley.edu>
  *
  * The latest version of this file can be downloaded from:
  * <http://www.taenarum.com/software/dropt/>
  *
  * This software is provided 'as-is', without any express or implied
  * warranty.  In no event will the authors be held liable for any damages
  * arising from the use of this software.
  *
  * Permission is granted to anyone to use this software for any purpose,
  * including commercial applications, and to alter it and redistribute it
  * freely, subject to the following restrictions:
  *
  * 1. The origin of this software must not be misrepresented; you must not
  *    claim that you wrote the original software. If you use this software
  *    in a product, an acknowledgment in the product documentation would be
  *    appreciated but is not required.
  *
  * 2. Altered source versions must be plainly marked as such, and must not be
  *    misrepresented as being the original software.
  *
  * 3. This notice may not be removed or altered from any source distribution.
  */
// THIS FILE HAS BEEN ALTERED from original version to fix warnings
#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include <float.h>
#include <errno.h>
#include <assert.h>

#include "dropt.h"
#include "dropt_string.h"

#define ABS(x) (((x) < 0) ? -(x) : (x))

typedef enum { false, true } bool;

/** dropt_handle_bool
  *
  *     Parses a boolean value from the given string if possible.
  *
  * PARAMETERS:
  *     IN/OUT context    : The options context.
  *     IN optionArgument : A string representing a boolean value (0 or 1).
  *                         If NULL, the boolean value is assumed to be
  *                           true.
  *     OUT handlerData   : A dropt_bool*.
  *                         On success, set to the interpreted boolean
  *                           value.
  *                         On error, left untouched.
  *
  * RETURNS:
  *     dropt_error_none
  *     dropt_error_unknown
  *     dropt_error_bad_configuration
  *     dropt_error_mismatch
  */
dropt_error
dropt_handle_bool(dropt_context* context, const dropt_char* optionArgument,
                  void* handlerData)
{
    dropt_error err = dropt_error_none;
    bool val = false;
    dropt_bool* out = handlerData;

    if (out == NULL)
    {
        DROPT_MISUSE("No handler data specified.");
        err = dropt_error_bad_configuration;
    }
    else if (optionArgument == NULL)
    {
        /* No explicit argument implies that the option is being turned on. */
        val = true;
    }
    else if (optionArgument[0] == DROPT_TEXT_LITERAL('\0'))
    {
        err = dropt_error_mismatch;
    }
    else
    {
        unsigned int i = 0;
        err = dropt_handle_uint(context, optionArgument, &i);
        if (err == dropt_error_none)
        {
            switch (i)
            {
                case 0:
                    val = false;
                    break;
                case 1:
                    val = true;
                    break;
                default:
                    err = dropt_error_mismatch;
                    break;
            }
        }
        else if (err == dropt_error_overflow)
        {
            err = dropt_error_mismatch;
        }
    }

    if (err == dropt_error_none) { *out = val; }
    return err;
}


/** dropt_handle_verbose_bool
  *
  *     Like dropt_handle_bool but accepts "true" and "false" string
  *     values.
  *
  * PARAMETERS:
  *     IN/OUT context    : The options context.
  *     IN optionArgument : A string representing a boolean value.
  *                         If NULL, the boolean value is assumed to be
  *                           true.
  *     OUT handlerData   : A dropt_bool*.
  *                         On success, set to the interpreted boolean
  *                           value.
  *                         On error, left untouched.
  *
  * RETURNS:
  *     See dropt_handle_bool.
  */
dropt_error
dropt_handle_verbose_bool(dropt_context* context, const dropt_char* optionArgument,
                          void* handlerData)
{
    dropt_error err = dropt_handle_bool(context, optionArgument, handlerData);
    if (err == dropt_error_mismatch)
    {
        bool val = false;
        dropt_bool* out = handlerData;

        /* dropt_handle_bool already checks for this. */
        assert(out != NULL);

        if (dropt_stricmp(optionArgument, DROPT_TEXT_LITERAL("false")) == 0)
        {
            val = false;
            err = dropt_error_none;
        }
        else if (dropt_stricmp(optionArgument, DROPT_TEXT_LITERAL("true")) == 0)
        {
            val = true;
            err = dropt_error_none;
        }

        if (err == dropt_error_none) { *out = val; }
    }
    return err;
}


/** dropt_handle_int
  *
  *     Parses an integer from the given string.
  *
  * PARAMETERS:
  *     IN/OUT context    : The options context.
  *     IN optionArgument : A string representing a base-10 integer.
  *                         If NULL, returns dropt_error_insufficient_arguments.
  *     OUT handlerData   : An int*.
  *                         On success, set to the interpreted integer.
  *                         On error, left untouched.
  *
  * RETURNS:
  *     dropt_error_none
  *     dropt_error_unknown
  *     dropt_error_bad_configuration
  *     dropt_error_insufficient_arguments
  *     dropt_error_mismatch
  *     dropt_error_overflow
  */
dropt_error
dropt_handle_int(dropt_context* context, const dropt_char* optionArgument,
                 void* handlerData)
{
    dropt_error err = dropt_error_none;
    int val = 0;
    int* out = handlerData;
    (void)context;

    if (out == NULL)
    {
        DROPT_MISUSE("No handler data specified.");
        err = dropt_error_bad_configuration;
    }
    else if (optionArgument == NULL || optionArgument[0] == DROPT_TEXT_LITERAL('\0'))
    {
        err = dropt_error_insufficient_arguments;
    }
    else
    {
        dropt_char* end;
        long n;
        errno = 0;
        n = dropt_strtol(optionArgument, &end, 10);

        /* Check that we matched at least one digit.
         * (strtol/strtoul will return 0 if fed a string with no digits.)
         */
        if (*end == DROPT_TEXT_LITERAL('\0') && end > optionArgument)
        {
            if (errno == ERANGE || n < INT_MIN || n > INT_MAX)
            {
                err = dropt_error_overflow;
                val = (n < 0) ? INT_MIN : INT_MAX;
            }
            else if (errno == 0)
            {
                val = (int) n;
            }
            else
            {
                err = dropt_error_unknown;
            }
        }
        else
        {
            err = dropt_error_mismatch;
        }
    }

    if (err == dropt_error_none) { *out = val; }
    return err;
}


/** dropt_handle_uint
  *
  *     Parses an unsigned integer from the given string.
  *
  * PARAMETERS:
  *     IN/OUT context    : The options context.
  *     IN optionArgument : A string representing an unsigned base-10
  *                           integer.
  *                         If NULL, returns dropt_error_insufficient_arguments.
  *     OUT handlerData   : An unsigned int*.
  *                         On success, set to the interpreted integer.
  *                         On error, left untouched.
  *
  * RETURNS:
  *     dropt_error_none
  *     dropt_error_unknown
  *     dropt_error_bad_configuration
  *     dropt_error_insufficient_arguments
  *     dropt_error_mismatch
  *     dropt_error_overflow
  */
dropt_error
dropt_handle_uint(dropt_context* context, const dropt_char* optionArgument,
                  void* handlerData)
{
    dropt_error err = dropt_error_none;
    int val = 0;
    unsigned int* out = handlerData;
    (void)context;

    if (out == NULL)
    {
        DROPT_MISUSE("No handler data specified.");
        err = dropt_error_bad_configuration;
    }
    else if (   optionArgument == NULL
             || optionArgument[0] == DROPT_TEXT_LITERAL('\0'))
    {
        err = dropt_error_insufficient_arguments;
    }
    else if (optionArgument[0] == DROPT_TEXT_LITERAL('-'))
    {
        err = dropt_error_mismatch;
    }
    else
    {
        dropt_char* end;
        unsigned long n;
        errno = 0;
        n = dropt_strtoul(optionArgument, &end, 10);

        /* Check that we matched at least one digit.
         * (strtol/strtoul will return 0 if fed a string with no digits.)
         */
        if (*end == DROPT_TEXT_LITERAL('\0') && end > optionArgument)
        {
            if (errno == ERANGE || n > UINT_MAX)
            {
                err = dropt_error_overflow;
                val = UINT_MAX;
            }
            else if (errno == 0)
            {
                val = (unsigned int) n;
            }
            else
            {
                err = dropt_error_unknown;
            }
        }
        else
        {
            err = dropt_error_mismatch;
        }
    }

    if (err == dropt_error_none) { *out = val; }
    return err;
}


/** dropt_handle_double
  *
  *     Parses a double from the given string.
  *
  * PARAMETERS:
  *     IN/OUT context    : The options context.
  *     IN optionArgument : A string representing a base-10 floating-point
  *                           number.
  *                         If NULL, returns dropt_error_insufficient_arguments.
  *     OUT handlerData   : A double*.
  *                         On success, set to the interpreted double.
  *                         On error, left untouched.
  *
  * RETURNS:
  *     dropt_error_none
  *     dropt_error_unknown
  *     dropt_error_bad_configuration
  *     dropt_error_insufficient_arguments
  *     dropt_error_mismatch
  *     dropt_error_overflow
  *     dropt_error_underflow
  */
dropt_error
dropt_handle_double(dropt_context* context, const dropt_char* optionArgument,
                    void* handlerData)
{
    dropt_error err = dropt_error_none;
    double val = 0.0;
    double* out = handlerData;
    (void)context;

    if (out == NULL)
    {
        DROPT_MISUSE("No handler data specified.");
        err = dropt_error_bad_configuration;
    }
    else if (   optionArgument == NULL
             || optionArgument[0] == DROPT_TEXT_LITERAL('\0'))
    {
        err = dropt_error_insufficient_arguments;
    }
    else
    {
        dropt_char* end;
        errno = 0;
        val = dropt_strtod(optionArgument, &end);

        /* Check that we matched at least one digit.
         * (strtod will return 0 if fed a string with no digits.)
         */
        if (*end == DROPT_TEXT_LITERAL('\0') && end > optionArgument)
        {
            if (errno == ERANGE)
            {
                /* Note that setting errno to ERANGE for underflow errors
                 * is implementation-defined behavior, but glibc, BSD's
                 * libc, and Microsoft's CRT all have implementations of
                 * strtod documented to return 0 and to set errno to ERANGE
                 * for such cases.
                 */
                err = (ABS(val) <= DBL_MIN)
                      ? dropt_error_underflow
                      : dropt_error_overflow;
            }
            else if (errno != 0)
            {
                err = dropt_error_unknown;
            }
        }
        else
        {
            err = dropt_error_mismatch;
        }
    }

    if (err == dropt_error_none) { *out = val; }
    return err;
}


/** dropt_handle_string
  *
  *     Obtains a string.
  *
  * PARAMETERS:
  *     IN/OUT context    : The options context.
  *     IN optionArgument : A string.
  *                         If NULL, returns dropt_error_insufficient_arguments.
  *     OUT handlerData   : A dropt_char**.
  *                         On success, set to the input string.  The
  *                           string is NOT copied from the original argv
  *                           array, so do not free it.
  *                         On error, left untouched.
  *
  * RETURNS:
  *     dropt_error_none
  *     dropt_error_bad_configuration
  *     dropt_error_insufficient_arguments
  */
dropt_error
dropt_handle_string(dropt_context* context, const dropt_char* optionArgument,
                    void* handlerData)
{
    dropt_error err = dropt_error_none;
    const dropt_char** out = handlerData;
    (void)context;

    if (out == NULL)
    {
        DROPT_MISUSE("No handler data specified.");
        err = dropt_error_bad_configuration;
    }
    else if (optionArgument == NULL)
    {
        err = dropt_error_insufficient_arguments;
    }

    if (err == dropt_error_none) { *out = optionArgument; }
    return err;
}
