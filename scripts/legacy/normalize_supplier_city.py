#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
供应商模块改造 v2.0「一、城市固定化」- 存量城市归一幂等脚本
================================================================
职责：
  1) 三级归一：将自动沉淀产生的「其他/沙溪镇」按人工清单归一为「广东省/中山市」；
  2) 更新 supplier.city_id 引用（350 -> 中山市 id）；
  3) 删除「其他/沙溪镇」字典行（幂等：不存在则跳过，可重复执行）；
  4) 校验：无「其他」前缀城市、province+name 无重名、无悬空 city_id、code 无缺失。

用法：
  python normalize_supplier_city.py [--host 127.0.0.1] [--port 3306] [--db expert_system]
      [--user root] [--password 密码]
依赖：pip install pymysql
"""
import argparse
import os
import sys

try:
    import pymysql
except ImportError:
    print("缺少 pymysql，请先执行: pip install pymysql")
    sys.exit(1)

DB_SCHEMA = "expert_system"

# 人工清单：自动沉淀的异常城市 -> (目标省, 目标市)
NORMALIZE_MAP = {
    ("其他", "沙溪镇"): ("广东省", "中山市"),
}


def parse_args():
    parser = argparse.ArgumentParser(description="supplier_city 存量归一幂等脚本")
    parser.add_argument("--host", default=os.environ.get("MYSQL_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("MYSQL_PORT", "3306")))
    parser.add_argument("--db", default=os.environ.get("MYSQL_DB", DB_SCHEMA))
    parser.add_argument("--user", default=os.environ.get("MYSQL_USER", "root"))
    parser.add_argument("--password", default=os.environ.get("MYSQL_PASSWORD", ""))
    return parser.parse_args()


def main():
    args = parse_args()
    conn = pymysql.connect(
        host=args.host, port=args.port, user=args.user, password=args.password,
        database=args.db, charset="utf8mb4", autocommit=False,
    )
    cur = conn.cursor()
    try:
        normalized = 0
        for (src_province, src_name), (dst_province, dst_name) in NORMALIZE_MAP.items():
            # 定位异常城市（幂等：不存在则跳过）
            cur.execute(
                "SELECT id FROM `supplier_city` WHERE `province`=%s AND `name`=%s LIMIT 1",
                (src_province, src_name),
            )
            row = cur.fetchone()
            if row is None:
                print(f"[SKIP] 异常城市「{src_province}/{src_name}」不存在，无需归一")
                continue
            src_id = row[0]
            # 定位目标城市（中山市，广东省下地级市）
            cur.execute(
                "SELECT id FROM `supplier_city` WHERE `province`=%s AND `name`=%s LIMIT 1",
                (dst_province, dst_name),
            )
            dst = cur.fetchone()
            if dst is None:
                raise RuntimeError(f"目标城市「{dst_province}/{dst_name}」不存在，归一中止")
            dst_id = dst[0]
            if src_id == dst_id:
                print(f"[SKIP] 「{src_province}/{src_name}」与目标相同，跳过")
                continue
            # 更新 supplier 引用
            cur.execute("UPDATE `supplier` SET `city_id`=%s WHERE `city_id`=%s", (dst_id, src_id))
            supplier_updated = cur.rowcount
            # 删除异常城市行
            cur.execute("DELETE FROM `supplier_city` WHERE `id`=%s", (src_id,))
            print(
                f"[OK] 归一「{src_province}/{src_name}」(id={src_id}) -> "
                f"「{dst_province}/{dst_name}」(id={dst_id})，更新 supplier 引用 {supplier_updated} 行"
            )
            normalized += 1

        conn.commit()

        # 校验
        cur.execute("SELECT COUNT(*) FROM `supplier_city` WHERE `province`='其他' OR `province` LIKE '其他%'")
        other_cnt = cur.fetchone()[0]
        cur.execute(
            "SELECT `province`, `name`, COUNT(*) c FROM `supplier_city` GROUP BY `province`, `name` HAVING c > 1"
        )
        dup_names = cur.fetchall()
        cur.execute("SELECT COUNT(*) FROM `supplier_city` WHERE `code` IS NULL OR `code`=''")
        missing_code = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM `supplier_city`")
        total = cur.fetchone()[0]
        cur.execute(
            "SELECT COUNT(*) FROM `supplier` s LEFT JOIN `supplier_city` c ON s.city_id=c.id WHERE c.id IS NULL"
        )
        orphan = cur.fetchone()[0]
        # 确认中山市最终被引用
        cur.execute(
            "SELECT COUNT(*) FROM `supplier` s JOIN `supplier_city` c ON s.city_id=c.id "
            "WHERE c.province='广东省' AND c.name='中山市'"
        )
        zs_used = cur.fetchone()[0]

        print("===== 校验结果 =====")
        print(f"全表城市行数: {total}（应为 349）")
        print(f"异常「其他」前缀城市: {other_cnt}（应为 0）")
        print(f"province+name 重名: {len(dup_names)} 组（应为 0）")
        print(f"code 缺失: {missing_code}（应为 0）")
        print(f"supplier 悬空 city_id: {orphan}（应为 0）")
        print(f"引用广东省/中山市的供应商数: {zs_used}")
        ok = (other_cnt == 0 and not dup_names and missing_code == 0 and orphan == 0)
        print("===== " + ("PASS" if ok else "FAIL") + " =====")
        if not ok:
            sys.exit(2)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
