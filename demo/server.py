#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
专家库系统 - 轻量 Demo 服务端（SQLite 版）
============================================
仅使用 Python 3.11 标准库实现，无任何第三方依赖（存储升级为 SQLite）。

启动方式：python server.py
监听 0.0.0.0:8000，供公司内网多用户浏览器访问。

页面：
  员工查询页   /            （敏感字段脱敏：身份证/银行卡仅保留前3后4）
  管理员维护页 /admin       （完整数据维护 + 照片上传 + CSV 批量导入/模板下载/导出）

接口：
  GET    /api/experts                  全部专家（?keyword= 模糊过滤；?sensitive=1 返回完整字段，默认脱敏）
  GET    /api/experts/selected/export  按选中 id 集合导出 CSV（?ids=1,2,3；?sensitive=1 完整，默认脱敏）
  GET    /api/experts/selected/zip     按选中 id 集合导出 ZIP（CSV + 对应照片；脱敏规则同上）
  POST   /api/experts                  新增专家（JSON 或 multipart/form-data 含照片）
  PUT    /api/experts/<id>             修改专家（编辑时保留原修改人id，更新修改时间）
  DELETE /api/experts/<id>             删除专家（同时删除其照片文件）
  GET    /api/experts/export           导出全部专家 CSV（完整字段，带 BOM 便于 Excel）
  GET    /api/export                   员工脱敏 ZIP（脱敏 CSV + 照片原图，文件名含日期）
  GET    /api/export/full              管理员完整 ZIP（完整 CSV + 照片原图）
  GET    /api/export/md                筛选数据下载为 Markdown 说明文档（?keyword= 筛选；?full=1 完整版）
  GET    /api/experts/template         CSV 导入模板下载
  POST   /api/experts/import           批量导入 CSV（multipart 上传，返回成功/失败明细）
  GET    /uploads/<照片文件名>          照片静态资源

数据存储：
  SQLite 数据库文件：demo/data/experts.db（15 字段：id、姓名、单位、科室、职称、研究方向、
  擅长领域、联系方式、简介、身份证号、银行卡号、照片、创建时间、修改时间、修改人id）
  原 experts.json 保留为历史备份，不再作为主存储。
"""

import cgi
import csv
import io
import json
import os
import re
import socket
import sqlite3
import threading
import time
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs, quote

# ---------------------------------------------------------------------------
# 路径与常量
# ---------------------------------------------------------------------------
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "data")
DATA_DB = os.path.join(DATA_DIR, "experts.db")
STATIC_DIR = os.path.join(BASE_DIR, "static")
UPLOAD_DIR = os.path.join(BASE_DIR, "uploads")

DATA_FIELDS = [
    "姓名", "单位", "科室", "职称", "研究方向", "擅长领域",
    "联系方式", "简介", "身份证号", "银行卡号", "照片",
]
TIME_FIELDS = ["创建时间", "修改时间", "修改人id"]
EXPORT_COLUMNS = ["id"] + DATA_FIELDS + TIME_FIELDS
TEMPLATE_COLUMNS = [
    "姓名", "单位", "科室", "职称", "研究方向", "擅长领域",
    "联系方式", "简介", "身份证号", "银行卡号",
]
REQUIRED_CSV_COLUMNS = ["姓名", "身份证号", "银行卡号", "联系方式"]

ALLOWED_IMAGE_EXT = {".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"}
MAX_IMAGE_SIZE = 5 * 1024 * 1024  # 5MB
MIME_MAP = {
    ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png",
    ".gif": "image/gif", ".webp": "image/webp", ".bmp": "image/bmp",
}

_lock = threading.RLock()


def now_str():
    t = time.localtime()
    return "%04d-%02d-%02d %02d:%02d:%02d" % (
        t.tm_year, t.tm_mon, t.tm_mday, t.tm_hour, t.tm_min, t.tm_sec)


def detect_lan_ip():
    """探测本机内网 IP（如 192.168.x.x），用于生成可访问的接口地址。"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        if ip and not ip.startswith("127."):
            return ip
    except OSError:
        pass
    try:
        return socket.gethostbyname(socket.gethostname())
    except OSError:
        return "127.0.0.1"


