#!/usr/bin/env python3
"""Build an offline SCP metadata + extractive synopsis catalog from scp-data.

Requires Python 3.10+. Uses only the standard library. Not affiliated with the SCP Wiki.
SCP text/adaptations: CC BY-SA 3.0. Images are URLs only, not bundled.
"""
import argparse
import html
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import time
from urllib.request import Request, urlopen
from urllib.parse import urljoin

BASE = "https://scp-data.tedivm.com/data/scp/items/"
DESCRIPTION = re.compile(r"\bDescription\s*:\s*", re.I)
SECTION = re.compile(r"\n\s*(?:Addendum|Appendix|Incident Report|Test Log|Interview Log|Footnotes?)\b", re.I)


class PlainText(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts = []
        self.suppress = 0

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style"):
            self.suppress += 1
        elif tag in ("p", "br", "div", "li", "h1", "h2", "h3", "section", "tr"):
            self.parts.append("\n")

    def handle_endtag(self, tag):
        if tag in ("script", "style"):
            self.suppress = max(0, self.suppress-1)
        elif tag in ("p", "div", "li", "h1", "h2", "h3", "section", "tr"):
            self.parts.append("\n")

    def handle_data(self, data):
        if not self.suppress:
            self.parts.append(data)


class SeriesTitles(HTMLParser):
    """Read official SCP series-index display names from <li> elements."""
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.level = 0
        self.chunks = []
        self.names = {}

    def handle_starttag(self, tag, attrs):
        if tag == "li":
            if self.level == 0:
                self.chunks = []
            self.level += 1

    def handle_endtag(self, tag):
        if tag == "li" and self.level:
            self.level -= 1
            if self.level == 0:
                line = re.sub(r"\s+", " ", "".join(self.chunks)).strip()
                match = re.match(r"^(SCP-\d{3,5})\s*[-–]\s*(.+)$", line, re.I)
                if match:
                    self.names[match.group(1).lower()] = match.group(2).strip()

    def handle_data(self, data):
        if self.level:
            self.chunks.append(data)


def series_titles():
    titles = {}
    for number in range(1, 11):
        url = "https://scp-wiki.wikidot.com/scp-series" + (f"-{number}" if number != 1 else "")
        try:
            request = Request(url, headers={"User-Agent": "SCPArchiveBuilder/1.0"})
            with urlopen(request, timeout=30) as response:
                source = response.read(4_000_000).decode("utf-8", errors="replace")
            parser = SeriesTitles()
            parser.feed(source)
            titles.update(parser.names)
        except Exception as ex:
            print(f"WARN: No series titles from {url}: {ex}", flush=True)
    return titles


def summarize(raw_html):
    if not raw_html:
        return ""
    parser = PlainText()
    try:
        parser.feed(raw_html)
        plain = "".join(parser.parts)
    except Exception:
        plain = re.sub(r"<[^>]+>", " ", raw_html)
    plain = html.unescape(plain).replace("\xa0", " ")
    plain = re.sub(r"[ \t]+", " ", plain)
    plain = re.sub(r"\n[ \t]*\n+", "\n", plain).strip()
    match = DESCRIPTION.search(plain)
    if not match:
        return ""
    part = SECTION.split(plain[match.end():], maxsplit=1)[0]
    part = re.sub(r"\s+", " ", part).strip()
    if len(part) < 25:
        return ""
    if len(part) > 460:
        clip = part[:460]
        cut = max(clip.rfind(". "), clip.rfind("! "))
        if cut > 150:
            return clip[:cut+1]
        return clip[:max(200, clip.rfind(" "))] + "…"
    return part


def download_json(url, max_bytes=175_000_000):
    for attempt in range(3):
        try:
            request = Request(url, headers={"User-Agent": "SCPArchiveBuilder/1.0 (+https://scp-wiki.wikidot.com/)"})
            with urlopen(request, timeout=95) as reply:
                length = reply.headers.get("Content-Length")
                if length and int(length) > max_bytes:
                    raise ValueError(f"File too large: {url}")
                raw = reply.read(max_bytes+1)
                if len(raw) > max_bytes:
                    raise ValueError(f"File too large: {url}")
                return json.loads(raw)
        except Exception as ex:
            if attempt == 2:
                raise RuntimeError(f"Could not fetch {url}: {ex}") from ex
            time.sleep(2*(attempt+1))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="app/src/main/assets/catalog.json")
    ap.add_argument("--metadata-only", action="store_true", help="Do not fetch series content for summaries")
    ap.add_argument("--require-full", action="store_true", help="Refuse to package starter-only or partial catalogs")
    ap.add_argument("--max-files", type=int, default=None, help="For testing synopsis extraction only")
    args = ap.parse_args()

    data = download_json(BASE + "index.json", max_bytes=35_000_000)
    if not isinstance(data, dict) or len(data) < 1000:
        raise ValueError("Upstream returned too few records. Refusing to overwrite local catalog")
    entries = {}
    by_file = {}
    for key, record in data.items():
        if not isinstance(record, dict):
            continue
        slug = record.get("link", key)
        if not isinstance(slug, str) or not slug.startswith("scp-"):
            continue
        file = record.get("content_file") or ""
        if file and (".." in file or file.startswith("/") or not re.fullmatch(r"[\w./-]+", file)):
            file = ""
        item = {
            "link": slug,
            "scp": record.get("scp") or slug.upper(),
            "title": record.get("title") or slug.upper(),
            "url": record.get("url") or "https://scp-wiki.wikidot.com/" + slug,
            "created_by": record.get("created_by") or "",
            "content_file": file,
            "tags": record.get("tags") if isinstance(record.get("tags"), list) else [],
            "images": (record.get("images") or [])[:6],
            "summary": ""
        }
        entries[slug] = item
        if file:
            by_file.setdefault(file, []).append(slug)

    print("Fetching canonical names from the SCP series indexes…", flush=True)
    names = series_titles()
    for slug, name in names.items():
        if slug in entries:
            entries[slug]["display_name"] = name
    print(f"Resolved {len(names)} names from series indexes", flush=True)

    if args.require_full and len(entries) < 5000:
        raise RuntimeError(f"Only {len(entries)} SCPs found; full build refused")

    files = sorted(by_file)
    if args.max_files is not None:
        files = files[:args.max_files]
    successful_files = 0
    if not args.metadata_only:
        for index, file in enumerate(files, 1):
            print(f"[{index}/{len(files)}] Fetching {file}", flush=True)
            try:
                content = download_json(urljoin(BASE, file))
                if not isinstance(content, dict):
                    continue
                successful_files += 1
                for key, obj in content.items():
                    if not isinstance(obj, dict):
                        continue
                    slug = obj.get("link") or key
                    if slug in entries:
                        entries[slug]["summary"] = summarize(obj.get("raw_content") or "")
            except Exception as ex:
                print(f"WARN: could not process {file}: {ex}", flush=True)
    if args.require_full and not args.metadata_only and files and successful_files == 0:
        raise RuntimeError("Could not fetch any article content. Refusing incomplete full pack")

    summaries = sum(bool(item["summary"]) for item in entries.values())
    target = Path(args.out)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps({
        "source": BASE + "index.json",
        "license": "CC BY-SA 3.0. SCP concepts, texts and descriptions belong to the wiki and respective authors.",
        "note": "Extracted descriptions may not accurately summarize unconventional pages. Some entries have no accompanying images.",
        "entries": entries
    }, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"Wrote {target}: {len(entries)} records, {summaries} description excerpts, {len(files)} content files considered.")


if __name__ == "__main__":
    main()
