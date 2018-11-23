////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_TRANSACTIONS_TRAVERSABLETRANSACTION_H
#define NET_CORDA_CORE_TRANSACTIONS_TRAVERSABLETRANSACTION_H

#include "corda.h"
#include "net/corda/core/transactions/CoreTransaction.h"

namespace net {
namespace corda {
namespace core {
namespace transactions {
class CoreTransaction;
class TraversableTransaction;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace transactions {

class TraversableTransaction : public net::corda::core::transactions::CoreTransaction {
public:
    

    TraversableTransaction() = default;

    explicit TraversableTransaction(proton::codec::decoder &decoder) : net::corda::core::transactions::CoreTransaction(decoder) {
        
    }
};

}
}
}
}

net::corda::TypeRegistration Registration7("net.corda:7uh5OkEW1sLz08a+OOUFJg==", [](proton::codec::decoder &decoder) { return new net::corda::core::transactions::TraversableTransaction(decoder); }); // NOLINT(cert-err58-cpp)

#endif