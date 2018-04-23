/** dropt.c
  *
  * A deliberately rudimentary command-line option parser.
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
// THIS FILE HAS BEEN ALTERED from original version to:
// * fix warnings
// * modify error messages
// * resolve bool type conflict
// * fix issues found by static code analisys:
//   * possible leak in dropt_get_help
//   * null pointer dereference in dropt_parse
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <wctype.h>
#include <assert.h>

#include "dropt.h"
#include "dropt_string.h"

#if __STDC_VERSION__ >= 199901L
    #include <stdint.h>
    #include <stdbool.h>
#else
    /* Compatibility junk for things that don't yet support ISO C99. */
    #ifndef SIZE_MAX
        #define SIZE_MAX ((size_t) -1)
    #endif

    #ifndef __cplusplus
    #ifndef _Bool
    /// C99 standard name for bool
    #define _Bool char
    /// Boolean type
    typedef char bool;
    /// integer constant 1
    #define true 1
    /// integer constant 0
    #define false 0
    #endif
    #endif  // ifndef __cplusplus
#endif
#ifndef MIN
#define MIN(x, y) (((x) < (y)) ? (x) : (y))
#endif

#ifndef ARRAY_LENGTH
#define ARRAY_LENGTH(array) (sizeof (array) / sizeof (array)[0])
#endif

#define IMPLIES(p, q) (!(p) || q)

#define OPTION_TAKES_ARG(option) ((option)->arg_description != NULL)

enum
{
    default_help_indent = 2,
    default_description_start_column = 6,
};


/** A string that might not be NUL-terminated. */
typedef struct
{
    const dropt_char* s;

    /* The length of s, excluding any NUL terminator. */
    size_t len;
} char_array;


/** A proxy for a dropt_option used for qsort and bsearch.  Instead of
  * sorting the dropt_option table directly, we sort arrays of option_proxy
  * structures.  This allows us to have separate arrays sorted by different
  * keys and allows passing along additional data.
  */
typedef struct
{
    const dropt_option* option;

    /* The qsort and bsearch comparison callbacks don't pass along any
     * client-supplied contextual data, so we have to embed it alongside
     * the regular data.
     */
    const dropt_context* context;
} option_proxy;


struct dropt_context
{
    const dropt_option* options;
    size_t numOptions;

    /* These may be NULL. */
    option_proxy* sortedByLong;
    option_proxy* sortedByShort;

    bool allowConcatenatedArgs;

    dropt_error_handler_func errorHandler;
    void* errorHandlerData;

    struct
    {
        dropt_error err;
        dropt_char* optionName;
        dropt_char* optionArgument;
        dropt_char* message;
    } errorDetails;

    /* This isn't named strncmp because platforms might provide a macro
     * version of strncmp, and we want to avoid a potential naming
     * conflict.
     */
    dropt_strncmp_func ncmpstr;
};


typedef struct
{
    const dropt_option* option;
    const dropt_char* optionArgument;
    dropt_char** argNext;
    int argsLeft;
} parse_state;


/** make_char_array
  *
  * PARAMETERS:
  *     IN s : A string.  Might not be NUL-terminated.
  *            May be NULL.
  *     len  : The length of s, excluding any NUL terminator.
  *
  * RETURNS:
  *     The constructed char_array structure.
  */
static char_array
make_char_array(const dropt_char* s, size_t len)
{
   char_array a;

   assert(IMPLIES(s == NULL, len == 0));

   a.s = s;
   a.len = len;
   return a;
}


/** cmp_key_option_proxy_long
  *
  *     Comparison callback for bsearch.  Compares a char_array structure
  *     against an option_proxy structure based on long option names.
  *
  * PARAMETERS:
  *     IN key  : A pointer to the char_array structure to search for.
  *     IN item : A pointer to the option_proxy structure being searched
  *                 against.
  *
  * RETURNS:
  *     0 if key and item are equivalent,
  *     < 0 if key should precede item,
  *     > 0 if key should follow item.
  */
static int
cmp_key_option_proxy_long(const void* key, const void* item)
{
    const char_array* longName = key;
    const option_proxy* op = item;

    size_t optionLen;
    int ret;

    assert(longName != NULL);
    assert(op != NULL);
    assert(op->option != NULL);
    assert(op->context != NULL);
    assert(op->context->ncmpstr != NULL);

    if (longName->s == op->option->long_name)
    {
        return 0;
    }
    else if (longName->s == NULL)
    {
        return -1;
    }
    else if (op->option->long_name == NULL)
    {
        return +1;
    }

    /* Although the longName key might not be NUL-terminated, the
     * option_proxy item we're searching against must be.
     */
    optionLen = dropt_strlen(op->option->long_name);
    ret = op->context->ncmpstr(longName->s,
                               op->option->long_name,
                               MIN(longName->len, optionLen));
    if (ret != 0)
    {
        return ret;
    }

    if (longName->len < optionLen)
    {
        return -1;
    }
    else if (longName->len > optionLen)
    {
        return +1;
    }

    return 0;
}


