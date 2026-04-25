package com.evalvis.loadbalancer.balance

import jakarta.servlet.http.HttpServletRequest
import java.util.regex.Pattern

class ShardingBackendSelector(
    private val ring: ShardRing,
    private val pathPattern: String
) : BackendTargetSelector {

    private val pattern = Pattern.compile(pathPattern)

    override fun selectTarget(request: HttpServletRequest): String {
        val path = request.requestURI
        val matcher = pattern.matcher(path)
        
        return if (matcher.find() && matcher.groupCount() >= 1) {
            val key = matcher.group(1)
            ring.assign(key).getNextBackend()
        } else {
            // Fallback: use a default key or first shard
            ring.assign("default").getNextBackend()
        }
    }

    override fun selectTargets(request: HttpServletRequest): List<String> {
        val path = request.requestURI
        val method = request.method
        
        // Broadcast for POST /tables/{tableName} - only to active/available shards
        if (isBroadcast(request)) {
            return ring.getAllShards()
                .filter { !it.decommissioning }
                .map { it.getNextBackend() }
        }
        
        return listOf(selectTarget(request))
    }

    override fun selectFallbackTarget(request: HttpServletRequest): String? {
        // Only for GET requests matching the path pattern
        if (request.method != "GET") return null
        
        val path = request.requestURI
        val matcher = pattern.matcher(path)
        
        return if (matcher.find() && matcher.groupCount() >= 1) {
            val key = matcher.group(1)
            ring.assignFallback(key)?.getNextBackend()
        } else {
            null
        }
    }

    override fun isBroadcast(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        val method = request.method
        return method == "POST" && path.matches(Regex(".*/tables/[^/]+$"))
    }
}
