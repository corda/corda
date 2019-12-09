include_directories (include)
include_directories (src/reader)
include_directories (src/schema)
include_directories (src/schema/descriptors)
include_directories (src/schema/field-types)
include_directories (src/schema/described-types)

set (amqp_schema_sources
        src/schema/descriptors/AMQPDescriptor.cxx
        src/schema/descriptors/AMQPDescriptors.cxx
        src/schema/descriptors/AMQPDescriptorRegistory.cxx
        src/schema/descriptors/corda-descriptors/FieldDescriptor.cxx
        src/schema/descriptors/corda-descriptors/SchemaDescriptor.cxx
        src/schema/descriptors/corda-descriptors/ObjectDescriptor.cxx
        src/schema/descriptors/corda-descriptors/ChoiceDescriptor.cxx
        src/schema/descriptors/corda-descriptors/EnvelopeDescriptor.cxx
        src/schema/descriptors/corda-descriptors/CompositeDescriptor.cxx
        src/schema/descriptors/corda-descriptors/RestrictedDescriptor.cxx
        src/schema/field-types/Field.cxx
        src/schema/field-types/PrimitiveField.cxx
        src/schema/field-types/CompositeField.cxx
        src/schema/field-types/RestrictedField.cxx
        src/schema/field-types/ArrayField.cxx
        src/schema/described-types/Schema.cxx
        src/schema/described-types/Choice.cxx
        src/schema/described-types/Envelope.cxx
        src/schema/described-types/Composite.cxx
        src/schema/described-types/Descriptor.cxx
        src/schema/restricted-types/Restricted.cxx
        src/schema/restricted-types/List.cxx
        src/schema/restricted-types/Enum.cxx
        src/schema/restricted-types/Map.cxx
        src/schema/restricted-types/Array.cxx
        src/schema/AMQPTypeNotation.cxx
        src/schema/Descriptors.cxx
        )

set (amqp_assembler_sources
        src/assembler/CompositeFactory.cxx
     )

set (amqp_sources
        src/reader/Reader.cxx
        src/reader/PropertyReader.cxx
        src/reader/CompositeReader.cxx
        src/reader/RestrictedReader.cxx
        src/reader/property-readers/IntPropertyReader.cxx
        src/reader/property-readers/LongPropertyReader.cxx
        src/reader/property-readers/BoolPropertyReader.cxx
        src/reader/property-readers/DoublePropertyReader.cxx
        src/reader/property-readers/StringPropertyReader.cxx
        src/reader/restricted-readers/MapReader.cxx
        src/reader/restricted-readers/ListReader.cxx
        src/reader/restricted-readers/ArrayReader.cxx
        src/reader/restricted-readers/EnumReader.cxx
        src/AMQPBlob.cxx
        src/CordaBytes.cxx)

ADD_LIBRARY ( amqp ${amqp_sources} ${amqp_schema_sources} ${amqp_assembler_sources})

ADD_SUBDIRECTORY (src)
