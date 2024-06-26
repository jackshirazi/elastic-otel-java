#!/usr/bin/env bash
set -euo pipefail

echo "--- Prepare a secure temp :closed_lock_with_key:"
# Prepare a secure temp folder not shared between other jobs to store the key ring
export TMP_WORKSPACE=/tmp/secured
export KEY_FILE=$TMP_WORKSPACE"/private.key"

# Secure home for our keyring
export GNUPGHOME=$TMP_WORKSPACE"/keyring"
mkdir -p $GNUPGHOME
chmod -R 700 $TMP_WORKSPACE

echo "--- Prepare keys context :key:"
# Nexus credentials
NEXUS_SECRET=kv/ci-shared/release-eng/team-release-secrets/otel/maven_central
ORG_GRADLE_PROJECT_sonatypeUsername=$(vault kv get --field="username" $NEXUS_SECRET)
export ORG_GRADLE_PROJECT_sonatypeUsername
ORG_GRADLE_PROJECT_sonatypePassword=$(vault kv get --field="password" $NEXUS_SECRET)
export ORG_GRADLE_PROJECT_sonatypePassword

# Signing keys
GPG_SECRET=kv/ci-shared/release-eng/team-release-secrets/otel/gpg
vault kv get --field="keyring" $GPG_SECRET | base64 -d > $KEY_FILE
## NOTE: passphase is the name of the field.
KEYPASS_SECRET=$(vault kv get --field="passphase" $GPG_SECRET)
export KEYPASS_SECRET
KEY_ID=$(vault kv get --field="key_id" $GPG_SECRET)
KEY_ID_SECRET=${KEY_ID: -8}
export KEY_ID_SECRET

# Import the key into the keyring
echo "$KEYPASS_SECRET" | gpg --batch --import "$KEY_FILE"

# Export the key in ascii armored format
SECRING_ASC=$(gpg --pinentry-mode=loopback --passphrase "$KEYPASS_SECRET" --armor --export-secret-key "$KEY_ID_SECRET")
export SECRING_ASC

echo "--- Configure git context :git:"
# Configure the committer since the maven release requires to push changes to GitHub
# This will help with the SLSA requirements.
git config --global user.email "obltmachine@users.noreply.github.com"
git config --global user.name "obltmachine"

