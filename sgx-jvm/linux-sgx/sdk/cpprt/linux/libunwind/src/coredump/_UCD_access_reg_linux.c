/* libunwind - a platform-independent unwind library

This file is part of libunwind.

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.  */

#include "_UCD_lib.h"

#include "_UCD_internal.h"

int
_UCD_access_reg (unw_addr_space_t as,
                                unw_regnum_t regnum, unw_word_t *valp,
                                int write, void *arg)
{
  if (write)
    {
      Debug(0, "%s: write is not supported\n", __func__);
      return -UNW_EINVAL;
    }

#if defined(UNW_TARGET_X86)
  static const uint8_t remap_regs[] =
    {
      /* names from libunwind-x86.h */
      [UNW_X86_EAX]    = offsetof(struct user_regs_struct, eax) / sizeof(long),
      [UNW_X86_EDX]    = offsetof(struct user_regs_struct, edx) / sizeof(long),
      [UNW_X86_ECX]    = offsetof(struct user_regs_struct, ecx) / sizeof(long),
      [UNW_X86_EBX]    = offsetof(struct user_regs_struct, ebx) / sizeof(long),
      [UNW_X86_ESI]    = offsetof(struct user_regs_struct, esi) / sizeof(long),
      [UNW_X86_EDI]    = offsetof(struct user_regs_struct, edi) / sizeof(long),
      [UNW_X86_EBP]    = offsetof(struct user_regs_struct, ebp) / sizeof(long),
      [UNW_X86_ESP]    = offsetof(struct user_regs_struct, esp) / sizeof(long),
      [UNW_X86_EIP]    = offsetof(struct user_regs_struct, eip) / sizeof(long),
      [UNW_X86_EFLAGS] = offsetof(struct user_regs_struct, eflags) / sizeof(long),
      [UNW_X86_TRAPNO] = offsetof(struct user_regs_struct, orig_eax) / sizeof(long),
    };
#elif defined(UNW_TARGET_X86_64)
  static const int8_t remap_regs[] =
    {
      [UNW_X86_64_RAX]    = offsetof(struct user_regs_struct, rax) / sizeof(long),
      [UNW_X86_64_RDX]    = offsetof(struct user_regs_struct, rdx) / sizeof(long),
      [UNW_X86_64_RCX]    = offsetof(struct user_regs_struct, rcx) / sizeof(long),
      [UNW_X86_64_RBX]    = offsetof(struct user_regs_struct, rbx) / sizeof(long),
      [UNW_X86_64_RSI]    = offsetof(struct user_regs_struct, rsi) / sizeof(long),
      [UNW_X86_64_RDI]    = offsetof(struct user_regs_struct, rdi) / sizeof(long),
      [UNW_X86_64_RBP]    = offsetof(struct user_regs_struct, rbp) / sizeof(long),
      [UNW_X86_64_RSP]    = offsetof(struct user_regs_struct, rsp) / sizeof(long),
      [UNW_X86_64_RIP]    = offsetof(struct user_regs_struct, rip) / sizeof(long),
    };
#else
#error Port me
#endif

  struct UCD_info *ui = arg;
  if (regnum < 0 || regnum >= (unw_regnum_t)ARRAY_SIZE(remap_regs))
    {
      Debug(0, "%s: bad regnum:%d\n", __func__, regnum);
      return -UNW_EINVAL;
    }
  regnum = remap_regs[regnum];

  /* pr_reg is a long[] array, but it contains struct user_regs_struct's
   * image.
   */
  Debug(1, "pr_reg[%d]:%ld (0x%lx)", regnum,
		(long)ui->prstatus->pr_reg[regnum],
		(long)ui->prstatus->pr_reg[regnum]
  );
  *valp = ui->prstatus->pr_reg[regnum];

  return 0;
}
