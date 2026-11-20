"""Renders the Turbo64 icon files with headless Chrome. Run: python3 make_icon.py"""
import subprocess, pathlib
HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE.parent
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
FONT = (HERE / "outfit.ttf").as_uri()
R, G, B, Y = "#F04A5A", "#2CC98A", "#3D7BFF", "#F8C43A"

def cube(left="#1D2533", right="#121823", text="#F5F7FA", mono=False):
    q = [R, B, Y, G] if not mono else ["#FFFFFF"] * 4
    if mono:
        left = right = "#FFFFFF"
    gap = ""
    return f'''<path d="M25 40 L60 60 V100 L25 80 Z" fill="{left}" {'opacity=".45"' if mono else ''}/>
<path d="M60 60 L95 40 V80 L60 100 Z" fill="{right}" {'opacity=".25"' if mono else ''}/>
<path d="M60 20 L77.5 30 L60 40 L42.5 30 Z" fill="{q[0]}"{gap}/>
<path d="M77.5 30 L95 40 L77.5 50 L60 40 Z" fill="{q[1]}"{gap}/>
<path d="M60 40 L77.5 50 L60 60 L42.5 50 Z" fill="{q[2]}"{gap}/>
<path d="M42.5 30 L60 40 L42.5 50 L25 40 Z" fill="{q[3]}"{gap}/>
<text x="42.5" y="54.5" text-anchor="middle" font-family="Outfit" font-weight="700" font-size="25" fill="{'#FFFFFF' if mono else text}" transform="matrix(1,0.571,0,1,0,0)">64</text>'''

def page(size, bg, scale, inner, radius=0):
    k = size / 512
    return f'''<html><head><style>@font-face{{font-family:Outfit;src:url({FONT});font-weight:100 900}}
html,body{{margin:0;background:transparent}}</style></head><body>
<svg width="{size}" height="{size}" viewBox="0 0 512 512" xmlns="http://www.w3.org/2000/svg">
{f'<rect width="512" height="512" rx="{radius}" fill="{bg}"/>' if bg else ''}
<g transform="translate(256,256) scale({scale}) translate(-60,-60)">{inner}</g></svg></body></html>'''

def shot(name, html, size):
    f = HERE / f"_{name}.html"; f.write_text(html)
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--default-background-color=00000000",
                    f"--window-size={size},{size}", "--virtual-time-budget=3000", f"--screenshot={OUT / (name + '.png')}", f.as_uri()],
                   check=True, capture_output=True)
    f.unlink()

BG = "#E9EDF4"
if __name__ == "__main__":
    import sys
    for tag, bg in (("a", "#E9EDF4"), ("b", "#CBD5E8"), ("c", "#34415C")) if "compare" in sys.argv else ():
        shot(f"compare-{tag}", page(512, bg, 4.0, cube(), radius=115), 512)
    if "compare" not in sys.argv:
        shot("app-icon-512", page(512, BG, 4.4, cube()), 512)                    # Play Store (square, Play rounds it)
        shot("preview-rounded-512", page(512, BG, 4.4, cube(), radius=115), 512)  # how it looks on a phone
        shot("ic_launcher_foreground-432", page(432, None, 2.85, cube()), 432)    # adaptive foreground (safe zone)
        shot("ic_launcher_monochrome-432", page(432, None, 2.85, cube(mono=True)), 432)
