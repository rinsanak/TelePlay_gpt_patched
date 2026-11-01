package com.muthumol.player.dummy;

import java.util.*;

/** Keep exception types and every stack frame, but redact private message content. */
final class DiagnosticText {
  private DiagnosticText(){}
  static String sanitize(String value){
    if(value==null)return "";
    return value.replaceAll("(?i)(?:https?|file|content|tg|rtsp)://\\S+","[uri]")
      .replaceAll("(?:/[A-Za-z0-9._%+-]+){2,}(?:[^\\s]*)?","[path]")
      .replaceAll("(?i)(?:api[_ -]?(?:hash|key|id)|token|password|authorization|phone|chatId|userId)\\s*[:=]\\s*\\S+","[private]")
      .replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b","[email]")
      .replaceAll("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b","[ip]")
      .replaceAll("\\b[a-fA-F0-9]{24,}\\b","[hex]")
      .replaceAll("\\+?\\d{9,}","[number]").replace('\r',' ').replace('\n',' ');
  }
  static String trace(Throwable error){
    StringBuilder out=new StringBuilder();
    Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
    append(error,"",out,seen);
    return out.toString();
  }
  private static void append(Throwable error,String label,StringBuilder out,Set<Throwable> seen){
    if(error==null)return;
    if(!seen.add(error)){out.append(label).append("[circular cause]\n");return;}
    if(out.length()>48000){out.append("trace_limit_reached\n");return;}
    out.append(label).append(error.getClass().getName()).append(": ").append(sanitize(error.getMessage())).append('\n');
    for(StackTraceElement frame:error.getStackTrace()){if(out.length()>48000){out.append("trace_limit_reached\n");return;}out.append("  at ").append(frame).append('\n');}
    for(Throwable suppressed:error.getSuppressed())append(suppressed,"Suppressed: ",out,seen);
    append(error.getCause(),"Caused by: ",out,seen);
  }
}
