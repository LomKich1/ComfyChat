# OkHttp / Okio: необязательные провайдеры TLS, которых на Android нет
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# kotlinx.serialization: сериализаторы наших классов (история в index.json)
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.comfychat.**$$serializer { *; }
-keepclassmembers class dev.comfychat.** { *** Companion; }
-keepclasseswithmembers class dev.comfychat.** { kotlinx.serialization.KSerializer serializer(...); }

# имена enum сохраняются в настройках (ThemeMode.valueOf)
-keepclassmembers enum dev.comfychat.** { *; }
