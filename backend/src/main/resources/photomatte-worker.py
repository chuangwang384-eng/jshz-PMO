import json
import os
import sys
import traceback
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
        # traceback 进 stderr：后端把 stderr 落盘并在失败日志中带出，便于定位（2026-11-17）
        traceback.print_exc()
        print("ERROR", flush=True)
