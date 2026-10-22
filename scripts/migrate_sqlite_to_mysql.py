#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
专家库系统正式版 V1.001 第三阶段：SQLite -> MySQL 迁移脚本
============================================================
数据源    : D:\\jshz-PMO\\demo\\data\\experts.db  (SQLite, 表 experts, 148 条, 15 中文字段)
照片源    : D:\\jshz-PMO\\demo\\uploads         (按文件名引用)
目标库    : expert_system (MySQL 8, 由 init.sql 建库建表)
目标表    : expert_info (18 英文列) / sys_user (账号=姓名)

安全设计:
  1. 目标表已有非空数据时直接退出(不覆盖、不删除)，需人工确认后使用 --force-clear 显式清空
  2. 迁移在单事务中执行，任一步失败自动回滚
  3. 照片复制使用"存在则跳过"策略，不覆盖已有文件
  4. 密码统一 111111 的 BCrypt 哈希(与 init.sql 种子一致)

用法:
  python migrate_sqlite_to_mysql.py --mysql-user root --mysql-password xxx [--mysql-host 127.0.0.1 --mysql-port 3306 --mysql-db expert_system --force-clear]
"""
import argparse
import os
import shutil
import sqlite3

import pymysql

BCRYPT_111111 = "$2a$10$FECPAy5iIj1rMBwiGKRc0uxg5b8kIlO4lSnHLowNxtdMrJyAo0brK"

SQLITE_DB = r"D:\jshz-PMO\demo\data\experts.db"
PHOTO_SRC_DIR = r"D:\jshz-PMO\demo\uploads"
PHOTO_DST_DIR = r"D:\jshz-PMO\照片库"

# SQLite 中文字段 -> expert_info 英文列 映射
FIELD_MAP = {
    "姓名": "name",
    "单位": "unit",
    "科室": "department",
    "职称": "title",
    "研究方向": "research_direction",
    "擅长领域": "expertise_area",
    "联系方式": "contact",
    "简介": "profile",
    "身份证号": "id_card",
    "银行卡号": "bank_card",
    "照片": "photo",
    "创建时间": "create_time",
    "修改时间": "update_time",
    "修改人id": "update_by",
    "id": "id",
}
# 迁移后留空待补的字段
EMPTY_FIELDS = ["bank_branch", "id_card_front", "id_card_back"]

COLUMNS = ["id", "name", "unit", "department", "title", "research_direction",
           "expertise_area", "contact", "profile", "id_card", "bank_card",
           "photo", "bank_branch", "id_card_front", "id_card_back",
           "create_time", "update_time", "update_by"]


def load_sqlite_rows():
    con = sqlite3.connect(SQLITE_DB)
    con.row_factory = sqlite3.Row
    cur = con.cursor()
    cur.execute("SELECT * FROM experts ORDER BY id")
    rows = [dict(r) for r in cur.fetchall()]
    con.close()
    return rows


def copy_photos(rows):
    """将照片文件从 demo/uploads 复制到照片库目录；返回 (复制成功数, 跳过数, 缺失列表)。
    rows 为已映射后的 expert 记录（键为英文列名）。"""
    os.makedirs(PHOTO_DST_DIR, exist_ok=True)
    copied, skipped, missing = 0, 0, []
    for row in rows:
        photo = (row.get("photo") or "").strip()
        if not photo:
            continue
        src = os.path.join(PHOTO_SRC_DIR, photo)
        dst = os.path.join(PHOTO_DST_DIR, photo)
        if not os.path.isfile(src):
            missing.append(photo)
            continue
        if os.path.isfile(dst):
            skipped += 1
            continue
        shutil.copy2(src, dst)
        copied += 1
    return copied, skipped, missing


def build_expert_row(srow):
    rec = {col: None for col in COLUMNS}
    for cn, en in FIELD_MAP.items():
        v = srow.get(cn)
        rec[en] = v if v is not None and str(v).strip() != "" else None
    # 创建/修改时间：SQLite 文本 'YYYY-MM-DD HH:MM:SS' 直接可用；空则保持 NULL
    for tcol in ("create_time", "update_time"):
        v = rec.get(tcol)
        if v is not None:
            rec[tcol] = str(v)[:19]
    return rec


def main():
    ap = argparse.ArgumentParser(description="SQLite -> MySQL 迁移")
    ap.add_argument("--mysql-host", default="127.0.0.1")
    ap.add_argument("--mysql-port", type=int, default=3306)
    ap.add_argument("--mysql-user", default="root")
    ap.add_argument("--mysql-password", default="")
    ap.add_argument("--mysql-db", default="expert_system")
    ap.add_argument("--force-clear", action="store_true",
                    help="目标表 expert_info / sys_user 存在数据时先清空（危险操作，需显式指定）")
    args = ap.parse_args()

    rows = load_sqlite_rows()
    print(f"[1/5] 读取 SQLite 数据源: {SQLITE_DB} -> {len(rows)} 条")

    expert_rows = [build_expert_row(r) for r in rows]
    # 照片迁移（物理复制）
    copied, skipped, missing = copy_photos(expert_rows)
    print(f"[2/5] 照片复制完成: 新复制 {copied}, 已存在跳过 {skipped}")
    if missing:
        print(f"      注意: {len(missing)} 个照片文件在源目录缺失: {missing[:5]}")
    # photo 字段保留相对文件名（与照片库目录对应）

    conn = pymysql.connect(host=args.mysql_host, port=args.mysql_port,
                           user=args.mysql_user, password=args.mysql_password,
                           database=args.mysql_db, charset="utf8mb4", autocommit=False)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=%s AND TABLE_NAME IN ('expert_info','sys_user')", (args.mysql_db,))
            existing = cur.fetchone()[0]
            if existing != 2:
                raise SystemExit(f"[错误] 目标库 {args.mysql_db} 缺少 expert_info/sys_user 表，请先执行 init.sql。当前命中表数={existing}")
            for tbl in ("expert_info", "sys_user"):
                cur.execute(f"SELECT COUNT(*) FROM `{tbl}`")
                n = cur.fetchone()[0]
                print(f"[3/5] 目标表 {tbl} 当前行数 = {n}")
                if n > 0:
                    if not args.force_clear:
                        raise SystemExit(f"[错误] {tbl} 已有 {n} 行数据，为防误覆盖已中止。"
                                         f"如确认要清空重迁，请加 --force-clear 参数")
                    if tbl == "expert_info":
                        cur.execute("DELETE FROM `expert_info`")
                    else:
                        # sys_user 保留管理员账号（is_admin=1），仅清除普通员工，避免丢失 admin 种子
                        cur.execute("DELETE FROM `sys_user` WHERE is_admin = 0")
                    print(f"      已按 --force-clear 处理 {tbl}（sys_user 保留管理员）")
    except SystemExit:
        conn.close()
        raise
    except Exception as e:
        conn.close()
        raise

    try:
        with conn.cursor() as cur:
            # 插入专家数据
            placeholders = ",".join(["%s"] * len(COLUMNS))
            cols_sql = ",".join(COLUMNS)
            sql = f"INSERT INTO expert_info ({cols_sql}) VALUES ({placeholders})"
            for rec in expert_rows:
                cur.execute(sql, [rec[c] for c in COLUMNS])

            # 同步 sys_user: 148 名专家姓名作账号
            sql_u = ("INSERT INTO sys_user (username, password, is_admin, wecom_userid, must_change_password, status) "
                     "VALUES (%s, %s, 0, NULL, 1, 1)")
            usernames = []
            for rec in expert_rows:
                if not rec["name"] or rec["name"] in usernames:
                    continue
                usernames.append(rec["name"])
                cur.execute(sql_u, (rec["name"], BCRYPT_111111))

            conn.commit()
            cur.execute("SELECT COUNT(*) FROM expert_info")
            ec = cur.fetchone()[0]
            cur.execute("SELECT COUNT(*) FROM sys_user")
            uc = cur.fetchone()[0]
            print(f"[4/5] 提交成功: expert_info={ec} 行, sys_user={uc} 行(其中专家账号 {len(usernames)})")
    except Exception as e:
        conn.rollback()
        conn.close()
        raise SystemExit(f"[错误] 事务已回滚: {type(e).__name__}: {e}")

    conn.close()
    print(f"[5/5] 迁移完成。照片库目录: {PHOTO_DST_DIR}")


if __name__ == "__main__":
    main()
