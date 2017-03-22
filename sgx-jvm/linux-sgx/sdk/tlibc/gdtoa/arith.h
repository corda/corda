#define IEEE_8087
#define Arith_Kind_ASL 1
//#define Bad_float_h

#ifdef __x86_64__
#define Long int
#define Intcast (int)(long)
#define Double_Align
#define X64_bit_pointers
#endif

#define INFNAN_CHECK
#define MULTIPLE_THREADS
#define NO_FENV_H
//#define USE_LOCALE
#define NO_LONG_LONG
