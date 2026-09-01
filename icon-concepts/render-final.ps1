
Add-Type -AssemblyName System.Drawing
$OutDir='E:\改着玩\AE2-Infinite-Expansion\icon-concepts'
$src=[System.Drawing.Image]::FromFile("$OutDir\concept-E5.png")
# 512/256/128/64/32/16 (integer ratios -> NearestNeighbor)
foreach($s in @(512,256,128,64,32,16)){
  $b=[System.Drawing.Bitmap]::new($s,$s)
  $g=[System.Drawing.Graphics]::FromImage($b)
  $g.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
  $g.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half
  $g.DrawImage($src,0,0,$s,$s);$g.Dispose()
  $b.Save("$OutDir\final-$s.png",[System.Drawing.Imaging.ImageFormat]::Png);$b.Dispose()
}
$src.Dispose()
# ICO: 16/32/48/256 PNG entries
$sizes=@(16,32,48,256)
$blobs=@()
foreach($s in $sizes){
  $img=[System.Drawing.Image]::FromFile("$OutDir\final-$s.png")
  $ms=[System.IO.MemoryStream]::new()
  $img.Save($ms,[System.Drawing.Imaging.ImageFormat]::Png)
  $blobs += ,$ms.ToArray()
  $ms.Dispose();$img.Dispose()
}
$ico=[System.IO.File]::Create("$OutDir\final.ico")
$bw=[System.IO.BinaryWriter]::new($ico)
$bw.Write([uint16]0);$bw.Write([uint16]1);$bw.Write([uint16]$sizes.Count)
$offset=6 + 16*$sizes.Count
for($i=0;$i -lt $sizes.Count;$i++){
  $s=$sizes[$i];$b=$blobs[$i]
  $bw.Write([byte]($s -eq 256 ? 0 : $s));$bw.Write([byte]($s -eq 256 ? 0 : $s))
  $bw.Write([byte]0);$bw.Write([byte]0)
  $bw.Write([uint16]1);$bw.Write([uint16]32)
  $bw.Write([uint32]$b.Length);$bw.Write([uint32]$offset)
  $offset += $b.Length
}
foreach($b in $blobs){ $bw.Write($b) }
$bw.Dispose();$ico.Dispose()
# logo.png (256) 与 mod_icon_final.png (512) 副本
Copy-Item "$OutDir\final-256.png" "$OutDir\logo.png" -Force
Copy-Item "$OutDir\final-512.png" "$OutDir\mod_icon_final.png" -Force
Write-Output "assets done"
