The privacy of our users is very important to us.
Your use of this software is subject to the <a href=https://policies.google.com/privacy>Google Privacy Policy</a>.

## Update check

To keep Jib up to date, update checks are made to Google servers to see if a new version of
Jib is available. By default, this behavior is enabled. As a side effect this request is logged.

To disable the update check you have two options:

1. set the `jib.disableUpdateChecks` system property to `true`
2. set `disableUpdateChecks` to `true` in Jib's global config. The global config is in the following locations by default:
    * Linux: `$HOME/.config/google-cloud-tools-java/jib/config.json`
    * Mac: `$HOME/Library/Preferences/Google/Jib/config.json`
    * Windows: `%LOCALAPPDATA%\Google\Jib\Config\config.json`