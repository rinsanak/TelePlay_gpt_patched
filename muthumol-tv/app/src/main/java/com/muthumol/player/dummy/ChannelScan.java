package com.muthumol.player.dummy;
import java.util.*;
import org.drinkless.tdlib.TdApi;
/** Newest ten supported videos, ordered by channel message id, independent of history page order. */
final class ChannelScan {
  static final int LIMIT=120;
  static final class Result {
    final Movie movie;final Object subtitle;final List<Movie> recent;final Map<String,Object> subtitles;
    Result(List<Movie> recent,Map<String,Object> subtitles){this.recent=Collections.unmodifiableList(recent);this.subtitles=subtitles;movie=recent.isEmpty()?null:recent.get(0);subtitle=movie==null?null:subtitles.get(movie.cacheIdentity());}
  }
  private ChannelScan(){}
  static Result scan(TdApi.Message[] messages,long chatId){
    TreeMap<Long,Movie> videos=new TreeMap<>(Collections.reverseOrder());
    for(TdApi.Message message:messages){Movie m=Movie.fromMessage(message,chatId);if(m!=null&&m.fileId>0)videos.put(m.messageId,m);}
    List<Movie> recent=new ArrayList<>();Set<String> seen=new HashSet<>();
    for(Movie m:videos.values()){if(seen.add(m.cacheIdentity()))recent.add(m);if(recent.size()==LIMIT)break;}
    Map<String,Object> subtitles=new HashMap<>();Map<String,Long> subtitleIds=new HashMap<>();
    // A subtitle belongs only to the nearest preceding video, never across the next video.
    for(TdApi.Message message:messages){long id=TelegramBridge.num(message,"id");Object doc=TelegramBridge.get(TelegramBridge.get(message,"content"),"document");if(doc==null||PlayerActivity.subtitleMime(TelegramBridge.str(doc,"fileName"))==null)continue;
      Movie owner=null;for(Movie m:videos.values())if(m.messageId<id){owner=m;break;}
      if(owner!=null){String key=owner.cacheIdentity();if(id>subtitleIds.getOrDefault(key,Long.MIN_VALUE)){subtitles.put(key,doc);subtitleIds.put(key,id);}}
    }
    return new Result(recent,subtitles);
  }
}
