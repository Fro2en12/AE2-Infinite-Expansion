
Add-Type -AssemblyName System.Drawing
$OutDir = 'E:\改着玩\AE2-Infinite-Expansion\icon-concepts'

# 官方 16x16 元件纹理
$official = [System.Drawing.Image]::FromFile("$OutDir\ref\item_storage_cell_1k.png")
$src = New-Object System.Drawing.Bitmap($official)

# 64x64 像素画布：深空底 + 官方盘(2x) + 像素∞环(覆盖盘中央)
$canvas = New-Object System.Drawing.Bitmap(64,64,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

function P([int]$x,[int]$y,$c){ $canvas.SetPixel($x,$y,$c) }
function D2([double]$x1,[double]$y1,[double]$x2,[double]$y2){ [math]::Sqrt(($x1-$x2)*($x1-$x2)+($y1-$y2)*($y1-$y2)) }

$bgC0=[System.Drawing.Color]::FromArgb(255,24,40,72)
$bgC1=[System.Drawing.Color]::FromArgb(255,14,24,48)
$bgC2=[System.Drawing.Color]::FromArgb(255,6,10,22)

for ($y=0; $y -lt 64; $y++) {
  for ($x=0; $x -lt 64; $x++) {
    $d = D2 $x $y 30 32
    # 1) background (pixelated vignette)
    $bgc = if ($d -lt 20) { $bgC0 } elseif ($d -lt 34) { $bgC1 } else { $bgC2 }
    # 2) official disk 2x (16..47)
    $inDisk = ($x -ge 16 -and $x -lt 48 -and $y -ge 16 -and $y -lt 48)
    $pix = $null
    if ($inDisk) {
      $bx=[int](($x-16)/2); $by=[int](($y-16)/2)
      $pix = $src.GetPixel($bx,$by)
      if ($pix.A -lt 64) { $pix = $null }
    }
    # 3) pixel infinity ring (covers disk center): centers (19,32),(45,32) rout 19 rin 14.5
    $dL = D2 $x $y 19 32
    $dR = D2 $x $y 45 32
    $inL = ($dL -le 19) -xor ($dL -le 14.5)
    $inR = ($dR -le 19) -xor ($dR -le 14.5)
    $inInf = $inL -or $inR
    # knot diamond (bright)
    $inKnot = ([math]::Abs($x-32) + [math]::Abs($y-32) -le 5) -and ([math]::Abs($x-32) + [math]::Abs($y-32) -ge 1)
    $infc = $null
    if ($inInf) {
      if ($y -lt 20) { $infc=[System.Drawing.Color]::FromArgb(255,234,253,255) }
      elseif ($y -lt 32) { $infc=[System.Drawing.Color]::FromArgb(255,135,220,246) }
      elseif ($y -lt 44) { $infc=[System.Drawing.Color]::FromArgb(255,63,179,214) }
      else { $infc=[System.Drawing.Color]::FromArgb(255,30,126,166) }
    }
    if ($inKnot) { $infc = [System.Drawing.Color]::FromArgb(255,255,255,255) }
    # compose: bg <- official disk <- infinity
    $final = $bgc
    if ($pix -ne $null) { $final = $pix }
    if ($infc -ne $null) { $final = $infc }
    P $x $y $final
  }
}
$canvas.Save("$OutDir\concept-E-64.png",[System.Drawing.Imaging.ImageFormat]::Png)

# 8x 最近邻放大 → 512
$big = New-Object System.Drawing.Bitmap(512,512)
$gB=[System.Drawing.Graphics]::FromImage($big)
$gB.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$gB.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half
$gB.DrawImage($canvas,0,0,512,512);$gB.Dispose()
$big.Save("$OutDir\concept-E.png",[System.Drawing.Imaging.ImageFormat]::Png);$big.Dispose()

# 16px 验证（真实缩小）
$s16=New-Object System.Drawing.Bitmap(16,16)
$g16=[System.Drawing.Graphics]::FromImage($s16);$g16.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$bigSrc=[System.Drawing.Image]::FromFile("$OutDir\concept-E.png")
$g16.DrawImage($bigSrc,0,0,16,16);$g16.Dispose()
$s16.Save("$OutDir\check-E-16.png",[System.Drawing.Imaging.ImageFormat]::Png);$s16.Dispose()
$blow=New-Object System.Drawing.Bitmap(128,128)
$gB2=[System.Drawing.Graphics]::FromImage($blow);$gB2.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$s16b=[System.Drawing.Image]::FromFile("$OutDir\check-E-16.png")
$gB2.DrawImage($s16b,0,0,128,128);$gB2.Dispose()
$blow.Save("$OutDir\check-E-16to128.png",[System.Drawing.Imaging.ImageFormat]::Png);$blow.Dispose()
$s16b.Dispose();$bigSrc.Dispose();$canvas.Dispose();$src.Dispose();$official.Dispose()
Write-Output "E ok"
