---
title: CLI Release {{ env.RELEASE_NAME }}
labels: release
---
## Requirements
- [ ] ⚠️ Ensure the release process has succeeded before proceeding

## Github
- [ ] Update [CHANGELOG.md]({{ env.CHANGELOG_URL }})
- [ ] Update [README.md]({{ env.README_URL }})
- [ ] Complete [Release]({{ env.RELEASE_DRAFT }})
- [ ] Merge [PR]({{ env.RELEASE_PR }})
- [ ] Update the current [milestone](https://github.com/GoogleContainerTools/jib/milestones), roll over any incomplete issues to next milestone.

## Announce
- [ ] Email jib users
- [ ] Post to [gitter](https://gitter.im/google/jib)
- [ ] Mention on all fixed issues that latest release has addressed the issue (usually any closed issues in a [milestone](https://github.com/GoogleContainerTools/jib/milestones))
