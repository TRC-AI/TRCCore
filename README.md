# TRC Core & Libs

TRC Core is the shared library and required base for all TRC mods. It starts the mods built on
it, holds the code they share, and gives all of them one common settings screen.

## Why do I need this?

Instead of every TRC mod bringing its own configuration, TRC Core brings them together: you
change, enable or disable features of all installed TRC mods in one place, in game or from the
chat.

- **Dependency:** TRC Core adds no content of its own(except the settings screen), but every
  TRC mod needs it.
- **Sides:** works on the client and on the server.
- **Versions:** Fabric, Minecraft 26.1 and newer. There is one file per minor Minecraft version.

## Opening the settings

- **Main menu:** the button with the TRC icon, left of "Singleplayer". Closing the screen
  brings you back to the main menu.
- **In game:** press **U**, or whatever you bound "TRC-Settings menu" to under Controls, in the
  "TRC-Mods" category.
- **Chat:** `/trc`, see [The /trc command](#the-trc-command).

## Languages

The settings screen and TRC Core's own settings follow your game language. English, German and
Pirate Speak are included; any other language shows English.

Mods built on TRC Core can translate their setting names the same way. Where a translation is
missing, the name is shown as the mod wrote it. Server settings are named in the language your
client uses, even on a server set to another language. Most chat messages and command output are
in English though.

## Editing server settings from the client

Server settings appear in the same screen as your client settings, and a change applies on the
server immediately, for the players the server allows to make it. TRC Core has to be installed
on the server as well; if it is not, the screen says so next to its title and shows only your
client settings.

**Who may edit**

- **Dedicated server:** the setting **Required OP level to edit server settings** (0 to 5,
  default 4). Players at or above it see and edit the server settings; everyone else does not
  see them at all. 0 to 4 are Minecraft's permission levels; 5 is above all of them and takes
  the right away from every operator. Players on the whitelist or the editors list and the
  server console still get in. The setting only appears when an installed TRC mod actually has
  server settings.
- **Singleplayer and LAN:** the host may always edit. Other players need **Allow LAN players to
  change server settings** (off by default), which appears once the world is opened to LAN.

Both are ordinary server settings and follow their own rule: raising the level above your own,
or switching LAN editing off while you are a LAN player, locks you out of changing it back in
game.

**In the screen**

- **Live:** a change reaches every other open settings screen at once. Losing the permission
  removes the server sections immediately; gaining it shows them the next time the screen
  opens. In singleplayer and LAN, sliders apply while being dragged; over a real connection,
  when you let go.
- **Order:** each mod's server settings come first, under its own heading. TRC Core's own
  permission setting is at the very bottom.
- **Filter:** the button top left switches between both sides, only server settings, or only
  client settings. It works together with the search and stays as you left it until you close
  the game.
- **Reset:** every setting has a reset button on its right, greyed out while the value already
  is the default.

## The /trc command

`/trc <mod> <setting> [value]` reads a setting when the value is left off and sets it when it is
given. Mod, setting and, where there is a fixed set, value all tab-complete, and only what you
are allowed to touch is offered.

| Command                        | Effect                                                           |
|--------------------------------|------------------------------------------------------------------|
| `/trc <mod> <setting>`         | Shows the current value                                          |
| `/trc <mod> <setting> <value>` | Sets the value                                                   |
| `/trc reset <mod> <setting>`   | Puts the setting back to its default                             |
| `/trc list`                    | Lists the mods whose settings you can reach, with the id to type |

- **Mod ids:** `mymod` addresses a mod's server settings, `mymod_client` its client settings,
  the same split the screen shows.
- **Values:** `true` or `false` for a switch, a whole number within range for a slider or number
  field, the option's text for a choice. A number outside the range is refused, not clamped.
- **Hidden values:** a mod can mark a text setting as hidden (a password or token). Its value is
  never shown: the screen offers an empty box for a new one, `/trc` answers `(hidden)`, and the
  server never sends it to a client. It is still stored in plain text in the settings file and
  logged with its real value when changed, so keep the log as private as the settings file.
- **Permission:** client settings are always yours. Server settings follow the rules above, and
  the server checks again when the change arrives.
- **Server side:** the command also exists on the server, so it works from the console and for
  players without TRC Core on their client. It is hidden from anybody who may not use it, and
  appears or disappears the moment the permission changes.
- **Pace:** the server takes one TRC Core request per player per tick. Faster changes are applied
  one tick after another; a faster command is refused. The console and the host of a
  singleplayer or LAN world are not limited.

## Administering access

These commands are open to the server console and, in game, to players on the editors list.
Everyone else does not see them.

```
/trc whitelist add <player>     /trc whitelist remove <player>     /trc whitelist list
/trc blacklist add|remove|list ...
/trc editors   add|remove|list ...
```

TRC Core's own settings are set like any other mod's, but need the editor right:
`/trc trc-core required_op_level 3`, `/trc trc-core allow_lan_edits true`.

- **Whitelist:** may change server settings without an OP level.
- **Blacklist:** may not change or see server settings, whatever their OP level. It wins over
  the whitelist. The host of a singleplayer or LAN world can never be locked out.
- **Editors:** may change the rules themselves (TRC Core's two settings and all three lists),
  and every mod's server settings as well. This right comes only from this list, from being the
  host, or from the console; no OP level grants it, and the blacklist does not affect it. A
  fresh server starts with an empty list, so its operator adds themselves from the console.

The lists live in `config/trc-settings/trc-core-access.json`. An entry is
`{"name": "Steve", "uuid": "..."}`, either half alone, or just `"Steve"`. The missing half is
filled in when the player next joins; once a UUID is known, the player is identified by it
alone, so a renamed account keeps its listing.

**Logging and notices**

- Every accepted change is written to the server log: time, who, by what right (console, host,
  whitelist or OP level), which setting, and the value before and after. Refusals are logged
  too; slider drags only with the final value. Changes to your client settings go to your
  client's log.
- Notices about changed settings go to the console, the host, and everyone who may change
  server settings. Changes to who may administer go to the console, the host and the other
  editors. The player who made a change, and the player whose rights changed, always hear
  about it.
- **Settings changes in chat** (client setting, default *command only*) chooses which
  setting-change notices you see: all, none, command only, or screen only. Notices about access,
  blocks and editors are always shown.

## Settings files

TRC Core reads its settings files and access lists once, when the game or server starts.
Changes are written within a second. Edit the files by hand only while the game or server is
stopped, because an edit made while it runs is overwritten. A settings file that is empty or
cannot be read is replaced by the mod's defaults.