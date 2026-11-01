package com.muthumol.player.dummy;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=25)
public class AppUpdaterTest {
  JSONObject manifest()throws Exception{return new JSONObject().put("versionCode",46).put("versionName","4.5").put("sha256",new String(new char[64]).replace('\0','a')).put("url","https://raw.githubusercontent.com/rinsanak/TelePlay_gpt_patched/tg-stream-updates/apks/TG_stream_V4.5.apk");}
  @Test public void sameOrOlderReleaseNeverTriggersUpgrade(){assertFalse(AppUpdater.newer(45,45));assertFalse(AppUpdater.newer(45,44));assertTrue(AppUpdater.newer(45,46));}
  @Test public void acceptsReleaseOnOurHttpsEndpoint()throws Exception{AppUpdater.validateManifest(manifest());}
  @Test public void rejectsForeignOrCleartextDownloadAndBadHash()throws Exception{
    for(String url:new String[]{"http://raw.githubusercontent.com/rinsanak/TelePlay_gpt_patched/app.apk","https://raw.githubusercontent.com/other/app/main/a.apk","https://example.org/a.apk"}){JSONObject m=manifest().put("url",url);assertThrows(Exception.class,()->AppUpdater.validateManifest(m));}
    JSONObject m=manifest().put("sha256","bad");assertThrows(Exception.class,()->AppUpdater.validateManifest(m));
  }
  @Test public void manualCheckCanRecheckAfterSuccessfulAutomaticCheck()throws Exception{
    android.app.Activity a=Robolectric.buildActivity(android.app.Activity.class).setup().get();final int[] calls={0};
    AppUpdater u=new AppUpdater(a,Runnable::run,()->{calls[0]++;return manifest().put("versionCode",BuildConfig.VERSION_CODE);});
    u.onResume();assertEquals(1,calls[0]);u.checkNow();assertEquals(2,calls[0]);
    android.app.AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertTrue(d.isShowing());assertEquals("You're up to date",org.robolectric.Shadows.shadowOf(d).getTitle());u.close();
  }
  @Test public void failedManualCheckCanRetryAndOfferNewRelease()throws Exception{
    android.app.Activity a=Robolectric.buildActivity(android.app.Activity.class).setup().get();final int[] calls={0};
    AppUpdater u=new AppUpdater(a,Runnable::run,()->{if(calls[0]++==0)throw new IOException("offline");return manifest().put("versionCode",BuildConfig.VERSION_CODE+1).put("versionName","next");});
    u.checkNow();android.app.AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertEquals("Couldn't check for updates",org.robolectric.Shadows.shadowOf(d).getTitle());
    d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper();
    d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(d.isShowing());
    assertEquals("Update available · Vnext",org.robolectric.Shadows.shadowOf(d).getTitle());assertEquals(2,calls[0]);u.close();
  }
  public static class TvActivity extends android.app.Activity {
    final java.util.List<android.content.Intent> tried=new java.util.ArrayList<>();final java.util.Set<String> allowed=new java.util.HashSet<>();
    @Override public void startActivity(android.content.Intent intent){tried.add(intent);String key=intent.getAction()+(intent.getData()==null?"":":data");if(!allowed.contains(key))throw new android.content.ActivityNotFoundException("TV page absent");}
  }
  @Test public void missingAppSpecificPageFallsBackToGlobalPermissionPage(){
    TvActivity a=Robolectric.buildActivity(TvActivity.class).setup().get();a.allowed.add(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
    AppUpdater u=new AppUpdater(a);assertTrue(u.openInstallSettings());assertEquals(2,a.tried.size());assertNotNull(a.tried.get(0).getData());assertNull(a.tried.get(1).getData());u.close();
  }
  @Test public void missingPermissionPagesFallBackToSecurityThenGeneralTvSettings(){
    for(String action:new String[]{android.provider.Settings.ACTION_SECURITY_SETTINGS,android.provider.Settings.ACTION_SETTINGS}){
      TvActivity a=Robolectric.buildActivity(TvActivity.class).setup().get();a.allowed.add(action);AppUpdater u=new AppUpdater(a);
      assertTrue(u.openInstallSettings());assertEquals(action,a.tried.get(a.tried.size()-1).getAction());u.close();
    }
  }
  @Test public void absentTvSettingsDoNotCrashAndInstallerKeepsTemporaryReadGrant(){
    TvActivity a=Robolectric.buildActivity(TvActivity.class).setup().get();AppUpdater u=new AppUpdater(a);assertFalse(u.openInstallSettings());assertEquals(4,a.tried.size());
    a.allowed.add(android.content.Intent.ACTION_INSTALL_PACKAGE+":data");assertTrue(u.launchInstaller(new File(a.getCacheDir(),"updates/update.apk")));
    android.content.Intent install=a.tried.get(a.tried.size()-1);assertEquals(android.content.Intent.ACTION_INSTALL_PACKAGE,install.getAction());
    assertEquals("content",install.getData().getScheme());assertTrue((install.getFlags()&android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);assertEquals(install.getData(),install.getClipData().getItemAt(0).getUri());u.close();
  }
  @Test @Config(sdk=28) public void android8PermissionDialogSurvivesMissingTvPagesAndCanTryInstaller(){
    TvActivity a=Robolectric.buildActivity(TvActivity.class).setup().get();AppUpdater u=new AppUpdater(a);assertFalse(u.canInstall());
    File f=new File(a.getCacheDir(),"updates/update.apk");u.install(f);
    android.app.AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertEquals("Allow app updates",org.robolectric.Shadows.shadowOf(d).getTitle());
    d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper();
    d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertEquals("TV permission settings unavailable",org.robolectric.Shadows.shadowOf(d).getTitle());
    assertEquals(f,org.robolectric.util.ReflectionHelpers.getField(u,"pendingInstall"));a.allowed.add(android.content.Intent.ACTION_VIEW+":data");
    d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper();assertEquals(android.content.Intent.ACTION_VIEW,a.tried.get(a.tried.size()-1).getAction());u.close();
  }
  @Test public void cachedDownloadIsReusedOnlyWhenHashMatches()throws Exception{
    File f=File.createTempFile("update-cache",".apk");try(FileOutputStream out=new FileOutputStream(f)){out.write(new byte[]{1,2,3});}
    String sha=AppUpdater.hex(java.security.MessageDigest.getInstance("SHA-256").digest(new byte[]{1,2,3}));assertTrue(AppUpdater.cachedApkMatches(f,sha));
    try(FileOutputStream out=new FileOutputStream(f)){out.write(new byte[]{4,5,6});}assertFalse(AppUpdater.cachedApkMatches(f,sha));f.delete();
  }
  @Test public void installerCanOnlyReadTheUpdateApk()throws Exception{
    UpdateProvider p=Robolectric.buildContentProvider(UpdateProvider.class).create().get();
    File f=new File(p.getContext().getCacheDir(),"updates/update.apk");f.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(f)){out.write(new byte[]{1,2,3});}
    Uri ok=Uri.parse("content://com.muthumol.player.dummy.updates/update.apk");try(ParcelFileDescriptor fd=p.openFile(ok,"r")){assertEquals(3,fd.getStatSize());}
    assertThrows(FileNotFoundException.class,()->p.openFile(ok,"w"));
    assertThrows(FileNotFoundException.class,()->p.openFile(Uri.parse("content://com.muthumol.player.dummy.updates/../shared_prefs/player.xml"),"r"));
  }
 @Test public void blockedRawFeedUsesVerifiedGithubApiFallback()throws Exception{JSONObject expected=manifest();java.util.List<String> urls=new java.util.ArrayList<>();JSONObject result=AppUpdater.loadRelease(url->{urls.add(url);if(url.contains("raw.githubusercontent"))throw new IOException("blocked");return new JSONObject().put("content",android.util.Base64.encodeToString(expected.toString().getBytes("UTF-8"),android.util.Base64.NO_WRAP));},123);assertEquals(expected.getInt("versionCode"),result.getInt("versionCode"));assertEquals(2,urls.size());assertTrue(urls.get(1).contains("api.github.com/repos/rinsanak/TelePlay_gpt_patched/contents/latest.json"));}
 @Test public void fallbackCannotBypassManifestVerification()throws Exception{JSONObject bad=manifest().put("url","https://example.org/foreign.apk");assertThrows(Exception.class,()->AppUpdater.loadRelease(url->{if(url.contains("raw.githubusercontent"))throw new IOException();return new JSONObject().put("content",android.util.Base64.encodeToString(bad.toString().getBytes("UTF-8"),android.util.Base64.NO_WRAP));},123));}

}
