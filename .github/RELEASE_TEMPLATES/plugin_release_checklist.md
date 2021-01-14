---
title: Plugin Release {{ env.RELEASE_NAME }}
labels: release
---
## Requirements
- [ ] ⚠️ Ensure the release process has succeeded before proceeding

## GCS
- [ ] Run the `update_gcs_latest.sh` scripts in the {{ env.PROJECT_NAME }} projects to update GCS with the latest version number

## Github
- [ ] Update [CHANGELOG.md]({{ env.CHANGELOG_URL }})
- [ ] Update [README.md]({{ env.README_URL }})
- [ ] Update [CONTRIBUTING.md](https://github.com/GoogleContainerTools/jib/blob/master/CONTRIBUTING.md)
- [ ] Complete [Release]({{ env.RELEASE_DRAFT }})
- [ ] Merge PR({{ env.RELEASE_PR }})
- [ ] Update the current [milestone](https://github.com/GoogleContainerTools/jib/milestones), roll over any incomplete issues to next milestone.

#### Skaffold
- [ ] Update versions in Skaffold ([example PR](https://github.com/GoogleContainerTools/skaffold/pull/4639))

#### Jib-Extensions
- [ ] Update versions in [Jib Extensions](https://github.com/GoogleContainerTools/jib-extensions)
- [ ] If there were Gradle API or Jib API changes, double-check compatibility and update Version Matrix on jib-extensions. It may require re-releasing first-party extensions. See [jib-extensions#45](https://github.com/GoogleContainerTools/jib-extensions/pull/45), [jib-extensions#44](https://github.com/GoogleContainerTools/jib-extensions/pull/44), and [jib-extensions#42](https://github.com/GoogleContainerTools/jib-extensions/pull/42)

## Announce
- [ ] Email jib users
- [ ] Post to [gitter](https://gitter.im/google/jib)
- [ ] Menntion on all fixed issues that latest release has addressed the issue (usually any closed issues in a milestone)
