"""Turbo64 Play Store screenshots and feature graphic.

Phone mockups of the real Turbo64 screens (home, library, player, pause menu), built as HTML
and rendered to PNG with headless Chrome. The game pictures are original low-poly 3D scenes
rendered from scene-adventure.html and scene-racing.html (no real game is shown).
Run: python3 make.py   (from this folder)
"""
import pathlib, subprocess

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE.parent
APP = HERE.parents[2]
FONT = (APP / "app/src/main/res/font/outfit.ttf").as_uri()
ICON = (HERE.parents[1] / "icon/app-icon-512.png").as_uri()
R = lambda name: (HERE / "renders" / f"{name}.png").as_uri()
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

# The app's own colours (ui/Theme.kt).
D = dict(bg="#1F2930", ink="#1A2228", surface="#27333B", raised="#31404A", stroke="#3D4D58", accent="#2CC98A",
         on="#04231A", text="#F1F5F7", muted="#B7C3CB", faint="#8C9BA5")
L = dict(bg="#EEF1F4", ink="#FFFFFF", surface="#FFFFFF", raised="#E3E8ED", stroke="#D0D8DF", accent="#0B8A5B",
         on="#FFFFFF", text="#18222A", muted="#45535E", faint="#62717C")
BLUE, GREEN, YELLOW, RED = "#3D7BFF", "#2CC98A", "#F8C43A", "#F04A5A"

# Original games shown in the library: title, picture, how far the picture is shifted.
GAMES = [
    ("Sky Castle Adventure", "adventure", "50% 60%", "2 h 14 min · Just now"),
    ("Desert Kart Rally", "racing", "50% 55%", "48 min · Today"),
    ("Twilight Kingdom", "adv-dusk", "50% 50%", "1 h 05 min · Yesterday"),
    ("Mesa Grand Prix", "race-side", "50% 50%", "25 min · 2 days ago"),
    ("Fortress Run", "adv-castle", "50% 50%", "3 h 40 min · Last week"),
    ("Canyon Circuit", "race-high", "50% 50%", "Not played yet"),
]

# ---------- icons (24 grid, rounded strokes, like Material Rounded) ----------
P = {
 "home": '<path d="M3.5 10.5 12 4l8.5 6.5V19a1.5 1.5 0 0 1-1.5 1.5h-4.5V15h-5v5.5H5A1.5 1.5 0 0 1 3.5 19z"/>',
 "grid": '<rect x="4" y="4" width="7" height="7" rx="2"/><rect x="13" y="4" width="7" height="7" rx="2"/><rect x="4" y="13" width="7" height="7" rx="2"/><rect x="13" y="13" width="7" height="7" rx="2"/>',
 "gear": '<circle cx="12" cy="12" r="3"/><path d="M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7"/>',
 "search": '<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/>',
 "plus": '<path d="M12 5v14M5 12h14"/>',
 "menu": '<path d="M4 7h16M4 12h16M4 17h16"/>',
 "save": '<path d="M5 4h11l3 3v12a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1z"/><path d="M8 4v5h7V4M8 20v-6h8v6"/>',
 "restore": '<path d="M4.5 12a7.5 7.5 0 1 0 2.2-5.3"/><path d="M4.5 4.5v4h4"/><path d="M12 8v4l3 2"/>',
 "rotate": '<path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3"/><path d="M19.5 4.5v4h-4"/>',
 "eyeoff": '<path d="M3 3l18 18"/><path d="M10.6 6.1A9.8 9.8 0 0 1 12 6c5 0 8.5 4.5 9 6-.3.8-1.2 2.4-2.8 3.8M6.5 7.6C4.6 8.9 3.4 10.9 3 12c.5 1.5 4 6 9 6a9.6 9.6 0 0 0 4-.9"/><path d="M9.9 10a3 3 0 0 0 4.1 4.1"/>',
 "swap": '<path d="M7 7h11l-3-3M17 17H6l3 3"/>',
 "sort": '<path d="M4 7h16M7 12h10M10 17h4"/>',
 "more": '<circle cx="6" cy="12" r="1.2"/><circle cx="12" cy="12" r="1.2"/><circle cx="18" cy="12" r="1.2"/>',
 "pad": '<rect x="2.5" y="7" width="19" height="11" rx="5.5"/><path d="M7.5 10.5v4M5.5 12.5h4"/><circle cx="16" cy="11.5" r="0.6"/><circle cx="18" cy="13.5" r="0.6"/>',
 "exit": '<path d="M14 4h4a1.5 1.5 0 0 1 1.5 1.5v13A1.5 1.5 0 0 1 18 20h-4"/><path d="M10 8l-4 4 4 4M6 12h9"/>',
 "edit": '<path d="M4 20h4L19 9l-4-4L4 16z"/>',
 "restart": '<path d="M5 12a7 7 0 1 0 2-4.9"/><path d="M5 4v4h4"/>',
 "code": '<path d="m8.5 8-4 4 4 4M15.5 8l4 4-4 4"/>',
}
FILLED = {"play": '<path d="M8 5.5v13l10.5-6.5z"/>', "ff": '<path d="M3.5 6v12l8.5-6zM12 6v12l8.5-6z"/>',
          "heart": '<path d="M12 20s-7.5-4.5-7.5-10A4 4 0 0 1 12 7.5 4 4 0 0 1 19.5 10C19.5 15.5 12 20 12 20z"/>'}
