////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_TRANSACTIONS_BASETRANSACTION_H
#define NET_CORDA_CORE_TRANSACTIONS_BASETRANSACTION_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.
namespace net {
namespace corda {
namespace core {
namespace contracts {
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

class BaseTransaction : public net::corda::core::contracts::NamedByHash {
public:
    

    explicit BaseTransaction(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.transactions.BaseTransaction", descriptor(), 0);
        
    }

    const std::string descriptor() { return "net.corda:Snt0KsHpWNVy4PBKJWzqKg=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif