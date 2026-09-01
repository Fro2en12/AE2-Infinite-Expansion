
Add-Type -AssemblyName System.Drawing
$OutDir = 'E:\改着玩\AE2-Infinite-Expansion\icon-concepts'
$SIZE = 512
function New-Bmp {
  $bmp = [System.Drawing.Bitmap]::new($SIZE,$SIZE,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  return @($bmp,$g)
}
function PT([int]$x,[int]$y){ [System.Drawing.Point]::new($x,$y) }

$t = New-Bmp; $bmp=$t[0]; $g=$t[1]
$g.Clear([System.Drawing.Color]::FromArgb(255,6,10,22))
$bgPath=[System.Drawing.Drawing2D.GraphicsPath]::new(); $bgPath.AddEllipse(0,0,512,512)
$pgb=[System.Drawing.Drawing2D.PathGradientBrush]::new($bgPath)
$pgb.CenterColor=[System.Drawing.Color]::FromArgb(255,21,32,72)
$pgb.SurroundColors=@([System.Drawing.Color]::FromArgb(255,6,10,22))
$pgb.CenterPoint=[System.Drawing.PointF]::new(252,266)
$g.FillPath($pgb,$bgPath);$pgb.Dispose();$bgPath.Dispose()

$CY = 288
# shadow
$bSh=[System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(70,0,0,0))
$g.FillEllipse($bSh,98,$CY+42,316,124);$bSh.Dispose()
# bottom of disc
$bBt=[System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255,33,39,49))
$g.FillEllipse($bBt,102,$CY+34,308,120);$bBt.Dispose()
# side wall
$sbGrad=[System.Drawing.Drawing2D.LinearGradientBrush]::new([System.Drawing.Rectangle]::new(102,$CY,308,60),[System.Drawing.Color]::FromArgb(255,84,95,116),[System.Drawing.Color]::FromArgb(255,40,47,58),90)
$g.FillRectangle($sbGrad,102,$CY,308,52);$sbGrad.Dispose()
# top face (silver)
$tgGrad=[System.Drawing.Drawing2D.LinearGradientBrush]::new([System.Drawing.Rectangle]::new(102,$CY-92,308,164),[System.Drawing.Color]::FromArgb(255,244,248,253),[System.Drawing.Color]::FromArgb(255,138,150,170),55)
$cpT=[System.Drawing.Drawing2D.ColorBlend]::new()
$cpT.Colors=@([System.Drawing.Color]::FromArgb(255,244,248,253),[System.Drawing.Color]::FromArgb(255,208,217,230),[System.Drawing.Color]::FromArgb(255,138,150,170))
$cpT.Positions=@(0.0,0.55,1.0);$tgGrad.InterpolationColors=$cpT
$g.FillEllipse($tgGrad,102,$CY-86,308,148);$tgGrad.Dispose()
# rim highlight
$penHL=[System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(105,255,255,255),5);$penHL.StartCap=[System.Drawing.Drawing2D.LineCap]::Round;$penHL.EndCap=[System.Drawing.Drawing2D.LineCap]::Round
$g.DrawArc($penHL,110,$CY-78,292,132,168,142);$penHL.Dispose()
# seam ring
$penSeam=[System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(85,118,130,150),2)
$g.DrawEllipse($penSeam,128,$CY-58,256,96);$penSeam.Dispose()

# blue label band (capsule)
$bandGrad=[System.Drawing.Drawing2D.LinearGradientBrush]::new([System.Drawing.Rectangle]::new(122,$CY-26,268,52),[System.Drawing.Color]::FromArgb(255,74,130,224),[System.Drawing.Color]::FromArgb(255,24,66,148),90)
$bandPath=[System.Drawing.Drawing2D.GraphicsPath]::new()
$bandPath.AddArc(122,$CY-26,26,26,180,90);$bandPath.AddArc(364,$CY-26,26,26,270,90)
$bandPath.AddArc(364,$CY+26,26,26,0,90);$bandPath.AddArc(122,$CY+26,26,26,90,90);$bandPath.CloseFigure()
$g.FillPath($bandGrad,$bandPath)
$penBand=[System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(80,255,255,255),2)
$g.DrawLine($penBand,136,$CY-16,376,$CY-16);$g.DrawLine($penBand,136,$CY+4,376,$CY+4);$penBand.Dispose()

# infinity ring (crystal blue)
$INF_Y = 258
$ringGrad=[System.Drawing.Drawing2D.LinearGradientBrush]::new([System.Drawing.Rectangle]::new(0,150,512,232),[System.Drawing.Color]::FromArgb(255,238,252,255),[System.Drawing.Color]::FromArgb(255,27,105,141),90)
$cpR=[System.Drawing.Drawing2D.ColorBlend]::new()
$cpR.Colors=@([System.Drawing.Color]::FromArgb(255,238,252,255),[System.Drawing.Color]::FromArgb(255,134,220,244),[System.Drawing.Color]::FromArgb(255,60,168,210),[System.Drawing.Color]::FromArgb(255,27,105,141))
$cpR.Positions=@(0.0,0.4,0.75,1.0);$ringGrad.InterpolationColors=$cpR
foreach($side in @(-1,1)){
  $cx = 256 + $side*96; $icx = 256 + $side*84
  $ringPath=[System.Drawing.Drawing2D.GraphicsPath]::new()
  $ringPath.FillMode=[System.Drawing.Drawing2D.FillMode]::Alternate
  $ringPath.AddEllipse($cx-106,$INF_Y-106,212,212)
  $ringPath.AddEllipse($icx-80,$INF_Y-80,160,160)
  $g.FillPath($ringGrad,$ringPath);$ringPath.Dispose()
}
$penInf=[System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(170,255,255,255),6);$penInf.StartCap=[System.Drawing.Drawing2D.LineCap]::Round;$penInf.EndCap=[System.Drawing.Drawing2D.LineCap]::Round
$g.DrawArc($penInf,154-96-102+102,246-102+12,204,204,208,110) | Out-Null
$penInf.Dispose()
# knot diamond
$coreGrad=[System.Drawing.Drawing2D.LinearGradientBrush]::new([System.Drawing.Rectangle]::new(230,$INF_Y-28,52,56),[System.Drawing.Color]::FromArgb(255,255,255,255),[System.Drawing.Color]::FromArgb(255,80,196,230),38)
$knotPath=[System.Drawing.Drawing2D.GraphicsPath]::new()
$knotPath.AddPolygon([System.Drawing.Point[]]@((PT 256 ($INF_Y-28)),(PT 284 $INF_Y),(PT 256 ($INF_Y+28)),(PT 228 $INF_Y)))
$g.FillPath($coreGrad,$knotPath)
$bmp.Save("$OutDir\concept-D2.png",[System.Drawing.Imaging.ImageFormat]::Png);$g.Dispose();$bmp.Dispose()
Write-Output "D2 ok"
