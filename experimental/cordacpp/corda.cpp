#include "corda.h"
#include <proton/codec/decoder.hpp>

namespace net {
namespace corda {

// TODO: Handle back-references
// TODO: Allow serialization, not just deserialization
// TODO: Support lists, maps
// TODO: Testing with other types

using namespace std;

string Parser::dump(bool resolve_descriptors) {
    const string amqp_bits = check_corda_amqp();
    // First pass: read the schema to build a map of descriptor->type names.
    map<proton::symbol, string> schema_mappings;
    if (resolve_descriptors) {
        proton::value v;
        proton::codec::decoder decoder(v);
        decoder.decode(amqp_bits);
        // Descend into the envelope.
        if (decoder.next_type() != proton::DESCRIBED)
            throw invalid_argument(msg() << "Did not find a composite type at the top level, got " << decoder.next_type());
        proton::codec::start s;
        decoder >> s;
        unsigned long descriptor_id;
        decoder >> descriptor_id;
        SchemaDescriptor id = corda_schema_descriptor_id(descriptor_id);
        if (id != ENVELOPE)
            throw invalid_argument(msg() << "Expected an envelope but got " << id);
        decoder >> s;
        decoder.next();
        // Descend into the schema.
        decoder >> s;
        decoder >> descriptor_id;
        id = corda_schema_descriptor_id(descriptor_id);
        if (id != SCHEMA)
            throw invalid_argument(msg() << "Expected a schema but got " << id);
        // Iterate over each element of the schema.
        decoder >> s;
        decoder >> s;
        size_t num_schema_elems = s.size;
        ss << "There are " << num_schema_elems << " schema elements defined" << endl;
        for (int i = 0; i < num_schema_elems; ++i) {
            // Enter the schema element.
            decoder >> s;
            decoder >> descriptor_id;
            id = corda_schema_descriptor_id(descriptor_id);
            if (id == COMPOSITE_TYPE || id == RESTRICTED_TYPE) {
                decoder >> s;
                string name;
                decoder >> name;
                decoder.next(); // Label
                decoder.next(); // Provides
                if (id == RESTRICTED_TYPE) {
                    decoder.next();  // Source type.
                }
                decoder >> s;
                decoder >> descriptor_id;
                id = corda_schema_descriptor_id(descriptor_id);
                if (id != OBJECT_DESCRIPTOR)
                    throw invalid_argument(msg() << "Expected an object descriptor but got " << id);
                decoder >> s;
                proton::symbol symbol;
                decoder >> symbol;
                decoder >> proton::codec::finish();  // Exit object descriptor list.
                decoder >> proton::codec::finish();  // Exit object descriptor composite type.
                decoder >> proton::codec::finish();  // Exit composite type list.
                schema_mappings[symbol] = name;
            }
            decoder >> proton::codec::finish();
        }
    }

    // Second pass: now dump everything, using the name mappings we got from the schema.
    {
        proton::value v;
        proton::codec::decoder decoder(v);
        decoder.decode(amqp_bits);
        indent_ = 0;
        ss.clear();
        dump_process(decoder, schema_mappings);
    }
    return ss.str();
}

void Parser::dump_process(proton::codec::decoder &decoder, const map<proton::symbol, string> &schema_mappings, bool need_indent, bool need_newline) {
    proton::type_id type = decoder.next_type();
    proton::codec::start s;
    if (need_indent) ss << indent();

    switch (type) {
        case proton::ARRAY:
        case proton::LIST:
            decoder >> s;  // Enter list
            if (s.size == 0) {
                ss << "[]" << endl;
            } else {
                ss << "[" << endl;
                right();
                for (size_t i = 0; i < s.size; ++i) {
                    ss << indent();
                    string number = to_string(i) + ". ";
                    ss << number;
                    indent_ += number.size();
                    dump_process(decoder, schema_mappings, false);
                    indent_ -= number.size();
                }
                left();
                ss << indent() << "]" << endl;
            }
            decoder >> proton::codec::finish();   // Leave list.
            break;
        case proton::MAP:
            decoder >> s;
            ss << "{" << endl;
            right();
            for (size_t i = 0; i < s.size / 2; ++i) {
                dump_process(decoder, schema_mappings, true, false);  // Key
                ss << " -> ";
                dump_process(decoder, schema_mappings, false, false);  // Value
            }
            left();
            ss << indent() << "}" << endl;
            decoder >> proton::codec::finish();
            break;
        case proton::DESCRIBED: {
            decoder >> s;  // Enter substructure
            string name;
            if (decoder.next_type() == proton::SYMBOL) {
                proton::symbol symbol;
                decoder >> symbol;
                if (schema_mappings.count(symbol))
                    name = schema_mappings.at(symbol);
                else
                    name = symbol;
            } else if (decoder.next_type() == proton::ULONG) {
                unsigned long descriptor_id;
                decoder >> descriptor_id;
                SchemaDescriptor id = corda_schema_descriptor_id(descriptor_id);
                switch (id) {
                    case UNKNOWN:
                        name = "non-corda-descriptor-ulong";
                        break;
                        // No meaning for zero.
                    case ENVELOPE:
                        name = "envelope";
                        break;
                    case SCHEMA:
                        name = "schema";
                        break;
                    case OBJECT_DESCRIPTOR:
                        name = "object descriptor";
                        break;
                    case FIELD:
                        name = "field";
                        break;
                    case COMPOSITE_TYPE:
                        name = "composite type";
                        break;
                    case RESTRICTED_TYPE:
                        name = "restricted type";
                        break;
                    case CHOICE:
                        name = "choice";
                        break;
                    case REFERENCED_OBJECT:
                        name = "referenced object";
                        break;
                    case TRANSFORM_SCHEMA:
                        name = "transform schema";
                        break;
                    case TRANSFORM_ELEMENT:
                        name = "transform element";
                        break;
                    case TRANSFORM_ELEMENT_KEY:
                        name = "transform element key";
                        break;
                    default:
                        name = to_string(id);
                }
            } else {
                name = "<reserved descriptor type?>";
            }

            ss << name << " ";
            dump_process(decoder, schema_mappings, false);
            decoder >> proton::codec::finish();   // Leave substructure
            break;
        }
        default:
            dump_scalar(decoder, type);
            if (need_newline) ss << endl;
    }
}

void Parser::dump_scalar(proton::codec::decoder &decoder, const proton::type_id &type) {
    proton::value v2;
    decoder >> v2;
    if (v2.type() == proton::BINARY) {
        proton::binary bin;
        proton::get(v2, bin);
        ss << "<binary of " << bin.size() << " bytes>";
    } else if (v2.type() == proton::STRING) {
        ss << "\"" << v2 << "\"";
    } else if (v2.type() == proton::NULL_TYPE) {
        ss << "NULL";
    } else if (v2.type() == proton::BOOLEAN) {
        if (v2 == true)
            ss << "true";
        else
            ss << "false";
    } else {
        ss << v2 << ": " << type;
    }
}

std::string Parser::check_corda_amqp() {
    const string &magic = bytes_.substr(0, 7);
    if (magic[0] != 'c' ||
        magic[1] != 'o' ||
        magic[2] != 'r' ||
        magic[3] != 'd' ||
        magic[4] != 'a' ||
        magic[5] != '\1' ||
        magic[6] != '\0' ||
        magic[7] != '\0') {
        throw invalid_argument("Bad magic or version");
    }
    return bytes_.substr(8);
}

proton::codec::decoder Parser::prepare_decoder() {
    const std::string amqp_bits = check_corda_amqp();
    proton::value v;
    proton::codec::decoder decoder(v);
    decoder.decode(amqp_bits);

    // Check the envelope. These reads will throw if the stream isn't in the right format.
    proton::codec::start start;
    decoder >> start;
    unsigned long desc = 0;
    decoder >> desc;
    if (corda_schema_descriptor_id(desc) != ENVELOPE)
        throw std::invalid_argument("Message does not start with an envelope");
    decoder >> start;
    if (start.size != 2 && start.size != 3)
        throw std::invalid_argument("Envelope is the wrong size");
    return decoder;
}

CompositeTypeGuard::CompositeTypeGuard(::proton::codec::decoder &decoder, const char *name, const ::proton::symbol &expected, int num_fields) : decoder(decoder),  sym(expected) {
    if (decoder.next_type() != proton::DESCRIBED)
        throw std::invalid_argument(msg() << "Expected a described element, but got " << decoder.next_type() << " decoding " << name);
    proton::codec::start start;
    decoder >> start;
    proton::symbol s;
    decoder >> s;
    if (s != expected)
        throw std::invalid_argument(msg() << "Expected descriptor " << expected << " but got " << s << " for " << name);
    if (num_fields > 0) {
        pop_second = true;
        decoder >> block;
        if (block.size != num_fields)
            throw std::invalid_argument(msg() << "Expected " << num_fields << " fields but got " << block.size << " for " << name);
    }
}

CompositeTypeGuard::~CompositeTypeGuard()  {
    decoder >> proton::codec::finish();
    if (pop_second) {
        // Composite types have two levels of nesting, the one that contains the "description, thing" pair, and
        // then the list inside "thing", so we have to pop up twice here.
        decoder >> proton::codec::finish();
    }
}

}
}