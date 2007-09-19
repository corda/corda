#include "zlib.h"

#ifdef inflateInit2
#undef inflateInit2
#define inflateInit2(strm, windowBits) \
        inflateInit2_((strm), (windowBits), ZLIB_VERSION, static_cast<int>(sizeof(z_stream)))
#endif
