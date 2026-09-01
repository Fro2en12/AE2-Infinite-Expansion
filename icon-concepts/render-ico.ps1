
Add-Type -AssemblyName System.Drawing
$OutDir='E:\改着玩\AE2-Infinite-Expansion\icon-concepts'
$sizes=@(16,32,48,256)
$blobs=@()
foreach($s in $sizes){
  $fn = Join-Path $OutDir ("final-" + $s + ".png")
  $img=[System.Drawing.Image]::FromFile($fn)
  $ms=[System.IO.MemoryStream]::new()
  $img.Save($ms,[System.Drawing.Imaging.ImageFormat]::Png)
  $blobs += ,$ms.ToArray()
  $ms.Dispose();$img.Dispose()
}
$icoPath=Join-Path $OutDir 'final.ico'
$ico=[System.IO.File]::Create($icoPath)
$bw=[System.IO.BinaryWriter]::new($ico)
$bw.Write([uint16]0);$bw.Write([uint16]1);$bw.Write([uint16]$sizes.Count)
$offset=6 + 16*$sizes.Count
for($i=0;$i -lt $sizes.Count;$i++){
  $s=$sizes[$i];$b=$blobs[$i]
  $dim = if($s -eq 256){[byte]0}else{[byte]$s}
  $bw.Write($dim);$bw.Write($dim)
  $bw.Write([byte]0);$bw.Write([byte]0)
  $bw.Write([uint16]1);$bw.Write([uint16]32)
  $bw.Write([uint32]$b.Length);$bw.Write([uint32]$offset)
  $offset += $b.Length
}
foreach($b in $blobs){ $bw.Write($b) }
$bw.Dispose();$ico.Dispose()
Write-Output ("ICO bytes: " + (Get-Item $icoPath).Length)
