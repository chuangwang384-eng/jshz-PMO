# 凭据解析公共库（方案 A，2026-11-13 收敛）
# 用途：所有需要数据库凭据的运维脚本统一引用本文件，消除各自复制解析逻辑。
# 用法：在脚本中 dot-source 后调用 Use-DbPassword（或直接 Resolve-DbPassword）：
#   . (Join-Path $PSScriptRoot 'common-credentials.ps1')
#   $null = Use-DbPassword
# 解析优先级：
#   1) 环境变量 MYSQL_PASSWORD / JWT_SECRET（运维推荐，deploy-backend.ps1 同源）
#   2) 本地 start-backend.bat（gitignored，运行时解析不落盘；bat 是 NSSM 服务环境的唯一事实源）
# 安全约定：解析结果只写入进程环境变量供消费方使用；任何日志/输出不得打印明文。

function Get-BatValue {
    # start-backend.bat 单变量运行时解析（2026-11-13 扩展：同模式支持 JWT_SECRET）
    param([Parameter(Mandatory = $true)][string]$Name)
    $batPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'start-backend.bat'
    if (-not (Test-Path -LiteralPath $batPath)) {
        throw "未找到凭据文件：$batPath（bat 是 NSSM 服务环境的唯一事实源）"
    }
    $batText = Get-Content -LiteralPath $batPath -Raw -Encoding Default
    $m = [regex]::Match($batText, "(?im)^[ \t]*set[ \t]+$Name=(.*)$")
    if (-not $m.Success) { throw "start-backend.bat 缺少 $Name 行" }
    $value = $m.Groups[1].Value.Trim()
    $quote = [char]34
    if ($value.Length -ge 2 -and $value[0] -eq $quote -and $value[$value.Length - 1] -eq $quote) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    if ([string]::IsNullOrWhiteSpace($value)) { throw "start-backend.bat 中 $Name 为空" }
    return $value
}

function Resolve-DbPassword {
    # 1) 环境变量优先
    if ($env:MYSQL_PASSWORD -and -not [string]::IsNullOrWhiteSpace($env:MYSQL_PASSWORD)) {
        return $env:MYSQL_PASSWORD
    }
    # 2) 本地 start-backend.bat 运行时解析（bat 位于仓库根，与 scripts/ 同级）
    return Get-BatValue -Name 'MYSQL_PASSWORD'
}

function Resolve-JwtSecret {
    # 与 NSSM AppEnvironmentExtra 同源：环境变量优先，缺省从 start-backend.bat 解析
    if ($env:JWT_SECRET -and -not [string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
        return $env:JWT_SECRET
    }
    return Get-BatValue -Name 'JWT_SECRET'
}

function Use-DbPassword {
    # 解析并注入 $env:MYSQL_PWD（mysql.exe 客户端标准通道）；返回 $true 便于 "$null = Use-DbPassword" 断言
    $env:MYSQL_PWD = Resolve-DbPassword
    return $true
}
