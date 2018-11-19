////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_TRANSACTIONSTATE_H
#define NET_CORDA_CORE_CONTRACTS_TRANSACTIONSTATE_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.
namespace net {
namespace corda {
namespace core {
namespace contracts {
class AttachmentConstraint;
class ContractState;
template <class T> class TransactionState;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace identity {
class Party;
}
}
}
}

// End of pre-declarations.

namespace net {
namespace corda {
namespace core {
namespace contracts {

template <class T> class TransactionState {
public:
    net::corda::ptr<net::corda::core::contracts::AttachmentConstraint> constraint;
    std::string contract;
    net::corda::ptr<T> data;
    int32_t encumbrance;
    net::corda::ptr<net::corda::core::identity::Party> notary;

    explicit TransactionState(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "net.corda.core.contracts.TransactionState<net.corda.core.contracts.ContractState>", descriptor(), 5);
        net::corda::Parser::read_to(decoder, constraint);
        net::corda::Parser::read_to(decoder, contract);
        net::corda::Parser::read_to(decoder, data);
        if (decoder.next_type() != proton::NULL_TYPE) net::corda::Parser::read_to(decoder, encumbrance); else decoder.next();
        net::corda::Parser::read_to(decoder, notary);
    }

    const std::string descriptor();
};

}
}
}
}

// Template specializations of the descriptor() method.
template<> const std::string net::corda::core::contracts::TransactionState<net::corda::core::contracts::ContractState>::descriptor() { return "net.corda:EXC6szFsBMi53/1So8maDg=="; }
// End specializations.

#endif