package com.muthumol.player.dummy;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.os.ParcelFileDescriptor;import java.io.*;
public final class OfflineProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 public String getType(Uri uri){return "video/*";}
 public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {if(!"r".equals(mode))throw new FileNotFoundException("Read only");String name=uri.getLastPathSegment();if(name==null||!name.matches("[0-9a-f]{64}\\.video"))throw new FileNotFoundException("Unknown video");File f=new File(new File(getContext().getFilesDir(),"offline-videos"),name);return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);}
 public Cursor query(Uri u,String[] p,String q,String[] a,String sort){return null;}public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String q,String[] a){return 0;}public int delete(Uri u,String q,String[] a){return 0;}
}
