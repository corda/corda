/** dropt.h
  *
  * A deliberately rudimentary command-line option parser.
  *
  * Version 1.1.1
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

#ifndef DROPT_H
#define DROPT_H

#include <stdio.h>
#include <wchar.h>

#ifdef __cplusplus
extern "C" {
#endif


#ifndef DROPT_USE_WCHAR
#if defined _UNICODE && (defined _MSC_VER || defined DROPT_NO_STRING_BUFFERS)
#define DROPT_USE_WCHAR 1
#endif
#endif

#ifdef DROPT_USE_WCHAR
    /* This may be used for both char and string literals. */
    #define DROPT_TEXT_LITERAL(s) L ## s

    typedef wchar_t dropt_char;
#else
    #define DROPT_TEXT_LITERAL(s) s

    typedef char dropt_char;
#endif


enum
{
    /* Errors in the range [0x00, 0x7F] are reserved for dropt. */
    dropt_error_none,
    dropt_error_unknown,
    dropt_error_bad_configuration,
    dropt_error_insufficient_memory,
    dropt_error_invalid_option,
    dropt_error_insufficient_arguments,
    dropt_error_mismatch,
    dropt_error_overflow,
    dropt_error_underflow,

    /* Errors in the range [0x80, 0xFFFF] are free for clients to use. */
    dropt_error_custom_start = 0x80,
    dropt_error_custom_last = 0xFFFF
};
typedef unsigned int dropt_error;

typedef unsigned char dropt_bool;

/* Opaque. */
typedef struct dropt_context dropt_context;


/** dropt_option_handler_func callbacks are responsible for parsing
  * individual options.
  *
  * dropt_option_handler_decl may be used for declaring the callback
  * functions; dropt_option_handler_func is the actual function pointer
  * type.
  *
  * optionArgument will be NULL if no argument is specified for an option.
  * It will be the empty string if the user explicitly passed an empty
  * string as the argument (e.g. --option="").
  *
  * An option that doesn't expect an argument still can receive a non-NULL
  * value for optionArgument if the user explicitly specified one (e.g.
  * --option=arg).
  *
  * If the option's argument is optional, the handler might be called
  * twice: once with a candidate argument, and if that argument is rejected
  * by the handler, again with no argument.  Handlers should be aware of
  * this if they have side-effects.
  *
  * handlerData is the client-specified value specified in the dropt_option
  * table.
  */
typedef dropt_error dropt_option_handler_decl(dropt_context* context,
                                              const dropt_char* optionArgument,
                                              void* handlerData);
typedef dropt_option_handler_decl* dropt_option_handler_func;

/** dropt_error_handler_func callbacks are responsible for generating error
  * messages.  The returned string must be allocated on the heap and must
  * be freeable with free().
  */
typedef dropt_char* (*dropt_error_handler_func)(dropt_error error,
                                                const dropt_char* optionName,
                                                const dropt_char* optionArgument,
                                                void* handlerData);

/** dropt_strncmp_func callbacks allow callers to provide their own (possibly
  * case-insensitive) string comparison function.
  */
typedef int (*dropt_strncmp_func)(const dropt_char* s, const dropt_char* t, size_t n);


/** Properties defining each option:
  *
  * short_name:
  *     The option's short name (e.g. the 'h' in -h).
  *     Use '\0' if the option has no short name.
  *
  * long_name:
  *     The option's long name (e.g. "help" in --help).
  *     Use NULL if the option has no long name.
  *
  * description:
  *     The description shown when generating help.
  *     May be NULL for undocumented options.
  *
  * arg_description:
  *     The description for the option's argument (e.g. --option=argument
  *     or --option argument), printed when generating help.  If NULL, the
  *     option does not take an argument.
  *
  * handler:
  *     The handler callback and data invoked in response to encountering
  *     the option.
  *
  * handler_data:
  *     Callback data for the handler.  For typical handlers, this is
  *     usually the address of a variable for the handler to modify.
  *
  * attr:
  *     Miscellaneous attributes.  See below.
  */
typedef struct dropt_option
{
    dropt_char short_name;
    const dropt_char* long_name;
    const dropt_char* description;
    const dropt_char* arg_description;
    dropt_option_handler_func handler;
    void* handler_data;
    unsigned int attr;
} dropt_option;


/** Bitwise flags for option attributes:
  *
  * dropt_attr_halt:
  *     Stop processing when this option is encountered.
  *
  * dropt_attr_hidden:
  *     Don't list the option when generating help.  Use this for
  *     undocumented options.
  *
  * dropt_attr_optional_val:
  *     The option's argument is optional.  If an option has this
  *     attribute, the handler callback may be invoked twice (once with a
  *     potential argument, and if that fails, again with a NULL argument).
  */
enum
{
    dropt_attr_halt = (1 << 0),
    dropt_attr_hidden = (1 << 1),
    dropt_attr_optional_val = (1 << 2)
};


typedef struct dropt_help_params
{
    unsigned int indent;
    unsigned int description_start_column;
    dropt_bool blank_lines_between_options;
} dropt_help_params;


dropt_context* dropt_new_context(const dropt_option* options);
void dropt_free_context(dropt_context* context);

const dropt_option* dropt_get_options(const dropt_context* context);

void dropt_set_error_handler(dropt_context* context,
                             dropt_error_handler_func handler, void* handlerData);
void dropt_set_strncmp(dropt_context* context, dropt_strncmp_func cmp);

/* Use this only for backward compatibility purposes. */
void dropt_allow_concatenated_arguments(dropt_context* context, dropt_bool allow);

dropt_char** dropt_parse(dropt_context* context, int argc, dropt_char** argv);

dropt_error dropt_get_error(const dropt_context* context);
void dropt_get_error_details(const dropt_context* context,
                             dropt_char** optionName,
                             dropt_char** optionArgument);
const dropt_char* dropt_get_error_message(dropt_context* context);
void dropt_clear_error(dropt_context* context);

#ifndef DROPT_NO_STRING_BUFFERS
dropt_char* dropt_default_error_handler(dropt_error error,
                                        const dropt_char* optionName,
                                        const dropt_char* optionArgument);

void dropt_init_help_params(dropt_help_params* helpParams);
dropt_char* dropt_get_help(const dropt_context* context,
                           const dropt_help_params* helpParams);
void dropt_print_help(FILE* f, const dropt_context* context,
                      const dropt_help_params* helpParams);
#endif


/* Stock option handlers for common types. */
dropt_option_handler_decl dropt_handle_bool;
dropt_option_handler_decl dropt_handle_verbose_bool;
dropt_option_handler_decl dropt_handle_int;
dropt_option_handler_decl dropt_handle_uint;
dropt_option_handler_decl dropt_handle_double;
dropt_option_handler_decl dropt_handle_string;

#define DROPT_MISUSE(message) dropt_misuse(message, __FILE__, __LINE__)
void dropt_misuse(const char* message, const char* filename, int line);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* DROPT_H */
