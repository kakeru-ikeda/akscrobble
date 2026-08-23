"""XAPK の ZIP 構造を検証する。

AK 謹製インストーラの判定基準は「書庫内の *.apk の個数」なので、
apk_count が候補の定義どおりであることが最重要。
"""
import sys
import zipfile


def inspect(path: str) -> dict:
    entries = []
    with zipfile.ZipFile(path) as z:
        for i in z.infolist():
            entries.append(
                {
                    "name": i.filename,
                    "method": i.compress_type,
                    "flag": i.flag_bits,
                    "size": i.file_size,
                    "comp_size": i.compress_size,
                    "header_offset": i.header_offset,
                }
            )
    return {
        "entries": entries,
        "apk_count": sum(1 for e in entries if e["name"].endswith(".apk")),
        "all_stored": all(e["method"] == 0 for e in entries),
        "all_data_descriptor": all(e["flag"] & 0x0008 for e in entries),
    }


def main(argv):
    if len(argv) < 2:
        print("usage: verify_xapk.py <xapk> [<xapk> ...]", file=sys.stderr)
        return 2
    for p in argv[1:]:
        r = inspect(p)
        print(f"=== {p}")
        print(f"  apk_count           {r['apk_count']}")
        print(f"  all_stored          {r['all_stored']}")
        print(f"  all_data_descriptor {r['all_data_descriptor']}")
        for e in r["entries"]:
            print(
                f"    {e['name']:28s} method={e['method']} "
                f"flag=0x{e['flag']:04x} size={e['size']}"
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
