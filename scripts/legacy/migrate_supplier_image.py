#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
供应商模块改造 v2.0「三、图片与主图」：存量图片迁移脚本（幂等可回滚）
================================================================
职责：
  1) apply（默认）：把 supplier.images JSON 数组迁移为 supplier_image 行
     - 按数组顺序生成 sort_order（0 起）；
     - 数组第一张设 is_main=1（主图选定）；
     - 完成迁移与主图选定后，再创建「部分唯一索引」等效约束：
       MySQL 不支持 partial index，采用生成列 main_key=IF(is_main=1,supplier_id,NULL)
       + 唯一索引 uk_supplier_main(supplier_id, main_key) 等效
       （is_main=0 时 main_key=NULL 不参与唯一约束），避免迁移期间唯一约束冲突；
     - 迁移后将 supplier.images 置空，避免双轨；重复执行幂等（仅处理未迁移行）。
  2) rollback：删除唯一索引与生成列，把 supplier_image 行按 sort_order 还原回
     supplier.images JSON 数组（仅恢复被迁移置空的供应商），并清空 supplier_image 表。
用法：
  python migrate_supplier_image.py [--host 127.0.0.1] [--port 3306] [--db expert_system]
                                   [--user root] [--password 密码] [--rollback] [--storage-dir 供应商图库目录]