# ---------------------------------------------------------------------------
# SQLite 数据存取（统一 threading.RLock 保护）
# ---------------------------------------------------------------------------
def init_db():
    """确保数据库文件与表结构存在。"""
    os.makedirs(DATA_DIR, exist_ok=True)
    conn = sqlite3.connect(DATA_DB)
    try:
        conn.execute(
            """CREATE TABLE IF NOT EXISTS experts (
                id INTEGER PRIMARY KEY,
                姓名 TEXT NOT NULL,
                单位 TEXT,
                科室 TEXT,
                职称 TEXT,
                研究方向 TEXT,
                擅长领域 TEXT,
                联系方式 TEXT,
                简介 TEXT,
                身份证号 TEXT,
                银行卡号 TEXT,
                照片 TEXT,
                创建时间 TEXT,
                修改时间 TEXT,
                修改人id TEXT
            )"""
        )
        conn.commit()
    finally:
        conn.close()


def load_experts():
    init_db()
    conn = sqlite3.connect(DATA_DB)
    try:
        cur = conn.execute(
            "SELECT %s FROM experts ORDER BY id" % ",".join(EXPORT_COLUMNS)
        )
        rows = cur.fetchall()
    finally:
        conn.close()
    return [dict(zip(EXPORT_COLUMNS, r)) for r in rows]


def save_experts(experts):
    """全量写回 SQLite（单事务，数据量小场景下保证与旧逻辑一致）。"""
    init_db()
    conn = sqlite3.connect(DATA_DB)
    try:
        cur = conn.cursor()
        cur.execute("DELETE FROM experts")
        for e in experts:
            vals = [e.get(f, "") for f in EXPORT_COLUMNS]
            cur.execute(
                "INSERT INTO experts (%s) VALUES (%s)"
                % (",".join(EXPORT_COLUMNS), ",".join("?" * len(EXPORT_COLUMNS))),
                vals,
            )
        conn.commit()
    finally:
        conn.close()


def next_id(experts):
    ids = [e.get("id") for e in experts if isinstance(e.get("id"), int)]
    return (max(ids) + 1) if ids else 1


def normalize_expert(data):
    """把外部提交字段规整为存储结构。"""
    e = {}
    for f in DATA_FIELDS:
        v = data.get(f)
        e[f] = (str(v).strip() if v is not None else "")
    return e


# ---------------------------------------------------------------------------
# 校验与脱敏
# ---------------------------------------------------------------------------
def validate_fields(data, require_sensitive=True):
    """字段校验。require_sensitive=True（新增/导入）强制身份证/银行卡/联系方式；
    False（编辑）时已填字段才校验格式，允许敏感字段暂空待补。"""
    errors = []
    name = str(data.get("姓名", "") or "").strip()
    if not name:
        errors.append("姓名不能为空")

    id_card = str(data.get("身份证号", "") or "").strip()
    bank = str(data.get("银行卡号", "") or "").strip()
    contact = str(data.get("联系方式", "") or "").strip()

    if require_sensitive:
        if not id_card:
            errors.append("身份证号不能为空")
        elif not (len(id_card) == 18
                  and id_card[:17].isdigit()
                  and (id_card[17].isdigit() or id_card[17].upper() == "X")):
            errors.append("身份证号必须为18位（前17位数字，末位数字或X）")
        if not bank:
            errors.append("银行卡号不能为空")
        elif not (bank.isdigit() and 13 <= len(bank) <= 19):
            errors.append("银行卡号须为13-19位数字")
        if not contact:
            errors.append("联系方式不能为空")
        elif not (len(contact) == 11 and contact.isdigit() and contact.startswith("1")
                  or ("@" in contact and "." in contact)):
            errors.append("联系方式格式不正确（11位手机号或邮箱）")
    else:
        if id_card and not (len(id_card) == 18
                            and id_card[:17].isdigit()
                            and (id_card[17].isdigit() or id_card[17].upper() == "X")):
            errors.append("身份证号必须为18位（前17位数字，末位数字或X）")
        if bank and not (bank.isdigit() and 13 <= len(bank) <= 19):
            errors.append("银行卡号须为13-19位数字")
        if contact and not (len(contact) == 11 and contact.isdigit() and contact.startswith("1")
                            or ("@" in contact and "." in contact)):
            errors.append("联系方式格式不正确（11位手机号或邮箱）")
    return errors


def mask_sensitive(value):
    s = str(value or "")
    if len(s) <= 7:
        return s
    return s[:3] + "****" + s[-4:]


def to_public(expert):
    e = dict(expert)
    e["身份证号"] = mask_sensitive(e.get("身份证号"))
    e["银行卡号"] = mask_sensitive(e.get("银行卡号"))
    return e


