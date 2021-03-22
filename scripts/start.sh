#!/bin/sh
cd /
find "/app" -iname "rssextender*all.jar" | xargs java -jar
