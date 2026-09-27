#!/bin/zsh
# tap.sh <text|desc:Description> [maxScrolls] [serial] – tap a UI node by text / content-desc (scrolling down if needed).
export PATH=$PATH:~/Library/Android/sdk/platform-tools
want="$1"; tries=${2:-6}; dev=${3:-${ANDROID_SERIAL:-emulator-5556}}
for i in $(seq 0 $tries); do
  adb -s $dev shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  xy=$(adb -s $dev shell cat /sdcard/u.xml | python3 -c "
import sys,re,html
want=sys.argv[1]; x=sys.stdin.read()
attr='content-desc' if want.startswith('desc:') else 'text'; w=want[5:] if want.startswith('desc:') else want
for m in re.finditer(r'<node [^>]*>',x):
    n=m.group(0); v=re.search(attr+r'=\"([^\"]*)\"',n)
    if v and html.unescape(v.group(1)).strip()==w:
        b=list(map(int,re.findall(r'\d+',re.search(r'bounds=\"([^\"]*)\"',n).group(1))))
        print((b[0]+b[2])//2,(b[1]+b[3])//2); break
" "$want")
  if [ -n "$xy" ]; then adb -s $dev shell input tap ${=xy}; echo "tapped $want at $xy"; exit 0; fi
  adb -s $dev shell input swipe 60 1700 60 900 400
done
echo "NOT FOUND: $want"; exit 1
