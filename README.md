![Preview](https://i.ibb.co/Dg6mThbQ/minecraft-title-minecraft.png)
# CaptureZones

CaptureZones adds configurable capture-zone gameplay to Minecraft servers:

- Circle and cuboid zones
- Per-zone rules and rewards
- Optional KOTH mode
- Reinforcements, shops, statistics, holograms, map integrations
- Towny-aware or standalone ownership modes

This README is intentionally short. Full documentation lives in the wiki.

## Quick start

1. Put the plugin jar in `plugins/`.
2. Start server once.
3. Edit `plugins/CaptureZones/config.yml`.
4. Use `/cap help` in game.

## Full documentation

Wiki:
- [Wiki Home](https://github.com/logichh/townycapturezones/wiki)
- [Installation](https://github.com/logichh/townycapturezones/wiki#installation)
- [Command Reference](https://github.com/logichh/townycapturezones/wiki#command-reference)
- [Permission Reference](https://github.com/logichh/townycapturezones/wiki#command-reference)
- [Global Config Reference](https://github.com/logichh/townycapturezones/wiki#global-configuration-reference-configyml)
- [Per-Zone Config Reference](https://github.com/logichh/townycapturezones/wiki#global-configuration-reference-configyml)
- [Setup Guides](https://github.com/logichh/townycapturezones/wiki#global-configuration-reference-configyml)
- [KOTH Mechanics](https://github.com/logichh/townycapturezones/wiki#global-configuration-reference-configyml)
- [Shops](https://github.com/logichh/townycapturezones/wiki#global-configuration-reference-configyml)
- [Discord](https://github.com/logichh/townycapturezones/wiki#discord-webhooks)
- [PlaceholderAPI](https://github.com/logichh/townycapturezones/wiki#discord-webhooks)
- [Repair and Migration](https://github.com/logichh/townycapturezones/wiki#discord-webhooks)
- [Troubleshooting](https://github.com/logichh/townycapturezones/wiki#discord-webhooks)

GitHub wiki:
- https://github.com/logichh/capturezones/wiki

## Requirements

- Paper, Purpur, or Leaf 1.21.x through 26.2
- Java 21 for Minecraft 1.21.x
- Java 25 for Minecraft 26.1 and newer

Spigot may work, but Paper-family servers are the supported target. Folia is not supported.

Optional integrations:
- Towny
- Vault + economy plugin
- Dynmap
- BlueMap
- PlaceholderAPI
- MythicMobs
- WorldGuard

## Concurrent conquests

Concurrent matches are off by default, so existing servers keep the old single-match behavior. To allow several profiles at once:

```yaml
conquest:
  allow-concurrent-matches: true
  max-active-matches: 10
  persist-active-matches: true
```

Active matches cannot share zones. A town or nation can still take part in more than one match when those matches use different zones. CaptureZones saves active matches to `conquest-state.yml` and restores valid entries after a restart or plugin reload.

```text
/cap admin conquest start <profile>
/cap admin conquest stop <profile|all>
/cap admin conquest status [profile]
```

## Offline owner command rewards

Use `ALL_OWNER` when a command should run for every known member of the controlling town, nation, scoreboard team, or standalone owner. The command runs as console and must support offline names or UUIDs.

```yaml
rewards:
  command-rewards:
    max-recipients: 250
    batch-size: 25
    triggers:
      hourly-control:
        enabled: true
        recipient: "ALL_OWNER"
        execution: "CONSOLE"
        commands:
          - "give %player% diamond 1"
```

`max-recipients` limits one reward run. `batch-size` spreads large owner rewards across server ticks.

## Addon API for external plugins

CaptureZones now registers a Bukkit service for addon plugins:

- Service interface: `com.logichh.capturezones.api.CaptureZonesApi`
- Result type: `com.logichh.capturezones.api.CaptureZonesActionResult`
- API version constant: `CaptureZonesApi.API_VERSION`
- Current API version: `1.1.0`

### Resolve the service from another plugin

```java
RegisteredServiceProvider<CaptureZonesApi> rsp =
    Bukkit.getServicesManager().getRegistration(CaptureZonesApi.class);
CaptureZonesApi api = rsp == null ? null : rsp.getProvider();
```

Your addon should declare a dependency on CaptureZones in its own `plugin.yml`:

```yaml
depend: [CaptureZones]
```

### What the API exposes

- Full snapshots: overview, zones, active captures, KOTH, conquests, shops, statistics, configs, data files.
- Actions: zone lifecycle, capture controls, KOTH and conquest controls, shop controls, stats controls, config writes and reloads.
- Capability discovery via `getCapabilities()` so addons can feature-gate safely.

Conquest methods added in API `1.1.0`:

```java
Map<String, Object> conquests = api.getConquestsSnapshot();
api.startConquest("weekend_war");
api.stopConquest("weekend_war", "Event ended", true);
api.stopAllConquests("Server maintenance", true);
```

## Build from source

```bash
mvn clean package
```

Compatibility compile profiles are available for `paper-1.21.11`, `paper-26.1`, and `paper-26.2`.

## Support

- Wiki: https://github.com/logichh/capturezones/wiki
- Discord: https://discord.gg/t96nrf7Nav
- Patreon: https://www.patreon.com/cw/logich
- GitHub Sponsors: https://github.com/sponsors/logichh

![Preview](https://bstats.org/signatures/bukkit/Capture%20Points.svg)