依赖：pip install pymysql
"""
import argparse
import json
import os
import sys

try:
    import pymysql
except ImportError:
    print("缺少 pymysql，请先执行：pip install pymysql")
    sys.exit(1)

DEFAULT_STORAGE_DIR = r"D:\jshz-PMO\供应商图库"
MIGRATION_SQL = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "database", "migration", "20261029_add_supplier_image.sql",
)
UNIQUE_INDEX_NAME = "uk_supplier_main"


def connect(args):
    return pymysql.connect(
        host=args.host, port=args.port, user=args.user,
        password=args.password, database=args.db, charset="utf8mb4",
        autocommit=False, cursorclass=pymysql.cursors.DictCursor,
    )


def table_exists(cursor, table):
    cursor.execute(
        "SELECT COUNT(*) AS c FROM information_schema.tables WHERE table_schema=%s AND table_name=%s",
        (args.db, table),
    )
    return cursor.fetchone()["c"] > 0


def index_exists(cursor, index_name):
    cursor.execute(
        "SELECT COUNT(*) AS c FROM information_schema.statistics "
        "WHERE table_schema=%s AND table_name='supplier_image' AND index_name=%s",
        (args.db, index_name),
    )
    return cursor.fetchone()["c"] > 0


def apply_migration(args):
    conn = connect(args)
    try:
        with conn.cursor() as cur:
            # 1) 确保表存在（执行 DDL，幂等）
            with open(MIGRATION_SQL, "r", encoding="utf-8") as f:
                for stmt in f.read().split(";"):
                    s = stmt.strip()
                    if s:
                        cur.execute(s)

            # 2) 迁移存量：images 非空 且 supplier_image 无记录 的供应商
            cur.execute(
                "SELECT id, images FROM supplier WHERE images IS NOT NULL AND images <> '' "
                "AND id NOT IN (SELECT DISTINCT supplier_id FROM supplier_image)"
            )
            pending = cur.fetchall()
            moved = 0
            for row in pending:
                try:
                    imgs = json.loads(row["images"])
                except Exception:
                    imgs = []
                if not isinstance(imgs, list) or not imgs:
                    continue
                for i, fname in enumerate(imgs):
                    if not fname or not isinstance(fname, str):
                        continue
                    size = 0
                    disk = os.path.join(args.storage_dir, fname)
                    if os.path.isfile(disk):
                        size = os.path.getsize(disk)
                    cur.execute(
                        "INSERT INTO supplier_image (supplier_id, file_name, name, is_main, file_size, sort_order) "
                        "VALUES (%s, %s, '', %s, %s, %s)",
                        (row["id"], fname, 1 if i == 0 else 0, size, i),
                    )
                cur.execute("UPDATE supplier SET images = NULL WHERE id = %s", (row["id"],))
                moved += 1
            conn.commit()

            # 3) 迁移与主图选定完成后，再创建「部分唯一索引」等效约束
            #    MySQL 不支持 partial index，采用生成列 main_key=IF(is_main=1,supplier_id,NULL)
            #    + 唯一索引 (supplier_id, main_key) 等效实现（is_main=0 时 NULL 不参与唯一约束）
            cur.execute(
                "SELECT COUNT(*) AS c FROM information_schema.columns "
                "WHERE table_schema=%s AND table_name='supplier_image' AND column_name='main_key'",
                (args.db,),
            )
            if cur.fetchone()["c"] == 0:
                cur.execute(
                    "ALTER TABLE supplier_image "
                    "ADD COLUMN main_key BIGINT GENERATED ALWAYS AS (IF(is_main = 1, supplier_id, NULL)) STORED"
                )
                conn.commit()
                print("已添加生成列 main_key（等效部分唯一索引语义）")
            else:
                print("生成列 main_key 已存在，跳过")
            if not index_exists(cur, UNIQUE_INDEX_NAME):
                cur.execute(
                    "ALTER TABLE supplier_image ADD UNIQUE KEY `%s` (supplier_id, main_key)" % UNIQUE_INDEX_NAME
                )
                conn.commit()
                print("已创建唯一索引 %s(supplier_id, main_key)，等效 (supplier_id,is_main) WHERE is_main=1" % UNIQUE_INDEX_NAME)
            else:
                print("唯一索引 %s 已存在，跳过创建" % UNIQUE_INDEX_NAME)

            cur.execute("SELECT COUNT(*) AS c FROM supplier_image")
            total = cur.fetchone()["c"]
            print("迁移完成：本次迁移供应商 %d 家，supplier_image 现有 %d 行（重复执行不会产生重复行）" % (moved, total))

            # 主图唯一性校验
            cur.execute(
                "SELECT supplier_id, COUNT(*) AS c FROM supplier_image WHERE is_main = 1 GROUP BY supplier_id HAVING c > 1"
            )
            dup = cur.fetchall()
            if dup:
                print("警告：存在多主图供应商 %d 家：%s" % (len(dup), [r["supplier_id"] for r in dup]))
            else:
                print("主图唯一性校验通过：每个供应商至多一张主图")
    finally:
        conn.close()


def rollback_migration(args):
    conn = connect(args)
    try:
        with conn.cursor() as cur:
            # 1) 删除唯一索引与生成列（等效部分唯一索引）
            if index_exists(cur, UNIQUE_INDEX_NAME):
                cur.execute("ALTER TABLE supplier_image DROP KEY `%s`" % UNIQUE_INDEX_NAME)
                print("已删除唯一索引 %s" % UNIQUE_INDEX_NAME)
            else:
                print("唯一索引不存在，跳过")
            cur.execute(
                "SELECT COUNT(*) AS c FROM information_schema.columns "
                "WHERE table_schema=%s AND table_name='supplier_image' AND column_name='main_key'",
                (args.db,),
            )
            if cur.fetchone()["c"] > 0:
                cur.execute("ALTER TABLE supplier_image DROP COLUMN main_key")
                print("已删除生成列 main_key")
            else:
                print("生成列 main_key 不存在，跳过")

            # 2) 恢复 images 字段（仅恢复被迁移置空的供应商）
            cur.execute(
                "SELECT s.id FROM supplier s WHERE s.images IS NULL "
                "AND EXISTS (SELECT 1 FROM supplier_image si WHERE si.supplier_id = s.id)"
            )
            restored_ids = [r["id"] for r in cur.fetchall()]
            for sid in restored_ids:
                cur.execute(
                    "SELECT file_name FROM supplier_image WHERE supplier_id = %s ORDER BY sort_order ASC, id ASC",
                    (sid,),
                )
                names = [r["file_name"] for r in cur.fetchall()]
                cur.execute("UPDATE supplier SET images = %s WHERE id = %s", (json.dumps(names, ensure_ascii=False), sid))

            # 3) 清空 supplier_image 表
            cur.execute("DELETE FROM supplier_image")
            conn.commit()
            print("回滚完成：恢复供应商 %d 家 images 字段，supplier_image 已清空" % len(restored_ids))
    finally:
        conn.close()


def parse_args():
    p = argparse.ArgumentParser(description="supplier.images -> supplier_image 迁移（幂等可回滚）")
    p.add_argument("--host", default=os.environ.get("MYSQL_HOST", "127.0.0.1"))
    p.add_argument("--port", type=int, default=int(os.environ.get("MYSQL_PORT", "3306")))
    p.add_argument("--db", default=os.environ.get("MYSQL_DB", "expert_system"))
    p.add_argument("--user", default=os.environ.get("MYSQL_USER", "root"))
    p.add_argument("--password", default=os.environ.get("MYSQL_PASSWORD", ""))
    p.add_argument("--storage-dir", default=DEFAULT_STORAGE_DIR, help="供应商图库磁盘目录（用于统计 file_size）")
    p.add_argument("--rollback", action="store_true", help="回滚：还原 images 字段并清空 supplier_image")
    return p.parse_args()


if __name__ == "__main__":
    args = parse_args()
    if args.rollback:
        rollback_migration(args)
    else:
        apply_migration(args)
