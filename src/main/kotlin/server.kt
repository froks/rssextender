import ch.qos.logback.classic.LoggerContext
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.*
import java.util.concurrent.TimeUnit

val RESPONSE_CACHE: LoadingCache<String, Deferred<OutgoingContent>> = CacheBuilder
    .newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build(object : CacheLoader<String, Deferred<OutgoingContent>>() {
        override fun load(feed: String): Deferred<OutgoingContent> =
            scope.async { retrieve(feed) }
    })

fun main() {
    val lc = LoggerFactory.getILoggerFactory() as LoggerContext
    lc.getLogger("root").level = ch.qos.logback.classic.Level.INFO

    val logger = LoggerFactory.getLogger("Server")
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
                    logger.error("Error while serving request", e)
                    throw e
                }
            }
        }
    }.start(wait = true)
}
