#include <stdarg.h>
#include <stdio.h>      /* vsnprintf */

#include "$(enclaveName).h"
#include "$(enclaveName)_t.h"  /* print_string */

/* 
 * printf: 
 *   Invokes OCALL to display the enclave buffer to the terminal.
 */
void printf(const char *fmt, ...)
{
    char buf[BUFSIZ] = {'\0'};
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, BUFSIZ, fmt, ap);
    va_end(ap);
    ocall_$(enclaveName)_sample(buf);
}

int ecall_$(enclaveName)_sample()
{
  printf("IN $(ENCLAVENAME)\n");
  return 0;
}

