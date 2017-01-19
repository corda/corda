#include <avian/system/memory.h>
#include <avian/util/assert.h>

namespace avian {
    namespace system {
        util::Slice<uint8_t> Memory::allocate(size_t sizeInBytes, Permissions)
        {
            void* p = malloc(sizeInBytes);

            if (p == NULL) {
                return util::Slice<uint8_t>(0, 0);
            } else {
                return util::Slice<uint8_t>(static_cast<uint8_t*>(p), sizeInBytes);
            }
        }

        void Memory::free(util::Slice<uint8_t> slice)
        {
            ::free(slice.begin());
        }

    }  // namespace system
}  // namespace avian