/** cmp_option_proxies_long
  *
  *     Comparison callback for qsort.  Compares two option_proxy
  *     structures based on long option names.
  *
  * PARAMETERS:
  *     IN p1, p2 : Pointers to the option_proxy structures to compare.
  *
  * RETURNS:
  *     0 if p1 and p2 are equivalent,
  *     < 0 if p1 should precede p2,
  *     > 0 if p1 should follow p2.
  */
static int
cmp_option_proxies_long(const void* p1, const void* p2)
{
    const option_proxy* o1 = p1;
    const option_proxy* o2 = p2;

    char_array ca1;

    assert(o1 != NULL);
    assert(o2 != NULL);
    assert(o1->option != NULL);
    assert(o1->context == o2->context);

    ca1 = make_char_array(o1->option->long_name,
                          (o1->option->long_name == NULL)
                          ? 0
                          : dropt_strlen(o1->option->long_name));
    return cmp_key_option_proxy_long(&ca1, o2);
}


/** cmp_key_option_proxy_short
  *
  *     Comparison callback for bsearch.  Compares a dropt_char against an
  *     option_proxy structure based on short option names.
  *
  * PARAMETERS:
  *     IN key  : A pointer to the dropt_char to search for.
  *     IN item : A pointer to the option_proxy structure being searched
  *                 against.
  *
  * RETURNS:
  *     0 if key and item are equivalent,
  *     < 0 if key should precede item,
  *     > 0 if key should follow item.
  */
static int
cmp_key_option_proxy_short(const void* key, const void* item)
{
    const dropt_char* shortName = key;
    const option_proxy* op = item;

    assert(shortName != NULL);
    assert(op != NULL);
    assert(op->option != NULL);
    assert(op->context != NULL);
    assert(op->context->ncmpstr != NULL);

    return op->context->ncmpstr(shortName,
                                &op->option->short_name,
                                1);
}


/** cmp_option_proxies_short
  *
  *     Comparison callback for qsort.  Compares two option_proxy
  *     structures based on short option names.
  *
  * PARAMETERS:
  *     IN p1, p2 : Pointers to the option_proxy structures to compare.
  *
  * RETURNS:
  *     0 if p1 and p2 are equivalent,
  *     < 0 if p1 should precede p2,
  *     > 0 if p1 should follow p2.
  */
static int
cmp_option_proxies_short(const void* p1, const void* p2)
{
    const option_proxy* o1 = p1;
    const option_proxy* o2 = p2;

    assert(o1 != NULL);
    assert(o2 != NULL);
    assert(o1->option != NULL);
    assert(o1->context == o2->context);

    return cmp_key_option_proxy_short(&o1->option->short_name, o2);
}


/** init_lookup_tables
  *
  *     Initializes the sorted lookup tables in a dropt context if not
  *     already initialized.
  *
  * PARAMETERS:
  *     IN/OUT context : The dropt context.
  *                      Must not be NULL.
  */
static void
init_lookup_tables(dropt_context* context)
{
    const dropt_option* options;
    size_t n;

    assert(context != NULL);

    options = context->options;
    n = context->numOptions;

    if (context->sortedByLong == NULL)
    {
        context->sortedByLong = dropt_safe_malloc(n, sizeof *(context->sortedByLong));
        if (context->sortedByLong != NULL)
        {
            size_t i;
            for (i = 0; i < n; i++)
            {
                context->sortedByLong[i].option = &options[i];
                context->sortedByLong[i].context = context;
            }

            qsort(context->sortedByLong,
                  n, sizeof *(context->sortedByLong),
                  cmp_option_proxies_long);
        }
    }

    if (context->sortedByShort == NULL)
    {
        context->sortedByShort = dropt_safe_malloc(n, sizeof *(context->sortedByShort));
        if (context->sortedByShort != NULL)
        {
            size_t i;
            for (i = 0; i < n; i++)
            {
                context->sortedByShort[i].option = &options[i];
                context->sortedByShort[i].context = context;
            }

            qsort(context->sortedByShort,
                  n, sizeof *(context->sortedByShort),
                  cmp_option_proxies_short);
        }
    }
}


/** free_lookup_tables
  *
  *     Frees the sorted lookup tables in a dropt context.
  *
  * PARAMETERS:
  *     IN/OUT context : The dropt context.
  *                      May be NULL.
  */
static void
free_lookup_tables(dropt_context* context)
{
    if (context != NULL)
    {
        free(context->sortedByLong);
        context->sortedByLong = NULL;

        free(context->sortedByShort);
        context->sortedByShort = NULL;
    }
}


/** is_valid_option
  *
  * PARAMETERS:
  *     IN option : Specification for an individual option.
  *
  * RETURNS:
  *     true if the specified option is valid, false if it's a sentinel
  *       value.
  */
static bool
is_valid_option(const dropt_option* option)
{
    return    option != NULL
           && !(   option->long_name == NULL
                && option->short_name == DROPT_TEXT_LITERAL('\0')
                && option->description == NULL
                && option->arg_description == NULL
                && option->handler == NULL
                && option->handler_data == NULL
                && option->attr == 0);
}


