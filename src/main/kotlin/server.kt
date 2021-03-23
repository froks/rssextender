import ch.qos.logback.classic.util.ContextInitializer
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.statement.*
import io.ktor.response.*
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.html.*
import org.slf4j.event.Level
import java.util.*
import java.util.concurrent.TimeUnit
import ch.qos.logback.classic.LoggerContext
import org.slf4j.LoggerFactory

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
            }
        }
    }.start(wait = true)
}
