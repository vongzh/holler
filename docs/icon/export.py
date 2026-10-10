# -*- coding: utf-8 -*-
"""
Holler 启动图标 · 资产生成器 v2
====================================
v2 变更：对齐领克 08 Flyme Auto 实机图标语言（docs/icon/ref/ 实拍佐证）
    · 横向长型圆角块（实测 ≈1.79:1），非方形深底
    · 两种形制：系统绿块 + 白色实心图形 ／ 白块 + 彩色实心图形
    · 图形一律实心扁平；描线仅属 DOCK 栏语言
    · 内容收敛在中央横向带（y 24–84），方形/长型遮罩均不裁切

单一数据源派生：svg/ + png/ + sheet.html
用法：python export.py [--no-png]
"""

import argparse
import os
import shutil
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.abspath(__file__))
SVG_DIR = os.path.join(ROOT, "svg")
PNG_DIR = os.path.join(ROOT, "png")
TMP_DIR = os.path.join(ROOT, "_tmp")

CHROME_CANDIDATES = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
]

# ----------------------------------------------------------------------------
# 1. 令牌
# ----------------------------------------------------------------------------
# Flyme Auto 实测（新出行 XCX-Test 领克 08 实拍聚类取色）
FLYME_GREEN = "#12D538"      # 系统应用绿块（座椅/电话/能量中心）
FLYME_BLUE = "#3387E3"       # 地图箭头 / 应用商店蓝
# Holler v2（青绿家族，与运行时令牌同源）
H_GREEN_DEEP = "#0FBF76"     # 白底图形主色（对白 ≈3.1:1）
H_GREEN_MID = "#1AE5A3"      # Accent（ui/theme/Theme.kt DarkHollerColors）
H_GREEN_TILE_A = "#33E89C"   # 绿块渐变上
H_GREEN_TILE_B = "#0FC77A"   # 绿块渐变下
H_SLATE = "#26313D"          # 白底车形深蓝灰
WHITE_TILE_A = "#FFFFFF"
WHITE_TILE_B = "#EFF3F7"

# ----------------------------------------------------------------------------
# 2. 图块底
# ----------------------------------------------------------------------------
TILE_DEFS = """  <defs>
    <linearGradient id="{uid}" x1="0" y1="0" x2="0.18" y2="1">
      <stop offset="0" stop-color="{a}"/>
      <stop offset="1" stop-color="{b}"/>
    </linearGradient>
  </defs>"""


def tile_defs(uid, a, b):
    return TILE_DEFS.format(uid=uid, a=a, b=b)


def tile_body(fill_id):
    return '  <rect width="108" height="108" fill="url(%s)"/>' % fill_id

# ----------------------------------------------------------------------------
# 3. 图形（全部实心扁平，内容带 y 24–84）
# ----------------------------------------------------------------------------

# 实心喇叭：喇叭体 + 手柄（同色融合）+ 两道声弧
def mega(body, a1, o1, a2, o2):
    return """  <g transform="translate(1.7,-1.75)">
    <path d="M32,46 L55.5,35.8 Q61,34 61,39 L61,69 Q61,74 55.5,72.2 L32,62 A8,8 0 0 1 32,46 Z" fill="{body}"/>
    <path d="M33,60 h8 a3.5,3.5 0 0 1 3.5,3.5 v11 a3.5,3.5 0 0 1 -3.5,3.5 h-8 a3.5,3.5 0 0 1 -3.5,-3.5 v-11 a3.5,3.5 0 0 1 3.5,-3.5 z" fill="{body}"/>
    <path d="M69.90,47.53 A11,11 0 0 1 69.90,60.47" stroke="{a1}" stroke-width="6" fill="none" stroke-linecap="round" opacity="{o1}"/>
    <path d="M77.58,41.95 A20.5,20.5 0 0 1 77.58,66.05" stroke="{a2}" stroke-width="6" fill="none" stroke-linecap="round" opacity="{o2}"/>
  </g>""".format(body=body, a1=a1, o1=o1, a2=a2, o2=o2)


# 白底车形：深蓝灰车侧剪影（evenOdd 挖窗/轮拱）+ 青绿声波
GLYPH_CAR = """  <g transform="translate(2.5,-1)">
    <path fill-rule="evenodd" fill="{car}" d="M22,75 L22,66.5 C22,63.6 23.8,61.9 26.6,61.5 L38,60 L45,54.4
      C46,53.5 47.2,53.1 48.5,53.1 L58.5,53.1 C59.8,53.1 61,53.6 61.8,54.5 L68.3,60 L72.5,60.9
      C75.3,61.3 76.8,63 76.8,65.9 L76.8,75 Z
      M46.5,58.8 L61,58.8 L58.2,55.8 L49.3,55.8 Z
      M27.4,75 A6.6,6.6 0 0 1 40.6,75 Z
      M57.4,75 A6.6,6.6 0 0 1 70.6,75 Z"/>
    <circle cx="34" cy="75" r="4.6" fill="{car}"/>
    <circle cx="64" cy="75" r="4.6" fill="{car}"/>
    <g fill="none" stroke-linecap="round" stroke-width="5.2">
      <path d="M56.26,48.15 A10,10 0 0 1 66.19,52.26" stroke="{a1}" opacity="1"/>
      <path d="M54.70,39.29 A19,19 0 0 1 73.56,47.10" stroke="{a2}" opacity="0.9"/>
      <path d="M53.14,30.43 A28,28 0 0 1 80.93,41.94" stroke="{a2}" opacity="0.55"/>
    </g>
  </g>""".format(car=H_SLATE, a1=H_GREEN_DEEP, a2="#5FDCAF")

# 单色层（状态栏 / 主题化图标）
MONO = """  <g transform="translate(54,54) scale(0.84) translate(-54,-54)" fill="none"
     stroke="#FFFFFF" stroke-linecap="round" stroke-linejoin="round">
    <path d="M32,46 L55.5,35.8 Q61,34 61,39 L61,69 Q61,74 55.5,72.2 L32,62 A8,8 0 0 1 32,46 Z"
          fill="#FFFFFF" stroke-width="1.5"/>
    <path d="M69.90,47.53 A11,11 0 0 1 69.90,60.47" stroke-width="6.5"/>
  </g>"""

