
Add-Type -AssemblyName System.Drawing
$OutDir = 'E:\改着玩\AE2-Infinite-Expansion\icon-concepts'
$official = [System.Drawing.Image]::FromFile("$OutDir\ref\item_storage_cell_1k.png")
$src = New-Object System.Drawing.Bitmap($official)
$sw = $src.Width; $sh = $src.Height
Write-Output ("src " + $sw + "x" + $sh)

$canvas = New-Object System.Drawing.Bitmap(64,64,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
function P([int]$x,[int]$y,$c){ $canvas.SetPixel($x,$y,$c) }
function D2([double]$x1,[double]$y1,[double]$x2,[double]$y2){ [math]::Sqrt(($x1-$x2)*($x1-$x2)+($y1-$y2)*($y1-$y2)) }
$bgC0=[System.Drawing.Color]::FromArgb(255,24,40,72)
$bgC1=[System.Drawing.Color]::FromArgb(255,14,24,48)
$bgC2=[System.Drawing.Color]::FromArgb(255,6,10,22)

for ($y=0; $y -lt 64; $y++) {
  for ($x=0; $x -lt 64; $x++) {
    $d = D2 $x $y 32 32
    $bgc = if ($d -lt 19) { $bgC0 } elseif ($d -lt 33) { $bgC1 } else { $bgC2 }
    # official disk 2x (x 16..47, y 16..47) with bounds guard
    $pix = $null
    if ($x -ge 16 -and $x -lt 48 -and $y -ge 16 -and $y -lt 48) {
      $bx=[int](($x-16)/2); $by=[int](($y-16)/2)
      if ($bx -ge 0 -and $bx -lt $sw -and $by -ge 0 -and $by -lt $sh) {
        $pp = $src.GetPixel($bx,$by)
        if ($pp.A -ge 64) { $pix = $pp }
      }
    }
    # infinity ring: centers (20/(42)?? -> keep 19/45), rout 19, rin 13.8 (wider twin holes -> disk shows more)
    $dL = D2 $x $y 19 32
    $dR = D2 $x $y 45 32
    $inL = (($dL -le 19) -and ($dL -gt 13.8))
    $inR = (($dR -le 19) -and ($dR -gt 13.8))
    $inInf = $inL -or $inR
    $inKnot = ([math]::Abs($x-32)+[math]::Abs($y-32) -le 6) -and ([math]::Abs($x-32)+[math]::Abs($y-32) -ge 2)
    $infc = $null
    if ($inInf) {
      if ($y -lt 21) { $infc=[System.Drawing.Color]::FromArgb(255,234,253,255) }
      elseif ($y -lt 33) { $infc=[System.Drawing.Color]::FromArgb(255,135,220,246) }
      elseif ($y -lt 45) { $infc=[System.Drawing.Color]::FromArgb(255,63,179,214) }
      else { $infc=[System.Drawing.Color]::FromArgb(255,30,126,166) }
    }
    if ($inKnot) { $infc = [System.Drawing.Color]::FromArgb(255,255,255,255) }
    $final = $bgc
    if ($pix -ne $null) { $final = $pix }
    if ($infc -ne $null) { $final = $infc }
    P $x $y $final
  }
}
$canvas.Save("$OutDir\concept-E2-64.png",[System.Drawing.Imaging.ImageFormat]::Png)
$big=New-Object System.Drawing.Bitmap(512,512)
$gB=[System.Drawing.Graphics]::FromImage($big)
$gB.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$gB.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half
$gB.DrawImage($canvas,0,0,512,512);$gB.Dispose()
$big.Save("$OutDir\concept-E2.png",[System.Drawing.Imaging.ImageFormat]::Png);$big.Dispose()

$s16=New-Object System.Drawing.Bitmap(16,16)
$g16=[System.Drawing.Graphics]::FromImage($s16);$g16.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$bigSrc=[System.Drawing.Image]::FromFile("$OutDir\concept-E2.png")
$g16.DrawImage($bigSrc,0,0,16,16);$g16.Dispose()
$s16.Save("$OutDir\check-E2-16.png",[System.Drawing.Imaging.ImageFormat]::Png);$s16.Dispose()
$blow=New-Object System.Drawing.Bitmap(128,128)
$gB2=[System.Drawing.Graphics]::FromImage($blow);$gB2.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$s16b=[System.Drawing.Image]::FromFile("$OutDir\check-E2-16.png")
$gB2.DrawImage($s16b,0,0,128,128);$gB2.Dispose()
$blow.Save("$OutDir\check-E2-16to128.png",[System.Drawing.Imaging.ImageFormat]::Png);$blow.Dispose()
$s16b.Dispose();$bigSrc.Dispose();$canvas.Dispose();$src.Dispose();$official.Dispose()
Write-Output "E2 ok"
