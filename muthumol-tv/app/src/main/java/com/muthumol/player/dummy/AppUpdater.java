package com.muthumol.player.dummy;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Foreground-only update check; no account data is sent to the release endpoint. */
final class AppUpdater {
  static final String FEED="https://raw.githubusercontent.com/rinsanak/TelePlay_gpt_patched/tg-stream-updates/latest.json";
  private final Activity activity;
  private boolean checked, busy, checking;
  private long lastAttempt;
  interface ReleaseSource { JSONObject load() throws Exception; }
  private final java.util.concurrent.Executor checkExecutor;
  private final ReleaseSource releaseSource;
  private volatile boolean closed;
  private File pendingInstall;
  private boolean returningFromSettings;
  private ProgressDialog progress;
  private AlertDialog offer;
  AppUpdater(Activity a){this(a,command->new Thread(command,"update-check").start(),null);}
  AppUpdater(Activity a,java.util.concurrent.Executor executor,ReleaseSource source){activity=a;checkExecutor=executor;releaseSource=source==null?this::readManifest:source;}
  static boolean newer(int installed,int available){return available>installed;}
  static void validateManifest(JSONObject m) throws Exception {
    if(m.getInt("versionCode")<=0||m.getString("versionName").length()>40||!m.getString("sha256").matches("[a-fA-F0-9]{64}"))throw new IOException("Invalid update information");
    URL u=new URL(m.getString("url"));
    if(!"https".equals(u.getProtocol())||!"raw.githubusercontent.com".equals(u.getHost())||!u.getPath().startsWith("/rinsanak/TelePlay_gpt_patched/"))throw new IOException("Invalid update location");
  }
  void onResume(){
    if(closed)return;
    if(pendingInstall!=null){
      File f=pendingInstall;
      if(canInstall()){pendingInstall=null;returningFromSettings=false;launchInstaller(f);}
      else if(returningFromSettings){returningFromSettings=false;showInstallPermission(f);}
      return;
    }
    if(checked||checking||busy||(lastAttempt!=0&&android.os.SystemClock.elapsedRealtime()-lastAttempt<30000))return;
    check(false);
  }
  void checkNow(){check(true);}
  private void check(boolean manual){
    if(closed)return;
    if(checking||busy){if(manual)Toast.makeText(activity,"An update check or download is already in progress",Toast.LENGTH_SHORT).show();return;}
    checking=true;lastAttempt=android.os.SystemClock.elapsedRealtime();
    if(manual)Toast.makeText(activity,"Checking for updates…",Toast.LENGTH_SHORT).show();
    BetaLog.event("update_check_start","installedVersion="+BuildConfig.VERSION_CODE+" manual="+manual);
    checkExecutor.execute(()->{
      try{
        JSONObject m=releaseSource.load();validateManifest(m);int available=m.getInt("versionCode");
        activity.runOnUiThread(()->{
          checking=false;if(closed||activity.isFinishing()||activity.isDestroyed())return;checked=true;
          BetaLog.event("update_check_result","installedVersion="+BuildConfig.VERSION_CODE+" availableVersion="+available);
          if(newer(BuildConfig.VERSION_CODE,available))show(m);
          else if(manual)offer=Ui.dialog(activity).setTitle("You're up to date").setMessage("Installed: V"+BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")\nServer: V"+m.optString("versionName")+" ("+available+")\nNo newer update is available.").setPositiveButton("OK",null).show();
        });
      }catch(Exception e){
        BetaLog.event("update_check_failed","type="+e.getClass().getSimpleName());
        activity.runOnUiThread(()->{checking=false;checked=false;if(manual&&!closed&&!activity.isFinishing()&&!activity.isDestroyed())offer=Ui.dialog(activity).setTitle("Couldn't check for updates").setMessage("The update server could not be reached or its response could not be verified. Check your connection and try again.").setPositiveButton("Retry",(d,w)->checkNow()).setNegativeButton("Cancel",null).show();});
      }
    });
  }
  interface FeedTransport{JSONObject read(String url)throws Exception;}
  static final String FALLBACK_FEED="https://api.github.com/repos/rinsanak/TelePlay_gpt_patched/contents/latest.json?ref=tg-stream-updates";
  static JSONObject loadRelease(FeedTransport reader,long timestamp)throws Exception{
    try{JSONObject direct=reader.read(FEED+"?t="+timestamp);validateManifest(direct);return direct;}catch(Exception primary){
      JSONObject envelope=reader.read(FALLBACK_FEED+"&t="+timestamp);String encoded=envelope.getString("content");if(encoded.length()>48000)throw new IOException("Update information too large");JSONObject m=new JSONObject(new String(android.util.Base64.decode(encoded,android.util.Base64.DEFAULT),"UTF-8"));validateManifest(m);BetaLog.event("update_feed_fallback","verified=true");return m;
    }
  }
  private JSONObject readManifest()throws Exception{return loadRelease(this::readJson,System.currentTimeMillis());}
  private JSONObject readJson(String url)throws Exception{HttpURLConnection c=connect(url);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new IOException("Update information too large");out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));}finally{c.disconnect();}}
  private void show(JSONObject m){
    if(closed||activity.isFinishing()||activity.isDestroyed())return;
    offer=new AlertDialog.Builder(activity).setTitle("Update available · V"+m.optString("versionName"))
      .setMessage(m.optString("notes","A new TG stream update is ready.")+"\n\nYour login and settings will be kept.")
      .setPositiveButton("Update now",(d,w)->download(m)).setNegativeButton("Cancel",null).create();offer.show();
  }
  private HttpURLConnection connect(String url)throws Exception{
    HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(url.contains("latest.json")?6000:15000);c.setReadTimeout(url.contains("latest.json")?8000:20000);c.setUseCaches(false);c.setRequestProperty("Cache-Control","no-cache, no-store");c.setRequestProperty("User-Agent","TGstream/"+BuildConfig.VERSION_NAME);
    if(c.getResponseCode()!=200){c.disconnect();throw new IOException("Download unavailable");}return c;
  }
  private void download(JSONObject m){
    if(busy||closed)return;busy=true;
    progress=new ProgressDialog(activity);progress.setTitle("Downloading update");progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);progress.setMax(100);progress.setCancelable(false);progress.show();
    new Thread(()->{
      File target=new File(activity.getCacheDir(),"updates/update.apk"),part=new File(target.getParentFile(),"update.part");
      try{
        if(cachedApkMatches(target,m.getString("sha256"))){
          validateApk(target,m.getInt("versionCode"));BetaLog.event("update_cached_apk","verified=true");
          activity.runOnUiThread(()->{busy=false;if(!closed){progress.dismiss();install(target);}});return;
        }
        if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("Cannot create download folder");
        HttpURLConnection c=connect(m.getString("url"));long total=c.getContentLengthLong(),received=0;int last=-1;MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(part)){
          byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(closed)throw new IOException("Download cancelled");received+=n;if(received>100L*1024*1024)throw new IOException("Update too large");out.write(b,0,n);digest.update(b,0,n);
            int percent=total>0?(int)Math.min(99,received*100/total):-1;if(percent!=last){last=percent;final int value=percent;activity.runOnUiThread(()->{if(!closed){progress.setIndeterminate(value<0);if(value>=0)progress.setProgress(value);}});}
          }
        }finally{c.disconnect();}
        if(!hex(digest.digest()).equalsIgnoreCase(m.getString("sha256")))throw new IOException("Update verification failed. Please retry.");
        validateApk(part,m.getInt("versionCode"));
        if(target.exists()&&!target.delete())throw new IOException("Cannot replace previous update");if(!part.renameTo(target))throw new IOException("Cannot finish update download");
        activity.runOnUiThread(()->{busy=false;if(!closed){progress.dismiss();install(target);}});
      }catch(Exception e){part.delete();activity.runOnUiThread(()->{busy=false;if(!closed){progress.dismiss();new AlertDialog.Builder(activity).setTitle("Update not installed").setMessage("Download or verification failed. Check your connection and free storage, then try again.").setPositiveButton("Retry",(d,w)->download(m)).setNegativeButton("Cancel",null).show();}});BetaLog.event("update_download_failed","type="+e.getClass().getSimpleName());}
    },"update-download").start();
  }
  @SuppressWarnings("deprecation") private void validateApk(File f,int expected)throws Exception{
    PackageManager pm=activity.getPackageManager();PackageInfo incoming=pm.getPackageArchiveInfo(f.getPath(),PackageManager.GET_SIGNATURES),current=pm.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNATURES);
    if(incoming==null||!activity.getPackageName().equals(incoming.packageName)||incoming.versionCode!=expected||!newer(current.versionCode,incoming.versionCode)||incoming.signatures==null||current.signatures==null||incoming.signatures.length!=1||current.signatures.length!=1||!incoming.signatures[0].equals(current.signatures[0]))throw new IOException("Update identity verification failed");
  }
  boolean canInstall(){
    if(Build.VERSION.SDK_INT<26)return true;
    try{return activity.getPackageManager().canRequestPackageInstalls();}
    catch(RuntimeException e){BetaLog.event("update_permission_check_failed","type="+e.getClass().getSimpleName());return false;}
  }
  void install(File f){
    if(closed)return;
    if(!canInstall()){pendingInstall=f;showInstallPermission(f);return;}
    pendingInstall=null;launchInstaller(f);
  }
  private void showInstallPermission(File f){
    if(closed||activity.isFinishing()||activity.isDestroyed())return;
    offer=Ui.dialog(activity).setTitle("Allow app updates")
      .setMessage("On your TV, enable TG stream in Install unknown apps (sometimes under Security & restrictions or Special app access), then return here. The downloaded APK is kept.\n\nIf your TV has no permission page, Try installer opens Android's installer so it can show its own permission controls.")
      .setPositiveButton("Open TV settings",(d,w)->{if(!openInstallSettings())showMissingSettings(f);})
      .setNeutralButton("Try installer",(d,w)->launchInstaller(f))
      .setNegativeButton("Cancel",(d,w)->{pendingInstall=null;returningFromSettings=false;}).show();
  }
  boolean openInstallSettings(){
    Intent[] choices={new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())),new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),new Intent(Settings.ACTION_SECURITY_SETTINGS),new Intent(Settings.ACTION_SETTINGS)};
    for(int i=0;i<choices.length;i++)if(tryLaunch(choices[i],"settings",i)){returningFromSettings=true;return true;}
    return false;
  }
  private void showMissingSettings(File f){
    offer=Ui.dialog(activity).setTitle("TV permission settings unavailable")
      .setMessage("This TV could not open its installation permission settings. Open TV Settings manually and look for Install unknown apps / Unknown sources. You can also open this APK using the file manager that installed TG stream originally. The downloaded APK is kept.")
      .setPositiveButton("Try installer",(d,w)->launchInstaller(f))
      .setNegativeButton("Close",null).show();
  }
  private boolean tryLaunch(Intent intent,String stage,int candidate){
    try{activity.startActivity(intent);BetaLog.event("update_intent_opened","stage="+stage+" candidate="+candidate);return true;}
    catch(ActivityNotFoundException|SecurityException e){BetaLog.event("update_intent_unavailable","stage="+stage+" candidate="+candidate+" type="+e.getClass().getSimpleName());return false;}
  }
  boolean launchInstaller(File f){
    if(closed)return false;
    Uri uri=Uri.parse("content://"+activity.getPackageName()+".updates/update.apk");
    for(String action:new String[]{Intent.ACTION_VIEW,Intent.ACTION_INSTALL_PACKAGE}){
      Intent intent=new Intent(action).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      intent.setClipData(ClipData.newRawUri("TG stream update",uri));
      if(tryLaunch(intent,"installer",Intent.ACTION_VIEW.equals(action)?0:1))return true;
    }
    offer=Ui.dialog(activity).setTitle("Android installer unavailable").setMessage("The TV's Android package installer may be missing or disabled. Try opening the APK from your TV file manager, or check the TV's app settings.")
      .setPositiveButton("TV settings",(d,w)->openInstallSettings()).setNegativeButton("Close",null).show();return false;
  }
  static boolean cachedApkMatches(File f,String expected)throws Exception{
    if(!f.isFile()||f.length()==0||f.length()>100L*1024*1024)return false;
    MessageDigest digest=MessageDigest.getInstance("SHA-256");
    try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
    return hex(digest.digest()).equalsIgnoreCase(expected);
  }
  static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
  void close(){closed=true;if(progress!=null)progress.dismiss();if(offer!=null)offer.dismiss();}
}
