"""Build XAPK archives in plain or APKPure-compatible ZIP formats."""
import binascii
import json
import os
import struct
import zipfile


def _manifest_json(package: str, version_code: int, apks, app_name, version_name) -> bytes:
    split_apks = []
    for name, _path in apks:
        if name == "base.apk" or name == f"{package}.apk":
            split_apks.append({"file": name, "id": "base"})
        else:
            split_apks.append({"file": name, "id": name[:-4]})
    doc = {
        "xapk_version": 2,
        "package_name": package,
        "name": app_name,
        "version_code": str(version_code),
        "version_name": version_name,
        "min_sdk_version": "21",
        "target_sdk_version": "28",
        "permissions": [
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE",
        ],
        "total_size": sum(os.path.getsize(p) for _n, p in apks),
        "split_apks": split_apks,
    }
    return json.dumps(doc, indent=4).encode("utf-8")


def _build_plain(out_path, entries):
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_STORED) as z:
        for name, data in entries:
            z.writestr(name, data)


def _build_apkpure(out_path, entries):
    """データディスクリプタ付きの ZIP を手書きする。"""
    out = bytearray()
    central = []

    for name, data in entries:
        nb = name.encode("utf-8")
        crc = binascii.crc32(data) & 0xFFFFFFFF
        offset = len(out)
        out += struct.pack(
            "<IHHHHHIIIHH",
            0x04034B50,
            20,
            0x0008,
            0,
            0,
            0,
            0,
            0,
            0,
            len(nb),
            0,
        )
        out += nb
        out += data
        out += struct.pack("<IIII", 0x08074B50, crc, len(data), len(data))
        central.append((nb, crc, len(data), offset))

    cd_offset = len(out)
    for nb, crc, size, offset in central:
        out += struct.pack(
            "<IHHHHHHIIIHHHHHII",
            0x02014B50,
            20,
            20,
            0x0008,
            0,
            0,
            0,
            crc,
            size,
            size,
            len(nb),
            0,
            0,
            0,
            0,
            0,
            offset,
        )
        out += nb
    cd_size = len(out) - cd_offset
    out += struct.pack(
        "<IHHHHIIH",
        0x06054B50,
        0,
        0,
        len(central),
        len(central),
        cd_size,
        cd_offset,
        0,
    )

    with open(out_path, "wb") as f:
        f.write(bytes(out))


def build(
    out_path,
    package,
    version_code,
    apks,
    style="plain",
    app_name="AKScrobble",
    version_name="1.0",
):
    """XAPK を生成する。

    apks: [(アーカイブ内名, ローカルパス), ...]。先頭を base として扱う。
    """
    entries = []
    for name, path in apks:
        with open(path, "rb") as f:
            entries.append((name, f.read()))
    entries.append(
        (
            "manifest.json",
            _manifest_json(package, version_code, apks, app_name, version_name),
        )
    )

    if style == "plain":
        _build_plain(out_path, entries)
    elif style == "apkpure":
        _build_apkpure(out_path, entries)
    else:
        raise ValueError(f"unknown style: {style}")
    return out_path