/** find_option_long
  *
  *     Finds the option specification for a long option name (i.e., an
  *     option of the form "--option").
  *
  * PARAMETERS:
  *     IN context     : The dropt context.
  *     IN longName    : The long option name to search for (excluding
  *                        leading dashes).
  *                      longName.s must not be NULL.
  *
  * RETURNS:
  *     A pointer to the corresponding option specification or NULL if not
  *       found.
  */
static const dropt_option*
find_option_long(const dropt_context* context,
                 char_array longName)
{
    assert(context != NULL);
    assert(longName.s != NULL);

    if (context->sortedByLong != NULL)
    {
        option_proxy* found = bsearch(&longName, context->sortedByLong,
                                      context->numOptions, sizeof *(context->sortedByLong),
                                      cmp_key_option_proxy_long);
        return (found == NULL) ? NULL : found->option;
    }

    /* Fall back to a linear search. */
    {
        option_proxy item = { 0 };
        item.context = context;
        for (item.option = context->options; is_valid_option(item.option); item.option++)
        {
            if (cmp_key_option_proxy_long(&longName, &item) == 0)
            {
                return item.option;
            }
        }
    }
    return NULL;
}


/** find_option_short
  *
  *     Finds the option specification for a short option name (i.e., an
  *     option of the form "-o").
  *
  * PARAMETERS:
  *     IN context   : The dropt context.
  *     IN shortName : The short option name to search for.
  *
  * RETURNS:
  *     A pointer to the corresponding option specification or NULL if not
  *       found.
  */
static const dropt_option*
find_option_short(const dropt_context* context, dropt_char shortName)
{
    assert(context != NULL);
    assert(shortName != DROPT_TEXT_LITERAL('\0'));
    assert(context->ncmpstr != NULL);

    if (context->sortedByShort != NULL)
    {
        option_proxy* found = bsearch(&shortName, context->sortedByShort,
                                      context->numOptions, sizeof *(context->sortedByShort),
                                      cmp_key_option_proxy_short);
        return (found == NULL) ? NULL : found->option;
    }

    /* Fall back to a linear search. */
    {
        const dropt_option* option;
        for (option = context->options; is_valid_option(option); option++)
        {
            if (context->ncmpstr(&shortName, &option->short_name, 1) == 0)
            {
                return option;
            }
        }
    }
    return NULL;
}


/** set_error_details
  *
  *     Generates error details in the dropt context.
  *
  * PARAMETERS:
  *     IN/OUT context    : The dropt context.
  *                         Must not be NULL.
  *     IN err            : The error code.
  *     IN optionName     : The name of the option we failed on.
  *                         optionName.s must not be NULL.
  *     IN optionArgument : The value of the option we failed on.
  *                         Pass NULL if unwanted.
  */
static void
set_error_details(dropt_context* context, dropt_error err,
                  char_array optionName,
                  const dropt_char* optionArgument)
{
    assert(context != NULL);
    assert(optionName.s != NULL);

    context->errorDetails.err = err;

    free(context->errorDetails.optionName);
    free(context->errorDetails.optionArgument);

    context->errorDetails.optionName = dropt_strndup(optionName.s, optionName.len);
    context->errorDetails.optionArgument = (optionArgument == NULL)
                                           ? NULL
                                           : dropt_strdup(optionArgument);

    /* The message will be generated lazily on retrieval. */
    free(context->errorDetails.message);
    context->errorDetails.message = NULL;
}


/** set_short_option_error_details
  *
  *     Generates error details in the dropt context.
  *
  * PARAMETERS:
  *     IN/OUT context    : The dropt context.
  *     IN err            : The error code.
  *     IN shortName      : the "short" name of the option we failed on.
  *     IN optionArgument : The value of the option we failed on.
  *                         Pass NULL if unwanted.
  */
static void
set_short_option_error_details(dropt_context* context, dropt_error err,
                               dropt_char shortName, const dropt_char* optionArgument)
{
    /* "-?" is just a placeholder. */
    dropt_char shortNameBuf[] = DROPT_TEXT_LITERAL("-?");

    assert(context != NULL);
    assert(shortName != DROPT_TEXT_LITERAL('\0'));

    shortNameBuf[1] = shortName;

    set_error_details(context, err,
                      make_char_array(shortNameBuf, ARRAY_LENGTH(shortNameBuf) - 1),
                      optionArgument);
}


/** dropt_get_error
  *
  * PARAMETERS:
  *     IN context : The dropt context.
  *                  Must not be NULL.
  *
  * RETURNS:
  *     The current error code waiting in the dropt context.
  */
dropt_error
dropt_get_error(const dropt_context* context)
{
    if (context == NULL)
    {
        DROPT_MISUSE("No dropt context specified.");
        return dropt_error_bad_configuration;
    }
    return context->errorDetails.err;
}


