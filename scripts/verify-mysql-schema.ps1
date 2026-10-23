<#
.SYNOPSIS
    Kiểm chứng lược đồ MySQL: migration Flyway chạy hết và Hibernate validate khớp với entity.

.DESCRIPTION
    Dựng MỘT container MySQL 8 tạm (tên riêng, dữ liệu nằm trong bộ nhớ, tự xóa khi xong) rồi chạy ứng dụng với hồ
    sơ docker (Flyway bật, ddl-auto=validate) qua hai kịch bản:

      A. CSDL mới hoàn toàn: Flyway chạy V1 và V2, nạp dữ liệu demo, đăng nhập bằng tài khoản demo.
      B. CSDL đã có, dựng bằng ddl-auto=update và chưa có lịch sử Flyway (giống CSDL nghiệm thu hoặc volume Docker
         cũ): Flyway đặt baseline ở V1 rồi chỉ chạy V2; dữ liệu cũ giữ nguyên.

    Script KHÔNG đụng tới container, volume hay cổng của bộ docker compose đang chạy (medbook-db, medbook-app).
    Mã thoát 0 nếu cả hai kịch bản đạt, khác 0 nếu có kịch bản thất bại.

.EXAMPLE
    .\scripts\verify-mysql-schema.ps1
    .\scripts\verify-mysql-schema.ps1 -AppPort 8095 -DbPort 3320 -SkipBuild
#>
param(
    [int]$AppPort = 8089,
    [int]$DbPort = 3317,
    [switch]$SkipBuild,
    [switch]$KeepContainer
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$container = "medbook-verify-$([Guid]::NewGuid().ToString('N').Substring(0, 8))"
$logDir = Join-Path $root 'target\verify-mysql'
$demoPassword = 'MedBook@2026'   # mật khẩu tài khoản demo, ghi trong README

function New-RandomSecret([int]$length) {
    -join ((48..57) + (65..90) + (97..122) | Get-Random -Count $length | ForEach-Object { [char]$_ })
}

# Mật khẩu truyền qua biến MYSQL_PWD bên trong container (không nằm trên dòng lệnh, không sinh cảnh báo ra stderr).
# Lệnh ngoài chạy với ErrorActionPreference=Continue vì Windows PowerShell coi mọi dòng stderr là lỗi dừng.
function Invoke-Mysql([string]$database, [string]$sql) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = docker exec -e "MYSQL_PWD=$dbPassword" $container mysql -uroot -h 127.0.0.1 -N -B -e $sql $database 2>$null
        return [pscustomobject]@{ Ok = ($LASTEXITCODE -eq 0); Output = $output }
    }
    finally { $ErrorActionPreference = $previous }
}

function Invoke-Sql([string]$database, [string]$sql) {
    $result = Invoke-Mysql $database $sql
    if (-not $result.Ok) { throw "Lệnh SQL thất bại: $sql" }
    return $result.Output
}

function Start-App([string]$database, [string]$profile, [string]$logName, [hashtable]$extraEnv) {
    $url = "jdbc:mysql://127.0.0.1:$DbPort/${database}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8"
    $envVars = @{
        SPRING_PROFILES_ACTIVE     = $profile
        SPRING_DATASOURCE_URL      = $url
        SPRING_DATASOURCE_USERNAME = 'root'
        SPRING_DATASOURCE_PASSWORD = $dbPassword
        MEDBOOK_DB_URL             = $url
        MEDBOOK_DB_USER            = 'root'
        MEDBOOK_DB_PASSWORD        = $dbPassword
        JWT_SECRET                 = $jwtSecret
        MEDBOOK_UPLOAD_DIR         = (Join-Path $logDir 'uploads')
        PORT                       = "$AppPort"
    }
    foreach ($key in $extraEnv.Keys) { $envVars[$key] = $extraEnv[$key] }
    $saved = @{}
    foreach ($key in $envVars.Keys) {
        $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $envVars[$key], 'Process')
    }
    try {
        $log = Join-Path $logDir $logName
        return Start-Process -FilePath 'java' -ArgumentList '-jar', "`"$jar`"" -PassThru -WindowStyle Hidden `
            -RedirectStandardOutput $log -RedirectStandardError "$log.err"
    }
    finally {
        foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process') }
    }
}