ICONS = [
    {
        "key": "green-megaphone",
        "order": "A",
        "name": "翡翠声号",
        "en": "Green Megaphone",
        "tile": (H_GREEN_TILE_A, H_GREEN_TILE_B),
        "glyph": mega("#FFFFFF", "#FFFFFF", "1", "#FFFFFF", "0.6"),
        "mono": MONO,
        "semantics": "对位系统绿块（座椅 / 电话 / 能量中心）：绿底 + 白色实心喇叭",
        "verdict": "主推",
        "verdict_cls": "rec",
        "scores": [5, 5, 5, 4],
        "notes": "与系统功能图标同一形制，一眼归位「车机自带工具」；青绿块在一片系统绿/白之间既融又跳。",
    },
    {
        "key": "white-megaphone",
        "order": "B",
        "name": "白瓷声号",
        "en": "White Megaphone",
        "tile": (WHITE_TILE_A, WHITE_TILE_B),
        "glyph": mega(H_GREEN_DEEP, H_GREEN_MID, "1", H_GREEN_MID, "0.45"),
        "mono": None,
        "semantics": "对位白块彩图（车辆设置 / 地图 / 浏览器）：白底 + 青绿实心喇叭",
        "verdict": "备选",
        "verdict_cls": "alt",
        "scores": [5, 5, 4, 4],
        "notes": "白块更接近第三方应用的默认观感；深青绿 #0FBF76 保证对白 ≥3:1。",
    },
    {
        "key": "white-car",
        "order": "C",
        "name": "车语声波",
        "en": "Car & Waves",
        "tile": (WHITE_TILE_A, WHITE_TILE_B),
        "glyph": GLYPH_CAR,
        "mono": None,
        "semantics": "对位车辆设置白块车标：深蓝灰车侧 + 三道青绿外扩声波",
        "verdict": "语义直给",
        "verdict_cls": "brand",
        "scores": [4, 5, 4, 3],
        "notes": "「车 + 声」两个语义都画了出来；但车机桌面车辆类图标密集，需靠声波做区分度。",
    },
]

# ----------------------------------------------------------------------------
# 4. SVG 产物
# ----------------------------------------------------------------------------


def standalone_svg(icon, with_tile=True):
    a, b = icon["tile"]
    parts = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">',
        tile_defs("tile", a, b),
    ]
    if with_tile:
        parts.append(tile_body("#tile"))
    parts.append(icon["glyph"])
    parts.append("</svg>")
    return "\n".join(parts) + "\n"


def mono_svg(icon):
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">\n'
        + icon["mono"]
        + "\n</svg>\n"
    )


def write_svgs():
    os.makedirs(SVG_DIR, exist_ok=True)
    keep = set()
    for ic in ICONS:
        targets = [(ic["key"] + ".svg", standalone_svg(ic, True)),
                   (ic["key"] + "-fg.svg", standalone_svg(ic, False))]
        if ic["mono"]:
            targets.append((ic["key"] + "-mono.svg", mono_svg(ic)))
        for name, content in targets:
            with open(os.path.join(SVG_DIR, name), "w", encoding="utf-8") as f:
                f.write(content)
            keep.add(name)
    # 清理上一版遗留
    for f in os.listdir(SVG_DIR):
        if f.endswith(".svg") and f not in keep:
            os.remove(os.path.join(SVG_DIR, f))


# ----------------------------------------------------------------------------
# 5. PNG 导出
# ----------------------------------------------------------------------------


def find_chrome():
    for p in CHROME_CANDIDATES:
        if os.path.exists(p):
            return p
    return None


def export_png(jobs):
    chrome = find_chrome()
    if not chrome:
        print("  ! 未找到 Chrome/Edge，跳过 PNG 导出", file=sys.stderr)
        return
    os.makedirs(TMP_DIR, exist_ok=True)
    for i, (svg, size, out) in enumerate(jobs):
        tmp_html = os.path.join(TMP_DIR, "r%02d.html" % i)
        with open(tmp_html, "w", encoding="utf-8") as f:
            f.write(
                '<!DOCTYPE html><html><head><style>html,body{margin:0;padding:0}'
                "img{display:block;width:%dpx;height:%dpx}</style></head>"
                '<body><img src="%s"></body></html>'
                % (size, size, os.path.abspath(svg).replace("\\", "/"))
            )
        os.makedirs(os.path.dirname(out), exist_ok=True)
        cmd = [
            chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars",
            "--force-device-scale-factor=1", "--default-background-color=00000000",
            "--no-first-run", "--user-data-dir=" + os.path.join(TMP_DIR, "p%02d" % i),
            "--virtual-time-budget=3000", "--window-size=%d,%d" % (size, size),
            "--screenshot=" + out, "file:///" + tmp_html.replace("\\", "/"),
        ]
        subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        ok = os.path.exists(out)
        for _ in range(20):
            if ok or os.path.exists(out):
                ok = True
                break
            time.sleep(0.25)
        if ok:
            print("  · %s  %dpx" % (os.path.basename(out), size))
        else:
            print("  ! 导出失败: %s" % out, file=sys.stderr)
    # 清理上一版 PNG
    keep = {os.path.basename(o) for _, _, o in jobs}
    for f in os.listdir(PNG_DIR):
        if f.endswith(".png") and f not in keep:
            os.remove(os.path.join(PNG_DIR, f))