/** dropt_get_error_details
  *
  *     Retrieves details about the current error.
  *
  * PARAMETERS:
  *     IN context         : The dropt context.
  *     OUT optionName     : On output, the name of the option we failed
  *                            on.  Do not free this string.
  *                          Pass NULL if unwanted.
  *     OUT optionArgument : On output, the value (possibly NULL) of the
  *                            option we failed on.  Do not free this
  *                            string.
  *                          Pass NULL if unwanted.
  */
void
dropt_get_error_details(const dropt_context* context,
                        dropt_char** optionName, dropt_char** optionArgument)
{
    if (optionName != NULL) { *optionName = context->errorDetails.optionName; }
    if (optionArgument != NULL) { *optionArgument = context->errorDetails.optionArgument; }
}


/** dropt_get_error_message
  *
  * PARAMETERS:
  *     IN context : The dropt context.
  *                  Must not be NULL.
  *
  * RETURNS:
  *     The current error message waiting in the dropt context or the empty
  *       string if there are no errors.  Note that calling any dropt
  *       function other than dropt_get_error, dropt_get_error_details, and
  *       dropt_get_error_message may invalidate a previously-returned
  *       string.
  */
const dropt_char*
dropt_get_error_message(dropt_context* context)
{
    if (context == NULL)
    {
        DROPT_MISUSE("no dropt context specified.");
        return DROPT_TEXT_LITERAL("");
    }

    if (context->errorDetails.err == dropt_error_none)
    {
        return DROPT_TEXT_LITERAL("");
    }

    if (context->errorDetails.message == NULL)
    {
        if (context->errorHandler != NULL)
        {
            context->errorDetails.message
                = context->errorHandler(context->errorDetails.err,
                                        context->errorDetails.optionName,
                                        context->errorDetails.optionArgument,
                                        context->errorHandlerData);
        }
        else
        {
#ifndef DROPT_NO_STRING_BUFFERS
            context->errorDetails.message
                = dropt_default_error_handler(context->errorDetails.err,
                                              context->errorDetails.optionName,
                                              context->errorDetails.optionArgument);
#endif
        }
    }

    return (context->errorDetails.message == NULL)
           ? DROPT_TEXT_LITERAL("unknown error")
           : context->errorDetails.message;
}


/** dropt_clear_error
  *
  *     Clears the error waiting in the dropt context.
  *
  * PARAMETERS:
  *     IN/OUT context : The dropt context to free.
  *                      May be NULL.
  */
void
dropt_clear_error(dropt_context* context)
{
    if (context != NULL)
    {
        context->errorDetails.err = dropt_error_none;

        free(context->errorDetails.optionName);
        context->errorDetails.optionName = NULL;

        free(context->errorDetails.optionArgument);
        context->errorDetails.optionArgument = NULL;

        free(context->errorDetails.message);
        context->errorDetails.message = NULL;
    }
}


#ifndef DROPT_NO_STRING_BUFFERS
/** dropt_default_error_handler
  *
  *     Default error handler.
  *
  * PARAMETERS:
  *     IN error          : The error code.
  *     IN optionName     : The name of the option we failed on.
  *     IN optionArgument : The value of the option we failed on.
  *                         Pass NULL if unwanted.
  *
  * RETURNS:
  *     An allocated string for the given error.  The caller is responsible
  *       for calling free() on it when no longer needed.
  *     May return NULL.
  */
dropt_char*
dropt_default_error_handler(dropt_error error,
                            const dropt_char* optionName,
                            const dropt_char* optionArgument)
{
    dropt_char* s = NULL;

    const dropt_char* separator = DROPT_TEXT_LITERAL(": ");

    if (optionArgument == NULL)
    {
        separator = optionArgument = DROPT_TEXT_LITERAL("");
    }

    switch (error)
    {
        case dropt_error_none:
            /* This shouldn't happen (unless client code invokes this
             * directly with dropt_error_none), but it's here for
             * completeness.
             */
            break;

        case dropt_error_bad_configuration:
            s = dropt_strdup(DROPT_TEXT_LITERAL("invalid option configuration"));
            break;

        case dropt_error_invalid_option:
            s = dropt_asprintf(DROPT_TEXT_LITERAL("invalid option: %s"),
                               optionName);
            break;
        case dropt_error_insufficient_arguments:
            s = dropt_asprintf(DROPT_TEXT_LITERAL("value required after option %s"),
                               optionName);
            break;
        case dropt_error_mismatch:
            s = dropt_asprintf(DROPT_TEXT_LITERAL("invalid value for option %s%s%s"),
                               optionName, separator, optionArgument);
            break;
        case dropt_error_overflow:
            s = dropt_asprintf(DROPT_TEXT_LITERAL("value too large for option %s%s%s"),
                               optionName, separator, optionArgument);
            break;
        case dropt_error_underflow:
            s = dropt_asprintf(DROPT_TEXT_LITERAL("value too small for option %s%s%s"),
                               optionName, separator, optionArgument);
            break;
        case dropt_error_insufficient_memory:
            s = dropt_strdup(DROPT_TEXT_LITERAL("insufficient memory"));
            break;
        case dropt_error_unknown:
        default:
            s = dropt_asprintf(DROPT_TEXT_LITERAL("unknown error handling option %s"),
                               optionName);
            break;
    }

    return s;
}


