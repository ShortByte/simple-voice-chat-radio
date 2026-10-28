# Voice Chat Music Bot

A client-side Fabric mod that turns a Minecraft account into a **music bot for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)**.
The bot streams YouTube, SoundCloud, Bandcamp, Twitch, direct links, local files and everything else
[yt-dlp](https://github.com/yt-dlp/yt-dlp) supports straight into its voice chat microphone. You control it through
chat commands, private messages or a web panel in your browser.

**Features**

- Queue with repeat (off / track / queue), playlists, search, shuffle, seek, volume and pause
- Two permission levels: **owners** (full control) and **members** (music commands)
- Joins voice chat groups automatically when an owner or member invites it
- **Library:** download songs once and replay them later without streaming
- **Web panel** to control the bot from any device in your home network
- Downloads yt-dlp, ffmpeg and Deno by itself, and needs no audio device (works over Remote Desktop and on headless PCs)
- Nods its head to the beat of the music

## How it works

The mod runs on a **second Minecraft client** (the bot account) that joins the server like a normal player.
Everything the bot plays is mixed into its Simple Voice Chat microphone, so everyone near the bot (or in its voice
chat group) hears the music. Nothing has to be installed on the server apart from Simple Voice Chat itself.

> **Before you start:** a bot needs its own Minecraft account, and some servers do not allow alt accounts or bots.
> Check the server rules or ask the server owner first.

## Requirements

On the bot's client:

- **Minecraft** – one of the supported versions below
- **[Fabric Loader](https://fabricmc.net/use/)** 0.19.5 or newer
- **[Fabric API](https://modrinth.com/mod/fabric-api)**
- **[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)** 2.6 or newer (Fabric)
- **Java** 21 (Minecraft 1.21.x) or 25 (Minecraft 26.x) – the Minecraft launcher already includes the right one

Optional, for an in-game config screen: **[Mod Menu](https://modrinth.com/mod/modmenu)** and
**[Cloth Config](https://modrinth.com/mod/cloth-config)**.

On the server: **Simple Voice Chat**, as usual.

### Supported Minecraft versions

Download the jar that matches your Minecraft version:

| Jar | Minecraft |
|---|---|
| `voicechat-musicbot-<version>+1.21.1.jar` | 1.21, 1.21.1 |
| `voicechat-musicbot-<version>+1.21.4.jar` | 1.21.4 |
| `voicechat-musicbot-<version>+1.21.5.jar` | 1.21.5 |
| `voicechat-musicbot-<version>+1.21.8.jar` | 1.21.8 |
| `voicechat-musicbot-<version>+1.21.10.jar` | 1.21.10 |
| `voicechat-musicbot-<version>+1.21.11.jar` | 1.21.11 |
| `voicechat-musicbot-<version>+26.1.2.jar` | 26.1 – 26.1.2 |
| `voicechat-musicbot-<version>+26.2.jar` | 26.2 |
| `voicechat-musicbot-<version>+26.3.jar` | 26.3 |

These are exactly the versions Simple Voice Chat publishes Fabric builds for. The mod was built and tested against
Simple Voice Chat **2.6.24**.

### External tools (downloaded automatically)

The bot uses **yt-dlp** to find and download audio, **ffmpeg** to decode it and **Deno** as JavaScript runtime
for yt-dlp (needed for full YouTube support). On first start the mod looks for them in its own folder and on your
system `PATH`. Anything missing is downloaded from the official GitHub releases into
`<game directory>/musicbot/bin/` and verified with its SHA-256 checksum. This is a one-time download of
about 250 MB (most of it ffmpeg). Progress is shown in chat and with `/musicbot status`.

| System | yt-dlp | ffmpeg | Deno |
|---|---|---|---|
| Windows x64 / ARM64 | ✔ | ✔ | ✔ |
| Linux x64 / ARM64 | ✔ | ✔ (extracted with `tar`) | ✔ |
| macOS | ✔ | ✘ – install it with `brew install ffmpeg` | ✔ |

Tools already installed on your `PATH` are used first. You can also set fixed paths in the config.
A yt-dlp downloaded by the mod updates itself on every start (`yt-dlp -U`), because YouTube changes often.

## Installation

1. Install Fabric Loader for your Minecraft version on the bot's client.
2. Put **Fabric API**, **Simple Voice Chat** and the matching **Voice Chat Music Bot** jar into the `mods` folder.
3. Start the game once. The mod creates `config/musicbot.json` and starts downloading the tools.

## Quick start

1. **Make yourself an owner.** On the bot's client, type:
   ```
   /musicbot owner add YourName
   ```
   (or add your name or UUID to `owners` in `config/musicbot.json`). From now on you can control the bot from your
   own account.
2. **Enable voice chat on the bot.** Open the bot's Simple Voice Chat settings and make sure voice chat is not
   disabled. The activation type (push to talk or voice) does not matter.
3. **Join the server** with the bot and with your own account.
4. **Play something.** In chat, type `!play never gonna give you up`, or send it privately: `/msg BotName play <link>`.
5. **Optional: invite the bot into your voice chat group** with `/voicechat invite BotName`. It joins automatically,
   and the whole group hears the music regardless of distance.
6. **Optional: enable the web panel** (see [Web panel](#web-panel)). It is off by default.

To let friends control the music as well: `/msg BotName member add FriendName`.

## Commands

Commands are accepted in three ways:

- In **public chat** with the prefix: `!play …`
- As a **private message** without prefix: `/msg BotName play …`
- On the **bot's own client**: `/musicbot <command>`

### Permissions

| Role | Can use |
|---|---|
| **Owner** | Everything, also via private message: manage members and owners, delete library entries, `web`, `update`, `reload`, `status retry`, play local files |
| **Member** | All normal music commands (see below), including group invites and downloads (can be turned off with `membersCanDownload`), but no management and no local files |
| Web panel | Same as an owner (protected by a password) |
| Everyone else | Ignored |

The last owner cannot be removed via chat (so you cannot lock yourself out). This is only possible directly on the
bot's client.

> **Note:** members have the same playback controls as owners. They can use `!stop` or `!clear` and empty your queue.
> Volume and head bobbing changes are saved to the config. Only add people you trust with the controls.

### Music commands (owners and members)

| Command | Aliases | Description |
|---|---|---|
| `play <URL\|search>` | `p`, `add` | Add a track or playlist to the queue (starts playing if nothing is playing) |
| `playnext <…>` | `pn`, `next` | Add to the front of the queue |
| `playnow <…>` | `pnow` | Play immediately (skips the current track) |
| `playlist <URL>` | `plist`, `pll` | Add a whole playlist, also from a link to a video inside it (starts at that video) |
| `skip [n]` | `s`, `fs` | Skip the current track (and n-1 more) |
| `stop` | | Stop and clear the queue |
| `pause` / `resume` | `unpause`, `continue` | Pause / resume |
| `queue [page]` | `q`, `list` | Show the queue |
| `np` | `nowplaying`, `now`, `current` | Show the current track and position |
| `remove <no.>` | `rm`, `delete` | Remove a track from the queue |
| `move <from> <to>` | `mv` | Move a track in the queue |
| `clear` | | Clear the queue (the current track keeps playing) |
| `shuffle` | | Shuffle the queue |
| `repeat [off\|one\|all]` | `loop`, `r` | Set the repeat mode; without argument it cycles through the modes |
| `volume <0-200>` | `vol`, `v` | Volume in percent |
| `seek <mm:ss>` | `jump` | Jump to a position in the current track |
| `bob [on\|off\|1-45]` | `headbob`, `nod` | Toggle head bobbing, or set its strength in degrees |
| `join <group> [password]` | | Join a voice chat group by name (without argument: shows how to invite the bot) |
| `leave` | `leavegroup` | Leave the voice chat group |
| `download [URL\|search]` | `dl`, `save` | Save to the library (playlists work too; without argument: the current track)* |
| `library [page\|search]` | `lib`, `saved` | List or search saved tracks |
| `playlib <no.\|1 4 7\|2-6\|all\|search>` | `pl`, `lp` | Add tracks from the library to the queue |
| `status` | `deps`, `setup` | State of the tools (yt-dlp, ffmpeg, Deno) or download progress |
| `help` | `h` | Show help (owners also see their commands) |

\* Members can only download if `membersCanDownload` is on (default). The `maxDownloadMinutes` limit applies to them too.

### Owner commands

| Command | Aliases | Description |
|---|---|---|
| `member add/remove/list [name]` | `members` | Manage members |
| `owner add/remove/list [name]` | `owners` | Manage owners |
| `libremove <no.>` | `librm`, `unsave` | Delete a track from the library (including the file) |
| `update` | | Update yt-dlp (helps when YouTube stops working) |
| `web` | `panel` | Show the web panel address (the password is only shown on the bot's client) |
| `reload` | | Reload the config |
| `status retry` | | Check for / download the tools again |

**Search:** text without a URL is searched on YouTube. Use `sc:<search>` for SoundCloud and `yt:<search>` for YouTube.

**Local files** (owners only): use the absolute path, e.g. `!play "C:\Music\song.mp3"`.

## Web panel

The bot can start a small web panel (default port **8095**) that is reachable from every device in your home network,
e.g. `http://192.168.1.50:8095`. The bot shows the exact address in chat when it joins a server, and with `/musicbot web`.

- **Enable it:** the panel is **off by default**, because it gives owner rights to anyone with the password.
  Set `webPanel` to `true` in the config screen or in `config/musicbot.json`, then restart the game.
- **Password:** generated randomly on first start and stored in `config/musicbot.json` (`webPanelPassword`).
  `/musicbot web` on the bot shows it. It is never sent through chat.
- **Features:** now playing with a progress bar (click to seek), pause / skip / stop / shuffle / repeat / head bob,
  volume slider, play / up next / play now / save, the queue (remove, move to top), the library (search, play,
  delete, add all), a log of all bot replies and a free command line.
- The panel has **owner rights**. Only share the password with people you would make an owner.
- **Windows firewall:** on first start, Windows asks whether Java may communicate on networks. Allow
  *private networks*. If the prompt did not appear, allow incoming TCP connections on port 8095.
- To make it reachable only on the bot's PC, set `webPanelBind` to `127.0.0.1`. To turn it off again, set `webPanel` to `false`.
- The connection is plain, unencrypted HTTP. It is meant for your home network. **Do not** expose it to the
  internet through port forwarding.

## Voice chat groups

When an owner or member invites the bot with `/voicechat invite BotName`, it accepts the invite automatically,
also for password-protected groups. Invites from anyone else are ignored. The inviting player is identified by their
UUID, not only by their name. You can also use `!join <group> [password]`, and `!leave` to leave the group.

### Servers behind a proxy (Velocity, BungeeCord)

Voice chat groups only exist on one backend server, so the bot loses its group when it is moved to another server.
With `createGroupOnServerSwitch` set to `true`, the bot creates an open group named `serverSwitchGroupName` after every
server switch. Players on the new server can join it from the group menu without a password, and players nearby hear
the music as well. The first server the bot joins is not affected. The server must allow groups, and the bot needs
the permission to create them.

The bot can also run commands automatically, one per entry and with or without a leading slash:

- `joinCommands`: after joining a server, e.g. `["server music"]` to move straight to a backend server.
- `serverSwitchCommands`: after every server switch, e.g. `["spawn"]`.

The commands are sent one after another with the delay from `replyDelayTicks`, so the server does not kick the bot
for spamming. They run with the permissions of the bot account.

## Playlists

Everything yt-dlp can read as a playlist works: YouTube and YouTube Music playlists, albums and mixes, SoundCloud
sets, and more. At most `maxPlaylistItems` tracks are loaded per playlist.

```
!play https://www.youtube.com/playlist?list=...           (playlist link: adds the whole playlist)
!play https://www.youtube.com/watch?v=...&list=...        (video inside a playlist: only this video)
!playlist https://www.youtube.com/watch?v=...&list=...    (the playlist, starting at this video)
!shuffle                                                  (shuffle the queue afterwards)
```

With `loadFullPlaylists` on, `play` treats links to a video inside a playlist like `playlist`. YouTube mixes
(`list=RD…`) are endless, so they are cut off at `maxPlaylistItems` as well.

## Library

`!download <URL|search>` saves tracks (audio only) to `<game directory>/musicbot/library/`, with an index in
`library.json`. Saved tracks start instantly and do not depend on a stable stream:

```
!download https://www.youtube.com/playlist?list=...   (save a whole playlist)
!library                (list, page 1)
!library daft punk      (search)
!playlib 3              (add track #3 to the queue)
!playlib 1-10           (tracks 1 to 10)
!playlib all            (everything, then e.g. !shuffle)
```

When someone requests a link with `!play` that is already in the library, the bot plays the local file instead
(`useLibraryCache`). Live streams and tracks longer than `maxDownloadMinutes` are not saved.

## Configuration

With **Mod Menu** and **Cloth Config** installed, every option can be changed in game: open *Mods*, select
*Voice Chat Music Bot* and click the config button. Changes apply as soon as you save, except options marked with
"requires restart".

All options are stored in `config/musicbot.json`. If you edit the file by hand, run `/musicbot reload`
(or `!reload` as owner) afterwards.

| Option | Default | Description |
|---|---|---|
| `owners` | `[]` | Names or UUIDs with full control |
| `members` | `[]` | Names or UUIDs that may use the normal music commands |
| `commandPrefix` | `!` | Prefix for chat commands |
| `acceptPublicChat` / `acceptWhispers` | `true` | Accept commands from public chat / private messages |
| `replyMode` | `WHISPER` | How the bot replies: `WHISPER`, `CHAT`, `LOCAL` (only the bot sees it) or `NONE` |
| `whisperCommand` | `msg {player} {message}` | Command for private replies (e.g. `tell …` on servers with chat plugins) |
| `replyDelayTicks` | `25` | Minimum delay between bot messages (prevents spam kicks) |
| `announceTracks` | `true` | Announce "▶ title" when a song starts |
| `autoDownloadDependencies` | `true` | Download missing tools automatically |
| `autoUpdateYtDlp` | `true` | Update the mod's own yt-dlp on start |
| `useDeno` | `true` | Use Deno as JavaScript runtime for yt-dlp (full YouTube support) |
| `ytDlpPath` / `ffmpegPath` / `denoPath` | `yt-dlp` / `ffmpeg` / `deno` | Default = automatic; otherwise a fixed path (nothing is downloaded then) |
| `ytDlpExtraArgs` | `[]` | Extra arguments for every yt-dlp call, e.g. `["--cookies-from-browser", "firefox"]` |
| `searchPrefix` | `ytsearch1:` | Default search (`scsearch1:` = SoundCloud) |
| `maxPlaylistItems` / `maxQueueSize` | `100` / `500` | Limits |
| `loadFullPlaylists` | `false` | `play` on a link to a video inside a playlist (`watch?v=…&list=…`) loads the whole playlist instead of only the video |
| `volume` | `60` | Volume on start |
| `virtualMicrophone` | `true` | Use a virtual microphone instead of a hardware device (restart required) |
| `virtualSpeaker` | `false` | Never play voice chat through speakers/headphones (used automatically when there is no audio output) |
| `suppressMicrophone` | `true` | Never transmit the bot's real microphone |
| `autoJoinInvites` | `true` | Accept group invites from owners and members automatically |
| `createGroupOnServerSwitch` | `false` | Create an open group after a proxy server switch (see [Voice chat groups](#voice-chat-groups)) |
| `serverSwitchGroupName` | `Music Bot` | Name of that group (at most 24 characters) |
| `joinCommands` | `[]` | Commands the bot runs after joining a server, e.g. `["server music"]` |
| `serverSwitchCommands` | `[]` | Commands the bot runs after every proxy server switch |
| `membersCanDownload` | `true` | Members may download into the library (otherwise owners only) |
| `maxDownloadMinutes` | `120` | Maximum track length for downloads (0 = unlimited) |
| `useLibraryCache` | `true` | Play links that are already saved from the library instead of streaming them |
| `webPanel` | `false` | Start the web panel (restart required) |
| `webPanelBind` / `webPanelPort` | `0.0.0.0` / `8095` | Address and port of the panel (`127.0.0.1` = this PC only) |
| `webPanelPassword` | random | Password for the panel |
| `headBob` | `true` | Nod to the beat (beat detection in the bass) |
| `headBobStrength` | `12` | How far the head nods, in degrees (1-45) |
| `headBobSway` | `true` | Also turn the head slightly left/right |
| `allowLocalFiles` | `true` | Allow playing local files (owners only) |
| `systemMessagePatterns` | `[]` | Private messages sent as system messages (prefix optional), see below |
| `chatMessagePatterns` | `[]` | Public chat sent as system messages (prefix required), see below |

### Servers with chat plugins

Vanilla `/msg` and normal chat work out of the box, and the sender is verified by their UUID.
Some server plugins (EssentialsX and others) send private messages or the whole chat as *system messages* instead.
In that case, tell the bot the message format with a regular expression that contains the named groups `sender`
and `message`.

Private messages, e.g. `[PlayerName -> me] play xyz`:

```json
"systemMessagePatterns": [ "^\\[(?<sender>\\w{3,16}) -> me\\] (?<message>.+)$" ]
```

Public chat, e.g. `[Rank] • PlayerName » message`. Here the command prefix (`!`) is required, so normal chat
messages are ignored:

```json
"chatMessagePatterns": [ "^(?:[^»]* • )?(?<sender>[A-Za-z0-9_]{3,16}) » (?<message>.+)$" ]
```

Tips for writing patterns:

- Always anchor them with `^` and `$`, so nobody can fake the format inside a normal chat message.
- Keep parts before the name strict. In the example, `[^»]*` prevents someone from writing
  `x • OwnerName » !play` to pretend to be an owner.
- Private message patterns are checked first, so a private message is never mistaken for public chat.
- The bot's log (`logs/latest.log`) shows commands it received, which helps when testing a pattern.

## Running without audio devices (headless, HeadlessMC, Remote Desktop)

The bot does not need any audio hardware:

- **Virtual microphone** (`virtualMicrophone`, on by default): replaces the microphone, the music is sent anyway.
- **Virtual speaker** (`virtualSpeaker`): replaces speakers and headphones, voices of other players are discarded.
  It is used **automatically** when no audio output can be opened. Without it, Simple Voice Chat would disable voice
  chat completely and the bot could not send music. Set `virtualSpeaker` to `true` to use it always.

Messages like "Failed to open OpenAL device" from Minecraft can be ignored.

### Linux server with HeadlessMC

[HeadlessMC](https://github.com/headlesshq/headlessmc) runs the Minecraft client without a screen, so the bot can run
on a Linux server.

1. Install the mods (Fabric API, Simple Voice Chat, Voice Chat Music Bot) into the instance's `mods` folder.
2. Simple Voice Chat stays disabled until its setup assistant has been completed, which needs a screen. Mark it as
   done in `config/voicechat/voicechat-client.properties` (create the file if needed):
   ```properties
   onboarding_finished=true
   disabled=false
   ```
3. Add your account to `owners` in `config/musicbot.json`, so you can control the bot from your own client
   right away. The file is created on the first start, or you can create it yourself:
   ```json
   { "owners": [ "YourName" ] }
   ```
4. For the automatic ffmpeg download, `tar` and `xz` must be installed (e.g. `apt install xz-utils`).
   Alternatively install ffmpeg with your package manager.
5. Start the game with HeadlessMC and join the server.

### Remote Desktop (Windows)

- Minecraft keeps running when you disconnect from Remote Desktop (disconnect, do not sign out).
- If Minecraft has problems after disconnecting, move the session to the console instead of closing the RDP window
  (as administrator): `tscon %sessionname% /dest:console`. This ends the RDP connection while the session keeps running.

## Troubleshooting

- **Nobody hears the bot:** check that voice chat is enabled in the bot's Simple Voice Chat settings and that the
  bot is near you or in your group. On a headless setup, see [HeadlessMC](#linux-server-with-headlessmc).
  Players can change the bot's volume individually in their voice chat menu.
- **YouTube stopped working:** run `!update` as owner. If you installed yt-dlp yourself, update it with `yt-dlp -U`
  or `pip install -U yt-dlp`.
- **A stream stops after a long pause:** YouTube streams can expire after a few minutes. Use `seek` to continue
  from the old position.
- **The game crashes with a mixin error after a Simple Voice Chat update:** the virtual microphone and speaker hook
  into Simple Voice Chat internals. Please
  [open an issue](https://github.com/ShortByte/simple-voice-chat-radio/issues).
- **The bot ignores commands:** make sure the sender is an owner or member, and that the prefix is used in public chat.
  On servers with chat plugins, see [Servers with chat plugins](#servers-with-chat-plugins).

## Building from source

The project uses [Stonecutter](https://stonecutter.kikugie.dev/) to build one jar per Minecraft version from a single
code base. Building requires **JDK 25**.

```
git clone https://github.com/ShortByte/simple-voice-chat-radio.git
cd simple-voice-chat-radio
gradlew buildAll
```

builds all versions and collects the jars in `build/libs/`. To build a single version, run `gradlew :1.21.1:build`
(the jar ends up in `versions/1.21.1/build/libs/`). `gradlew test` runs the unit tests for every version.

### Developing for multiple versions

- Version specific code is collected in `src/main/java/de/musicbot/compat/McCompat.java`. Comments like
  `//? if >=1.21.5 {` are Stonecutter conditions: inactive branches stay commented out and are swapped in when the
  matching version is built.
- `src/` is always written for the **active version** (`stonecutter.active` in `stonecutter.gradle`, 26.3 by default).
  To work on another version in your IDE, run `gradlew "Set active project to 1.21.1"`. Switch back with
  `gradlew "Reset active project"` before committing.
- Start the game for a version with `gradlew :1.21.1:runClient` (run directory: `versions/1.21.1/run/`).
- **Adding a Minecraft version:** add it to `settings.gradle` and create `versions/<version>/gradle.properties`
  (copy an existing one and update the Fabric API and Simple Voice Chat versions). Since 26.1, Minecraft is no longer
  obfuscated; `build.gradle` picks the matching Loom plugin automatically.

### Releasing

1. Set `version` in `gradle.properties` and add a matching `## <version>` section to `CHANGELOG.md`.
2. Commit, then tag and push: `git tag v1.0.0` and `git push origin v1.0.0`.
3. GitHub Actions builds and tests every Minecraft version and creates a **draft release** with all jars.
   Review it on GitHub and click *Publish release*.
4. Publishing the release uploads every version to Modrinth automatically. This needs the repository secret
   `MODRINTH_TOKEN` (a Modrinth personal access token with the scopes *Create versions*, *Read versions* and
   *Write versions*) and the project ID in `modrinth_id` in `gradle.properties`.
   `gradlew publishAll` without a token only does a dry run and prints what would be uploaded.

## Contributing

Bug reports and pull requests are welcome on [GitHub](https://github.com/ShortByte/simple-voice-chat-radio).
Please run `gradlew test` before opening a pull request, so the change is checked against every supported version.

## License

This project is licensed under the [MIT License](LICENSE).
