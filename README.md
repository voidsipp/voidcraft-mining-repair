# VoidCraft Mining Repair 1.0.3

Server-only compatibility addon for nested Architectural Building Wand block
breaks and exception-safe FTB Ultimine drop capture. This is an independent
workaround for the exact versions below, not an official FTB or Wand release.
Players do not install it.

## Supported targets

| Component | Exact reviewed target |
| --- | --- |
| Java | 25 |
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.121 |
| Architectural Building Wand | 1.3, SHA256 `ce80818c091c925d7f172f70a14f99423c923858c2cea3d56776a313bc0640a4` |
| FTB Ultimine | 26.1.2.5, SHA256 `2c5169a6f082338359f8c1249288a8420a87f9cdeec96fd1c2d983b17236c88f` |

Startup deliberately fails if the target JARs or required merged fields differ.
Do not remove these guards to use another pack version. Other loaders, client
installations and newer upstream builds have not been validated.

## What it fixes

Wand 1.3 stores the state and block entity for `destroyBlock` in two shared
`ServerPlayerGameMode` fields. A nested break overwrites and clears the outer
invocation's capture, allowing the outer successful callback to receive null.
The addon saves each invocation's entry values in JVM locals and restores them
in `finally`. The original callback still runs, with its original return and
exception behavior. Private VarHandles are resolved inside the merged target
class; Wand-added fields are checked in Mixin `postApply`.

Ultimine 26.1.2.5 can leave its singleton drop-capture flag set after a block-break
exception. Subsequent item entities and XP orbs are intercepted across the server,
including unrelated mining and inventory drops. On an owned operation's failure,
the addon releases capture before recovery and rethrows the same exception.
Normal successful reward behavior stays unchanged.

Captured items and XP from a failed operation are held for manual review in the
current world's `data/voidcraft-wand-compat/held-ultimine/`. Nothing is automatically
replayed: an exception during drop emission could otherwise duplicate items.
Intent, temporary and committed records block further Ultimine operations from
clearing the collector, including after a restart. Ordinary mining and item
tossing remain available. Persistence failures retain an in-memory hold and are
suppressed onto the original exception. Already-lost items cannot be recovered
from a previous JVM by this addon.

FTB already has an exception-safety change on its development branch:
[FTB-Ultimine de85253](https://github.com/FTBTeam/FTB-Ultimine/commit/de8525357640d3e27a6269d4501581de2c8130c2).
That change and the underlying Wand callback problem are distinct. This addon's
exact pins do not support that development build. Review both fixes before any
future modpack update.

## Build and test

1. Install Java 25.
2. Place these legally obtained dependencies from your FTB Evolution 2 1.3.1
   server pack in `libs/`: `architectural-building-wand-1.3.jar`,
   `ftb-ultimine-neoforge-26.1.2.5.jar` and
   `ftb-library-neoforge-26.1.2.9.jar`. The JARs are not redistributed here.
3. Run `./gradlew test jar sourcesJar`, or
   `.\gradlew.bat test jar sourcesJar` on Windows.

The first build downloads Gradle, NeoForge and test dependencies. Later builds
can use `--offline` once those dependencies are cached. The addon JAR is written
to `build/libs/`.

## Installation

1. Verify all exact target versions and hashes above.
2. Stop the dedicated server cleanly during an approved maintenance window.
3. Put only `voidcraft-wand-compat-26.1.2-1.0.3.jar` in the server's `mods/` folder.
4. Keep the original upstream mods and start the server.

If the compatibility checks fail, inspect the mismatch rather than bypassing
them. If held records appear, preserve them and reconcile the operation before
allowing further Ultimine use; there is no automatic clearing or refund command.

## Verification

The release passed 20 unit tests and 39 checks across two isolated full-pack
NeoForge boots. The unit tests execute compiled wrappers with external
collaborators replaced, exercise real hold-file I/O and verify exact target pins.
The native checks exercise actual Mixin integration, a real native ServerPlayer,
Supremium paxel 5x5/Fortune III/Ultimine reentrancy, controlled capture failure,
durable held items/XP, normal drops and cold-boot hold behavior.

See [verification details](docs/verification.md) for the test scope and limits.

## Attribution

Original compatibility source is MIT licensed. Gradle wrapper files retain their
own Apache 2.0 notices. Minecraft, NeoForge, Architectural Building Wand and FTB
Ultimine remain their respective authors' projects and licenses. No upstream
JARs, decompiled upstream sources, server worlds, player records or private logs
are included.
