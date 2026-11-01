package com.muthumol.player.dummy;

import android.content.*;
import android.os.*;
import android.util.Base64;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.drinkless.tdlib.*;

class TelegramBridge {
  interface Result {
    void done(TdApi.Object value);
  }

  interface Listener {
    void authChanged(String state);

    void error(String message);
  }

  private static TelegramBridge instance;

  static synchronized TelegramBridge getInstance(Context c) {
    if (instance == null) instance = new TelegramBridge(c.getApplicationContext());
    return instance;
  }

  final Context context;
  final SecretStore secrets;
  final Handler ui = new Handler(Looper.getMainLooper());
  final ConcurrentMap<Long, TdApi.Object> chats = new ConcurrentHashMap<>();
  final ConcurrentMap<Integer, TdApi.Object> files = new ConcurrentHashMap<>();
  final Object fileSignal=new Object();
  long fileRevision;
  volatile String connectionState="Unknown";
  final ConcurrentMap<Integer,StreamStats> streamObservers=new ConcurrentHashMap<>();
  Client client;
  volatile Listener listener;
  volatile String qrLink="";
  volatile String state = "Starting";
  volatile boolean ready;
  volatile String fatal = "";

  private TelegramBridge(Context c) {this(c,true);}
  TelegramBridge(Context c,boolean autoStart) {
    context=c;secrets=new SecretStore(c);if(autoStart)start();
  }

  void start() {
    try {
      System.loadLibrary("tdjni");
      Client.execute((TdApi.Function) obj("SetLogVerbosityLevel", "newVerbosityLevel", 0));
      client = Client.create(this::update, null, null);
    } catch (Throwable e) {
      fatal =
          "Telegram library cannot start on this device: "
              + e.getClass().getSimpleName()
              + " — "
              + e.getMessage();
      state = "Unavailable";
      notifyState();
    }
  }

  static Object get(Object v, String field) {
    if (v == null) return null;
    try {
      return v.getClass().getField(field).get(v);
    } catch (Exception e) {
      return null;
    }
  }

  static String str(Object v, String field) {
    Object x = get(v, field);
    return x == null ? "" : String.valueOf(x);
  }

  static long num(Object v, String field) {
    Object x = get(v, field);
    return x instanceof Number ? ((Number) x).longValue() : 0;
  }

  static boolean bool(Object v, String field) {
    return Boolean.TRUE.equals(get(v, field));
  }

  static String type(Object v) {
    return v == null ? "" : v.getClass().getSimpleName();
  }

  static TdApi.Object obj(String name, Object... kv) throws Exception {
    TdApi.Object v =
        (TdApi.Object)
            Class.forName("org.drinkless.tdlib.TdApi$" + name)
                .getDeclaredConstructor()
                .newInstance();
    for (int i = 0; i < kv.length; i += 2) {
      Field f = v.getClass().getField((String) kv[i]);
      Object x = kv[i + 1];
      if (x instanceof Number) {
        if (f.getType() == int.class) x = ((Number) x).intValue();
        else if (f.getType() == long.class) x = ((Number) x).longValue();
        else if (f.getType() == double.class) x = ((Number) x).doubleValue();
      }
      f.set(v, x);
    }
    return v;
  }

  void send(String name, Result cb, Object... fields) {
    try {
      if (client == null) throw new IllegalStateException(fatal);
      client.send(
          (TdApi.Function) obj(name, fields),
          v -> {
            if (v instanceof TdApi.Error && cb == null) error(str(v, "message"));
            if (cb != null) cb.done(v);
          });
    } catch (Exception e) {
      TdApi.Error v = new TdApi.Error(500, name + ": " + e.getMessage());
      if (cb != null) cb.done(v);
      else error(v.message);
    }
  }

  static String errorKind(Object error){String text=str(error,"message");String[] kinds={"FILE_REFERENCE_EXPIRED","FILE_REFERENCE_INVALID","FILE_MIGRATE_","FLOOD_WAIT_","TIMEOUT","NETWORK","CONNECTION","FILE_DOWNLOAD"};for(String k:kinds)if(text.contains(k))return k;return "OTHER";}

  void error(String s) {
    ui.post(
        () -> {
          if (listener != null) listener.error(s);
        });
  }