/** dropt_get_help
  *
  * PARAMETERS:
  *     IN context    : The dropt context.
  *                     Must not be NULL.
  *     IN helpParams : The help parameters.
  *                     Pass NULL to use the default help parameters.
  *
  * RETURNS:
  *     An allocated help string for the available options.  The caller is
  *       responsible for calling free() on it when no longer needed.
  *     Returns NULL on error.
  */
dropt_char*
dropt_get_help(const dropt_context* context, const dropt_help_params* helpParams)
{
    dropt_char* helpText = NULL;
    dropt_stringstream* ss = NULL;

    if (context == NULL)
    {
        DROPT_MISUSE("No dropt context specified.");
        return NULL;
    }

    ss = dropt_ssopen();

    if (ss != NULL)
    {
        const dropt_option* option;
        dropt_help_params hp;

        if (helpParams == NULL)
        {
            dropt_init_help_params(&hp);
        }
        else
        {
            hp = *helpParams;
        }

        for (option = context->options; is_valid_option(option); option++)
        {
            bool hasLongName =    option->long_name != NULL
                               && option->long_name[0] != DROPT_TEXT_LITERAL('\0');
            bool hasShortName = option->short_name != DROPT_TEXT_LITERAL('\0');

            /* The number of characters printed on the current line so far. */
            int n;

            if (option->description == NULL || (option->attr & dropt_attr_hidden))
            {
                /* Undocumented option.  Ignore it and move on. */
                continue;
            }
            else if (hasLongName && hasShortName)
            {
                n = dropt_ssprintf(ss, DROPT_TEXT_LITERAL("%*s-%c, --%s"),
                                   hp.indent, DROPT_TEXT_LITERAL(""),
                                   option->short_name, option->long_name);
            }
            else if (hasLongName)
            {
                n = dropt_ssprintf(ss, DROPT_TEXT_LITERAL("%*s--%s"),
                                   hp.indent, DROPT_TEXT_LITERAL(""),
                                   option->long_name);
            }
            else if (hasShortName)
            {
                n = dropt_ssprintf(ss, DROPT_TEXT_LITERAL("%*s-%c"),
                                   hp.indent, DROPT_TEXT_LITERAL(""),
                                   option->short_name);
            }
            else
            {
                /* Comment text.  Don't bother with indentation. */
                assert(option->description != NULL);
                dropt_ssprintf(ss, DROPT_TEXT_LITERAL("%s\n"), option->description);
                goto next;
            }

            if (n < 0) { n = 0; }

            if (option->arg_description != NULL)
            {
                int m = dropt_ssprintf(ss,
                                       (option->attr & dropt_attr_optional_val)
                                       ? DROPT_TEXT_LITERAL("[=%s]")
                                       : DROPT_TEXT_LITERAL("=%s"),
                                       option->arg_description);
                if (m > 0) { n += m; }
            }

            /* Check for equality to make sure that there's at least one
             * space between the option name and its description.
             */
            if ((unsigned int) n >= hp.description_start_column)
            {
                dropt_ssprintf(ss, DROPT_TEXT_LITERAL("\n"));
                n = 0;
            }

            {
                const dropt_char* line = option->description;
                while (line != NULL)
                {
                    size_t lineLen;
                    const dropt_char* nextLine;
                    const dropt_char* newline = dropt_strchr(line, DROPT_TEXT_LITERAL('\n'));

                    if (newline == NULL)
                    {
                      lineLen = (int)dropt_strlen(line);
                        nextLine = NULL;
                    }
                    else
                    {
                      lineLen = (int)(newline - line);
                        nextLine = newline + 1;
                    }

                    dropt_ssprintf(ss, DROPT_TEXT_LITERAL("%*s%.*s\n"),
                                   hp.description_start_column - n, DROPT_TEXT_LITERAL(""),
                                   lineLen, line);
                    n = 0;

                    line = nextLine;
                }
            }

        next:
            if (hp.blank_lines_between_options)
            {
                dropt_ssprintf(ss, DROPT_TEXT_LITERAL("\n"));
            }
        }
        helpText = dropt_ssfinalize(ss);
    }

    return helpText;
}


/** dropt_print_help
  *
  *     Prints help for the available options.
  *
  * PARAMETERS:
  *     IN/OUT f      : The file stream to print to.
  *     IN context    : The dropt context.
  *                     Must not be NULL.
  *     IN helpParams : The help parameters.
  *                     Pass NULL to use the default help parameters.
  */
void
dropt_print_help(FILE* f, const dropt_context* context,
                 const dropt_help_params* helpParams)
{
    dropt_char* helpText = dropt_get_help(context, helpParams);
    if (helpText != NULL)
    {
        dropt_fputs(helpText, f);
        free(helpText);
    }
}
#endif /* DROPT_NO_STRING_BUFFERS */


