param(
    [ValidateSet('test', 'package', 'run')][string]$Task = 'test',
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$ExtraArgs
)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$mavenVersion = '3.9.9'
$toolsDirectory = Join-Path $projectRoot '.tools'
$mavenHome = Join-Path $toolsDirectory "apache-maven-$mavenVersion"
$mavenCommand = Join-Path $mavenHome 'bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $mavenCommand)) {
    New-Item -ItemType Directory -Path $toolsDirectory -Force | Out-Null
    $archive = Join-Path $toolsDirectory "apache-maven-$mavenVersion-bin.zip"
    $downloadUrl = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$mavenVersion/apache-maven-$mavenVersion-bin.zip"
    Write-Host "Downloading Maven $mavenVersion into project .tools ..."
    Invoke-WebRequest -Uri $downloadUrl -OutFile $archive
    $expectedHash = (Invoke-RestMethod -Uri "$downloadUrl.sha512").ToString().Trim().Split(' ')[0]
    $actualHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash
    if ($expectedHash -ne $actualHash) { throw 'Maven archive checksum mismatch.' }
    Expand-Archive -LiteralPath $archive -DestinationPath $toolsDirectory -Force
}
$mavenGoal = switch ($Task) { 'test' { 'test' }; 'package' { 'package' }; 'run' { 'spring-boot:run' } }
Push-Location $projectRoot
try {
    & $mavenCommand '-B' '-ntp' "-Dmaven.repo.local=$projectRoot\.m2" '-f' (Join-Path $projectRoot 'pom.xml') $mavenGoal @ExtraArgs
    if ($LASTEXITCODE -ne 0) { throw "Maven failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