  void notifyState() {
    ui.post(
        () -> {
          if (listener != null) listener.authChanged(state);
        });
  }

  void update(TdApi.Object o) {
    switch (type(o)) {
      case "UpdateAuthorizationState":
        Object a = get(o, "authorizationState");
        state = type(a);qrLink=str(a,"link");
        ready = state.equals("AuthorizationStateReady");
        if (state.equals("AuthorizationStateWaitTdlibParameters")
            && configured()) parameters();
        notifyState();
        if (state.equals("AuthorizationStateClosed")) ui.post(this::start);
        break;
      case "UpdateNewChat":
        TdApi.Object chat = (TdApi.Object) get(o, "chat");
        chats.put(num(chat, "id"), chat);
        break;
      case "UpdateChatTitle":
        TdApi.Object old = chats.get(num(o, "chatId"));
        if (old != null)
          try {
            old.getClass().getField("title").set(old, str(o, "title"));
          } catch (Exception e) {
          }
        break;
      case "UpdateMessageSendFailed":
        Object content=get(get(o,"message"),"content");Object document=get(content,"document");
        if(str(document,"fileName").startsWith("beta-stream-"))ui.post(()->android.widget.Toast.makeText(context,"Log upload failed. Reconnect Telegram and press Log Stop again.",android.widget.Toast.LENGTH_LONG).show());
        break;
      case "UpdateConnectionState":
        connectionState=type(get(o,"state"));BetaLog.event("connection",connectionState);break;
      case "UpdateFile":
        TdApi.Object file = (TdApi.Object) get(o, "file");
        int fileId=(int)num(file,"id");publishFile(fileId, file);
        StreamStats observer=streamObservers.get(fileId);
        if(observer!=null)observer.fileProgress(fileId,num(get(file,"local"),"downloadedSize"),bool(get(file,"local"),"isDownloadingActive"));
        break;
    }
  }

  void configure(String apiId, String hash) throws Exception {
    if (!apiId.matches("[0-9]+") || !hash.matches("[a-fA-F0-9]{32}"))
      throw new IllegalArgumentException("Enter your Telegram API ID and 32-character API hash.");
    secrets.put("api_id", apiId);
    secrets.put("api_hash", hash);
    if (state.equals("AuthorizationStateWaitTdlibParameters")) parameters();
  }

  void parameters() {
    try {
      String key = secrets.get("db_key");
      if (key.isEmpty()) {
        byte[] b = new byte[32];
        new java.security.SecureRandom().nextBytes(b);
        key = Base64.encodeToString(b, 2);
        secrets.put("db_key", key);
      }
      send(
          "SetTdlibParameters",
          null,
          "useTestDc",
          false,
          "databaseDirectory",
          new java.io.File(context.getFilesDir(), "telegram-db").getAbsolutePath(),
          "filesDirectory",
          new java.io.File(context.getCacheDir(), "telegram-media").getAbsolutePath(),
          "databaseEncryptionKey",
          Base64.decode(key, 2),
          "useFileDatabase",
          true,
          "useChatInfoDatabase",
          true,
          "useMessageDatabase",
          true,
          "useSecretChats",
          false,
          "apiId",
          Integer.parseInt(apiId()),
          "apiHash",
          apiHash(),
          "systemLanguageCode",
          "en",
          "deviceModel",
          "Muthumol TV",
          "systemVersion",
          Build.VERSION.RELEASE,
          "applicationVersion",
          BuildConfig.VERSION_NAME);
    } catch (Exception e) {
      error("Telegram configuration: " + e.getMessage());
    }
  }

  String apiId(){return secrets.get("api_id").isEmpty()?Integer.toString(BuildConfig.TG_API_ID):secrets.get("api_id");}
  String apiHash(){return secrets.get("api_hash").isEmpty()?BuildConfig.TG_API_HASH:secrets.get("api_hash");}
  boolean configured() {try{return Integer.parseInt(apiId())>0&&apiHash().matches("[a-fA-F0-9]{32}");}catch(NumberFormatException e){return false;}}


