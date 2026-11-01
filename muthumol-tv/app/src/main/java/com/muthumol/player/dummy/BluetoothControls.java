package com.muthumol.player.dummy;
import android.app.Activity;
import android.bluetooth.*;
import android.content.*;
import android.media.*;
import android.provider.Settings;
import java.util.*;

final class BluetoothControls {
  final Activity activity;final Runnable lostAudio;boolean registered;
  BluetoothControls(Activity a,Runnable lost){activity=a;lostAudio=lost;}
  final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){String action=i.getAction();BetaLog.event("audio_route_event","noisy="+AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)+" bluetoothState="+i.getIntExtra(BluetoothProfile.EXTRA_STATE,-1));if(AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)||BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)&&i.getIntExtra(BluetoothProfile.EXTRA_STATE,-1)==BluetoothProfile.STATE_DISCONNECTED)lostAudio.run();}};
  void start(){if(registered)return;IntentFilter f=new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);f.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);activity.registerReceiver(receiver,f);registered=true;}
  void stop(){if(registered){activity.unregisterReceiver(receiver);registered=false;}}
}
