# Constant Music

**Forge 1.20.1 · client-side mod**

Minecraft's music normally plays one song every 10–20 minutes. Constant Music keeps a song
playing **all the time**: the last seconds of each song fade out while the first seconds of the
next one fade in, so the music flows from one track to the next.

It works in the main menu, overworld, creative, underwater, Nether biomes, the End and the credits
(whatever Minecraft would normally play there, including music added by other mods through the
usual music-selection system). Only the music *category* volume slider affects it, as usual.
It is client-only: install it on your own game, no server install needed.

## Install

Drop the jar into `.minecraft/mods` (needs Forge 1.20.1, version 47+).

## Config

`.minecraft/config/constantmusic-client.toml` (created on first launch).
Changes are picked up live; no restart needed.

| Option              | Default | What it does                                                                                  |
|---------------------|---------|-----------------------------------------------------------------------------------------------|
| `fadeEnabled`       | `true`  | Fade songs in/out and overlap them. `false` = hard cuts, songs play back to back.            |
| `fadeSeconds`       | `10.0`  | Length of the fade and of the overlap (0.5 – 120 s).                                          |
| `pauseBetweenSongs` | `false` | Leave a silent gap between songs.                                                             |
| `pauseSeconds`      | `10.0`  | Length of that gap in seconds (0 – 3600). Only used when `pauseBetweenSongs = true`.          |

When `pauseBetweenSongs` is on, songs don't overlap (the silence is the point): a song fades out,
there is silence for `pauseSeconds`, then the next song fades in. If fading is off as well, songs
just stop and start.

### The one variable (the default fade length)

`src/main/java/com/constantmusic/ConstantMusicMod.java`:

```java
public static final int DEFAULT_FADE_SECONDS = 10;
```

Change that number and rebuild. It is the default written into a *new* config file; if
`constantmusic-client.toml` already exists, edit `fadeSeconds` there (or delete the file).

## Build

Requires a JDK 17 (the first build downloads Forge and Minecraft, so give it a few minutes).

```
./gradlew build          # Windows: gradlew.bat build
```

The mod jar ends up in `build/libs/constantmusic-1.0.0.jar`.
To try it from the project: `./gradlew runClient`. For IntelliJ / Eclipse, open the folder as a
Gradle project and use the generated `runClient` configuration.

### Build on GitHub

`.github/workflows/build.yml` builds the mod on every push. Open the **Actions** tab, pick the latest
run and download `constantmusic-jar` from *Artifacts*. (`.github` is a hidden folder: if your upload
tool skips it, use *Add file → Create new file* on GitHub, type `.github/workflows/build.yml` as the
name and paste the contents.)

## How it works

* Vanilla's `MusicManager` keeps running, but anything it starts is silenced (`PlaySoundEvent`;
  only sounds that originate from `MusicManager` are blocked, so music discs and other sounds are untouched).
* `MusicController` plays songs itself using `ConstantMusicInstance`, a tickable sound whose volume
  follows an equal-power fade curve (sine in / cosine out), so two overlapping songs keep a steady
  loudness instead of dipping in the middle.
* To know *when* to start the overlap, the length of each song is read from the `.ogg` file header
  (`OggDuration`, on a background thread, cached). Resource packs that replace music work too.
  If a song's length can't be read it simply plays through, and the next song fades in.
* The same song is never picked twice in a row (when more than one is available).
* If the music situation changes in a way vanilla would interrupt for (credits, the End, joining a
  world from the menu), the current song crossfades into the new music.

## Files

```
src/main/java/com/constantmusic/
  ConstantMusicMod.java            mod entry point, DEFAULT_FADE_SECONDS
  ClientConfig.java                the four config options
  client/MusicController.java      "always have a song playing" logic: overlap, pause, situation changes
  client/ConstantMusicInstance.java  one song with fade in / fade out
  client/OggDuration.java          reads an .ogg's length from its page headers
  client/SongLengths.java          background lookup + cache of song lengths
  client/ClientEvents.java         client tick + silencing vanilla's own song starts
  client/ClientModEvents.java      clears the length cache on resource reload
```
