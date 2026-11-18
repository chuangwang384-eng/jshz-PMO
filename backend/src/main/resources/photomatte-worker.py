import json
import os
import subprocess
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

# 低像素照片超分（2026-11-18 新增）：SR_UPSCALE_PYTHON 指向 sr-upscale.py 的解释器；
# 缺省根目录 = ProgramFiles/JSHZ/sr-upscale（与 pp-ocrv5 同级）。任一条件不满足则跳过超分。
SR_MAX_SIDE = 800          # 最长边超过此值不超分（本身足够清晰）
SR_EDSR_MAX_SIDE = 350     # 该阈值内用 EDSR（最锐、最慢），其余用 LapSRN（快）
sr_python = os.environ.get("SR_UPSCALE_PYTHON") or str(
    Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "JSHZ" / "sr-upscale" / "venv" / "Scripts" / "python.exe")
sr_script = os.environ.get(
    "SR_UPSCALE_SCRIPT", str(Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "JSHZ" / "sr-upscale" / "sr-upscale.py"))
sr_model_dir = os.environ.get(
    "SR_UPSCALE_MODELS", str(Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "JSHZ" / "sr-upscale" / "models"))


def upscale_if_lowres(image):
    """最长边 < SR_MAX_SIDE 时用超分模型放大 4 倍（EDSR/LapSRN 二选一）。

    超分失败（解释器缺失/模型缺失/推理异常/超时）一律回退原图，不影响抠图主链路。
    """
    longest = max(image.size)
    if longest >= SR_MAX_SIDE:
        return image
    if not Path(sr_python).is_file() or not Path(sr_script).is_file():
        return image
    try:
        model = str(Path(sr_model_dir) / ("EDSR_x4.pb" if longest <= SR_EDSR_MAX_SIDE else "LapSRN_x4.pb"))
        if not Path(model).is_file():
            return image
        with Image.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
            source = tmp.name
        target = source + ".up.png"
        try:
            image.save(source, format="PNG")
            result = subprocess.run([sr_python, sr_script], input=json.dumps([source, target, model]),
                                    capture_output=True, text=True, timeout=300, encoding="utf-8")
            if result.returncode == 0 and "OK" in result.stdout and Path(target).is_file():
                upscaled = Image.open(target).convert("RGB")
                if max(upscaled.size) > 1200:  # 与 Java 侧 resizePortraitInput 上限一致，控制后续形态学运算规模
                    ratio = 1200.0 / max(upscaled.size)
                    upscaled = upscaled.resize((max(1, round(upscaled.width * ratio)),
                                                max(1, round(upscaled.height * ratio))), Image.LANCZOS)
                return upscaled
        finally:
            for path in (source, target):
                try:
                    os.unlink(path)
                except OSError:
                    pass
    except Exception:
        traceback.print_exc()
    return image


session = new_session("u2net")
print("READY", flush=True)