def ic(name, size, color, sw=2.0):
    if name in FILLED:
        return f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="{color}">{FILLED[name]}</svg>'
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" stroke="{color}" stroke-width="{sw}" '
            f'stroke-linecap="round" stroke-linejoin="round">{P[name]}</svg>')

def pic(name, pos="50% 50%", radius=0, extra=""):
    return (f'<div style="position:absolute;inset:0;border-radius:{radius}px;background:url({R(name)}) {pos}/cover;{extra}"></div>')

# ---------- app UI (dp units; the phone frame scales them) ----------
def statusbar(t, light_icons=None):
    c = light_icons or t["text"]
    return (f'<div style="height:30px;display:flex;align-items:center;padding:0 22px;font-size:13px;font-weight:600;color:{c}">10:54'
            f'<div style="margin-left:auto;display:flex;gap:6px;align-items:center">'
            f'<svg width="15" height="11" viewBox="0 0 15 11"><path d="M1 10h2V7H1zM5 10h2V5H5zM9 10h2V3H9zM13 10h2V1h-2z" fill="{c}"/></svg>'
            f'<div style="width:22px;height:11px;border-radius:3px;border:1.5px solid {c};padding:1.5px;box-sizing:border-box"><div style="width:72%;height:100%;background:{c};border-radius:1px"></div></div></div></div>')

def topbar(t, title=None):
    left = (f'<div style="display:flex;align-items:center;gap:10px"><img src="{ICON}" style="width:30px;height:30px;border-radius:8px">'
            f'<span style="font-size:20px;font-weight:700;color:{t["text"]};letter-spacing:-.2px">Turbo<span style="color:{t["accent"]}">64</span></span></div>'
            if title is None else f'<div style="font-size:24px;font-weight:700;color:{t["text"]}">{title}</div>')
    return (f'<div style="height:58px;display:flex;align-items:center;padding:0 16px">{left}'
            f'<div style="margin-left:auto;height:38px;border-radius:19px;background:{t["raised"]};color:{t["text"]};display:flex;align-items:center;gap:6px;padding:0 15px 0 11px;font-size:14px;font-weight:600">{ic("plus",18,t["text"],2.2)}Add games</div></div>')

def bottombar(t, sel):
    cells = ""
    for i, (icn, lab) in enumerate([("home", "Home"), ("grid", "Library"), ("gear", "Settings")]):
        on = i == sel
        cells += (f'<div style="display:flex;flex-direction:column;align-items:center;gap:3px">'
                  f'<div style="width:58px;height:30px;border-radius:15px;display:flex;align-items:center;justify-content:center;background:{t["accent"]+"2E" if on else "transparent"}">{ic(icn,22,t["accent"] if on else t["faint"])}</div>'
                  f'<div style="font-size:12.5px;font-weight:600;color:{t["text"] if on else t["faint"]}">{lab}</div></div>')
    return (f'<div style="position:absolute;left:0;right:0;bottom:0;height:84px;background:{t["ink"]};border-top:1px solid {t["stroke"]};'
            f'display:flex;justify-content:space-evenly;padding-top:10px;box-sizing:border-box">{cells}</div>')