# ---------------------------------------------------------------------------
# 照片处理
# ---------------------------------------------------------------------------
def save_uploaded_photo(file_item, expert_id):
    """保存上传照片到 uploads/，返回存储文件名；无文件返回 None。"""
    if file_item is None:
        return None
    filename = getattr(file_item, "filename", "") or ""
    filename = os.path.basename(filename)
    ext = os.path.splitext(filename)[1].lower()
    if not filename or ext not in ALLOWED_IMAGE_EXT:
        raise ValueError("照片格式仅支持 jpg/jpeg/png/gif/webp/bmp")
    raw = file_item.file.read() if hasattr(file_item, "file") else b""
    if not raw:
        raise ValueError("照片文件为空")
    if len(raw) > MAX_IMAGE_SIZE:
        raise ValueError("照片大小不能超过5MB")
    os.makedirs(UPLOAD_DIR, exist_ok=True)
    new_name = "avatar_%d_%d%s" % (expert_id, int(time.time()), ext)
    with open(os.path.join(UPLOAD_DIR, new_name), "wb") as f:
        f.write(raw)
    return new_name


def delete_photo_file(filename):
    if not filename:
        return
    full = os.path.join(UPLOAD_DIR, os.path.basename(filename))
    if os.path.isfile(full):
        try:
            os.remove(full)
        except OSError:
            pass


