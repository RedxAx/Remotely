## Added
- Added ReSync Studio support for chat channels, chat formats, rules, private messages, mentions, ignore lists, MOTDs, message rules, recipes, and text templates with JSON resource editing, live previews, and packet sync.
- Added ReSync recipe authoring in Flow Editor with shaped, shapeless, furnace, blasting, smoking, campfire, stonecutter, and smithing station layouts, slot editing, provider/item selection, and recipe I/O node support.
- Added MiniMessage rendering support in ReScreen and RemotelyMod text renderers for richer in-game and Remotely UI text.
- Added ItemSelectorWidget category support in ReScreen for grouped item browsing.
- Added trackpad gesture support for InfiniteScreen and InfiniteSpaceWidget panning and zooming.

## Changed
- Unified ReSync resource type handling in FlowManager and ReSyncResourceType for the expanded JSON-backed customization resource catalog.
- Improved ReSync Flow Client and synced resource plumbing for chat, MOTD, recipe, and text template resources.

## Fixed
- Fixed a critical RemotelyMod and Fabric API exception in custom payload bridge handling.
- Fixed potential local server startup issues in CachedResourceDataManager and TerminalWidget.
- Fixed ReStudio backend remote server file deletion so hosted server files are removed correctly from the cloud workspace.
- Fixed popup size initialization in ReScreen popups.
