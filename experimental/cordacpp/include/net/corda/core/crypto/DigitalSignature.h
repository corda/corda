////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_DIGITALSIGNATURE_H
#define NET_CORDA_CORE_CRYPTO_DIGITALSIGNATURE_H

#include "corda.h"
#include "net/corda/core/utilities/OpaqueBytes.h"

namespace net {
namespace corda {
namespace core {
namespace utilities {
class OpaqueBytes;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace crypto {

class DigitalSignature : public net::corda::core::utilities::OpaqueBytes {
public:
    proton::binary bytes;

    DigitalSignature() = default;

    explicit DigitalSignature(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.crypto.DigitalSignature", descriptor(), 1);
        net::corda::Parser::read_to(decoder, bytes);
    }

    virtual const std::string descriptor() { return "net.corda:de8amhp5gGpyflz2aiXnLg=="; }
};

}
}
}
}

#endif