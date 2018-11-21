////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_STATEREF_H
#define NET_CORDA_CORE_CONTRACTS_STATEREF_H

#include "corda.h"
namespace net {
namespace corda {
namespace core {
namespace crypto {
class SecureHash;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace contracts {

class StateRef {
public:
    int32_t index;
    net::corda::ptr<net::corda::core::crypto::SecureHash> txhash;

    StateRef() = default;

    explicit StateRef(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.contracts.StateRef", descriptor(), 2);
        net::corda::Parser::read_to(decoder, index);
        net::corda::Parser::read_to(decoder, txhash);
    }

    virtual const std::string descriptor() { return "net.corda:JddPXlP6d/8aSE9L9gmcuA=="; }
};

}
}
}
}

#endif