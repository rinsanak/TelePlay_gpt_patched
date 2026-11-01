package com.muthumol.player.dummy;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

final class SecretStore {
  final android.content.SharedPreferences prefs;

  SecretStore(Context c) {
    prefs = c.getSharedPreferences("private_config", 0);
  }

  javax.crypto.SecretKey key() throws Exception {
    KeyStore s = KeyStore.getInstance("AndroidKeyStore");
    s.load(null);
    String a = "MuthumolConfigV1";
    if (!s.containsAlias(a)) {
      KeyGenerator g = KeyGenerator.getInstance("AES", "AndroidKeyStore");
      g.init(
          new KeyGenParameterSpec.Builder(
                  a, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      g.generateKey();
    }
    return (javax.crypto.SecretKey) s.getKey(a, null);
  }

  void put(String n, String value) throws Exception {
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.ENCRYPT_MODE, key());
    String data =
        Base64.encodeToString(c.getIV(), 2)
            + ":"
            + Base64.encodeToString(c.doFinal(value.getBytes("UTF-8")), 2);
    prefs.edit().putString(n, data).apply();
  }

  String get(String n) {
    try {
      String value = prefs.getString(n, "");
      if (value.isEmpty()) return "";
      String[] a = value.split(":", 2);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(a[0], 2)));
      return new String(c.doFinal(Base64.decode(a[1], 2)), "UTF-8");
    } catch (Exception e) {
      return "";
    }
  }
}
