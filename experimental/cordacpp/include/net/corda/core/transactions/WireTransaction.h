////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_TRANSACTIONS_WIRETRANSACTION_H
#define NET_CORDA_CORE_TRANSACTIONS_WIRETRANSACTION_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.
namespace net {
namespace corda {
namespace core {
namespace transactions {
class ComponentGroup;
class TraversableTransaction;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace contracts {
class PrivacySalt;
class NamedByHash;
}
}
}
}

// End of pre-declarations.

namespace net {
namespace corda {
namespace core {
namespace transactions {

class WireTransaction : public net::corda::core::contracts::NamedByHash, public net::corda::core::transactions::TraversableTransaction {
public:
    std::list<net::corda::ptr<net::corda::core::transactions::ComponentGroup>> component_groups;
    net::corda::ptr<net::corda::core::contracts::PrivacySalt> privacy_salt;

    explicit WireTransaction(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.transactions.WireTransaction", descriptor(), 2);
        net::corda::Parser::read_to(decoder, component_groups);
        net::corda::Parser::read_to(decoder, privacy_salt);
    }

    const std::string descriptor() { return "net.corda:Ii6JRtJqxq7ObI9rkFxz4w=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif