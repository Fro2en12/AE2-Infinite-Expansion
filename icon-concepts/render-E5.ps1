
Add-Type -AssemblyName System.Drawing
$OutDir='E:\改着玩\AE2-Infinite-Expansion\icon-concepts'
$official=[System.Drawing.Image]::FromFile("$OutDir\ref\item_storage_cell_1k.png")
$src=New-Object System.Drawing.Bitmap($official)
$sw=$src.Width;$sh=$src.Height
$canvas=New-Object System.Drawing.Bitmap(64,64,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
function P([int]$x,[int]$y,$c){ $canvas.SetPixel($x,$y,$c) }
function D2([double]$x1,[double]$y1,[double]$x2,[double]$y2){ [math]::Sqrt(($x1-$x2)*($x1-$x2)+($y1-$y2)*($y1-$y2)) }
$bgC0=[System.Drawing.Color]::FromArgb(255,24,40,72)
$bgC1=[System.Drawing.Color]::FromArgb(255,14,24,48)
$bgC2=[System.Drawing.Color]::FromArgb(255,6,10,22)

# disk 3x = 48x48 (x 8..55, y 8..55); infinity thinner: centers (20,32)/(44,32) rout 8.5 rin 6
for($y=0;$y -lt 64;$y++){
  for($x=0;$x -lt 64;$x++){
    $d=D2 $x $y 32 32
    $bgc=if($d -lt 19){$bgC0}elseif($d -lt 33){$bgC1}else{$bgC2}
    $pix=$null
    if($x -ge 8 -and $x -lt 56 -and $y -ge 8 -and $y -lt 56){
      $bx=[int](($x-8)/3);$by=[int](($y-8)/3)
      if($bx -ge 0 -and $bx -lt $sw -and $by -ge 0 -and $by -lt $sh){
        $pp=$src.GetPixel($bx,$by)
        if($pp.A -ge 64){$pix=$pp}
      }
    }
    $dL=D2 $x $y 20 32;$dR=D2 $x $y 44 32
    $inL=(($dL -le 8.5)-and($dL -gt 6));$inR=(($dR -le 8.5)-and($dR -gt 6))
    $inInf=$inL -or $inR
    $inKnot=([math]::Abs($x-32)+[math]::Abs($y-32) -le 2.5) -and ([math]::Abs($x-32)+[math]::Abs($y-32) -ge 1)
    $infc=$null
    if($inInf){
      if($y -lt 28){$infc=[System.Drawing.Color]::FromArgb(255,242,254,255)}
      elseif($y -lt 33){$infc=[System.Drawing.Color]::FromArgb(255,150,228,249)}
      elseif($y -lt 38){$infc=[System.Drawing.Color]::FromArgb(255,78,192,222)}
      else{$infc=[System.Drawing.Color]::FromArgb(255,40,146,178)}
    }
    if($inKnot){$infc=[System.Drawing.Color]::FromArgb(255,255,255,255)}
    $final=$bgc
    if($pix -ne $null){$final=$pix}
    if($infc -ne $null){$final=$infc}
    P $x $y $final
  }
}
$canvas.Save("$OutDir\concept-E5-64.png",[System.Drawing.Imaging.ImageFormat]::Png)
$big=New-Object System.Drawing.Bitmap(512,512)
$gB=[System.Drawing.Graphics]::FromImage($big);$gB.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor;$gB.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half
$gB.DrawImage($canvas,0,0,512,512);$gB.Dispose()
$big.Save("$OutDir\concept-E5.png",[System.Drawing.Imaging.ImageFormat]::Png);$big.Dispose()
$s16=New-Object System.Drawing.Bitmap(16,16)
$g16=[System.Drawing.Graphics]::FromImage($s16);$g16.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$bigSrc=[System.Drawing.Image]::FromFile("$OutDir\concept-E5.png")
$g16.DrawImage($bigSrc,0,0,16,16);$g16.Dispose()
$s16.Save("$OutDir\check-E5-16.png",[System.Drawing.Imaging.ImageFormat]::Png);$s16.Dispose()
$blow=New-Object System.Drawing.Bitmap(128,128)
$gB2=[System.Drawing.Graphics]::FromImage($blow);$gB2.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$s16b=[System.Drawing.Image]::FromFile("$OutDir\check-E5-16.png")
$gB2.DrawImage($s16b,0,0,128,128);$gB2.Dispose()
$blow.Save("$OutDir\check-E5-16to128.png",[System.Drawing.Imaging.ImageFormat]::Png);$blow.Dispose()
$s16b.Dispose();$bigSrc.Dispose();$canvas.Dispose();$src.Dispose();$official.Dispose()
Write-Output "E5 ok"
