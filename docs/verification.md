# Verification scope

Tested on 2026-10-16 UTC against FTB Evolution 2 1.3.1, Minecraft 26.1.2,
NeoForge 26.1.2.121 and the exact Wand/Ultimine hashes in the README.

## Unit tests: 20 passing

- 4 tests execute the compiled Wand context wrapper: nested callback ordering,
  false returns, original exception identity, both capture fields and deep nesting.
- 7 tests execute the compiled Ultimine wrapper with external game/filesystem
  collaborators replaced: normal success, nested rejection, owned capture failure,
  partial-flush ambiguity, pre-capture failure, preservation failure and a
  restart-visible hold.
- 3 real filesystem tests cover an intent surviving pre-codec failure, distinct
  committed records without overwrite and fail-closed filesystem errors.
- 6 target-pin tests cover the reviewed JARs and changed/missing/wrong targets.

These unit tests do not replace native Mixin integration tests.

## Native checks: 39 passing across two boots

An isolated full-pack dedicated server used the exact release addon and pinned
upstream targets. Its other custom server mods were staged test versions, so
this is not a claim that every production interaction was exercised.

The first completed boot passed 22 checks, including:

- Actual native ServerPlayer, pressed Ultimine, actual Supremium paxel with
  Mining AOE II and Fortune III; all 25 blocks in the 5x5 ore patch were cleared.
- 16 observed Wand callbacks all received non-null state, including reentrancy.
  This is not a claim that 25 separate callbacks were counted.
- A controlled exception inside an owned capture propagated unchanged; the global
  capture flag was false afterward.
- Seven named emeralds and 11 XP were preserved in native NBT with their item
  components intact and no automatic replay.
- The next Ultimine attempt was held before collector clearing; ordinary block
  drops, item tossing and pickup continued working.

A second completed JVM boot passed 17 checks, including exact held-record
retention, a fresh empty collector, continued Ultimine hold, no reward replay,
and normal mining/toss/pickup positive controls.

Both JVMs exited normally. All test listeners were loopback-only and closed at
completion. No real players, production worlds or credentials were used in the
fixture. Live client mining after deployment was left to natural player use.

Earlier 1.0.1/1.0.2 candidates failed native Mixin lifecycle integration and were
rejected before deployment. Version 1.0.3 fixes those integration failures. Only
1.0.3 is released here.

## Release identity

`voidcraft-wand-compat-26.1.2-1.0.3.jar`

SHA256: `1f80b7f7704603f5e4f5db5d924dc0cb90d761bde1d5f12ad675473146a9f4aa`

The public source tree changes build/test dependency locations to `libs/`; its
production source and resources match the tested release. Publication includes
a fresh build/test run and comparison of the generated JAR with that identity.
