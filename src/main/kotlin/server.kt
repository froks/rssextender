import ch.qos.logback.classic.LoggerContext
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.defaultheaders.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.*
import java.util.concurrent.TimeUnit

val RESPONSE_CACHE: LoadingCache<String, Deferred<OutgoingContent>> = CacheBuilder
    .newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build(object : CacheLoader<String, Deferred<OutgoingContent>>() {
        override fun load(feed: String): Deferred<OutgoingContent> {
            val scope = CoroutineScope(Dispatchers.IO)
            return scope.async { retrieve(feed) }
        }
    })

fun main() {
    val lc = LoggerFactory.getILoggerFactory() as LoggerContext
    lc.getLogger("root").level = ch.qos.logback.classic.Level.INFO

    val port = System.getenv("RSSEXTENDER_PORT")?.toInt() ?: 8080
    val host = System.getenv("RSSEXTENDER_BIND") ?: "0.0.0.0"
    val apiKey = System.getenv("RSSEXTENDER_APIKEY") ?: UUID.randomUUID().toString()

    println("RSSExtender starting on $host:$port with apikey $apiKey")

    embeddedServer(Netty, port = port, host = host) {
        install(CallLogging) {
            level = Level.INFO
        }
        install(DefaultHeaders)
        routing {
            get("/") {
                try {
                    val feed = call.request.queryParameters["feed"]
                    val apiKeyParam = call.request.queryParameters["apikey"]
                    if (apiKeyParam != apiKey) {
                        call.respond(HttpStatusCode.Unauthorized, "Not authorized to use this service")
                        return@get
                    } else if (feed.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest, "No feed given")
                        return@get
                    }
                    call.respond(RESPONSE_CACHE.get(feed).await())
                } catch (e: Exception) {
                    // logback can't handle circular references, which are thrown by coroutines
                    e.printStackTrace(System.err)
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }.start(wait = true)
}
