package com.muthumol.player.dummy;
import java.io.*;
final class SmallFileIO {
 static byte[] read(File f,int limit)throws IOException {try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("File too large");out.write(buffer,0,n);}return out.toByteArray();}}
 static void write(File f,byte[] data)throws IOException {try(FileOutputStream out=new FileOutputStream(f)){out.write(data);}}
 static void copySubtitle(File source,File target)throws IOException {if(source.length()>4L*1024*1024)throw new IOException("Subtitle too large");try(BufferedInputStream in=new BufferedInputStream(new FileInputStream(source));FileOutputStream out=new FileOutputStream(target)){in.mark(4);int a=in.read(),b=in.read();in.reset();if(a==255&&b==254||a==254&&b==255){Reader reader=new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_16);Writer writer=new OutputStreamWriter(out,java.nio.charset.StandardCharsets.UTF_8);char[] text=new char[4096];int n;while((n=reader.read(text))!=-1)writer.write(text,0,n);writer.flush();}else{byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}}}
}
