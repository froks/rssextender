rss-extender is a super-fast configurable rss feed extender, which will extend a rss-feed with more content than originally included, by getting each linked article and processing the contents with definable css-selectors.

It utilizes asynchronous ktor with kotlin coroutines for performance, as well as JSoup css-selectors for selecting and removing content in the linked articles.

Environment:
- RSSEXTENDER_PORT - Port on which the server shall start (default: 8080)
- RSSEXTENDER_APIKEY - Apikey to access the feeds (default: random uuid, printed on startup)
- RSSEXTENDER_BIND - Interface to bind on (default: 0.0.0.0)

Supported feeds:
- heise
- engadget

URL:
- `http://localhost:8080/?feed=<feedname>&apikey=<secretapikey>`
