
Add-Type -AssemblyName System.Drawing
$OutDir = 'E:\改着玩\AE2-Infinite-Expansion\icon-concepts'
function New-Bmp {
  $bmp = New-Object System.Drawing.Bitmap(512,512,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  return @($bmp,$g)
}
function Pt([int]$x,[int]$y){ New-Object System.Drawing.Point($x,$y) }

$t=New-Bmp;$bmp=$t[0];$g=$t[1]
$g.Clear([System.Drawing.Color]::FromArgb(255,6,10,22))
$gp=New-Object System.Drawing.Drawing2D.GraphicsPath;$gp.AddEllipse(0,0,512,512)
$pgb=New-Object System.Drawing.Drawing2D.PathGradientBrush($gp)
$pgb.CenterColor=[System.Drawing.Color]::FromArgb(255,21,32,72)
$pgb.SurroundColors=@([System.Drawing.Color]::FromArgb(255,6,10,22))
$pgb.CenterPoint=New-Object System.Drawing.PointF(252,262)
$g.FillPath($pgb,$gp);$pgb.Dispose();$gp.Dispose()

# ---- AE2 silver disk (flatter, fuller) ----
$CY=286
$b0=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(70,0,0,0))
$g.FillEllipse($b0,256-158,$CY+50-2,316,152);$b0.Dispose()
# base bottom
$bb=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255,33,39,49))
$g.FillEllipse($bb,256-154,$CY+40,308,136);$bb.Dispose()
# side wall
$sb=New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(102,$CY-10,308,64)),[System.Drawing.Color]::FromArgb(255,84,95,116),[System.Drawing.Color]::FromArgb(255,40,47,58),90)
$g.FillRectangle($sb,256-154,$CY,308,54);$sb.Dispose()
# top face
$tg=New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(102,$CY-96,308,172)),[System.Drawing.Color]::FromArgb(255,244,248,253),[System.Drawing.Color]::FromArgb(255,150,162,182),55)
$cp=New-Object System.Drawing.Drawing2D.ColorBlend
$cp.Colors=@([System.Drawing.Color]::FromArgb(255,244,248,253),[System.Drawing.Color]::FromArgb(255,208,217,230),[System.Drawing.Color]::FromArgb(255,138,150,170))
$cp.Positions=@(0.0,0.55,1.0);$tg.InterpolationColors=$cp
$g.FillEllipse($tg,256-154,$CY-82,308,156);$tg.Dispose()
$ph=New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(105,255,255,255),5);$ph.StartCap='Round';$ph.EndCap='Round'
$g.DrawArc($ph,256-146,$CY-74,292,140,168,142);$ph.Dispose()
$ps2=New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(85,118,130,150),2)
$g.DrawEllipse($ps2,256-128,$CY-54,256,104);$ps2.Dispose()

# ---- blue label band (shorter, capsule, AE2 blue) ----
$bg2=New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(122,$CY-24,268,48)),[System.Drawing.Color]::FromArgb(255,74,130,224),[System.Drawing.Color]::FromArgb(255,24,66,148),90)
$bPath=New-Object System.Drawing.Drawing2D.GraphicsPath
$bPath.AddArc(122,$CY-24,26,26,180,90);$bPath.AddArc(364,$CY-24,26,26,270,90)
$bPath.AddArc(364,$CY+22,26,26,0,90);$bPath.AddArc(122,$CY+22,26,26,90,90);$bPath.CloseFigure()
$g.FillPath($bg2,$bPath)
$hl=New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(80,255,255,255),2)
$g.DrawLine($hl,136,$CY-14,376,$CY-14);$g.DrawLine($hl,136,$CY+6,376,$CY+6);$hl.Dispose()

# ---- infinity ring (crystal blue, thinner, grounded) ----
$IR=256-14
$ir=New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(0,150,512,240)),[System.Drawing.Color]::Transparent,[System.Drawing.Color]::Transparent,90)
$cpI=New-Object System.Drawing.Drawing2D.ColorBlend
$cpI.Colors=@([System.Drawing.Color]::FromArgb(255,238,252,255),[System.Drawing.Color]::FromArgb(255,134,220,244),[System.Drawing.Color]::FromArgb(255,60,168,210),[System.Drawing.Color]::FromArgb(255,27,105,141))
$cpI.Positions=@(0.0,0.4,0.75,1.0);$ir.InterpolationColors=$cpI
foreach($side in @(-1,1)){
  $cx=256+$side*96;$icx=256+$side*84
  $p2=New-Object System.Drawing.Drawing2D.GraphicsPath
  $p2.FillMode=[System.Drawing.Drawing2D.FillMode]::Alternate
  $p2.AddEllipse($cx-106,$IR-106,212,212)
  $p2.AddEllipse($icx-80,$IR-80,160,160)
  $g.FillPath($ir,$p2);$p2.Dispose()
}
# upper highlight arc only (hot edge, thin)
$ih=New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(170,255,255,255),6);$ih.StartCap='Round';$ih.EndCap='Round'
$g.DrawArc($ih,256-96-102,$IR-102,204,204,208,110)
$g.DrawArc($ih,256+96-102,$IR-102,204,204,222,110)
$ih.Dispose()
# knot diamond (white-hot core)
$core=New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(230,$IR-28,52,56)),[System.Drawing.Color]::FromArgb(255,255,255,255),[System.Drawing.Color]::FromArgb(255,80,196,230),38)
$p3=New-Object System.Drawing.Drawing2D.GraphicsPath
$p3.AddPolygon([System.Drawing.Point[]]@((Pt 256 ($IR-28)),(Pt 284 $IR),(Pt 256 ($IR+28)),(Pt 228 $IR)))
$g.FillPath($core,$p3)

$bmp.Save("$OutDir\concept-D2.png",[System.Drawing.Imaging.ImageFormat]::Png);$g.Dispose();$bmp.Dispose()
Write-Output "D2 ok"
