package com.curva.ppob;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = "";
        String body = "";
        Map<String, String> dataPayload = null;

        // Ambil notifikasi standar
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }

        // Ambil data payload dari PHP kita
        if (remoteMessage.getData().size() > 0) {
            dataPayload = remoteMessage.getData();
            if (title == null || title.isEmpty()) title = dataPayload.get("title");
            if (body == null || body.isEmpty()) body = dataPayload.get("body");
        }

        if (title != null && body != null) {
            sendNotification(title, body, dataPayload);
        }
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
    }

    // ========================================================
    // MESIN PENDOWNLOAD GAMBAR (Mengubah URL jadi Foto)
    // ========================================================
    private Bitmap getBitmapFromUrl(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void sendNotification(String title, String messageBody, Map<String, String> data) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        String imageUrl = "";

        // Sisipkan seluruh data dari PHP ke dalam Intent
        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }
            // Tangkap link gambarnya!
            if (data.containsKey("image_url")) {
                imageUrl = data.get("image_url");
            }
        }

        // Jalankan mesin pendownload gambar jika URL tersedia
        Bitmap imageBitmap = null;
        if (imageUrl != null && !imageUrl.isEmpty()) {
            imageBitmap = getBitmapFromUrl(imageUrl);
        }

        int pendingFlags = PendingIntent.FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= 23) {
            pendingFlags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingFlags);

        String channelId = "curva_payment_notif";
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationManager notificationManager =
			(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Notification notification;

        // [JALAN BERCABANG UNTUK LOLOS AIDE]
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // UNTUK HP BARU (ANDROID 8.0 KE ATAS)
            NotificationChannel channel = new NotificationChannel(channelId,
																  "Transaksi & Deposit",
																  NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);

            android.app.Notification.Builder builder = new android.app.Notification.Builder(this, channelId)
				.setSmallIcon(R.drawable.ic_launcher)
				.setContentTitle(title)
				.setContentText(messageBody)
				.setAutoCancel(true)
				.setSound(defaultSoundUri)
				.setVibrate(new long[]{1000, 1000})
				.setContentIntent(pendingIntent);

            // Jika ada gambar, sulap menjadi Notifikasi Besar (BigPictureStyle)
            if (imageBitmap != null) {
                builder.setStyle(new android.app.Notification.BigPictureStyle()
								 .bigPicture(imageBitmap)
								 .bigLargeIcon((Bitmap) null));
            }

            notification = builder.build();
        } else {
            // UNTUK HP LAMA (DI BAWAH ANDROID 8.0)
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.ic_launcher)
				.setContentTitle(title)
				.setContentText(messageBody)
				.setAutoCancel(true)
				.setSound(defaultSoundUri)
				.setVibrate(new long[]{1000, 1000})
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setContentIntent(pendingIntent);

            // Jika ada gambar, sulap menjadi Notifikasi Besar (BigPictureStyle)
            if (imageBitmap != null) {
                builder.setStyle(new NotificationCompat.BigPictureStyle()
								 .bigPicture(imageBitmap)
								 .bigLargeIcon(null));
            }

            notification = builder.build();
        }

        notificationManager.notify((int) System.currentTimeMillis(), notification);
    }
}

