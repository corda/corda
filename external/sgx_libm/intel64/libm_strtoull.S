/*
* Math library
*
* Copyright (C) 2016 Intel Corporation. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions
* are met:
*
*   * Redistributions of source code must retain the above copyright
*     notice, this list of conditions and the following disclaimer.
*   * Redistributions in binary form must reproduce the above copyright
*     notice, this list of conditions and the following disclaimer in
*     the documentation and/or other materials provided with the
*     distribution.
*   * Neither the name of Intel Corporation nor the names of its
*     contributors may be used to endorse or promote products derived
*     from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
* "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
* LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
* A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
* OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
* SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
* DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
* THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*
*
* Author Name <jingwei.zhang@intel.com>
*   History:
*   03-14-2016 Initial version. numerics svn rev. 12864
*/
	.file "libm_strtoull.c"
	.text
..TXTST0:
# -- Begin  __libm_conv_strtoull
	.text
       .align    16,0x90
	.globl __libm_conv_strtoull
__libm_conv_strtoull:
# parameter 1: %rdi
..B1.1:
	.cfi_startproc
..___tag_value___libm_conv_strtoull.1:
..L2:

        movq      %rdi, %rdx
        xorl      %esi, %esi
        testq     %rdi, %rdi
        je        ..B1.7
..B1.2:
        movsbl    (%rdi), %eax
        testl     %eax, %eax
        je        ..B1.7
..B1.4:
        incq      %rdx
        incl      %esi
        cmpb      $0, (%rdx)
        jne       ..B1.4
..B1.5:
        testl     %esi, %esi
        je        ..B1.7
..B1.6:
        lea       -48(%rax), %ecx
        cmpl      $9, %ecx
        jbe       ..B1.8
..B1.7:
        xorl      %eax, %eax
        jmp       ..B1.21
..B1.8:
        cmpl      $48, %eax
        je        ..B1.23
..B1.9:
        movl      $1, %ecx
        cmpl      $1, %esi
        jle       ..B1.14
..B1.10:
        movslq    %esi, %rax
..B1.11:
        movsbl    (%rcx,%rdi), %r8d
        addl      $-48, %r8d
        cmpl      $9, %r8d
        ja        ..B1.7
..B1.12:
        incq      %rcx
        cmpq      %rax, %rcx
        jl        ..B1.11
..B1.14:
        movl      $64, %ecx
        cmpl      $64, %esi
        cmovb     %esi, %ecx
        xorl      %eax, %eax
        testl     %esi, %esi
        jle       ..B1.21
..B1.15:
        movl      %ecx, %r9d
        xorl      %r10d, %r10d
        shrl      $1, %r9d
        movl      $1, %edi
        xorl      %r8d, %r8d
        xorl      %r11d, %r11d
        testl     %r9d, %r9d
        jbe       ..B1.19
..B1.16:
        movq      %r12, -8(%rsp)
        xorl      %edi, %edi
        lea       _C_MUL_10_POW_I(%rip), %rsi
	.cfi_offset 12, -16
..B1.17:
        movb      -1(%r11,%rdx), %r12b
        incl      %r8d
        addb      $-48, %r12b
        movsbq    %r12b, %r12
        lea       (%rsi,%r12,8), %r12
        addq      (%r12,%r10), %rax
        movb      -2(%r11,%rdx), %r12b
        addq      $-2, %r11
        addb      $-48, %r12b
        movsbq    %r12b, %r12
        lea       (%rsi,%r12,8), %r12
        addq      80(%r12,%r10), %rdi
        addq      $160, %r10
        cmpl      %r9d, %r8d
        jb        ..B1.17
..B1.18:
        movq      -8(%rsp), %r12
	.cfi_restore 12
        addq      %rdi, %rax
        lea       1(%r8,%r8), %edi
..B1.19:
        lea       -1(%rdi), %esi
        cmpl      %esi, %ecx
        jbe       ..B1.21
..B1.20:
        movslq    %edi, %rdi
        lea       _C_MUL_10_POW_I(%rip), %r8
        subq      %rdi, %rdx
        lea       (%rdi,%rdi,4), %rcx
        movb      (%rdx), %dl
        addb      $-48, %dl
        movsbq    %dl, %rsi
        shlq      $4, %rcx
        lea       (%rcx,%rsi,8), %r9
        addq      -80(%r8,%r9), %rax
..B1.21:
        ret       
..B1.23:
        cmpl      $3, %esi
        jl        ..B1.25
..B1.24:
        movsbl    1(%rdi), %eax
        andl      $-33, %eax
        cmpl      $88, %eax
        je        ..B1.38
..B1.25:
        movl      $1, %ecx
        cmpl      $1, %esi
        jle       ..B1.30
