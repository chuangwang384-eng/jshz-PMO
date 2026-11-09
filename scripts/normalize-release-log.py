#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""把 docs/deployment.md 中重复的「## R-1 发布记录」标题块合并为单张表格。

背景：历史上每次发布都追加一个新标题 + 一行列表项，至 2026-11-06 已累积 39 个同名二级标题。
本脚本只合并「纯记录块」（块内仅含列表项 / 表格行 / AI 免责声明行），含正文的块原样保留。

用法：
    python scripts/normalize-release-log.py            # 执行合并（自动备份）
    python scripts/normalize-release-log.py --check    # 只报告，不写文件
"""
import os
import re
import shutil
import sys
import time

DOC = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'docs', 'deployment.md')
HEADING = '## R-1 发布记录'
DISCLAIMER = '*（内容由AI生成，仅供参考）*'
RECORD = re.compile(
    r'^-\s*(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\s+R-1 发布\s+(\S+)\s+(成功|失败|回滚)'
    r'[（(]jar:\s*([^）)]*)[）)]\s*$'
)


def collect_blocks(lines):
    """返回 [(heading_index, block_end_index), ...]，块终点为下一个二级标题。"""
    heads = [i for i, l in enumerate(lines) if l.strip() == HEADING]
    blocks = []
    for i in heads:
        end = len(lines)
        for j in range(i + 1, len(lines)):
            if lines[j].startswith('## '):
                end = j
                break
        blocks.append((i, end))
    return blocks


def main():
    check_only = '--check' in sys.argv
    raw = open(DOC, 'rb').read().decode('utf-8')
    eol = '\r\n' if '\r\n' in raw else '\n'
    lines = raw.replace('\r\n', '\n').replace('\r', '\n').split('\n')

    blocks = collect_blocks(lines)
    mergeable, kept, records, unparsed = [], [], [], []
    for start, end in blocks:
        body = [l for l in lines[start + 1:end] if l.strip()]
        if not body:
            kept.append((start, end))
            continue
        allowed = all(
            l.strip().startswith('- ') or l.strip().startswith('|') or l.strip() == DISCLAIMER
            for l in body
        )
        if not allowed:
            kept.append((start, end))
            continue
        parsed, bad = [], []
        for l in body:
            t = l.strip()
            if t == DISCLAIMER or t.startswith('|'):
                continue
            m = RECORD.match(t)
            if m:
                parsed.append(m.groups())
            else:
                bad.append(t)
        if bad:
            kept.append((start, end))
            unparsed.extend(bad)
            continue
        mergeable.append((start, end))
        records.extend(parsed)

    print('标题块 %d | 可合并 %d | 保留(含正文) %d | 提取记录 %d'
          % (len(blocks), len(mergeable), len(kept), len(records)))
    for u in unparsed[:3]:
        print('  未解析(块已保留): %s' % u[:100])

    if check_only or not mergeable:
        return 0

    remove = set(k for s, e in mergeable for k in range(s, e))
    kept_lines = [l for i, l in enumerate(lines) if i not in remove]

    collapsed, blank = [], 0
    for l in kept_lines:
        if l.strip() == '':
            blank += 1
            if blank > 1:
                continue
        else:
            blank = 0
        collapsed.append(l)

    records.sort(key=lambda r: r[0])
    table = [
        '', HEADING, '',
        '> 2026-11-06 起发布记录统一以表格行追加（实现 `scripts/release-record.ps1`），以下为历史记录汇总。', '',
        '| 时间 | 版本 | 结果 | 制品 |', '| --- | --- | --- | --- |',
    ]
    for t, v, res, jar in records:
        table.append('| %s | %s | %s | %s |' % (t, v, res, jar.strip()))

    backup = '%s.bak-%s' % (DOC, time.strftime('%Y%m%d_%H%M%S'))
    shutil.copy2(DOC, backup)
    with open(DOC, 'wb') as f:
        f.write((eol.join(collapsed + table) + eol).encode('utf-8'))

    left = sum(1 for l in collapsed + table if l.strip() == HEADING)
    print('已合并并写入（备份 %s）| 剩余同名标题 %d | 表格行 %d'
          % (os.path.basename(backup), left, len(records)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
