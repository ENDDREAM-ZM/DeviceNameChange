import zipfile

base, dex, xposed_init, out = __import__("sys").argv[1:5]

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

    with open(xposed_init, "rb") as f:
        zi = zipfile.ZipInfo("assets/xposed_init")
        zi.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(zi, f.read())

print("packaged", out)
