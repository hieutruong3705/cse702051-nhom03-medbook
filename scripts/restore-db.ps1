<#
.SYNOPSIS
    Phục hồi một bản sao lưu MedBook vào CSDL TRỐNG mới và đối chiếu số dòng với CSDL gốc.

.DESCRIPTION
    Dùng để kiểm chứng bản sao lưu phục hồi được (sổ tay Buổi 10). Script tạo một CSDL mới tên
    <Database>_restore_<thời điểm> trong cùng container, nạp tệp .sql vào đó rồi so số dòng từng bảng với CSDL
    nguồn. KHÔNG ghi đè, không xóa CSDL đang dùng. CSDL kiểm chứng được giữ lại để xem; lệnh xóa in ở cuối.

.EXAMPLE
    .\scripts\restore-db.ps1 -File .\backups\medbook-medbook_db-20261007-153000.sql
#>
param(
    [Parameter(Mandatory = $true)][string]$File,
    [string]$Container = 'medbook-db',
    [string]$Database = 'medbook_db'
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path $File)) { throw "Không thấy tệp sao lưu: $File" }
$target = "{0}_restore_{1}" -f $Database, (Get-Date -Format 'yyyyMMddHHmmss')
$inside = "/tmp/medbook-restore-$([Guid]::NewGuid().ToString('N')).sql"

function Invoke-InContainer([string]$command) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = docker exec $Container sh -c $command 2>$null
        if ($LASTEXITCODE -ne 0) { throw "Lệnh trong container thất bại: $command" }
        return $output
    }
    finally { $ErrorActionPreference = $previous }
}

function Get-RowCounts([string]$db) {
    $tables = Invoke-InContainer "MYSQL_PWD=`"`$MYSQL_ROOT_PASSWORD`" mysql -uroot -N -B -e `"SELECT table_name FROM information_schema.tables WHERE table_schema='$db' AND table_type='BASE TABLE' ORDER BY table_name`""
    $counts = [ordered]@{}
    foreach ($table in $tables) {
        $counts[$table] = [long](Invoke-InContainer "MYSQL_PWD=`"`$MYSQL_ROOT_PASSWORD`" mysql -uroot -N -B -e 'SELECT COUNT(*) FROM ``$table``' $db")
    }
    return $counts
}

$ErrorActionPreference = 'Continue'
docker cp $File "${Container}:$inside" 2>$null | Out-Null
$copied = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = 'Stop'
if (-not $copied) { throw "Không chép được tệp vào container '$Container'." }

try {
    Invoke-InContainer "MYSQL_PWD=`"`$MYSQL_ROOT_PASSWORD`" mysql -uroot -e 'CREATE DATABASE ``$target`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'" | Out-Null
    Invoke-InContainer "MYSQL_PWD=`"`$MYSQL_ROOT_PASSWORD`" mysql -uroot --default-character-set=utf8mb4 $target < $inside" | Out-Null
}
finally {
    $ErrorActionPreference = 'Continue'
    docker exec $Container rm -f $inside 2>$null | Out-Null
    $ErrorActionPreference = 'Stop'
}

$source = Get-RowCounts $Database
$restored = Get-RowCounts $target
$mismatch = 0
Write-Host ("{0,-28} {1,10} {2,10}" -f 'Bảng', 'Gốc', 'Phục hồi')
foreach ($table in $restored.Keys) {
    $original = if ($source.Contains($table)) { $source[$table] } else { $null }
    $mark = ''
    if ($original -ne $restored[$table]) { $mark = '  <-- khác'; $mismatch++ }
    Write-Host ("{0,-28} {1,10} {2,10}{3}" -f $table, $original, $restored[$table], $mark)
}
Write-Host ''
if ($mismatch -eq 0) {
    Write-Host "PHỤC HỒI ĐẠT: $($restored.Count) bảng trong '$target' khớp số dòng với '$Database'."
}
else {
    Write-Host "CÓ $mismatch bảng lệch số dòng (bình thường nếu CSDL gốc đã có thêm dữ liệu sau thời điểm sao lưu)."
}
Write-Host "CSDL kiểm chứng được giữ lại. Khi không cần nữa, tự xóa bằng lệnh DROP DATABASE ``$target`` trong MySQL."
if ($mismatch -ne 0) { exit 2 }
