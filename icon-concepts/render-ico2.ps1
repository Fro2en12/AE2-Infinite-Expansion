
Add-Type -AssemblyName System.Drawing
$OutDir='E:\改着玩\AE2-Infinite-Expansion\icon-concepts'
# 48px 从 64px 缩 (bicubic)
$b64=[System.Drawing.Image]::FromFile((Join-Path $OutDir 'final-64.png'))
$b48=[System.Drawing.Bitmap]::new(48,48)
$g48=[System.Drawing.Graphics]::FromImage($b48);$g48.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g48.DrawImage($b64,0,0,48,48);$g48.Dispose()
$b48.Save((Join-Path $OutDir 'final-48.png'),[System.Drawing.Imaging.ImageFormat]::Png);$b48.Dispose();$b64.Dispose()

$sizes=@(16,32,48,256)
$blobs=@()
foreach($s in $sizes){
  $img=[System.Drawing.Image]::FromFile((Join-Path $OutDir ("final-" + $s + ".png")))
  $ms=[System.IO.MemoryStream]::new()
  $img.Save($ms,[System.Drawing.Imaging.ImageFormat]::Png)
  $blobs += ,$ms.ToArray()
  $ms.Dispose();$img.Dispose()
}
$ico=[System.IO.File]::Create((Join-Path $OutDir 'final.ico'))
$bw=[System.IO.BinaryWriter]::new($ico)
$bw.Write([uint16]0);$bw.Write([uint16]1);$bw.Write([uint16]$sizes.Count)
$offset=6+16*$sizes.Count
for($i=0;$i -lt $sizes.Count;$i++){
  $s=$sizes[$i];$b=$blobs[$i]
  $dim=if($s -eq 256){[byte]0}else{[byte]$s}
  $bw.Write($dim);$bw.Write($dim);$bw.Write([byte]0);$bw.Write([byte]0)
  $bw.Write([uint16]1);$bw.Write([uint16]32)
  $bw.Write([uint32]$b.Length);$bw.Write([uint32]$offset)
  $offset += $b.Length
}
foreach($b in $blobs){$bw.Write($b)}
$bw.Dispose();$ico.Dispose()
$bytes=[System.IO.File]::ReadAllBytes((Join-Path $OutDir 'final.ico'))
$cnt=[BitConverter]::ToUInt16($bytes,4)
$info=@()
for($i=0;$i -lt $cnt;$i++){
  $o=6+$i*16
  $info += ("e"+$i+" "+$(if($bytes[$o] -eq 0){'256'}else{$bytes[$o]})+"x"+$(if($bytes[$o+1] -eq 0){'256'}else{$bytes[$o+1]})+" size="+[BitConverter]::ToUInt32($bytes,$o+8))
}
Write-Output ("ICO OK: " + ($info -join ' | ') + " total=" + $bytes.Length)
