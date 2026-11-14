# Plugin Hub compatibility gate

`tools-src/hub-compat-scan.py` resolves every class, field and method reference of every
Plugin Hub jar against what the Android app offers a plugin (android-awt, android.jar,
runelite-client/api, the app's shims and host libraries). Whatever does not resolve lands
in `build/hub-compat/report.json` and `report.md`, grouped into the buckets of issues
#62-#72. It is static linkage only: a symbol that resolves may still misbehave.

`docs/hub-compat-baseline.json` lists the unresolved signatures we have accepted. CI fails
when the scan finds one that is not listed, so a gap can only grow on purpose.

## In CI

`ci/build.sh`, after the release build:

1. `./gradlew :client:jar :runelite-api:jar`, so the host jars exist;
2. `python3 tools-src/hub-compat-scan.py --refresh` (current manifest and install counts;
   jars come from `build/hub-cache`, which `.github/workflows/CI.yml` restores with
   `actions/cache`, so only new or updated plugins are downloaded);
3. `python3 tools-src/hub-compat-issues.py --check`: exits 1 on a signature missing from the
   baseline, lists baseline entries that now resolve, and always writes
   `build/hub-compat/baseline-proposed.json`.

The `hub-compat-<run>` artifact holds `report.md`, `report.json` and
`baseline-proposed.json`; it is uploaded even when the build fails.

The hub moves on its own: a plugin update that references a new missing symbol fails CI
on a commit that did not touch anything. That is the point of the gate; handle it like
any other new gap below.

## When the gate fails

Read the `-- Unresolved and not in the baseline --` list in the log, then either

- shadow the symbol in android-awt (or provide the library), so the next scan resolves it; or
- accept it: copy `baseline-proposed.json` from the artifact over
  `docs/hub-compat-baseline.json` (or run the scan locally and
  `python tools-src/hub-compat-issues.py --update-baseline`), review the diff and commit.

An empty baseline (as first committed) fails on every signature; the first CI run's
`baseline-proposed.json` is the initial baseline.

## Shrinking the baseline

When `--check` lists entries that now resolve, run `--update-baseline` on a full scan and
commit: resolved entries are dropped, the rest kept. Do this from a full scan only;
`--limit`/`--plugin` scans drop signatures without fixing them, and the tool warns when
the report covers fewer plugins than the baseline did.

## Documented gaps

A signature that is deliberately unsupported (no Android equivalent, a plugin on
`ANDROID_UNAVAILABLE`) gets `"status": "documented"` and a `"note"` in the baseline:

```json
"class java/awt/Robot": {"bucket": "dialogs/windows/desktop", "status": "documented",
                         "note": "no input injection on Android; plugin is ANDROID_UNAVAILABLE"}
```

`--update-baseline` keeps the status and note while the signature stays unresolved.

## Updating issues #62-#72

```
python tools-src/hub-compat-issues.py --issue 62    # or --all
```

prints a markdown checklist for the issue's bucket, ready to paste into the issue body:

| status | mark | meaning |
|---|---|---|
| unresolved | `- [ ]` | in the latest report |
| documented | `- [ ] … documented: <note>` | in the report, explained in the baseline |
| resolved | `- [x] … resolved` | in the baseline, gone from the report |

The unresolved count includes the documented ones and equals
`report.json` → `buckets[<bucket>].signatures`. Issue to bucket:

| issue | bucket |
|---|---|
| #62 | layout/input/painting |
| #63 | drawing/geometry/fonts/images |
| #64 | dialogs/windows/desktop |
| #65 | widgets/menus/tables |
| #66 | text/HTML |
| #67 | dnd/datatransfer |
| #68 | tooltips/accessibility |
| #69 | audio |
| #70 | lwjgl/native |
| #71 | guava |
| #72 | other libraries |

`runelite` and `other jdk` signatures are gated too but belong to no issue.

To get the report locally, use the CI artifact's `report.json` (`--report path`), or build
`:android-awt:compileDebugJavaWithJavac` and the client/api jars and run the scan.
