#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_channels_json.py
=====================
Pull all TV channels from the public famelack/famelack-data GitHub repo
and consolidate them into a single channels.json that the FameTV Android
app can consume as a remote config.

Output schema (channels.json):
{
  "version": 1,
  "generated_at": "2026-09-24T10:00:00Z",
  "channel_count": 12345,
  "country_count": 169,
  "channels": [
    {
      "id": "cjno9NJJ87yGNT",
      "name": "Cartoon Network Asia",
      "country": "sg",
      "country_name": "Singapore",
      "languages": ["eng"],
      "is_geo_blocked": false,
      "url": "https://famelack.com/tv/sg/cjno9NJJ87yGNT",
      "stream_url": "https://.../playlist.m3u8",   # may be null
      "youtube_url": "https://www.youtube-nocookie.com/embed/xxx"  # may be null
    },
    ...
  ]
}

Usage:
    python3 build_channels_json.py --output /home/z/my-project/download/channels.json
"""

import argparse
import json
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError

REPO_OWNER = "famelack"
REPO_NAME = "famelack-data"
REPO_BRANCH = "main"
RAW_BASE = f"https://raw.githubusercontent.com/{REPO_OWNER}/{REPO_NAME}/{REPO_BRANCH}/tv/raw"

USER_AGENT = "FameTV-Builder/1.0 (+https://github.com/famelack/famelack-data)"


def http_get(url: str, timeout: int = 15) -> bytes:
    """Simple HTTP GET with UA header."""
    req = Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
    return urlopen(req, timeout=timeout).read()


def load_countries_meta() -> dict:
    """Load countries_metadata.json which gives country code -> name mapping."""
    url = f"{RAW_BASE}/countries_metadata.json"
    print(f"[meta] fetching {url}")
    raw = http_get(url)
    data = json.loads(raw)
    # Normalize: keys are uppercase ISO codes; values contain {country, channelCount, hasChannels}
    return data


def fetch_country(country_code: str) -> list:
    """Fetch a single country's channels. Returns list of channel dicts (raw)."""
    url = f"{RAW_BASE}/countries/{country_code}.json"
    try:
        raw = http_get(url, timeout=20)
        return json.loads(raw)
    except (HTTPError, URLError, TimeoutError) as e:
        print(f"  [warn] {country_code}: {e}", file=sys.stderr)
        return []
    except json.JSONDecodeError as e:
        print(f"  [warn] {country_code}: bad json - {e}", file=sys.stderr)
        return []


def consolidate_channel(raw_ch: dict, country_name: str) -> dict:
    """Map raw channel record to the FameTV app schema."""
    nanoid = raw_ch.get("nanoid") or ""
    country = (raw_ch.get("country") or "").lower()
    sources = raw_ch.get("sources") or {}
    streams = sources.get("streams") or []
    youtube = sources.get("youtube") or []

    famelack_url = f"https://famelack.com/tv/{country}/{nanoid}" if country and nanoid else ""

    return {
        "id": nanoid,
        "name": raw_ch.get("name") or "Unnamed",
        "country": country,
        "country_name": country_name,
        "languages": raw_ch.get("languages") or [],
        "is_geo_blocked": bool(raw_ch.get("isGeoBlocked", False)),
        "url": famelack_url,
        "stream_url": streams[0] if streams else None,
        "youtube_url": youtube[0] if youtube else None,
    }


def build(output_path: Path, max_workers: int = 16):
    print(f"[build] FameTV channel aggregator")
    print(f"[build] data source: {RAW_BASE}")

    meta = load_countries_meta()
    print(f"[build] {len(meta)} countries in metadata")

    # Filter to countries that actually have channels
    countries = []
    for code_upper, info in meta.items():
        if not info.get("hasChannels"):
            continue
        countries.append((code_upper.lower(), info.get("country") or code_upper))
    print(f"[build] {len(countries)} countries with channels, fetching in parallel...")

    all_channels = []
    country_count_with_channels = 0

    with ThreadPoolExecutor(max_workers=max_workers) as pool:
        future_to_country = {
            pool.submit(fetch_country, code): (code, name)
            for code, name in countries
        }
        for i, fut in enumerate(as_completed(future_to_country), start=1):
            code, name = future_to_country[fut]
            raw_channels = fut.result()
            if not raw_channels:
                continue
            country_count_with_channels += 1
            for raw_ch in raw_channels:
                all_channels.append(consolidate_channel(raw_ch, name))
            if i % 20 == 0 or i == len(countries):
                print(f"  progress: {i}/{len(countries)} countries, {len(all_channels)} channels so far")

    # Sort by country_name then channel name for stable UX
    all_channels.sort(key=lambda c: (c["country_name"], c["name"].lower()))

    output = {
        "version": 1,
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "channel_count": len(all_channels),
        "country_count": country_count_with_channels,
        "source_repo": f"https://github.com/{REPO_OWNER}/{REPO_NAME}",
        "channels": all_channels,
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    size_mb = output_path.stat().st_size / 1024 / 1024
    print(f"[build] DONE: {len(all_channels)} channels across {country_count_with_channels} countries")
    print(f"[build] wrote {output_path}  ({size_mb:.2f} MB)")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output", "-o",
        default="/home/z/my-project/download/channels.json",
        help="Output channels.json path",
    )
    parser.add_argument(
        "--workers", "-w",
        type=int, default=16,
        help="Parallel HTTP workers",
    )
    args = parser.parse_args()
    build(Path(args.output), max_workers=args.workers)


if __name__ == "__main__":
    main()
