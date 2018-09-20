package net.corda.tools.error.codes.server.commons.web.vertx

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

abstract class VertxEndpoint {

    protected operator fun JsonObject.set(key: String, value: Any?) {

        put(key, value)
    }

    protected operator fun JsonArray.plus(value: Any?): JsonArray = add(value)

    protected fun HttpServerResponse.end(json: JsonObject) = putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON).end(json.toBuffer())

    protected fun HttpServerResponse.end(json: JsonArray) = putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON).end(json.toBuffer())
}