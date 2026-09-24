# Offline icon sync

From the repository root:

```powershell
npm.cmd ci --prefix tools/icon-sync --ignore-scripts
npm.cmd run sync --prefix tools/icon-sync
```

`package-lock.json` pins theSVG package. The generated assets are committed, so Android builds
and phone rendering need neither Node.js nor an internet connection. Run sync after updating
ICON files, the catalog, or the pinned package; review the generated diff before shipping.

`catalog.json` maps asset keys to theSVG slugs, optional local ICON filenames, and exact email
domains (including their subdomains at runtime). Add a catalog entry to support a new brand
without editing Kotlin. Domain matching uses a dot boundary, never a substring. A library
cannot reliably infer a sender's brand from an arbitrary email domain.

Existing ASCII asset names are also matched to ICON filenames ignoring punctuation/case.
Nonmatching names (including Chinese names) require an explicit `local` entry in the catalog;
previously bundled overrides remain authoritative. The sync tool preserves curated root SVGs and
removes its own duplicate generated copies; raw ICON exports must not replace corrected marks.
Original ICON files are never changed.
After syncing, duplicate SVG basenames are removed in runtime priority order, including
obsolete Simple Icons fallbacks. Attribution and license files are retained.

Runtime order: curated root assets, generated local ICON imports, theSVG, Simple Icons,
then the existing initials fallback. Mono variants are preferred; the renderer applies the
app's tint to default variants as well. Yahoo's background circle is removed explicitly.

Only catalog-selected library icons are packaged, not the entire npm catalog. No favicon or
network request runs on the phone. `thesvg/sources.json` records the source of each synced asset.
Package updates are deliberate, not an automatic fetch of latest code on every Gradle build.

Source: https://github.com/glincker/thesvg . Tooling is MIT; individual brand marks retain
their respective rights. See the bundled LICENSE and THIRD_PARTY_NOTICES.md.

## Generic category registry

`categories.json` records checked brand domains, bounded name aliases, exact-only ambiguous names,
and source URLs. `node tools/icon-sync/sync.mjs` generates `GenericSenderCategories.kt` and merges
category domain aliases into `contact_logos/domains.json`. Commit both generated files.
Existing category SVGs are reused. Dedicated domain icons win; known category domains precede
loose name matching (e.g. Maya banking versus Maya Mobile eSIM). These are visual associations,
not sender authentication. Do not add shared mail-delivery domains to a brand category.
