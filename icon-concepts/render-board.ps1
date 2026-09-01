
Add-Type -AssemblyName System.Drawing
$OutDir = 'E:\改着玩\AE2-Infinite-Expansion\icon-concepts'
$board = New-Object System.Drawing.Bitmap(1700,1240)
$g = [System.Drawing.Graphics]::FromImage($board)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::FromArgb(255,13,20,36))
$font = New-Object System.Drawing.Font('Segoe UI',22,[System.Drawing.FontStyle]::Bold)
$fontS = New-Object System.Drawing.Font('Segoe UI',14)
$white = [System.Drawing.Brushes]::White
$labels = @(@('A · Energy Infinity',6),@('B · Gravitational Lensing',10),@('C · Circuit Infinity',10))
$xs = @(30, 590, 1126)
for ($i=0;$i -lt 3;$i++) {
  $n = @('A','B','C')[$i]
  $img = [System.Drawing.Image]::FromFile("$OutDir\concept-$n.png")
  $g.DrawImage($img,$xs[$i],30,468,468); $img.Dispose()
  $g.DrawString($labels[$i][0],$font,$white,$xs[$i],512)
  # 16px check
  $c16 = [System.Drawing.Image]::FromFile("$OutDir\check-$n-16to128.png")
  $g.DrawImage($c16,$xs[$i]+170,570,128,128); $c16.Dispose()
  $g.DrawString('16px (8x check)',$fontS,$white,$xs[$i]+170,706)
}
$bmp2 = New-Object System.Drawing.Bitmap(1700,152)
$g2 = [System.Drawing.Graphics]::FromImage($bmp2)
$g2.Clear([System.Drawing.Color]::FromArgb(255,13,20,36))
$bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle(0,0,1700,152)),[System.Drawing.Color]::FromArgb(255,17,28,66),[System.Drawing.Color]::FromArgb(255,5,8,18),90)
$g2.FillRectangle($bg,0,0,1700,152)
$g.DrawImage($bmp2,0,0)
$g.DrawString('AE2 Addon — Infinite Expansion · icon directions',(New-Object System.Drawing.Font('Segoe UI',18,[System.Drawing.FontStyle]::Bold)),$white,26,26)
$board.Save("$OutDir\board-3x.png",[System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose();$board.Dispose()
Write-Output "board ok"
