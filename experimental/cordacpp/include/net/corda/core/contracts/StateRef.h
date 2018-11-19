////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_STATEREF_H
#define NET_CORDA_CORE_CONTRACTS_STATEREF_H

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
namespace contracts {

class StateRef {
public:
    int32_t index;
    net::corda::ptr<net::corda::core::crypto::SecureHash> txhash;

    explicit StateRef(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.contracts.StateRef", descriptor(), 2);
        net::corda::Parser::read_to(decoder, index);
        net::corda::Parser::read_to(decoder, txhash);
    }

    const std::string descriptor() { return "net.corda:FBO2qNv9g8cKtcp4IF4cjA=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif