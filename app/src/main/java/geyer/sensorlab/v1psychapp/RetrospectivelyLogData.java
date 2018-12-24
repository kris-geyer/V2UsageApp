package geyer.sensorlab.v1psychapp;

import android.annotation.SuppressLint;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RetrospectivelyLogData extends AsyncTask<Object, Integer, Integer> {

    private static final String TAG = "rLOG-DATA";



    private Context context;
    private UsageStatsManager usm;
    private PackageManager pm;
    private int NUMBER_OF_DAYS_BACK_TO_MEASURE_DURATION_OF_USE,
    DURATION_OF_BINS_OF_DURATION_IN_DAYS,
    TIME_SAMPLED_FOR_EVENTS_OF_USAGE;
    private SharedPreferences prefs;
    private long startDate;
    private asyncResponse delegate;

    RetrospectivelyLogData(asyncResponse delegate) {
        this.delegate = delegate;
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected Integer doInBackground(Object... objects) {

        Log.i(TAG, "upload history operations called");

        initialization(objects);
        Boolean queryUsageStatistics = (Integer)objects[4] == 1,
        queryUseageEvents = (Integer)objects[5] == 1;
        if(queryUsageStatistics){
            documentStatistic(recordUsageStatistics());
        }
        if(queryUseageEvents){
            documentEvents(recordUsageEvent());
        }
        final Boolean documentedPastUsage = fileExists(Constants.PAST_USAGE_FILE);
        final Boolean documentedPastEvents = fileExists(Constants.PAST_EVENTS_FILE);
        if(documentedPastUsage && ! documentedPastEvents){
            return 7;
        }else if(!documentedPastUsage && documentedPastEvents){
            return 8;
        }else if(!documentedPastUsage && !documentedPastEvents){
            return 9;
        }else{
            return 10;
        }
    }

    private boolean fileExists(String file) {
        String directory = (String.valueOf(context.getFilesDir()) + File.separator);
        File inspectedFile = new File(directory + File.separator + file);
        return inspectedFile.exists();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("WrongConstant")
    private void initialization(Object[] objects) {
        context = (Context) objects[0];
        NUMBER_OF_DAYS_BACK_TO_MEASURE_DURATION_OF_USE = (Integer) objects[1];
        DURATION_OF_BINS_OF_DURATION_IN_DAYS = (Integer) objects[2];
        TIME_SAMPLED_FOR_EVENTS_OF_USAGE = (Integer) objects[3];

        prefs = context.getSharedPreferences("app initialization prefs", Context.MODE_PRIVATE);
        usm = (UsageStatsManager) context.getSystemService("usagestats");
        pm = context.getPackageManager();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private LinkedHashMap<Integer, HashMap<Long, String>> recordUsageStatistics() {

        List<UsageStats> appStatistics;
        Map<String, UsageStats> appStatisticsNew;
        int dataPoint = 0;

        //set calendar to the beginning of when the usage is intended to be monitored
        Calendar calendar = initializeCalendar();

        //initialize what will contain the app Usage
        LinkedHashMap<Integer, HashMap<Long, String>> orderedAppUsage = new LinkedHashMap<>();

        //start loop, so that if the start of the bin is in the future then stop documenting the usage
        if (usm != null) {

            int dayCount = 0;
            final long currentTime = System.currentTimeMillis();
            final long thisTimeTomorrow = currentTime + (86400 * 1000);
            while (calendar.getTimeInMillis() < thisTimeTomorrow) {

                calendar = makeMorning(calendar);
                long startOfDay = calendar.getTimeInMillis();
                String currentDayOFWeek = returnDayOfWeek(calendar.get(Calendar.DAY_OF_WEEK));

                calendar = makeEvening(calendar);

                long endOfDay = calendar.getTimeInMillis();
                String stillCurrentDayOFWeek = returnDayOfWeek(calendar.get(Calendar.DAY_OF_WEEK));

                calendar.add(Calendar.DAY_OF_YEAR, 1);
                if (endOfDay > currentTime) {
                    endOfDay = currentTime;
                }

                documentDay(startOfDay, currentDayOFWeek, endOfDay, stillCurrentDayOFWeek, ++dayCount);

                long totalUsage = 0;

                //appStatistics = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, endOfDay);

                appStatisticsNew = usm.queryAndAggregateUsageStats(startOfDay, endOfDay);


                if(appStatisticsNew != null && appStatisticsNew.size() >0){
                    Iterator appStatisticsEntry = appStatisticsNew.entrySet().iterator();

                    while (appStatisticsEntry.hasNext()) {
                        Map.Entry pair = (Map.Entry) appStatisticsEntry.next();
                        UsageStats appUsageStat = (UsageStats) pair.getValue();
                        long timeAppWasInForeground  = appUsageStat.getTotalTimeInForeground();
                        totalUsage += timeAppWasInForeground;
                        if (appUsageStat.getFirstTimeStamp() < startDate) {
                            startDate = appUsageStat.getFirstTimeStamp();
                        }
                        appStatisticsEntry.remove();
                    }


                    HashMap<Long, String> dateHashMap = new HashMap<>();
                    dateHashMap.put(totalUsage, startOfDay + " - " + endOfDay);
                    orderedAppUsage.put(++dataPoint, dateHashMap);

                    appStatisticsNew = usm.queryAndAggregateUsageStats(startOfDay, endOfDay);

                    Iterator secondAppStatisticsEntry = appStatisticsNew.entrySet().iterator();

                    while (secondAppStatisticsEntry.hasNext()) {

                        Map.Entry pair = (Map.Entry) secondAppStatisticsEntry.next();
                        UsageStats appUsageStat = (UsageStats) pair.getValue();
                        long timeAppWasInForeground  = appUsageStat.getTotalTimeInForeground();
                        String appUsed = (String) pair.getKey();
                        HashMap<Long, String> hashMapToEnter = new HashMap<>();
                        Log.i("record", "app: " + appUsed + " time: " + timeAppWasInForeground);

                        hashMapToEnter.put(timeAppWasInForeground, appUsed);
                        orderedAppUsage.put(++dataPoint, hashMapToEnter);

                        secondAppStatisticsEntry.remove();
                    }


                }


                /*
                //if apps aren't null
                if (appStatistics != null && appStatistics.size() > 0) {
                    //set the total usage to null

                    //for each documented stats in the stats
                    HashMap<Long, String> appUsage = new HashMap<>();
                    for (UsageStats usageStats : appStatistics) {

                        long timeInForeground = usageStats.getTotalTimeInForeground() / 1000;
                        //add the time that the analysed app was in the foreground to the int totalUsage
                        totalUsage += timeInForeground;
                        ////put the app usage

                        appUsage.put(timeInForeground, usageStats.getPackageName());
                        Log.i(TAG, "APP: " + usageStats.getPackageName());

                        if (usageStats.getFirstTimeStamp() < startDate) {
                            startDate = usageStats.getFirstTimeStamp();
                        }
                    }
                    */
                    /**
                     * Generate a method for listing the values in order of duration employed
                     */
                    /*
                    HashMap<Long, String> dateHashMap = new HashMap<>();
                    dateHashMap.put(totalUsage, startOfDay + " - " + endOfDay);
                    orderedAppUsage.put(++dataPoint, dateHashMap);

                    Iterator it = appUsage.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry pair = (Map.Entry) it.next();
                        HashMap<Long, String> toEnter = new HashMap<>();
                        toEnter.put((Long) pair.getKey(), (String) pair.getValue());
                        orderedAppUsage.put(++dataPoint, toEnter);
                        it.remove();
                    }
                    */
                }
                return orderedAppUsage;
            }
            return orderedAppUsage;
        }


    private Calendar initializeCalendar() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -NUMBER_OF_DAYS_BACK_TO_MEASURE_DURATION_OF_USE);

        Log.i("initialize", ""+calendar.getTimeInMillis());

        return calendar;
    }

    /**
     * No idea why this is the sums required to access midnight and the end of the day, but that's what android wants.
     * @param calendar
     * @return
     */
    private Calendar makeMorning(Calendar calendar){
        calendar.set(Calendar.HOUR, -12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private Calendar makeEvening(Calendar calendar){
        calendar.set(Calendar.HOUR, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private void documentDay(long startOfDay, String currentDayOfWeek, long endOfDay, String stillCurrentdayOfWeek, int dayCount) {
        Log.i("day: " + dayCount, "start of" + currentDayOfWeek + ": " + startOfDay + " end of "+stillCurrentdayOfWeek +": " + endOfDay + " difference: " + (endOfDay-startOfDay));
    }

    private String returnDayOfWeek (int dayInt){
        switch (dayInt){
            case Calendar.SUNDAY:
                return "Sunday";
            case Calendar.MONDAY:
                return "Monday";
            case Calendar.TUESDAY:
                return "Tuesday";
            case Calendar.WEDNESDAY:
                return "Wednesday";
            case Calendar.THURSDAY:
                return "Thursday";
            case Calendar.FRIDAY:
                return "Friday";
            case Calendar.SATURDAY:
                return "Saturday";
            default:
                return "Error";
        }
    }

    private void documentStatistic(LinkedHashMap<Integer, HashMap<Long, String>> stats) {

        if(stats.size()>0){
            //creates document
            Document document = new Document();
            //getting destination
            File path = context.getFilesDir();
            File file = new File(path, Constants.PAST_USAGE_FILE);
            // Location to save
            PdfWriter writer = null;
            try {
                writer = PdfWriter.getInstance(document, new FileOutputStream(file));
            } catch (DocumentException e) {
                Log.e(TAG, "document exception: " + e);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "File not found exception: " + e);
            }
            try {
                if (writer != null) {
                    writer.setEncryption("concretepage".getBytes(), prefs.getString("pdfPassword", "sensorlab").getBytes(), PdfWriter.ALLOW_COPY, PdfWriter.ENCRYPTION_AES_128);
                }
            } catch (DocumentException e) {
                Log.e(TAG, "document exception: " + e);
            }
            if (writer != null) {
                writer.createXmpMetadata();
            }
            // Open to write
            document.open();

            PdfPTable table = new PdfPTable(2);
            //attempts to add the columns
            table.addCell("start date");
            table.addCell(String.valueOf(startDate));
            try {
                int count = 0;
                for (Map.Entry<Integer, HashMap<Long, String>> app : stats.entrySet()) {

                    HashMap<Long, String> toAddToDB = app.getValue();
                    Map.Entry<Long, String> entry = toAddToDB.entrySet().iterator().next();
                    table.addCell("££$" + entry.getKey());
                    table.addCell( "$$£"+ String.valueOf(entry.getValue()));
                    int currentProgress = (count * 100) / stats.size();
                    count++;
                    publishProgress(currentProgress);
                }
            }catch (Exception e) {
                Log.e("file construct", "error " + e);
            }

            //add to document
            document.setPageSize(PageSize.A4);
            document.addCreationDate();
            try {
                document.add(table);
            } catch (DocumentException e) {
                Log.e(TAG, "Document exception: " + e);
            }
            document.addAuthor("Kris");
            document.close();
        }else{
            Log.i(TAG, "no data in passed stats value");
        }


    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private HashMap<Long, HashMap<String, Integer>> recordUsageEvent() {
        HashMap<Long, HashMap<String, Integer>>CompleteRecord = new HashMap<>();
        UsageEvents usageEvents = null;

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -TIME_SAMPLED_FOR_EVENTS_OF_USAGE);
        long start = calendar.getTimeInMillis();

        if (usm != null) {
            usageEvents =  usm.queryEvents(start, System.currentTimeMillis());
        }else{
            Log.e(TAG, "usm equals null");
        }

        ArrayList <String> databaseEvent = new ArrayList<>();
        ArrayList<Long> databaseTimestamp = new ArrayList<>();
        ArrayList <Integer> databaseEventType = new ArrayList<>();

        int count = 0;
        if (usageEvents != null) {
            while(usageEvents.hasNextEvent()){
                //Log.i(TAG, "number: " + count);
                UsageEvents.Event e = new UsageEvents.Event();
                usageEvents.getNextEvent(e);
                count++;
            }

            usageEvents =  usm.queryEvents(start, System.currentTimeMillis());


            int newCount = 0;
            while(usageEvents.hasNextEvent()){

                UsageEvents.Event e = new UsageEvents.Event();
                usageEvents.getNextEvent(e);
                HashMap<String , Integer> uninstalledApps = new HashMap<>();

                try {
                    String appName = (String) pm.getApplicationLabel(pm.getApplicationInfo(e.getPackageName(), PackageManager.GET_META_DATA));
                    appName = appName.replace(" ", "-");
                    databaseEvent.add(appName);
                    //Log.i("app name", appName );
                } catch (PackageManager.NameNotFoundException e1) {

                    String packageName = e.getPackageName();
                    packageName = packageName.replace(" ", "-");
                    if(uninstalledApps.containsKey(packageName)){
                        databaseEvent.add("app" +uninstalledApps.get(packageName));
                    }else{
                        databaseEvent.add("app"+uninstalledApps.size());
                        uninstalledApps.put(packageName, uninstalledApps.size());
                    }
                    Log.e("usageHistory","Error in identify package name: " + e);
                }

                databaseTimestamp.add(e.getTimeStamp());
                databaseEventType.add(e.getEventType());

                newCount++;
                publishProgress((newCount*100)/count);
                if(newCount%100==0){
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }


                for (int i = 0; i <databaseEvent.size(); i ++){
                    HashMap<String, Integer>eventAndEventType = new HashMap<>();
                    eventAndEventType .put(databaseEvent.get(i), databaseEventType.get(i));
                    CompleteRecord.put(databaseTimestamp.get(i), eventAndEventType);
                }
            }

        }else{
            Log.e(TAG, "usage event equals null");
        }
        return CompleteRecord;
    }

    private void documentEvents(HashMap<Long, HashMap<String, Integer>> stringHashMapHashMap) {
        //creates document
        Document document = new Document();
        //getting destination
        File path = context.getFilesDir();
        File file = new File(path, Constants.PAST_EVENTS_FILE);
        // Location to save
        PdfWriter writer = null;
        try {
            writer = PdfWriter.getInstance(document, new FileOutputStream(file));
        } catch (DocumentException e) {
            Log.e(TAG, "document exception: " + e);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found exception: " + e);
        }
        try {
            if (writer != null) {
                writer.setEncryption("concretepage".getBytes(), prefs.getString("pdfPassword", "sensorlab").getBytes(), PdfWriter.ALLOW_COPY, PdfWriter.ENCRYPTION_AES_128);
            }
        } catch (DocumentException e) {
            Log.e(TAG, "document exception: " + e);
        }
        if (writer != null) {
            writer.createXmpMetadata();
        }
        // Open to write
        document.open();

        PdfPTable table = new PdfPTable(3);
        //attempts to add the columns
        try {
            int rowSize = stringHashMapHashMap.size();
            int currentRow = 0;
            for (Long key: stringHashMapHashMap.keySet()){
                HashMap<String, Integer> toStore = stringHashMapHashMap.get(key);
                //timestamp
                table.addCell("@€£" + key);

                for (String nestedKey : toStore.keySet()){
                    //database event
                    table.addCell("#£$" + nestedKey);
                    //database type
                    table.addCell("^&*" + toStore.get(nestedKey));
                }
                if(currentRow %10 ==0){
                    int currentProgress = (currentRow * 100) / rowSize;
                    publishProgress(currentProgress);
                }

            }
        } catch (Exception e) {
            Log.e("file construct", "error " + e);
        }

        //add to document
        document.setPageSize(PageSize.A4);
        document.addCreationDate();
        try {
            document.add(table);
        } catch (DocumentException e) {
            Log.e(TAG, "Document exception: " + e);
        }
        document.addAuthor("Kris");
        document.close();
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        //Log.i("Main", "Progress update: " + values[0]);
        //set up sending local signal to update main activity

        informMain(values[0]);
    }

    private void informMain(int progressBarValue) {
        Intent intent = new Intent("changeInService");
        intent.putExtra("progress bar update", true);
        intent.putExtra("progress bar progress", progressBarValue);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Override
    protected void onPostExecute(Integer integer) {
        super.onPostExecute(integer);
        delegate.processFinish(integer);
    }

}