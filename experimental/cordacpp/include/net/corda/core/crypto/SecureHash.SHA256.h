////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_SECUREHASH_SHA256_H
#define NET_CORDA_CORE_CRYPTO_SECUREHASH_SHA256_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.
namespace net {
namespace corda {
namespace core {
namespace crypto {
class SecureHash;
}
}
}
}

// End of pre-declarations.

namespace net {
namespace corda {
namespace core {
namespace crypto {

class SecureHash$SHA256 : public net::corda::core::crypto::SecureHash {
public:
    proton::binary bytes;

    explicit SecureHash$SHA256(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.crypto.SecureHash$SHA256", descriptor(), 1);
        net::corda::Parser::read_to(decoder, bytes);
    }

    const std::string descriptor() { return "net.corda:xfZOo5Q9bZCROsc7aTCTEQ=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif