
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
# ---- space bg ----
$g.Clear([System.Drawing.Color]::FromArgb(255,6,10,22))
$gp=New-Object System.Drawing.Drawing2D.GraphicsPath;$gp.AddEllipse(0,0,512,512)
$pgb=New-Object System.Drawing.Drawing2D.PathGradientBrush($gp)
$pgb.CenterColor=[System.Drawing.Color]::FromArgb(255,21,32,72)
$pgb.SurroundColors=@([System.Drawing.Color]::FromArgb(255,6,10,22))
$pgb.CenterPoint=New-Object System.Drawing.PointF(252,258)
$g.FillPath($pgb,$gp);$pgb.Dispose();$gp.Dispose()

# ---- AE2 storage disk (silver cylinder disc, slight top view) ----
# bottom edge shadow
$b0=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(60,0,0,0))
$g.FillEllipse($b0,256-158,262+64-4,316,152);$b0.Dispose()
# body side wall (dark steel) + bottom ellipse
$bw=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255,52,60,74))
$g.FillEllipse($bw,256-158,262+64-74,316,148);$bw.Dispose()
$sb=New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(98,238,316,90)),[System.Drawing.Color]::FromArgb(255,88,99,122),[System.Drawing.Color]::FromArgb(255,44,51,63),90)
$g.FillRectangle($sb,256-158,262-60,316,128);$sb.Dispose()
$bb=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255,38,44,55))
$g.FillEllipse($bb,256-158,262+18,316,148);$bb.Dispose()
# top face (bright silver, light from top-left)
$tg=New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(98,186,316,152)),[System.Drawing.Color]::FromArgb(255,242,246,252),[System.Drawing.Color]::FromArgb(255,154,165,184),55)
$cp=New-Object System.Drawing.Drawing2D.ColorBlend
$cp.Colors=@([System.Drawing.Color]::FromArgb(255,242,246,252),[System.Drawing.Color]::FromArgb(255,205,214,228),[System.Drawing.Color]::FromArgb(255,140,152,172))
$cp.Positions=@(0.0,0.55,1.0);$tg.InterpolationColors=$cp
$g.FillEllipse($tg,256-158,262-74,316,148);$tg.Dispose()
# top face highlight rim
$ph=New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(110,255,255,255),5);$ph.StartCap='Round';$ph.EndCap='Round'
$g.DrawArc($ph,256-150,262-66,300,132,170,140);$ph.Dispose()
# subtle dark lower rim of top face
$pk=New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(60,20,26,36),3)
$g.DrawArc($pk,256-150,262-66,300,132,10,160);$pk.Dispose()
# metal seam line across top (like the disc's edge ring)
$ps2=New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(90,120,132,152),2)
$g.DrawEllipse($ps2,256-132,262-48,264,96);$ps2.Dispose()

# ---- blue label band across the face (AE2 label stripe) ----
$bg2=New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(104,236,304,52)),[System.Drawing.Color]::FromArgb(255,63,122,222),[System.Drawing.Color]::FromArgb(255,28,74,158),90)
# rounded band
$bPath=New-Object System.Drawing.Drawing2D.GraphicsPath
$bPath.AddArc(104,236,20,20,180,90);$bPath.AddArc(388,236,20,20,270,90)
$bPath.AddArc(388,268,20,20,0,90);$bPath.AddArc(104,268,20,20,90,90);$bPath.CloseFigure()
$g.FillPath($bg2,$bPath)
# band inner highlight lines
$hl=New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(90,255,255,255),2)
$g.DrawLine($hl,116,244,396,244);$g.DrawLine($hl,116,260,396,260);$hl.Dispose()

# ---- infinity ring (single-stroke knot, white-hot -> ice -> deep blue) ----
$ir=New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(0,130,512,264)),[System.Drawing.Color]::Transparent,[System.Drawing.Color]::Transparent,90)
$cpI=New-Object System.Drawing.Drawing2D.ColorBlend
$cpI.Colors=@([System.Drawing.Color]::FromArgb(255,244,253,255),[System.Drawing.Color]::FromArgb(255,126,220,246),[System.Drawing.Color]::FromArgb(255,44,146,186))
$cpI.Positions=@(0.0,0.5,1.0);$ir.InterpolationColors=$cpI
foreach($side in @(-1,1)){
  $cx=256+$side*98;$icx=256+$side*84
  $p2=New-Object System.Drawing.Drawing2D.GraphicsPath
  $p2.FillMode=[System.Drawing.Drawing2D.FillMode]::Alternate
  $p2.AddEllipse($cx-112,262-112,224,224)
  $p2.AddEllipse($icx-82,262-82,164,164)
  $g.FillPath($ir,$p2);$p2.Dispose()
}
# infinity top highlight arcs
$ih=New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(150,255,255,255),8);$ih.StartCap='Round';$ih.EndCap='Round'
$g.DrawArc($ih,256-98-108,262-108,216,216,205,115)
$g.DrawArc($ih,256+98-108,262-108,216,216,220,115)
$ih.Dispose()
# small energy diamond at the knot
$core=New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(226,232,60,60)),[System.Drawing.Color]::FromArgb(255,255,255,255),[System.Drawing.Color]::FromArgb(255,70,190,226),38)
$p3=New-Object System.Drawing.Drawing2D.GraphicsPath
$p3.AddPolygon([System.Drawing.Point[]]@((Pt 256 232),(Pt 286 262),(Pt 256 292),(Pt 226 262)))
$g.FillPath($core,$p3)
$c2=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(80,255,255,255))
$g.FillPolygon($c2,[System.Drawing.Point[]]@((Pt 256 244),(Pt 274 262),(Pt 256 280),(Pt 238 262)))
$c2.Dispose()

$bmp.Save("$OutDir\concept-D-ae2disk.png",[System.Drawing.Imaging.ImageFormat]::Png);$g.Dispose();$bmp.Dispose()
Write-Output "D ok"
