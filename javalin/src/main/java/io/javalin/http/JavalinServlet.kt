/*
 * Javalin - https://javalin.io
 * Copyright 2017 David Åse
 * Licensed under Apache 2.0: https://github.com/tipsy/javalin/blob/master/LICENSE
 */

package io.javalin.http

import io.javalin.core.JavalinConfig
import io.javalin.core.security.RouteRole
import io.javalin.core.util.CorsPlugin
import io.javalin.core.util.LogUtil
import io.javalin.http.HandlerType.AFTER
import io.javalin.http.HandlerType.BEFORE
import io.javalin.http.HandlerType.GET
import io.javalin.http.HandlerType.HEAD
import io.javalin.http.HandlerType.OPTIONS
import io.javalin.http.util.MethodNotAllowedUtil
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class JavalinServlet(val config: JavalinConfig) : HttpServlet() {

    val matcher = PathMatcher()
    val exceptionMapper = ExceptionMapper()
    val errorMapper = ErrorMapper()

    private val scopes = listOf(
        Scope("before") { submitTask ->
            matcher.findEntries(BEFORE, requestUri).forEach { entry ->
                submitTask {
                    handle(ctx, requestUri, entry)
                }
            }
        },
        Scope("http") { submitTask ->
            matcher.findEntries(type, requestUri).firstOrNull()?.let { entry ->
                submitTask {
                    handle(ctx, requestUri, entry)
                }
                return@Scope // return after first match
            }
            submitTask {
                if (type == HEAD && matcher.hasEntries(GET, requestUri)) { // return 200, there is a get handler
                    return@submitTask
                }
                if (type == HEAD || type == GET) { // check for static resources (will write response if found)
                    if (config.inner.resourceHandler?.handle(it.request, JavalinResponseWrapper(it.response, responseWrapperContext)) == true) return@submitTask
                    if (config.inner.singlePageHandler.handle(ctx)) return@submitTask
                }
                if (type == OPTIONS && isCorsEnabled(config)) { // CORS is enabled, so we return 200 for OPTIONS
                    return@submitTask
                }
                if (ctx.handlerType == BEFORE) { // no match, status will be 404 or 405 after this point
                    ctx.endpointHandlerPath = "No handler matched request path/method (404/405)"
                }
                val availableHandlerTypes = MethodNotAllowedUtil.findAvailableHttpHandlerTypes(matcher, requestUri)
                if (config.prefer405over404 && availableHandlerTypes.isNotEmpty()) {
                    throw MethodNotAllowedResponse(details = MethodNotAllowedUtil.getAvailableHandlerTypes(ctx, availableHandlerTypes))
                }
                throw NotFoundResponse()
            }
        },
        Scope("error", allowsErrors = true) { submitTask ->
            submitTask {
                handleError(ctx)
            }
        },
        Scope("after", allowsErrors = true) { submitTask ->
            matcher.findEntries(AFTER, requestUri).forEach { entry ->
                submitTask {
                    handle(ctx, requestUri, entry)
                }
            }
        }
    )

    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
        try {
            val context = JavalinFlowContext(
                type = HandlerType.fromServletRequest(request),
                ctx = Context(request, response, config.inner.appAttributes),
                requestUri = request.requestURI.removePrefix(request.contextPath),
                responseWrapperContext = ResponseWrapperContext(request, config)
            )

            LogUtil.setup(context.ctx, matcher, config.inner.requestLogger != null)
            context.ctx.contentType(config.defaultContentType)

            val flow = JavalinServletFlow(request, response, this, context, scopes)
            flow.start() // Start request lifecycle
        }
        catch (throwable: Throwable) {
            exceptionMapper.handleUnexpectedThrowable(response, throwable)
        }
    }

    private fun updateContext(ctx: Context, requestUri: String, handlerEntry: HandlerEntry) = ctx.apply {
        matchedPath = handlerEntry.path
        pathParamMap = handlerEntry.extractPathParams(requestUri)
        handlerType = handlerEntry.type
        if (handlerType != AFTER) endpointHandlerPath = handlerEntry.path // Idk what it does
    }

    internal fun handle(ctx: Context, requestUri: String, handlerEntry: HandlerEntry) =
        handlerEntry.handler.handle(updateContext(ctx, requestUri, handlerEntry))

    internal fun handleError(ctx: Context) =
        errorMapper.handle(ctx.status(), ctx)

    fun addHandler(handlerType: HandlerType, path: String, handler: Handler, roles: Set<RouteRole>) {
        val protectedHandler =
            if (handlerType.isHttpMethod())
                Handler { ctx -> config.inner.accessManager.manage(handler, ctx, roles) }
            else
                handler

        matcher.add(HandlerEntry(handlerType, path, config.ignoreTrailingSlashes, protectedHandler, handler))
    }

    private fun isCorsEnabled(config: JavalinConfig) =
        config.inner.plugins[CorsPlugin::class.java] != null

}
