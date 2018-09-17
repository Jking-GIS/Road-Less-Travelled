package com.example.jeff9123.roadlesstravelled;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.os.Process;

import com.esri.arcgisruntime.mapping.view.LocationDisplay;

import java.util.concurrent.TimeUnit;

public class BackgroundService extends Service {
    private static final String TAG = "GPS_SERVICE";

    private ServiceHandler mServiceHandler;
    private LocationDisplay mLocationDisplay;

    private boolean running = true;

    private final class ServiceHandler extends Handler {
        ServiceHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            try {
                try {
                    Intent intent = new Intent(getApplicationContext(), MapActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);

                    LocationRepository mRepository = new LocationRepository(getApplication());
                    while(running) {
                        TimeUnit.SECONDS.sleep(60);
                        Location mLocation = new Location("past " + System.currentTimeMillis(),
                                mLocationDisplay.getLocation().getPosition().getY(),
                                mLocationDisplay.getLocation().getPosition().getX());
                        if(mRepository.locationNotExists(mLocation.getLocation())) {
                            mRepository.insert(mLocation);
                        }
                    }

                    /*NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext(), getString(R.string.notification_channel_id))
                            .setSmallIcon(R.drawable.notification_icon)
                            .setContentTitle("My notification")
                            .setContentText("test: " + mLocationDisplay.getLocation().getPosition().toString())
                            .setStyle(new NotificationCompat.BigTextStyle()
                                    .bigText("test: " + mLocationDisplay.getLocation().getPosition().toString()))
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true);

                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
                    notificationManager.notify(0, mBuilder.build());*/
                } catch (java.lang.SecurityException ex) {
                    Log.i(TAG, "fail to request location update, ignore", ex);
                } catch (Exception ex) {
                    Log.d(TAG, "generic error in thread, " + ex.getMessage());
                }
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
            stopSelf(msg.arg1);
        }
    }

    @Override
    public void onCreate() {
        Log.e(TAG, "onCreate");

        createNotificationChannel();
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        Looper mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "onStartCommand");
        super.onStartCommand(intent, flags, startId);

        mLocationDisplay = ((MyApplication)getApplication()).getLocationDisplay();

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        mServiceHandler.sendMessage(msg);

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;

        Log.e(TAG, "onDestroy");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.notification_channel_name);
            String description = getString(R.string.notification_channel_desc);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(
                    getString(R.string.notification_channel_id), name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