/** set_option_value
  *
  *     Sets the value for a specified option by invoking the option's
  *     handler callback.
  *
  * PARAMETERS:
  *     IN/OUT context    : The dropt context.
  *     IN option         : The option.
  *     IN optionArgument : The option's value.  May be NULL.
  *
  * RETURNS:
  *     An error code.
  */
static dropt_error
set_option_value(dropt_context* context,
                 const dropt_option* option, const dropt_char* optionArgument)
{
    assert(option != NULL);

    if (option->handler == NULL)
    {
        DROPT_MISUSE("No option handler specified.");
        return dropt_error_bad_configuration;
    }

    return option->handler(context, optionArgument, option->handler_data);
}


/** parse_option_arg
  *
  *     Helper function to dropt_parse to deal with consuming possibly
  *     optional arguments.
  *
  * PARAMETERS:
  *     IN/OUT context : The dropt context.
  *     IN/OUT ps      : The current parse state.
  *
  * RETURNS:
  *     An error code.
  */
static dropt_error
parse_option_arg(dropt_context* context, parse_state* ps)
{
    dropt_error err;

    bool consumeNextArg = false;

    if (OPTION_TAKES_ARG(ps->option) && ps->optionArgument == NULL)
    {
        /* The option expects an argument, but none was specified with '='.
         * Try using the next item from the command-line.
         */
        if (ps->argsLeft > 0 && *(ps->argNext) != NULL)
        {
            consumeNextArg = true;
            ps->optionArgument = *(ps->argNext);
        }
        else if (!(ps->option->attr & dropt_attr_optional_val))
        {
            err = dropt_error_insufficient_arguments;
            goto exit;
        }
    }

    /* Even for options that don't ask for arguments, always parse and
     * consume an argument that was specified with '='.
     */
    err = set_option_value(context, ps->option, ps->optionArgument);

    if (   err != dropt_error_none
        && (ps->option->attr & dropt_attr_optional_val)
        && consumeNextArg
        && ps->optionArgument != NULL)
    {
        /* The option's handler didn't like the argument we fed it.  If the
         * argument was optional, try again without it.
         */
        consumeNextArg = false;
        ps->optionArgument = NULL;
        err = set_option_value(context, ps->option, NULL);
    }

exit:
    if (err == dropt_error_none && consumeNextArg)
    {
        ps->argNext++;
        ps->argsLeft--;
    }
    return err;
}


/** dropt_parse
  *
  *     Parses command-line options.
  *
  * PARAMETERS:
  *     IN/OUT context : The dropt context.
  *                      Must not be NULL.
  *     IN argc        : The maximum number of arguments to parse from
  *                        argv.
  *                      Pass -1 to parse all arguments up to a NULL
  *                        sentinel value.
  *     IN argv        : The list of command-line arguments, not including
  *                        the initial program name.
  *
  * RETURNS:
  *     A pointer to the first unprocessed element in argv.
  */