# ----------------------------------------------------------------------------
# 6. 设计稿
# ----------------------------------------------------------------------------
CSS = """
:root{--bg:#F2F5F8;--card:#FFFFFF;--ink:#161C24;--ink2:#6B7888;--ink3:#9AA7B5;
  --line:#E3E9F0;--accent:#0FBF76;--accent-soft:rgba(15,191,118,.09);--radius:18px}
*{box-sizing:border-box}html,body{margin:0;padding:0}
body{background:var(--bg);color:var(--ink);
  font-family:"PingFang SC","Microsoft YaHei","Segoe UI",system-ui,sans-serif;
  font-size:14px;line-height:1.68;-webkit-font-smoothing:antialiased}
.wrap{max-width:1120px;margin:0 auto;padding:36px 28px 72px}
h1{font-size:27px;margin:0 0 6px;letter-spacing:-.4px;font-weight:700}
h2{font-size:17px;margin:0 0 14px;font-weight:700;letter-spacing:-.2px}
h3{font-size:14px;margin:0 0 8px;font-weight:700}
p{margin:0 0 10px}
.sub{color:var(--ink2);font-size:13.5px;margin:0 0 12px}
.sec{margin:44px 0 0}
.sec-h{display:flex;align-items:baseline;gap:10px;border-bottom:1px solid var(--line);
  padding-bottom:10px;margin-bottom:18px}
.sec-h .n{font-size:12px;font-weight:700;color:var(--accent);letter-spacing:.12em}
.sec-h h2{margin:0}
.card{background:var(--card);border:1px solid var(--line);border-radius:var(--radius);padding:20px 22px}
.muted{color:var(--ink2)}
.tiny{font-size:12px;color:var(--ink3);line-height:1.6}
header.hero{display:flex;gap:24px;align-items:center;background:var(--card);
  border:1px solid var(--line);border-radius:22px;padding:24px 26px}
.hero .big{flex:0 0 132px}
.tags{display:flex;flex-wrap:wrap;gap:8px;margin-top:14px}
.tag{font-size:12px;color:var(--ink2);background:#F4F7FA;border:1px solid var(--line);
  border-radius:999px;padding:4px 11px}
.tag.on{color:#046B49;background:var(--accent-soft);border-color:rgba(15,191,118,.3)}
table{width:100%;border-collapse:collapse;font-size:13px}
th,td{text-align:left;padding:10px 12px;border-bottom:1px solid var(--line);vertical-align:top}
th{font-size:12px;color:var(--ink2);font-weight:600;background:#F8FAFC;white-space:nowrap}
tbody tr:last-child td{border-bottom:none}
code{font-family:"JetBrains Mono","Cascadia Mono",Consolas,monospace;font-size:12px;
  background:#F4F7FA;border:1px solid var(--line);border-radius:6px;padding:1px 6px;color:#33445A}
.mono-block{font-family:"JetBrains Mono","Cascadia Mono",Consolas,monospace;font-size:11.5px;
  background:#F8FAFC;border:1px solid var(--line);border-radius:12px;padding:12px 14px;
  white-space:pre;overflow-x:auto;color:#33445A;line-height:1.6}
.grid2{display:grid;grid-template-columns:1fr 1fr;gap:18px}
.grid2.wide{grid-template-columns:1.35fr 1fr}
.swatch{display:inline-flex;align-items:center;gap:7px}
.dot{width:18px;height:18px;border-radius:6px;border:1px solid rgba(0,0,0,.08)}
.cards{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}
.icard{background:var(--card);border:1px solid var(--line);border-radius:var(--radius);
  padding:18px;display:flex;flex-direction:column;gap:12px}
.icard.rec{border-color:rgba(15,191,118,.45);box-shadow:0 6px 22px -14px rgba(15,191,118,.55)}
.badge{font-size:11px;font-weight:700;border-radius:999px;padding:3px 9px}
.badge.rec{background:var(--accent);color:#fff}
.badge.alt{background:#EDF2F7;color:#5A6B7D}
.badge.brand{background:#EAF1FF;color:#3B5BDB}
.icard .nm{font-size:14.5px;font-weight:700}
.icard .en{font-size:11.5px;color:var(--ink3);letter-spacing:.06em;text-transform:uppercase}
.ladder{display:flex;align-items:flex-end;gap:18px;flex-wrap:wrap}
.ladder figure{margin:0;text-align:center}
.ladder figcaption{font-size:11px;color:var(--ink3);margin-top:7px}
.sizebox{display:flex;align-items:center;justify-content:center;background:#F6F8FB;
  border:1px solid var(--line);border-radius:14px}
.refshots{display:grid;grid-template-columns:1.25fr 1fr;gap:16px}
.refshots figure{margin:0}
.refshots img{width:100%;border-radius:12px;border:1px solid var(--line);display:block}
.refshots figcaption{font-size:12px;color:var(--ink2);margin-top:8px;line-height:1.6}
.bug{background:#FFF8F8;border:1px solid #F6D9D9;border-radius:12px;padding:14px 16px}
.bug .hd{font-weight:700;color:#B4373B;font-size:13px;margin-bottom:6px}
ul{margin:6px 0 0;padding-left:20px}li{margin-bottom:5px}
.footer{margin-top:48px;padding-top:18px;border-top:1px solid var(--line);
  color:var(--ink3);font-size:12px}
/* ---- Flyme Auto 实机风格 mock ---- */
.fa{border-radius:18px;padding:20px 22px 16px;overflow:hidden}
.fa.light{background:linear-gradient(175deg,#E9EDF1,#DDE3E9)}
.fa.dark{background:linear-gradient(165deg,#33404E,#161D25 70%)}
.fa .tabs{display:flex;justify-content:center;gap:34px;font-size:13px;margin-bottom:20px}
.fa.light .tabs{color:#7C8A99}.fa.dark .tabs{color:#93A3B3}
.fa .tabs b{color:#2F80ED;font-weight:600}
.fa .grid{display:grid;grid-template-columns:repeat(5,1fr);gap:22px 30px;margin-bottom:16px}
.fa .app{text-align:center}
.fa .tile{width:100%;aspect-ratio:1.79;border-radius:26% / 46%;margin-bottom:8px;
  display:flex;align-items:center;justify-content:center}
.fa .tile svg{width:58%;height:auto}
.fa.light .tile.ghost{background:#FDFDFE;box-shadow:0 1px 3px rgba(30,45,60,.10)}
.fa.light .tile.g{background:linear-gradient(160deg,#2EDF6E,#0CC457);opacity:.42}
.fa.light .tile.b{background:linear-gradient(160deg,#5B9CF3,#2F6FE0);opacity:.42}
.fa.light .tile.w{background:#FDFDFE;opacity:.6}
.fa .app .lb{font-size:11.5px}
.fa.light .app .lb{color:#5C6B7A}.fa.dark .app .lb{color:#93A3B3}
.fa .app.me .tile{background:linear-gradient(160deg,#33E89C,#0FC77A);
  box-shadow:0 6px 16px -8px rgba(15,199,122,.75)}
.fa .app.me .lb{color:#161C24;font-weight:700}
.fa.light .app.me .lb{color:#101820}
.fa .dock{display:flex;align-items:center;justify-content:center;gap:16px;
  border-radius:999px;padding:10px 22px;margin-top:6px}
.fa.light .dock{background:#F6F8FA;box-shadow:0 2px 8px rgba(30,45,60,.10)}
.fa.dark .dock{background:#222C37}
.fa .dock svg{width:20px;height:20px;opacity:.75}
.fa .dock .ring{width:17px;height:17px;border:2.5px solid currentColor;border-radius:50%;opacity:.8}
.fa.light .dock{color:#4A5866}.fa.dark .dock{color:#C3CEDA}
/* 长型遮罩预演容器 */
.mask17{position:relative;overflow:hidden;aspect-ratio:1.79;border-radius:26% / 46%;
  display:inline-block;border:1px solid rgba(20,40,60,.10)}
.mask17 svg{position:absolute;width:179%;height:179%;left:-39.5%;top:-39.5%}
.mrow{display:flex;align-items:center;gap:18px;flex-wrap:wrap}
.mrow figure{margin:0;text-align:center}
@media (max-width:980px){.cards{grid-template-columns:1fr}.grid2,.grid2.wide,
  .refshots{grid-template-columns:1fr}.fa .grid{grid-template-columns:repeat(4,1fr)}}
"""


