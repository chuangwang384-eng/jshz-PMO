#requires -Version 5.1
<#
.SYNOPSIS
  发布记录写入（docs\deployment.md 的「## R-1 发布记录」表格）。

.DESCRIPTION
  历史做法是每次发布追加一个新标题 + 一行列表项，导致文档里堆出多个同名二级标题
  （2026-11-06 验收时已出现 2 个）。改为单一章节 + 表格行：首次写入建章节与表头，
  之后每次只在表格末尾插入一行。

  依赖：logging.ps1 的 Read-Utf8Text / Write-Utf8Text（UTF-8 无 BOM、保留原文件换行风格）。
#>

$ReleaseHeading = '## R-1 发布记录'
$ReleaseTableHeader = '| 时间 | 版本 | 结果 | 制品 |'
$ReleaseTableSep = '| --- | --- | --- | --- |'

function Add-ReleaseTableRow {
    <#
      .PARAMETER Path    文档绝对路径（不存在则创建）
      .PARAMETER Version 发布版本号
      .PARAMETER JarName 制品文件名
      .PARAMETER Result  结果（成功 / 失败 / 回滚）
      .PARAMETER Time    时间文本，缺省取当前时间
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Version,
        [Parameter(Mandatory = $true)][string]$JarName,
        [string]$Result = '成功',
        [string]$Time = ''
    )
    if (-not $Time) { $Time = Get-Date -Format 'yyyy-MM-dd HH:mm:ss' }
    $row = "| $Time | $Version | $Result | $JarName |"

    $text = Read-Utf8Text $Path
    if ([string]::IsNullOrWhiteSpace($text)) {
        # 注意：必须整体括起来再拼 "`n"，否则命令模式下 "+" 会被当成独立位置参数传入
        Write-Utf8Text $Path (((@($ReleaseHeading, '', $ReleaseTableHeader, $ReleaseTableSep, $row)) -join "`n") + "`n")
        return
    }

    $eol = if ($text -match "`r`n") { "`r`n" } else { "`n" }
    $lines = [System.Collections.Generic.List[string]](([regex]::Split($text, "\r?\n")))
    while ($lines.Count -gt 0 -and $lines[$lines.Count - 1] -eq '') { $lines.RemoveAt($lines.Count - 1) }

    $headingIndex = -1
    $allHeadings = @()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i].Trim() -eq $ReleaseHeading) { $allHeadings += $i }
    }

    if ($allHeadings.Count -eq 0) {
        $lines.Add('')
        $lines.Add($ReleaseHeading)
        $lines.Add('')
        $lines.Add($ReleaseTableHeader)
        $lines.Add($ReleaseTableSep)
        $lines.Add($row)
        Write-Utf8Text $Path (($lines -join $eol) + $eol)
        return
    }

    # 文档里可能存在多个同名章节（历史遗留的叙述型章节），必须锁定「带表格的最后一个章节」，
    # 否则会把新行插进中部的叙述块。找不到带表格的章节时，退化为最后一个同名章节。
    $sectionEnd = @{}
    for ($k = 0; $k -lt $allHeadings.Count; $k++) {
        $start = $allHeadings[$k]
        $end = $lines.Count
        if ($k + 1 -lt $allHeadings.Count) { $end = $allHeadings[$k + 1] }
        for ($j = $start + 1; $j -lt $lines.Count; $j++) {
            if ($lines[$j].Trim().StartsWith('## ')) { $end = $j; break }
        }
        $sectionEnd[$start] = $end
    }

    $tableHeadings = @()
    foreach ($h in $allHeadings) {
        for ($j = $h + 1; $j -lt $sectionEnd[$h]; $j++) {
            if ($lines[$j].Trim().StartsWith('|')) { $tableHeadings += $h; break }
        }
    }
    $headingIndex = if ($tableHeadings.Count -gt 0) { $tableHeadings[$tableHeadings.Count - 1] } else { $allHeadings[$allHeadings.Count - 1] }
    $sectionLimit = $sectionEnd[$headingIndex]

    # 在该章节范围内找最后一行表格，插入到其后；无表格则补表头
    $lastTableIndex = -1
    for ($i = $headingIndex + 1; $i -lt $sectionLimit; $i++) {
        $trimmed = $lines[$i].Trim()
        if ($trimmed.StartsWith('## ')) { break }
        if ($trimmed.StartsWith('|')) { $lastTableIndex = $i }
    }

    if ($lastTableIndex -lt 0) {
        # 章节存在但还没有表格（历史列表项写法）：在标题后补表头与首行
        $lines.Insert($headingIndex + 1, '')
        $lines.Insert($headingIndex + 2, $ReleaseTableHeader)
        $lines.Insert($headingIndex + 3, $ReleaseTableSep)
        $lines.Insert($headingIndex + 4, $row)
    } else {
        $lines.Insert($lastTableIndex + 1, $row)
    }
    Write-Utf8Text $Path (($lines -join $eol) + $eol)
}