# ---------------------------------------------------------------------------
# HTTP 处理器
# ---------------------------------------------------------------------------
class ExpertHandler(BaseHTTPRequestHandler):
    server_version = "ExpertDemo/3.0"

    # ---------- 基础工具 ----------
    def _send_bytes(self, body, status=200, ctype="application/json; charset=utf-8",
                    headers=None):
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if body:
            self.wfile.write(body)

    def _send_json(self, obj, status=200):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self._send_bytes(body, status)

    def _send_error(self, message, status=400):
        self._send_json({"error": message}, status)

    def _parse_json_body(self):
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            return None, "Content-Length 无效"
        raw = self.rfile.read(length) if length else b""
        if not raw:
            return {}, None
        try:
            data = json.loads(raw.decode("utf-8"))
        except Exception:
            return None, "请求体不是合法 JSON"
        if not isinstance(data, dict):
            return None, "请求体应为 JSON 对象"
        return data, None

    def _parse_multipart(self):
        try:
            form = cgi.FieldStorage(
                fp=self.rfile,
                headers=self.headers,
                environ={
                    "REQUEST_METHOD": self.command,
                    "CONTENT_TYPE": self.headers.get("Content-Type", ""),
                    "CONTENT_LENGTH": self.headers.get("Content-Length", "0"),
                },
            )
        except Exception as exc:
            return None, None, "multipart 解析失败: %s" % exc
        data = {}
        for key in DATA_FIELDS + ["修改人id"]:
            v = form.getvalue(key)
            if v is not None:
                data[key] = v if isinstance(v, str) else v.decode("utf-8", "ignore")
        file_item = form["照片"] if ("照片" in form and form["照片"].filename) else None
        return data, file_item, None

    def _parse_ids(self, qs):
        """解析 ?ids=1,2,3 参数，返回 int 列表；无效返回 None。"""
        ids_str = (qs.get("ids") or [""])[0].strip()
        if not ids_str:
            return None
        ids = []
        for part in ids_str.split(","):
            part = part.strip()
            if part.isdigit():
                ids.append(int(part))
        return ids or None

    # ---------- GET ----------
    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        qs = parse_qs(parsed.query)

        # 照片静态资源
        if path.startswith("/uploads/"):
            self._serve_upload(path[len("/uploads/"):])
            return

        # API：专家列表
        if path == "/api/experts":
            keyword = (qs.get("keyword") or [""])[0].strip()
            sensitive = (qs.get("sensitive") or ["0"])[0] in ("1", "true", "yes")
            with _lock:
                experts = load_experts()
            if keyword:
                experts = [e for e in experts if self._match(e, keyword)]
            if not sensitive:
                experts = [to_public(e) for e in experts]
            self._send_json({"total": len(experts), "items": experts})
            return

        # 按选中 id 集合导出 CSV（默认脱敏；sensitive=1 完整）
        if path == "/api/experts/selected/export":
            ids = self._parse_ids(qs)
            if not ids:
                self._send_error("请通过 ids 参数指定要导出的专家ID（逗号分隔，如 ids=1,2,3）", 400)
                return
            sensitive = (qs.get("sensitive") or ["0"])[0] in ("1", "true", "yes")
            with _lock:
                experts = [e for e in load_experts() if e.get("id") in ids]
            experts.sort(key=lambda e: ids.index(e["id"]))
            buf = io.StringIO()
            writer = csv.writer(buf)
            writer.writerow(EXPORT_COLUMNS)
            for e in experts:
                row = [e.get(c, "") for c in EXPORT_COLUMNS]
                if not sensitive:
                    pub = to_public(e)
                    row[EXPORT_COLUMNS.index("身份证号")] = pub.get("身份证号", "")
                    row[EXPORT_COLUMNS.index("银行卡号")] = pub.get("银行卡号", "")
                writer.writerow(row)
            filename = "experts_selected_%s.csv" % time.strftime("%Y%m%d_%H%M%S")
            self._send_bytes(
                ("\ufeff" + buf.getvalue()).encode("utf-8"),
                ctype="text/csv; charset=utf-8",
                headers={"Content-Disposition": 'attachment; filename="%s"' % filename},
            )
            return

        # 按选中 id 集合导出 ZIP（CSV + 对应照片；脱敏规则同上）
        if path == "/api/experts/selected/zip":
            ids = self._parse_ids(qs)
            if not ids:
                self._send_error("请通过 ids 参数指定要导出的专家ID（逗号分隔，如 ids=1,2,3）", 400)
                return
            sensitive = (qs.get("sensitive") or ["0"])[0] in ("1", "true", "yes")
            with _lock:
                experts = [e for e in load_experts() if e.get("id") in ids]
            experts.sort(key=lambda e: ids.index(e["id"]))
            buf = io.StringIO()
            writer = csv.writer(buf)
            writer.writerow(EXPORT_COLUMNS)
            for e in experts:
                row = [e.get(c, "") for c in EXPORT_COLUMNS]
                if not sensitive:
                    pub = to_public(e)
                    row[EXPORT_COLUMNS.index("身份证号")] = pub.get("身份证号", "")
                    row[EXPORT_COLUMNS.index("银行卡号")] = pub.get("银行卡号", "")
                writer.writerow(row)
            zip_buf = io.BytesIO()
            with zipfile.ZipFile(zip_buf, "w", zipfile.ZIP_DEFLATED) as zf:
                zf.writestr("experts_selected.csv", ("\ufeff" + buf.getvalue()).encode("utf-8"))
                added = set()
                for e in experts:
                    photo = os.path.basename(str(e.get("照片") or ""))
                    if not photo or photo in added:
                        continue
                    full = os.path.join(UPLOAD_DIR, photo)
                    if not os.path.isfile(full):
                        continue
                    added.add(photo)
                    with open(full, "rb") as pf:
                        zf.writestr("photos/%s" % photo, pf.read())
            filename = "experts_selected_%s.zip" % time.strftime("%Y%m%d_%H%M%S")
            self._send_bytes(
                zip_buf.getvalue(),
                ctype="application/zip",
                headers={"Content-Disposition": 'attachment; filename="%s"' % filename},
            )
            return

        # 筛选数据下载为 Markdown 说明文档（员工脱敏版；管理员 ?full=1 完整版）
        if path == "/api/export/md":
            keyword = (qs.get("keyword") or [""])[0].strip()
            full = (qs.get("full") or ["0"])[0] in ("1", "true", "yes")
            self._send_export_md(keyword=keyword, full=full)
            return

        # 员工脱敏 ZIP 下载（脱敏 CSV + 照片原图）
        if path == "/api/export":
            self._send_export_zip(sensitive=True, name_prefix="experts")
            return

        # 管理员完整 ZIP 下载（完整 CSV + 照片原图）
        if path == "/api/export/full":
            self._send_export_zip(sensitive=False, name_prefix="experts_full")
            return

        # CSV 导出（完整字段）
        if path == "/api/experts/export":
            with _lock:
                experts = load_experts()
            buf = io.StringIO()
            writer = csv.writer(buf)
            writer.writerow(EXPORT_COLUMNS)
            for e in experts:
                writer.writerow([e.get(c, "") for c in EXPORT_COLUMNS])
            filename = "experts_%s.csv" % time.strftime("%Y%m%d_%H%M%S")
            self._send_bytes(
                ("\ufeff" + buf.getvalue()).encode("utf-8"),
                ctype="text/csv; charset=utf-8",
                headers={"Content-Disposition": 'attachment; filename="%s"' % filename},
            )
            return

        # CSV 模板下载
        if path == "/api/experts/template":
            buf = io.StringIO()
            writer = csv.writer(buf)
            writer.writerow(TEMPLATE_COLUMNS)
            writer.writerow([
                "示例姓名", "示例单位", "示例科室", "高级工程师", "示例方向",
                "示例领域", "13800138000", "示例简介", "110101199001011234", "6222020212345678901",
            ])
            self._send_bytes(
                ("\ufeff" + buf.getvalue()).encode("utf-8"),
                ctype="text/csv; charset=utf-8",
                headers={"Content-Disposition": 'attachment; filename="expert_template.csv"'},
            )
            return

        # 静态页面
        if path == "/":
            self._serve_static("index.html")
            return
        if path == "/admin":
            self._serve_static("admin.html")
            return
        if path in ("/favicon.ico",):
            self._send_bytes(b"", 404)
            return
        self._send_error("未知接口", 404)

    def _match(self, expert, keyword):
        kw = keyword.lower()
        fields = ["姓名", "单位", "科室", "职称", "研究方向", "擅长领域", "联系方式", "简介"]
        return any(kw in str(expert.get(f, "") or "").lower() for f in fields)

    def _serve_static(self, name):
        full = os.path.join(STATIC_DIR, name)
        if not os.path.isfile(full):
            self._send_error("页面不存在", 404)
            return
        ext = os.path.splitext(name)[1].lower()
        ctype = {
            ".html": "text/html; charset=utf-8",
            ".js": "application/javascript; charset=utf-8",
            ".css": "text/css; charset=utf-8",
        }.get(ext, "application/octet-stream")
        with open(full, "rb") as f:
            self._send_bytes(f.read(), ctype=ctype)

    def _serve_upload(self, filename):
        filename = os.path.basename(filename)
        if not filename:
            self._send_error("文件不存在", 404)
            return
        ext = os.path.splitext(filename)[1].lower()
        full = os.path.join(UPLOAD_DIR, filename)
        if not os.path.isfile(full):
            self._send_error("文件不存在", 404)
            return
        ctype = MIME_MAP.get(ext, "application/octet-stream")
        with open(full, "rb") as f:
            self._send_bytes(f.read(), ctype=ctype)

    def _send_export_zip(self, sensitive=True, name_prefix="experts"):
        """生成 ZIP 压缩包：CSV（脱敏或完整，带 BOM）+ 照片原图。"""
        with _lock:
            experts = load_experts()
        buf = io.StringIO()
        writer = csv.writer(buf)
        writer.writerow(EXPORT_COLUMNS)
        for e in experts:
            row = [e.get(c, "") for c in EXPORT_COLUMNS]
            if sensitive:
                pub = to_public(e)
                row[EXPORT_COLUMNS.index("身份证号")] = pub.get("身份证号", "")
                row[EXPORT_COLUMNS.index("银行卡号")] = pub.get("银行卡号", "")
            writer.writerow(row)

        zip_buf = io.BytesIO()
        with zipfile.ZipFile(zip_buf, "w", zipfile.ZIP_DEFLATED) as zf:
            zf.writestr("experts.csv", ("\ufeff" + buf.getvalue()).encode("utf-8"))
            added = set()
            for e in experts:
                photo = os.path.basename(str(e.get("照片") or ""))
                if not photo:
                    continue
                if photo in added:
                    continue
                full = os.path.join(UPLOAD_DIR, photo)
                if not os.path.isfile(full):
                    continue
                added.add(photo)
                with open(full, "rb") as pf:
                    zf.writestr("photos/%s" % photo, pf.read())
        zip_bytes = zip_buf.getvalue()
        filename = "%s_%s.zip" % (name_prefix, time.strftime("%Y%m%d_%H%M%S"))
        self._send_bytes(
            zip_bytes,
            ctype="application/zip",
            headers={"Content-Disposition": 'attachment; filename="%s"' % filename},
        )

    def _send_export_md(self, keyword="", full=False):
        """生成 Markdown 说明文档：接口、字段索引、图片命名规则、筛选结果清单、AI 下载指引。"""
        with _lock:
            all_experts = load_experts()
        experts = [e for e in all_experts if self._match(e, keyword)] if keyword else list(all_experts)
        ip = detect_lan_ip()
        root = "http://%s:8000" % ip
        gen_time = now_str()
        count = len(experts)
        kw_display = keyword if keyword else "（全部）"
        kw_filename = re.sub(r'[^A-Za-z0-9._-]+', "_", keyword).strip("_") if keyword else "all"
        if not kw_filename:
            kw_filename = "ai"
        kw_url = quote(keyword) if keyword else ""

        lines = []
        lines.append("# 专家库系统 - 筛选数据下载说明")
        lines.append("")
        lines.append("> 本文档由专家库系统自动生成，供 AI 据此把数据与图片下载到本地对应文件夹。")
        lines.append("")
        lines.append("## 元信息")
        lines.append("")
        lines.append("| 项目 | 内容 |")
        lines.append("|---|---|")
        lines.append("| 生成时间 | %s |" % gen_time)
        lines.append("| 筛选条件 | %s |" % kw_display)
        lines.append("| 命中数据条数 | %d |" % count)
        lines.append("")
        lines.append("## 一、系统内网接口")
        lines.append("")
        lines.append("服务根地址：`%s`（内网环境，需与服务器同一局域网访问）" % root)
        lines.append("")
        lines.append("| 用途 | 接口 | 说明 |")
        lines.append("|---|---|---|")
        lines.append("| 专家数据接口 | `GET %s/api/experts?keyword=xxx` | 返回全部/筛选专家 JSON 列表（员工默认脱敏，管理员 `?sensitive=1` 返回完整） |" % root)
        lines.append("| 选择导出接口 | `GET %s/api/experts/selected/export?ids=1,2,3` | 按选中 id 集合导出 CSV（默认脱敏，`&sensitive=1` 完整） |" % root)
        lines.append("| 选择导出接口 | `GET %s/api/experts/selected/zip?ids=1,2,3` | 按选中 id 集合导出 CSV+照片 ZIP（脱敏规则同上） |" % root)
        lines.append("| 完整导出接口（管理员ZIP） | `GET %s/api/export/full` | 完整字段 CSV + 照片原图 ZIP |" % root)
        lines.append("| 脱敏导出接口（员工ZIP） | `GET %s/api/export` | 脱敏 CSV + 照片原图 ZIP |" % root)
        lines.append("| 图片访问接口 | `GET %s/uploads/{文件名}` | 按数据中“照片”字段值直接访问图片 |" % root)
        lines.append("| 本次 MD 接口自身 | `GET %s/api/export/md?keyword=xxx` | 生成并下载本说明文档（管理员加 `&full=1` 为完整版） |" % root)
        lines.append("")
        lines.append("## 二、所需信息字段索引")
        lines.append("")
        lines.append("以下为专家数据全部字段（含含义、示例与脱敏说明）：")
        lines.append("")
        lines.append("| 字段名 | 含义 | 示例值 | 是否脱敏 |")
        lines.append("|---|---|---|---|")
        fields_index = [
            ("id", "专家唯一编号（自增）", "1", "否"),
            ("姓名", "专家姓名", "张建国", "否"),
            ("单位", "所属单位（新增字段）", "南方医科大学坪山医院", "否"),
            ("科室", "所属科室（旧数据“部门”已迁移）", "人工智能实验室", "否"),
            ("职称", "专家职称", "教授级高级工程师", "否"),
            ("研究方向", "主要研究方向", "机器学习与计算机视觉", "否"),
            ("擅长领域", "擅长领域描述", "图像识别、目标检测", "否"),
            ("联系方式", "手机号或邮箱", "13800138000", "否"),
            ("简介", "个人简介", "从事 AI 研究 20 年", "否"),
            ("身份证号", "身份证号码", "110****7894（完整18位）", "员工版脱敏：仅保留前3后4"),
            ("银行卡号", "银行卡号", "622****8902（完整13-19位）", "员工版脱敏：仅保留前3后4"),
            ("照片", "照片文件名（对应 uploads/ 下文件）", "avatar_1_1699000000.jpg", "否；无照片为空"),
            ("创建时间", "数据创建时间", "2025-06-10 09:00:00", "否"),
            ("修改时间", "数据最后修改时间", "2025-06-12 10:20:00", "否"),
            ("修改人id", "最后修改人标识", "admin", "否"),
        ]
        for fname, meaning, example, mask in fields_index:
            lines.append("| %s | %s | %s | %s |" % (fname, meaning, example, mask))
        lines.append("")
        lines.append("## 三、图片命名规则")
        lines.append("")
        lines.append("- 照片存储目录：服务端 `uploads/` 目录（与 `server.py` 同级）。")
        lines.append("- 命名规则：`avatar_<专家id>_<时间戳>.<扩展名>`（形如 `avatar_1_1699000000.jpg`，实际为“avatar_专家id_时间戳”存储形式）。")
        lines.append("- 数据中“照片”字段值与 `uploads/` 目录内文件名一一对应：访问 `%s/uploads/{照片字段值}` 即可下载原图。" % root)
        lines.append("- 无照片的专家“照片”字段为空字符串，不产生图片下载任务。")
        lines.append("")
        lines.append("## 四、本次筛选结果清单")
        lines.append("")
        lines.append("| id | 姓名 | 单位 | 科室 | 照片文件名 |")
        lines.append("|---|---|---|---|---|")
        for e in experts:
            rec = to_public(e) if not full else e
            pid = rec.get("id", "")
            pname = rec.get("姓名", "")
            punit = rec.get("单位", "")
            pdept = rec.get("科室", "")
            pphoto = rec.get("照片", "")
            lines.append("| %s | %s | %s | %s | %s |" % (pid, pname, punit, pdept, pphoto))
        lines.append("")
        lines.append("## 五、AI 下载指引")
        lines.append("")
        lines.append("请 AI 按以下步骤把数据与图片下载到本地：")
        lines.append("")
        lines.append("1. 调用专家数据接口获取 JSON：`GET %s/api/experts?keyword=%s`（员工脱敏版，推荐）或 `GET %s/api/experts?keyword=%s&sensitive=1`（管理员完整版）。" % (root, kw_url, root, kw_url))
        lines.append("2. 解析 JSON 中 `items` 数组，每条记录的“照片”字段即图片文件名；字段含义见“二、所需信息字段索引”。")
        lines.append("3. 依次下载图片：对每条记录按 `GET %s/uploads/{照片字段值}` 请求图片，保存到本地 `photos/` 文件夹；“照片”为空的记录跳过。" % root)
        lines.append("4. 将专家数据（JSON/CSV 均可）保存到本地 `data/` 文件夹，文件命名建议按 `id` 或 `姓名` 区分，便于后续检索。")
        lines.append("5. 下载完成后核对“四、本次筛选结果清单”中命中条数，确认无遗漏。")
        lines.append("")

        md_text = "\n".join(lines)
        filename = "expert_download_%s_%s.md" % (time.strftime("%Y%m%d"), kw_filename)
        self._send_bytes(
            md_text.encode("utf-8"),
            ctype="text/markdown; charset=utf-8",
            headers={"Content-Disposition": 'attachment; filename="%s"' % filename},
        )

    # ---------- POST ----------
    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        ctype = self.headers.get("Content-Type", "")
        is_multipart = "multipart/form-data" in ctype

        # 批量导入 CSV
        if path == "/api/experts/import":
            self._handle_import(is_multipart)
            return

        # 新增专家
        if path == "/api/experts":
            self._handle_create(is_multipart)
            return

        self._send_error("未知接口", 404)

    def _handle_create(self, is_multipart):
        if is_multipart:
            data, photo_item, err = self._parse_multipart()
        else:
            data, err = self._parse_json_body()
            photo_item = None
        if err:
            self._send_error(err, 400)
            return
        errors = validate_fields(data, require_sensitive=True)
        if errors:
            self._send_error("；".join(errors), 400)
            return
        with _lock:
            experts = load_experts()
            expert = normalize_expert(data)
            expert["id"] = next_id(experts)
            ts = now_str()
            expert["创建时间"] = ts
            expert["修改时间"] = ts
            expert["修改人id"] = str(data.get("修改人id") or "admin").strip() or "admin"
            if photo_item is not None:
                try:
                    expert["照片"] = save_uploaded_photo(photo_item, expert["id"]) or ""
                except ValueError as ve:
                    self._send_error(str(ve), 400)
                    return
            experts.append(expert)
            save_experts(experts)
        self._send_json(expert, 201)

    # ---------- PUT ----------
    def do_PUT(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        if not path.startswith("/api/experts/"):
            self._send_error("未知接口", 404)
            return
        try:
            expert_id = int(path.rsplit("/", 1)[1])
        except ValueError:
            self._send_error("专家ID无效", 400)
            return
        ctype = self.headers.get("Content-Type", "")
        is_multipart = "multipart/form-data" in ctype
        if is_multipart:
            data, photo_item, err = self._parse_multipart()
        else:
            data, err = self._parse_json_body()
            photo_item = None
        if err:
            self._send_error(err, 400)
            return
        errors = validate_fields(data, require_sensitive=False)
        if errors:
            self._send_error("；".join(errors), 400)
            return
        with _lock:
            experts = load_experts()
            for idx, old in enumerate(experts):
                if old.get("id") != expert_id:
                    continue
                expert = normalize_expert(data)
                expert["id"] = expert_id
                expert["创建时间"] = old.get("创建时间") or now_str()
                expert["修改时间"] = now_str()
                # 编辑已有数据时保留原修改人
                expert["修改人id"] = old.get("修改人id") or "admin"
                if photo_item is not None:
                    try:
                        new_photo = save_uploaded_photo(photo_item, expert_id)
                    except ValueError as ve:
                        self._send_error(str(ve), 400)
                        return
                    delete_photo_file(old.get("照片"))
                    expert["照片"] = new_photo or ""
                else:
                    expert["照片"] = old.get("照片") or ""
                experts[idx] = expert
                save_experts(experts)
                self._send_json(expert)
                return
            self._send_error("专家不存在", 404)

    # ---------- DELETE ----------
    def do_DELETE(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        if not path.startswith("/api/experts/"):
            self._send_error("未知接口", 404)
            return
        try:
            expert_id = int(path.rsplit("/", 1)[1])
        except ValueError:
            self._send_error("专家ID无效", 400)
            return
        with _lock:
            experts = load_experts()
            for old in experts:
                if old.get("id") == expert_id:
                    delete_photo_file(old.get("照片"))
                    experts.remove(old)
                    save_experts(experts)
                    self._send_json({"ok": True, "id": expert_id})
                    return
            self._send_error("专家不存在", 404)

    # ---------- CSV 导入 ----------
    def _handle_import(self, is_multipart):
        if not is_multipart:
            self._send_error("请通过 multipart 上传 CSV 文件", 400)
            return
        try:
            form = cgi.FieldStorage(
                fp=self.rfile,
                headers=self.headers,
                environ={
                    "REQUEST_METHOD": "POST",
                    "CONTENT_TYPE": self.headers.get("Content-Type", ""),
                    "CONTENT_LENGTH": self.headers.get("Content-Length", "0"),
                },
            )
        except Exception as exc:
            self._send_error("multipart 解析失败: %s" % exc, 400)
            return
        if "file" not in form or not form["file"].filename:
            self._send_error("请选择要导入的 CSV 文件", 400)
            return
        file_item = form["file"]
        filename = os.path.basename(file_item.filename or "")
        if not filename.lower().endswith(".csv"):
            self._send_error("仅支持 .csv 文件", 400)
            return
        raw = file_item.file.read()
        text = None
        for enc in ("utf-8-sig", "utf-8", "gbk"):
            try:
                text = raw.decode(enc)
                break
            except UnicodeDecodeError:
                continue
        if text is None:
            self._send_error("CSV 编码无法识别（支持 UTF-8 / GBK）", 400)
            return

        reader = csv.reader(io.StringIO(text))
        rows = [r for r in reader if r and any(c.strip() for c in r)]
        if len(rows) < 2:
            self._send_error("CSV 为空或缺少数据行", 400)
            return
        header = [h.strip() for h in rows[0]]
        missing = [c for c in REQUIRED_CSV_COLUMNS if c not in header]
        if missing:
            self._send_error("模板表头缺少字段：%s" % "、".join(missing), 400)
            return
        col_index = {h: i for i, h in enumerate(header)}

        def get_col(row, col):
            idx = col_index.get(col)
            return row[idx].strip() if (idx is not None and idx < len(row)) else ""

        success = 0
        failed = []
        with _lock:
            experts = load_experts()
            existing_names = {e.get("姓名") for e in experts}
            for line_no, row in enumerate(rows[1:], start=2):
                record = {c: get_col(row, c) for c in TEMPLATE_COLUMNS}
                name = record.get("姓名", "")
                if not name:
                    failed.append({"line": line_no, "name": "", "reason": "姓名不能为空"})
                    continue
                if name in existing_names:
                    failed.append({"line": line_no, "name": name, "reason": "姓名已存在（重复）"})
                    continue
                errors = validate_fields(record, require_sensitive=True)
                if errors:
                    failed.append({"line": line_no, "name": name, "reason": "；".join(errors)})
                    continue
                ts = now_str()
                expert = normalize_expert(record)
                expert["id"] = next_id(experts)
                expert["创建时间"] = ts
                expert["修改时间"] = ts
                expert["修改人id"] = "admin"
                expert["照片"] = ""
                experts.append(expert)
                existing_names.add(name)
                success += 1
            save_experts(experts)
        self._send_json({"success": success, "failed": failed, "total": success + len(failed)})

    def log_message(self, fmt, *args):
        pass


# ---------------------------------------------------------------------------
# 启动
# ---------------------------------------------------------------------------
def bootstrap():
    """启动前初始化 SQLite 数据库与 uploads 目录。"""
    os.makedirs(UPLOAD_DIR, exist_ok=True)
    init_db()


def main():
    bootstrap()
    host = "0.0.0.0"
    port = int(os.environ.get("PORT", "8000"))
    server = ThreadingHTTPServer((host, port), ExpertHandler)
    print("专家库系统 Demo 已启动（SQLite 版）")
    print("  内网访问地址 : http://<本机内网IP>:%d/" % port)
    print("  管理员维护页 : http://<本机内网IP>:%d/admin" % port)
    print("  员工查询页   : http://<本机内网IP>:%d/ (默认端口8000)" % port)
    print("  数据库文件   : %s" % DATA_DB)
    print("  照片目录     : %s" % UPLOAD_DIR)
    print("按 Ctrl+C 停止服务")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n服务已停止")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
