package com.ataiva.eden.auth.ktor

import com.ataiva.eden.auth.rbac.RbacService
import com.ataiva.eden.auth.rbac.UnauthorizedException
import com.ataiva.eden.core.models.UserContext
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.util.*

/**
 * RBAC authentication plugin for Ktor
 * Provides role-based access control for API endpoints
 */
class RbacAuthPlugin(configuration: Configuration) {
    private val rbacService = configuration.rbacService
    
    /**
     * Configuration for the RBAC authentication plugin
     */
    class Configuration {
        var rbacService: RbacService? = null
    }
    
    /**
     * Companion object for the RBAC authentication plugin
     */
    companion object Plugin : BaseApplicationPlugin<Application, Configuration, RbacAuthPlugin> {
        override val key = AttributeKey<RbacAuthPlugin>("RbacAuth")
        
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): RbacAuthPlugin {
            val configuration = Configuration().apply(configure)
            
            // Ensure RBAC service is configured
            requireNotNull(configuration.rbacService) { "RBAC service must be configured" }
            
            val plugin = RbacAuthPlugin(configuration)
            
            // Install exception handler for unauthorized access
            pipeline.install(StatusPages) {
                exception<UnauthorizedException> { call, cause ->
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Access denied", "message" to cause.message)
                    )
                }
            }
            
            return plugin
        }
    }
}

/**
 * Extension function to require a specific permission for a route
 */
fun Route.requirePermission(permission: String, rbacService: RbacService, build: Route.() -> Unit): Route {
    val authorizedRoute = createChild(AuthenticationRouteSelector(permission))
    
    authorizedRoute.install(RbacCheckPlugin) {
        validate { userContext ->
            if (rbacService.checkPermission(userContext, permission)) {
                UserContextPrincipal(userContext)
            } else {
                null
            }
        }
    }
    
    authorizedRoute.build()
    return authorizedRoute
}

/**
 * Extension function to require a specific permission for a resource
 */
fun Route.requireResourcePermission(
    permission: String,
    resourceType: String,
    resourceIdExtractor: (ApplicationCall) -> String,
    rbacService: RbacService,
    build: Route.() -> Unit
): Route {
    val authorizedRoute = createChild(AuthenticationRouteSelector("$permission:$resourceType"))
    
    authorizedRoute.install(RbacResourceCheckPlugin) {
        validate { userContext, call ->
            val resourceId = resourceIdExtractor(call)
            if (rbacService.checkPermission(userContext, permission, resourceType, resourceId)) {
                UserContextPrincipal(userContext)
            } else {
                null
            }
        }
    }
    
    authorizedRoute.build()
    return authorizedRoute
}

/**
 * RBAC check plugin for Ktor
 */
private val RbacCheckPlugin = createRouteScopedPlugin(
    name = "RbacCheckPlugin",
    createConfiguration = ::RbacCheckConfiguration
) {
    val configuration = pluginConfig
    
    on(AuthenticationChecked) { call ->
        val principal = call.principal<JWTPrincipal>()
            ?: throw UnauthorizedException("Authentication required")
        
        val userContext = principal.payload.getClaim("userContext").asString().let {
            // Parse user context from JWT claim
            // In a real implementation, this would use proper JSON deserialization
            UserContext(
                userId = principal.payload.getClaim("sub").asString(),
                username = principal.payload.getClaim("username").asString(),
                email = principal.payload.getClaim("email").asString(),
                permissions = principal.payload.getClaim("permissions").asList(String::class.java).toSet(),
                roles = principal.payload.getClaim("roles").asList(String::class.java),
                organizationId = principal.payload.getClaim("organizationId").asString()
            )
        }
        
        val validationResult = configuration.validate(userContext)
        if (validationResult == null) {
            throw UnauthorizedException("Insufficient permissions")
        }
        
        call.authentication.principal = validationResult
    }
}

/**
 * RBAC resource check plugin for Ktor
 */
private val RbacResourceCheckPlugin = createRouteScopedPlugin(
    name = "RbacResourceCheckPlugin",
    createConfiguration = ::RbacResourceCheckConfiguration
) {
    val configuration = pluginConfig
    
    on(AuthenticationChecked) { call ->
        val principal = call.principal<JWTPrincipal>()
            ?: throw UnauthorizedException("Authentication required")
        
        val userContext = principal.payload.getClaim("userContext").asString().let {
            // Parse user context from JWT claim
            // In a real implementation, this would use proper JSON deserialization
            UserContext(
                userId = principal.payload.getClaim("sub").asString(),
                username = principal.payload.getClaim("username").asString(),
                email = principal.payload.getClaim("email").asString(),
                permissions = principal.payload.getClaim("permissions").asList(String::class.java).toSet(),
                roles = principal.payload.getClaim("roles").asList(String::class.java),
                organizationId = principal.payload.getClaim("organizationId").asString()
            )
        }
        
        val validationResult = configuration.validate(userContext, call)
        if (validationResult == null) {
            throw UnauthorizedException("Insufficient permissions for this resource")
        }
        
        call.authentication.principal = validationResult
    }
}

/**
 * Configuration for the RBAC check plugin
 */
class RbacCheckConfiguration {
    var validate: (UserContext) -> UserContextPrincipal? = { null }
}

/**
 * Configuration for the RBAC resource check plugin
 */
class RbacResourceCheckConfiguration {
    var validate: (UserContext, ApplicationCall) -> UserContextPrincipal? = { _, _ -> null }
}

/**
 * User context principal for authentication
 */
data class UserContextPrincipal(val userContext: UserContext) : Principal