def card(t, i, w, info=None, more=False, fav=False):
    title, img, pos, line = GAMES[i]
    h = w * 3 / 4
    badge = lambda inner, where: (f'<div style="position:absolute;{where};width:28px;height:28px;border-radius:14px;background:rgba(0,0,0,.45);'
                                  f'display:flex;align-items:center;justify-content:center">{inner}</div>')
    return (f'<div style="width:{w}px;flex:none"><div style="position:relative;width:{w}px;height:{h}px;border-radius:16px;overflow:hidden">{pic(img,pos)}'
            + (badge(ic("heart", 14, "#fff"), "right:7px;top:7px") if fav else "")
            + (badge(ic("more", 18, "#fff", 2.6), "right:7px;bottom:7px") if more else "")
            + f'</div><div style="font-size:15px;font-weight:600;color:{t["text"]};margin-top:9px;line-height:19px">{title}</div>'
              f'<div style="font-size:12.5px;color:{t["faint"]};margin-top:2px">{info or line}</div></div>')

def section(t, title):
    return (f'<div style="display:flex;align-items:center;margin:26px 16px 12px"><div style="font-size:19px;font-weight:700;color:{t["text"]}">{title}</div>'
            f'<div style="margin-left:auto;font-size:13px;font-weight:600;color:{t["muted"]}">See all</div></div>')

def home_ui(t):
    title, img, pos, _ = GAMES[0]
    hero = (f'<div style="margin:4px 16px 0;border-radius:20px;background:{t["surface"]};overflow:hidden;'
            f'box-shadow:{"0 3px 10px rgba(20,30,40,.10)" if t is L else "none"}">'
            f'<div style="position:relative;height:{328*9/16}px">{pic(img,"50% 62%")}</div>'
            f'<div style="display:flex;align-items:center;padding:13px 12px 13px 16px"><div style="flex:1">'
            f'<div style="font-size:19px;font-weight:700;color:{t["text"]};line-height:23px">{title}</div>'
            f'<div style="font-size:12.5px;color:{t["muted"]};margin-top:3px">2 h 14 min · Just now</div></div>'
            f'<div style="height:44px;border-radius:14px;background:{t["accent"]};color:{t["on"]};display:flex;align-items:center;gap:6px;padding:0 17px;font-size:14.5px;font-weight:700">{ic("play",20,t["on"])}Continue</div></div></div>')
    shelf = lambda items: f'<div style="display:flex;gap:12px;padding-left:16px;overflow:hidden">{"".join(card(t,i,152,s) for i,s in items)}</div>'
    return (statusbar(t) + topbar(t) + hero
            + section(t, "Recently played") + shelf([(1, "48 min"), (2, "1 h 05 min"), (3, "25 min")])
            + section(t, "Recently added") + shelf([(4, "Europe · Not played yet"), (5, "USA · Not played yet"), (2, "USA")])
            + bottombar(t, 0))

def library_ui(t):
    chips = "".join(f'<div style="height:34px;border-radius:17px;padding:0 15px;display:flex;align-items:center;font-size:13.5px;font-weight:600;'
                    + (f'background:{t["accent"]};color:{t["on"]}' if k == 0 else f'background:{t["surface"]};color:{t["text"]};border:1px solid {t["stroke"]}')
                    + f'">{c}</div>' for k, c in enumerate(["All 24", "Recent", "Favourites"]))
    grid = "".join(card(t, k, 158, more=True, fav=k in (0, 4)) for k in range(6))
    return (statusbar(t) + topbar(t, "Library")
            + f'<div style="margin:2px 16px 0;height:46px;border-radius:14px;background:{t["surface"]};border:1px solid {t["stroke"]};display:flex;align-items:center;gap:10px;padding:0 14px;font-size:14.5px;color:{t["faint"]}">{ic("search",19,t["muted"])}Search your games</div>'
            + f'<div style="display:flex;gap:8px;margin:14px 16px 16px;align-items:center">{chips}<div style="margin-left:auto">{ic("sort",22,t["text"])}</div></div>'
            + f'<div style="display:grid;grid-template-columns:158px 158px;gap:18px 12px;padding:0 16px">{grid}</div>' + bottombar(t, 1))

# ---------- on-screen controls (as in player/PlayerControls.kt) ----------
GLASS = "rgba(20,26,32,.55)"
EDGE = "rgba(255,255,255,.22)"
def glass(icn, size=34, active=False, badge=None):
    bg = YELLOW if active else GLASS
    fg = "#1C1500" if active else "#fff"
    b = (f'<div style="position:absolute;right:-6px;top:-6px;padding:1px 5px;border-radius:8px;background:#fff;color:#111;font-size:10px;font-weight:800">{badge}</div>' if badge else "")
    return (f'<div style="position:relative;width:{size}px;height:{size}px;border-radius:{size/2}px;background:{bg};border:1px solid {EDGE};'
            f'display:flex;align-items:center;justify-content:center;backdrop-filter:blur(6px)">{ic(icn,size*0.5,fg)}{b}</div>')

