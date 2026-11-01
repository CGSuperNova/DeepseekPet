# Generate the base whalechan texture (64x64 pixels).
# This is a "flat-color" base texture: each 8x8 cell maps to one part of the model,
# colored according to the whale-girl design (white hair + blue whale tail + maid dress).
# Replace it with finer pixel art in BlockBench later if desired.
# Usage:  .\generate-texture.ps1

Add-Type -AssemblyName System.Drawing

$out = Join-Path $PSScriptRoot 'whalechan.png'

$bmp = New-Object System.Drawing.Bitmap 64, 64
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::FromArgb(0, 0, 0, 0))  # transparent background

$white = [System.Drawing.Color]::FromArgb(255, 245, 248, 255)
$navy  = [System.Drawing.Color]::FromArgb(255, 47, 77, 143)
$blue  = [System.Drawing.Color]::FromArgb(255, 59, 130, 246)
$eye   = [System.Drawing.Color]::FromArgb(255, 37, 99, 235)
$apron = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)

function Cell([int]$cx, [int]$cy, [System.Drawing.Color]$c) {
    $r = [System.Drawing.Rectangle]::new($cx * 8, $cy * 8, 8, 8)
    $b = [System.Drawing.SolidBrush]::new($c)
    $g.FillRectangle($b, $r)
    $b.Dispose()
}

# head (white hair)
Cell 0 0 $white; Cell 1 0 $white; Cell 0 1 $white; Cell 2 1 $white; Cell 3 1 $white; Cell 1 1 $white

# blue eyes on the front face
$b = [System.Drawing.SolidBrush]::new($eye)
$g.FillRectangle($b, [System.Drawing.Rectangle]::new(9, 10, 2, 2))
$g.FillRectangle($b, [System.Drawing.Rectangle]::new(13, 10, 2, 2))
$b.Dispose()

# body (navy maid dress + white apron)
Cell 2 0 $navy; Cell 3 0 $navy; Cell 3 2 $navy; Cell 2 2 $navy
$b = [System.Drawing.SolidBrush]::new($apron)
$g.FillRectangle($b, [System.Drawing.Rectangle]::new(18, 18, 4, 5))
$b.Dispose()

# arms / legs (white)
Cell 0 2 $white; Cell 1 2 $white; Cell 0 3 $white; Cell 1 3 $white

# whale tail / fin ears / ahoge (blue)
Cell 2 3 $blue; Cell 3 3 $blue; Cell 4 0 $blue

$g.Dispose()
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output ("saved: " + $out)
