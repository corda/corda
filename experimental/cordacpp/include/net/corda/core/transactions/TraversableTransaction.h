////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_TRANSACTIONS_TRAVERSABLETRANSACTION_H
#define NET_CORDA_CORE_TRANSACTIONS_TRAVERSABLETRANSACTION_H

#include "corda.h"
#include "net/corda/core/transactions/CoreTransaction.h"

namespace net {
namespace corda {
namespace core {
namespace crypto {
class SecureHash;
}
}
}
}
namespace java {
namespace lang {
class Object;
}
}
namespace net {
namespace corda {
namespace core {
namespace contracts {
template <class T> class Command;
class StateRef;
class ContractState;
template <class T> class TransactionState;
class TimeWindow;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace transactions {
class ComponentGroup;
class CoreTransaction;
class TraversableTransaction;
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
namespace net {
namespace corda {
namespace core {
namespace transactions {

class TraversableTransaction : public net::corda::core::transactions::CoreTransaction {
public:
    std::vector<net::corda::ptr<net::corda::core::crypto::SecureHash>> attachments;
    std::vector<net::corda::ptr<net::corda::core::contracts::Command<net::corda::Any>>> commands;
    std::vector<net::corda::ptr<net::corda::core::transactions::ComponentGroup>> component_groups;
    std::vector<net::corda::ptr<net::corda::core::contracts::StateRef>> inputs;
    net::corda::ptr<net::corda::core::identity::Party> notary;
    std::vector<net::corda::ptr<net::corda::core::contracts::TransactionState<net::corda::core::contracts::ContractState>>> outputs;
    std::vector<net::corda::ptr<net::corda::core::contracts::StateRef>> references;
    net::corda::ptr<net::corda::core::contracts::TimeWindow> time_window;

    TraversableTransaction() = default;

    explicit TraversableTransaction(proton::codec::decoder &decoder) : net::corda::core::transactions::CoreTransaction(decoder) {
        net::corda::Parser::read_to(decoder, attachments);
        net::corda::Parser::read_to(decoder, commands);
        net::corda::Parser::read_to(decoder, component_groups);
        net::corda::Parser::read_to(decoder, inputs);
        if (decoder.next_type() != proton::NULL_TYPE) net::corda::Parser::read_to(decoder, notary); else decoder.next();
        net::corda::Parser::read_to(decoder, outputs);
        net::corda::Parser::read_to(decoder, references);
        if (decoder.next_type() != proton::NULL_TYPE) net::corda::Parser::read_to(decoder, time_window); else decoder.next();
    }
};

}
}
}
}

net::corda::TypeRegistration Registration4("net.corda:7uh5OkEW1sLz08a+OOUFJg==", [](proton::codec::decoder &decoder) { return new net::corda::core::transactions::TraversableTransaction(decoder); }); // NOLINT(cert-err58-cpp)

#endif