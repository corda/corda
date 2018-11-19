////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_SECUREHASH_H
#define NET_CORDA_CORE_CRYPTO_SECUREHASH_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.
namespace net {
namespace corda {
namespace core {
namespace crypto {
class SHA256;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace utilities {
class OpaqueBytes;
}
}
}
}

// End of pre-declarations.

namespace net {
namespace corda {
namespace core {
namespace crypto {

class SecureHash : public net::corda::core::utilities::OpaqueBytes {
public:
    proton::binary bytes;
    int32_t offset;
    int32_t size;

    explicit SecureHash(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.crypto.SecureHash", descriptor(), 3);
        net::corda::Parser::read_to(decoder, bytes);
        net::corda::Parser::read_to(decoder, offset);
        net::corda::Parser::read_to(decoder, size);
    }

    const std::string descriptor() { return "net.corda:Qnqr8CCRtqq1xmzvoIliGQ=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif