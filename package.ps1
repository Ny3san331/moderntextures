$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$dist = Join-Path $root 'dist'
$release = Join-Path $dist 'release'
$docs = Join-Path $dist 'docs'
New-Item -ItemType Directory -Force -Path $dist, $release, $docs | Out-Null
Copy-Item (Join-Path $root 'plugin/build/libs/ModernTextures.jar') $release
Copy-Item (Join-Path $root 'generator/build/libs/ModernTextures-Generator.jar') $release
Copy-Item (Join-Path $root 'README.md') $release
Copy-Item (Join-Path $root 'mappings.json') $release
Copy-Item (Join-Path $root 'docs/*.md') $docs -Force

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Write-Zip($Destination, $Entries) {
    if (Test-Path $Destination) { Remove-Item $Destination }
    $archive = [IO.Compression.ZipFile]::Open($Destination, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($entry in $Entries) {
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $entry.File, $entry.Name, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally { $archive.Dispose() }
}

$manifestPath = Join-Path $root 'packs/manifest.json'
$manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
$packEntries = @(@{ File=$manifestPath; Name='manifest.json' })
foreach ($pack in $manifest.packs) {
    if ($pack.file -notmatch '^[a-zA-Z0-9._-]+\.zip$') { throw 'Nome inseguro no manifesto' }
    $file = Join-Path $root ('packs/' + $pack.file)
    if ((Get-FileHash $file -Algorithm SHA1).Hash.ToLower() -ne $pack.sha1) { throw "Hash divergente: $file" }
    $packEntries += @{ File=$file; Name=$pack.file }
    $report = 'moderntextures-26.3-to-' + $pack.clientVersion + '-report.json'
    $packEntries += @{ File=(Join-Path $root ('packs/' + $report)); Name=$report }
}
Write-Zip (Join-Path $release 'ModernTextures-Packs.zip') $packEntries

$sourceEntries = @()
foreach ($folder in @('core','generator','plugin','gradle','docs')) {
    Get-ChildItem (Join-Path $root $folder) -File -Recurse | Where-Object { $_.FullName -notmatch '[\\/]build[\\/]' } | ForEach-Object {
        $sourceEntries += @{ File=$_.FullName; Name=$_.FullName.Substring($root.Length+1).Replace('\','/') }
    }
}
foreach ($file in @('build.gradle','settings.gradle','gradlew','gradlew.bat','mappings.json','README.md','package.ps1','.gitignore')) {
    $sourceEntries += @{ File=(Join-Path $root $file); Name=$file }
}
Write-Zip (Join-Path $release 'ModernTextures-Source.zip') $sourceEntries

$releaseEntries = @()
foreach ($file in @('ModernTextures.jar','ModernTextures-Generator.jar','ModernTextures-Packs.zip','ModernTextures-Source.zip','README.md','mappings.json')) {
    $releaseEntries += @{ File=(Join-Path $release $file); Name=$file }
}
Get-ChildItem (Join-Path $root 'docs') -File | ForEach-Object { $releaseEntries += @{ File=$_.FullName; Name=('docs/' + $_.Name) } }
Write-Zip (Join-Path $release 'ModernTextures-1.0.0.zip') $releaseEntries
Get-ChildItem $release -File | Select-Object Name,Length

