# Standalone Menu operator scripts

Run `standalone-tools.cmd` from the project root for the complete interactive manager.

It covers module debug/release builds, Root and Non-root launcher builds, complete production packages, guided device testing, APK installation, output inspection, tests, and cleanup. The guided device test is local-only and intentionally stages only the manually selected module folder or folders, or runs launcher-only, so old module-output folders cannot pollute a test run. Its module-only scope reuses an installed launcher without rebuilding or reinstalling it.

`test_helper.cmd` remains available as the direct build/install/device-test helper. Examples:

- `test_helper.cmd "module-output\com.biglime.cookingmadness" root "" stage release`
- `test_helper.cmd cooking nonroot "" stage debug`
- `test_helper.cmd launcher root "" "" release`

Production builds ask for a numeric `major.minor.patch` version, derive the build by removing its dots (`1.1.1` becomes `111`), then apply the pair equally to Root and Non-root APKs.
