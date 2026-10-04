# LiteRT (TensorFlow Lite) – nativní rozhraní volá Java třídy reflexí
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn org.tensorflow.lite.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class io.github.marciano.psitlumocnik.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.marciano.psitlumocnik.data.**$$serializer { *; }
