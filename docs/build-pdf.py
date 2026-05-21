#!/usr/bin/env python3
"""docs/gui-manual.md から docs/gui-manual.pdf を生成する。

日本語が文字化けしないよう、システムにインストールされた日本語フォント
(IPAGothic / WenQuanYi Zen Hei など) を CSS で明示指定する。

使い方:
    pip install weasyprint markdown
    python3 docs/build-pdf.py
"""
from pathlib import Path

import markdown
from weasyprint import HTML

DOCS = Path(__file__).resolve().parent
SRC = DOCS / "gui-manual.md"
OUT = DOCS / "gui-manual.pdf"

CSS = """
@page {
    size: A4;
    margin: 16mm 15mm 18mm 15mm;
    @bottom-center { content: counter(page) " / " counter(pages); font-size: 8pt; color: #888; }
}
body {
    font-family: "IPAGothic", "WenQuanYi Zen Hei", sans-serif;
    font-size: 10.5pt;
    line-height: 1.65;
    color: #222;
}
h1 { font-size: 20pt; border-bottom: 3px solid #444; padding-bottom: 4px; }
h2 { font-size: 15pt; border-bottom: 1px solid #bbb; padding-bottom: 3px;
     margin-top: 1.4em; page-break-after: avoid; }
h3 { font-size: 12.5pt; margin-top: 1.1em; page-break-after: avoid; }
h1, h2, h3 { font-weight: bold; }
a { color: #0b5; text-decoration: none; }
code {
    font-family: "WenQuanYi Zen Hei Mono", "IPAGothic", monospace;
    background: #f2f2f2; padding: 1px 4px; border-radius: 3px; font-size: 90%;
}
pre {
    font-family: "WenQuanYi Zen Hei Mono", "IPAGothic", monospace;
    background: #f6f8fa; border: 1px solid #ddd; border-radius: 5px;
    padding: 8px 10px; font-size: 8.5pt; line-height: 1.3;
    white-space: pre; page-break-inside: avoid;
}
pre code { background: none; padding: 0; font-size: 100%; }
table { border-collapse: collapse; width: 100%; margin: 0.6em 0; font-size: 9.5pt; }
th, td { border: 1px solid #ccc; padding: 4px 8px; text-align: left; vertical-align: top; }
th { background: #eef; }
tr { page-break-inside: avoid; }
blockquote {
    border-left: 4px solid #9c9; background: #f4faf4;
    margin: 0.6em 0; padding: 4px 12px; color: #355;
}
hr { border: none; border-top: 1px solid #ddd; margin: 1.2em 0; }
"""


def main() -> None:
    text = SRC.read_text(encoding="utf-8")
    body = markdown.markdown(
        text, extensions=["extra", "toc", "sane_lists"], output_format="html5"
    )
    html = (
        '<!DOCTYPE html><html lang="ja"><head><meta charset="utf-8">'
        f"<style>{CSS}</style></head><body>{body}</body></html>"
    )
    HTML(string=html, base_url=str(DOCS)).write_pdf(str(OUT))
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
