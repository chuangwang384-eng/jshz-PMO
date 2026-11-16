import json
import os
import sys
from pathlib import Path

os.environ.setdefault("U2NET_HOME", str(Path.cwd() / "models"))
os.environ.setdefault("OMP_NUM_THREADS", "4")
from PIL import Image
from rembg import remove, new_session

session = new_session("u2net")
print("READY", flush=True)
for line in sys.stdin:
    try:
        source, target = json.loads(line)
        with Image.open(source) as image:
            remove(image.convert("RGBA"), session=session).save(target, format="PNG")
        print("OK", flush=True)
    except Exception:
        print("ERROR", flush=True)
