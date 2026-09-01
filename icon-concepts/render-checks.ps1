
Add-Type -AssemblyName System.Drawing
$OutDir = 'E:\改着玩\AE2-Infinite-Expansion\icon-concepts'
# 16px downscale + 8x nearest-neighbor blowup (readability check)
foreach ($n in @('A','B','C')) {
  $src = [System.Drawing.Image]::FromFile("$OutDir\concept-$n.png")
  $s16 = New-Object System.Drawing.Bitmap(16,16)
  $g16 = [System.Drawing.Graphics]::FromImage($s16)
  $g16.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g16.DrawImage($src,0,0,16,16); $g16.Dispose()
  $s16.Save("$OutDir\check-$n-16.png",[System.Drawing.Imaging.ImageFormat]::Png); $s16.Dispose()
  $big = New-Object System.Drawing.Bitmap(128,128)
  $gB = [System.Drawing.Graphics]::FromImage($big)
  $gB.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
  $s16b = [System.Drawing.Image]::FromFile("$OutDir\check-$n-16.png")
  $gB.DrawImage($s16b,0,0,128,128); $gB.Dispose()
  $big.Save("$OutDir\check-$n-16to128.png",[System.Drawing.Imaging.ImageFormat]::Png); $big.Dispose(); $s16b.Dispose(); $src.Dispose()
}
Write-Output "checks ok"