def pill(label, w, h, x, y, size=13, color="#fff"):
    return (f'<div style="position:absolute;left:{x}px;top:{y}px;width:{w}px;height:{h}px;border-radius:{h/2}px;background:{GLASS};border:1.5px solid {EDGE};'
            f'display:flex;align-items:center;justify-content:center;color:{color};font-size:{size}px;font-weight:700;letter-spacing:.6px">{label}</div>')

def round_btn(label, d, x, y, color, pressed=False):
    """A face button: a coloured ring and letter on dark glass; pressed ones fill with their colour."""
    fill = color if pressed else GLASS
    halo = f'box-shadow:0 0 0 7px {color}33;' if pressed else ""
    return (f'<div style="position:absolute;left:{x-d/2}px;top:{y-d/2}px;width:{d}px;height:{d}px;border-radius:50%;background:{fill};'
            f'border:2.5px solid {color};{halo}display:flex;align-items:center;justify-content:center;'
            f'color:{"#fff" if pressed else color};font-size:{d*0.38}px;font-weight:800">{label}</div>')

def c_button(d, x, y, rot):
    return (f'<div style="position:absolute;left:{x-d/2}px;top:{y-d/2}px;width:{d}px;height:{d}px;border-radius:50%;background:{GLASS};border:2px solid {YELLOW};'
            f'display:flex;align-items:center;justify-content:center"><svg width="{d*0.42}" height="{d*0.42}" viewBox="0 0 10 10" style="transform:rotate({rot}deg)">'
            f'<path d="M5 1.5 9 8H1z" fill="{YELLOW}"/></svg></div>')

def stick(x, y, base=132, knob=58, dx=14, dy=-10):
    return (f'<div style="position:absolute;left:{x-base/2}px;top:{y-base/2}px;width:{base}px;height:{base}px;border-radius:50%;background:rgba(20,26,32,.42);border:1.5px solid {EDGE}"></div>'
            f'<div style="position:absolute;left:{x-base/2+10}px;top:{y-base/2+10}px;width:{base-20}px;height:{base-20}px;border-radius:50%;border:1px dashed rgba(255,255,255,.12)"></div>'
            f'<div style="position:absolute;left:{x-knob/2+dx}px;top:{y-knob/2+dy}px;width:{knob}px;height:{knob}px;border-radius:50%;'
            f'background:radial-gradient(circle at 38% 32%,#5A6872 0%,#36424B 55%,#273139 100%);border:1.5px solid rgba(255,255,255,.30);box-shadow:0 6px 14px rgba(0,0,0,.45)"></div>')

def hud_adventure():
    return ('<div style="position:absolute;left:16px;top:60px;display:flex;gap:5px">'
            + "".join(f'<svg width="22" height="20" viewBox="0 0 24 22"><path d="M12 21S1.5 14.5 1.5 7.5A5.5 5.5 0 0 1 12 4.5a5.5 5.5 0 0 1 10.5 3C22.5 14.5 12 21 12 21z" fill="{c}" stroke="#3A0B12" stroke-width="2"/></svg>' for c in ["#F04A5A"] * 3 + ["#5A2A30"])
            + '</div><div style="position:absolute;right:16px;top:58px;display:flex;align-items:center;gap:6px;font:800 21px/1 monospace;color:#FFE27A;-webkit-text-stroke:1.2px #4A3200">'
              '<svg width="22" height="22" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="none" stroke="#F7C531" stroke-width="5"/></svg>× 34</div>')

def hud_racing():
    return ('<div style="position:absolute;left:16px;top:60px;font:800 22px/1 monospace;color:#fff;-webkit-text-stroke:1.3px #111">LAP 2/3</div>'
            '<div style="position:absolute;right:18px;top:56px;font:900 46px/1 monospace;color:#FFD54A;-webkit-text-stroke:2px #5A3A00">1<span style="font-size:24px">st</span></div>'
            '<div style="position:absolute;left:16px;bottom:14px;font:800 18px/1 monospace;color:#fff;-webkit-text-stroke:1.2px #111">0\'42"18</div>')

