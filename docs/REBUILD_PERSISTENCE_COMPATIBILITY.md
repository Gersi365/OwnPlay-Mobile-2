# Rebuild persistence compatibility

OwnPlay-Mobile-2 rebuild preserves the existing durable Room database contract before introducing new repository behavior.

Compatibility baseline:

- database file: `ownplay-v1.db`
- Room schema version: `3`
- committed schemas `1.json`, `2.json`, `3.json` remain migration evidence
- migrations `1 -> 2` and `2 -> 3` remain supported
- no destructive migration

The v3 durable model already represents provider catalog state, category/channel personalization, custom groups, media favorites, playback progress, downloads, refresh state, Live organization mode, hierarchical OwnPlay categories, and AUTO/MANUAL Live memberships.

The fixed OwnPlay Live presentation model (`Country -> General/News/Sports/Movies/Series/Kids/Music/Documentaries`) and persistent per-channel manual placement can be implemented on this durable model without creating a schema v4 solely for taxonomy.

A later database version increase requires a real durable-data requirement plus an explicit non-destructive migration and migration tests.
