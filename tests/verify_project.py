from pathlib import Path
from xml.etree import ElementTree as ET
from PIL import Image

root = Path(__file__).resolve().parents[1]
manifest = root/'app/src/main/AndroidManifest.xml'
ns = {'a':'http://schemas.android.com/apk/res/android'}
xml = ET.parse(manifest)
for path in root.rglob('*.xml'):
    ET.parse(path)
app=xml.find('application')
assert app.attrib['{http://schemas.android.com/apk/res/android}usesCleartextTraffic']=='false'
assert app.attrib['{http://schemas.android.com/apk/res/android}allowBackup']=='false'
perms={p.attrib['{http://schemas.android.com/apk/res/android}name'] for p in xml.findall('uses-permission')}
assert perms=={'android.permission.INTERNET','android.permission.RECORD_AUDIO'},perms
provider=app.find('provider')
assert provider.attrib['{http://schemas.android.com/apk/res/android}exported']=='false'
main=(root/'app/src/main/java/id/denpam/app/MainActivity.java').read_text()
priv=(root/'app/src/main/java/id/denpam/app/PrivateAudioProvider.java').read_text()
for name in ['https://andin.top/denpam/','com.openai.chatgpt','ACTION_SEND','onPermissionRequest','onShowFileChooser','shouldOverrideUrlLoading','cookieFor','isDenpamUrl','WebSettings.MIXED_CONTENT_NEVER_ALLOW','handOff' if False else 'handleBridgeUri','setInstanceFollowRedirects(false)','MAX_AUDIO_BYTES','FLAG_GRANT_READ_URI_PERMISSION']:
 assert name in main,name
assert '.addJavascriptInterface(' not in main
assert 'MODE_READ_ONLY' in priv
assert 'startsWith(canonicalRoot+File.separator)' in priv
for file in (root/'app/src/main/res').rglob('ic_launcher.png'):
  im=Image.open(file)
  assert im.width==im.height
  assert im.width in (48,72,96,144,192)
assert len(list((root/'app/src/main/res').rglob('ic_launcher.png')))==5
assert (root/'.github/workflows/build-apk.yml').exists()
assert (root/'README.md').exists()
print('PROJECT CHECK: 14 checks passed (manifest, permissions, origin, URI safety, logo, workflow)')
