Currently Work-In-Progress, but it does already work

rss-extender is supposed to be a super-fast configurable rss feed extender, which will put 
more meaningful content into the feed, than in the original feed.

The basic algorithm is to visit every link in the rss-feed, process the data with JSoup-selects
and replace the content of the entry in the rss-feed and return the modified feed.

Environment:
- RSSEXTENDER_PORT - Port on which the server shall start (default: 8080)
- RSSEXTENDER_APIKEY - Apikey to access the feeds (default: random uuid, printed on started)
- RSSEXTENDER_BIND - Interface to bind on (default: 0.0.0.0)

Supported feeds:
- heise
- engadget
