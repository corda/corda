Note, the files `elf_common.h' and `elfstructs.h' are from FreeBSD.
  git://github.com/freebsd/freebsd.git

But with slight modifications shown below:

diff --git a/sys/sys/elf_common.h b/sys/sys/elf_common.h
index 8f02ef1..7601abb 100644
--- a/sys/sys/elf_common.h
+++ b/sys/sys/elf_common.h
@@ -30,6 +30,9 @@
 #ifndef _SYS_ELF_COMMON_H_
 #define	_SYS_ELF_COMMON_H_ 1
 
+typedef uint32_t u_int32_t;
+typedef uint32_t Elf_Symndx;
+
 /*
  * ELF definitions that are independent of architecture or word size.
  */
@@ -117,9 +120,6 @@ typedef struct {
 #define	ELFOSABI_ARM		97	/* ARM */
 #define	ELFOSABI_STANDALONE	255	/* Standalone (embedded) application */
 
-#define	ELFOSABI_SYSV		ELFOSABI_NONE	/* symbol used in old spec */
-#define	ELFOSABI_MONTEREY	ELFOSABI_AIX	/* Monterey */
-
 /* e_ident */
 #define	IS_ELF(ehdr)	((ehdr).e_ident[EI_MAG0] == ELFMAG0 && \
 			 (ehdr).e_ident[EI_MAG1] == ELFMAG1 && \
@@ -242,7 +242,6 @@ typedef struct {
 #define	EM_486		6	/* Intel i486. */
 #define	EM_MIPS_RS4_BE	10	/* MIPS R4000 Big-Endian */
 #define	EM_ALPHA_STD	41	/* Digital Alpha (standard value). */
-#define	EM_ALPHA	0x9026	/* Alpha (written in the absence of an ABI) */
 
 /* Special section indexes. */
 #define	SHN_UNDEF	     0		/* Undefined, missing, irrelevant. */
@@ -441,6 +440,8 @@ typedef struct {
 #define	DT_MOVETAB	0x6ffffefe	/* move table */
 #define	DT_SYMINFO	0x6ffffeff	/* syminfo table */
 #define	DT_ADDRRNGHI	0x6ffffeff
+#define DT_ADDRTAGIDX(tag) (DT_ADDRRNGHI - (tag))  /* Reverse order! */
+#define DT_ADDRNUM      11
 
 #define	DT_VERSYM	0x6ffffff0	/* Address of versym section. */
 #define	DT_RELACOUNT	0x6ffffff9	/* number of RELATIVE relocations */
@@ -505,12 +506,9 @@ typedef struct {
 #define	STT_FILE	4	/* Source file. */
 #define	STT_COMMON	5	/* Uninitialized common block. */
 #define	STT_TLS		6	/* TLS object. */
-#define	STT_NUM		7
 #define	STT_LOOS	10	/* Reserved range for operating system */
 #define	STT_GNU_IFUNC	10
 #define	STT_HIOS	12	/*   specific semantics. */
-#define	STT_LOPROC	13	/* reserved range for processor */
-#define	STT_HIPROC	15	/*   specific semantics. */
 
 /* Symbol visibility - ELFNN_ST_VISIBILITY - st_other */
 #define	STV_DEFAULT	0x0	/* Default visibility (see binding). */
