package id.denpam.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DENPAM V1 Android shell. No local AI, no API key, no private website credentials in code.
 * Website login/session, recordings, user roles and audit remain managed by andin.top.
 * "Otomatis" launches Android's explicit audio sharing mechanism; ChatGPT Project
 * upload and completion cannot be guaranteed or silently invoked.
 */
public final class MainActivity extends Activity {
    private static final String HOME = "https://andin.top/denpam/";
    private static final String HOST = "andin.top";
    private static final String CHATGPT_HOST = "chatgpt.com";
    private static final String INTERNAL_SCHEME = "denpamshare";
    private static final int MICROPHONE_REQUEST = 101;
    private static final int FILE_REQUEST = 102;
    private static final long MAX_AUDIO_BYTES = 250L * 1024L * 1024L;
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView web;
    private ProgressBar progress;
    private PermissionRequest waitingMic;
    private ValueCallback<Uri[]> waitingFiles;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(87,11,24));
        getWindow().setNavigationBarColor(Color.rgb(51,7,17));
        new File(getCacheDir(), "shared").mkdirs();
        cleanupStaleAudio();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.WHITE);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14),dp(4),dp(14),dp(4));
        top.setBackgroundColor(Color.rgb(92,9,22));
        TextView title = new TextView(this);
        title.setText("DENPAM  •  Pengamanan Internal");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14f);
        title.setTypeface(null,1);
        top.addView(title, new LinearLayout.LayoutParams(0,dp(34),1));
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleSmall);
        progress.setVisibility(View.GONE);
        top.addView(progress,new LinearLayout.LayoutParams(dp(28),dp(28)));
        layout.addView(top);

        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(layout);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        WebView.setWebContentsDebuggingEnabled(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (INTERNAL_SCHEME.equalsIgnoreCase(uri.getScheme())) {
                    if (request.isForMainFrame() && isDenpamUrl(Uri.parse(view.getUrl()==null?"":view.getUrl()))) {
                        handleBridgeUri(uri);
                    }
                    return true;
                }
                if (isDenpamUrl(uri)) return false;
                if (request.isForMainFrame()) openExternal(uri);
                return true;
            }
            @Override public void onPageFinished(WebView view,String url) {
                if (isDenpamUrl(Uri.parse(url))) {
                    injectShareIntegration(url);
                    boolean session = Uri.parse(url).getPath().matches("/denpam/sessions/[0-9]+/?");
                    if (session) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
                progress.setVisibility(View.GONE);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view,int newProgress) {
                progress.setVisibility(newProgress >= 95 ? View.GONE : View.VISIBLE);
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> requestMicrophone(request));
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (waitingMic == request) waitingMic = null;
            }
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params) {
                if (waitingFiles != null) waitingFiles.onReceiveValue(null);
                waitingFiles = callback;
                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent,FILE_REQUEST);
                    return true;
                } catch (Exception ex) {
                    waitingFiles = null;
                    callback.onReceiveValue(null);
                    toast("Pemilih file tidak tersedia");
                    return true;
                }
            }
        });
        web.setDownloadListener((url,userAgent,contentDisposition,mimeType,contentLength) -> {
            if (!isDenpamUrl(Uri.parse(url))) { toast("Unduhan di luar DENPAM diblokir"); return; }
            startDownload(url,mimeType,contentDisposition);
        });
        web.loadUrl(HOME);
    }

    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    private static boolean isDenpamUrl(Uri url) {
        if (url==null || !"https".equalsIgnoreCase(url.getScheme()) || !HOST.equalsIgnoreCase(url.getHost())) return false;
        String path = url.getPath();
        return path != null && (path.equals("/denpam") || path.startsWith("/denpam/"));
    }
    private static boolean isSafeProject(Uri url) {
        return url!=null && "https".equalsIgnoreCase(url.getScheme()) && CHATGPT_HOST.equalsIgnoreCase(url.getHost());
    }
    private void toast(String msg){Toast.makeText(this,msg,Toast.LENGTH_LONG).show();}

    private void requestMicrophone(PermissionRequest req) {
        if (!isDenpamUrl(req.getOrigin())) { req.deny(); return; }
        boolean audioRequested = false;
        for (String res : req.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) audioRequested=true;
            else { req.deny(); return; } // Never grant camera, video, etc. implicitly
        }
        if (!audioRequested) { req.deny(); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {
            req.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else {
            if (waitingMic!=null) waitingMic.deny();
            waitingMic=req;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MICROPHONE_REQUEST);
        }
    }

    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if (code != MICROPHONE_REQUEST || waitingMic==null) return;
        PermissionRequest req=waitingMic;waitingMic=null;
        if (results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED && isDenpamUrl(req.getOrigin())) {
            req.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else { req.deny();toast("Izin mikrofon diperlukan untuk merekam"); }
    }
    @Override protected void onActivityResult(int code,int result,Intent data) {
        super.onActivityResult(code,result,data);
        if(code==FILE_REQUEST && waitingFiles!=null){
            waitingFiles.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));
            waitingFiles=null;
        }
    }

    private void injectShareIntegration(String url) {
        // Use URL navigation, NOT addJavascriptInterface: avoids exposing privileged Java methods to iframes.
        // Only inject on a trusted DENPAM session page. Native handler also checks main-frame + trusted origin.
        if (!Uri.parse(url).getPath().matches("/denpam/sessions/[0-9]+/?")) return;
        String script = "(function(){"
           + "if(window.__denpamNativeReady)return;window.__denpamNativeReady=true;"
           + "function project(){try{var e=document.getElementById('denpamChatgptConfig');"
           + "var j=JSON.parse(e?e.textContent:'{}');"
           + "return /^https:\\/\\/chatgpt\\.com\\//.test(j.project_url||'')?j.project_url:'https://chatgpt.com/';"
           + "}catch(e){return 'https://chatgpt.com/';}}"
           + "function launch(m,id){"
           + "if(!confirm('Anda akan membagikan rekaman ke layanan di luar DENPAM. Pastikan izin instansi sudah tersedia. Lanjutkan?'))return;"
           + "var s=document.getElementById('handoffStatus');if(s){s.hidden=false;s.textContent='Menyiapkan rekaman untuk Android...';}"
           + "location.href='denpamshare://'+m+'?rid='+encodeURIComponent(id)+'&project='+encodeURIComponent(project());"
           + "}"
           + "window.handoffAutomatic=function(id){launch('auto',id)};"
           + "window.handoffManual=function(id){launch('manual',id)};"
           + "})();";
        web.evaluateJavascript(script,null);
    }

    private void handleBridgeUri(Uri uri) {
        String method=uri.getHost();
        int rid;
        try { rid=Integer.parseInt(uri.getQueryParameter("rid")); }
        catch(Exception ignored) {toast("ID rekaman tidak valid");return;}
        if (rid <= 0 || !("auto".equals(method)||"manual".equals(method))) {toast("Perintah tidak valid");return;}
        Uri project;
        try {project=Uri.parse(uri.getQueryParameter("project"));}
        catch(Exception ex){project=Uri.parse("https://chatgpt.com/");}
        if(!isSafeProject(project)) project=Uri.parse("https://chatgpt.com/");
        if("manual".equals(method)){
            startDownload(HOME+"recordings/"+rid+"?download=true","audio/webm","DENPAM-Rekaman-"+rid+".webm");
            logHandoff(rid,"manual");
            openExternal(project);
            toast("Lampirkan rekaman dari folder Download di Project ChatGPT");
        }else{
            shareNative(rid);
        }
    }

    private String cookieFor(String url){String cookie=CookieManager.getInstance().getCookie(url);return cookie==null?"":cookie;}
    private void startDownload(String url,String mime,String disposition) {
        try {
            Uri uri=Uri.parse(url);
            if(!isDenpamUrl(uri)) throw new IllegalArgumentException("URL unduhan tidak diizinkan");
            DownloadManager.Request r=new DownloadManager.Request(uri);
            String cookie=cookieFor(url);
            if(!cookie.isEmpty())r.addRequestHeader("Cookie",cookie);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            String file=URLUtil.guessFileName(url,disposition,mime);
            if(!file.matches("[A-Za-z0-9_ .()\\-]{1,180}\\.(webm|m4a|mp3|wav|ogg|aac|flac|docx|zip)")){
                file="DENPAM-Rekaman-"+System.currentTimeMillis()+".webm";
            }
            r.setTitle(file);
            r.setDescription("Unduhan rekaman DENPAM");
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,file);
            DownloadManager dm=(DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(r);
            toast("Rekaman sedang diunduh ke folder Download");
        }catch(Exception e){toast("Unduhan gagal: "+e.getMessage());}
    }

    private void shareNative(int rid) {
        progress.setVisibility(View.VISIBLE);
        final String audioUrl=HOME+"recordings/"+rid+"?download=true";
        final String cookie=cookieFor(audioUrl);
        background.execute(()->{
            File path=null;
            try {
                path=downloadPrivateAudio(audioUrl,cookie,rid);
                File finalFile=path;
                main.post(()->{
                    progress.setVisibility(View.GONE);
                    Uri attachment=Uri.parse("content://"+getPackageName()+".share/audio/"+finalFile.getName());
                    Intent send=new Intent(Intent.ACTION_SEND);
                    send.setType(PrivateAudioProvider.mimeFor(finalFile.getName()));
                    send.putExtra(Intent.EXTRA_STREAM,attachment);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    send.setClipData(ClipData.newUri(getContentResolver(),"Rekaman DENPAM",attachment));
                    // Try sending directly into installed ChatGPT; fall back to Android chooser.
                    try {
                        Intent toChatGPT=new Intent(send);
                        toChatGPT.setPackage("com.openai.chatgpt");
                        startActivity(toChatGPT);
                        toast("ChatGPT dibuka. Pilih Project DENPAM dan kirim audio bila diminta.");
                    } catch (ActivityNotFoundException ex) {
                        try { startActivity(Intent.createChooser(send,"Pilih aplikasi untuk audio DENPAM"));
                            toast("Jika ChatGPT tidak tersedia, gunakan Start Manual.");
                        } catch (ActivityNotFoundException e){toast("Tidak ada aplikasi penerima audio. Gunakan Start Manual.");}
                    }
                    logHandoff(rid,"automatic");
                });
            } catch(Exception ex) {
                if(path!=null)path.delete();
                String message=ex.getMessage()==null?"tidak diketahui":ex.getMessage();
                main.post(()->{progress.setVisibility(View.GONE);toast("Gagal menyiapkan rekaman: "+message+". Gunakan Start Manual.");});
            }
        });
    }

    private File downloadPrivateAudio(String url,String cookie,int rid)throws IOException{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(15000);
        c.setReadTimeout(120000);
        c.setRequestProperty("Accept","audio/*");
        if(!cookie.isEmpty())c.setRequestProperty("Cookie",cookie);
        File target=null;
        try{
            int code=c.getResponseCode();
            if(code==401||code==403||code==302||code==303||code==307)throw new IOException("Sesi login berakhir. Login kembali di DENPAM.");
            if(code!=200)throw new IOException("Server audio HTTP "+code);
            String mime=c.getContentType();
            if(mime==null)mime="";
            mime=mime.split(";")[0].trim().toLowerCase(Locale.ROOT);
            if(!mime.startsWith("audio/")&&!mime.equals("application/octet-stream"))throw new IOException("Respons bukan file audio ("+mime+")");
            long size=c.getContentLengthLong();
            if(size>MAX_AUDIO_BYTES)throw new IOException("Audio melebihi 250 MB, pilih Start Manual");
            String extension=PrivateAudioProvider.extensionFor(mime);
            String name=UUID.randomUUID().toString().replace("-","")+"."+extension;
            target=new File(new File(getCacheDir(),"shared"),name);
            long count=0;
            try(InputStream in=c.getInputStream(); FileOutputStream out=new FileOutputStream(target)){
                byte[] b=new byte[65536];int n;
                while((n=in.read(b))!=-1){count+=n;if(count>MAX_AUDIO_BYTES)throw new IOException("Audio terlalu besar");out.write(b,0,n);}
            }
            if(count==0)throw new IOException("File audio kosong");
            return target;
        }catch(IOException e){if(target!=null)target.delete();throw e;}
        finally{c.disconnect();}
    }

    private void openExternal(Uri uri){
        if(!"https".equalsIgnoreCase(uri.getScheme()))return;
        try {Intent intent=new Intent(Intent.ACTION_VIEW,uri);startActivity(intent);}
        catch(ActivityNotFoundException e){toast("Tidak ada browser untuk membuka tautan");}
    }
    private void logHandoff(int rid,String mode) {
        String url=HOME+"api/recordings/"+rid+"/chatgpt-handoff";
        String cookie=cookieFor(url);
        background.execute(()->{
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(url).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setRequestMethod("POST");
                c.setConnectTimeout(5000);c.setReadTimeout(5000);
                c.setRequestProperty("Content-Type","application/json");
                if(!cookie.isEmpty())c.setRequestProperty("Cookie",cookie);
                c.setDoOutput(true);
                byte[] bytes=("{\"mode\":\""+mode+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try(java.io.OutputStream output=c.getOutputStream()){output.write(bytes);}
                c.getResponseCode(); // Records intent only, never confirms upload to ChatGPT
            }catch(Exception ignored){}
            finally{if(c!=null)c.disconnect();}
        });
    }
    private void cleanupStaleAudio(){
        File folder=new File(getCacheDir(),"shared");File[] files=folder.listFiles();if(files==null)return;
        long threshold=System.currentTimeMillis()-24L*60*60*1000;
        for(File file:files){if(file.isFile()&&file.lastModified()<threshold)file.delete();}
    }
    @Override public void onBackPressed() {
        if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(waitingMic!=null){waitingMic.deny();waitingMic=null;}
        if(waitingFiles!=null){waitingFiles.onReceiveValue(null);waitingFiles=null;}
        background.shutdown();
        if(web!=null)web.destroy();
        super.onDestroy();
    }
}
