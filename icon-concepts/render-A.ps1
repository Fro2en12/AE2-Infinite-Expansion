
Add-Type -AssemblyName System.Drawing
$OutDir = 'E:\改着玩\AE2-Infinite-Expansion\icon-concepts'

function New-Bitmap {
  param([int]$W=512,[int]$H=512)
  $bmp = New-Object System.Drawing.Bitmap($W,$H,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  return @($bmp,$g)
}

function Draw-Base {
  param($g)
  # dark space background: diagonal blend (center-left lighter)
  $rect = New-Object System.Drawing.Rectangle(0,0,512,512)
  $lg = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect,[System.Drawing.Color]::FromArgb(255,17,28,66),[System.Drawing.Color]::FromArgb(255,5,8,18),135)
  $g.FillRectangle($lg,$rect)
  $lg.Dispose()
  # teal center halo: concentric alpha ellipses
  for ($i=0; $i -lt 7; $i++) {
    $r = 250 * (1-$i/7.2)
    $a = [int](20*(1-$i/7))
    $b2 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb($a,34,211,238))
    $g.FillEllipse($b2,256-$r,256-$r,$r*2,$r*2)
    $b2.Dispose()
  }
}

function Color-Lerp {
  param($c1,$c2,[double]$t)
  $r=[int]($c1.R+($c2.R-$c1.R)*$t); $gg=[int]($c1.G+($c2.G-$c1.G)*$t); $b=[int]($c1.B+($c2.B-$c1.B)*$t)
  return [System.Drawing.Color]::FromArgb($r,$gg,$b)
}

# ================= A: Energy Infinity =================
$t = New-Bitmap
$bmp=$t[0]; $g=$t[1]
Draw-Base $g
# infinity rings: outer circle + offset inner circle, evenodd
$ringGrad = New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(0,140,512,232)),[System.Drawing.Color]::FromArgb(255,103,232,249),[System.Drawing.Color]::FromArgb(255,14,116,144),90)
$mid = New-Object System.Drawing.Color
$cp = New-Object System.Drawing.Drawing2D.ColorBlend
$cp.Colors = @([System.Drawing.Color]::FromArgb(255,103,232,249),[System.Drawing.Color]::FromArgb(255,34,211,238),[System.Drawing.Color]::FromArgb(255,14,116,144))
$cp.Positions = @(0.0,0.55,1.0)
$ringGrad.InterpolationColors = $cp
foreach ($side in @(-1,1)) {
  $cx = 256 + $side*105
  $icx = 256 + $side*88
  $p = New-Object System.Drawing.Drawing2D.GraphicsPath
  $p.FillMode = [System.Drawing.Drawing2D.FillMode]::Alternate
  $p.AddEllipse($cx-122,256-122,244,244)
  $p.AddEllipse($icx-93,256-93,186,186)
  $g.FillPath($ringGrad,$p)
  $p.Dispose()
}
# top highlight arcs
$hp = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(128,224,250,255),9)
$hp.StartCap='Round'; $hp.EndCap='Round'
$g.DrawArc($hp,256-105-118,256-118,236,236,200,120)   # left ring top arc
$g.DrawArc($hp,256+105-118,256-118,236,236,220,120)   # right ring top
$hp.Dispose()
# central isolation disc
$b3 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(235,7,48,63))
$g.FillEllipse($b3,160,160,192,192); $b3.Dispose()
$pp = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(150,14,116,144),1.5)
$g.DrawEllipse($pp,160,160,192,192); $pp.Dispose()
# diamond core
$dg = New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(172,180,168,152)),[System.Drawing.Color]::FromArgb(255,207,248,255),[System.Drawing.Color]::FromArgb(255,11,94,117),25)
$cp2 = New-Object System.Drawing.Drawing2D.ColorBlend
$cp2.Colors = @([System.Drawing.Color]::FromArgb(255,207,248,255),[System.Drawing.Color]::FromArgb(255,34,211,238),[System.Drawing.Color]::FromArgb(255,11,94,117))
$cp2.Positions = @(0.0,0.35,1.0)
$dg.InterpolationColors = $cp2
$p2 = New-Object System.Drawing.Drawing2D.GraphicsPath
$p2.AddPolygon(@((New-Object System.Drawing.Point(256,180)),(New-Object System.Drawing.Point(340,256)),(New-Object System.Drawing.Point(256,332)),(New-Object System.Drawing.Point(172,256))))
$g.FillPath($dg,$p2)
$b4 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(77,255,255,255))
$g.FillPolygon($b4,@((New-Object System.Drawing.Point(256,214)),(New-Object System.Drawing.Point(292,256)),(New-Object System.Drawing.Point(256,298)),(New-Object System.Drawing.Point(220,256))))
$b4.Dispose()
$b5 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$g.FillEllipse($b5,249,241,14,14)
$b5.Dispose()
$bmp.Save("$OutDir\concept-A.png",[System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Write-Output "A ok"
