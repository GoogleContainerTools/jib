The privacy of our users is very important to us.
Your use of this software is subject to the <a href=https://policies.google.com/privacy>Google Privacy Policy</a>.

## Update check
Many Jib users are unaware of new releases. To encourage users to stay up-to-date, the Jib Maven and Jib Gradle plugins (2.0.0 and later) and Jib CLI (0.6.0 and later) will
periodically check to see if there is a new version of Jib is available. This check fetches a simple text
file hosted in Google Cloud Storage. As a side effect this request is logged, which includes the request path,
source IP address, and the user-agent string. The user-agent is set by Jib and includes the Jib tool name
and version.

### How to disable update checks

1. set the `jib.disableUpdateChecks` system property to `true`
2. set `disableUpdateCheck` to `true` in Jib's global config. The global config is in the following locations by default:
    * Linux: `$XDG_CONFIG_HOME/google-cloud-tools-java/jib/config.json` (if `$XDG_CONFIG_HOME` is defined), else `$HOME/.config/google-cloud-tools-java/jib/config.json`
    * Mac: `$XDG_CONFIG_HOME/Google/Jib/config.json` (if `$XDG_CONFIG_HOME` is defined), else `$HOME/Library/Preferences/Google/Jib/config.json`
    * Windows: `$XDG_CONFIG_HOME\Google\Jib\Config\config.json` (if `$XDG_CONFIG_HOME` is defined), else `%LOCALAPPDATA%\Google\Jib\Config\config.json`