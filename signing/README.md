# Signing key

The key every release is signed with is **not** in this repository. It is the repository secret
`SIGNING_KEYSTORE_BASE64` (Settings > Secrets and variables > Actions): the Base64 text of a PKCS12 keystore with the
alias `nbtaad`. The passwords in `.github/workflows/build.yml` are not what protects it: keeping the file secret is.

Android installs an update over an installed app only when both are signed with the same key, so the key must not
change or be lost: keep a private backup of the keystore file. A new key means the app has to be uninstalled once
before the next version can be installed.

The key that was kept in this repository until v1.0.55 is retired (it is still in the Git history, so it must not be
trusted). Builds that cannot see the secret, such as a fork's, are signed with a throw-away debug key.
