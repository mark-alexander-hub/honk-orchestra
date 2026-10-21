# The page calls these through addJavascriptInterface.
-keepclassmembers class io.github.markalexanderhub.honkorchestra.MainActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