def recover_solid_background_portrait(image, mask):
    """从与顶边相连的纯色背景中分离被模型漏掉的肩部和衣服。"""
    height, width = mask.shape
    if ndimage is None or not mask.any():
        return mask
    if image.convert("RGBA").getchannel("A").getextrema()[0] < 255:
        return mask

    rgb = np.asarray(image.convert("RGB"), dtype=np.int16)
    top = rgb[:max(1, height // 32)]
    background = np.median(top, axis=(0, 1))
    # 有色纯背景下即使模型已识别到下半身，也可能单侧漏肩。
    reaches_bottom = mask[int(height * 0.88):].any()
    if reaches_bottom and np.ptp(background) < 48:
        return mask
    distance = np.max(np.abs(rgb - background), axis=2)
    if np.mean(distance[:len(top)] <= 12) < 0.98:
        return mask

    # 容忍压缩和缩放产生的色差，并限制为可从顶边到达的背景。
    similar = distance <= 24
    seed = np.zeros_like(mask)
    seed[0] = similar[0]
    exterior = ndimage.binary_propagation(seed, mask=similar)
    foreground = ~exterior
    # 要求头部主体与底部中央的衣服相连，且顶边估算出的背景覆盖足够面积。
    if exterior.mean() < 0.15:
        return mask
    labels, _ = ndimage.label(foreground | mask)
    subject = np.argmax(np.bincount(labels[mask]))
    portrait = labels == subject
    lower_center = portrait[-max(1, height // 16):, width // 4:3 * width // 4]
    if not lower_center.size or lower_center.mean() < 0.8 or portrait[mask].mean() < 0.95:
        return mask
    # 已识别到衣服时，仅补回明显漏分的区域，避免把原本完整轮廓的少量底色带进来。
    if reaches_bottom and np.count_nonzero(portrait & ~mask) < 0.02 * np.count_nonzero(portrait):
        return mask
    return portrait | mask


def strip_background_colored_regions(image, portrait):
    """纯色底残留清理（2026-11-18 修复"蓝底块残留"；同日二次修复"白底批量损毁"）。

    fill_holes 会把脖子/肩窝等处被纯色背景填充的凹陷当成人像洞保留。此处把主体内
    与背景同色的连通块重新透明化。按背景色分级（白底照片脸部浅色区距白底 <64，
    容差 64 曾把段战锋/潘娜/刘朵平的脸部、头发成片误删）：
    - 彩色纯色底（蓝底等，通道差 ≥48）：容差 64，保留与底部 12% 行相交的衣物锚点；
    - 白/灰底：容差降到 30，且只清理与外部透明区相邻的连通块，脸部内部区域绝不触碰。
    非纯色底或残留占比过低时不动刀。
    """
    if ndimage is None:
        return portrait
    rgb = np.asarray(image.convert("RGB"), dtype=np.int16)
    background = np.median(rgb[:max(1, rgb.shape[0] // 32)], axis=(0, 1))
    distance = np.max(np.abs(rgb - background), axis=2)
    top = distance[:max(1, rgb.shape[0] // 32)]
    # 仅处理纯色底：顶边 98% 像素接近背景色（与 recover 的纯色判定一致）
    if np.mean(top <= 12) < 0.98:
        return portrait
    colored = np.ptp(background) >= 48
    tolerance = 64 if colored else 30
    # 饱和度判别（2026-11-18 段战锋回归）：深棕发与深红底色距仅 ~45（<容差），但饱和度
    # 迥异（发~0.2、底~1.0）；与背景同色系（含阴影）的残留饱和度必然接近背景。
    mx = np.clip(rgb.max(axis=2), 1, None)
    sat = (rgb.max(axis=2) - rgb.min(axis=2)) / mx
    bg_sat = float((int(background.max()) - int(background.min())) / max(1, int(background.max())))
    sat_close = np.abs(sat - bg_sat) <= (0.3 if colored else 1.0)
    similar = (distance <= tolerance) & sat_close & portrait
    if not colored:
        # 亮衣物保护（刘朵平白西装回归 2026-11-18）：白衬衫亮度 ≥200，背景灰 ≤175；
        # 衣服褶皱阴影虽与背景色距近，但亮度远高于背景，不算残留
        brightness = rgb.mean(axis=2)
        bg_bright = float(background.mean())
        similar &= brightness <= bg_bright + 45
    if similar.mean() < 0.02:
        return portrait
    labels, count = ndimage.label(similar)
    if count == 0:
        return portrait
    anchor_top = int(portrait.shape[0] * 0.88)
    border = np.zeros_like(portrait)
    border[:2, :] = True; border[-2:, :] = True; border[:, :2] = True; border[:, -2:] = True
    boundary = (ndimage.binary_dilation(~portrait, np.ones((3, 3))) | border) & portrait
    boundary_set = set(int(v) for v in np.unique(labels[boundary]) if v != 0)
    min_clothes_px = 0.02 * portrait.sum()  # 衣物级大块：灰衣灰底时大衣碎边不算残留（刘朵平回归 2026-11-18）
    keep = np.zeros_like(portrait)
    for i in range(1, count + 1):
        comp = labels == i
        if not colored:
            # 白/灰底：底部衣物锚点 + 内部块 + 衣物级大块全部保留，只清贴边小碎底
            if (comp[anchor_top:]).any() or i not in boundary_set or comp.sum() >= min_clothes_px:
                keep |= comp
        # 彩色底：一律清除（饱和度判别已挡住深发深底误删）；底部锚点会保护贴底蓝块（支卫国回归 2026-11-18）
    result = (portrait & ~similar) | keep
    if not colored:
        # 灰背景专用清理（刘朵平回归 2026-11-18）：u2net 把领口/肩部的背景灰误判为主体，
        # 色距分布宽（12~90）超出容差。低饱和 + 亮度接近背景 + 贴边/小块的像素视为背景灰。
        brightness = rgb.mean(axis=2)
        bg_bright = float(background.mean())
        grey_bg = (sat < 0.15) & (np.abs(brightness - bg_bright) < 40) & (distance < 100) & result
        if grey_bg.sum() > 0.002 * result.sum():
            gl, gn = ndimage.label(grey_bg)
            gborder = (ndimage.binary_dilation(~result, np.ones((3,3))) | border) & result
            gboundary_set = set(int(v) for v in np.unique(gl[gborder]) if v != 0)
            gkeep = np.zeros_like(result)
            for i in range(1, gn+1):
                gc = gl == i
                if i in gboundary_set:
                    continue  # 贴边灰块一律清除（背景灰）
                if gc.sum() >= 0.05 * result.sum():
                    gkeep |= gc  # 内部大块保留（衣物阴影）
            result = (result & ~grey_bg) | gkeep
    return result


def recover_background_detail(image, portrait):
    """非纯彩色底细节回收（2026-11-18 刘朵平回归：白西装+蓝灰底整块衣襟被 u2net 漏分）。

    白/亮衣物与灰底的色差大（>50）却被模型整块漏掉：以现有掩码为种子，向"与背景
    色差 >50"的相邻区域传播收回。仅彩色度 <48 的底启用；新增像素超过主体 40% 时
    视为异常回退。彩色底不用此通道——深发深底靠残留清理的饱和度判别保护。
    """
    if ndimage is None:
        return portrait
    rgb = np.asarray(image.convert("RGB"), dtype=np.int16)
    background = np.median(rgb[:max(1, rgb.shape[0] // 32)], axis=(0, 1))
    distance = np.max(np.abs(rgb - background), axis=2)
    top = distance[:max(1, rgb.shape[0] // 32)]
    if np.mean(top <= 12) < 0.98 or np.ptp(background) >= 48:
        return portrait
    detail = distance > 50
    grown = ndimage.binary_propagation(portrait, mask=detail | portrait)
    if grown.sum() > portrait.sum() * 1.4:
        return portrait
    labels, count = ndimage.label(grown)
    if count > 1:
        sizes = ndimage.sum(grown, labels, range(1, count + 1))
        grown = labels == (1 + int(np.argmax(sizes)))
    return grown


def refine(image):
    """证件照抠图精修（2026-11-17 修复"仅识别到人头/人像发虚"）。

    根因：u2net 通用显著性检测对人像输出软 alpha（身体多为 128~254 半透明），
    小图（<320px）掩码细节不足，导致抠出的人像发虚、身体衰减甚至只剩头部清晰。
    处理链：分辨率预处理 → 背景分割 → alpha 硬化 → 人物主体提取 → 裁剪边界判定。
    """
    # 1) 分辨率预处理：低像素照片先超分（EDSR/LapSRN），再统一放大到最长边 640
    #    （u2net 内部 320 输入）；大图不放大（Java 侧 resizePortraitInput 已把 >1200 缩到 1200）。
    image = upscale_if_lowres(image)
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
    # 部分纯色背景证件照的衣服与背景接近，模型只分割出头部。
    portrait = recover_solid_background_portrait(image, mask)
    portrait = strip_background_colored_regions(image, portrait)
    portrait = recover_background_detail(image, portrait)
    restored = portrait & ~mask
    # 主体内强制至少 200 不透明（清晰），主体外全透明
    alpha = np.where(portrait, np.maximum(alpha, 200), 0).astype(np.uint8)
    rgba = np.array(out)
    if restored.any():
        rgba[restored] = np.asarray(image.convert("RGBA"))[restored]
        alpha[restored] = 255
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
