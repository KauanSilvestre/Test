# O MVP é distribuído sem minificação. Se a minificação for ativada,
# mantenha os serializadores gerados pelo kotlinx.serialization.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.sentinela.** {
    *** Companion;
}
-keepclasseswithmembers class app.sentinela.** {
    kotlinx.serialization.KSerializer serializer(...);
}
