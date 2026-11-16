#requires -Version 5.1
<#
.SYNOPSIS
  scripts\release-record.ps1 的确定性单测（不依赖文档真实内容）。

.DESCRIPTION
  覆盖三个必须成立的行为：
    1. 目标文件不存在 / 为空 -> 建立章节 + 表头 + 首行
    2. 文档中存在多个同名章节（含叙述型无表格章节）-> 新行必须落入「带表格的最后一个章节」
    3. 连续追加两次 -> 同章节内两行，标题不重复

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\tests\test-release-record.ps1
#>
$ErrorActionPreference = 'Stop'
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'logging.ps1')
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'release-record.ps1')
Enable-Utf8Console

$failures = 0
function Assert-True([bool]$cond, [string]$name) {
    if ($cond) { Write-Host "  [PASS] $name" -ForegroundColor Green }
    else { Write-Host "  [FAIL] $name" -ForegroundColor Red; $script:failures++ }
}

$tmpDir = Join-Path ([IO.Path]::GetTempPath()) ('rr-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null

try {
    # ---- 用例 1：空文件 ----
    Write-Host '用例 1：空文件新建章节'
    $f1 = Join-Path $tmpDir 'empty.md'
    Write-Utf8Text $f1 ''
    Add-ReleaseTableRow -Path $f1 -Version '1.0.1' -JarName 'a.jar' -Time '2026-01-01 00:00:00'
    $t1 = Read-Utf8Text $f1
    Assert-True ($t1.Contains('## R-1 发布记录')) '包含章节标题'
    Assert-True ($t1.Contains('| 时间 | 版本 | 结果 | 制品 |')) '包含表头'
    Assert-True ($t1.Contains('| 2026-01-01 00:00:00 | 1.0.1 | 成功 | a.jar |')) '包含数据行'

    # ---- 用例 2：多个同名章节，叙述型在前、表格型在后 ----
    Write-Host '用例 2：多章节时锁定带表格的最后一节'
    $f2 = Join-Path $tmpDir 'multi.md'
    $content = @(
        '# 部署说明',
        '',
        '## R-1 发布记录',
        '这里是叙述型章节，没有表格。',
        '',
        '## 其他章节',
        '无关内容',
        '',
        '## R-1 发布记录',
        '',
        '| 时间 | 版本 | 结果 | 制品 |',
        '| --- | --- | --- | --- |',
        '| 2026-01-01 00:00:00 | 1.0.0 | 成功 | old.jar |',
        ''
    ) -join "`n"
    Write-Utf8Text $f2 $content
    Add-ReleaseTableRow -Path $f2 -Version '1.0.2' -JarName 'b.jar' -Time '2026-01-02 00:00:00'
    $t2 = Read-Utf8Text $f2
    $narrativeIdx = $t2.IndexOf('这里是叙述型章节')
    $newRowIdx = $t2.IndexOf('| 2026-01-02 00:00:00 | 1.0.2 | 成功 | b.jar |')
    $oldRowIdx = $t2.IndexOf('| 2026-01-01 00:00:00 | 1.0.0 | 成功 | old.jar |')
    Assert-True ($newRowIdx -gt 0) '新行已写入'
    Assert-True ($newRowIdx -gt $oldRowIdx) '新行在旧行之后'
    Assert-True ($newRowIdx -gt $narrativeIdx) '新行未落入叙述型章节'
    Assert-True (([regex]::Matches($t2, '(?m)^## R-1 发布记录\s*$')).Count -eq 2) '标题数量未增加'

    # ---- 用例 3：连续追加两次 ----
    Write-Host '用例 3：连续追加不重复标题'
    Add-ReleaseTableRow -Path $f2 -Version '1.0.3' -JarName 'c.jar' -Time '2026-01-03 00:00:00'
    $t3 = Read-Utf8Text $f2
    Assert-True (([regex]::Matches($t3, '(?m)^## R-1 发布记录\s*$')).Count -eq 2) '标题仍为 2 个'
    Assert-True ($t3.Contains('| 2026-01-03 00:00:00 | 1.0.3 | 成功 | c.jar |')) '第二行已写入'
    $rowsAfter = ([regex]::Matches($t3, '(?m)^\| 2026-01-0')).Count
    Assert-True ($rowsAfter -eq 3) '表格共 3 行数据'
}
finally {
    Remove-Item -LiteralPath $tmpDir -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
if ($failures -eq 0) { Write-Host '全部用例通过' -ForegroundColor Green; exit 0 }
else { Write-Host "失败 $failures 项" -ForegroundColor Red; exit 1 }
