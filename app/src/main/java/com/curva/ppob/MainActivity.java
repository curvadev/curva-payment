package com.curva.ppob;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.v4.content.FileProvider;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JsPromptResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.iid.FirebaseInstanceId;
import com.journeyapps.barcodescanner.CaptureActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    private WebView webView;
    private RelativeLayout splash;
    private Handler splashHandler = new Handler();
    private Handler delayHandler = new Handler(); 

    private String fcmToken = "";
    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int REQUEST_CONTACT_PERMISSION = 1002;
    private static final int PICK_CONTACT_REQUEST = 1003;
    private static final int BARCODE_SCAN_REQUEST = 1004;
    private static final int REQUEST_BLUETOOTH_PERMISSION = 1005;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1006;

    private final String BASE_URL = "https://curva.web.id/ppob/";
    private final String HOME_URL = BASE_URL + "index.php";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setStatusBarColor("#f5f5f5");

        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                         FrameLayout.LayoutParams.MATCH_PARENT, 
                         FrameLayout.LayoutParams.MATCH_PARENT));

        // ============================================
        // SETUP SPLASH SCREEN
        // ============================================
        splash = new RelativeLayout(this);
        splash.setBackgroundColor(Color.parseColor("#f5f5f5"));

        LinearLayout centerWrap = new LinearLayout(this);
        centerWrap.setOrientation(LinearLayout.VERTICAL);
        centerWrap.setGravity(Gravity.CENTER);

        RelativeLayout.LayoutParams centerParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT, 
            RelativeLayout.LayoutParams.WRAP_CONTENT);
        centerParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        centerParams.bottomMargin = (int) (80 * getResources().getDisplayMetrics().density); 
        splash.addView(centerWrap, centerParams);

        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier("splash_logo", "drawable", getPackageName()));
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setAdjustViewBounds(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            logo.setElevation(8f);
        }

        int logoWidth = (int) (260 * getResources().getDisplayMetrics().density); 
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(logoWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        centerWrap.addView(logo, logoParams);

        String appVersion = "1.0.0";
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            appVersion = pInfo.versionName; 
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        TextView version = new TextView(this);
        version.setText("Version " + appVersion);
        version.setTextColor(Color.GRAY);
        version.setTextSize(12);

        RelativeLayout.LayoutParams versionParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT, 
            RelativeLayout.LayoutParams.WRAP_CONTENT);
        versionParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        versionParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        versionParams.bottomMargin = (int) (40 * getResources().getDisplayMetrics().density);
        splash.addView(version, versionParams);

        root.addView(splash, new FrameLayout.LayoutParams(
                         FrameLayout.LayoutParams.MATCH_PARENT, 
                         FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // ============================================
        // PROSES INIT WEBVIEW
        // ============================================
        setupWebView();
        setupCookies();
        loadFcmToken(); 

        webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    saveCookies();
                    injectAndroidBridge();
                    sendFcmTokenToWeb();
                }

                @SuppressWarnings("deprecation")
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    if (isMaintenanceTime()) {
                        showMaintenanceScreen(view);
                    } else {
                        showOfflineScreen(view);
                    }
                }

                @TargetApi(Build.VERSION_CODES.M)
                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request.isForMainFrame()) {
                        if (isMaintenanceTime()) showMaintenanceScreen(view);
                        else showOfflineScreen(view);
                    }
                }

                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                    if (request.isForMainFrame()) {
                        if (errorResponse.getStatusCode() >= 500) {
                            if (isMaintenanceTime()) showMaintenanceScreen(view);
                            else showOfflineScreen(view);
                        }
                    }
                }
            });

        String initialUrl = handleDeepLink(getIntent());

        if (isMaintenanceTime()) {
            showMaintenanceScreen(webView);
        } else if (isNetworkAvailable()) {
            webView.loadUrl(initialUrl);
        } else {
            showOfflineScreen(webView);
        }

        splashHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    hideSplash();
                }
            }, 2500);
    }

    private void hideSplash() {
        splash.animate().alpha(0f).setDuration(450);

        delayHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					setStatusBarColor("#1791f4");
					splash.setVisibility(View.GONE);

					checkNotificationPermission();
					checkForAppUpdate();
				}
			}, 500); 
    }

    // ============================================================
    // TRIK MEMANCING POP-UP IZIN NOTIFIKASI DI ANDROID 13+
    // ============================================================
    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    "curva_payment_notif",
                    "Transaksi & Deposit",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                );
                notificationManager.createNotificationChannel(channel);
            }
        }

        if (Build.VERSION.SDK_INT >= 33) { 
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{"android.permission.POST_NOTIFICATIONS"}, 
                    REQUEST_NOTIFICATION_PERMISSION
                );
            }
        }
    }

    // ============================================================
    // MESIN PEMBANDING VERSI
    // ============================================================
    private boolean isVersionOlder(String currentVersion, String serverVersion) {
        if (currentVersion == null || serverVersion == null) return false;

        String[] currentParts = currentVersion.split("\\.");
        String[] serverParts = serverVersion.split("\\.");

        int length = Math.max(currentParts.length, serverParts.length);

        for (int i = 0; i < length; i++) {
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            int serverPart = i < serverParts.length ? Integer.parseInt(serverParts[i]) : 0;

            if (currentPart < serverPart) {
                return true; 
            }
            if (currentPart > serverPart) {
                return false; 
            }
        }
        return false; 
    }

    // ============================================================
    // LOGIKA PENGECEKAN VERSI APLIKASI 
    // ============================================================
    private void checkForAppUpdate() {
        new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						String bypassCacheUrl = BASE_URL + "api/check_version.php?timestamp=" + System.currentTimeMillis();

						URL url = new URL(bypassCacheUrl); 
						HttpURLConnection conn = (HttpURLConnection) url.openConnection();
						conn.setRequestMethod("GET");

						conn.setUseCaches(false); 
						conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36");
						conn.setRequestProperty("Cache-Control", "no-cache");
						conn.setRequestProperty("Pragma", "no-cache");
						conn.setRequestProperty("Accept", "application/json");

						conn.setConnectTimeout(8000); 
						conn.setReadTimeout(8000);

						if (conn.getResponseCode() == 200) {
							BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
							StringBuilder response = new StringBuilder();
							String line;
							while ((line = reader.readLine()) != null) {
								response.append(line);
							}
							reader.close();

							JSONObject json = new JSONObject(response.toString());

							if (json.getBoolean("success")) {
								String serverVersion = "1.0.0";
								if (json.has("latest_version_name")) {
									serverVersion = json.getString("latest_version_name");
								} else if (json.has("latest_version_code")) {
									serverVersion = json.getString("latest_version_code");
								}

								final String finalServerVersion = serverVersion;
								final boolean forceUpdate = json.getBoolean("force_update");
								final String updateUrl = json.getString("update_url");
								final String releaseNotes = json.getString("release_notes");

								PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
								final String currentVersion = pInfo.versionName; 

								runOnUiThread(new Runnable() {
										@Override
										public void run() {
											if (isVersionOlder(currentVersion, finalServerVersion)) {
												if (!isFinishing()) {
													showUpdateDialog(forceUpdate, updateUrl, releaseNotes);
												}
											}
										}
									});
							}
						}
					} catch (final Exception e) {
						e.printStackTrace();
					}
				}
			}).start();
    }

    // ============================================================
    // CUSTOM MODERN DIALOG UNTUK POP-UP UPDATE
    // ============================================================
    private void showUpdateDialog(final boolean isForced, final String url, String notes) {
        final android.app.Dialog dialog = new android.app.Dialog(MainActivity.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(!isForced);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        int dp20 = (int) (20 * getResources().getDisplayMetrics().density);
        int dp24 = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(dp24, dp24, dp24, dp24);

        android.graphics.drawable.GradientDrawable bgShape = new android.graphics.drawable.GradientDrawable();
        bgShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bgShape.setCornerRadius(dp20);
        bgShape.setColor(Color.WHITE);
        root.setBackground(bgShape);

        TextView title = new TextView(this);
        title.setText("Pembaruan Tersedia \uD83D\uDE80"); 
        title.setTextSize(22);
        title.setTextColor(Color.parseColor("#1e293b")); 
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView message = new TextView(this);
        message.setText(notes);
        message.setTextSize(15);
        message.setTextColor(Color.parseColor("#64748b")); 
        message.setGravity(Gravity.LEFT); 
        message.setLineSpacing(0, 1.2f);

        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        msgParams.setMargins(0, dp20, 0, dp24);
        root.addView(message, msgParams);

        TextView btnUpdate = new TextView(this);
        btnUpdate.setText("Perbarui Sekarang");
        btnUpdate.setTextColor(Color.WHITE);
        btnUpdate.setTextSize(16);
        btnUpdate.setTypeface(null, android.graphics.Typeface.BOLD);
        btnUpdate.setGravity(Gravity.CENTER);

        int btnPadding = (int)(14 * getResources().getDisplayMetrics().density);
        btnUpdate.setPadding(0, btnPadding, 0, btnPadding);

        android.graphics.drawable.GradientDrawable btnShape = new android.graphics.drawable.GradientDrawable();
        btnShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        btnShape.setCornerRadius(dp20);
        btnShape.setColor(Color.parseColor("#1791f4")); 
        btnUpdate.setBackground(btnShape);

        btnUpdate.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setData(Uri.parse(url));
					startActivity(intent);
					if (isForced) {
						finish();
					}
				}
			});
        root.addView(btnUpdate);

        if (!isForced) {
            TextView btnLater = new TextView(this);
            btnLater.setText("Nanti Saja");
            btnLater.setTextColor(Color.parseColor("#94a3b8"));
            btnLater.setTextSize(15);
            btnLater.setTypeface(null, android.graphics.Typeface.BOLD);
            btnLater.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            laterParams.setMargins(0, (int)(12 * getResources().getDisplayMetrics().density), 0, 0);
            btnLater.setPadding(0, (int)(10 * getResources().getDisplayMetrics().density), 0, (int)(10 * getResources().getDisplayMetrics().density));

            btnLater.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						dialog.dismiss();
					}
				});
            root.addView(btnLater, laterParams);
        }

        dialog.setContentView(root);

        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = (int) (metrics.widthPixels * 0.85);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        dialog.show();
    }

    // ============================================
    // LOGIKA SENSOR WAKTU MAINTENANCE
    // ============================================
    private boolean isMaintenanceTime() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        if (hour == 23 && minute >= 30) {
            return true;
        } else if (hour == 0 && minute <= 10) {
            return true;
        }

        return false;
    }

    private void showMaintenanceScreen(WebView view) {
        String maintenanceHtml = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>" +
            "<body style=\"display:flex;flex-direction:column;justify-content:center;align-items:center;height:100vh;margin:0;background-color:#f8fafc;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;text-align:center;padding:20px;\">" +
            "<div style=\"width:90px;height:90px;background:#eff6ff;border-radius:24px;display:flex;justify-content:center;align-items:center;margin-bottom:24px;box-shadow:0 10px 25px rgba(23,145,244,0.15);\">" +
            "<svg width=\"48\" height=\"48\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#1791f4\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\">" +
            "<circle cx=\"12\" cy=\"12\" r=\"3\"></circle><path d=\"M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z\"></path>" +
            "</svg></div>" +
            "<h2 style=\"color:#1e293b;margin:0 0 12px;font-size:24px;font-weight:800;\">Sistem Maintenance</h2>" +
            "<p style=\"color:#64748b;margin:0 0 10px;line-height:1.6;font-size:15px;\">Untuk menjaga performa dan kestabilan transaksi, aplikasi rutin melakukan pemeliharaan server pada:</p>" +
            "<div style=\"background:#e2e8f0; color:#334155; padding:8px 16px; border-radius:12px; font-weight:800; font-family:monospace; font-size:16px; margin-bottom:30px;\">Pkl 23:30 - 00:10 WIB</div>" +
            "<button onclick=\"prompt('AndroidBridge:checkMaintenance', '')\" style=\"background:#1791f4;color:#fff;border:none;padding:16px 32px;border-radius:16px;font-size:16px;font-weight:bold;cursor:pointer;box-shadow:0 4px 15px rgba(23,145,244,0.3);width:100%;max-width:280px;\">Cek Kembali Akses</button>" +
            "</body></html>";

        view.loadDataWithBaseURL(null, maintenanceHtml, "text/html", "UTF-8", null);
    }

    private void showOfflineScreen(WebView view) {
        String offlineHtml = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>" +
            "<body style=\"display:flex;flex-direction:column;justify-content:center;align-items:center;height:100vh;margin:0;background-color:#f8fafc;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;text-align:center;padding:20px;\">" +
            "<div style=\"width:80px;height:80px;background:#fee2e2;border-radius:50%;display:flex;justify-content:center;align-items:center;margin-bottom:20px;box-shadow:0 4px 10px rgba(239,68,68,0.2);\">" +
            "<svg width=\"40\" height=\"40\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#ef4444\" stroke-width=\"2.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\">" +
            "<path d=\"M10.53 5.53A8.96 8.96 0 0 1 12 5c2.6 0 5.1 1.1 6.9 2.9l2.3-2.3\"/><path d=\"M14 14l-2 2\"/><path d=\"M19 19l-7-7\"/><path d=\"M5 5l14 14\"/><path d=\"M8.5 8.5A8.96 8.96 0 0 0 3 11l2.3 2.3\"/>" +
            "</svg></div>" +
            "<h2 style=\"color:#1e293b;margin:0 0 10px;font-size:22px;font-weight:800;\">Koneksi Terputus</h2>" +
            "<p style=\"color:#64748b;margin:0 0 30px;line-height:1.5;font-size:14px;\">Aplikasi membutuhkan koneksi internet atau link tujuan tidak ditemukan. Silakan periksa koneksi Anda dan coba lagi.</p>" +
            "<button onclick=\"prompt('AndroidBridge:retryConnection', '')\" style=\"background:#1791f4;color:#fff;border:none;padding:14px 32px;border-radius:16px;font-size:16px;font-weight:bold;cursor:pointer;box-shadow:0 4px 12px rgba(23,145,244,0.3);width:100%;max-width:250px;\">Buka Halaman Utama</button>" +
            "</body></html>";

        view.loadDataWithBaseURL(null, offlineHtml, "text/html", "UTF-8", null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (intent != null && intent.getExtras() != null) {
            String newUrl = handleDeepLink(intent);
            if (!newUrl.equals(HOME_URL)) {
                if (isMaintenanceTime()) {
                    showMaintenanceScreen(webView);
                } else if (isNetworkAvailable()) {
                    webView.loadUrl(newUrl);
                } else {
                    showOfflineScreen(webView);
                }
            }
        }
    }

    // ============================================================
    // PERBAIKAN: MENGARAHKAN DEEP LINK LANGSUNG KE POPUP MODAL
    // ============================================================
    private String handleDeepLink(Intent intent) {
        String urlToLoad = HOME_URL; 
        if (intent != null && intent.getExtras() != null) {
            String type = intent.getStringExtra("type");

            if ("deposit_success".equals(type)) {
                String notifId = intent.getStringExtra("notification_id");
                if (notifId != null) { 
                    // Mengarahkan ke list notif dengan parameter show_id agar modal otomatis kebuka
                    urlToLoad = BASE_URL + "user/notifications.php?show_id=" + notifId; 
                } else {
                    urlToLoad = BASE_URL + "user/notifications.php";
                }
            } else if ("transaction_success".equals(type) || "transaction_failed".equals(type)) {
                String trxId = intent.getStringExtra("transaction_id");
                if (trxId != null) { 
                    urlToLoad = BASE_URL + "user/transaction_detail.php?id=" + trxId; 
                }
            } else if (intent.hasExtra("target_url")) {
                String target = intent.getStringExtra("target_url");
                if (target != null && !target.isEmpty()) {
                    if (target.startsWith("http")) {
                        urlToLoad = target;
                    } else {
                        if (target.startsWith("/")) {
                            target = target.substring(1); 
                        }
                        urlToLoad = BASE_URL + target;
                    }
                }
            }
        }
        return urlToLoad;
    }

    // ============================================================
    // JAVASCRIPT INTERFACE BRIDGE
    // ============================================================
    private void injectAndroidBridge() {
        String js = "javascript:(function(){" +
            "if (typeof window.Android === 'undefined') {" +
            "window.Android = {" +
            "getContacts: function() { return prompt('AndroidBridge:getContacts', ''); }," +
            "requestContactPermission: function() { prompt('AndroidBridge:requestContactPermission', ''); }," +
            "showToast: function(message) { prompt('AndroidBridge:showToast', message); }," +
            "openContactPicker: function() { prompt('AndroidBridge:openContactPicker', ''); }," +
            "scanBarcode: function() { prompt('AndroidBridge:scanBarcode', ''); }," +
            "printReceipt: function(data, base64) { " +
            "   if(base64 === undefined) base64 = ''; " +
            "   prompt('AndroidBridge:printReceipt', data + '|||SPLIT|||' + base64); " +
            "}," +
            "shareImage: function(base64) { prompt('AndroidBridge:shareImage', base64); }," +
            "shareReceiptText: function(base64, caption) { " +
            "   if(caption === undefined) caption = ''; " +
            "   prompt('AndroidBridge:shareReceiptText', base64 + '|||SPLIT|||' + caption); " +
            "}," +
            "saveImage: function(base64, filename) { prompt('AndroidBridge:saveImage', base64 + '|||SPLIT|||' + filename); }," +
            "openApp: function(url) { prompt('AndroidBridge:openApp', url); }," +
            "updateFCMToken: function() { prompt('AndroidBridge:updateFCMToken', ''); }" +
            "};" +
            "}" +
            "})()";
        webView.evaluateJavascript(js, null);
    }

    private boolean handleAndroidBridge(String message, String defaultValue, JsPromptResult result) {
        if (message != null && message.startsWith("AndroidBridge:")) {
            String action = message.replace("AndroidBridge:", "");

            if (action.equals("getContacts")) { 
                result.confirm(getContactsFromDevice()); 
                return true; 
            } 
            else if (action.equals("requestContactPermission")) { 
                requestContactPermission(); 
                result.confirm(""); 
                return true; 
            } 
            else if (action.equals("showToast")) { 
                showToast(defaultValue); 
                result.confirm(""); 
                return true; 
            } 
            else if (action.equals("openContactPicker")) { 
                openContactPicker(); 
                result.confirm(""); 
                return true; 
            } 
            else if (action.equals("scanBarcode")) { 
                scanBarcode(); 
                result.confirm(""); 
                return true; 
            } 
            else if (action.equals("printReceipt")) { 
                String textToPrint = defaultValue;
                String logoBase64 = "";

                if (defaultValue != null && defaultValue.contains("|||SPLIT|||")) {
                    String[] parts = defaultValue.split("\\|\\|\\|SPLIT\\|\\|\\|");
                    textToPrint = parts.length > 0 ? parts[0] : "";
                    logoBase64 = parts.length > 1 ? parts[1] : "";
                }

                autoPrintReceipt(textToPrint, logoBase64); 
                result.confirm(""); 
                return true; 
            }
            else if (action.equals("shareImage")) { 
                shareImage(defaultValue); 
                result.confirm(""); 
                return true; 
            } 
            else if (action.equals("shareReceiptText")) {
                String base64Str = defaultValue;
                String captionStr = "Berikut adalah bukti pembayaran.";
                if (defaultValue != null && defaultValue.contains("|||SPLIT|||")) {
                    String[] parts = defaultValue.split("\\|\\|\\|SPLIT\\|\\|\\|");
                    base64Str = parts.length > 0 ? parts[0] : "";
                    captionStr = parts.length > 1 ? parts[1] : "";
                }
                shareReceiptText(base64Str, captionStr);
                result.confirm("");
                return true;
            }
            else if (action.equals("saveImage")) {
                String base64Str = defaultValue;
                String fileNameStr = "QRIS_Deposit.png";

                if (defaultValue != null && defaultValue.contains("|||SPLIT|||")) {
                    String[] parts = defaultValue.split("\\|\\|\\|SPLIT\\|\\|\\|");
                    base64Str = parts.length > 0 ? parts[0] : "";
                    fileNameStr = parts.length > 1 ? parts[1] : "QRIS_Deposit.png";
                }

                saveImage(base64Str, fileNameStr);
                result.confirm("");
                return true;
            }
            else if (action.equals("openApp")) { 
                openAppNative(defaultValue); 
                result.confirm(""); 
                return true; 
            }
            else if (action.equals("retryConnection")) {
                runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isMaintenanceTime()) {
                                showMaintenanceScreen(webView);
                            } else if (isNetworkAvailable()) {
                                webView.loadUrl(HOME_URL);
                            } else {
                                showToast("Koneksi internet masih terputus.");
                            }
                        }
                    });
                result.confirm(""); 
                return true; 
            }
            else if (action.equals("checkMaintenance")) {
                runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isMaintenanceTime()) {
                                showToast("Sistem masih dalam mode pemeliharaan.");
                            } else {
                                if (isNetworkAvailable()) {
                                    webView.loadUrl(HOME_URL);
                                } else {
                                    showOfflineScreen(webView);
                                }
                            }
                        }
                    });
                result.confirm(""); 
                return true; 
            }
            else if (action.equals("updateFCMToken")) {
                runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            sendFcmTokenToWeb();
                        }
                    });
                result.confirm("");
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // FITUR CETAK THERMAL 
    // =========================================================================
    private void autoPrintReceipt(final String textToPrint, final String logoBase64) {
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) { 
            webView.post(new Runnable() {
                    @Override
                    public void run() {
                        webView.evaluateJavascript("javascript:if(typeof onPrintResult === 'function'){onPrintResult(false, 'Perangkat tidak mendukung Bluetooth');}", null);
                    }
                });
            return; 
        }

        if (!bluetoothAdapter.isEnabled()) { 
            webView.post(new Runnable() {
                    @Override
                    public void run() {
                        webView.evaluateJavascript("javascript:if(typeof onPrintResult === 'function'){onPrintResult(false, 'Silakan aktifkan Bluetooth HP Anda');}", null);
                    }
                });
            return; 
        }

        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, REQUEST_BLUETOOTH_PERMISSION);
                webView.post(new Runnable() {
                        @Override
                        public void run() {
                            webView.evaluateJavascript("javascript:if(typeof onPrintResult === 'function'){onPrintResult(false, 'Izin Bluetooth belum diberikan');}", null);
                        }
                    });
                return;
            }
        }

        final Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() == 0) { 
            webView.post(new Runnable() {
                    @Override
                    public void run() {
                        webView.evaluateJavascript("javascript:if(typeof onPrintResult === 'function'){onPrintResult(false, 'Belum ada printer yang di-pairing');}", null);
                    }
                });
            return; 
        }

        final ArrayList<BluetoothDevice> likelyPrinters = new ArrayList<>();
        final ArrayList<BluetoothDevice> otherDevices = new ArrayList<>();

        for (BluetoothDevice device : pairedDevices) {
            String name = device.getName() != null ? device.getName().toLowerCase() : "";
            int majorClass = device.getBluetoothClass() != null ? device.getBluetoothClass().getMajorDeviceClass() : -1;

            if (majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO || majorClass == BluetoothClass.Device.Major.PHONE || majorClass == BluetoothClass.Device.Major.COMPUTER) {    
                continue; 
            }

            if (majorClass == BluetoothClass.Device.Major.IMAGING || name.contains("print") || name.contains("mtp") || name.contains("zj") || name.contains("58") || name.contains("80") || name.contains("pt-") || name.contains("blue")) {
                likelyPrinters.add(device);
            } else {
                otherDevices.add(device);
            }
        }

        final ArrayList<BluetoothDevice> devicesToTry = new ArrayList<>();
        devicesToTry.addAll(likelyPrinters);
        devicesToTry.addAll(otherDevices);

        if (devicesToTry.isEmpty()) { 
            webView.post(new Runnable() {
                    @Override
                    public void run() {
                        webView.evaluateJavascript("javascript:if(typeof onPrintResult === 'function'){onPrintResult(false, 'Tidak ada perangkat tipe printer');}", null);
                    }
                });
            return; 
        }

        new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean isPrinted = false;
                    String printerName = "";
                    UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

                    byte[] imageBytes = null;
                    if (logoBase64 != null && logoBase64.startsWith("data:image")) {
                        try {
                            String pureBase64 = logoBase64.substring(logoBase64.indexOf(",") + 1);
                            byte[] decodedString = Base64.decode(pureBase64, Base64.DEFAULT);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

                            if(bitmap != null){
                                int targetWidth = 360; 
                                targetWidth = (targetWidth / 8) * 8; 
                                int targetHeight = (int) (bitmap.getHeight() * ((float) targetWidth / bitmap.getWidth()));

                                Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
                                imageBytes = decodeBitmapToEscPos(scaledBitmap);
                            }
                        } catch (Exception e) {
                            e.printStackTrace(); 
                        }
                    }

                    for (BluetoothDevice device : devicesToTry) {
                        try {
                            BluetoothSocket socket = device.createRfcommSocketToServiceRecord(uuid);
                            socket.connect(); 
                            OutputStream outputStream = socket.getOutputStream();

                            outputStream.write(new byte[]{27, 64}); 

                            if (imageBytes != null) {
                                outputStream.write(new byte[]{27, 97, 1}); 
                                outputStream.flush();

                                int chunkSize = 256; 
                                for (int i = 0; i < imageBytes.length; i += chunkSize) {
                                    int length = Math.min(chunkSize, imageBytes.length - i);
                                    outputStream.write(imageBytes, i, length);
                                    outputStream.flush();
                                    Thread.sleep(15); 
                                }

                                outputStream.write("\n\n".getBytes()); 
                                outputStream.flush();
                                Thread.sleep(400); 
                            }

                            outputStream.write(textToPrint.getBytes("UTF-8"));
                            outputStream.write("\n\n\n".getBytes());
                            outputStream.flush();
                            socket.close();

                            isPrinted = true;
                            printerName = device.getName();
                            break; 
                        } catch (Exception e) { 
                            continue; 
                        }
                    }

                    final boolean finalResult = isPrinted;
                    final String finalName = printerName;

                    runOnUiThread(new Runnable() { 
                            @Override public void run() { 
                                if (finalResult) {
                                    webView.evaluateJavascript("javascript:if(typeof onPrintResult === 'function'){onPrintResult(true, 'Berhasil mencetak di printer: " + finalName + "');}", null);
                                } else {
                                    webView.evaluateJavascript("javascript:if(typeof onPrintResult === 'function'){onPrintResult(false, 'Gagal terhubung ke printer. Pastikan printer menyala.');}", null);
                                }
                            } 
                        });
                }
            }).start();
    }

    private byte[] decodeBitmapToEscPos(Bitmap bmp) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();

        int xBytes = (width + 7) / 8;
        byte[] data = new byte[8 + (xBytes * height)];

        data[0] = 0x1D;
        data[1] = 0x76;
        data[2] = 0x30;
        data[3] = 0x00;
        data[4] = (byte) (xBytes % 256);
        data[5] = (byte) (xBytes / 256);
        data[6] = (byte) (height % 256);
        data[7] = (byte) (height / 256);

        int idx = 8;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < xBytes * 8; x += 8) {
                byte b = 0;
                for (int k = 0; k < 8; k++) {
                    if (x + k < width) {
                        int pixel = bmp.getPixel(x + k, y);
                        int alpha = Color.alpha(pixel);
                        int r = Color.red(pixel);
                        int g = Color.green(pixel);
                        int blue = Color.blue(pixel);

                        if (alpha < 128) continue; 

                        int luminance = (int) (r * 0.299 + g * 0.587 + blue * 0.114);

                        if (luminance < 235) {
                            b |= (1 << (7 - k));
                        }
                    }
                }
                data[idx++] = b;
            }
        }
        return data;
    }

    // ============================================================
    // FUNGSI BAWAAN LAINNYA
    // ============================================================
    private void shareImage(String base64Data) {
        try {
            byte[] decodedString = Base64.decode(base64Data, Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

            File cachePath = new File(getCacheDir(), "images");
            cachePath.mkdirs(); 
            File file = new File(cachePath, "struk_curva_payment.png");
            FileOutputStream stream = new FileOutputStream(file);
            decodedByte.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);

            if (contentUri != null) {
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.setDataAndType(contentUri, getContentResolver().getType(contentUri));
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Berikut adalah bukti pembayaran dari Curva Payment."); 

                startActivity(Intent.createChooser(shareIntent, "Bagikan Struk Melalui..."));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ============================================================
    // FUNGSI BARU: SHARE GAMBAR DENGAN TEKS KUSTOM DARI WEB
    // ============================================================
    @android.webkit.JavascriptInterface
    private void shareReceiptText(String base64Data, String customCaption) {
        try {
            byte[] decodedString = Base64.decode(base64Data, Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

            File cachePath = new File(getCacheDir(), "images");
            cachePath.mkdirs(); 
            File file = new File(cachePath, "struk_pembayaran.png");
            FileOutputStream stream = new FileOutputStream(file);
            decodedByte.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);

            if (contentUri != null) {
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.setDataAndType(contentUri, getContentResolver().getType(contentUri));
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                // MENGGUNAKAN TEKS KUSTOM
                shareIntent.putExtra(Intent.EXTRA_TEXT, customCaption); 

                startActivity(Intent.createChooser(shareIntent, "Bagikan Struk Melalui..."));
            }
        } catch (IOException e) {
            e.printStackTrace();
            showToast("Gagal memproses gambar struk");
        }
    }

    @android.webkit.JavascriptInterface
    public void saveImage(String base64String, String fileName) {
        try {
            byte[] decodedString = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT);
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1);
            }

            android.content.ContentResolver resolver = MainActivity.this.getContentResolver();
            android.net.Uri uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                java.io.OutputStream out = resolver.openOutputStream(uri);
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
                out.close();

                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    values.clear();
                    values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0);
                    resolver.update(uri, values, null, null);
                }

                MainActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            android.widget.Toast.makeText(MainActivity.this, "QRIS berhasil disimpan ke Galeri", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
            } else {
                throw new Exception("URI is null");
            }
        } catch (Exception e) {
            e.printStackTrace();
            MainActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        android.widget.Toast.makeText(MainActivity.this, "Gagal menyimpan gambar", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
        }
    }

    private void openAppNative(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            try {
                if (url.startsWith("whatsapp://")) {
                    String fallbackUrl = url.replace("whatsapp://send?phone=", "https://wa.me/");
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)));
                } else if (url.startsWith("tg://")) {
                    String fallbackUrl = url.replace("tg://resolve?domain=", "https://t.me/");
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)));
                } 
            } catch (Exception ex) {
            }
        }
    }

    private void scanBarcode() {
        try {
            Intent intent = new Intent(this, CaptureActivity.class);
            intent.putExtra("SCAN_MODE", "QR_CODE_MODE,PRODUCT_MODE");
            intent.putExtra("ORIENTATION_LOCK", true);
            startActivityForResult(intent, BARCODE_SCAN_REQUEST);
        } catch (Exception e) {
            try {
                Intent intent = new Intent("com.google.zxing.client.android.SCAN");
                intent.putExtra("SCAN_MODE", "QR_CODE_MODE,PRODUCT_MODE");
                startActivityForResult(intent, BARCODE_SCAN_REQUEST);
            } catch (Exception ex) {
            }
        }
    }

    private void openContactPicker() {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{android.Manifest.permission.READ_CONTACTS}, 
                REQUEST_CONTACT_PERMISSION
            );
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        startActivityForResult(intent, PICK_CONTACT_REQUEST);
    }

    private String getContactsFromDevice() {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) { 
            return "[]"; 
        }

        JSONArray contactsArray = new JSONArray();
        ContentResolver cr = getContentResolver();

        try {
            Cursor cursor = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
            if (cursor != null && cursor.getCount() > 0) {
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameIndex);
                    String phone = cursor.getString(phoneIndex);
                    if (phone != null) {
                        phone = phone.replaceAll("[^0-9]", "");
                        if (phone.startsWith("62")) { phone = "0" + phone.substring(2); }
                        if (phone.length() > 13) { phone = phone.substring(phone.length() - 13); }
                    }
                    if (name != null && phone != null && phone.length() >= 10) {
                        JSONObject contact = new JSONObject();
                        contact.put("name", name);
                        contact.put("tel", phone);
                        contactsArray.put(contact);
                    }
                }
                cursor.close();
            }
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
        return contactsArray.toString();
    }

    private void requestContactPermission() { 
        requestPermissions(
            new String[]{android.Manifest.permission.READ_CONTACTS}, 
            REQUEST_CONTACT_PERMISSION
        ); 
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CONTACT_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) { 
                openContactPicker(); 
            } else { 
                webView.evaluateJavascript("javascript:onAndroidContactsError('Izin ditolak')", null); 
            }
        } 
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) { 
                results = new Uri[]{data.getData()}; 
            }
            if (filePathCallback != null) { 
                filePathCallback.onReceiveValue(results); 
                filePathCallback = null; 
            }
        }

        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri contactUri = data.getData();
            String contactNumber = "";
            try {
                Cursor phoneCursor = getContentResolver().query(contactUri, new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
                if (phoneCursor != null && phoneCursor.moveToFirst()) { 
                    contactNumber = phoneCursor.getString(0); 
                    phoneCursor.close(); 
                }
            } catch (Exception e) { 
                e.printStackTrace(); 
            }

            if (contactNumber != null && !contactNumber.isEmpty()) {
                contactNumber = contactNumber.replaceAll("[^0-9]", "");
                if (contactNumber.startsWith("62")) { contactNumber = "0" + contactNumber.substring(2); }
                if (contactNumber.length() > 13) { contactNumber = contactNumber.substring(contactNumber.length() - 13); }
            }

            final String phoneNumber = contactNumber;
            webView.post(new Runnable() {
                    @Override
                    public void run() {
                        webView.evaluateJavascript("javascript:try{onContactPicked('" + phoneNumber + "');}catch(e){}", null);
                        webView.evaluateJavascript("javascript:(function(){var input=document.getElementById('target');if(input){input.value='" + phoneNumber + "';var ev=new Event('input');input.dispatchEvent(ev);}})()", null);
                        webView.evaluateJavascript("javascript:try{closeContactModal();}catch(e){}", null);
                    }
                });
        }

        if (requestCode == BARCODE_SCAN_REQUEST && resultCode == RESULT_OK && data != null) {
            String barcodeResult = data.getStringExtra("SCAN_RESULT");
            if (barcodeResult == null || barcodeResult.isEmpty()) { 
                barcodeResult = data.getStringExtra("barcode"); 
            }

            if (barcodeResult != null && !barcodeResult.isEmpty()) {
                final String js = "javascript:onBarcodeScanned('" + barcodeResult + "')";
                webView.post(new Runnable() { 
                        @Override public void run() { 
                            webView.evaluateJavascript(js, null); 
                        } 
                    });
            }
        }
    }

    public void showToast(final String message) { 
        runOnUiThread(new Runnable() { 
                @Override public void run() { 
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show(); 
                } 
            }); 
    }

    private void setStatusBarColor(String color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor(color));
            window.getDecorView().setSystemUiVisibility(0);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        settings.setAppCacheEnabled(true);
        settings.setAppCachePath(getApplicationContext().getCacheDir().getAbsolutePath());
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);

        String defaultAgent = settings.getUserAgentString();
        settings.setUserAgentString(defaultAgent + " CurvaApp");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.setWebChromeClient(new WebChromeClient() {

                @Override
                public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                    if (MainActivity.this.filePathCallback != null) {
                        MainActivity.this.filePathCallback.onReceiveValue(null);
                    }
                    MainActivity.this.filePathCallback = filePathCallback;

                    String[] acceptTypes = fileChooserParams.getAcceptTypes();
                    Intent intent;
                    String chooserTitle;

                    if (acceptTypes != null && acceptTypes.length > 0 && acceptTypes[0].contains("image")) {
                        intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        intent.setType("image/*");
                        chooserTitle = "Pilih Aplikasi Foto";
                    } else {
                        intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                        chooserTitle = "Pilih Aplikasi File Manager";
                    }

                    Intent chooserIntent = Intent.createChooser(intent, chooserTitle);

                    try {
                        startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST);
                        return true;
                    } catch (ActivityNotFoundException e) {
                        MainActivity.this.filePathCallback = null;
                        return false;
                    }
                }

                @Override
                public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                    if (MainActivity.this.handleAndroidBridge(message, defaultValue, result)) {
                        return true;
                    }
                    return super.onJsPrompt(view, url, message, defaultValue, result);
                }
            });
    }

    private void setupCookies() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }
    }

    private void saveCookies() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { 
            CookieManager.getInstance().flush(); 
        }
    }

    private void loadFcmToken() {
        try {
            fcmToken = FirebaseInstanceId.getInstance().getToken();
        } catch (Exception e) { 
            fcmToken = ""; 
        }
    }

    public void sendFcmTokenToWeb() {
        if (fcmToken == null || fcmToken.equals("")) { loadFcmToken(); }
        if (fcmToken == null || fcmToken.equals("")) { return; }

        try {
            String token = java.net.URLEncoder.encode(fcmToken, "UTF-8");
            String device = java.net.URLEncoder.encode("Android WebView", "UTF-8");

            final String js = "javascript:(function(){" +
                "try{" +
                "var xhr=new XMLHttpRequest();" +
                "xhr.open('POST','" + BASE_URL + "api/save_fcm_token.php',true);" +
                "xhr.setRequestHeader('Content-Type','application/x-www-form-urlencoded');" +
                "xhr.send('token=" + token + "&device_name=" + device + "');" +
                "}catch(e){}" +
                "})()";

            webView.post(new Runnable() {
                    @Override
                    public void run() {
                        webView.evaluateJavascript(js, null);
                    }
                });
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
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

