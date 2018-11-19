////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_TRANSACTIONS_TRAVERSABLETRANSACTION_H
#define NET_CORDA_CORE_TRANSACTIONS_TRAVERSABLETRANSACTION_H

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
template <class T> class TransactionState;
class ContractState;
class TimeWindow;
class NamedByHash;
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
namespace transactions {

class TraversableTransaction : public net::corda::core::contracts::NamedByHash, public net::corda::core::transactions::CoreTransaction {
public:
    std::list<net::corda::ptr<net::corda::core::crypto::SecureHash>> attachments;
    std::list<net::corda::ptr<net::corda::core::contracts::Command<void *>>> commands;
    std::list<net::corda::ptr<net::corda::core::transactions::ComponentGroup>> component_groups;
    std::list<net::corda::ptr<net::corda::core::contracts::StateRef>> inputs;
    net::corda::ptr<net::corda::core::identity::Party> notary;
    std::list<net::corda::ptr<net::corda::core::contracts::TransactionState<net::corda::core::contracts::ContractState>>> outputs;
    std::list<net::corda::ptr<net::corda::core::contracts::StateRef>> references;
    net::corda::ptr<net::corda::core::contracts::TimeWindow> time_window;

    explicit TraversableTransaction(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.transactions.TraversableTransaction", descriptor(), 8);
        net::corda::Parser::read_to(decoder, attachments);
        net::corda::Parser::read_to(decoder, commands);
        net::corda::Parser::read_to(decoder, component_groups);
        net::corda::Parser::read_to(decoder, inputs);
        if (decoder.next_type() != proton::NULL_TYPE) net::corda::Parser::read_to(decoder, notary); else decoder.next();
        net::corda::Parser::read_to(decoder, outputs);
        net::corda::Parser::read_to(decoder, references);
        if (decoder.next_type() != proton::NULL_TYPE) net::corda::Parser::read_to(decoder, time_window); else decoder.next();
    }

    const std::string descriptor() { return "net.corda:6Vnfg6U4l+4PJqeyiZNnwQ=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif