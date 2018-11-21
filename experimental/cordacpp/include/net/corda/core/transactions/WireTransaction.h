////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_TRANSACTIONS_WIRETRANSACTION_H
#define NET_CORDA_CORE_TRANSACTIONS_WIRETRANSACTION_H

#include "corda.h"
#include "net/corda/core/transactions/TraversableTransaction.h"

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
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace transactions {

class WireTransaction : public net::corda::core::transactions::TraversableTransaction {
public:
    std::list<net::corda::ptr<net::corda::core::transactions::ComponentGroup>> component_groups;
    net::corda::ptr<net::corda::core::contracts::PrivacySalt> privacy_salt;

    WireTransaction() = default;

    explicit WireTransaction(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.transactions.WireTransaction", descriptor(), 2);
        net::corda::Parser::read_to(decoder, component_groups);
        net::corda::Parser::read_to(decoder, privacy_salt);
    }

    virtual const std::string descriptor() { return "net.corda:XOo5Xrn01mcVjokIlH1ekA=="; }
};

}
}
}
}

#endif