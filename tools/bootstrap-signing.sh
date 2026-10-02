#!/usr/bin/env bash
# One-time owner key creation. Only the PUBLIC recovery key is uploaded to the repository.
# The keystore password is returned RSA-OAEP encrypted; plaintext never enters artifacts.
set -euo pipefail
task_public_key="$1"
task_output_dir="${2:-owner-signing}"
mkdir -p "$task_output_dir"
chmod 700 "$task_output_dir"
test ! -e "$task_output_dir/owner.jks"
export HOUSE_SIGNING_PASSWORD="$(openssl rand -hex 32)"
if [ "${GITHUB_ACTIONS:-}" = "true" ]; then
    printf '::add-mask::%s\n' "$HOUSE_SIGNING_PASSWORD"
fi
keytool -genkeypair -storetype PKCS12 -keystore "$task_output_dir/owner.jks" \
    -storepass:env HOUSE_SIGNING_PASSWORD -keypass:env HOUSE_SIGNING_PASSWORD \
    -alias owner -keyalg RSA -keysize 3072 -validity 10000 \
    -dname 'CN=Hesab Beitna Owner, OU=Android, O=Hesab Beitna, C=EG'
printf '%s' "$HOUSE_SIGNING_PASSWORD" | openssl pkeyutl -encrypt -pubin \
    -inkey "$task_public_key" -pkeyopt rsa_padding_mode:oaep \
    -pkeyopt rsa_oaep_md:sha256 -pkeyopt rsa_mgf1_md:sha256 \
    -out "$task_output_dir/owner-password.rsa"
export HOUSE_SIGNING_STORE="$task_output_dir/owner.jks"
python3 - <<'PY'
import os, pathlib
values = {'storeFile':os.environ['HOUSE_SIGNING_STORE'], 'storePassword':os.environ['HOUSE_SIGNING_PASSWORD'],
          'keyAlias':'owner', 'keyPassword':os.environ['HOUSE_SIGNING_PASSWORD']}
def escape(value):
    return value.replace('\\','\\\\').replace(':','\\:').replace('=','\\=').replace(' ','\\ ')
path = pathlib.Path('signing.properties')
path.write_text('\n'.join(k+'='+escape(v) for k,v in values.items()), encoding='utf-8')
path.chmod(0o600)
PY
chmod 600 "$task_output_dir/owner.jks" "$task_output_dir/owner-password.rsa"
unset HOUSE_SIGNING_PASSWORD
