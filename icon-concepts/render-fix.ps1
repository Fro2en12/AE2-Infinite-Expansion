
Add-Type -AssemblyName System.Drawing
$OutDir = 'E:\改着玩\AE2-Infinite-Expansion\icon-concepts'
function New-Bmp {
  $bmp = New-Object System.Drawing.Bitmap(512,512,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  return @($bmp,$g)
}
function Draw-Base {
  param($g)
  $g.Clear([System.Drawing.Color]::FromArgb(255,5,8,18))
  $gp = New-Object System.Drawing.Drawing2D.GraphicsPath; $gp.AddEllipse(0,0,512,512)
  $pgb = New-Object System.Drawing.Drawing2D.PathGradientBrush($gp)
  $pgb.CenterColor = [System.Drawing.Color]::FromArgb(255,24,38,84)
  $pgb.SurroundColors = @([System.Drawing.Color]::FromArgb(255,5,8,18))
  $pgb.CenterPoint = New-Object System.Drawing.PointF(250,240)
  $g.FillPath($pgb,$gp); $pgb.Dispose(); $gp.Dispose()
  $gp2 = New-Object System.Drawing.Drawing2D.GraphicsPath; $gp2.AddEllipse(116,116,280,280)
  $pgb2 = New-Object System.Drawing.Drawing2D.PathGradientBrush($gp2)
  $pgb2.CenterColor = [System.Drawing.Color]::FromArgb(46,34,211,238)
  $pgb2.SurroundColors = @([System.Drawing.Color]::FromArgb(0,34,211,238))
  $g.FillPath($pgb2,$gp2); $pgb2.Dispose(); $gp2.Dispose()
}
function Pt([int]$x,[int]$y){ New-Object System.Drawing.Point($x,$y) }

# ===== B fix: photon ring hugging the core top =====
$t = New-Bmp; $bmp=$t[0]; $g=$t[1]
Draw-Base $g
$stars = @(@(72,92,1.6,204),@(168,60,1.1,128),@(442,120,1.8,178),@(410,420,1.3,153),@(60,356,1.2,140),@(330,470,1.5,178),@(120,452,1.0,128),@(470,300,1.2,128))
foreach ($s in $stars) { $sb=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb($s[3],219,233,255)); $g.FillEllipse($sb,$s[0]-$s[2],$s[1]-$s[2],$s[2]*2,$s[2]*2); $sb.Dispose() }
$sb2=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(230,255,255,255))
$g.FillPolygon($sb2,[System.Drawing.Point[]]@((Pt 418 58),(Pt 420.5 66),(Pt 428 68.5),(Pt 420.5 71),(Pt 418 79),(Pt 415.5 71),(Pt 408 68.5),(Pt 415.5 66)))
$g.FillPolygon($sb2,[System.Drawing.Point[]]@((Pt 88 296),(Pt 90.4 304),(Pt 98 306.4),(Pt 90.4 308.8),(Pt 88 316),(Pt 85.6 308.8),(Pt 78 306.4),(Pt 85.6 304)))
$sb2.Dispose()
$c0=[System.Drawing.Color]::FromArgb(255,109,19,10);$c1=[System.Drawing.Color]::FromArgb(255,224,82,42);$c2=[System.Drawing.Color]::FromArgb(255,255,140,66);$c3=[System.Drawing.Color]::FromArgb(255,255,209,102);$c4=[System.Drawing.Color]::FromArgb(255,255,248,232)
for ($i=0;$i -lt 16;$i++){
  $t2=$i/15.0
  $col = if($t2 -lt 0.35){$tt=$t2/0.35;$r=[int]($c0.R+($c1.R-$c0.R)*$tt);$gg=[int]($c0.G+($c1.G-$c0.G)*$tt);$bb=[int]($c0.B+($c1.B-$c0.B)*$tt);[System.Drawing.Color]::FromArgb(255,$r,$gg,$bb)}
  elseif($t2 -lt 0.62){$tt=($t2-0.35)/0.27;$r=[int]($c1.R+($c2.R-$c1.R)*$tt);$gg=[int]($c1.G+($c2.G-$c1.G)*$tt);$bb=[int]($c1.B+($c2.B-$c1.B)*$tt);[System.Drawing.Color]::FromArgb(255,$r,$gg,$bb)}
  elseif($t2 -lt 0.84){$tt=($t2-0.62)/0.22;$r=[int]($c2.R+($c3.R-$c2.R)*$tt);$gg=[int]($c2.G+($c3.G-$c2.G)*$tt);$bb=[int]($c2.B+($c3.B-$c2.B)*$tt);[System.Drawing.Color]::FromArgb(255,$r,$gg,$bb)}
  else{$tt=($t2-0.84)/0.16;$r=[int]($c3.R+($c4.R-$c3.R)*$tt);$gg=[int]($c3.G+($c4.G-$c3.G)*$tt);$bb=[int]($c3.B+($c4.B-$c3.B)*$tt);[System.Drawing.Color]::FromArgb(255,$r,$gg,$bb)}
  $rx=168-42*$t2;$ry=108-32*$t2
  $sb3=New-Object System.Drawing.SolidBrush($col);$g.FillEllipse($sb3,256-$rx,256-$ry,$rx*2,$ry*2);$sb3.Dispose()
}
# photon ring now hugging the disk inner edge (bridge arch right above the hole)
$pa=New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(90,255,170,110),13);$pa.StartCap='Round';$pa.EndCap='Round'
$g.DrawArc($pa,256-172,256-114,344,228,192,156);$pa.Dispose()
$pb=New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(235,255,214,170),5.5);$pb.StartCap='Round';$pb.EndCap='Round'
$g.DrawArc($pb,256-176,256-118,352,236,192,156);$pb.Dispose()
$pc=New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(250,255,255,255),2);$pc.StartCap='Round';$pc.EndCap='Round'
$g.DrawArc($pc,256-174,256-116,348,232,192,156);$pc.Dispose()
$b6=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::Black);$g.FillEllipse($b6,256-82,256-82,164,164);$b6.Dispose()
$bmp.Save("$OutDir\concept-B.png",[System.Drawing.Imaging.ImageFormat]::Png);$g.Dispose();$bmp.Dispose()
Write-Output "B fixed"

