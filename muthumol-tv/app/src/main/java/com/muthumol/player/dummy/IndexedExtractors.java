package com.muthumol.player.dummy;
import androidx.media3.extractor.*;
import java.io.IOException;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class IndexedExtractors implements ExtractorsFactory {
  final ChannelSession session;final String identity;
  IndexedExtractors(ChannelSession s,String id){session=s;identity=id;}
  public Extractor[] createExtractors(){Extractor[] all=new DefaultExtractorsFactory().createExtractors();for(int i=0;i<all.length;i++){final Extractor target=all[i];all[i]=new Extractor(){
    public boolean sniff(ExtractorInput in)throws IOException{return target.sniff(in);}
    public void init(ExtractorOutput out){target.init(new ExtractorOutput(){public TrackOutput track(int id,int type){return out.track(id,type);}public void endTracks(){out.endTracks();}public void seekMap(SeekMap map){if(session.movie!=null&&identity.equals(session.movie.cacheIdentity()))session.capture(map);out.seekMap(map);}});}
    public int read(ExtractorInput in,PositionHolder holder)throws IOException{return target.read(in,holder);}
    public void seek(long p,long time){target.seek(p,time);}public void release(){target.release();}
  };}return all;}
}
