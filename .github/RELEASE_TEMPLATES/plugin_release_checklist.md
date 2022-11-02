---
title: Plugin Release {{ env.RELEASE_NAME }}
labels: release
---
## Requirements
- [ ] ⚠️ Ensure the release process has succeeded before proceeding

## GCS
- [ ] Run {{ env.GCS_UPDATE_SCRIPT }} script to update GCS with the latest version number

## Github
- [ ] Update [CHANGELOG.md]({{ env.CHANGELOG_URL }})
- [ ] Update [README.md]({{ env.README_URL }})
- [ ] Search/replace the old published version with the new published version in [examples](https://github.com/GoogleContainerTools/jib/tree/master/examples)
- [ ] Publish [Release]({{ env.RELEASE_DRAFT }})
- [ ] Merge PR ({{ env.RELEASE_PR }})

#### Jib-Extensions
- [ ] Update versions in [Jib Extensions](https://github.com/GoogleContainerTools/jib-extensions)
- [ ] If there were Gradle API or Jib API changes, double-check compatibility and update Version Matrix on jib-extensions. It may require re-releasing first-party extensions. See [jib-extensions#45](https://github.com/GoogleContainerTools/jib-extensions/pull/45), [jib-extensions#44](https://github.com/GoogleContainerTools/jib-extensions/pull/44), and [jib-extensions#42](https://github.com/GoogleContainerTools/jib-extensions/pull/42)
