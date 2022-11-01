---
title: CLI Release {{ env.RELEASE_NAME }}
labels: release
---
## Requirements
- [ ] ⚠️ Ensure the release process has succeeded before proceeding
- [ ] ⚠️ Publish [Release]({{ env.RELEASE_DRAFT }}) after adding CHANGELOG entries ([example](https://github.com/GoogleContainerTools/jib/releases/tag/v0.8.0-cli))

## GCS
- [ ] Run {{ env.GCS_UPDATE_SCRIPT }} script to update GCS with the latest version number

## Github
- [ ] Update [CHANGELOG.md]({{ env.CHANGELOG_URL }})
- [ ] Update [README.md]({{ env.README_URL }})
- [ ] Merge PR ({{ env.RELEASE_PR }})