..B1.26:
        movslq    %esi, %rax
..B1.27:
        movsbl    (%rcx,%rdi), %r8d
        addl      $-48, %r8d
        cmpl      $7, %r8d
        ja        ..B1.7
..B1.28:
        incq      %rcx
        cmpq      %rax, %rcx
        jl        ..B1.27
..B1.30:
        decl      %esi
        movl      $22, %r8d
        cmpl      $22, %esi
        cmovb     %esi, %r8d
        xorl      %eax, %eax
        testl     %esi, %esi
        jle       ..B1.21
..B1.31:
        movl      %r8d, %edi
        xorl      %esi, %esi
        shrl      $1, %edi
        xorl      %r9d, %r9d
        movl      $1, %r11d
        xorl      %r10d, %r10d
        testl     %edi, %edi
        jbe       ..B1.35
..B1.32:
        movq      %r12, -8(%rsp)
        xorl      %r11d, %r11d
	.cfi_offset 12, -16
..B1.33:
        movslq    %r9d, %r9
        movl      %r10d, %ecx
        incl      %esi
        movb      -1(%r9,%rdx), %r12b
        addb      $-48, %r12b
        movsbq    %r12b, %r12
        shlq      %cl, %r12
        lea       3(%r10), %ecx
        addq      %r12, %rax
        addl      $6, %r10d
        movb      -2(%r9,%rdx), %r12b
        addl      $-2, %r9d
        addb      $-48, %r12b
        movsbq    %r12b, %r12
        shlq      %cl, %r12
        addq      %r12, %r11
        cmpl      %edi, %esi
        jb        ..B1.33
..B1.34:
        movq      -8(%rsp), %r12
	.cfi_restore 12
        addq      %r11, %rax
        lea       1(%rsi,%rsi), %r11d
..B1.35:
        lea       -1(%r11), %ecx
        cmpl      %ecx, %r8d
        jbe       ..B1.21
..B1.36:
        movslq    %r11d, %r11
        subq      %r11, %rdx
        lea       61(%r11,%r11,2), %ecx
        movb      (%rdx), %dl
        addb      $-48, %dl
        movsbq    %dl, %rsi
        shlq      %cl, %rsi
        addq      %rsi, %rax
        jmp       ..B1.21
..B1.38:
        movslq    %esi, %rax
        xorl      %ecx, %ecx
        addq      $-2, %rax
..B1.39:
        movsbl    2(%rcx,%rdi), %r9d
        lea       -48(%r9), %r8d
        cmpl      $9, %r8d
        jbe       ..B1.42
..B1.40:
        lea       -97(%r9), %r8d
        cmpl      $5, %r8d
        jbe       ..B1.42
..B1.41:
        addl      $-65, %r9d
        cmpl      $5, %r9d
        ja        ..B1.7
..B1.42:
        incq      %rcx
        cmpq      %rax, %rcx
        jb        ..B1.39
..B1.43:
        addl      $-2, %esi
        movl      $16, %r9d
        cmpl      $16, %esi
        cmovb     %esi, %r9d
        xorl      %eax, %eax
        testl     %esi, %esi
        jle       ..B1.21
..B1.44:
        movl      %r9d, %r8d
        xorl      %r10d, %r10d
        shrl      $1, %r8d
        movl      $1, %ecx
        xorl      %r11d, %r11d
        testl     %r8d, %r8d
        jbe       ..B1.54
..B1.45:
        movq      %r12, -8(%rsp)
	.cfi_offset 12, -16
..B1.46:
        movslq    %r11d, %rsi
        movsbl    -1(%rsi,%rdx), %edi
        lea       -48(%rdi), %ecx
        cmpl      $9, %ecx
        ja        ..B1.48
..B1.47:
        movsbq    %cl, %rdi
        jmp       ..B1.49
..B1.48:
        lea       -97(%rdi), %ecx
        lea       -87(%rdi), %r12d
        addl      $-55, %edi
        cmpl      $5, %ecx
        movsbq    %r12b, %r12
        movsbq    %dil, %rdi
        cmovbe    %r12, %rdi
..B1.49:
        lea       (,%r10,8), %ecx
        shlq      %cl, %rdi
        movsbl    -2(%rsi,%rdx), %ecx
        addq      %rdi, %rax
        lea       -48(%rcx), %esi
        cmpl      $9, %esi
        ja        ..B1.51
..B1.50:
        movsbq    %sil, %rsi
        jmp       ..B1.52
..B1.51:
        lea       -97(%rcx), %edi
        lea       -87(%rcx), %esi
        addl      $-55, %ecx
        cmpl      $5, %edi
        movsbq    %sil, %r12
        movsbq    %cl, %rsi
        cmovbe    %r12, %rsi