def player_land(w, scene="adventure", ff=False, notice=None, hud=None, pressed=None):
    """Landscape player, 360 dp tall: game in 4:3 centred, controls on the sides."""
    gw = 360 * 4 / 3
    gl = (w - gw) / 2
    quick = "".join([glass("menu"), glass("save"), glass("restore"), glass("rotate"),
                     glass("ff", active=ff, badge="3×" if ff else None), glass("eyeoff")])
    cx, cy, cd = w - 86, 132, 40
    cbtn = "".join(c_button(36, cx + dx * cd / 1.4, cy + dy * cd / 1.4, r) for dx, dy, r in [(0, -1, 0), (1, 0, 90), (0, 1, 180), (-1, 0, 270)])
    note = (f'<div style="position:absolute;left:50%;top:62px;transform:translateX(-50%);padding:9px 17px;border-radius:20px;background:rgba(26,34,40,.94);'
            f'border:1px solid {D["stroke"]};color:#F1F5F7;font-size:13.5px;font-weight:600;white-space:nowrap">{notice}</div>' if notice else "")
    return (f'<div style="position:absolute;inset:0;background:#000"></div>'
            f'<div style="position:absolute;left:{gl}px;top:0;width:{gw}px;height:360px;overflow:hidden">{pic(scene)}{(hud or "")}</div>'
            + pill("L", 76, 34, 16, 14) + pill("R", 76, 34, w - 92, 14)
            + f'<div style="position:absolute;left:50%;top:12px;transform:translateX(-50%);display:flex;gap:9px">{quick}</div>' + note
            + stick(96, 262)
            + f'<div style="position:absolute;left:170px;top:196px">{glass("swap", 32)}</div>'
            + cbtn
            + round_btn("A", 62, w - 96, 290, BLUE, pressed == "A") + round_btn("B", 50, w - 150, 238, GREEN, pressed == "B")
            + f'<div style="position:absolute;left:{w-236}px;top:282px;width:56px;height:44px;border-radius:13px;background:{GLASS};border:1.5px solid {EDGE};display:flex;align-items:center;justify-content:center;color:#fff;font-size:17px;font-weight:800">Z</div>'
            + pill("START", 72, 28, w / 2 - 36, 322, 11.5))

def player_portrait():
    """Portrait player: game on top, controls in the space below."""
    gh = 360 * 3 / 4
    w = 360
    cbtn = "".join(c_button(34, 290 + dx * 30, 470 + dy * 30, r) for dx, dy, r in [(0, -1, 0), (1, 0, 90), (0, 1, 180), (-1, 0, 270)])
    return (f'<div style="position:absolute;inset:0;background:#0E1316"></div>' + statusbar(D, "#fff")
            + f'<div style="position:absolute;left:0;top:30px;width:{w}px;height:{gh}px;overflow:hidden">{pic("racing")}{hud_racing()}</div>'
            + f'<div style="position:absolute;left:0;right:0;top:{30+gh+12}px;display:flex;justify-content:center;gap:9px">'
            + "".join([glass("menu", 32), glass("save", 32), glass("restore", 32), glass("rotate", 32), glass("ff", 32), glass("eyeoff", 32)]) + '</div>'
            + pill("L", 70, 32, 16, 368) + pill("R", 70, 32, w - 86, 368)
            + f'<div style="position:absolute;left:{w-86-62}px;top:368px;width:52px;height:32px;border-radius:11px;background:{GLASS};border:1.5px solid {EDGE};display:flex;align-items:center;justify-content:center;color:#fff;font-size:15px;font-weight:800">Z</div>'
            + stick(86, 560, 138, 60, -12, 16)
            + cbtn + round_btn("A", 64, 286, 640, BLUE, True) + round_btn("B", 52, 222, 590, GREEN)
            + pill("START", 74, 28, w / 2 - 37, 700, 11.5))

