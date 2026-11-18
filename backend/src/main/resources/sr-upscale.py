"""证件照低分辨率超分助手（2026-11-18 新增）。

协议（与 photomatte-worker.py 同风格，stdin 每行一个 JSON）：
    ["输入图路径", "输出图路径", "模型.pb路径"]
成功打印 OK，失败打印 ERROR（traceback 走 stderr）。

运行环境：独立 venv，仅依赖 opencv-contrib-python-headless（dnn_superres）+ numpy。
调用方（photomatte-worker.py）通过 SR_UPSCALE_PYTHON 指向本解释器；
禁止在其他业务代码中复制本逻辑。
"""
import json
import sys
import traceback

import cv2
import numpy as np


def imread_unicode(path):
    return cv2.imdecode(np.fromfile(path, dtype=np.uint8), cv2.IMREAD_COLOR)


def imwrite_unicode(path, img):
    ok, buf = cv2.imencode(".png", img)
    if not ok:
        raise ValueError("PNG 编码失败")
    buf.tofile(path)


def main():
    for line in sys.stdin:
        try:
            source, target, model = json.loads(line)
            image = imread_unicode(source)
            if image is None:
                raise ValueError("输入图读取失败")
            scale = int(model.rsplit("x", 1)[-1].split(".")[0])
            sr = cv2.dnn_superres.DnnSuperResImpl_create()
            sr.readModel(model)
            sr.setModel("edsr" if "edsr" in model.lower() else "lapsrn", scale)
            imwrite_unicode(target, sr.upsample(image))
            print("OK", flush=True)
        except Exception:
            traceback.print_exc()
            print("ERROR", flush=True)


if __name__ == "__main__":
    main()
