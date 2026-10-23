<#
.SYNOPSIS
    Sao lưu CSDL MySQL của MedBook ra một tệp .sql (kết xuất logic bằng mysqldump).

.DESCRIPTION
    Mặc định sao lưu CSDL trong container "medbook-db" của docker compose. Tệp kết xuất gồm lược đồ, dữ liệu, và cả
    bảng lịch sử Flyway nên phục hồi xong ứng dụng chạy tiếp được ngay. Mật khẩu đọc từ biến MYSQL_ROOT_PASSWORD
    của chính container (không cần gõ, không nằm trên dòng lệnh).

    Phục hồi và kiểm chứng bằng scripts\restore-db.ps1.

.EXAMPLE
    .\scripts\backup-db.ps1
    .\scripts\backup-db.ps1 -Container medbook-db -Database medbook_db -OutDir D:\saoluu
#>
param(
    [string]$Container = 'medbook-db',
    [string]$Database = 'medbook_db',
    [string]$OutDir = (Join-Path (Split-Path -Parent $PSScriptRoot) 'backups')
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force $OutDir | Out-Null
$file = Join-Path $OutDir ("medbook-{0}-{1}.sql" -f $Database, (Get-Date -Format 'yyyyMMdd-HHmmss'))

# Chạy mysqldump bên trong container và ghi tệp ở đó, rồi chép ra: tránh việc PowerShell đổi bảng mã của dữ liệu
# tiếng Việt khi cho đi qua đường ống.
$inside = "/tmp/medbook-backup-$([Guid]::NewGuid().ToString('N')).sql"
$ErrorActionPreference = 'Continue'
docker exec $Container sh -c "MYSQL_PWD=`"`$MYSQL_ROOT_PASSWORD`" mysqldump -uroot --single-transaction --routines --triggers --default-character-set=utf8mb4 --result-file=$inside $Database" 2>$null
$dumped = ($LASTEXITCODE -eq 0)
if ($dumped) {
    docker cp "${Container}:$inside" $file 2>$null | Out-Null
    $dumped = ($LASTEXITCODE -eq 0)
}
docker exec $Container rm -f $inside 2>$null | Out-Null
$ErrorActionPreference = 'Stop'

if (-not $dumped -or -not (Test-Path $file) -or (Get-Item $file).Length -eq 0) {
    throw "Sao lưu thất bại. Kiểm tra container '$Container' đang chạy và CSDL '$Database' tồn tại."
}
$tables = (Select-String -Path $file -Pattern '^CREATE TABLE' -Encoding UTF8).Count
Write-Host ("Đã sao lưu {0} bảng của '{1}' vào {2} ({3:N0} byte)." -f $tables, $Database, $file, (Get-Item $file).Length)
Write-Host "Kiểm chứng phục hồi: .\scripts\restore-db.ps1 -File `"$file`""
