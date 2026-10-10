-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn org.slf4j.**
# Jackson's optional desktop-JVM annotations and DOM bootstrap are absent on Android.
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry

# Commons Compress discovers ZIP extra fields reflectively during runtime extraction.
# Preserve concrete classes and public no-argument constructors in optimized APKs.
-keep class * implements org.apache.commons.compress.archivers.zip.ZipExtraField {
    public <init>();
}
