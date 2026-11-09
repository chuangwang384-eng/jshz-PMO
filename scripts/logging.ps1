#requires -Version 5.1
<#
.SYNOPSIS
  统一日志编码工具（UTF-8 无 BOM）。

.DESCRIPTION
  Windows 控制台默认代码页为 936(GBK)，PowerShell 的 Write-Host 输出经 cmd 重定向时会
  以 GBK 写入文件，导致 UTF-8 工具链读到乱码（2026-11-06 发布日志即为此问题）。
  本模块提供三件事：
    1. Enable-Utf8Console   —— 把当前进程的标准输出编码切到 UTF-8(无 BOM)，
                               重定向落到磁盘的字节即为 UTF-8。
    2. Add-Utf8Line         —— 以 UTF-8(无 BOM) 追加一行，不产生 BOM、不做二次转码。
    3. Write-Utf8Text       —— 以 UTF-8(无 BOM) 覆盖写入整段文本。

  说明：只影响本脚本进程的输出编码，不修改系统代码页，不修改任何注册表。
#>

$script:Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Enable-Utf8Console {
    <#
      将当前进程的控制台输出编码切为 UTF-8。
      失败（如无控制台句柄）时静默降级，不阻断脚本。
    #>
    try {
        [Console]::OutputEncoding = $script:Utf8NoBom
    } catch {
        # 无控制台（服务/计划任务）场景忽略
    }
    try {
        $global:OutputEncoding = $script:Utf8NoBom
    } catch {
    }
}

function Add-Utf8Line {
    <#
      .PARAMETER Path    目标文件绝对路径
      .PARAMETER Line    单行内容（不含换行）
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Line
    )
    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    Add-Utf8Text -Path $Path -Text ($Line + "`r`n")
}

function Add-Utf8Text {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text
    )
    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    [System.IO.File]::AppendAllText($Path, $Text, $script:Utf8NoBom)
}

function Write-Utf8Text {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text
    )
    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    [System.IO.File]::WriteAllText($Path, $Text, $script:Utf8NoBom)
}

function Read-Utf8Text {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return '' }
    return [System.IO.File]::ReadAllText($Path, $script:Utf8NoBom)
}
