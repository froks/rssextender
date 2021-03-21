import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.lang.IllegalArgumentException

@Serializable
class Config(
    val feeds: Map<String, FeedConfig>,
)

@Serializable
data class FeedConfig(
    val url: String,
    val selectors: List<String> = emptyList(),
    val removes: List<String> = emptyList(),
)

val config = ClassLoader.getSystemResourceAsStream("feeds.yml")?.use {
    Yaml.default.decodeFromString<Config>(String(it.readAllBytes(), Charsets.UTF_8))
} ?: throw IllegalArgumentException("Config file not found")
