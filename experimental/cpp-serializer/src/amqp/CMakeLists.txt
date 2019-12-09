include_directories (schema/descriptors)
include_directories (schema/field-types)
include_directories (schema/described-types)
include_directories (schema)
include_directories (reader)
include_directories (.)

set (amqp_schema_sources
        schema/descriptors/AMQPDescriptor.cxx
        schema/descriptors/AMQPDescriptors.cxx
        schema/descriptors/AMQPDescriptorRegistory.cxx
        schema/descriptors/corda-descriptors/FieldDescriptor.cxx
        schema/descriptors/corda-descriptors/SchemaDescriptor.cxx
        schema/descriptors/corda-descriptors/ObjectDescriptor.cxx
        schema/descriptors/corda-descriptors/ChoiceDescriptor.cxx
        schema/descriptors/corda-descriptors/EnvelopeDescriptor.cxx
        schema/descriptors/corda-descriptors/CompositeDescriptor.cxx
        schema/descriptors/corda-descriptors/RestrictedDescriptor.cxx
        schema/field-types/Field.cxx
        schema/field-types/PrimitiveField.cxx
        schema/field-types/CompositeField.cxx
        schema/field-types/RestrictedField.cxx
        schema/field-types/ArrayField.cxx
        schema/described-types/Schema.cxx
        schema/described-types/Choice.cxx
        schema/described-types/Envelope.cxx
        schema/described-types/Composite.cxx
        schema/described-types/Descriptor.cxx
        schema/restricted-types/Restricted.cxx
        schema/restricted-types/List.cxx
        schema/restricted-types/Enum.cxx
        schema/restricted-types/Map.cxx
        schema/restricted-types/Array.cxx
        schema/AMQPTypeNotation.cxx
        schema/Descriptors.cxx
)

set (amqp_sources
        CompositeFactory.cxx
        reader/Reader.cxx
        reader/PropertyReader.cxx
        reader/CompositeReader.cxx
        reader/RestrictedReader.cxx
        reader/property-readers/IntPropertyReader.cxx
        reader/property-readers/LongPropertyReader.cxx
        reader/property-readers/BoolPropertyReader.cxx
        reader/property-readers/DoublePropertyReader.cxx
        reader/property-readers/StringPropertyReader.cxx
        reader/restricted-readers/MapReader.cxx
        reader/restricted-readers/ListReader.cxx
        reader/restricted-readers/ArrayReader.cxx
        reader/restricted-readers/EnumReader.cxx
)

ADD_LIBRARY ( amqp ${amqp_sources} ${amqp_schema_sources})

ADD_SUBDIRECTORY (test)
