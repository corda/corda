////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_PRIVACYSALT_H
#define NET_CORDA_CORE_CONTRACTS_PRIVACYSALT_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.
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
namespace contracts {

class PrivacySalt : public net::corda::core::utilities::OpaqueBytes {
public:
    proton::binary bytes;

    explicit PrivacySalt(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.contracts.PrivacySalt", descriptor(), 1);
        net::corda::Parser::read_to(decoder, bytes);
    }

    const std::string descriptor() { return "net.corda:AqYCY2sYI/LR/zYyZLjkwg=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif