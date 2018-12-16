package geyer.sensorlab.v1psychapp;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.util.Objects;

public class logger extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    BroadcastReceiver screenReceiver;
    SharedPreferences prefs;
    SharedPreferences.Editor editor;

    Handler handler;
    printForegroundTask printForeground;

    UsageStatsManager usm;
    ActivityManager am;

    String currentlyRunningApp, runningApp;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        initializeService();
        initializeSQLCipher();
        initializeSharedPreferences();
        initializeHandler();

        //retrieve data from bundle
        Bundle bundle = intent.getExtras();
        if(bundle != null){
            if(bundle.getBoolean("usage log")){
                Log.i("Bundle", "true");
                initializeBroadcastReceiversWithUsageCapabilities();
            }else{
                Log.i("Bundle", "false");
                initializeBroadcastReceivers();
            }
        }else{
            initializeBroadcastReceivers();
        }

        return START_STICKY;
    }

    private void initializeSharedPreferences() {
        prefs = getSharedPreferences("app initialization prefs", MODE_PRIVATE);
        editor = prefs.edit();
        editor.apply();
    }

    private void initializeService() {
        Log.i("SERVICE", "running");

        if (Build.VERSION.SDK_INT >= 26) {
            if (Build.VERSION.SDK_INT > 26) {
                String CHANNEL_ONE_ID = "sensor.example. geyerk1.inspect.screenservice";
                String CHANNEL_ONE_NAME = "Screen service";
                NotificationChannel notificationChannel = null;
                notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,
                        CHANNEL_ONE_NAME, NotificationManager.IMPORTANCE_MIN);
                notificationChannel.setShowBadge(true);
                notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                assert manager != null;
                manager.createNotificationChannel(notificationChannel);

                Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_background_logger);
                Notification notification = new Notification.Builder(getApplicationContext())
                        .setChannelId(CHANNEL_ONE_ID)
                        .setContentTitle("Recording data")
                        .setContentText("activity logger is collecting data")
                        .setSmallIcon(R.drawable.ic_background_logger)
                        .setLargeIcon(icon)
                        .build();

                Intent notificationIntent = new Intent(getApplicationContext(),MainActivity.class);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                notification.contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);

                startForeground(101, notification);
            } else {
                startForeground(101, updateNotification());
            }
        } else {
            Intent notificationIntent = new Intent(this, MainActivity.class);

            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    notificationIntent, 0);

            Notification notification = new NotificationCompat.Builder(this)
                    .setContentTitle("Recording data")
                    .setContentText("activity logger is collecting data")
                    .setContentIntent(pendingIntent).build();

            startForeground(101, notification);
        }
    }

    private Notification updateNotification() {

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        return new NotificationCompat.Builder(this)
                .setContentTitle("Recording data")
                .setContentText("activity logger is collecting data")
                .setSmallIcon(R.drawable.ic_background_logger)
                .setContentIntent(pendingIntent)
                .setOngoing(true).build();
    }

    private void initializeSQLCipher() {
        SQLiteDatabase.loadLibs(this);
    }
    @SuppressLint("WrongConstant")
    private void initializeHandler() {
        handler = new Handler();
        printForeground = new printForegroundTask();

        currentlyRunningApp = "";
        runningApp = "x";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            usm = (UsageStatsManager) this.getSystemService("usagestats");
        }else{
            am = (ActivityManager)this.getSystemService(getApplicationContext().ACTIVITY_SERVICE);
        }
    }

    private void initializeBroadcastReceivers() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction() != null){
                    switch (intent.getAction()){
                        case Intent.ACTION_SCREEN_OFF:
                            storeData("screen off");
                            handler.removeCallbacks(documentForegroundTask);
                            break;
                        case Intent.ACTION_SCREEN_ON:
                            storeData("screen on");
                            handler.postDelayed(documentForegroundTask, 1000);
                            break;
                        case Intent.ACTION_USER_PRESENT:
                            storeData("user present");
                            break;
                    }
                }
            }


            final Runnable documentForegroundTask = new Runnable() {
                @Override
                public void run() {
                    String appRunningInForeground;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        appRunningInForeground = printForeground.identifyForegroundTaskLollipop(usm, getApplicationContext());
                    } else {
                        appRunningInForeground = printForeground.identifyForegroundTaskUnderLollipop(am);
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        if (Objects.equals(appRunningInForeground, "usage architecture")) {
                            informMain("", false);
                        }
                        if (!Objects.equals(appRunningInForeground, currentlyRunningApp)) {
                            storeData("App: " + appRunningInForeground);
                            currentlyRunningApp = appRunningInForeground;
                        }
                    } else {
                        if(appRunningInForeground == "usage architecture"){
                            informMain("", false);
                        }
                        if (appRunningInForeground == currentlyRunningApp) {
                            storeData("App: " + appRunningInForeground);
                            currentlyRunningApp = appRunningInForeground;
                        }
                    }
                    handler.postDelayed(documentForegroundTask, 1000);

                }
            };
        };

        IntentFilter screenReceiverFilter = new IntentFilter();
        screenReceiverFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenReceiverFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenReceiverFilter.addAction(Intent.ACTION_USER_PRESENT);

        registerReceiver(screenReceiver, screenReceiverFilter);

    }

    private void initializeBroadcastReceiversWithUsageCapabilities() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction() != null){
                    switch (intent.getAction()){
                        case Intent.ACTION_SCREEN_OFF:
                            storeData("screen off");
                            break;
                        case Intent.ACTION_SCREEN_ON:
                            storeData("screen on");
                            break;
                        case Intent.ACTION_USER_PRESENT:
                            storeData("user present");
                            break;
                    }
                }
            }
        };

        IntentFilter screenReceiverFilter = new IntentFilter();
        screenReceiverFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenReceiverFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenReceiverFilter.addAction(Intent.ACTION_USER_PRESENT);

        registerReceiver(screenReceiver, screenReceiverFilter);
    }

    private void storeData(String event) {
        SQLiteDatabase database = BackgroundLoggingSQL.getInstance(this).getWritableDatabase(prefs.getString("password", "not to be used"));

        final long time = System.currentTimeMillis();

        ContentValues values = new ContentValues();
        values.put(BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.EVENT, event);
        values.put(BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.TIME, time);

        database.insert(BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.TABLE_NAME, null, values);

        Cursor cursor = database.rawQuery("SELECT * FROM '" + BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.TABLE_NAME + "';", null);
        Log.d("BackgroundLogging", "Update: " + event + " " + time);
        cursor.close();
        database.close();
        informMain("event", false);
    }

    private void informMain(String message, boolean error) {
        if(prefs.getBoolean("main in foreground",true)){
            if(!error){
                Intent intent = new Intent("changeInService");
                intent.putExtra("dataToReceive", true);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                Log.i("service", "data sent to main");
            }else {
                Intent intent = new Intent("changeInService");
                intent.putExtra("dataToDisplay", true);
                intent.putExtra("dataToRelay", "error detected: " + message);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                Log.i("service", "data sent to main");
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(screenReceiver);
    }
}

