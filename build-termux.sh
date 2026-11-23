#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

# Builds ZipSqueeze on an ARM Android device inside Termux. No Gradle/Android Studio.
PROJECT="$(cd "$(dirname "$0")" && pwd)"
SDK="$HOME/android-sdk"
ANDROID_JAR="$SDK/platforms/android-36/android.jar"
BUILD="$PROJECT/build-termux"

pkg update -y
pkg install -y openjdk-21 aapt aapt2 d8 apksigner python

if [ ! -f "$ANDROID_JAR" ]; then
  mkdir -p "$SDK/platforms/android-36" "$HOME/.cache/zipsqueeze"
  python - "$ANDROID_JAR" <<'PY'
import sys, os, urllib.request, xml.etree.ElementTree as ET, zipfile, tempfile
out = sys.argv[1]
meta = urllib.request.urlopen('https://dl.google.com/android/repository/repository2-3.xml', timeout=60).read()
root = ET.fromstring(meta)
ns = {'r':'http://schemas.android.com/repository/android/generic/01'}
# Android SDK repository metadata has used multiple namespace revisions; find by local tag names.
def children(el, name): return [x for x in el.iter() if x.tag.rsplit('}',1)[-1] == name]
pkg = next((p for p in children(root,'remotePackage') if p.attrib.get('path') == 'platforms;android-36'), None)
if pkg is None: raise SystemExit('Android API 36 package not present in Google SDK repository metadata')
archive = next((a for a in children(pkg,'archive') if children(a,'complete')), None)
if archive is None: raise SystemExit('No complete Android platform archive found')
url = children(archive,'url')[0].text
fd, tmp = tempfile.mkstemp(suffix='.zip'); os.close(fd)
try:
  print('Downloading Android API 36 platform…', flush=True)
  urllib.request.urlretrieve('https://dl.google.com/android/repository/'+url, tmp)
  with zipfile.ZipFile(tmp) as z:
    match = next((n for n in z.namelist() if n.endswith('/android.jar')), None)
    if not match: raise SystemExit('android.jar missing from downloaded platform archive')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with z.open(match) as src, open(out,'wb') as dst:
      while True:
        b=src.read(1024*1024)
        if not b: break
        dst.write(b)
finally:
  if os.path.exists(tmp): os.unlink(tmp)
PY
fi

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex"
aapt2 compile --dir "$PROJECT/res" -o "$BUILD/resources.zip"
aapt2 link -I "$ANDROID_JAR" --manifest "$PROJECT/AndroidManifest.xml" \
  --min-sdk-version 24 --target-sdk-version 36 \
  --version-code 3 --version-name 1.0.0 \
  --java "$BUILD/gen" -o "$BUILD/base.apk" "$BUILD/resources.zip"
javac --release 8 -classpath "$ANDROID_JAR" -encoding UTF-8 -nowarn \
  -d "$BUILD/classes" $(find "$PROJECT/java" "$BUILD/gen" -name '*.java')
d8 --release --min-api 24 --lib "$ANDROID_JAR" --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name '*.class')
python - "$BUILD/base.apk" "$BUILD/dex/classes.dex" "$BUILD/unsigned.apk" <<'PY'
import sys, zipfile
src, dex, dst = sys.argv[1:]
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst,'w') as zout:
  entries=[(i,zin.read(i.filename)) for i in zin.infolist()]
  entries.append((zipfile.ZipInfo('classes.dex'),open(dex,'rb').read()))
  entries[-1][0].compress_type=zipfile.ZIP_DEFLATED
  for info,data in entries:
    out=zipfile.ZipInfo(info.filename,date_time=info.date_time)
    out.compress_type=info.compress_type; out.external_attr=info.external_attr
    if out.compress_type==zipfile.ZIP_STORED:
      start=zout.fp.tell()+30+len(out.filename.encode())
      out.extra=b'\0'*((-start)%4)
    zout.writestr(out,data)
PY
cp "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
mkdir -p "$HOME/.android"
PASSFILE="$HOME/.android/zipsqueeze.pass"
if [ ! -f "$PASSFILE" ]; then
  python -c 'import secrets; print(secrets.token_urlsafe(36))' > "$PASSFILE"
  chmod 600 "$PASSFILE"
fi
SIGN_PASS="$(cat "$PASSFILE")"
if [ ! -f "$HOME/.android/zipsqueeze.jks" ]; then
  keytool -genkeypair -keystore "$HOME/.android/zipsqueeze.jks" -alias zipsqueeze \
    -keyalg RSA -keysize 2048 -validity 10000 -storepass "$SIGN_PASS" -keypass "$SIGN_PASS" \
    -dname 'CN=ZipSqueeze' >/dev/null 2>&1
fi
apksigner sign --ks "$HOME/.android/zipsqueeze.jks" --ks-key-alias zipsqueeze \
  --ks-pass "pass:$SIGN_PASS" --key-pass "pass:$SIGN_PASS" --out "$BUILD/ZipSqueeze.apk" "$BUILD/aligned.apk"
apksigner verify "$BUILD/ZipSqueeze.apk"
aapt dump badging "$BUILD/ZipSqueeze.apk" | head -n 3
printf '\nAPK built on Termux: %s\n' "$BUILD/ZipSqueeze.apk"
