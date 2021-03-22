import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.rometools.rome.feed.synd.SyndContentImpl
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.SyndFeedOutput
import com.rometools.rome.io.XmlReader
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import java.io.ByteArrayInputStream
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import kotlin.system.measureNanoTime

val XML11_PATTERN =
    Regex("[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD\\uD800-\\uDC00-\\uDBFF-\\uFFFF\\x{10000}-\\x{10FFFF}]")

val RAW_FEED_CACHE_EXPIRY: Duration = Duration.ofMinutes(5)
val ARTICLE_CACHE_EXPIRY: Duration = Duration.ofDays(7)

val client = HttpClient()

val scope = CoroutineScope(Dispatchers.IO)

val RAW_FEED_RESPONSE_CACHE: LoadingCache<String, Deferred<Pair<ByteArray, ContentType?>>> = CacheBuilder
    .newBuilder()
    .expireAfterWrite(RAW_FEED_CACHE_EXPIRY)
    .build(object : CacheLoader<String, Deferred<Pair<ByteArray, ContentType?>>>() {
        override fun load(url: String): Deferred<Pair<ByteArray, ContentType?>> {
            return scope.async {
                val response = client.get<HttpResponse>(url)
                if (response.status != HttpStatusCode.OK) {
                    throw RuntimeException("Server didn't respond with OK")
                }
                Pair(response.readBytes(), response.contentType())
            }
        }
    })

val ARTICLE_RESPONSE_CACHE: LoadingCache<ArticleIdentifier, Future<String>> = CacheBuilder
    .newBuilder()
    .expireAfterWrite(ARTICLE_CACHE_EXPIRY)
    .build(object : CacheLoader<ArticleIdentifier, Future<String>>() {
        override fun load(article: ArticleIdentifier): Future<String> {
            return scope.async<String> {
                val response = client.get<HttpResponse>(article.link)
                if (response.status != HttpStatusCode.OK) {
                    throw RuntimeException("invalid status")
                }
                var soup = Jsoup.parse(String(response.readBytes(), Charsets.UTF_8), article.link).allElements

                val selectors = config.feeds[article.feed]?.selectors ?: emptyList()

                selectors.forEach {
                    soup = soup.select(it)
                }

                val removes = config.feeds[article.feed]?.removes ?: emptyList()
                removes.forEach {
                    soup.select(it).remove()
                }

                Jsoup.clean(soup.toString(), Whitelist.basicWithImages()).replace(XML11_PATTERN, "")
            }.asCompletableFuture()
        }
    })

val FEEDMETA_CACHE: MutableMap<String, SyndFeed> = ConcurrentHashMap()

suspend fun retrieve(feed: String): OutgoingContent {
    val feedConfig = config.feeds[feed] ?: return TextContent(
        "Unknown feed $feed",
        ContentType.Text.Plain,
        HttpStatusCode.BadRequest
    )

    val response = RAW_FEED_RESPONSE_CACHE.get(feedConfig.url).await()
    val reader = runCatching { XmlReader(ByteArrayInputStream(response.first)) }
    val syndFeed = SyndFeedInput().build(reader.getOrThrow())
    val existing = FEEDMETA_CACHE.putIfAbsent(feedConfig.url, syndFeed)
    lateinit var resultFeed: SyndFeed
    if (existing?.publishedDate == null ||
        syndFeed.publishedDate == null ||
        existing.publishedDate != syndFeed.publishedDate
    ) {
        val nanos = measureNanoTime {
            syndFeed.entries
                .associateWith {
                    ARTICLE_RESPONSE_CACHE.get(ArticleIdentifier(feed, it.link))
                }
                .forEach {
                    val content = SyndContentImpl()
                    content.type = "html"
                    content.value = it.value.get()

                    it.key.description = content
                    it.key.contents = emptyList() // listOf(content)
                }
        }
        println("Getting $feed took ${nanos / 1e6} ms")

        FEEDMETA_CACHE[feedConfig.url] = syndFeed
        resultFeed = syndFeed
    } else {
        resultFeed = existing
    }

    val data = SyndFeedOutput().outputString(resultFeed)
    return ByteArrayContent(data.toByteArray(), response.second)
}

data class ArticleIdentifier(
    val feed: String,
    val link: String,
)

suspend fun main() {
    println(String((retrieve("heise") as ByteArrayContent).bytes()))
}
