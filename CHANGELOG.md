## Added
- Added Quick Servers for RemotelyMod, including faster server joining from Minecraft and synced Remotely server data.
- Added background local server support with auto-restart, local controller lifecycle handling, and stronger terminal process management.
- Added packet-based Remotely-to-ReSync communication through RemotelyMod, including chunked bridge messages and live server sessions.
- Added a unified ReSync Studio experience across flows, GUIs, custom content, tab lists, scoreboards, and world generation.
- Added Content Studio and custom content project support, including resource drag payloads, content metadata, graph adapters, and pack content providers.
- Added ReSync Marketplace support backed by the ReStudio Marketplace API, including marketplace browsing and content import flow.
- Added scoreboard editing access from RemotelyMod and improved packet-based GUI and scoreboard subscriptions.
- Added GUI command action support and better GUI designer behavior for Minecraft item tooltips.
- Added native Minecraft texture fetching and Minecraft tooltip rendering for in-game Remotely/ReSync interfaces.
- Added Skript editor integration through Rebase, including completion, syntax highlighting, project indexing, addon jar indexing, and bundled Minecraft/Skript catalogs.
- Added SSH Java management and more asynchronous server-management paths for remote instances.
- Added terminal scrollbar support and improved terminal selection behavior in `ServerDetailsScreen`.
- Added generic resource browser layouts and marketplace resource widgets from Rebase.
- Added unified container interactions and notification-based download feedback across resource containers.
- Added notification inbox improvements and cleaner quick-download notification feedback.
- Added desktop and windowing polish from ReScreen, including F11 desktop mode handling, tab icons, popup improvements, closing animations, cursor resizing visuals, and updated default desktop/gui-scale values.

## Changed
- Reworked ReSync Studio so it behaves as a single coordinated workspace instead of scattered standalone screens.
- Retired more of the old `FlowManagerScreen` flow in favor of ReSync Studio-backed views.
- Improved ReSync Studio asset explorer behavior and general studio UI/UX across Flow Editor, Content Studio, GUI Designer, Tab Designer, and Scoreboard Designer.
- Improved node registry, option catalog, player facet, and synced resource handling for live ReSync projects.
- Replaced the old settings `Account` tab with an `About` tab.
- Improved file explorer and workspace tree widgets through generic Rebase/ReScreen components.
- Improved editor text handling for carriage returns, new lines, completion, undo history, breadcrumbs, and quick access behavior.
- Improved resource container actions by making interaction behavior consistent across Remotely and shared Rebase/ReScreen widgets.

## Fixed
- Fixed ReSync Studio tab state mutation issues.
- Fixed ReSync Studio singleton behavior.
- Fixed broken ReSync filesystem sync and cache behavior from the Remotely side.
- Fixed unnecessary ReSync log spam.
- Fixed RemotelyMod hint rendering.
- Fixed Remotely app escape handling when hosted inside Minecraft.
- Fixed `OP Me` server configuration issues.
- Fixed SSH-related server management problems, including restart-needed rename behavior and remote Java handling.
- Fixed local server controller reliability issues that could make it stop responding.
- Fixed mounted buttons needing a double-click in shared ReScreen containers.
- Fixed terminal widget UX issues and line ending handling.
- Fixed taskbar icon widget management and minimized windows not hiding correctly.
- Fixed popup ownership causing incorrect screen changes.
- Fixed uncentered tab text, incorrect asset path names, and missing placeholder icon assets.
- Fixed file explorer breadcrumb edge cases, item selector lifecycle issues, and faster item selector behavior regressions.
- Fixed potential infinite LSP installation and removed LSP suggestions where they were no longer useful.