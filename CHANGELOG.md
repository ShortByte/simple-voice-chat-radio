# Changelog

## 1.1.0

- Playlists: new `playlist <URL>` command adds a whole playlist, also from a link to a video inside it
  (`watch?v=…&list=…`), starting at that video. The queue message now shows the playlist name.
- New option `loadFullPlaylists`: `play` loads the whole playlist for such links, too
- Virtual speaker: runs without any audio output device (e.g. HeadlessMC on a Linux server). Used automatically when
  no output can be opened, or always with `virtualSpeaker`
- Servers behind a proxy (Velocity, BungeeCord): optionally creates an open voice chat group after a server switch
  (`createGroupOnServerSwitch`, `serverSwitchGroupName`)
- New options `joinCommands` and `serverSwitchCommands`: commands the bot runs after joining a server or after a
  server switch
- Fixed: a config file that is not valid UTF-8 (e.g. saved as ANSI by Windows Notepad) or has a syntax error was
  replaced with the defaults, losing owners and message patterns. Such files are now read correctly, broken ones are
  kept as `musicbot.json.broken`, and `reload` keeps the current settings

## 1.0.0

First public release.

- Streams YouTube, SoundCloud, Bandcamp, Twitch, direct links and local files (everything yt-dlp supports) into
  Simple Voice Chat
- Queue with repeat, playlists, search, shuffle, seek, volume and pause
- Owners and members, controlled via public chat, private messages or `/musicbot`
- Joins voice chat groups automatically when an owner or member invites the bot
- Library to save songs and replay them without streaming
- Optional web panel for your home network (off by default)
- In-game config screen with Mod Menu and Cloth Config (both optional)
- Downloads yt-dlp, ffmpeg and Deno automatically, no audio device needed
- Head bobbing to the beat
- Supports Minecraft 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2 and 26.3
