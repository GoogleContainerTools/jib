# Contributing to (Crepecake)

We'd love to accept your patches and contributions to this project. There are
just a few small guidelines you need to follow.

## Contributor License Agreement

Contributions to this project must be accompanied by a Contributor License
Agreement. You (or your employer) retain the copyright to your contribution,
this simply gives us permission to use and redistribute your contributions as
part of the project. Head over to <https://cla.developers.google.com/> to see
your current agreements on file or to sign a new one.

You generally only need to submit a CLA once, so if you've already submitted one
(even if it was for a different project), you probably don't need to do it
again.

## Code Reviews

1. Set your git user.email property to the address used for step 1. E.g.
   ```
   git config --global user.email "janedoe@google.com"
   ```
   If you're a Googler or other corporate contributor,
   use your corporate email address here, not your personal address.
2. Fork the repository into your own Github account.
3. Please include unit tests (and integration tests if applicable) for all new code.
4. Make sure all existing tests pass. (./gradlew goJF build integrationTest)
5. Associate the change with an existing issue or file a [new issue](../../issues)
6. Create a pull request!
