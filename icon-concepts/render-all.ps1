
Add-Type -AssemblyName System.Drawing
$OutDir = 'E:\改着玩\AE2-Infinite-Expansion\icon-concepts'

function New-Bmp {
  $bmp = New-Object System.Drawing.Bitmap(512,512,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  return @($bmp,$g)
}

# smooth radial background: dark base + PathGradient (no concentric banding)
function Draw-Base {
  param($g)
  $g.Clear([System.Drawing.Color]::FromArgb(255,5,8,18))
  $gp = New-Object System.Drawing.Drawing2D.GraphicsPath
  $gp.AddEllipse(0,0,512,512)
  $pgb = New-Object System.Drawing.Drawing2D.PathGradientBrush($gp)
  $pgb.CenterColor = [System.Drawing.Color]::FromArgb(255,24,38,84)
  $pgb.SurroundColors = @([System.Drawing.Color]::FromArgb(255,5,8,18))
  $pgb.CenterPoint = New-Object System.Drawing.PointF(250,240)
  $g.FillPath($pgb,$gp)
  $pgb.Dispose(); $gp.Dispose()
  # subtle teal glow near center
  $gp2 = New-Object System.Drawing.Drawing2D.GraphicsPath
  $gp2.AddEllipse(116,116,280,280)
  $pgb2 = New-Object System.Drawing.Drawing2D.PathGradientBrush($gp2)
  $pgb2.CenterColor = [System.Drawing.Color]::FromArgb(46,34,211,238)
  $pgb2.SurroundColors = @([System.Drawing.Color]::FromArgb(0,34,211,238))
  $g.FillPath($pgb2,$gp2)
  $pgb2.Dispose(); $gp2.Dispose()
}

function Pt([int]$x,[int]$y){ New-Object System.Drawing.Point($x,$y) }

# ===== A: Energy Infinity =====
$t = New-Bmp; $bmp=$t[0]; $g=$t[1]
Draw-Base $g
$ringGrad = New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(0,140,512,232)),[System.Drawing.Color]::Transparent,[System.Drawing.Color]::Transparent,90)
$cp = New-Object System.Drawing.Drawing2D.ColorBlend
$cp.Colors = @([System.Drawing.Color]::FromArgb(255,103,232,249),[System.Drawing.Color]::FromArgb(255,34,211,238),[System.Drawing.Color]::FromArgb(255,13,100,125))
$cp.Positions = @(0.0,0.5,1.0)
$ringGrad.InterpolationColors = $cp
foreach ($side in @(-1,1)) {
  $cx = 256 + $side*105
  $icx = 256 + $side*88
  $p2 = New-Object System.Drawing.Drawing2D.GraphicsPath
  $p2.FillMode = [System.Drawing.Drawing2D.FillMode]::Alternate
  $p2.AddEllipse($cx-122,134,244,244)
  $p2.AddEllipse($icx-93,163,186,186)
  $g.FillPath($ringGrad,$p2)
  $p2.Dispose()
}
# top highlight arcs (thicker, brighter)
$hp = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(150,224,250,255),11)
$hp.StartCap='Round'; $hp.EndCap='Round'
$g.DrawArc($hp,151-118,256-118,236,236,205,115)
$g.DrawArc($hp,361-118,256-118,236,236,220,115)
$hp.Dispose()
# central isolation disc
$b3 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(235,7,48,63))
$g.FillEllipse($b3,160,160,192,192); $b3.Dispose()
$pp = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(150,14,116,144),1.5)
$g.DrawEllipse($pp,160,160,192,192); $pp.Dispose()
# diamond core (strong gradient white -> teal -> deep)
$dg = New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(172,180,168,152)),[System.Drawing.Color]::Transparent,[System.Drawing.Color]::Transparent,38)
$cp2 = New-Object System.Drawing.Drawing2D.ColorBlend
$cp2.Colors = @([System.Drawing.Color]::FromArgb(255,240,253,255),[System.Drawing.Color]::FromArgb(255,64,226,245),[System.Drawing.Color]::FromArgb(255,10,80,102))
$cp2.Positions = @(0.0,0.42,1.0)
$dg.InterpolationColors = $cp2
$p3 = New-Object System.Drawing.Drawing2D.GraphicsPath
$p3.AddPolygon([System.Drawing.Point[]]@((Pt 256 180),(Pt 340 256),(Pt 256 332),(Pt 172 256)))
$g.FillPath($dg,$p3)
# facet highlight + core dot
$b4 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(90,255,255,255))
$g.FillPolygon($b4,[System.Drawing.Point[]]@((Pt 256 214),(Pt 292 256),(Pt 256 298),(Pt 220 256)))
$b4.Dispose()
$b5 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$g.FillEllipse($b5,249,241,14,14); $b5.Dispose()
$bmp.Save("$OutDir\concept-A.png",[System.Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $bmp.Dispose()
Write-Output "A ok"

# ===== B: Gravitational Lensing =====
$t = New-Bmp; $bmp=$t[0]; $g=$t[1]
Draw-Base $g
# stars
$stars = @(@(72,92,1.6,204),@(168,60,1.1,128),@(442,120,1.8,178),@(410,420,1.3,153),@(60,356,1.2,140),@(330,470,1.5,178),@(120,452,1.0,128),@(470,300,1.2,128))
foreach ($s in $stars) {
  $sb = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb($s[3],219,233,255))
  $g.FillEllipse($sb,$s[0]-$s[2],$s[1]-$s[2],$s[2]*2,$s[2]*2); $sb.Dispose()
}
# four-point sparkles
$sb2 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(230,255,255,255))
$g.FillPolygon($sb2,[System.Drawing.Point[]]@((Pt 418 58),(Pt 420.5 66),(Pt 428 68.5),(Pt 420.5 71),(Pt 418 79),(Pt 415.5 71),(Pt 408 68.5),(Pt 415.5 66)))
$g.FillPolygon($sb2,[System.Drawing.Point[]]@((Pt 88 296),(Pt 90.4 304),(Pt 98 306.4),(Pt 90.4 308.8),(Pt 88 316),(Pt 85.6 308.8),(Pt 78 306.4),(Pt 85.6 304)))
$sb2.Dispose()
# accretion disk: 16 solid ellipses outer(deep red) -> inner(white-hot)
$c0=[System.Drawing.Color]::FromArgb(255,109,19,10); $c1=[System.Drawing.Color]::FromArgb(255,224,82,42)
$c2=[System.Drawing.Color]::FromArgb(255,255,140,66); $c3=[System.Drawing.Color]::FromArgb(255,255,209,102)
$c4=[System.Drawing.Color]::FromArgb(255,255,248,232)
for ($i=0;$i -lt 16;$i++){
  $t2=$i/15.0
  $col = if($t2 -lt 0.35){ $tt=$t2/0.35; $r=[int]($c0.R+($c1.R-$c0.R)*$tt);$gg=[int]($c0.G+($c1.G-$c0.G)*$tt);$bb=[int]($c0.B+($c1.B-$c0.B)*$tt); [System.Drawing.Color]::FromArgb(255,$r,$gg,$bb) }
        elseif($t2 -lt 0.62){ $tt=($t2-0.35)/0.27; $r=[int]($c1.R+($c2.R-$c1.R)*$tt);$gg=[int]($c1.G+($c2.G-$c1.G)*$tt);$bb=[int]($c1.B+($c2.B-$c1.B)*$tt); [System.Drawing.Color]::FromArgb(255,$r,$gg,$bb) }
        elseif($t2 -lt 0.84){ $tt=($t2-0.62)/0.22; $r=[int]($c2.R+($c3.R-$c2.R)*$tt);$gg=[int]($c2.G+($c3.G-$c2.G)*$tt);$bb=[int]($c2.B+($c3.B-$c2.B)*$tt); [System.Drawing.Color]::FromArgb(255,$r,$gg,$bb) }
        else{ $tt=($t2-0.84)/0.16; $r=[int]($c3.R+($c4.R-$c3.R)*$tt);$gg=[int]($c3.G+($c4.G-$c3.G)*$tt);$bb=[int]($c3.B+($c4.B-$c3.B)*$tt); [System.Drawing.Color]::FromArgb(255,$r,$gg,$bb) }
  $rx = 168 - 42*$t2; $ry = 108 - 32*$t2
  $sb3 = New-Object System.Drawing.SolidBrush($col)
  $g.FillEllipse($sb3,256-$rx,256-$ry,$rx*2,$ry*2); $sb3.Dispose()
}
# photon ring: arcs above the hole (lensing signature)
$pa = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(95,255,156,91),14); $pa.StartCap='Round';$pa.EndCap='Round'
$g.DrawArc($pa,256-196,256-118,392,236,195,150)
$pa.Dispose()
$pb = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(200,255,217,168),6); $pb.StartCap='Round';$pb.EndCap='Round'
$g.DrawArc($pb,256-204,256-128,408,256,193,154)
$pb.Dispose()
$pc = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(245,255,255,255),2.5); $pc.StartCap='Round';$pc.EndCap='Round'
$g.DrawArc($pc,256-200,256-122,400,244,194,152)
$pc.Dispose()
# event horizon
$b6 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::Black)
$g.FillEllipse($b6,256-82,256-82,164,164); $b6.Dispose()
$bmp.Save("$OutDir\concept-B.png",[System.Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $bmp.Dispose()
Write-Output "B ok"

# ===== C: Circuit Infinity =====
$t = New-Bmp; $bmp=$t[0]; $g=$t[1]
Draw-Base $g
# hex frame
$hex = [System.Drawing.Point[]]@((Pt 256 42),(Pt 441 149),(Pt 441 363),(Pt 256 470),(Pt 71 363),(Pt 71 149))
$pp2 = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(235,30,48,96),2.5)
$g.DrawPolygon($pp2,$hex); $pp2.Dispose()
$hb = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(115,34,211,238))
foreach($v in $hex){ $g.FillEllipse($hb,$v.X-5,$v.Y-5,10,10) }
$hb.Dispose()
# rounded square rings via arc path (pen stroke)
function New-RoundRectPath([int]$x,[int]$y,[int]$w,[int]$h,[int]$rr){
  $p = New-Object System.Drawing.Drawing2D.GraphicsPath
  $p.AddArc($x,$y,$rr,$rr,180,90)
  $p.AddArc($x+$w-$rr,$y,$rr,$rr,270,90)
  $p.AddArc($x+$w-$rr,$y+$h-$rr,$rr,$rr,0,90)
  $p.AddArc($x,$y+$h-$rr,$rr,$rr,90,90)
  $p.CloseFigure()
  return $p
}
$rp = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255,34,211,238),13)
$rp.LineJoin='Round'
$g.DrawPath($rp,(New-RoundRectPath 59 139 100 234 16))
$g.DrawPath($rp,(New-RoundRectPath 353 139 100 234 16))
$rp.Dispose()
# crossing energy lines
$cl = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255,14,116,144),13); $cl.StartCap='Round';$cl.EndCap='Round'
$g.DrawLine($cl,200,205,312,307)
$g.DrawLine($cl,312,205,200,307)
$cl.Dispose()
$cl2 = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(215,103,232,249),4); $cl2.StartCap='Round';$cl2.EndCap='Round'
$g.DrawLine($cl2,206,211,306,301)
$g.DrawLine($cl2,306,211,206,301)
$cl2.Dispose()
# vias
$vb = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255,103,232,249))
$g.FillEllipse($vb,61,247,18,18); $g.FillEllipse($vb,441,247,18,18)
$g.FillEllipse($vb,104,141,14,14); $g.FillEllipse($vb,398,361,14,14)
$vb.Dispose()
$wb = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(230,255,255,255))
$g.FillEllipse($wb,66,252,8,8); $g.FillEllipse($wb,446,252,8,8)
$g.FillEllipse($wb,108,145,6,6); $g.FillEllipse($wb,402,365,6,6)
$wb.Dispose()
# core
$b7 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(242,7,48,63))
$g.FillEllipse($b7,180,180,152,152); $b7.Dispose()
$cg = New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(194,196,124,120)),[System.Drawing.Color]::Transparent,[System.Drawing.Color]::Transparent,38)
$cp3 = New-Object System.Drawing.Drawing2D.ColorBlend
$cp3.Colors = @([System.Drawing.Color]::FromArgb(255,255,255,255),[System.Drawing.Color]::FromArgb(255,199,247,255),[System.Drawing.Color]::FromArgb(255,34,211,238),[System.Drawing.Color]::FromArgb(255,11,94,117))
$cp3.Positions = @(0.0,0.3,0.7,1.0)
$cg.InterpolationColors = $cp3
$p4 = New-Object System.Drawing.Drawing2D.GraphicsPath
$p4.AddPolygon([System.Drawing.Point[]]@((Pt 256 196),(Pt 318 256),(Pt 256 316),(Pt 194 256)))
$g.FillPath($cg,$p4)
$b8 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(140,255,255,255))
$g.FillPolygon($b8,[System.Drawing.Point[]]@((Pt 256 222),(Pt 286 256),(Pt 256 290),(Pt 226 256)))
$b8.Dispose()
$bmp.Save("$OutDir\concept-C.png",[System.Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $bmp.Dispose()
Write-Output "C ok"
