# User release naming requirement

Always increment the numeric version for every new WINGO release. Current release is 5.13.1; the next patch release is 5.13.2.

- Use `wingo-release/v<version>/` for each published release, with archive `WINGO_v<version>_FULL_SOURCE.zip` and archive top-level folder `WINGO_v<version>`.
- Include `v<version>` in all accompanying release README, checksum, validation, changes, diff and artifact verification filenames.
- Keep VERSION.txt, runtime APP_VERSION/BUILD_NAME, source distribution profile, install marker, installer version and four-part Windows PE metadata consistent.
- Preserve previous release folders and immutable download links. Do not replace published release archives with different contents under the same version.
- Verify source identity, test report identity, archive CRC and all manifest hashes before publishing. Record Windows/live-broker limitations accurately.
- Fixed internal import names and runtime executable names are compatibility contracts; version the distributable folders/files.