def pause_menu_portrait():
    t = D
    slots = ""
    shots = ["racing", "race-side", "race-high", None, None, None]
    times = ["Today 18:42", "Today 14:10", "Yesterday", "Empty", "Empty", "Empty"]
    for k in range(6):
        img = shots[k]
        thumb = (f'<div style="position:relative;height:74px">{pic(img)}</div>' if img else
                 f'<div style="height:74px;background:rgba(0,0,0,.35);display:flex;align-items:center;justify-content:center;color:{t["faint"]};font-size:11px">Empty</div>')
        btns = (f'<span style="padding:4px 11px;border-radius:12px;border:1px solid {t["stroke"]};font-size:11px;font-weight:600;color:{t["text"]}">Save</span>'
                + (f'<span style="padding:4px 11px;border-radius:12px;background:{t["accent"]};font-size:11px;font-weight:700;color:{t["on"]}">Load</span>' if img else ""))
        slots += (f'<div style="border-radius:14px;background:{t["raised"]};overflow:hidden">{thumb}<div style="padding:8px 10px 10px">'
                  f'<div style="font-size:12.5px;font-weight:700;color:{t["text"]}">Slot {k+1}</div><div style="font-size:10.5px;color:{t["muted"]};margin-top:2px">{times[k]}</div>'
                  f'<div style="display:flex;gap:6px;margin-top:7px">{btns}</div></div></div>')
    btn = lambda icn, lab, bg, fg: (f'<div style="flex:1;height:42px;border-radius:13px;background:{bg};color:{fg};display:flex;align-items:center;justify-content:center;gap:7px;font-size:13.5px;font-weight:700">{ic(icn,17,fg)}{lab}</div>')
    return (player_portrait() + '<div style="position:absolute;inset:0;background:rgba(0,0,0,.78)"></div>'
            f'<div style="position:absolute;left:14px;right:14px;top:70px;border-radius:24px;background:{t["surface"]};border:1px solid {t["stroke"]};padding:18px;display:flex;flex-direction:column;gap:12px">'
            f'<div style="font-size:17px;font-weight:700;color:{t["text"]}">Desert Kart Rally</div>'
            f'<div style="height:46px;border-radius:14px;background:{t["accent"]};color:{t["on"]};display:flex;align-items:center;justify-content:center;gap:7px;font-size:15px;font-weight:700">{ic("play",20,t["on"])}Resume</div>'
            f'<div style="display:flex;gap:8px">{btn("save","Quick save",t["raised"],t["text"])}{btn("restore","Quick load",t["raised"],t["text"])}</div>'
            f'<div style="font-size:13px;font-weight:600;color:{t["muted"]};margin-top:2px">Save slots</div>'
            f'<div style="display:grid;grid-template-columns:1fr 1fr;gap:9px">{slots}</div>'
            f'<div style="display:flex;gap:8px">{btn("code","Cheats",t["raised"],t["text"])}{btn("exit","Save & exit",t["raised"],t["text"])}</div></div>')

def settings_land(w):
    t = D
    base = player_land(w, "adv-dusk") + '<div style="position:absolute;inset:0;background:rgba(0,0,0,.78)"></div>'
    pills = lambda opts, on: "".join(f'<span style="padding:7px 13px;border-radius:15px;font-size:12px;font-weight:600;'
                                     + (f'background:{t["accent"]};color:{t["on"]}' if o == on else f'background:{t["raised"]};color:{t["text"]}') + f'">{o}</span>' for o in opts)
    def slider(lab, val, f):
        return (f'<div><div style="display:flex;font-size:12px;font-weight:600;color:{t["muted"]}">{lab}<span style="margin-left:auto;color:{t["text"]}">{val}</span></div>'
                f'<div style="height:4px;border-radius:2px;background:{t["raised"]};margin-top:10px;position:relative"><div style="width:{f}%;height:100%;border-radius:2px;background:{t["accent"]}"></div>'
                f'<div style="position:absolute;left:calc({f}% - 8px);top:-6px;width:16px;height:16px;border-radius:8px;background:{t["accent"]}"></div></div></div>')
    def sw(lab, on):
        return (f'<div style="display:flex;align-items:center;font-size:12.5px;color:{t["text"]};gap:10px">{lab}<div style="margin-left:auto;flex:none;width:38px;height:22px;border-radius:11px;background:{t["accent"] if on else t["raised"]};position:relative">'
                f'<div style="position:absolute;top:3px;{"right" if on else "left"}:3px;width:16px;height:16px;border-radius:8px;background:{t["on"] if on else t["muted"]}"></div></div></div>')
    left = (f'<div style="font-size:15px;font-weight:700;color:{t["text"]}">Picture</div>'
            f'<div style="font-size:12px;font-weight:600;color:{t["muted"]}">Shape</div><div style="display:flex;gap:6px">{pills(["4:3","16:9","Fill"],"16:9")}</div>'
            f'<div style="font-size:12px;font-weight:600;color:{t["muted"]}">Quality</div><div style="display:flex;gap:6px">{pills(["1×","2×","3×"],"2×")}</div>'
            + slider("Brightness", "100%", 50) + sw("Show the whole picture", False))
    right = (f'<div style="font-size:15px;font-weight:700;color:{t["text"]}">Controls</div>'
             f'<div style="display:flex;gap:8px"><div style="flex:1;height:36px;border-radius:12px;background:{t["raised"]};color:{t["text"]};display:flex;align-items:center;justify-content:center;gap:6px;font-size:12.5px;font-weight:700">{ic("edit",15,t["text"])}Edit layout</div>'
             f'<div style="flex:1;height:36px;border-radius:12px;background:{t["raised"]};color:{t["text"]};display:flex;align-items:center;justify-content:center;gap:6px;font-size:12.5px;font-weight:700">{ic("pad",17,t["text"])}Controller</div></div>'
             + slider("Controls size", "110%", 62) + slider("Controls opacity", "65%", 55)
             + f'<div style="font-size:12px;font-weight:600;color:{t["muted"]}">Left control (this game)</div><div style="display:flex;gap:6px">{pills(["Stick","D-pad","Both"],"Stick")}</div>'
             + sw("C buttons as a second stick", False))
    return (base + f'<div style="position:absolute;left:{(w-700)/2}px;top:16px;width:700px;height:328px;border-radius:24px;background:{t["surface"]};border:1px solid {t["stroke"]};display:flex;overflow:hidden">'
            f'<div style="flex:1;padding:18px;display:flex;flex-direction:column;gap:11px">{left}</div><div style="width:1px;background:{t["stroke"]};margin:18px 0"></div>'
            f'<div style="flex:1;padding:18px;display:flex;flex-direction:column;gap:11px">{right}</div></div>')