..B1.52:
        addl      $-2, %r11d
        lea       4(,%r10,8), %ecx
        shlq      %cl, %rsi
        incl      %r10d
        addq      %rsi, %rax
        cmpl      %r8d, %r10d
        jb        ..B1.46
..B1.53:
        movq      -8(%rsp), %r12
	.cfi_restore 12
        lea       1(%r10,%r10), %ecx
..B1.54:
        lea       -1(%rcx), %esi
        cmpl      %esi, %r9d
        jbe       ..B1.21
..B1.55:
        movslq    %ecx, %rcx
        subq      %rcx, %rdx
        movsbl    (%rdx), %esi
        lea       -48(%rsi), %edx
        cmpl      $9, %edx
        ja        ..B1.57
..B1.56:
        movsbq    %dl, %rdx
        jmp       ..B1.58
..B1.57:
        lea       -97(%rsi), %edi
        lea       -87(%rsi), %edx
        addl      $-55, %esi
        cmpl      $5, %edi
        movsbq    %dl, %r8
        movsbq    %sil, %rdx
        cmovbe    %r8, %rdx
..B1.58:
        lea       60(,%rcx,4), %ecx
        shlq      %cl, %rdx
        addq      %rdx, %rax
        jmp       ..B1.21
        .align    16,0x90
	.cfi_endproc
	.type	__libm_conv_strtoull,@function
	.size	__libm_conv_strtoull,.-__libm_conv_strtoull
	.data
# -- End  __libm_conv_strtoull
	.section .rodata, "a"
	.align 8
	.align 8