function Wait-App($process, [string]$logName) {
    foreach ($i in 1..90) {
        if ($process.HasExited) {
            $tail = Get-Content (Join-Path $logDir $logName) -Tail 25 -ErrorAction SilentlyContinue
            throw "Ứng dụng dừng khi khởi động (mã $($process.ExitCode)). Cuối log:`n$($tail -join "`n")"
        }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$AppPort/api/v1/system/status" -TimeoutSec 3
            if ($response.StatusCode -eq 200) { return }
        }
        catch { }
        Start-Sleep -Seconds 2
    }
    throw 'Ứng dụng không sẵn sàng sau 180 giây.'
}

# Lệnh "java" trên PATH của Windows thường là trình khởi chạy trung gian (javapath) sinh ra tiến trình JVM thật,
# nên phải dừng cả cây tiến trình; sau đó dừng nốt tiến trình nào còn giữ cổng nếu đúng là ứng dụng vừa chạy.
function Stop-App($process) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if ($process -and -not $process.HasExited) {
            taskkill /PID $process.Id /T /F 2>$null | Out-Null
        }
        foreach ($i in 1..15) {
            $listener = Get-NetTCPConnection -LocalPort $AppPort -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
            if (-not $listener) { break }
            $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
            if ($owner -and $jar -and $owner.CommandLine -like "*$jar*") {
                Stop-Process -Id $listener.OwningProcess -Force -Confirm:$false -ErrorAction SilentlyContinue
            }
            Start-Sleep -Seconds 1
        }
    }
    finally { $ErrorActionPreference = $previous }
}

function Test-DemoLogin {
    $body = @{ usernameOrEmail = 'admin1'; password = $demoPassword } | ConvertTo-Json
    $login = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$AppPort/api/v1/auth/login" `
        -ContentType 'application/json' -Body $body
    if (-not $login.token) { throw 'Đăng nhập tài khoản demo không trả token.' }
    $me = Invoke-RestMethod -Uri "http://127.0.0.1:$AppPort/api/v1/users/me" -Headers @{ Authorization = "Bearer $($login.token)" }
    if ($me.username -ne 'admin1') { throw 'GET /users/me không trả đúng tài khoản.' }
}

$results = @()
$app = $null
$jar = $null
$exitCode = 1
try {
    if (Get-NetTCPConnection -LocalPort $AppPort -State Listen -ErrorAction SilentlyContinue) {
        throw "Cổng $AppPort đang được dùng. Chọn cổng khác bằng -AppPort."
    }
    New-Item -ItemType Directory -Force $logDir | Out-Null
    Set-Location $root
    if (-not $SkipBuild) {
        Write-Host '== Đóng gói ứng dụng (bỏ qua test) =='
        $ErrorActionPreference = 'Continue'
        & .\mvnw.cmd -q -DskipTests package 2>&1 | Out-Null
        $packaged = ($LASTEXITCODE -eq 0)
        $ErrorActionPreference = 'Stop'
        if (-not $packaged) { throw 'Đóng gói thất bại (chạy .\mvnw.cmd -DskipTests package để xem lỗi).' }
    }
    $jar = (Get-ChildItem (Join-Path $root 'target') -Filter 'medbook-*.jar' | Where-Object { $_.Name -notmatch 'original' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
    if (-not $jar) { throw 'Không tìm thấy target\medbook-*.jar. Bỏ -SkipBuild để đóng gói.' }

    $dbPassword = New-RandomSecret 24
    $jwtSecret = New-RandomSecret 48
    Write-Host "== Dựng MySQL tạm: $container (cổng $DbPort) =="
    $ErrorActionPreference = 'Continue'
    docker run -d --name $container -e "MYSQL_ROOT_PASSWORD=$dbPassword" -p "127.0.0.1:${DbPort}:3306" `
        --tmpfs /var/lib/mysql mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci 2>$null | Out-Null
    $started = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = 'Stop'
    if (-not $started) { throw 'Không dựng được container MySQL (Docker đã chạy chưa?).' }
    $ready = $false
    foreach ($i in 1..60) {
        Start-Sleep -Seconds 2
        if ((Invoke-Mysql 'mysql' 'select 1').Ok) { $ready = $true; break }
    }
    if (-not $ready) { throw 'MySQL không sẵn sàng sau 120 giây.' }
    Invoke-Sql 'mysql' 'CREATE DATABASE medbook_fresh CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE DATABASE medbook_legacy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;' | Out-Null

    # ---------- Kịch bản A ----------
    Write-Host '== A. CSDL mới: Flyway V1 + V2, validate, nạp dữ liệu demo =='
    $app = Start-App 'medbook_fresh' 'docker' 'a-fresh.log' @{ MEDBOOK_SEED_DEMO = 'true' }
    Wait-App $app 'a-fresh.log'
    $history = Invoke-Sql 'medbook_fresh' "SELECT CONCAT(version, ':', success) FROM flyway_schema_history ORDER BY installed_rank"
    if (($history -join ',') -ne '1:1,2:1') { throw "Lịch sử Flyway không đúng: $($history -join ',')" }
    $users = Invoke-Sql 'medbook_fresh' 'SELECT COUNT(*) FROM users'
    if ("$users" -ne '5') { throw "Mong đợi 5 tài khoản demo, có $users." }
    Test-DemoLogin
    Stop-App $app
    $results += 'A. CSDL mới: ĐẠT (Flyway 1,2; validate; 5 tài khoản demo; đăng nhập admin1 được)'

    Write-Host '== A2. Khởi động lại trên cùng CSDL, không có cờ demo: không nạp chồng =='
    $app = Start-App 'medbook_fresh' 'docker' 'a-restart.log' @{ MEDBOOK_SEED_DEMO = 'false' }
    Wait-App $app 'a-restart.log'
    $users = Invoke-Sql 'medbook_fresh' 'SELECT COUNT(*) FROM users'
    if ("$users" -ne '5') { throw "Sau khi khởi động lại có $users tài khoản (mong đợi 5)." }
    Stop-App $app
    $results += 'A2. Khởi động lại: ĐẠT (không migration mới, dữ liệu giữ nguyên)'

    # ---------- Kịch bản B ----------
    Write-Host '== B. CSDL cũ: dựng bằng ddl-auto=update (hồ sơ v4mysql), không có lịch sử Flyway =='
    $app = Start-App 'medbook_legacy' 'v4mysql' 'b-legacy-create.log' @{}
    Wait-App $app 'b-legacy-create.log'
    Stop-App $app
    Invoke-Sql 'medbook_legacy' "INSERT INTO roles (code, name, created_at) VALUES ('PATIENT', 'Bệnh nhân', NOW())" | Out-Null
    $tablesBefore = Invoke-Sql 'medbook_legacy' 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()'

    Write-Host '== B. Nâng cấp: hồ sơ docker đặt baseline V1, chạy V2, validate =='
    $app = Start-App 'medbook_legacy' 'docker' 'b-legacy-upgrade.log' @{ MEDBOOK_SEED_DEMO = 'false' }
    Wait-App $app 'b-legacy-upgrade.log'
    $history = Invoke-Sql 'medbook_legacy' "SELECT CONCAT(version, ':', type) FROM flyway_schema_history ORDER BY installed_rank"
    if (($history -join ',') -ne '1:BASELINE,2:JDBC') { throw "Lịch sử Flyway không đúng: $($history -join ',')" }
    $roles = Invoke-Sql 'medbook_legacy' 'SELECT COUNT(*) FROM roles'
    if ("$roles" -ne '1') { throw "Dữ liệu cũ bị thay đổi: roles = $roles." }
    Stop-App $app
    $results += "B. CSDL cũ ($tablesBefore bảng, không lịch sử Flyway): ĐẠT (baseline V1, chạy V2, validate, dữ liệu cũ còn nguyên)"

    Write-Host ''
    Write-Host '=== KẾT QUẢ ==='
    $results | ForEach-Object { Write-Host $_ }
    Write-Host 'verify-mysql-schema: THÀNH CÔNG'
    $exitCode = 0
}
catch {
    Write-Host ''
    Write-Host '=== KẾT QUẢ ==='
    $results | ForEach-Object { Write-Host $_ }
    Write-Host "verify-mysql-schema: THẤT BẠI - $($_.Exception.Message)"
    Write-Host "Log của ứng dụng: $logDir"
}
finally {
    Stop-App $app
    if (-not $KeepContainer) {
        $ErrorActionPreference = 'Continue'
        docker rm -f $container 2>$null | Out-Null
    }
}
exit $exitCode
