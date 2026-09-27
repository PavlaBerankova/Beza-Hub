# Podcast detail header with artwork and author — design doc

Date: 2026-09-27
Status: Approved in chat, awaiting written-spec review
Branch: `feature/podcast-tags`

## Purpose

The podcast detail screen (`PodcastEpisodesScreen`) only shows the podcast
title in a plain top app bar. Like AntennaPod (reference screenshot in
chat), it should open with a header: a blurred, darkened copy of the cover
as background, the large cover on the left, and the podcast title and
author on the right.

The app doesn't store the podcast author yet, so this adds it.

Out of scope: artwork in the episode rows of this screen, AntennaPod's
info / filter / settings buttons, background sync.

## Header

- First item of the episode list, so it scrolls away with the episodes.
- Background: the cover image, `ContentScale.Crop`, `Modifier.blur(24.dp)`
  (effective on Android 12+; on older versions the image is simply not
  blurred), with a black scrim at 55% alpha on top. No image → a
  `surfaceVariant` background.
- Bottom corners rounded 24dp (top corners square).
- Content, below the top app bar: cover 112dp square, 12dp corners, left;
  to its right, vertically centred, the title (`headlineSmall`, white,
  max 2 lines, ellipsis) and, if known, the author (`bodyMedium`, white at
  80% alpha, max 1 line, ellipsis). Missing cover → `PodcastArtwork`-style
  fallback (surfaceVariant + podcast icon) at 112dp.
- Padding 16dp around, 16dp between cover and texts, 24dp at the bottom.

## Top app bar

- Stays pinned. While the header is visible (list scrolled to the first
  item) the bar is transparent, overlays the header, shows no title, and
  its icons (back, tag, refresh / progress) are white.
- Once the header has scrolled out (`firstVisibleItemIndex > 0`) the bar
  switches to the normal surface colour with the podcast title and default
  icon colours.

## Empty episode list

The header is still shown; the existing empty state ("Zatím žádné
epizody") appears below it.

## Author data

- `PodcastFeedEntity` gains `author: String? = null`; column `author TEXT`
  (nullable) via `MIGRATION_5_6`: `ALTER TABLE podcast_feeds ADD COLUMN author TEXT`.
  DB version 6.
- `ParsedFeedMeta` gains `author: String? = null`.
- `FeedParser`: `author = pickAuthor(channel.itunesChannelData?.author, channel.itunesChannelData?.owner?.name)`
  where `pickAuthor` trims both and returns the first non-blank, else null.
  (`RssChannel` itself has no author field in rssparser 6.1.8.)
- `PodcastRepository.addFeed` stores the author.
- `EpisodeRepository.syncEpisodesForFeed` also calls
  `PodcastFeedDao.updateAuthor(feed.id, author)` when the parsed author is
  non-null, so existing podcasts get their author on the next refresh
  (↻ on the detail screen or in New Episodes). A null author never erases
  a stored one.
- `PodcastFeedDao.observeById(feedId): Flow<PodcastFeedEntity?>`;
  `PodcastRepository.observeFeed(feedId)`. The detail view model observes
  the feed instead of reading it once, so title / image / author update
  after a refresh (and the tag dialog title is no longer blank when opened
  quickly).

## Testing

- JVM: `pickAuthor` (itunes author wins, falls back to owner name, trims,
  blank → null); `PodcastRepository.addFeed` stores the parsed author.
- Build: exported `6.json` has `author` on `podcast_feeds`; migration SQL
  matches it.
- Manual on the emulator: install over the v5 database (data kept);
  header shows blurred background, cover, title; after ↻ the author
  appears; scrolling turns the bar solid with the title; tag and refresh
  still work.