# ---------- device frames and canvases ----------
CSS = (f"<style>@font-face{{font-family:Outfit;src:url('{FONT}');font-weight:100 900}}*{{margin:0;padding:0;box-sizing:border-box}}"
       "body{font-family:Outfit,sans-serif;-webkit-font-smoothing:antialiased}</style>")

def phone_portrait(ui, t, x, y, w=720, h=1520):
    bez = 14; sw = w - 2 * bez; sh = h - 2 * bez; s = sw / 360
    return (f'<div style="position:absolute;left:{x}px;top:{y}px;width:{w}px;height:{h}px;border-radius:88px;'
            f'background:linear-gradient(145deg,#3A444C,#151A1E 40%,#0B0E10);box-shadow:0 50px 110px rgba(0,0,0,.55),inset 0 0 0 2px rgba(255,255,255,.08)">'
            f'<div style="position:absolute;left:{bez}px;top:{bez}px;width:{sw}px;height:{sh}px;border-radius:76px;overflow:hidden;background:{t["bg"]}">'
            f'<div style="position:absolute;left:0;top:0;width:360px;height:{sh/s}px;transform:scale({s});transform-origin:0 0;background:{t["bg"]}">{ui}</div></div>'
            f'<div style="position:absolute;left:50%;top:30px;width:24px;height:24px;margin-left:-12px;border-radius:12px;background:#05070A;box-shadow:inset 0 0 0 3px #1B2228"></div></div>')

def phone_land(ui_fn, x, y, w=1560, h=730):
    bez = 14; sw = w - 2 * bez; sh = h - 2 * bez; s = sh / 360; uw = sw / s
    return (f'<div style="position:absolute;left:{x}px;top:{y}px;width:{w}px;height:{h}px;border-radius:86px;'
            f'background:linear-gradient(145deg,#3A444C,#151A1E 40%,#0B0E10);box-shadow:0 50px 110px rgba(0,0,0,.55),inset 0 0 0 2px rgba(255,255,255,.08)">'
            f'<div style="position:absolute;left:{bez}px;top:{bez}px;width:{sw}px;height:{sh}px;border-radius:74px;overflow:hidden;background:#000">'
            f'<div style="position:absolute;left:0;top:0;width:{uw}px;height:360px;transform:scale({s});transform-origin:0 0">{ui_fn(uw)}</div></div>'
            f'<div style="position:absolute;top:50%;left:30px;width:24px;height:24px;margin-top:-12px;border-radius:12px;background:#05070A;box-shadow:inset 0 0 0 3px #1B2228"></div></div>')

BG = "#121A1F"
def backdrop(w, h):
    # The icon's four colours as one quiet stripe along the bottom edge, nothing else.
    q = w / 4
    return "".join(f'<div style="position:absolute;left:{k*q}px;bottom:0;width:{q}px;height:10px;background:{c}"></div>' for k, c in enumerate([RED, BLUE, YELLOW, GREEN]))

