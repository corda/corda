////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_COMMAND_H
#define NET_CORDA_CORE_CONTRACTS_COMMAND_H

#include "corda.h"
namespace java {
namespace security {
class PublicKey;
}
}
namespace net {
namespace corda {
namespace core {
namespace contracts {
class CommandData;
template <class T> class Command;
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

template <class T> class Command : public net::corda::Any {
public:
    std::list<net::corda::ptr<java::security::PublicKey>> signers;
    net::corda::ptr<T> value;

    Command() = default;

    explicit Command(proton::codec::decoder &decoder) {
        net::corda::Parser::read_to(decoder, signers);
        net::corda::Parser::read_to(decoder, value);
    }
};

}
}
}
}

net::corda::TypeRegistration Registration11("net.corda:+8JPOg3TmkKZMh1QVVm3QA==", [](proton::codec::decoder &decoder) { return new net::corda::core::contracts::Command<net::corda::Any>(decoder); }); // NOLINT(cert-err58-cpp)
net::corda::TypeRegistration Registration12("net.corda:MtyVix+F6TOq6oXfQY8+kg==", [](proton::codec::decoder &decoder) { return new net::corda::core::contracts::Command<net::corda::core::contracts::CommandData>(decoder); }); // NOLINT(cert-err58-cpp)

#endif