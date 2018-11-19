////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_TRANSACTIONS_CORETRANSACTION_H
#define NET_CORDA_CORE_TRANSACTIONS_CORETRANSACTION_H

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
namespace net {
namespace corda {
namespace core {
namespace transactions {
class BaseTransaction;
}
}
}
}

// End of pre-declarations.

namespace net {
namespace corda {
namespace core {
namespace transactions {

class CoreTransaction : public net::corda::core::contracts::NamedByHash, public net::corda::core::transactions::BaseTransaction {
public:
    

    explicit CoreTransaction(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.transactions.CoreTransaction", descriptor(), 0);
        
    }

    const std::string descriptor() { return "net.corda:KC6Ngf9J67N6aGbhM+Uw0w=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif