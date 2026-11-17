import json
import os
import sys
import traceback
from pathlib import Path

os.environ.setdefault("U2NET_HOME", str(Path.cwd() / "models"))
os.environ.setdefault("OMP_NUM_THREADS", "4")
import numpy as np
from PIL import Image
from rembg import remove, new_session

# scipy 用于形态学填洞/最大连通域；受保护 venv 若缺省则降级为纯 alpha 硬化（不阻断抠图）
try:
    from scipy import ndimage
except Exception:
    ndimage = None

session = new_session("u2net")
print("READY", flush=True)


def refine(image):
    """证件照抠图精修（2026-11-17 修复"仅识别到人头/人像发虚"）。

    根因：u2net 通用显著性检测对人像输出软 alpha（身体多为 128~254 半透明），
    小图（<320px）掩码细节不足，导致抠出的人像发虚、身体衰减甚至只剩头部清晰。
    处理链：分辨率预处理 → 背景分割 → alpha 硬化 → 人物主体提取 → 裁剪边界判定。
    """
    # 1) 分辨率预处理：小图放大到最长边 640（u2net 内部 320 输入），提升掩码细节；
    #    大图不放大（Java 侧 resizePortraitInput 已把 >1200 缩到 1200）。
    width, height = image.size
    longest = max(width, height)
    if longest < 640:
        scale = 640.0 / longest
        image = image.resize((max(1, round(width * scale)), max(1, round(height * scale))), Image.LANCZOS)

    # 2) 背景分割：u2net 本地推理（数据不出内网）
    out = remove(image.convert("RGBA"), session=session)
    alpha = np.array(out.getchannel("A")).astype(np.float32)

    # 3) alpha 硬化：gamma 提升让半透明身体变实，保留边缘抗锯齿
    alpha = np.clip((alpha / 255.0) ** 0.45 * 255.0, 0, 255)
    mask = alpha >= 90
    if ndimage is not None:
        # 4) 人物区域检测：闭运算填补身体空洞 + 填洞 + 保留最大连通域（人物主体），去散点噪声
        mask = ndimage.binary_closing(mask, structure=np.ones((5, 5)))
        mask = ndimage.binary_fill_holes(mask)
        labels, count = ndimage.label(mask)
        if count > 1:
            sizes = ndimage.sum(mask, labels, range(1, count + 1))
            mask = labels == (1 + int(np.argmax(sizes)))
    # 主体内强制至少 200 不透明（清晰），主体外全透明
    alpha = np.where(mask, np.maximum(alpha, 200), 0).astype(np.uint8)
    rgba = np.array(out)
    rgba[..., 3] = alpha
    result = Image.fromarray(rgba)

    # 5) 裁剪边界判定：按前景 bbox 裁剪 + 3% 边距，完整保留人物有效区域、去掉透明边距
    bbox = result.getchannel("A").getbbox()
    if bbox:
        pad = round(0.03 * max(result.size))
        left, top, right, bottom = bbox
        left = max(0, left - pad)
        top = max(0, top - pad)
        right = min(result.width, right + pad)
        bottom = min(result.height, bottom + pad)
        result = result.crop((left, top, right, bottom))
    return result


for line in sys.stdin:
    try:
        source, target = json.loads(line)
        with Image.open(source) as image:
            refine(image).save(target, format="PNG")
        print("OK", flush=True)
    except Exception:
        # traceback 进 stderr：后端把 stderr 落盘并在失败日志中带出，便于定位（2026-11-17）
        traceback.print_exc()
        print("ERROR", flush=True)
