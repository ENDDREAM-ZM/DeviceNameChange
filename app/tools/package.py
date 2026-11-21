import os
import sys
import zipfile

base, dex, meta_dir, out = sys.argv[1:5]

with zipfile.ZipFile(base) as zin, zipfile.ZipFile(out, "w") as zout:
    for info in zin.infolist():
        data = zin.read(info.filename)
        zi = zipfile.ZipInfo(info.filename, date_time=info.date_time)
        zi.compress_type = (zipfile.ZIP_STORED if info.filename == "resources.arsc"
                            else zipfile.ZIP_DEFLATED)
        zi.external_attr = info.external_attr
        zout.writestr(zi, data)

    with open(dex, "rb") as f:
        zi = zipfile.ZipInfo("classes.dex")
        zi.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(zi, f.read())

    for root, _, files in os.walk(meta_dir):
        for name in files:
            path = os.path.join(root, name)
            rel = os.path.relpath(path, os.path.dirname(os.path.dirname(meta_dir)))
            rel = rel.replace(os.sep, "/")
            with open(path, "rb") as f:
                zi = zipfile.ZipInfo(rel)
                zi.compress_type = zipfile.ZIP_DEFLATED
                zout.writestr(zi, f.read())

print("packaged", out)
