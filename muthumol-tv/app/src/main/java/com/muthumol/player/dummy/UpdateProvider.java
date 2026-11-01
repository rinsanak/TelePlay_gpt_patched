package com.muthumol.player.dummy;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Only grants temporary read access to the verified APK, never other app files. */
public final class UpdateProvider extends ContentProvider {
  public boolean onCreate(){return true;}
  private File file(Uri uri)throws FileNotFoundException{
    if(!"/update.apk".equals(uri.getPath()))throw new FileNotFoundException();
    return new File(getContext().getCacheDir(),"updates/update.apk");
  }
  public String getType(Uri uri){return "application/vnd.android.package-archive";}
  public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
  public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
    try{File f=file(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]="TG_stream_update.apk";else if(OpenableColumns.SIZE.equals(cols[i]))row[i]=f.length();}c.addRow(row);return c;}catch(FileNotFoundException e){return null;}
  }
  public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
  public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
  public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