dropt_char**
dropt_parse(dropt_context* context,
            int argc, dropt_char** argv)
{
    dropt_error err = dropt_error_none;

    dropt_char* arg;
    parse_state ps;

    ps.option = NULL;
    ps.optionArgument = NULL;
    ps.argNext = argv;

    if (argv == NULL)
    {
        /* Nothing to do. */
        goto exit;
    }

    if (context == NULL)
    {
        DROPT_MISUSE("No dropt context specified.");
        goto exit;
    }

#ifdef DROPT_NO_STRING_BUFFERS
    if (context->errorHandler == NULL)
    {
        DROPT_MISUSE("No error handler specified.");
        set_error_details(context, dropt_error_bad_configuration,
                          make_char_array(DROPT_TEXT_LITERAL(""), 0),
                          NULL);
        goto exit;
    }
#endif

    if (argc == -1)
    {
        argc = 0;
        while (argv[argc] != NULL) { argc++; }
    }

    if (argc == 0)
    {
        /* Nothing to do. */
        goto exit;
    }

    init_lookup_tables(context);

    ps.argsLeft = argc;

    while (   ps.argsLeft-- > 0
           && (arg = *ps.argNext) != NULL
           && arg[0] == DROPT_TEXT_LITERAL('-'))
    {
        assert(err == dropt_error_none);

        if (arg[1] == DROPT_TEXT_LITERAL('\0'))
        {
            /* - */

            /* This intentionally leaves "-" unprocessed for the caller to
             * deal with.  This allows construction of programs that treat
             * "-" to mean "stdin".
             */
            goto exit;
        }

        ps.argNext++;

        if (arg[1] == DROPT_TEXT_LITERAL('-'))
        {
            const dropt_char* longName = arg + 2;
            if (longName[0] == DROPT_TEXT_LITERAL('\0'))
            {
                /* -- */

                /* This is used to mark the end of the option processing
                 * to prevent some arguments with leading '-' characters
                 * from being treated as options.
                 *
                 * Don't pass this back to the caller.
                 */
                goto exit;
            }
            else if (longName[0] == DROPT_TEXT_LITERAL('='))
            {
                /* Deal with the pathological case of a user supplying
                 * "--=".
                 */
                err = dropt_error_invalid_option;
                set_error_details(context, err,
                                  make_char_array(arg, dropt_strlen(arg)),
                                  NULL);
                goto exit;
            }
            else
            {
                /* --longName */
                const dropt_char* p = dropt_strchr(longName, DROPT_TEXT_LITERAL('='));
                const dropt_char* longNameEnd;
                if (p != NULL)
                {
                    /* --longName=arg */
                    longNameEnd = p;
                    ps.optionArgument = p + 1;
                }
                else
                {
                    longNameEnd = longName + dropt_strlen(longName);
                    assert(ps.optionArgument == NULL);
                }

                /* Pass the length of the option name so that we don't need
                 * to mutate the original string by inserting a
                 * NUL-terminator.
                 */
                ps.option = find_option_long(context,
                                             make_char_array(longName,
                                                             longNameEnd - longName));
                if (ps.option == NULL)
                {
                    err = dropt_error_invalid_option;
                    set_error_details(context, err,
                                      make_char_array(arg, longNameEnd - arg),
                                      NULL);
                }
                else
                {
                    err = parse_option_arg(context, &ps);
                    if (err != dropt_error_none)
                    {
                        set_error_details(context, err,
                                          make_char_array(arg, longNameEnd - arg),
                                          ps.optionArgument);
                    }
                }

                if (   err != dropt_error_none
                    || ps.option->attr & dropt_attr_halt)
                {
                    goto exit;
                }
            }
        }
        else
        {
            /* Short name. (-x) */
            size_t len;
            size_t j;

            if (arg[1] == DROPT_TEXT_LITERAL('='))
            {
                /* Deal with the pathological case of a user supplying
                 * "-=".
                 */
                err = dropt_error_invalid_option;
                set_error_details(context, err,
                                  make_char_array(arg, dropt_strlen(arg)),
                                  NULL);
                goto exit;
            }
            else
            {
                const dropt_char* p = dropt_strchr(arg, DROPT_TEXT_LITERAL('='));
                if (p != NULL)
                {
                    /* -x=arg */
                    len = p - arg;
                    ps.optionArgument = p + 1;
                }
                else
                {
                    len = dropt_strlen(arg);
                    assert(ps.optionArgument == NULL);
                }
            }

            for (j = 1; j < len; j++)
            {
                ps.option = find_option_short(context, arg[j]);
                if (ps.option == NULL)
                {
                    err = dropt_error_invalid_option;
                    set_short_option_error_details(context, err, arg[j], NULL);
                    goto exit;
                }
                else if (j + 1 == len)
                {
                    /* The last short option in a condensed list gets
                     * to use an argument.
                     */
                    err = parse_option_arg(context, &ps);
                    if (err != dropt_error_none)
                    {
                        set_short_option_error_details(context, err, arg[j],
                                                       ps.optionArgument);
                        goto exit;
                    }
                }
                else if (   context->allowConcatenatedArgs
                         && OPTION_TAKES_ARG(ps.option)
                         && j == 1)
                {
                    err = set_option_value(context, ps.option, &arg[j + 1]);

                    if (   err != dropt_error_none
                        && (ps.option->attr & dropt_attr_optional_val))
                    {
                        err = set_option_value(context, ps.option, NULL);
                    }

                    if (err != dropt_error_none)
                    {
                        set_short_option_error_details(context, err, arg[j], &arg[j + 1]);
                        goto exit;
                    }

                    /* Skip to the next argument. */
                    break;
                }
                else if (   OPTION_TAKES_ARG(ps.option)
                         && !(ps.option->attr & dropt_attr_optional_val))
                {
                    /* Short options with required arguments can't be used
                     * in condensed lists except in the last position.
                     *
                     * e.g. -abcd arg
                     *          ^
                     */
                    err = dropt_error_insufficient_arguments;
                    set_short_option_error_details(context, err, arg[j], NULL);
                    goto exit;
                }
                else
                {
                    err = set_option_value(context, ps.option, NULL);
                    if (err != dropt_error_none)
                    {
                        set_short_option_error_details(context, err, arg[j], NULL);
                        goto exit;
                    }
                }

                if (ps.option->attr & dropt_attr_halt) { goto exit; }
            }
        }

        ps.option = NULL;
        ps.optionArgument = NULL;
    }

exit:
    return ps.argNext;
}


/** dropt_new_context
  *
  *     Creates a new dropt context.
  *
  * PARAMETERS:
  *     IN options : The list of option specifications.
  *                  Must not be NULL.
  *
  * RETURNS:
  *     An allocated dropt context.  The caller is responsible for freeing
  *       it with dropt_free_context when no longer needed.
  *     Returns NULL on error.
  */
