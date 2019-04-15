package net.corda.nodeapi.internal.cordapp

import com.fasterxml.jackson.databind.module.SimpleModule

/**
 * Should be extended by CorDapps who wish to declare custom serializers.
 * Classes of this type will be autodiscovered and registered.
 */
abstract class CustomRPCSerializationJacksonModule : SimpleModule()