# Signing key

`nbt-aad-helper.keystore` is the key every GitHub build is signed with. Android installs an update over an
installed app only when both are signed with the same key, so this file must not change or be lost: a new key means
the app has to be uninstalled once before the next version can be installed.

The key is stored in the repository on purpose (no secret to set up). Its passwords are in
`.github/workflows/build.yml`. It holds no personal data: it only lets this app be updated on a phone.

To keep the key private instead, add its Base64 text as the repository secret `SIGNING_KEYSTORE_BASE64`
(Settings > Secrets and variables > Actions); the build then uses the secret and ignores this file.