dropt_context*
dropt_new_context(const dropt_option* options)
{
    dropt_context* context = NULL;
    size_t n;

    if (options == NULL)
    {
        DROPT_MISUSE("No option list specified.");
        goto exit;
    }

    /* Sanity-check the options. */
    for (n = 0; is_valid_option(&options[n]); n++)
    {
        if (   options[n].short_name == DROPT_TEXT_LITERAL('=')
            || (   options[n].long_name != NULL
                && dropt_strchr(options[n].long_name, DROPT_TEXT_LITERAL('=')) != NULL))
        {
            DROPT_MISUSE("Invalid option list. '=' may not be used in an option name.");
            goto exit;
        }
    }

    context = malloc(sizeof *context);
    if (context == NULL)
    {
        goto exit;
    }
    else
    {
        dropt_context emptyContext = { 0 };
        *context = emptyContext;

        context->options = options;
        context->numOptions = n;
        dropt_set_strncmp(context, NULL);
    }

exit:
    return context;
}


/** dropt_free_context
  *
  *     Frees a dropt context.
  *
  * PARAMETERS:
  *     IN/OUT context : The dropt context to free.
  *                      May be NULL.
  */
void
dropt_free_context(dropt_context* context)
{
    dropt_clear_error(context);
    free_lookup_tables(context);
    free(context);
}


/** dropt_get_options
  *
  * PARAMETERS:
  *     IN context : The dropt context.
  *                  Must not be NULL.
  *
  * RETURNS:
  *     The context's list of option specifications.
  */
const dropt_option*
dropt_get_options(const dropt_context* context)
{
    if (context == NULL)
    {
        DROPT_MISUSE("No dropt context specified.");
        return NULL;
    }

    return context->options;
}


/** dropt_init_help_params
  *
  *     Initializes a dropt_help_params structure with the default
  *     values.
  *
  * PARAMETERS:
  *     OUT helpParams : On output, set to the default help parameters.
  *                      Must not be NULL.
  */
void
dropt_init_help_params(dropt_help_params* helpParams)
{
    if (helpParams == NULL)
    {
        DROPT_MISUSE("No dropt help parameters specified.");
        return;
    }

    helpParams->indent = default_help_indent;
    helpParams->description_start_column = default_description_start_column;
    helpParams->blank_lines_between_options = true;
}


/** dropt_set_error_handler
  *
  *     Sets the callback function used to generate error strings from
  *     error codes.
  *
  * PARAMETERS:
  *     IN/OUT context : The dropt context.
  *                      Must not be NULL.
  *     IN handler     : The error handler callback.
  *                      Pass NULL to use the default error handler.
  *     IN handlerData : Caller-defined callback data.
  */
void
dropt_set_error_handler(dropt_context* context, dropt_error_handler_func handler, void* handlerData)
{
    if (context == NULL)
    {
        DROPT_MISUSE("No dropt context specified.");
        return;
    }

    context->errorHandler = handler;
    context->errorHandlerData = handlerData;
}


/** dropt_set_strncmp
  *
  *     Sets the callback function used to compare strings.
  *
  * PARAMETERS:
  *     IN/OUT context : The dropt context.
  *                      Must not be NULL.
  *     IN cmp         : The string comparison function.
  *                      Pass NULL to use the default string comparison
  *                        function.
  */
void
dropt_set_strncmp(dropt_context* context, dropt_strncmp_func cmp)
{
    if (context == NULL)
    {
        DROPT_MISUSE("No dropt context specified.");
        return;
    }

    if (cmp == NULL) { cmp = dropt_strncmp; }
    context->ncmpstr = cmp;

    /* Changing the sort method invalidates our existing lookup tables. */
    free_lookup_tables(context);
}


/** dropt_allow_concatenated_arguments
  *
  *     Specifies whether "short" options are allowed to have concatenated
  *     arguments (i.e. without space or '=' separators, such as -oARGUMENT).
  *
  *     (Concatenated arguments are disallowed by default.)
  *
  * PARAMETERS:
  *     IN/OUT context : The dropt context.
  *     IN allow       : Pass 1 if concatenated arguments should be allowed,
  *                        0 otherwise.
  */
void
dropt_allow_concatenated_arguments(dropt_context* context, dropt_bool allow)
{
    if (context == NULL)
    {
        DROPT_MISUSE("No dropt context specified.");
        return;
    }

    context->allowConcatenatedArgs = (allow != 0);
}


/** dropt_misuse
  *
  *     Prints a diagnostic for logical errors caused by external clients
  *     calling into dropt improperly.
  *
  *     In debug builds, terminates the program and prints the filename and
  *     line number of the failure.
  *
  *     For logical errors entirely internal to dropt, use assert()
  *     instead.
  *
  * PARAMETERS:
  *     IN message  : The error message.
  *                   Must not be NULL.
  *     IN filename : The name of the file where the logical error
  *                     occurred.
  *                   Must not be NULL.
  *     IN line     : The line number where the logical error occurred.
  */
void
dropt_misuse(const char* message, const char* filename, int line)
{
#ifdef NDEBUG
    (void)filename;
    (void)line;
    fprintf(stderr, "dropt: %s\n", message);
#else
    fprintf(stderr, "dropt: %s (%s: %d)\n", message, filename, line);
    abort();
#endif
}
