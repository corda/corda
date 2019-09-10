include_directories (descriptors)
include_directories (schema)
include_directories (reader)
include_directories (.)

set (amqp_sources
        CompositeFactory.cxx
        descriptors/AMQPDescriptor.cxx
        descriptors/AMQPDescriptors.cxx
        descriptors/AMQPDescriptorRegistory.cxx
        descriptors/corda-descriptors/FieldDescriptor.cxx
        descriptors/corda-descriptors/SchemaDescriptor.cxx
        descriptors/corda-descriptors/ObjectDescriptor.cxx
        descriptors/corda-descriptors/EnvelopeDescriptor.cxx
        descriptors/corda-descriptors/CompositeDescriptor.cxx
        descriptors/corda-descriptors/RestrictedDescriptor.cxx
        schema/Schema.cxx
        schema/Field.cxx
        schema/Envelope.cxx
        schema/Composite.cxx
        schema/Descriptor.cxx
        schema/restricted-types/Restricted.cxx
        schema/restricted-types/List.cxx
        schema/AMQPTypeNotation.cxx
        reader/Reader.cxx
        reader/PropertyReader.cxx
        reader/CompositeReader.cxx
        reader/RestrictedReader.cxx
        reader/property-readers/IntPropertyReader.cxx
        reader/property-readers/LongPropertyReader.cxx
        reader/property-readers/BoolPropertyReader.cxx
        reader/property-readers/DoublePropertyReader.cxx
        reader/property-readers/StringPropertyReader.cxx
        reader/restricted-readers/ListReader.cxx
)

ADD_LIBRARY ( amqp ${amqp_sources} )

ADD_SUBDIRECTORY (test)