def build_keyline_svg():
    return """<svg viewBox="-6 -8 120 124" style="width:100%;max-width:300px;height:auto;display:block">
  <rect x="0" y="0" width="108" height="108" rx="6" fill="#F6F8FB"/>
  <rect x="0" y="23.8" width="108" height="60.4" rx="14" fill="rgba(15,191,118,.07)"
        stroke="#0FBF76" stroke-width="0.8" stroke-dasharray="3 2"/>
  <circle cx="54" cy="54" r="33" fill="none" stroke="#9AA7B5" stroke-width="0.6" stroke-dasharray="2.5 2.5"/>
  <path d="M54,10 L54,98 M10,54 L98,54" stroke="#D3DCE5" stroke-width="0.5"/>
  <circle cx="54" cy="54" r="1.2" fill="#0FBF76"/>
  <text x="54" y="-2.5" font-size="5.2" fill="#7C8A99" text-anchor="middle" font-family="sans-serif">108 画布（自适应图标）</text>
  <text x="54" y="20.5" font-size="4.6" fill="#0FA46B" text-anchor="middle" font-family="sans-serif">长型遮罩 ≈1.79:1（实测 104×58）</text>
  <text x="54" y="93" font-size="4.6" fill="#8B98A6" text-anchor="middle" font-family="sans-serif">内容带 y 24–84 ＋ Ø66 兜底安全圆</text>
  <text x="54" y="105" font-size="5" fill="#9AA7B5" text-anchor="middle" font-family="sans-serif">主笔画 6 · 全实心图形</text>
</svg>"""


