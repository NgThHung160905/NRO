[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$Root = $PSScriptRoot
$Eff = Join-Path $Root 'Eff'
$Source = Join-Path $Root 'Source'
$GameDir = Join-Path $Root 'Teamobi2026'
$StateDir = Join-Path $Root '.state'
$LogDir = Join-Path $Root 'Logs'
$StateFile = Join-Path $StateDir 'setup.json'
$LogFile = Join-Path $LogDir 'setup.log'

New-Item -ItemType Directory -Force -Path $StateDir, $LogDir | Out-Null
if ((Test-Path -LiteralPath $LogFile) -and (Get-Item -LiteralPath $LogFile).Length -gt 5MB) {
    Move-Item -LiteralPath $LogFile -Destination (Join-Path $LogDir 'setup.previous.log') -Force
}
try { Start-Transcript -Path $LogFile -Append | Out-Null } catch {}

function Write-Step([string]$Text) {
    Write-Host "`n=== $Text ===" -ForegroundColor Cyan
}

function Load-State {
    if (Test-Path -LiteralPath $StateFile) {
        try { return (Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json) } catch {}
    }
    return [pscustomobject]@{}
}

function Save-State([string]$Name, $Value = $true) {
    $script:State | Add-Member -NotePropertyName $Name -NotePropertyValue $Value -Force
    $temporaryState = Join-Path $StateDir 'setup.json.tmp'
    $script:State | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $temporaryState -Encoding UTF8
    Move-Item -LiteralPath $temporaryState -Destination $StateFile -Force
}

function Assert-File([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Khong tim thay file bat buoc: $Path" }
}

function Unblock-Safe([string[]]$Paths) {
    foreach ($targetPath in $Paths) {
        if (-not (Test-Path -LiteralPath $targetPath)) { continue }
        $item = Get-Item -LiteralPath $targetPath -Force
        if ($item.PSIsContainer) {
            Get-ChildItem -LiteralPath $targetPath -File -Recurse -Force -ErrorAction SilentlyContinue |
                Unblock-File -ErrorAction SilentlyContinue
        } else {
            Unblock-File -LiteralPath $targetPath -ErrorAction SilentlyContinue
        }
    }
}

function Assert-ValidSignature([string]$Path) {
    $signature = Get-AuthenticodeSignature -LiteralPath $Path
    if ($signature.Status -ne 'Valid') {
        throw "Chu ky so cua bo cai khong hop le ($($signature.Status)): $Path"
    }
}

function Join-NativeArguments([string[]]$Values) {
    $quoted = foreach ($value in $Values) {
        if ($null -eq $value -or $value.Length -eq 0) { '""'; continue }
        if ($value -notmatch '[\s"]') { $value; continue }
        $escaped = $value.Replace('"', '\"')
        $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
        '"' + $escaped + '"'
    }
    return ($quoted -join ' ')
}

function Test-AppInstalled([string]$Pattern, [string[]]$ExecutablePaths) {
    foreach ($path in $ExecutablePaths) {
        if ($path -and (Test-Path -LiteralPath $path -PathType Leaf)) { return $true }
    }

    $exeNames = @($ExecutablePaths | Where-Object { $_ } | ForEach-Object { Split-Path $_ -Leaf } | Select-Object -Unique)
    $appPathHives = @(
        'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths',
        'HKLM:\SOFTWARE\Wow6432Node\Microsoft\Windows\CurrentVersion\App Paths',
        'HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths'
    )
    foreach ($exe in $exeNames) {
        foreach ($hive in $appPathHives) {
            $regKey = Join-Path $hive $exe
            if (Test-Path -LiteralPath $regKey) {
                $target = (Get-ItemProperty -LiteralPath $regKey -ErrorAction SilentlyContinue).'(default)'
                if ($target) {
                    $clean = $target.Trim('"')
                    if (Test-Path -LiteralPath $clean -PathType Leaf) { return $true }
                }
            }
        }
        $cmd = Get-Command $exe -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($cmd -and $cmd.Source -and (Test-Path -LiteralPath $cmd.Source -PathType Leaf)) { return $true }
    }

    if ($Pattern) {
        $uninstallHives = @(
            'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
            'HKLM:\Software\Wow6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*',
            'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
            'HKCU:\Software\Wow6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*'
        )
        $matched = Get-ItemProperty -Path $uninstallHives -ErrorAction SilentlyContinue |
            Where-Object { $_.DisplayName -and $_.DisplayName -match $Pattern } |
            Select-Object -First 1
        if ($matched) {
            if ($matched.DisplayIcon) {
                $iconPath = ($matched.DisplayIcon -split ',')[0].Trim('"')
                if (Test-Path -LiteralPath $iconPath -PathType Leaf) { return $true }
            }
            if ($matched.InstallLocation -and (Test-Path -LiteralPath $matched.InstallLocation -PathType Container)) {
                return $true
            }
            if ($matched.UninstallString) {
                $uninst = ($matched.UninstallString.Trim('"') -replace '".*$', '')
                $uninstDir = Split-Path $uninst -Parent
                if ($uninstDir -and (Test-Path -LiteralPath $uninstDir -PathType Container)) {
                    return $true
                }
            }
            return $true
        }
    }

    return $false
}

function Get-JavaVersionText([string]$JavaExe) {
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $JavaExe
    $startInfo.Arguments = '-version'
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $javaProcess = New-Object System.Diagnostics.Process
    $javaProcess.StartInfo = $startInfo
    if (-not $javaProcess.Start()) { return $null }
    $standardOutput = $javaProcess.StandardOutput.ReadToEnd()
    $standardError = $javaProcess.StandardError.ReadToEnd()
    $javaProcess.WaitForExit()
    if ($javaProcess.ExitCode -ne 0) { return $null }
    return ($standardOutput + "`n" + $standardError).Trim()
}

function Get-MatchingJavaHome([string]$ExpectedVersion) {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $javaRoots = @(
        (Join-Path $env:ProgramFiles 'Java'),
        (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
        (Join-Path $env:ProgramFiles 'Microsoft')
    )
    foreach ($javaRoot in $javaRoots) {
        Get-ChildItem -LiteralPath $javaRoot -Directory -Filter '*jdk*21*' -ErrorAction SilentlyContinue | ForEach-Object {
            $candidates += $_.FullName
        }
    }
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($javaCommand -and $javaCommand.Source) {
        $candidates += Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
    }
    foreach ($candidateJavaHome in @($candidates | Select-Object -Unique)) {
        $exe = Join-Path $candidateJavaHome 'bin\java.exe'
        if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) { continue }
        $text = Get-JavaVersionText $exe
        if ($text -match [regex]::Escape($ExpectedVersion)) { return $candidateJavaHome.TrimEnd('\') }
    }
    return $null
}

function Invoke-Installer([string]$Label, [string]$File, [string[]]$Arguments, [scriptblock]$Verify) {
    Assert-File $File
    Write-Host "Dang cai $Label..."
    $process = Start-Process -FilePath $File -ArgumentList (Join-NativeArguments $Arguments) -Wait -PassThru
    if ($process.ExitCode -notin @(0, 1641, 3010)) {
        throw "$Label cai dat that bai (ma loi $($process.ExitCode))."
    }
    Start-Sleep -Seconds 2
    if (-not (& $Verify)) { throw "Khong xac minh duoc $Label sau khi cai dat." }
    Write-Host "$Label da cai thanh cong." -ForegroundColor Green
}

function Start-InstallerAsync([string]$Label, [string]$File, [string[]]$Arguments, [scriptblock]$Verify) {
    Assert-File $File
    Write-Host "Bat dau cai nen: $Label..."
    $process = Start-Process -FilePath $File -ArgumentList (Join-NativeArguments $Arguments) -PassThru
    return [pscustomobject]@{ Label=$Label; Process=$process; Verify=$Verify }
}

function Complete-InstallerAsync($Task) {
    if (-not $Task.Process.WaitForExit(1800000)) {
        try { $Task.Process.Kill() } catch {}
        throw "$($Task.Label) vuot qua thoi gian cho 30 phut."
    }
    $exitCode = $Task.Process.ExitCode
    if ($exitCode -notin @(0, 1641, 3010)) {
        throw "$($Task.Label) cai dat that bai (ma loi $exitCode)."
    }
    for ($i = 0; $i -lt 90; $i++) {
        if (& $Task.Verify) {
            Write-Host "$($Task.Label) da cai thanh cong." -ForegroundColor Green
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "Khong xac minh duoc $($Task.Label) sau khi cho 3 phut."
}

function Get-XamppRoot {
    $service = Get-CimInstance Win32_Service -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^(Apache2\.4|mysql|mariadb)$' -and $_.PathName -match 'xampp' } |
        Select-Object -First 1
    if ($service -and $service.PathName) {
        $raw = $service.PathName.Trim('"') -replace '".*$', ''
        $dir = Split-Path (Split-Path $raw -Parent) -Parent
        if (Test-Path -LiteralPath (Join-Path $dir 'xampp-control.exe')) { return $dir }
    }
    foreach ($candidate in @((Join-Path $env:SystemDrive 'xampp'))) {
        if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'xampp-control.exe'))) { return $candidate }
    }
    return $null
}

function Get-Extractor {
    $candidates = @(
        @{ Kind='WinRAR'; Path=(Join-Path $env:ProgramFiles 'WinRAR\WinRAR.exe') },
        @{ Kind='7Zip'; Path=(Join-Path $env:ProgramFiles '7-Zip\7z.exe') },
        @{ Kind='WinRAR'; Path=$(if (${env:ProgramFiles(x86)}) { Join-Path ${env:ProgramFiles(x86)} 'WinRAR\WinRAR.exe' }) },
        @{ Kind='7Zip'; Path=$(if (${env:ProgramFiles(x86)}) { Join-Path ${env:ProgramFiles(x86)} '7-Zip\7z.exe' }) },
        @{ Kind='Tar'; Path=(Join-Path $env:SystemRoot 'System32\tar.exe') }
    )
    return $candidates | Where-Object { $_.Path -and (Test-Path -LiteralPath $_.Path) } | Select-Object -First 1
}

function Start-RarExtraction([string]$Archive, [string]$Destination) {
    Assert-File $Archive
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    $extractor = Get-Extractor
    if (-not $extractor) { throw 'Can WinRAR, 7-Zip hoac tar cua Windows de giai nen RAR.' }
    switch ($extractor.Kind) {
        'WinRAR' { $extractArguments = @('x','-ibck','-inul','-o+', $Archive, "$Destination\") }
        '7Zip'   { $extractArguments = @('x','-y',"-o$Destination", $Archive) }
        'Tar'    { $extractArguments = @('-xf', $Archive, '-C', $Destination) }
    }
    $process = Start-Process -FilePath $extractor.Path -ArgumentList (Join-NativeArguments $extractArguments) -PassThru -WindowStyle Hidden
    return [pscustomobject]@{ Process=$process; Kind=$extractor.Kind; Archive=$Archive; Destination=$Destination }
}

function Complete-RarExtraction($Task) {
    if (-not $Task.Process.WaitForExit(3600000)) {
        try { $Task.Process.Kill() } catch {}
        throw "Giai nen $($Task.Archive) vuot qua thoi gian cho 60 phut."
    }
    if ($Task.Process.ExitCode -ne 0) {
        $hasOutput = @(Get-ChildItem -LiteralPath $Task.Destination -Force -ErrorAction SilentlyContinue).Count -gt 0
        if ($Task.Kind -eq 'Tar' -and $hasOutput) {
            Write-Warning "tar bo qua mot vai entry ten rong/khong hop le trong archive; cac file hop le da duoc giai nen."
        } else {
            throw "Giai nen $($Task.Archive) that bai (ma loi $($Task.Process.ExitCode))."
        }
    }
}

function Expand-Rar([string]$Archive, [string]$Destination) {
    $task = Start-RarExtraction $Archive $Destination
    Complete-RarExtraction $task
}

function Backup-Htdocs([string]$Htdocs) {
    $items = @(Get-ChildItem -LiteralPath $Htdocs -Force -ErrorAction SilentlyContinue)
    if ($items.Count -eq 0) { return $null }
    $stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
    $rarExe = Join-Path $env:ProgramFiles 'WinRAR\Rar.exe'
    if (-not (Test-Path -LiteralPath $rarExe) -and ${env:ProgramFiles(x86)}) {
        $rarExe = Join-Path ${env:ProgramFiles(x86)} 'WinRAR\Rar.exe'
    }
    $backupParent = Split-Path $Htdocs -Parent
    if (Test-Path -LiteralPath $rarExe) {
        $temporaryArchive = Join-Path $backupParent "htdocs_backup_$stamp.rar"
        $archive = Join-Path $Htdocs (Split-Path $temporaryArchive -Leaf)
        $archiveArguments = @('a','-r','-ep1','-idq', $temporaryArchive, (Join-Path $Htdocs '*'))
        $p = Start-Process -FilePath $rarExe -ArgumentList (Join-NativeArguments $archiveArguments) -Wait -PassThru -WindowStyle Hidden
    } else {
        # Windows cannot create genuine RAR archives natively. ZIP is the safe fallback.
        $temporaryArchive = Join-Path $backupParent "htdocs_backup_$stamp.zip"
        $archive = Join-Path $Htdocs (Split-Path $temporaryArchive -Leaf)
        Compress-Archive -Path (Join-Path $Htdocs '*') -DestinationPath $temporaryArchive -CompressionLevel Optimal -Force
        $p = [pscustomobject]@{ ExitCode = 0 }
        Write-Warning 'Khong tim thay WinRAR; backup htdocs duoc tao dang ZIP.'
    }
    if ($p.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $temporaryArchive) -or (Get-Item -LiteralPath $temporaryArchive).Length -le 0) {
        throw 'Khong tao/xac minh duoc backup htdocs. Du lieu cu duoc giu nguyen.'
    }
    foreach ($item in $items) {
        Remove-Item -LiteralPath $item.FullName -Recurse -Force
    }
    Move-Item -LiteralPath $temporaryArchive -Destination $archive -Force
    return $archive
}

function Invoke-NativeCommand([string]$File, [string[]]$Arguments) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $nativeOutput = @(& $File @Arguments 2>&1 | ForEach-Object { [string]$_ })
        $nativeExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    return [pscustomobject]@{ ExitCode=$nativeExitCode; Output=($nativeOutput -join [Environment]::NewLine) }
}

function Start-Xampp([string]$XamppRoot) {
    foreach ($component in @('apache','mysql')) {
        $serviceInfo = Get-CimInstance Win32_Service -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match $component -and $_.PathName -like "*$XamppRoot*" } |
            Select-Object -First 1
        if ($serviceInfo) {
            $service = Get-Service -Name $serviceInfo.Name
            if ($service.Status -ne 'Running') { Start-Service -Name $service.Name }
            continue
        }
        $startBat = Join-Path $XamppRoot "${component}_start.bat"
        Assert-File $startBat
        Start-Process -FilePath $startBat -WorkingDirectory $XamppRoot -WindowStyle Hidden | Out-Null
    }
    $mysqlAdmin = Join-Path $XamppRoot 'mysql\bin\mysqladmin.exe'
    Assert-File $mysqlAdmin
    for ($i = 0; $i -lt 30; $i++) {
        $pingResult = Invoke-NativeCommand $mysqlAdmin @('--user=root','ping','--silent')
        $mysqlReady = $pingResult.ExitCode -eq 0
        $apacheReady = [bool](Get-CimInstance Win32_Process -Filter "Name='httpd.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $_.ExecutablePath -like "$XamppRoot*" } | Select-Object -First 1)
        if ($mysqlReady -and $apacheReady) { return }
        Start-Sleep -Seconds 2
    }
    throw 'Apache/MySQL cua XAMPP khong san sang sau 60 giay. Kiem tra xung dot cong 80/443/3306 va Logs.'
}

function Show-XamppControl([string]$XamppRoot) {
    $controlPanel = Join-Path $XamppRoot 'xampp-control.exe'
    Assert-File $controlPanel
    $alreadyOpen = [bool](Get-CimInstance Win32_Process -Filter "Name='xampp-control.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.ExecutablePath -eq $controlPanel } | Select-Object -First 1)
    if (-not $alreadyOpen) {
        Start-Process -FilePath $controlPanel -WorkingDirectory $XamppRoot | Out-Null
        Write-Host 'Da mo bang dieu khien XAMPP.' -ForegroundColor Green
    } else {
        Write-Host 'Bang dieu khien XAMPP dang mo - khong mo trung.' -ForegroundColor Yellow
    }
}

function Invoke-MySql([string]$MysqlExe, [string[]]$MySqlArguments) {
    $result = Invoke-NativeCommand $MysqlExe $MySqlArguments
    if ($result.ExitCode -ne 0) {
        $detail = $result.Output
        if ($detail.Length -gt 1000) { $detail = $detail.Substring($detail.Length - 1000) }
        throw "Lenh MySQL that bai (ma loi $($result.ExitCode)): $detail"
    }
    return $result.Output
}

$State = Load-State
$setupMutex = New-Object System.Threading.Mutex($false, 'Global\ToolCaiGameV3_Setup')
$ownsMutex = $false

try {
    try { $ownsMutex = $setupMutex.WaitOne(0, $false) } catch [System.Threading.AbandonedMutexException] { $ownsMutex = $true }
    if (-not $ownsMutex) { throw 'Tool dang chay o mot cua so khac. Hay cho phien dang chay hoan tat.' }

    Write-Host '============================================================' -ForegroundColor Cyan
    Write-Host '       Tool Setup Game NRO Free - Daitenstudio.com' -ForegroundColor Green
    Write-Host '                 Vui long doc ky HDSD' -ForegroundColor Yellow
    Write-Host '============================================================' -ForegroundColor Cyan

    Write-Step 'BUOC 1 - KIEM TRA MOI TRUONG'
    $os = Get-CimInstance Win32_OperatingSystem
    $cs = Get-CimInstance Win32_ComputerSystem
    $caption = [string]$os.Caption
    $isServer = [int]$os.ProductType -ne 1
    if (-not [Environment]::Is64BitOperatingSystem) { throw 'Tool va cac bo cai chi ho tro Windows 64-bit.' }
    if ($isServer) {
        if ($caption -notmatch 'Server 2019') {
            throw "Chua ho tro he dieu hanh VPS/Server nay: $caption. Tool chi ho tro Windows Server 2019."
        }
        Write-Host "Moi truong: VPS/Server - $caption" -ForegroundColor Green
    } else {
        if ($caption -notmatch 'Windows 10|Windows 11') { throw "Chua ho tro he dieu hanh: $caption" }
        $kind = if ($cs.PCSystemType -eq 2) { 'Laptop' } else { 'PC' }
        Write-Host "Moi truong: $kind - $caption" -ForegroundColor Green
    }
    Write-Host "May: $($cs.Manufacturer) $($cs.Model); RAM: $([math]::Round($cs.TotalPhysicalMemory / 1GB, 1)) GB"
    $rootDrive = (Get-Item -LiteralPath $Root).PSDrive
    if ($rootDrive -and $null -ne $rootDrive.Free -and $rootDrive.Free -lt 3GB) {
        throw "Khong du dung luong trong tren o $($rootDrive.Name): (can toi thieu 3 GB)."
    }

    $xamppInstaller = Join-Path $Eff 'xampp-windows-x64-8.0.30-0-VS16-installer.exe'
    $chromeInstaller = Join-Path $Eff 'ChromeSetup.exe'
    $nppInstaller = Join-Path $Eff 'npp.8.9.8.Installer.x64.exe'
    $jdkInstaller = Join-Path $Eff 'jdk-21_windows-x64_bin.exe'
    $gameArchive = Join-Path $Source 'Teamobi2026.rar'
    $webArchive = Join-Path $Source 'WebTeamobi.rar'
    @($xamppInstaller,$chromeInstaller,$nppInstaller,$jdkInstaller,$gameArchive,$webArchive) | ForEach-Object { Assert-File $_ }
    Unblock-Safe @($xamppInstaller,$chromeInstaller,$nppInstaller,$jdkInstaller,$gameArchive,$webArchive)
    @($xamppInstaller,$chromeInstaller,$nppInstaller,$jdkInstaller) | ForEach-Object { Assert-ValidSignature $_ }

    Write-Step 'BUOC 2 + 3 - CAI APP VA GIAI NEN SONG SONG'
    $installerTasks = @()
    $chromePaths = @((Join-Path $env:ProgramFiles 'Google\Chrome\Application\chrome.exe'))
    if (${env:ProgramFiles(x86)}) {
        $chromePaths += Join-Path ${env:ProgramFiles(x86)} 'Google\Chrome\Application\chrome.exe'
    }
    $nppPaths = @((Join-Path $env:ProgramFiles 'Notepad++\notepad++.exe'))
    if (${env:ProgramFiles(x86)}) {
        $nppPaths += Join-Path ${env:ProgramFiles(x86)} 'Notepad++\notepad++.exe'
    }
    $nppAppPath = (Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\notepad++.exe' -ErrorAction SilentlyContinue).'(default)'
    if ($nppAppPath) { $nppPaths += $nppAppPath.Trim('"') }
    $installerVersion = (Get-Item -LiteralPath $jdkInstaller).VersionInfo.ProductVersion
    $versionMatch = [regex]::Match($installerVersion, '^\d+(?:\.\d+){2,3}')
    if (-not $versionMatch.Success) { throw "Khong doc duoc phien ban tu bo cai JDK: $installerVersion" }
    $expectedJavaVersion = $versionMatch.Value
    $javaHome = Get-MatchingJavaHome $expectedJavaVersion
    $javaMatches = [bool]$javaHome

    # Khoi dong tat ca installer ngay, khong cho XAMPP ket thuc moi lam buoc tiep theo.
    if (Get-XamppRoot) { Write-Host 'XAMPP da co - bo qua.' -ForegroundColor Yellow }
    else {
        $installRoot = Join-Path $env:SystemDrive 'xampp'
        $installerTasks += Start-InstallerAsync 'XAMPP' $xamppInstaller @('--mode','unattended','--unattendedmodeui','none','--prefix', $installRoot) { [bool](Get-XamppRoot) }
    }
    if (Test-AppInstalled 'Google Chrome' $chromePaths) { Write-Host 'Google Chrome da co - bo qua.' -ForegroundColor Yellow }
    else { $installerTasks += Start-InstallerAsync 'Google Chrome' $chromeInstaller @('/silent','/install') { Test-AppInstalled 'Google Chrome' $chromePaths } }
    if (Test-AppInstalled '^Notepad\+\+' $nppPaths) { Write-Host 'Notepad++ da co - bo qua.' -ForegroundColor Yellow }
    else { $installerTasks += Start-InstallerAsync 'Notepad++' $nppInstaller @('/S') { Test-AppInstalled '^Notepad\+\+' $nppPaths } }
    if ($javaMatches) { Write-Host "JDK $expectedJavaVersion da dung - bo qua cai dat." -ForegroundColor Yellow }
    else { $installerTasks += Start-InstallerAsync 'JDK 21' $jdkInstaller @('/s') { [bool](Get-MatchingJavaHome $expectedJavaVersion) } }

    # Giai nen game cung luc cac installer dang chay.
    $gameHash = (Get-FileHash -LiteralPath $gameArchive -Algorithm SHA256).Hash
    $gameRun = Join-Path $GameDir 'run.bat'
    $gameSql = Join-Path $GameDir 'team10.sql'
    $gameJar = Join-Path $GameDir 'Teamobi2026.jar'
    $gameExtractTask = $null
    if ($State.GameHash -eq $gameHash -and
        (Test-Path -LiteralPath $gameRun -PathType Leaf) -and
        (Test-Path -LiteralPath $gameSql -PathType Leaf) -and
        (Test-Path -LiteralPath $gameJar -PathType Leaf)) {
        Write-Host 'Teamobi2026 da giai nen - bo qua.' -ForegroundColor Yellow
    } else {
        if (Test-Path -LiteralPath $GameDir) {
            $safeOld = Join-Path $Root ("Teamobi2026_old_" + (Get-Date -Format 'yyyyMMdd_HHmmss'))
            Move-Item -LiteralPath $GameDir -Destination $safeOld
            Write-Host "Da giu ban cu tai: $safeOld"
        }
        Write-Host 'Bat dau giai nen Teamobi2026 trong nen...'
        $gameExtractTask = Start-RarExtraction $gameArchive $GameDir
    }

    Write-Host 'Dang cho cac tac vu cai dat song song hoan tat...' -ForegroundColor Cyan
    foreach ($task in $installerTasks) { Complete-InstallerAsync $task }

    $javaHome = Get-MatchingJavaHome $expectedJavaVersion
    if (-not $javaHome) { throw "Khong tim thay JDK dung phien ban $expectedJavaVersion sau khi cai." }
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $javaHome, 'Machine')
    $machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $parts = @($machinePath -split ';' | Where-Object { $_ -and $_ -notmatch '(?i)%JAVA_HOME%\\bin|\\Java\\jdk[^\\]*\\bin\\?$' })
    [Environment]::SetEnvironmentVariable('Path', (';%JAVA_HOME%\bin;' + ($parts -join ';')).Trim(';'), 'Machine')
    $env:JAVA_HOME = $javaHome
    $env:Path = "$javaHome\bin;$env:Path"
    Write-Host "JAVA_HOME = $javaHome" -ForegroundColor Green

    Write-Step 'BUOC 3 - XAC MINH TEAMOBI2026'
    if ($gameExtractTask) {
        Complete-RarExtraction $gameExtractTask
        Assert-File $gameRun
        Save-State 'GameHash' $gameHash
        Write-Host 'Teamobi2026 da giai nen thanh cong.' -ForegroundColor Green
    }

    Write-Step 'BUOC 4 - CAI WEB VA DATABASE'
    $xamppRoot = Get-XamppRoot
    if (-not $xamppRoot) { throw 'Khong tim thay thu muc XAMPP sau khi cai.' }
    $htdocs = Join-Path $xamppRoot 'htdocs'
    New-Item -ItemType Directory -Force -Path $htdocs | Out-Null
    $webHash = (Get-FileHash -LiteralPath $webArchive -Algorithm SHA256).Hash
    $webMarker = Join-Path $htdocs '.toolcaigamev3-web.json'
    $webReady = $false
    if (Test-Path -LiteralPath $webMarker) {
        try { $webReady = ((Get-Content -LiteralPath $webMarker -Raw | ConvertFrom-Json).Hash -eq $webHash) } catch {}
    }
    $webReady = $webReady -and (Test-Path -LiteralPath (Join-Path $htdocs 'index.php') -PathType Leaf)
    if ($webReady) { Write-Host 'WebTeamobi da cai - bo qua backup/trien khai.' -ForegroundColor Yellow }
    else {
        $backup = Backup-Htdocs $htdocs
        if ($backup) { Write-Host "Backup htdocs: $backup" -ForegroundColor Green }
        Expand-Rar $webArchive $htdocs
        Assert-File (Join-Path $htdocs 'index.php')
        @{ Hash=$webHash; InstalledAt=(Get-Date).ToString('o') } | ConvertTo-Json | Set-Content -LiteralPath $webMarker -Encoding UTF8
    }

    Start-Xampp $xamppRoot
    Write-Host 'Apache va MySQL da duoc khoi dong.' -ForegroundColor Green
    Show-XamppControl $xamppRoot
    $sqlFile = Join-Path $GameDir 'team10.sql'
    Assert-File $sqlFile
    $sqlHash = (Get-FileHash -LiteralPath $sqlFile -Algorithm SHA256).Hash
    $mysql = Join-Path $xamppRoot 'mysql\bin\mysql.exe'
    Assert-File $mysql
    $dbPassword = [Environment]::GetEnvironmentVariable('TEAMOBI_DB_PASSWORD', 'Machine')
    $auth = @('--user=root')
    if ($dbPassword) { $auth += "--password=$dbPassword" }
    $databaseReady = $State.DatabaseHash -eq $sqlHash
    if ($databaseReady) {
        $probe = Invoke-MySql $mysql ($auth + @('--batch','--skip-column-names','--execute=SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME=''team10'';'))
        $databaseReady = $probe.Trim() -eq 'team10'
    }
    if ($databaseReady) { Write-Host 'Database team10 da import - bo qua.' -ForegroundColor Yellow }
    else {
        Invoke-MySql $mysql ($auth + @('--execute=CREATE DATABASE IF NOT EXISTS `team10` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'))
        $sqlForMysql = $sqlFile.Replace('\','/')
        Invoke-MySql $mysql ($auth + @('--default-character-set=utf8mb4','team10',"--execute=source $sqlForMysql"))
        Save-State 'DatabaseHash' $sqlHash
        Write-Host 'Da tao va import database team10.' -ForegroundColor Green
    }

    Write-Step 'BUOC 5 - CHAY SERVER GAME'
    Assert-File $gameRun
    Unblock-Safe @($GameDir)
    $gameArguments = Join-NativeArguments @('/d','/c','call', $gameRun)
    Start-Process -FilePath $env:ComSpec -ArgumentList $gameArguments -WorkingDirectory $GameDir
    Write-Host 'HOAN TAT. Teamobi2026 dang duoc khoi chay.' -ForegroundColor Green
    Save-State 'CompletedAt' (Get-Date).ToString('o')
    exit 0
} catch {
    Write-Host "`nLOI: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Tai dong: $($_.InvocationInfo.ScriptLineNumber)" -ForegroundColor Red
    Write-Host "Lenh: $($_.InvocationInfo.Line.Trim())" -ForegroundColor DarkYellow
    Write-Host "Stack: $($_.ScriptStackTrace)" -ForegroundColor DarkGray
    Write-Host "Chi tiet: $LogFile" -ForegroundColor Yellow
    exit 1
} finally {
    if ($ownsMutex) { try { $setupMutex.ReleaseMutex() } catch {} }
    $setupMutex.Dispose()
    try { Stop-Transcript | Out-Null } catch {}
}