_C_MUL_10_POW_I:
	.long	0x00000000,0x00000000
	.long	0x00000001,0x00000000
	.long	0x00000002,0x00000000
	.long	0x00000003,0x00000000
	.long	0x00000004,0x00000000
	.long	0x00000005,0x00000000
	.long	0x00000006,0x00000000
	.long	0x00000007,0x00000000
	.long	0x00000008,0x00000000
	.long	0x00000009,0x00000000
	.long	0x00000000,0x00000000
	.long	0x0000000a,0x00000000
	.long	0x00000014,0x00000000
	.long	0x0000001e,0x00000000
	.long	0x00000028,0x00000000
	.long	0x00000032,0x00000000
	.long	0x0000003c,0x00000000
	.long	0x00000046,0x00000000
	.long	0x00000050,0x00000000
	.long	0x0000005a,0x00000000
	.long	0x00000000,0x00000000
	.long	0x00000064,0x00000000
	.long	0x000000c8,0x00000000
	.long	0x0000012c,0x00000000
	.long	0x00000190,0x00000000
	.long	0x000001f4,0x00000000
	.long	0x00000258,0x00000000
	.long	0x000002bc,0x00000000
	.long	0x00000320,0x00000000
	.long	0x00000384,0x00000000
	.long	0x00000000,0x00000000
	.long	0x000003e8,0x00000000
	.long	0x000007d0,0x00000000
	.long	0x00000bb8,0x00000000
	.long	0x00000fa0,0x00000000
	.long	0x00001388,0x00000000
	.long	0x00001770,0x00000000
	.long	0x00001b58,0x00000000
	.long	0x00001f40,0x00000000
	.long	0x00002328,0x00000000
	.long	0x00000000,0x00000000
	.long	0x00002710,0x00000000
	.long	0x00004e20,0x00000000
	.long	0x00007530,0x00000000
	.long	0x00009c40,0x00000000
	.long	0x0000c350,0x00000000
	.long	0x0000ea60,0x00000000
	.long	0x00011170,0x00000000
	.long	0x00013880,0x00000000
	.long	0x00015f90,0x00000000
	.long	0x00000000,0x00000000
	.long	0x000186a0,0x00000000
	.long	0x00030d40,0x00000000
	.long	0x000493e0,0x00000000
	.long	0x00061a80,0x00000000
	.long	0x0007a120,0x00000000
	.long	0x000927c0,0x00000000
	.long	0x000aae60,0x00000000
	.long	0x000c3500,0x00000000
	.long	0x000dbba0,0x00000000
	.long	0x00000000,0x00000000
	.long	0x000f4240,0x00000000
	.long	0x001e8480,0x00000000
	.long	0x002dc6c0,0x00000000
	.long	0x003d0900,0x00000000
	.long	0x004c4b40,0x00000000
	.long	0x005b8d80,0x00000000
	.long	0x006acfc0,0x00000000
	.long	0x007a1200,0x00000000
	.long	0x00895440,0x00000000
	.long	0x00000000,0x00000000
	.long	0x00989680,0x00000000
	.long	0x01312d00,0x00000000
	.long	0x01c9c380,0x00000000
	.long	0x02625a00,0x00000000
	.long	0x02faf080,0x00000000
	.long	0x03938700,0x00000000
	.long	0x042c1d80,0x00000000
	.long	0x04c4b400,0x00000000
	.long	0x055d4a80,0x00000000
	.long	0x00000000,0x00000000
	.long	0x05f5e100,0x00000000
	.long	0x0bebc200,0x00000000
	.long	0x11e1a300,0x00000000
	.long	0x17d78400,0x00000000
	.long	0x1dcd6500,0x00000000
	.long	0x23c34600,0x00000000
	.long	0x29b92700,0x00000000
	.long	0x2faf0800,0x00000000
	.long	0x35a4e900,0x00000000
	.long	0x00000000,0x00000000
	.long	0x3b9aca00,0x00000000
	.long	0x77359400,0x00000000
	.long	0xb2d05e00,0x00000000
	.long	0xee6b2800,0x00000000
	.long	0x2a05f200,0x00000001
	.long	0x65a0bc00,0x00000001
	.long	0xa13b8600,0x00000001
	.long	0xdcd65000,0x00000001
	.long	0x18711a00,0x00000002
	.long	0x00000000,0x00000000
	.long	0x540be400,0x00000002
	.long	0xa817c800,0x00000004
	.long	0xfc23ac00,0x00000006
	.long	0x502f9000,0x00000009
	.long	0xa43b7400,0x0000000b
	.long	0xf8475800,0x0000000d
	.long	0x4c533c00,0x00000010
	.long	0xa05f2000,0x00000012
	.long	0xf46b0400,0x00000014
	.long	0x00000000,0x00000000
	.long	0x4876e800,0x00000017
	.long	0x90edd000,0x0000002e
	.long	0xd964b800,0x00000045
	.long	0x21dba000,0x0000005d
	.long	0x6a528800,0x00000074
	.long	0xb2c97000,0x0000008b
	.long	0xfb405800,0x000000a2
	.long	0x43b74000,0x000000ba
	.long	0x8c2e2800,0x000000d1
	.long	0x00000000,0x00000000
	.long	0xd4a51000,0x000000e8
	.long	0xa94a2000,0x000001d1
	.long	0x7def3000,0x000002ba
	.long	0x52944000,0x000003a3
	.long	0x27395000,0x0000048c
	.long	0xfbde6000,0x00000574
	.long	0xd0837000,0x0000065d
	.long	0xa5288000,0x00000746
	.long	0x79cd9000,0x0000082f
	.long	0x00000000,0x00000000
	.long	0x4e72a000,0x00000918
	.long	0x9ce54000,0x00001230
	.long	0xeb57e000,0x00001b48
	.long	0x39ca8000,0x00002461
	.long	0x883d2000,0x00002d79
	.long	0xd6afc000,0x00003691
	.long	0x25226000,0x00003faa
	.long	0x73950000,0x000048c2
	.long	0xc207a000,0x000051da
	.long	0x00000000,0x00000000
	.long	0x107a4000,0x00005af3
	.long	0x20f48000,0x0000b5e6
	.long	0x316ec000,0x000110d9
	.long	0x41e90000,0x00016bcc
	.long	0x52634000,0x0001c6bf
	.long	0x62dd8000,0x000221b2
	.long	0x7357c000,0x00027ca5
	.long	0x83d20000,0x0002d798
	.long	0x944c4000,0x0003328b
	.long	0x00000000,0x00000000
	.long	0xa4c68000,0x00038d7e
	.long	0x498d0000,0x00071afd
	.long	0xee538000,0x000aa87b
	.long	0x931a0000,0x000e35fa
	.long	0x37e08000,0x0011c379
	.long	0xdca70000,0x001550f7
	.long	0x816d8000,0x0018de76
	.long	0x26340000,0x001c6bf5
	.long	0xcafa8000,0x001ff973
	.long	0x00000000,0x00000000
	.long	0x6fc10000,0x002386f2
	.long	0xdf820000,0x00470de4
	.long	0x4f430000,0x006a94d7
	.long	0xbf040000,0x008e1bc9
	.long	0x2ec50000,0x00b1a2bc
	.long	0x9e860000,0x00d529ae
	.long	0x0e470000,0x00f8b0a1
	.long	0x7e080000,0x011c3793
	.long	0xedc90000,0x013fbe85
	.long	0x00000000,0x00000000
	.long	0x5d8a0000,0x01634578
	.long	0xbb140000,0x02c68af0
	.long	0x189e0000,0x0429d069
	.long	0x76280000,0x058d15e1
	.long	0xd3b20000,0x06f05b59
	.long	0x313c0000,0x0853a0d2
	.long	0x8ec60000,0x09b6e64a
	.long	0xec500000,0x0b1a2bc2
	.long	0x49da0000,0x0c7d713b
	.long	0x00000000,0x00000000
	.long	0xa7640000,0x0de0b6b3
	.long	0x4ec80000,0x1bc16d67
	.long	0xf62c0000,0x29a2241a
	.long	0x9d900000,0x3782dace
	.long	0x44f40000,0x45639182
	.long	0xec580000,0x53444835
	.long	0x93bc0000,0x6124fee9
	.long	0x3b200000,0x6f05b59d
	.long	0xe2840000,0x7ce66c50
	.long	0x00000000,0x00000000
	.long	0x89e80000,0x8ac72304
	.long	0x13d00000,0x158e4609
	.long	0x9db80000,0xa055690d
	.long	0x27a00000,0x2b1c8c12
	.long	0xb1880000,0xb5e3af16
	.long	0x3b700000,0x40aad21b
	.long	0xc5580000,0xcb71f51f
	.long	0x4f400000,0x56391824
	.long	0xd9280000,0xe1003b28
	.long	0x00000000,0x00000000
	.long	0x63100000,0x6bc75e2d
	.long	0xc6200000,0xd78ebc5a
	.long	0x29300000,0x43561a88
	.long	0x8c400000,0xaf1d78b5
	.long	0xef500000,0x1ae4d6e2
	.long	0x52600000,0x86ac3510
	.long	0xb5700000,0xf273933d
	.long	0x18800000,0x5e3af16b
	.long	0x7b900000,0xca024f98
	.long	0x00000000,0x00000000
	.long	0xdea00000,0x35c9adc5
	.long	0xbd400000,0x6b935b8b
	.long	0x9be00000,0xa15d0951
	.long	0x7a800000,0xd726b717
	.long	0x59200000,0x0cf064dd
	.long	0x37c00000,0x42ba12a3
	.long	0x16600000,0x7883c069
	.long	0xf5000000,0xae4d6e2e
	.long	0xd3a00000,0xe4171bf4
	.long	0x00000000,0x00000000
	.long	0xb2400000,0x19e0c9ba
	.long	0x64800000,0x33c19375
	.long	0x16c00000,0x4da25d30
	.long	0xc9000000,0x678326ea
	.long	0x7b400000,0x8163f0a5
	.long	0x2d800000,0x9b44ba60
	.long	0xdfc00000,0xb525841a
	.long	0x92000000,0xcf064dd5
	.long	0x44400000,0xe8e71790
	.long	0x00000000,0x00000000
	.long	0xf6800000,0x02c7e14a
	.long	0xed000000,0x058fc295
	.long	0xe3800000,0x0857a3e0
	.long	0xda000000,0x0b1f852b
	.long	0xd0800000,0x0de76676
	.long	0xc7000000,0x10af47c1
	.long	0xbd800000,0x1377290c
	.long	0xb4000000,0x163f0a57
	.long	0xaa800000,0x1906eba2
	.long	0x00000000,0x00000000
	.long	0xa1000000,0x1bcecced
	.long	0x42000000,0x379d99db
	.long	0xe3000000,0x536c66c8
	.long	0x84000000,0x6f3b33b6
	.long	0x25000000,0x8b0a00a4
	.long	0xc6000000,0xa6d8cd91
	.long	0x67000000,0xc2a79a7f
	.long	0x08000000,0xde76676d
	.long	0xa9000000,0xfa45345a
	.long	0x00000000,0x00000000
	.long	0x4a000000,0x16140148
	.long	0x94000000,0x2c280290
	.long	0xde000000,0x423c03d8
	.long	0x28000000,0x58500521
	.long	0x72000000,0x6e640669
	.long	0xbc000000,0x847807b1
	.long	0x06000000,0x9a8c08fa
	.long	0x50000000,0xb0a00a42
	.long	0x9a000000,0xc6b40b8a
	.long	0x00000000,0x00000000
	.long	0xe4000000,0xdcc80cd2
	.long	0xc8000000,0xb99019a5
	.long	0xac000000,0x96582678
	.long	0x90000000,0x7320334b
	.long	0x74000000,0x4fe8401e
	.long	0x58000000,0x2cb04cf1
	.long	0x3c000000,0x097859c4
	.long	0x20000000,0xe6406697
	.long	0x04000000,0xc308736a
	.long	0x00000000,0x00000000
	.long	0xe8000000,0x9fd0803c
	.long	0xd0000000,0x3fa10079
	.long	0xb8000000,0xdf7180b6
	.long	0xa0000000,0x7f4200f3
	.long	0x88000000,0x1f128130
	.long	0x70000000,0xbee3016d
	.long	0x58000000,0x5eb381aa
	.long	0x40000000,0xfe8401e7
	.long	0x28000000,0x9e548224
	.long	0x00000000,0x00000000
	.long	0x10000000,0x3e250261
	.long	0x20000000,0x7c4a04c2
	.long	0x30000000,0xba6f0723
	.long	0x40000000,0xf8940984
	.long	0x50000000,0x36b90be5
	.long	0x60000000,0x74de0e46
	.long	0x70000000,0xb30310a7
	.long	0x80000000,0xf1281308
	.long	0x90000000,0x2f4d1569
	.long	0x00000000,0x00000000
	.long	0xa0000000,0x6d7217ca
	.long	0x40000000,0xdae42f95
	.long	0xe0000000,0x4856475f
	.long	0x80000000,0xb5c85f2a
	.long	0x20000000,0x233a76f5
	.long	0xc0000000,0x90ac8ebf
	.long	0x60000000,0xfe1ea68a
	.long	0x00000000,0x6b90be55
	.long	0xa0000000,0xd902d61f
	.long	0x00000000,0x00000000
	.long	0x40000000,0x4674edea
	.long	0x80000000,0x8ce9dbd4
	.long	0xc0000000,0xd35ec9be
	.long	0x00000000,0x19d3b7a9
	.long	0x40000000,0x6048a593
	.long	0x80000000,0xa6bd937d
	.long	0xc0000000,0xed328167
	.long	0x00000000,0x33a76f52
	.long	0x40000000,0x7a1c5d3c
	.long	0x00000000,0x00000000
	.long	0x80000000,0xc0914b26
	.long	0x00000000,0x8122964d
	.long	0x80000000,0x41b3e173
	.long	0x00000000,0x02452c9a
	.long	0x80000000,0xc2d677c0
	.long	0x00000000,0x8367c2e7
	.long	0x80000000,0x43f90e0d
	.long	0x00000000,0x048a5934
	.long	0x80000000,0xc51ba45a
	.long	0x00000000,0x00000000
	.long	0x00000000,0x85acef81
	.long	0x00000000,0x0b59df02
	.long	0x00000000,0x9106ce83
	.long	0x00000000,0x16b3be04
	.long	0x00000000,0x9c60ad85
	.long	0x00000000,0x220d9d06
	.long	0x00000000,0xa7ba8c87
	.long	0x00000000,0x2d677c08
	.long	0x00000000,0xb3146b89
	.long	0x00000000,0x00000000
	.long	0x00000000,0x38c15b0a
	.long	0x00000000,0x7182b614
	.long	0x00000000,0xaa44111e
	.long	0x00000000,0xe3056c28
	.long	0x00000000,0x1bc6c732
	.long	0x00000000,0x5488223c
	.long	0x00000000,0x8d497d46
	.long	0x00000000,0xc60ad850
	.long	0x00000000,0xfecc335a
	.long	0x00000000,0x00000000
	.long	0x00000000,0x378d8e64
	.long	0x00000000,0x6f1b1cc8
	.long	0x00000000,0xa6a8ab2c
	.long	0x00000000,0xde363990
	.long	0x00000000,0x15c3c7f4
	.long	0x00000000,0x4d515658
	.long	0x00000000,0x84dee4bc
	.long	0x00000000,0xbc6c7320
	.long	0x00000000,0xf3fa0184
	.long	0x00000000,0x00000000
	.long	0x00000000,0x2b878fe8
	.long	0x00000000,0x570f1fd0
	.long	0x00000000,0x8296afb8
	.long	0x00000000,0xae1e3fa0
	.long	0x00000000,0xd9a5cf88
	.long	0x00000000,0x052d5f70
	.long	0x00000000,0x30b4ef58
	.long	0x00000000,0x5c3c7f40
	.long	0x00000000,0x87c40f28
	.long	0x00000000,0x00000000
	.long	0x00000000,0xb34b9f10
	.long	0x00000000,0x66973e20
	.long	0x00000000,0x19e2dd30
	.long	0x00000000,0xcd2e7c40
	.long	0x00000000,0x807a1b50
	.long	0x00000000,0x33c5ba60
	.long	0x00000000,0xe7115970
	.long	0x00000000,0x9a5cf880
	.long	0x00000000,0x4da89790
	.long	0x00000000,0x00000000
	.long	0x00000000,0x00f436a0
	.long	0x00000000,0x01e86d40
	.long	0x00000000,0x02dca3e0
	.long	0x00000000,0x03d0da80
	.long	0x00000000,0x04c51120
	.long	0x00000000,0x05b947c0
	.long	0x00000000,0x06ad7e60
	.long	0x00000000,0x07a1b500
	.long	0x00000000,0x0895eba0
	.long	0x00000000,0x00000000
	.long	0x00000000,0x098a2240
	.long	0x00000000,0x13144480
	.long	0x00000000,0x1c9e66c0
	.long	0x00000000,0x26288900
	.long	0x00000000,0x2fb2ab40
	.long	0x00000000,0x393ccd80
	.long	0x00000000,0x42c6efc0
	.long	0x00000000,0x4c511200
	.long	0x00000000,0x55db3440
	.long	0x00000000,0x00000000
	.long	0x00000000,0x5f655680
	.long	0x00000000,0xbecaad00
	.long	0x00000000,0x1e300380
	.long	0x00000000,0x7d955a00
	.long	0x00000000,0xdcfab080
	.long	0x00000000,0x3c600700
	.long	0x00000000,0x9bc55d80
	.long	0x00000000,0xfb2ab400
	.long	0x00000000,0x5a900a80
	.long	0x00000000,0x00000000
	.long	0x00000000,0xb9f56100
	.long	0x00000000,0x73eac200
	.long	0x00000000,0x2de02300
	.long	0x00000000,0xe7d58400
	.long	0x00000000,0xa1cae500
	.long	0x00000000,0x5bc04600
	.long	0x00000000,0x15b5a700
	.long	0x00000000,0xcfab0800
	.long	0x00000000,0x89a06900
	.long	0x00000000,0x00000000
	.long	0x00000000,0x4395ca00
	.long	0x00000000,0x872b9400
	.long	0x00000000,0xcac15e00
	.long	0x00000000,0x0e572800
	.long	0x00000000,0x51ecf200
	.long	0x00000000,0x9582bc00
	.long	0x00000000,0xd9188600
	.long	0x00000000,0x1cae5000
	.long	0x00000000,0x60441a00
	.long	0x00000000,0x00000000
	.long	0x00000000,0xa3d9e400
	.long	0x00000000,0x47b3c800
	.long	0x00000000,0xeb8dac00
	.long	0x00000000,0x8f679000
	.long	0x00000000,0x33417400
	.long	0x00000000,0xd71b5800
	.long	0x00000000,0x7af53c00
	.long	0x00000000,0x1ecf2000
	.long	0x00000000,0xc2a90400
	.long	0x00000000,0x00000000
	.long	0x00000000,0x6682e800
	.long	0x00000000,0xcd05d000
	.long	0x00000000,0x3388b800
	.long	0x00000000,0x9a0ba000
	.long	0x00000000,0x008e8800
	.long	0x00000000,0x67117000
	.long	0x00000000,0xcd945800
	.long	0x00000000,0x34174000
	.long	0x00000000,0x9a9a2800
	.long	0x00000000,0x00000000
	.long	0x00000000,0x011d1000
	.long	0x00000000,0x023a2000
	.long	0x00000000,0x03573000
	.long	0x00000000,0x04744000
	.long	0x00000000,0x05915000
	.long	0x00000000,0x06ae6000
	.long	0x00000000,0x07cb7000
	.long	0x00000000,0x08e88000
	.long	0x00000000,0x0a059000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x0b22a000
	.long	0x00000000,0x16454000
	.long	0x00000000,0x2167e000
	.long	0x00000000,0x2c8a8000
	.long	0x00000000,0x37ad2000
	.long	0x00000000,0x42cfc000
	.long	0x00000000,0x4df26000
	.long	0x00000000,0x59150000
	.long	0x00000000,0x6437a000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x6f5a4000
	.long	0x00000000,0xdeb48000
	.long	0x00000000,0x4e0ec000
	.long	0x00000000,0xbd690000
	.long	0x00000000,0x2cc34000
	.long	0x00000000,0x9c1d8000
	.long	0x00000000,0x0b77c000
	.long	0x00000000,0x7ad20000
	.long	0x00000000,0xea2c4000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x59868000
	.long	0x00000000,0xb30d0000
	.long	0x00000000,0x0c938000
	.long	0x00000000,0x661a0000
	.long	0x00000000,0xbfa08000
	.long	0x00000000,0x19270000
	.long	0x00000000,0x72ad8000
	.long	0x00000000,0xcc340000
	.long	0x00000000,0x25ba8000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x7f410000
	.long	0x00000000,0xfe820000
	.long	0x00000000,0x7dc30000
	.long	0x00000000,0xfd040000
	.long	0x00000000,0x7c450000
	.long	0x00000000,0xfb860000
	.long	0x00000000,0x7ac70000
	.long	0x00000000,0xfa080000
	.long	0x00000000,0x79490000
	.long	0x00000000,0x00000000
	.long	0x00000000,0xf88a0000
	.long	0x00000000,0xf1140000
	.long	0x00000000,0xe99e0000
	.long	0x00000000,0xe2280000
	.long	0x00000000,0xdab20000
	.long	0x00000000,0xd33c0000
	.long	0x00000000,0xcbc60000
	.long	0x00000000,0xc4500000
	.long	0x00000000,0xbcda0000
	.long	0x00000000,0x00000000
	.long	0x00000000,0xb5640000
	.long	0x00000000,0x6ac80000
	.long	0x00000000,0x202c0000
	.long	0x00000000,0xd5900000
	.long	0x00000000,0x8af40000
	.long	0x00000000,0x40580000
	.long	0x00000000,0xf5bc0000
	.long	0x00000000,0xab200000
	.long	0x00000000,0x60840000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x15e80000
	.long	0x00000000,0x2bd00000
	.long	0x00000000,0x41b80000
	.long	0x00000000,0x57a00000
	.long	0x00000000,0x6d880000
	.long	0x00000000,0x83700000
	.long	0x00000000,0x99580000
	.long	0x00000000,0xaf400000
	.long	0x00000000,0xc5280000
	.long	0x00000000,0x00000000
	.long	0x00000000,0xdb100000
	.long	0x00000000,0xb6200000
	.long	0x00000000,0x91300000
	.long	0x00000000,0x6c400000
	.long	0x00000000,0x47500000
	.long	0x00000000,0x22600000
	.long	0x00000000,0xfd700000
	.long	0x00000000,0xd8800000
	.long	0x00000000,0xb3900000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x8ea00000
	.long	0x00000000,0x1d400000
	.long	0x00000000,0xabe00000
	.long	0x00000000,0x3a800000
	.long	0x00000000,0xc9200000
	.long	0x00000000,0x57c00000
	.long	0x00000000,0xe6600000
	.long	0x00000000,0x75000000
	.long	0x00000000,0x03a00000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x92400000
	.long	0x00000000,0x24800000
	.long	0x00000000,0xb6c00000
	.long	0x00000000,0x49000000
	.long	0x00000000,0xdb400000
	.long	0x00000000,0x6d800000
	.long	0x00000000,0xffc00000
	.long	0x00000000,0x92000000
	.long	0x00000000,0x24400000
	.long	0x00000000,0x00000000
	.long	0x00000000,0xb6800000
	.long	0x00000000,0x6d000000
	.long	0x00000000,0x23800000
	.long	0x00000000,0xda000000
	.long	0x00000000,0x90800000
	.long	0x00000000,0x47000000
	.long	0x00000000,0xfd800000
	.long	0x00000000,0xb4000000
	.long	0x00000000,0x6a800000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x21000000
	.long	0x00000000,0x42000000
	.long	0x00000000,0x63000000
	.long	0x00000000,0x84000000
	.long	0x00000000,0xa5000000
	.long	0x00000000,0xc6000000
	.long	0x00000000,0xe7000000
	.long	0x00000000,0x08000000
	.long	0x00000000,0x29000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x4a000000
	.long	0x00000000,0x94000000
	.long	0x00000000,0xde000000
	.long	0x00000000,0x28000000
	.long	0x00000000,0x72000000
	.long	0x00000000,0xbc000000
	.long	0x00000000,0x06000000
	.long	0x00000000,0x50000000
	.long	0x00000000,0x9a000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0xe4000000
	.long	0x00000000,0xc8000000
	.long	0x00000000,0xac000000
	.long	0x00000000,0x90000000
	.long	0x00000000,0x74000000
	.long	0x00000000,0x58000000
	.long	0x00000000,0x3c000000
	.long	0x00000000,0x20000000
	.long	0x00000000,0x04000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0xe8000000
	.long	0x00000000,0xd0000000
	.long	0x00000000,0xb8000000
	.long	0x00000000,0xa0000000
	.long	0x00000000,0x88000000
	.long	0x00000000,0x70000000
	.long	0x00000000,0x58000000
	.long	0x00000000,0x40000000
	.long	0x00000000,0x28000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x10000000
	.long	0x00000000,0x20000000
	.long	0x00000000,0x30000000
	.long	0x00000000,0x40000000
	.long	0x00000000,0x50000000
	.long	0x00000000,0x60000000
	.long	0x00000000,0x70000000
	.long	0x00000000,0x80000000
	.long	0x00000000,0x90000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0xa0000000
	.long	0x00000000,0x40000000
	.long	0x00000000,0xe0000000
	.long	0x00000000,0x80000000
	.long	0x00000000,0x20000000
	.long	0x00000000,0xc0000000
	.long	0x00000000,0x60000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0xa0000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x40000000
	.long	0x00000000,0x80000000
	.long	0x00000000,0xc0000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x40000000
	.long	0x00000000,0x80000000
	.long	0x00000000,0xc0000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x40000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x80000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x80000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x80000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x80000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x80000000
	.type	_C_MUL_10_POW_I,@object
	.size	_C_MUL_10_POW_I,5120
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