def build_sheet():
    def symbol(ic):
        a, b = ic["tile"]
        uid = "tile-" + ic["key"]
        return ('<symbol id="sym-%s" viewBox="0 0 108 108">%s\n%s\n%s\n</symbol>'
                % (ic["key"], tile_defs(uid, a, b), tile_body("#" + uid), ic["glyph"]))

    def icon_use(ic, size):
        return ('<svg width="%d" height="%d" viewBox="0 0 108 108"><use href="#sym-%s"/></svg>'
                % (size, size, ic["key"]))

    def mask_use(ic, w):
        return ('<span class="mask17" style="width:%dpx"><svg viewBox="0 0 108 108">'
                '<use href="#sym-%s"/></svg></span>' % (w, ic["key"]))

    symdefs = "".join(symbol(ic) for ic in ICONS)
    main = ICONS[0]

    cards = ""
    for ic in ICONS:
        cards += (
            '<div class="icard %s"><div style="display:flex;align-items:flex-end;gap:14px">'
            '%s<span class="badge %s">%s</span></div>'
            '<div><div class="nm">%s <span class="tiny">方案 %s</span></div>'
            '<div class="en">%s</div></div>'
            '<div class="muted" style="font-size:12.5px">%s</div>'
            '<div class="tiny">%s</div></div>'
            % ("rec" if ic["verdict_cls"] == "rec" else "", mask_use(ic, 96),
               ic["verdict_cls"], ic["verdict"], ic["name"], ic["order"],
               ic["en"], ic["semantics"], ic["notes"])
        )

    star = lambda n: "".join(
        "<span style='color:#D8A21A'>★</span>" if i < n else "<span style='color:#DCE2E9'>★</span>"
        for i in range(5))
    rows = "".join(
        "<tr><td><b>%s</b> %s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
        % (ic["order"], ic["name"], ic["semantics"], star(ic["scores"][0]),
           star(ic["scores"][1]), star(ic["scores"][2]))
        for ic in ICONS)

    # 方形尺寸阶梯（自适应原图）
    sizes = [192, 144, 96, 72, 48, 32]
    ladders = ""
    for ic in ICONS:
        cells = "".join(
            '<figure><div class="sizebox" style="width:%dpx;height:%dpx">%s</div>'
            "<figcaption>%dpx</figcaption></figure>" % (s + 12, s + 12, icon_use(ic, s), s)
            for s in sizes)
        ladders += (
            '<div class="card" style="margin-bottom:16px"><h3>方案 %s · %s'
            '<span class="tiny">（方形全出血 192/144/96/72/48/32 实像素渲染）</span></h3>'
            '<div class="ladder">%s</div></div>' % (ic["order"], ic["name"], cells))

    # 长型遮罩预演（领克 08 应用列表实际渲染比例）
    mrow = "".join(
        '<figure>%s<div class="tiny" style="margin-top:6px">%s</div></figure>'
        % (mask_use(ic, 150), ic["name"]) for ic in ICONS)

    # ---- 实机风格 mock ----
    g_car = '<svg viewBox="0 0 28 28"><path d="M5 19 v-5 c0-1.6 1-2.4 2.6-2.6 L12 11 l3-3.4 c.5-.6 1.2-.9 2-.9 h4 c.9 0 1.6.4 2 1.1 L25 11.4 c1 .2 1.6 1 1.6 2 V19 h-2.4 a2.8 2.8 0 0 0-5.5 0 h-6.4 a2.8 2.8 0 0 0-5.5 0 Z" fill="#5C6B7A"/></svg>'
    g_seat = '<svg viewBox="0 0 28 28"><path d="M10 5 c2 0 3 1.2 3 3 v7 h6 c1.4 0 2.4 1 2.4 2.4 0 1.5-1 2.6-2.4 2.6 h-8 c-1.7 0-3-1.3-3-3 V8 c0-2 1-3 2-3 z" fill="#fff"/></svg>'
    g_bolt = '<svg viewBox="0 0 28 28"><circle cx="14" cy="14" r="9" fill="#101820"/><path d="M15 7 l-5 8 h3.4 l-1 6 5.4-8.4 h-3.6 z" fill="#fff"/></svg>'
    g_map = '<svg viewBox="0 0 28 28"><path d="M14 4 l7 18 -7-4 -7 4 z" fill="#3387E3"/></svg>'
    g_phone = '<svg viewBox="0 0 28 28"><path d="M9 5 h4 l1.6 4.4 -2.4 2 c1 2.2 2.6 3.8 4.8 4.8 l2-2.4 L23.4 15 v4 c0 1.4-1.1 2.5-2.5 2.5 C13 21.5 6.5 15 6.5 7.5 6.5 6.1 7.6 5 9 5 z" fill="#fff"/></svg>'
    g_screen = '<svg viewBox="0 0 28 28"><rect x="5" y="7" width="18" height="12" rx="2" fill="none" stroke="#fff" stroke-width="2.2"/><path d="M11 22 h6" stroke="#fff" stroke-width="2.2" stroke-linecap="round"/></svg>'
    g_shirt = '<svg viewBox="0 0 28 28"><path d="M10 5 l4 2 4-2 5 4 -2.6 3.4 -1.9-1 V22 H9.5 V11.4 l-1.9 1 L5 9 z" fill="#fff"/></svg>'
    g_music = '<svg viewBox="0 0 28 28"><path d="M11 19 V7.5 l9-1.8 V17" fill="none" stroke="#5C6B7A" stroke-width="2.2" stroke-linecap="round"/><circle cx="9" cy="19.4" r="2.6" fill="#5C6B7A"/><circle cx="18" cy="17.4" r="2.6" fill="#5C6B7A"/></svg>'
    g_globe = '<svg viewBox="0 0 28 28"><circle cx="14" cy="14" r="8.5" fill="none" stroke="#3387E3" stroke-width="2"/><path d="M5.5 14 h17 M14 5.5 c-5.5 5-5.5 13 0 17 c5.5-4 5.5-12 0-17" fill="none" stroke="#3387E3" stroke-width="1.8"/></svg>'
    ghost_apps = [
        ("车辆设置", "w", g_car), ("座椅", "g", g_seat), ("能量中心", "g", g_bolt),
        ("地图", "w", g_map), ("Holler", "ME", None), ("音乐", "w", g_music),
        ("投屏助手", "w", g_screen), ("主题美化", "b", g_shirt),
        ("电话", "g", g_phone), ("浏览器", "w", g_globe),
    ]

    def applist_mock():
        tiles = ""
        for name, kind, glyph in ghost_apps:
            if kind == "ME":
                tiles += ('<div class="app me"><div class="tile">%s</div>'
                          '<div class="lb">Holler</div></div>' % icon_use(main, 46))
            else:
                tiles += ('<div class="app"><div class="tile ghost %s">%s</div>'
                          '<div class="lb">%s</div></div>' % (kind, glyph, name))
        return ('<div class="fa light"><div class="tabs"><span><b>应用列表</b></span>'
                '<span>AI 广场</span><span>无界互联</span></div>'
                '<div class="grid">%s</div>'
                '<div class="dock"><span class="ring"></span>'
                '<svg viewBox="0 0 28 28"><path d="M5 14 h13 M13 8 l6 6 -6 6" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>'
                '<svg viewBox="0 0 28 28"><path d="M8 19 v-6 l6-5 6 5 v6 z" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round"/></svg>'
                '<svg viewBox="0 0 28 28"><rect x="6" y="6" width="16" height="16" rx="3" fill="none" stroke="currentColor" stroke-width="2.2"/></svg>'
                '<svg viewBox="0 0 28 28"><path d="M6 16 c3-6 13-6 16 0 M9.5 19 a5.5 5.5 0 0 1 9 0" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/></svg>'
                "</div></div>") % tiles

    def wallpaper_mock():
        card = ('<div style="background:#FDFDFE;border-radius:16px;padding:12px 14px;'
                "display:flex;align-items:center;gap:10px;box-shadow:0 2px 8px rgba(0,0,0,.18)\">"
                "%s<div><div style=\"font-size:12.5px;font-weight:700;color:#1B2530\">%s</div>"
                '<div style="font-size:10.5px;color:#8B98A6">%s</div></div></div>')
        cards = (
            card % ('<span class="mask17" style="width:44px;flex:0 0 auto"><svg viewBox="0 0 108 108"><use href="#sym-%s"/></svg></span>' % main["key"],
                    "Holler · 车外喊话", "待命 · 点击快捷播报")
            + card % ('<span style="width:44px;height:25px;border-radius:26%/46%;background:#E8ECF0;flex:0 0 auto"></span>',
                      "QQ音乐", "最近播放")
            + card % ('<span style="width:44px;height:25px;border-radius:26%/46%;background:#E8ECF0;flex:0 0 auto"></span>',
                      "车辆状态", "胎压 243 kPa")
            + card % ('<span style="width:44px;height:25px;border-radius:26%/46%;background:#E8ECF0;flex:0 0 auto"></span>',
                      "续航", "425 km")
        )
        dock = ('<div class="dock"><span class="ring"></span>'
                + "".join('<svg viewBox="0 0 28 28"><path d="M8 19 v-6 l6-5 6 5 v6 z M11 19 v-4 h6 v4" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>'
                          '<svg viewBox="0 0 28 28"><circle cx="14" cy="14" r="8" fill="none" stroke="currentColor" stroke-width="2"/></svg>'
                          '<svg viewBox="0 0 28 28"><path d="M6 18 l6-9 4 5 2.5-3.5 L23 18 z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>'
                          '<svg viewBox="0 0 28 28"><rect x="6" y="10" width="16" height="9" rx="2.5" fill="none" stroke="currentColor" stroke-width="2"/><path d="M10 10 V8.5 a4 4 0 0 1 8 0 V10" fill="none" stroke="currentColor" stroke-width="2"/></svg>')
                + "</div>")
        return ('<div class="fa dark">'
                '<div style="font-size:11px;color:#93A3B3;display:flex;justify-content:space-between;margin-bottom:14px">'
                "<span>23:18 · 22°C</span><span>80%  4G</span></div>"
                '<div style="display:grid;grid-template-columns:repeat(2,1fr);gap:12px;margin-bottom:14px">'
                + cards + "</div>" + dock + "</div>")

    wrong = "M 54,27 C 68.91,27 81,39.09 81,54 C 81,68.91 68.91,81 54,81\n  C 39.09,81 27,39.09 27,54 C 27,39.09 39.09,27 54,27 Z"
    right = "M 54,27 C 68.91,27 81,39.09 81,54 C 81,68.91 68.91,81 54,81\n  C 39.09,81 27,68.91 27,54 C 27,39.09 39.09,27 54,27 Z"

    html = (
        '<!DOCTYPE html>\n<html lang="zh-CN">\n<head>\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width,initial-scale=1">\n'
        "<title>Holler 启动图标设计方案 v2</title>\n<style>" + CSS + "</style>\n</head>\n<body>\n"
        '<svg width="0" height="0" style="position:absolute" aria-hidden="true"><defs>'
        + symdefs + "</defs></svg>\n<div class=\"wrap\">\n"
        # hero
        '<header class="hero"><div class="big">' + icon_use(main, 132) + "</div><div>\n"
        "<h1>Holler 启动图标设计 <span class=\"tiny\">v2</span></h1>\n"
        '<p class="sub">车外喊话 · 领克 08 Flyme Auto · 提案 v2 · 2026-10-10</p>\n'
        '<p class="muted" style="font-size:13px;max-width:660px">v1 推翻重来：v2 依据<b>领克 08 实机截图</b>'
        "（新出行 XCX-Test）与 flymeauto.com 官网重新对齐——横向长型块、实心扁平图形、系统绿块语言。"
        "语义主体（喇叭 = 喊话）经 v1 验证保留。</p>\n"
        '<div class="tags"><span class="tag on">对齐实机长型块 1.79:1</span>'
        '<span class="tag">系统绿块语言</span><span class="tag">实心扁平图形</span>'
        '<span class="tag">108 自适应图标</span></div>\n</div>\n</header>\n'
        # 01 实机观察
        '<div class="sec"><div class="sec-h"><span class="n">01</span><h2>实机观察（设计依据）</h2></div>\n'
        '<div class="refshots">\n'
        '<figure><img src="ref/flymeauto-applist.jpg" alt="领克08 应用列表实拍">'
        "<figcaption><b>应用列表</b>（新出行 XCX-Test 实拍）：长型圆角块 ≈1.79:1；系统应用 = 绿块白图"
        "（座椅/电话/能量中心）或白块彩图（车辆设置/地图/浏览器）；图形全部<b>实心扁平</b>。</figcaption></figure>\n"
        '<figure><img src="ref/flymeauto-wallpaper-desktop.jpg" alt="领克08 壁纸桌面实拍">'
        "<figcaption><b>壁纸桌面</b>：第三方应用以<b>白色卡片</b>形态出现在卡片栏；DOCK 栏才是描线语言。"
        "图标本身几乎总是「浅底 + 彩图」或「彩底 + 白图」。</figcaption></figure>\n</div>\n"
        '<div class="card" style="margin-top:16px"><h3>实测色值（截图聚类取样）</h3><table>'
        "<thead><tr><th>来源</th><th>色值</th><th>用途</th></tr></thead><tbody>"
        '<tr><td>系统绿块（座椅/电话/能量中心）</td><td><span class="swatch"><span class="dot" style="background:#12D538"></span><code>#12D538</code> ±（实测 #0BD338–#2DDC35）</span></td><td>功能类应用底色</td></tr>'
        '<tr><td>地图 / 应用商店蓝</td><td><span class="swatch"><span class="dot" style="background:#3387E3"></span><code>#3387E3</code> / <code>#5194F3</code></span></td><td>导航与工具蓝</td></tr>'
        '<tr><td>Holler Accent（Theme.kt）</td><td><span class="swatch"><span class="dot" style="background:#1AE5A3"></span><code>#1AE5A3</code></span></td><td>与系统绿同族偏青，绿块语言可直接复用</td></tr>'
        '<tr><td>v2 白底图形深青绿</td><td><span class="swatch"><span class="dot" style="background:#0FBF76"></span><code>#0FBF76</code></span></td><td>白底上的图形主色（对白 3.1:1，≥WCAG 图形 3:1）</td></tr>'
        "</tbody></table>"
        '<p class="tiny" style="margin-top:10px">诚实说明：系统绿块上白图约 2.0:1，本身低于 3:1——图标属品牌装饰图形，'
        "与系统行为保持一致即可；v2 在白底方案上仍把图形压深到 3.1:1，不留可避免的短板。</p></div>\n</div>\n"
        # 02 策略
        '<div class="sec"><div class="sec-h"><span class="n">02</span><h2>v1 → v2 策略修正</h2></div>\n'
        '<div class="card"><table><thead><tr><th>维度</th><th>v1（已否决）</th><th>v2（对齐实机）</th></tr></thead><tbody>'
        "<tr><td>图块形制</td><td>方形深底</td><td>横向长型 1.79:1 语言，内容收敛在中央带 y 24–84，方/长遮罩均不裁切</td></tr>"
        "<tr><td>配色</td><td>深海军蓝底 + 白线性</td><td>系统绿块 / 白块两种形制，青绿与系统绿同族</td></tr>"
        "<tr><td>图形风格</td><td>描线（DOCK 语言误用）</td><td>实心扁平（应用列表语言）</td></tr>"
        "<tr><td>桌面观感</td><td>深底块在浅色桌面上突兀</td><td>融于应用列表，同时以青绿保持辨识度</td></tr>"
        "<tr><td>语义主体</td><td colspan=\"2\">喇叭 = 喊话（v1 验证有效，保留；v1 方案 B 的车形剪影亦保留为备选）</td></tr>"
        "</tbody></table></div></div>\n"
        # 03 三案
        '<div class="sec"><div class="sec-h"><span class="n">03</span><h2>三套方案</h2></div>\n'
        '<div class="cards">' + cards + "</div>\n"
        '<div class="card" style="margin-top:18px"><h3>对比矩阵</h3><table>'
        "<thead><tr><th>方案</th><th>语义</th><th>实机契合</th><th>语义辨识</th><th>48px 可读</th></tr></thead>"
        "<tbody>" + rows + "</tbody></table></div>\n</div>\n"
        # 04 阶梯
        '<div class="sec"><div class="sec-h"><span class="n">04</span><h2>尺寸与遮罩验证</h2></div>\n'
        '<div class="card" style="margin-bottom:16px"><h3>长型遮罩预演（应用列表实际比例 1.79:1）</h3>'
        '<div class="mrow">' + mrow + "</div>"
        '<p class="tiny" style="margin-top:10px">领克 08 应用列表遮罩下的实际呈现（中央带裁切）；'
        "同 row 内三种底色在灰白页面上均可成立。</p></div>\n"
        + ladders + "</div>\n"
        # 05 上车预演
        '<div class="sec"><div class="sec-h"><span class="n">05</span><h2>上车预演（复刻实机版式）</h2></div>\n'
        '<p class="sub">左：应用列表（浅色页 + 长型块 + 标签）；右：壁纸桌面卡片栏（白卡片）。'
        "旁侧为示意灰块，非真实应用。</p>\n"
        '<div class="grid2" style="grid-template-columns:1.4fr 1fr">'
        + applist_mock() + wallpaper_mock() + "</div>\n</div>\n"
        # 06 关键线
        '<div class="sec"><div class="sec-h"><span class="n">06</span><h2>关键线与令牌</h2></div>\n'
        '<div class="grid2 wide"><div class="card"><h3>108 网格 · 长型遮罩 · 安全区</h3>'
        + build_keyline_svg() + "</div>\n"
        '<div class="card"><h3>v2 令牌</h3><table><thead><tr><th>令牌</th><th>值</th><th>用途</th></tr></thead><tbody>'
        '<tr><td>HollerGreen-Deep</td><td><span class="swatch"><span class="dot" style="background:#0FBF76"></span><code>#0FBF76</code></span></td><td>白底图形主色</td></tr>'
        '<tr><td>HollerGreen-Tile</td><td><span class="swatch"><span class="dot" style="background:#26DD8B"></span><code>#33E89C→#0FC77A</code></span></td><td>绿块底（渐变）</td></tr>'
        '<tr><td>HollerSlate</td><td><span class="swatch"><span class="dot" style="background:#26313D"></span><code>#26313D</code></span></td><td>白底车形（方案 C）</td></tr>'
        '<tr><td>Flyme 系统绿（参照）</td><td><span class="swatch"><span class="dot" style="background:#12D538"></span><code>#12D538</code></span></td><td>仅作对位参照，不直接使用</td></tr>'
        "</tbody></table>"
        '<p class="tiny" style="margin-top:10px">建议同步项：<code>Theme.kt</code> 的 Light.Accent '
        "<code>#00B578</code> 与 v2 图形色 <code>#0FBF76</code> 接近，可二选一统一，避免 App 内外出现第三种绿。</p></div></div>\n</div>\n"
        # 07 单色层
        '<div class="sec"><div class="sec-h"><span class="n">07</span><h2>单色层与主题化图标</h2></div>\n'
        '<div class="grid2 wide"><div class="card"><h3>Monochrome（API 33+ / 状态栏）</h3>'
        '<div style="display:flex;gap:26px;align-items:flex-start">'
        '<div style="text-align:center"><div style="width:96px;height:96px;border-radius:26%/46%;'
        'background:#161D25;display:flex;align-items:center;justify-content:center">'
        '<svg width="70" height="70" viewBox="0 0 108 108">' + main["mono"] + "</svg></div>"
        '<div class="tiny">夜间桌面</div></div>'
        '<div style="text-align:center"><div style="width:96px;height:96px;border-radius:26%/46%;'
        'background:#FDFDFE;border:1px solid var(--line);display:flex;align-items:center;justify-content:center">'
        '<svg width="70" height="70" viewBox="0 0 108 108">' + main["mono"].replace("#FFFFFF", "#161C24") + "</svg></div>"
        '<div class="tiny">日间桌面</div></div>'
        '<div style="text-align:center"><div style="width:96px;height:96px;border-radius:26%/46%;'
        'background:#222C37;display:flex;align-items:center;justify-content:center">'
        '<svg width="44" height="44" viewBox="0 0 108 108">' + main["mono"] + "</svg></div>"
        '<div class="tiny">状态栏 ≈24dp</div></div></div>'
        '<p class="tiny" style="margin-top:12px">纯白剪影、无渐变、无描边依赖；喇叭主形 + 一道声弧，缩至 Ø66 内。</p></div>\n'
        '<div class="card"><h3>形状体系说明</h3><ul>'
        "<li><b>长型遮罩由系统施加</b>：交付物仍是 108×108 方形自适应图标（矢量），应用列表的 1.79:1 由 Flyme Auto 裁切。</li>"
        "<li>因此内容必须落在中央带（y 24–84）——三案图形均按此约束设计，方形/圆形/长型遮罩通用。</li>"
        "<li><code>monochrome</code> 层建议一并补上（v1 体检项 ③）。</li></ul></div></div></div>\n"
        # 08 体检
        '<div class="sec"><div class="sec-h"><span class="n">08</span><h2>现有图标体检（v1 结论仍有效）</h2></div>\n'
        '<div class="bug"><div class="hd">① 内层圆环路径控制点错误 —— 环形已变形（几何缺陷）</div>'
        '<p class="tiny" style="margin-bottom:8px"><code>drawable/ic_launcher_foreground.xml</code> 第 45 行，'
        "第三个三次贝塞尔段的第二个控制点 y 值写成 <code>39.09</code>（应为 <code>68.91</code>）。</p>"
        '<div class="mono-block">错误：' + wrong + "\n\n正确：" + right + "</div></div>\n"
        '<div class="grid2" style="margin-top:16px">'
        '<div class="bug" style="background:#F8FAFC;border-color:var(--line)">'
        '<div class="hd" style="color:var(--ink)">② 强调色未与运行时令牌对齐</div>'
        '<p class="tiny">图标混用 <code>#00B578</code>/<code>#66FFCC</code>/<code>#1AE5A3</code> 三套青绿；'
        "建议统一为当套主题 Accent。</p></div>"
        '<div class="bug" style="background:#F8FAFC;border-color:var(--line)">'
        '<div class="hd" style="color:var(--ink)">③ 缺 monochrome 层与商店位图<span class="flag">待补</span></div>'
        '<p class="tiny">未声明 <code>monochrome</code>；Play 上架需 512×512。另：整套旧图标为深底线性，'
        "与实机「浅块实心」语言相悖，v2 即为此修正。</p></div></div>\n</div>\n"
        # 09 交付
        '<div class="sec"><div class="sec-h"><span class="n">09</span><h2>交付清单与落位</h2></div>\n'
        '<div class="grid2 wide"><div class="card"><h3>文件映射（选定方案后写入）</h3><table>'
        "<thead><tr><th>目标路径</th><th>来源</th><th>说明</th></tr></thead><tbody>"
        "<tr><td><code>drawable/ic_launcher_background.xml</code></td><td><code>svg/*.svg</code> 底</td><td>108×108 纯色/渐变块</td></tr>"
        "<tr><td><code>drawable/ic_launcher_foreground.xml</code></td><td><code>svg/*-fg.svg</code></td><td>实心图形层</td></tr>"
        "<tr><td><code>drawable/ic_launcher_monochrome.xml</code></td><td><code>svg/*-mono.svg</code></td><td>纯白剪影</td></tr>"
        "<tr><td><code>mipmap-anydpi-v26/ic_launcher.xml</code></td><td>—</td><td>增补 monochrome 引用</td></tr>"
        "<tr><td><code>png/</code> 各密度位图</td><td>本生成器</td><td>48/72/96/144/192 + 512 商店图</td></tr>"
        "</tbody></table></div>\n"
        '<div class="card"><h3>待你确认</h3><ul>'
        "<li><b>选哪一案</b>（A 翡翠声号 / B 白瓷声号 / C 车语声波），或指出继续调整的方向。</li>"
        "<li>绿块底色用<b>青绿渐变（#33E89C→#0FC77A）还是纯色</b>？系统块多为纯色，纯色更「原生」。</li>"
        "<li>是否顺手统一 <code>Theme.kt</code> 的 Light.Accent 到 #0FBF76。</li></ul></div></div>\n"
        '<div class="grid2" style="margin-top:16px">'
        '<div class="card"><h3>本次已产出</h3><div class="mono-block">'
        "docs/icon/\n  sheet.html              设计稿 v2（本页）\n  export.py               生成器\n"
        "  svg/green-megaphone.svg   方案 A 完整块 / -fg / -mono\n"
        "  svg/white-megaphone.svg   方案 B\n  svg/white-car.svg         方案 C\n"
        "  png/*.png               48/72/96/144/192/512\n  ref/*.jpg               领克08 实拍参考</div></div>\n"
        '<div class="card"><h3>参考来源</h3><ul>'
        "<li>flymeauto.com 官网（Flyme Auto 2 设计语言）</li>"
        "<li>新出行《XCX 车机体验 3 期：深度体验领克 08 Flyme Auto 2》实拍</li>"
        "<li>本地参考截图已存 <code>docs/icon/ref/</code></li></ul></div></div>\n</div>\n"
        '<div class="footer">Holler · 车外喊话 · 启动图标设计稿 v2 &nbsp;|&nbsp; '
        "对齐领克 08 Flyme Auto 实机语言 &nbsp;|&nbsp; 2026-10-10</div>\n"
        "</div>\n</body>\n</html>\n"
    )
    return html

