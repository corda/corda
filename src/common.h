#ifndef COMMON_H
#define COMMON_H

#define NO_RETURN __attribute__((noreturn))
#define UNLIKELY(v) __builtin_expect(v, 0)

#endif//COMMON_H
