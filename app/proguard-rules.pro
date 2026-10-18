# The JNI bridge looks these up by name.
-keep class com.omnifind.app.engine.Embedder { native <methods>; }
-keep class com.omnifind.app.engine.EmbedderException { <init>(java.lang.String); }
-keep class com.omnifind.app.search.VectorMath { native <methods>; }

# WorkManager creates the workers by class name.
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# PDFBox: optional codecs it never needs here (JPEG 2000, JBIG2), and its font and glyph resources.
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