# ----------------------------------------------------------------------------
# 7. main
# ----------------------------------------------------------------------------


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-png", action="store_true")
    args = ap.parse_args()

    print("[1/3] 生成 SVG …")
    write_svgs()
    print("  · svg/ 已更新")

    print("[2/3] 生成设计稿 …")
    with open(os.path.join(ROOT, "sheet.html"), "w", encoding="utf-8") as f:
        f.write(build_sheet())
    print("  · sheet.html")

    if args.no_png:
        return

    print("[3/3] 导出 PNG …")
    jobs = []
    full = [48, 72, 96, 144, 192, 512]
    for ic in ICONS:
        sizes = full if ic["key"] == "green-megaphone" else [48, 192]
        for s in sizes:
            jobs.append((os.path.join(SVG_DIR, ic["key"] + ".svg"), s,
                         os.path.join(PNG_DIR, "%s-%d.png" % (ic["key"], s))))
    if ICONS[0]["mono"]:
        jobs.append((os.path.join(SVG_DIR, ICONS[0]["key"] + "-mono.svg"), 96,
                     os.path.join(PNG_DIR, "green-megaphone-mono-96.png")))
    export_png(jobs)
    shutil.rmtree(TMP_DIR, ignore_errors=True)
    print("完成。")


if __name__ == "__main__":
    main()
