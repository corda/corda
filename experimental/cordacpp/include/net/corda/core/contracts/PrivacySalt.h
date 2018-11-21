////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_PRIVACYSALT_H
#define NET_CORDA_CORE_CONTRACTS_PRIVACYSALT_H

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
namespace contracts {

class PrivacySalt : public net::corda::core::utilities::OpaqueBytes {
public:
    proton::binary bytes;

    PrivacySalt() = default;

    explicit PrivacySalt(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.contracts.PrivacySalt", descriptor(), 1);
        net::corda::Parser::read_to(decoder, bytes);
    }

    virtual const std::string descriptor() { return "net.corda:1skUfBacU1AgmLX8M1z83A=="; }
};

}
}
}
}

#endif