  void login(String value) {
    switch (state) {
      case "AuthorizationStateWaitPhoneNumber":
        send("SetAuthenticationPhoneNumber", null, "phoneNumber", value, "settings", null);
        break;
      case "AuthorizationStateWaitCode":
        send("CheckAuthenticationCode", null, "code", value);
        break;
      case "AuthorizationStateWaitPassword":
        send("CheckAuthenticationPassword", null, "password", value);
        break;
      case "AuthorizationStateWaitEmailAddress":
        send("SetAuthenticationEmailAddress", null, "emailAddress", value);
        break;
      case "AuthorizationStateWaitEmailCode":
        try {
          send(
              "CheckAuthenticationEmailCode",
              null,
              "code",
              obj("EmailAddressAuthenticationCode", "code", value));
        } catch (Exception e) {
          error(e.getMessage());
        }
        break;
      default:
        error("Waiting for Telegram: " + state);
    }
  }

  void download(int id, long offset, long limit) {download(id,offset,limit,32);}
  void download(int id, long offset, long limit,int priority) {
    BetaLog.event("download_request","file="+id+" offset="+offset+" limit="+limit+" priority="+priority);
    send(
        "DownloadFile",
        v -> {
          if (!(v instanceof TdApi.Error)) {
            publishFile(id,v);StreamStats observer=streamObservers.get(id);
            if(observer!=null)observer.fileProgress(id,num(get(v,"local"),"downloadedSize"),bool(get(v,"local"),"isDownloadingActive"));
          }
          else {BetaLog.event("download_error","file="+id+" code="+num(v,"code")+" kind="+errorKind(v));error(str(v, "message"));}
        },
        "fileId",
        id,
        "priority",
        priority,
        "offset",
        offset,
        "limit",
        limit,
        "synchronous",
        false);
  }

  TdApi.Object waitRequest(String name, Object... args) throws java.io.IOException {
    long started=SystemClock.elapsedRealtime();
    CountDownLatch l = new CountDownLatch(1);
    TdApi.Object[] result = new TdApi.Object[1];
    send(
        name,
        v -> {
          result[0] = v;
          l.countDown();
        },
        args);
    try {
      if (!l.await(8, TimeUnit.SECONDS)){BetaLog.event("td_timeout","method="+name);
        throw new java.io.IOException("Telegram request timed out");}
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new java.io.IOException(e);
    }
    if(BetaLog.active()&&SystemClock.elapsedRealtime()-started>250)BetaLog.event("td_wait","method="+name+" ms="+(SystemClock.elapsedRealtime()-started));
    if (result[0] instanceof TdApi.Error) {BetaLog.event("td_error","method="+name+" code="+num(result[0],"code")+" kind="+errorKind(result[0]));}
    if (result[0] instanceof TdApi.Error) throw new java.io.IOException(str(result[0], "message"));
    return result[0];
  }

  void cancel(int id) {
    BetaLog.event("download_cancel","file="+id);
    send("CancelDownloadFile", v -> {}, "fileId", id, "onlyIfPending", false);
  }

  void publishFile(int id,TdApi.Object file) {
    files.put(id,file);synchronized(fileSignal){fileRevision++;fileSignal.notifyAll();}
  }
  long revision(){synchronized(fileSignal){return fileRevision;}}
  void awaitFileChange(long revision,long millis)throws java.io.IOException {
    synchronized(fileSignal){if(fileRevision!=revision)return;try{fileSignal.wait(millis);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.IOException(e);}}
  }

  void deleteCached(int id) {
    cancel(id);
    send("DeleteFile", v -> {}, "fileId", id);
  }

  long cachedBytes(int id) {
    return num(get(files.get(id),"local"),"downloadedSize");
  }

  void clearMediaCache() throws java.io.IOException {
    if(!ready) throw new java.io.IOException("Reconnect Telegram and clear cache again to remove remaining downloaded media");
    for(int id:new ArrayList<>(files.keySet())) {
      cancel(id);
      waitRequest("DeleteFile","fileId",id);
      files.remove(id);
    }
    // Media only: the encrypted Telegram account/database remains untouched.
    waitRequest("OptimizeStorage","size",0L,"ttl",0,"count",0,"immunityDelay",0,
        "fileTypes",new TdApi.FileType[]{new TdApi.FileTypeVideo(),new TdApi.FileTypeDocument()},
        "chatIds",new long[0],"excludeChatIds",new long[0],"returnDeletedFileStatistics",false,"chatLimit",0);
  }
}