def title(text, sub, size=92, align="center"):
    return (f'<div style="text-align:{align}"><div style="font-size:{size}px;line-height:1.04;font-weight:800;color:#F4F7F9;letter-spacing:-2px">{text}</div>'
            f'<div style="font-size:38px;color:#A9B7C0;margin-top:22px;font-weight:500">{sub}</div></div>')

def canvas(w, h, body):
    return (f'<!doctype html><html><head><meta charset="utf-8">{CSS}</head><body>'
            f'<div style="position:relative;width:{w}px;height:{h}px;overflow:hidden;background:{BG}">{body}{backdrop(w,h)}</div></body></html>')

def portrait_shot(head, sub, ui, t=D):
    return canvas(1080, 1920, f'<div style="position:absolute;left:60px;right:60px;top:120px">{title(head, sub)}</div>' + phone_portrait(ui, t, 210, 450, 660, 1394))

def land_shot(head, sub, ui_fn):
    return canvas(1920, 1080, f'<div style="position:absolute;left:0;right:0;top:58px">{title(head, sub, 74)}</div>' + phone_land(ui_fn, 220, 318, 1480, 692))

shots = {
    "01-gameplay": land_shot("Your games, full speed", "Smooth 3D, sound and saves on your phone",
                         lambda w: player_land(w, "adventure", hud=hud_adventure(), pressed="A")),
    "03-home": portrait_shot("Pick up where<br>you left off", "Your last game is one tap away", home_ui(D)),
    "02-fast-forward": land_shot("Fast-forward when you want", "Up to 4× speed, one tap",
                           lambda w: player_land(w, "racing", ff=True, notice="Fast-forward 3×", hud=hud_racing())),
    "05-library": portrait_shot("Your whole<br>library", "Real game names, search and favourites", library_ui(D)),
    "04-saves": portrait_shot("Save anywhere", "10 slots with pictures, plus auto-save", pause_menu_portrait()),
    "07-settings": land_shot("Make it yours", "Picture, controls and layout, per game", settings_land),
    "06-portrait-play": portrait_shot("Play upright too", "Landscape, portrait or auto", player_portrait()),
    "08-light-theme": portrait_shot("Light or dark", "Follows your phone", home_ui(L), t=L),
}

# Feature graphic: everything that matters sits in the middle, so nothing is lost when Play
# crops the edges or lays its own text and buttons over the sides and bottom.
feature = (f'<!doctype html><html><head><meta charset="utf-8">{CSS}</head><body>'
    f'<div style="position:relative;width:1024px;height:500px;overflow:hidden;background:{BG}">'
    # Game scenes fill the edges (fine to crop), darkened so the centre reads clearly.
    f'<div style="position:absolute;left:0;top:0;width:512px;height:500px;background:url({R("adventure")}) 40% 60%/cover"></div>'
    f'<div style="position:absolute;left:512px;top:0;width:512px;height:500px;background:url({R("racing")}) 60% 60%/cover"></div>'
    f'<div style="position:absolute;inset:0;background:rgba(12,17,21,.84)"></div>'
    f'<div style="position:absolute;left:0;right:0;top:118px;display:flex;flex-direction:column;align-items:center">'
    f'<img src="{ICON}" style="width:112px;height:112px;border-radius:26px;box-shadow:0 14px 34px rgba(0,0,0,.5)">'
    f'<div style="font-size:72px;font-weight:800;color:#F4F7F9;margin-top:22px;letter-spacing:-1.8px;line-height:1">Turbo<span style="color:{GREEN}">64</span></div>'
    f'<div style="font-size:24px;color:#C2CDD4;margin-top:12px;font-weight:500">Retro 64 emulator</div></div>'
    f'{backdrop(1024, 500)}</div></body></html>')

def render(name, html, w, h):
    src = HERE / f"{name}.html"
    src.write_text(html)
    out = (OUT / "screenshots" if name[0].isdigit() else OUT) / f"{name}.png"
    subprocess.run([CHROME, "--headless=new", "--allow-file-access-from-files", "--disable-gpu", "--hide-scrollbars",
                    "--force-device-scale-factor=1", f"--window-size={w},{h}", "--virtual-time-budget=4000",
                    f"--screenshot={out}", src.as_uri()], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print(out.name)

if __name__ == "__main__":
    for name, html in shots.items():
        land = name in ("01-gameplay", "02-fast-forward", "07-settings")
        render(name, html, 1920 if land else 1080, 1080 if land else 1920)
    render("feature-graphic-1024x500", feature, 1024, 500)
