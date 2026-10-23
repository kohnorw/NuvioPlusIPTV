<div align="center">

# Nuvio + IPTV

**Everything in Nuvio, plus everything IPTV: a TiviMate-style Live TV guide, catch-up, and your provider's movies and series, all in one app for Google TV, Android TV and Fire TV.**

[![Latest release](https://img.shields.io/github/v/release/kohnorw/NuvioPlusIPTV?include_prereleases&label=latest&style=for-the-badge)](https://github.com/kohnorw/NuvioPlusIPTV/releases)
[![Downloads](https://img.shields.io/github/downloads/kohnorw/NuvioPlusIPTV/total?style=for-the-badge)](https://github.com/kohnorw/NuvioPlusIPTV/releases)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue?style=for-the-badge)](LICENSE)
[![Based on Nuvio](https://img.shields.io/badge/based%20on-NuvioMedia%2FNuvioTV-8A2BE2?style=for-the-badge)](https://github.com/NuvioMedia/NuvioTV)
[![Support on Ko-fi](https://img.shields.io/badge/support-Ko--fi-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/homelessbrian)

**Downloader code: 5772128**

[Download](#-installation) · [Features](#-features) · [Setup](#-getting-started) · [Remote controls](CONTROLS.md) · [Report a bug](#-reporting-bugs-and-requesting-features) · [Support](#-support-the-project)

</div>

---

## 🔄 Nuvio Bridge favorites sync

This fork has no Sports section (lighter and quicker), and syncs Live TV favorites with a
[Nuvio Bridge](https://github.com/kohnorw/nuvio-bridge) server, both ways.

It's on by default and uses the Nuvio account the app is already signed in to: the bridge at
`https://inuvio.kohnor.ca` checks the app's Nuvio token, so there's nothing to enter. Channels
are matched by name. Favorites sync when Live TV opens, a few seconds after you change one,
and every 15 minutes. Turn it off under **Settings → Live TV → Nuvio Bridge**.

---

## ✨ Features

### 📺 A proper TV guide
- **TiviMate-style guide:** channels down the side, a timeline across, and a line marking "now" (which you can hide). It fits **8 or more channels** on screen.
- **Guide Size:** Compact, Standard, Large or Extra Large. It sets the size of overlay mode too.
- **Group list:**
  - **Opening it:** it slides out with a tap of Left.
  - **Grouping:** optionally grouped by playlist.
  - **Browse by channel name** (satellite-box style).
- **Info panel** with the show's poster (or just the channel logo, if you prefer), time left, the description (as much as fits), the playlist it comes from, and a live preview window.
- **Make room for more channels:** a smaller info panel, and hiding numbers, logos, names or episode numbers.
- **Badges:** catch-up icons on channels that support it, and 4K / FHD / HD / SD badges on channels you've watched.

### 🎬 Overlay mode (while watching)
- Press **Left** in full screen for a see-through guide over the video: the group's channels, the highlighted channel's schedule, and the show's details.
- **Left again** switches groups. **Right** opens the channel's full schedule, with a date picker.
- **Grouped by playlist** (when you have more than one), with headings you can fold, as in the guide.

### 📡 Your sources, your way
- **Multiple M3U playlists** and **multiple XMLTV guides,** merged into one guide.
- **Xtream Codes logins:**
  - **Pick what to include:** TV channels, movies & series, or both.
  - **Stream format:** Auto, TS or HLS.
  - **Account details:** expiry date, status and connections in use, with a warning before it runs out.
- **Smart guide matching:** real listings always beat "Programming" placeholders. Fine-tune any channel with **Assign EPG**.
- **Catch-up:**
  - **Watch past shows** with a seek bar, or **restart what's on now**.
  - **Follow-on playback:** carries straight on into the next show.
  - **Guide time shift** and **Catch-up time shift** for providers that are off by an hour or more.
- **Channel name editor** (like TiviMate): strip "USA:", "HD", "24/7" and similar from channel names.

### 🗂️ Channel management
- **Manage visibility** and **reorder** for channels *and* groups.
- **Copy, rename and renumber** channels, **create your own groups,** and **number channels 1, 2, 3…** in each group.
- **Channel numbers from your playlist** are used when it has them.
- **Favorites** and **Recently watched,** with sorting that works everywhere.

### 🎞️ On Demand
- **What it is:** movies and series from your Xtream providers, with **categories or genres**, **Recently added**, **Search** and **Sort by**.
- **Your posters, not the provider's:** posters and details come from your own addons. Matched titles open on Nuvio's normal details page, with watched marks and the usual long-press menu.
- **📡 On Demand** in Nuvio's stream list, with quality, codec and audio badges. Choose where its tab goes, and whether auto-play may pick it.
- **Each title once:** duplicates are merged, and a picker lets you choose the version when there are several.

### 🔔 Watching
- **Zap** with Up/Down, type a channel number, or jump to the **last channel**.
- **Pause and rewind live TV**, even on channels without catch-up.
- **Reminders:** "Remind me" on any upcoming show, with a pop-up and a Watch button.
- **Player menu:** audio, **closed captions**, screen size, **picture shape**, stream info, **sleep timer**, record, find & stream, and watch from the beginning.
- **Your audio choice is remembered for each channel** (a second language, or 5.1 over stereo).
- **⏪ / ⏩ on the remote** skip 30 seconds in catch-up and paused live TV; hold to go faster.
- **On-screen clock** in any corner, if you want one.
- **Smooth playback:**
  - **Keeps the TV awake** while you watch (no screen saver).
  - **Match frame rate** (Off, On start, or On start/stop, as in Nuvio's player) and **audio passthrough** settings.
  - **Auto-reconnect,** plus automatic recovery from frozen or stuck streams, with the reason shown when a stream fails.

### 🔒 Parental controls
- **PIN locks:** a PIN that locks any group or category. Adult content locks automatically.

### 🔎 Part of Nuvio, not bolted on
- **In the side menu:** Live TV and On Demand sit in Nuvio's side menu, with their settings inside Nuvio's own Settings.
- **Start page:** open the app on Home, Live TV, On Demand and more.
- **Home screen row:** your favorite channels and what's on now, next to Continue watching.
- **Live TV in Nuvio search:**
  - **Your order:** choose which results come first.
  - **Preview behind search:** live channels play in the background while you search.
- **Find & stream in Nuvio:** see a movie in the guide, and stream it through your own addons and debrid.
- **Google Drive sync:** your setup on every TV, combined correctly when you use more than one.

---

## 📥 Installation

### The quick way: Downloader code **5772128**

1. Install **Downloader** (by AFTVnews) from your TV's app store.
2. Open it, type **5772128** in the search or address box, and press **Go**.
3. It downloads the latest **universal** version (works on every device). Press **Install**, allowing installs from unknown sources when asked.

### Or download the APK yourself

1. Open the **[latest release](https://github.com/kohnorw/NuvioPlusIPTV/releases/latest)**.
2. Download the `universal` APK. It works on every Google TV, Android TV and Fire TV device.
3. Install it with a file manager or **Downloader**, allowing installs from unknown sources when asked.

Requires **Android 7.0 or newer** (Fire OS 6 or later). It installs **alongside** the official Nuvio app. It also installs over homelessbrian's Nuvio + IPTV, keeping your setup.

**Updating:** the app checks this fork's releases and offers updates **inside the app**. Downloader code **5772128** always installs the newest one.

---

## 🚀 Getting started

1. Open **Settings → Live TV**.
2. Choose **Add M3U Playlist** or **Add Xtream Codes Login**. For Xtream, pick **Include TV channels** and/or **Include movies & series**.
3. Optionally **Add EPG Source** for an extra TV guide.
4. Open **Live TV**, **On Demand** or **Sports** from the side menu. The first load can take a minute on big providers; a small progress bubble shows what's happening.
5. Optional, all in **Settings → Live TV**:
   - set a **Start page**
   - turn on the **home screen row**
   - set a parental **PIN**
   - turn on **Google Drive sync**
6. In **Sports**, press **✎ Follow Teams & Leagues** to pick your teams. Set your region and reminders under **Settings → Sports**.

---

## 🎮 Remote controls

Every button for the guide, full screen and On Demand: see **[CONTROLS.md](CONTROLS.md)**.

---

## 🐞 Reporting bugs and requesting features

Please search [existing issues](https://github.com/kohnorw/NuvioPlusIPTV/issues) first.

| | |
|---|---|
| 🐛 **Something isn't working** | [Report a bug](https://github.com/kohnorw/NuvioPlusIPTV/issues/new?template=live_tv_bug.yml) |
| 📺 **An idea for a new feature** | [Request a feature](https://github.com/kohnorw/NuvioPlusIPTV/issues/new?template=live_tv_feature.yml) |
| 🎨 **The look or layout could be better** | [Suggest a UI / UX improvement](https://github.com/kohnorw/NuvioPlusIPTV/issues/new?template=live_tv_ui.yml) |

**If the app crashes,** scan the QR code on the crash screen with your phone and paste the text into your report.

**If something doesn't load** (a playlist, the guide or catch-up), open **Settings → Live TV → Help → Start-Up Report** and include it in your report. Usernames and passwords are hidden in it.

Problems with Nuvio's movies, shows, addons or accounts that also happen in the official app belong with [Nuvio](https://github.com/NuvioMedia/NuvioTV/issues).

---

## ❓ FAQ

<details>
<summary><b>Does this include any channels or movies?</b></summary>

No. It only plays what you add yourself: your own playlists, guides, Xtream logins, addons and debrid services.
</details>

<details>
<summary><b>I don't see On Demand.</b></summary>

Turn on movies & series for your Xtream login under **Settings → Live TV → On Demand**. On Demand appears in the side menu once the first import finishes.
</details>

<details>
<summary><b>Sports shows the wrong channel for a game, or none at all.</b></summary>

Open the game and press **Fix channel**. Pick the right channel for that game, for every game on that network, or for every game a team plays. It's remembered from then on. **MAYBE** means the app wasn't sure; a channel whose guide lists the game is marked **SHOWING IT**.
</details>

<details>
<summary><b>I can't find my team or league in Sports.</b></summary>

Search for it in **✎ Follow Teams & Leagues**: search covers every league, not just your region's. If it still isn't there, follow it as a name, and any show in your TV guide with that name in the title appears on My Sports.
</details>

<details>
<summary><b>Why doesn't a league show under Tables?</b></summary>

Tables lists the leagues you follow and your teams' leagues that have a table. Cups and knockout competitions don't have one, and neither do golf, tennis or fights. Turn off **Hide Scores** under **Settings → Sports** to see tables.
</details>

<details>
<summary><b>Where do the scores come from?</b></summary>

Scores, schedules, tables, team logos and colors come from ESPN's free public feed. Non-league football, rugby league, cricket, UK ice hockey, Gaelic games, netball, and the wrestling and boxing schedules come from TheSportsDB, and some event dates from sporting-events.org. Sports they don't cover are found in your own TV guide.
</details>

<details>
<summary><b>Some channels show "Programming" or the wrong listings.</b></summary>

Long-press the channel and choose **Assign EPG**. Press Left to search, or pick a TV guide from the buttons at the top. If every channel is off by the same amount, use **Guide time shift**.
</details>

<details>
<summary><b>Catch-up plays the wrong show (an hour or more off).</b></summary>

Run **Update now** on that playlist once, so the app learns your provider's time zone. If it's still off, set **Catch-Up Time Shift** under **Settings → Live TV → Watching**.
</details>

<details>
<summary><b>A channel looks stretched or squashed.</b></summary>

Long-press OK while it's playing and choose **Picture shape**. Try **Use real size**; the app remembers it for that channel. **Screen size** in the same menu changes how the picture fills the screen on every channel.
</details>

<details>
<summary><b>Sound cuts out or stops on Fire TV with Echo speakers.</b></summary>

Turn off **Audio passthrough** under **Settings → Live TV → Watching**.
</details>

<details>
<summary><b>What does Google Drive sync store, and where?</b></summary>

Your Live TV setup, in a private app folder in your own Google Drive that only this app can see. See the [privacy policy](https://homelessbrian.github.io/privacy.html).
</details>

---

## ☕ Support the project

Nuvio + IPTV is free. If it's made your TV better and you'd like to say thanks, you can buy me a coffee on **[Ko-fi](https://ko-fi.com/homelessbrian)**. It's completely optional, and every bit helps keep updates coming.

[![Support me on Ko-fi](https://img.shields.io/badge/Buy%20me%20a%20coffee-Ko--fi-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/homelessbrian)

---

## 🙏 Credits

- **[Nuvio](https://github.com/NuvioMedia/NuvioTV)** by the NuvioMedia team: Nuvio + IPTV is built on their work and follows their releases.
- Guide and overlay design inspired by **TiviMate**.
- Sports data from **ESPN**'s public feed, **[TheSportsDB](https://www.thesportsdb.com)** and **sporting-events.org**. Nuvio + IPTV isn't affiliated with any of them, and team and league names and logos belong to their owners.
- Built with Kotlin, Jetpack Compose, TV Material 3 and Media3 / ExoPlayer.

Nuvio + IPTV is an unofficial fork and is **not affiliated with or endorsed by NuvioMedia or TiviMate**. Please report issues here, not to the Nuvio team.

## ⚖️ Legal

This app is a client-side player. It does not host, store or distribute any media, and it ships with no channels, playlists or content sources. Only use sources you own or are authorized to access. See Nuvio's [legal and DMCA information](https://github.com/NuvioMedia/NuvioTV#legal--dmca), which applies here as well.

Licensed under the **GNU General Public License v3.0**, like upstream Nuvio. See [LICENSE](LICENSE).
