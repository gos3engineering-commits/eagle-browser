package sg.gos3.delivery.driver;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Toast;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final int LOCATION_PERMISSION_REQUEST = 1002;
    private static final String DRIVER_URL = "https://gos3-delivery.vercel.app/driver";

    private WebView webView;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    private boolean pendingPhotoCameraPermission = false;

    private static final String APP_PATCH =
        "(function(){"+
        "window.__gos3NativeScanSuccess=null;window.__gos3NativeScanError=null;"+
        "window.__gos3ParcelWeights=window.__gos3ParcelWeights||{};"+
        "window.gos3NativeScanResult=function(code){if(window.__gos3NativeScanSuccess){var cb=window.__gos3NativeScanSuccess;window.__gos3NativeScanSuccess=null;cb(code,{result:{text:code}});}};"+
        "window.gos3NativeScanCancelled=function(){if(window.__gos3NativeScanError){try{window.__gos3NativeScanError('Scan cancelled');}catch(e){}}};"+
        "window.Html5Qrcode=function(){this.start=function(camera,config,success,error){window.__gos3NativeScanSuccess=success;window.__gos3NativeScanError=error;if(window.AndroidBridge&&AndroidBridge.scanBarcode){AndroidBridge.scanBarcode();}else if(error){error('Native scanner unavailable');}return Promise.resolve();};this.stop=function(){return Promise.resolve();};this.clear=function(){return Promise.resolve();};};"+
        "window.gos3WeightDetected=function(w){var i=document.getElementById('parcelWeight');var s=document.getElementById('weightScanStatus');if(!i)return;if(w){if(!i.value||i.dataset.auto==='1'){i.value=w;i.dataset.auto='1';}if(s)s.textContent='Detected weight: '+w+' kg. You can correct it if needed.';}else{if(s)s.textContent='Weight not detected. Enter it manually.';}};"+
        "var oldCanvasToDataURL=HTMLCanvasElement.prototype.toDataURL;"+
        "HTMLCanvasElement.prototype.toDataURL=function(){var d=oldCanvasToDataURL.apply(this,arguments);try{if(document.getElementById('savep')&&d&&d.indexOf('data:image/')===0&&window.AndroidBridge&&AndroidBridge.detectWeight){var s=document.getElementById('weightScanStatus');if(s)s.textContent='Reading weight from photo…';setTimeout(function(){try{AndroidBridge.detectWeight(d);}catch(e){}},30);}}catch(e){}return d;};"+
        "var oldFetch=window.fetch;window.fetch=function(input,init){try{var u=String(input);if(init&&String(init.method||'GET').toUpperCase()==='POST'&&init.body){var b=JSON.parse(init.body);if(u.indexOf('r=parcels')>=0){var w=window.__gos3ParcelWeights[String(b.sequence)];if(w){b.size='Parcel|KG:'+w;init=Object.assign({},init,{body:JSON.stringify(b)});}}else if(u.indexOf('r=deliveries')>=0){var total=0;Object.keys(window.__gos3ParcelWeights).forEach(function(k){total+=parseFloat(window.__gos3ParcelWeights[k]||0)||0;});var isBig=total>30;b.small=isBig?0:1;b.medium=0;b.large=isBig?1:0;var cls=isBig?'BIG':'SMALL';b.note=((b.note||'')+(b.note?' | ':'')+'Total Weight: '+total.toFixed(3)+' kg | Order Type: '+cls);init=Object.assign({},init,{body:JSON.stringify(b)});}}}catch(e){}return oldFetch.call(this,input,init);};"+
        "function enhance(){"+
        " var first=document.getElementById('first');if(first&&!first.dataset.weightInit){window.__gos3ParcelWeights={};first.dataset.weightInit='1';}"+
        " var save=document.getElementById('savep');if(save&&!document.getElementById('parcelWeight')){var psz=document.getElementById('psz');if(psz){psz.value='Small';var pl=psz.closest('label');if(pl)pl.style.display='none';}var step=document.querySelector('#modal .step');var m=step&&step.textContent.match(/Parcel\\s+(\\d+)/i);var n=m?m[1]:String(Object.keys(window.__gos3ParcelWeights).length+1);var box=document.createElement('div');box.style.cssText='margin:12px 0;padding:12px;border:1px solid #dbe1ea;border-radius:10px;background:#f8fafc';var lab=document.createElement('label');lab.style.cssText='display:block;font-weight:700';lab.appendChild(document.createTextNode('Parcel Weight (kg)'));var inp=document.createElement('input');inp.id='parcelWeight';inp.type='number';inp.step='0.001';inp.min='0.001';inp.inputMode='decimal';inp.placeholder='Auto-detect from photo';inp.style.cssText='width:100%;padding:11px;margin-top:5px;border:1px solid #ccd3dd;border-radius:9px';lab.appendChild(inp);box.appendChild(lab);var ws=document.createElement('div');ws.id='weightScanStatus';ws.style.cssText='font-size:12px;color:#6b7280;margin-top:5px';ws.textContent='Take a clear photo showing the KG label.';box.appendChild(ws);save.parentNode.insertBefore(box,save);save.addEventListener('click',function(e){var i=document.getElementById('parcelWeight');var num=parseFloat(i?i.value:'');if(!isFinite(num)||num<=0){e.preventDefault();e.stopImmediatePropagation();alert('Parcel weight is required. Take a clear label photo or enter the KG manually.');return;}window.__gos3ParcelWeights[n]=num.toFixed(3);},true);}"+
        " var submit=document.getElementById('submitStop');if(submit&&!document.getElementById('orderWeightTotal')){var list=document.getElementById('parlist');if(list){var cards=list.querySelectorAll('.parcel');cards.forEach(function(c,idx){var pill=c.querySelector('.pill');if(pill)pill.style.display='none';var w=window.__gos3ParcelWeights[String(idx+1)];if(w&&!c.querySelector('.gos3kg')){var d=document.createElement('div');d.className='muted gos3kg';d.innerHTML='Weight: <b>'+w+' kg</b>';c.appendChild(d);}});}var total=0;Object.keys(window.__gos3ParcelWeights).forEach(function(k){total+=parseFloat(window.__gos3ParcelWeights[k]||0)||0;});var cls=total>30?'BIG':'SMALL';var t=document.createElement('div');t.id='orderWeightTotal';t.style='margin:12px 0;padding:12px;border-radius:10px;background:#ecfdf5;font-size:18px;font-weight:800';t.innerHTML='TOTAL ORDER WEIGHT: '+total.toFixed(3)+' kg<br>ORDER TYPE: '+cls;submit.parentNode.insertBefore(t,submit);}"+
        " var success=[].slice.call(document.querySelectorAll('.card h2')).find(function(x){return /DELIVERY SUBMITTED/i.test(x.textContent||'');});if(success&&!document.getElementById('submittedWeightTotal')){var total2=0;Object.keys(window.__gos3ParcelWeights).forEach(function(k){total2+=parseFloat(window.__gos3ParcelWeights[k]||0)||0;});var cls2=total2>30?'BIG':'SMALL';var p=document.createElement('p');p.id='submittedWeightTotal';p.innerHTML='<b>Total Order Weight: '+total2.toFixed(3)+' kg<br>Order Type: '+cls2+'</b>';success.parentNode.insertBefore(p,success.nextSibling);}"+
        "}"+
        "new MutationObserver(enhance).observe(document.documentElement,{childList:true,subtree:true});enhance();"+
        "})();";

    public class AndroidBridge {
        @JavascriptInterface
        public void scanBarcode() {
            runOnUiThread(() -> launchGoogleScanner());
        }

        @JavascriptInterface
        public void detectWeight(String dataUrl) {
            try {
                int comma = dataUrl.indexOf(',');
                String encoded = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
                byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) {
                    sendWeightResult("");
                    return;
                }

                InputImage image = InputImage.fromBitmap(bitmap, 0);
                TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        String weight = extractWeight(text.getText());
                        sendWeightResult(weight);
                        recognizer.close();
                    })
                    .addOnFailureListener(e -> {
                        sendWeightResult("");
                        recognizer.close();
                    });
            } catch (Throwable e) {
                sendWeightResult("");
            }
        }
    }

    private String extractWeight(String text) {
        if (text == null) return "";
        String normalized = text.replace(',', '.');
        Pattern p = Pattern.compile("(?i)(\\d{1,3}(?:\\.\\d{1,3})?)\\s*k\\s*g");
        Matcher m = p.matcher(normalized);
        double best = -1;
        while (m.find()) {
            try {
                double v = Double.parseDouble(m.group(1));
                if (v > 0 && v <= 1000) best = v;
            } catch (Exception ignored) {}
        }
        if (best < 0) return "";
        return String.format(Locale.US, "%.3f", best);
    }

    private void sendWeightResult(String weight) {
        final String safe = weight == null ? "" : weight.replace("'", "");
        runOnUiThread(() -> webView.evaluateJavascript(
            "window.gos3WeightDetected && window.gos3WeightDetected('" + safe + "')",
            null
        ));
    }

    private void launchGoogleScanner() {
        try {
            GmsBarcodeScannerOptions options =
                new GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_QR_CODE,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_CODE_93,
                        Barcode.FORMAT_CODABAR,
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_ITF,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_DATA_MATRIX,
                        Barcode.FORMAT_PDF417,
                        Barcode.FORMAT_AZTEC
                    )
                    .enableAutoZoom()
                    .build();

            GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);
            scanner.startScan()
                .addOnSuccessListener(barcode -> {
                    String raw = barcode.getRawValue();
                    if (raw == null) raw = "";
                    raw = raw.trim();
                    if (raw.matches("\\d{6,}")) {
                        Toast.makeText(
                            MainActivity.this,
                            "Raw QR data ignored. Please scan the actual CSN barcode.",
                            Toast.LENGTH_LONG
                        ).show();
                        return;
                    }
                    String code = raw
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "")
                        .replace("\r", "");
                    webView.evaluateJavascript(
                        "window.gos3NativeScanResult && window.gos3NativeScanResult('" + code + "')",
                        null
                    );
                })
                .addOnCanceledListener(() -> webView.evaluateJavascript(
                    "window.gos3NativeScanCancelled && window.gos3NativeScanCancelled()",
                    null
                ))
                .addOnFailureListener(e -> {
                    Toast.makeText(
                        MainActivity.this,
                        "Scanner error: " + (e.getMessage() == null ? "Unable to open scanner" : e.getMessage()),
                        Toast.LENGTH_LONG
                    ).show();
                    webView.evaluateJavascript(
                        "window.gos3NativeScanCancelled && window.gos3NativeScanCancelled()",
                        null
                    );
                });
        } catch (Throwable e) {
            Toast.makeText(this, "Unable to start scanner", Toast.LENGTH_LONG).show();
            webView.evaluateJavascript(
                "window.gos3NativeScanCancelled && window.gos3NativeScanCancelled()",
                null
            );
        }
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
        s.setUserAgentString(s.getUserAgentString() + " GOS3DriverAndroid/1.8");

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.startsWith("https://gos3-delivery.vercel.app/")) {
                    view.evaluateJavascript(APP_PATCH, null);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final android.webkit.PermissionRequest request) {
                runOnUiThread(() -> {
                    if (checkSelfPermission(Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        pendingPhotoCameraPermission = true;
                        request.deny();
                        requestPermissions(
                            new String[]{Manifest.permission.CAMERA},
                            CAMERA_PERMISSION_REQUEST
                        );
                    }
                });
            }

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
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (pendingPhotoCameraPermission) {
                pendingPhotoCameraPermission = false;
                Toast.makeText(
                    this,
                    "Camera permission granted. Tap Take Photo again.",
                    Toast.LENGTH_SHORT
                ).show();
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
