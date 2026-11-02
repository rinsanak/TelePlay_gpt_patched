package com.muthumol.player.dummy;

import android.net.Uri;
import org.json.*;

final class Movie {
  String title = "Video", uri = "", mime = "video/mp4";
  boolean saveAllowed=true;
  int fileId, duration, width, height;
  String caption = "", identity="", artwork="", artworkPath="",groupOverride="";
  String uiTitle(android.content.Context c){return MovieDetails.override(c,this);}
  String cacheIdentity(){return identity.isEmpty()?uri:identity;}
  String legacyUri(){return fileId>0?"tg://file/"+fileId+"/"+Uri.encode(title):uri;}
  long savedPosition(android.content.Context c){return VideoCopies.position(c,this);}
  long ownSavedPosition(android.content.Context c){android.content.SharedPreferences prefs=c.getSharedPreferences("player",0);String key="pos:"+cacheIdentity();if(prefs.contains(key))return prefs.getLong(key,0);return prefs.getLong("pos:"+uri,prefs.getLong("pos:"+legacyUri(),0));}
  org.json.JSONArray alternatives=new org.json.JSONArray();
  long date;
  long size, chatId, messageId;

  JSONObject json() {
    JSONObject j = new JSONObject();
    try {
      j.put("alternatives",alternatives).put("groupOverride",groupOverride).put("saveAllowed",saveAllowed).put("title", title)
          .put("uri", uri)
          .put("mime", mime)
          .put("file", fileId)
          .put("size", size)
          .put("chat", chatId)
          .put("message", messageId)
          .put("duration", duration).put("width", width).put("height", height)
          .put("artwork",artwork).put("artworkPath",artworkPath).put("identity",identity).put("caption", caption).put("date", date);
    } catch (Exception e) {
    }
    return j;
  }

  static Movie from(JSONObject j) {
    Movie m = new Movie();
    m.alternatives=j.optJSONArray("alternatives")==null?new org.json.JSONArray():j.optJSONArray("alternatives");m.groupOverride=j.optString("groupOverride");m.saveAllowed=j.optBoolean("saveAllowed",true);
    m.title = j.optString("title", "Video");
    m.uri = j.optString("uri");
    m.mime = j.optString("mime", "video/mp4");
    m.fileId = j.optInt("file");
    m.size = j.optLong("size");
    m.chatId = j.optLong("chat");
    m.messageId = j.optLong("message");
    m.duration = j.optInt("duration"); m.width=j.optInt("width"); m.height=j.optInt("height");
    m.artwork=j.optString("artwork");m.artworkPath=j.optString("artworkPath");
    m.identity=j.optString("identity");
    m.caption=j.optString("caption"); m.date=j.optLong("date");
    return m;
  }

  String details() {
    String detail = MainActivity.size(size) + "  •  " + (mime.isEmpty() ? "Video file" : mime);
    if (width > 0 && height > 0) detail += "  •  " + width + "×" + height;
    if (duration > 0) detail += "  •  " + String.format(java.util.Locale.US, "%d:%02d", duration / 60, duration % 60);
    if (date > 0) detail += "  •  " + new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(new java.util.Date(date * 1000));
    return title + "\n" + detail + (caption.isEmpty() ? "" : "\n" + caption.substring(0, Math.min(180, caption.length())));
  }

  static Movie fromMessage(Object message, long chat) {
    if(!TelegramBridge.str(message,"restrictionReason").isEmpty())return null;
    Object content = TelegramBridge.get(message, "content"),
        media = TelegramBridge.get(content, "video");
    if (media == null) media = TelegramBridge.get(content, "document");
    if (media == null) return null;
    String name = TelegramBridge.str(media, "fileName");
    String mime = TelegramBridge.str(media, "mimeType");
    Object file = TelegramBridge.get(media, "video");
    if (file == null) file = TelegramBridge.get(media, "document");
    if (file == null) return null;
    boolean movie =
        mime.startsWith("video/")
            || name.toLowerCase().matches(".*\\.(mp4|mkv|webm|avi|mov|m4v|ts|mpeg|mpg)$");
    if (!movie) return null;
    Movie m = new Movie();
    m.title = name.isEmpty() ? "Telegram video" : name;
    m.mime = mime.startsWith("video/") ? mime : "";
    m.fileId = (int) TelegramBridge.num(file, "id");
    m.size = TelegramBridge.num(file, "size");
    if (m.size == 0) m.size = TelegramBridge.num(file, "expectedSize");
    m.duration=(int)TelegramBridge.num(media,"duration"); m.width=(int)TelegramBridge.num(media,"width"); m.height=(int)TelegramBridge.num(media,"height");
    Object mini=TelegramBridge.get(media,"minithumbnail"),data=TelegramBridge.get(mini,"data");
    if(data instanceof byte[]&&((byte[])data).length<=65536)m.artwork=android.util.Base64.encodeToString((byte[])data,android.util.Base64.NO_WRAP);
    Object thumb=TelegramBridge.get(TelegramBridge.get(media,"thumbnail"),"file"),local=TelegramBridge.get(thumb,"local");
    if(TelegramBridge.bool(local,"isDownloadingCompleted"))m.artworkPath=TelegramBridge.str(local,"path");
    m.date=TelegramBridge.num(message,"date");
    m.caption=TelegramBridge.str(TelegramBridge.get(content,"caption"),"text");
    Object canSave=TelegramBridge.get(message,"canBeSaved");if(canSave instanceof Boolean)m.saveAllowed=(Boolean)canSave;
    m.chatId = chat;
    m.messageId = TelegramBridge.num(message, "id");
    String unique=TelegramBridge.str(TelegramBridge.get(file,"remote"),"uniqueId");
    m.identity=unique.isEmpty()?"telegram:"+chat+":"+m.messageId+":"+m.fileId:"telegram:"+unique;
    m.uri = "tg://file/" + m.fileId + "/" + Uri.encode(m.identity);
    Object variants=TelegramBridge.get(content,"alternativeVideos");if(variants instanceof Object[])for(Object variant:(Object[])variants){Object altFile=TelegramBridge.get(variant,"video");int id=(int)TelegramBridge.num(altFile,"id");long bytes=TelegramBridge.num(altFile,"size");if(bytes<=0)bytes=TelegramBridge.num(altFile,"expectedSize");if(id<=0||bytes<=0)continue;Movie copy=Movie.from(m.json());copy.alternatives=new org.json.JSONArray();copy.fileId=id;copy.size=bytes;copy.width=(int)TelegramBridge.num(variant,"width");copy.height=(int)TelegramBridge.num(variant,"height");String uniqueId=TelegramBridge.str(TelegramBridge.get(altFile,"remote"),"uniqueId");copy.identity=uniqueId.isEmpty()?"telegram:"+chat+":"+m.messageId+":alternative:"+TelegramBridge.num(variant,"id"):"telegram:"+uniqueId;copy.mime="video/mp4";String altName=m.title.replaceAll("(?i)(2160|1080|720|576|480|360)p",copy.height+"p");if(altName.equals(m.title))altName=altName.replaceFirst("(?i)\\.(mkv|mp4)$","")+" "+copy.height+"p.mp4";copy.title=altName;copy.uri="tg://file/"+id+"/"+Uri.encode(copy.identity);m.alternatives.put(copy.json());}
    return m;
  }
}
