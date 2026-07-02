# Chronicler

A PaperMC plugin that tracks server events and generates a dynamic in-game newspaper delivered as a written book.

## Features

- **Death & PvP Tracking** — Records deaths, PvP kills, and mob kills with causes
- **Advancement Tracking** — Captures every advancement earned by players
- **Building & Exploration** — Tracks blocks placed/broken (non-natural), biome discoveries
- **Social Tracking** — Join/leave events, session length, login streaks
- **Session Tracking** — Cumulative playtime, session counts, daily login streaks with milestones
- **Dynamic Newspaper** — Generates a structured book with sections: Headlines, Obituaries, Achievements, Hunting Grounds, Exploration & Building, Statistics
- **LLM Integration** — Optional AI-powered article generation via Ollama, OpenAI-compatible APIs (OpenRouter, OpenAI), or Anthropic Claude; falls back to template summaries
- **Web View** — Optional embedded HTTP server serves a styled HTML version of each issue
- **PlaceholderAPI** — 13+ placeholders exposing issue stats, player playtime, login streaks
- **Archived Issues** — Every issue is saved to disk and browsable via `/chronicler archive`
- **Headline Ticker** — Periodic action-bar broadcasts of random headlines from the latest issue
- **Issue #0** — On first run, any backlogged events from before the plugin was installed are automatically compiled into issue #0
- **Auto-Delivery** — Each new issue spawns directly into every online player's inventory (drops at feet if full)
- **bStats Metrics** — Anonymous usage statistics

## Requirements

- **Server:** Paper 1.21.5+ (API 26.1.2.build.72-stable)
- **Java:** 26+
- **Optional:** [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for placeholder expansion
- **Optional:** Ollama, OpenAI, OpenRouter, or Anthropic API key for LLM articles

## Installation

1. Download the latest `Chronicler-1.0.0-all.jar` from the [releases page](https://github.com/ewanc26/Chronicler/releases)
2. Place the jar in your server's `plugins/` folder
3. Restart the server
4. Edit `plugins/Chronicler/config.yml` to your liking
5. Run `/chronicler reload` to apply changes

## Configuration

`plugins/Chronicler/config.yml`:

```yaml
# Master toggle
enabled: true

# Publication schedule: DAILY, WEEKLY, BIWEEKLY, MONTHLY, or ticks
schedule: WEEKLY
publish-day: 0        # 0=Monday, 6=Sunday
publish-hour: 8       # Hour in 0-23

# Event tracking toggles
tracking:
  deaths: true
  kills: true
  pvp: true
  advancements: true
  blocks: true
  exploration: true
  social: true

# Maximum stored events before pruning
event-limit: 500

# LLM article generation (set enabled: false for template-only mode)
llm:
  enabled: true
  provider: ollama    # ollama, openai, anthropic
  model: llama3.2
  api-key: ""
  base-url: https://openrouter.ai/api/v1
  ollama-url: http://localhost:11434
  timeout-seconds: 30
  system-prompt: "You are the editor of \"{series_title}\"..."

# Newspaper appearance
newspaper:
  title: "The Weekly Chronicle"
  author: "Chronicler"
  stories-per-section: 5
  show-statistics: true

# Headline ticker & placeholders
ticker:
  interval-ticks: 1200    # 60 seconds; 0 to disable
  papi-enabled: true

# Web view
web:
  enabled: true
  port: 8080
  write-files: true
```

## Commands

| Command | Permission | Description |
|---|---|---|
| `/chronicler read` | `chronicler.use` | Receive the latest issue as a book |
| `/chronicler web` | `chronicler.use` | Show the web view URL |
| `/chronicler status` | `chronicler.admin` | Show plugin status |
| `/chronicler stats <player>` | `chronicler.use` | View a player's tracked stats |
| `/chronicler archive list` | `chronicler.use` | List past issues |
| `/chronicler archive read <#>` | `chronicler.admin` | Receive an old issue as a book |
| `/chronicler reload` | `chronicler.admin` | Reload configuration |
| `/chronicler publish` | `chronicler.admin` | Force-publish a new issue now |
| `/chronicler help` | `chronicler.use` | Show help |

Alias: `/clr`

## Placeholders (PlaceholderAPI)

| Placeholder | Description |
|---|---|
| `%chronicler_issue%` | Current issue number |
| `%chronicler_events%` | Events stored in buffer |
| `%chronicler_schedule%` | Publication schedule |
| `%chronicler_llm%` | LLM status (online/offline) |
| `%chronicler_web%` | Web status (enabled/disabled) |
| `%chronicler_last_publish%` | Last publish date |
| `%chronicler_players%` | Unique players in latest issue |
| `%chronicler_deaths%` | Death stories in latest issue |
| `%chronicler_kills%` | Kill stories in latest issue |
| `%chronicler_advancements%` | Advancement stories in latest issue |
| `%chronicler_playtime%` | Player's total playtime (Xh Ym) |
| `%chronicler_streak%` | Player's current login streak (days) |
| `%chronicler_sessions%` | Player's total session count |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `chronicler.use` | `true` | Use basic commands |
| `chronicler.admin` | `op` | Reload, publish, archive read |

## Building from Source

```bash
git clone https://github.com/ewanc26/Chronicler.git
cd Chronicler
./gradlew build
```

The compiled jar will be at `build/libs/Chronicler-1.0.0-all.jar`.

## Testing

```bash
./gradlew test
```

## Data Files

- `plugins/Chronicler/events.json` — Event buffer (JSON, kotlinx-serialization)
- `plugins/Chronicler/sessions.json` — Per-player session data
- `plugins/Chronicler/publish-state.json` — Last publish time and issue number
- `plugins/Chronicler/archive/issue-*.json` — Archived issues

## License

AGPL-3.0. See [LICENSE](LICENSE).
