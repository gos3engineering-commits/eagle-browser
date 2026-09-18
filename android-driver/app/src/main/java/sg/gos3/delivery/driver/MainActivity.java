package sg.gos3.delivery.driver;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class MainActivity extends Activity {
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final int LOCATION_PERMISSION_REQUEST = 1002;
    private static final String DRIVER_URL = "https://gos3-delivery.vercel.app/driver";

    private WebView webView;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    private boolean pendingScan = false;

    private static final String SCANNER_PATCH =
        "(function(){"+
        "window.__gos3NativeScanSuccess=null;"+
        "window.__gos3NativeScanError=null;"+
        "window.gos3NativeScanResult=function(code){"+
        " if(window.__gos3NativeScanSuccess){var cb=window.__gos3NativeScanSuccess;window.__gos3NativeScanSuccess=null;cb(code,{result:{text:code}});}"+
        "};"+
        "window.gos3NativeScanCancelled=function(){"+
        " if(window.__gos3NativeScanError){try{window.__gos3NativeScanError('Scan cancelled');}catch(e){}}"+
        "};"+
        "window.Html5Qrcode=function(){"+
        " this.start=function(camera,config,success,error){"+
        "   window.__gos3NativeScanSuccess=success;window.__gos3NativeScanError=error;"+
        "   if(window.AndroidBridge&&AndroidBridge.scanBarcode){AndroidBridge.scanBarcode();}"+
        "   else if(error){error('Native scanner unavailable');}"+
        "   return Promise.resolve();"+
        " };"+
        " this.stop=function(){return Promise.resolve();};"+
        " this.clear=function(){return Promise.resolve();};"+
        "};"+
        "})();";

    public class AndroidBridge {
        @JavascriptInterface
        public void scanBarcode() {
            runOnUiThread(() -> {
                pendingScan = true;
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
                    return;
                }
                launchScanner();
            });
        }
    }

    private void launchScanner() {
        pendingScan = false;
        IntentIntegrator integrator = new IntentIntegrator(MainActivity.this);
        integrator.setPrompt("Scan parcel QR code / CSN barcode");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(false);
        integrator.setBarcodeImageEnabled(false);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.initiateScan();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " GOS3DriverAndroid/1.4");

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.startsWith("https://gos3-delivery.vercel.app/")) {
                    view.evaluateJavascript(SCANNER_PATCH, null);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin,
                    GeolocationPermissions.Callback callback) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                } else {
                    geoOrigin = origin;
                    geoCallback = callback;
                    requestPermissions(
                            new String[]{
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                            },
                            LOCATION_PERMISSION_REQUEST
                    );
                }
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(DRIVER_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                String code = result.getContents()
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "")
                        .replace("\r", "");
                webView.evaluateJavascript(
                        "window.gos3NativeScanResult && window.gos3NativeScanResult('" + code + "')",
                        null
                );
            } else {
                webView.evaluateJavascript(
                        "window.gos3NativeScanCancelled && window.gos3NativeScanCancelled()",
                        null
                );
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (checkSelfPermission(Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED && pendingScan) {
                launchScanner();
            } else {
                pendingScan = false;
                webView.evaluateJavascript(
                        "alert('Camera permission is required to scan parcel QR / CSN barcodes.')",
                        null
                );
            }
            return;
        }

        if (requestCode == LOCATION_PERMISSION_REQUEST && geoCallback != null) {
            boolean allowed =
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED;
            geoCallback.invoke(geoOrigin, allowed, false);
            geoCallback = null;
            geoOrigin = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
