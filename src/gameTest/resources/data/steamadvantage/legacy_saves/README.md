# Pre-XML save fixtures

Each file is a block entity saved by SteamAdvantage 3.1's shared `SteamMachine` entity, before
the machines became MMDLib XML definitions. They were captured from commit 74ffdb0 by building
the old entity with known contents and writing `NbtUtils.structureToSnbt` of
`saveWithFullMetadata` (position removed; the electric boiler's buffer renamed to the pre-2.0.2
`Energy` key). `SteamGameTests.preXmlMachineSavesLoad` and `preXmlTankAndSwitchSavesLoad` load
them into the XML machines. Do not regenerate them from current code: the old entity is gone.
