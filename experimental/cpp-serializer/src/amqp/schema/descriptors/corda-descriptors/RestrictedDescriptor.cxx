#include "RestrictedDescriptor.h"

#include "types.h"
#include "debug.h"

#include "amqp/schema/described-types/Choice.h"
#include "amqp/schema/restricted-types/Restricted.h"
#include "amqp/schema/descriptors/AMQPDescriptors.h"

#include <map>
#include <regex>
#include <sstream>

/******************************************************************************/

namespace {

    const std::map<std::string, std::pair<std::regex, std::string>> regexs {
            {
                "java.lang.Integer",
                std::pair { std::regex { "java.lang.Integer"}, "int"}
            },
            {
                "java.lang.Boolean",
                std::pair { std::regex { "java.lang.Boolean"}, "bool"}
            },
            {
                "java.lang.Byte",
                std::pair { std::regex { "java.lang.Byte"}, "char"}
            },
            {
                "java.lang.Short",
                std::pair { std::regex { "java.lang.Short"}, "short"}
            },
            {
                "java.lang.Character",
                std::pair { std::regex { "java.lang.Character"}, "char"}
            },
            {
                "java.lang.Float",
                std::pair { std::regex { "java.lang.Float"}, "float"}
            },
            {
                "java.lang.Long",
                std::pair { std::regex { "java.lang.Long"}, "long"}
            },
            {
                "java.lang.Double",
                std::pair { std::regex { "java.lang.Double"}, "double"}
            }
    };

}

/******************************************************************************/

namespace amqp::internal::schema::descriptors {

    std::string
    RestrictedDescriptor::makePrim (const std::string & name_) {
        std::string name { name_ };
        for (const auto & i: regexs) {
            name = std::regex_replace (name, i.second.first, i.second.second);
        }

        return name;
    }

}

/******************************************************************************/

amqp::internal::schema::descriptors::
RestrictedDescriptor::RestrictedDescriptor (
    std::string symbol_,
    int val_
) : AMQPDescriptor (std::move (symbol_)
  , val_
) {
    
}

/******************************************************************************
 *
 * Restricted types represent lists and maps
 *
 * NOTE: The Corda serialization scheme doesn't support all container classes
 * as it has the requiremnt that iteration order be deterministic for purposes
 * of signing over data.
 *
 *      name : String
 *      label : String?
 *      provides : List<String>
 *      source : String
 *      descriptor : Descriptor
 *      choices : List<Choice>
 *
 ******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::schema::descriptors::
RestrictedDescriptor::build (pn_data_t * data_) const {
    DBG ("RESTRICTED" << std::endl); // NOLINT
    validateAndNext(data_);

    proton::auto_enter ae (data_);

    auto name  = makePrim (proton::readAndNext<std::string>(data_));
    auto label = proton::readAndNext<std::string>(data_, true);

    DBG ("  name: " << name << ", label: \"" << label << "\"" << std::endl);

    std::vector<std::string> provides;
    {
        proton::auto_list_enter ae2 (data_);
        while (pn_data_next(data_)) {
            provides.push_back (proton::get_string (data_));

            DBG ("  provides: " << provides.back() << std::endl);
        }
    }

    pn_data_next (data_);

    auto source = proton::readAndNext<std::string> (data_);

    DBG ("source: " << source << std::endl);

    auto descriptor = descriptors::dispatchDescribed<schema::Descriptor> (data_);

    pn_data_next (data_);

    DBG ("choices: " << data_ << std::endl);

    std::vector<std::unique_ptr<schema::Choice>> choices;
    {
        proton::auto_list_enter ae2 (data_);
        while (pn_data_next (data_)) {
            choices.push_back (
                descriptors::dispatchDescribed<schema::Choice> (data_));

            DBG (" choice: " << choices.back()->choice() << std::endl);
        }
    }

    DBG (data_ << std::endl);

    return schema::Restricted::make (
            std::move (descriptor),
            std::move (name),
            std::move (label),
            std::move (provides),
            std::move (source),
            std::move (choices));
}

/******************************************************************************/

void
amqp::internal::schema::descriptors::
RestrictedDescriptor::read (
        pn_data_t * data_,
        std::stringstream & ss_,
        const AutoIndent & ai_
) const {
    proton::is_list (data_);
    proton::auto_enter ae (data_);
    AutoIndent ai { ai_ };

    ss_ << ai << "1] String: Name: "
        << proton::readAndNext<std::string> (data_)
        << std::endl;
    ss_ << ai << "2] String: Label: "
        << proton::readAndNext<std::string> (data_, true)
        << std::endl;
    ss_ << ai << "3] List: Provides: [ ";

    {
        proton::auto_list_enter ae2 (data_);
        while (pn_data_next(data_)) {
            ss_ << proton::get_string (data_) << " ";
        }
        ss_ << "]" << std::endl;
    }

    pn_data_next (data_);
    ss_ << ai << "4] String: Source: "
        << proton::readAndNext<std::string> (data_)
        << std::endl;

    ss_ << ai << "5] Descriptor:" << std::endl;

    AMQPDescriptorRegistory[pn_data_type(data_)]->read (
            (pn_data_t *)proton::auto_next(data_), ss_, AutoIndent { ai });
}

/******************************************************************************/
