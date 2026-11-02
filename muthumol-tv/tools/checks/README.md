# Focused checks without Robolectric

Compile `:app:compileDebugJavaWithJavac` first, then run:

```sh
python3 tools/checks/run.py /absolute/path/to/org.json.jar
```

Set `JAVA_HOME` to a JDK 17 installation and `ANDROID_HOME` to the SDK containing android-35. Use a real `org.json` jar rather than Android's runtime stubs. The harness runs production parsing/filtering code without contacting providers. It does not replace the full Gradle unit suite, lint, native codec tests or device testing.