# ===== C fix: crossing lines visible, core smaller, true infinity read =====
$t=New-Bmp;$bmp=$t[0];$g=$t[1]
Draw-Base $g
$hex=[System.Drawing.Point[]]@((Pt 256 42),(Pt 441 149),(Pt 441 363),(Pt 256 470),(Pt 71 363),(Pt 71 149))
$pp2=New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(245,38,60,120),2.5);$g.DrawPolygon($pp2,$hex);$pp2.Dispose()
$hb=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(120,34,211,238));foreach($v in $hex){$g.FillEllipse($hb,$v.X-5,$v.Y-5,10,10)};$hb.Dispose()
function New-RoundRectPath([int]$x,[int]$y,[int]$w,[int]$h,[int]$rr){
  $p=New-Object System.Drawing.Drawing2D.GraphicsPath
  $p.AddArc($x,$y,$rr,$rr,180,90);$p.AddArc($x+$w-$rr,$y,$rr,$rr,270,90);$p.AddArc($x+$w-$rr,$y+$h-$rr,$rr,$rr,0,90);$p.AddArc($x,$y+$h-$rr,$rr,$rr,90,90);$p.CloseFigure();return $p
}
# rings closer to center, bigger, with elliptical stretch (infinity silhouette)
$rp=New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255,34,211,238),14);$rp.LineJoin='Round'
$g.DrawPath($rp,(New-RoundRectPath 46 138 118 236 18))
$g.DrawPath($rp,(New-RoundRectPath 348 138 118 236 18))
$rp.Dispose()
# crossing energy lines: same aqua as rings + white core -> reads as the same circuit
$cl=New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255,34,211,238),14);$cl.StartCap='Round';$cl.EndCap='Round'
$g.DrawLine($cl,196,215,316,297)
$g.DrawLine($cl,316,215,196,297)
$cl.Dispose()
$cl2=New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(235,224,250,255),4.5);$cl2.StartCap='Round';$cl2.EndCap='Round'
$g.DrawLine($cl2,203,221,309,291)
$g.DrawLine($cl2,309,221,203,291)
$cl2.Dispose()
# vias at connection points
$vb=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255,103,232,249))
$g.FillEllipse($vb,60,247,18,18);$g.FillEllipse($vb,448,247,18,18)
$g.FillEllipse($vb,103,140,14,14);$g.FillEllipse($vb,397,362,14,14)
$vb.Dispose()
$wb=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(235,255,255,255))
$g.FillEllipse($wb,65,252,8,8);$g.FillEllipse($wb,453,252,8,8)
$g.FillEllipse($wb,107,144,6,6);$g.FillEllipse($wb,401,366,6,6)
$wb.Dispose()
# smaller core so crossing shows
$b7=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(242,7,48,63));$g.FillEllipse($b7,204,204,104,104);$b7.Dispose()
$cg=New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(214,214,84,84)),[System.Drawing.Color]::Transparent,[System.Drawing.Color]::Transparent,38)
$cp3=New-Object System.Drawing.Drawing2D.ColorBlend
$cp3.Colors=@([System.Drawing.Color]::FromArgb(255,255,255,255),[System.Drawing.Color]::FromArgb(255,199,247,255),[System.Drawing.Color]::FromArgb(255,34,211,238),[System.Drawing.Color]::FromArgb(255,11,94,117))
$cp3.Positions=@(0.0,0.3,0.7,1.0);$cg.InterpolationColors=$cp3
$p4=New-Object System.Drawing.Drawing2D.GraphicsPath
$p4.AddPolygon([System.Drawing.Point[]]@((Pt 256 212),(Pt 302 256),(Pt 256 300),(Pt 210 256)))
$g.FillPath($cg,$p4)
$b8=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(140,255,255,255))
$g.FillPolygon($b8,[System.Drawing.Point[]]@((Pt 256 230),(Pt 282 256),(Pt 256 282),(Pt 230 256)))
$b8.Dispose()
$bmp.Save("$OutDir\concept-C.png",[System.Drawing.Imaging.ImageFormat]::Png);$g.Dispose();$bmp.Dispose()
Write-Output "C fixed"
