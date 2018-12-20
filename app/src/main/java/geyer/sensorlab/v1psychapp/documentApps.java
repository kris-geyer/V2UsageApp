package geyer.sensorlab.v1psychapp;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
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

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class documentApps extends AsyncTask<Object, Integer, Integer> {

    private static final String TAG = "D_APPS";

    private Context mContextApps;
    private SharedPreferences appPrefs, appPrefsToDelete;
    private SharedPreferences.Editor appEditor;
    private MainActivity mainActivityContext;
    private int levelOfAnalysis;

    private PackageManager pm;
    // you may separate this or combined to caller class.

    documentApps(MainActivity delegate) {
        this.delegate = (asyncResponse) delegate;
    }

    private asyncResponse delegate = null;

    /**
     *Objects:
     * 1 - context
     * 2 - shared prefs
     */

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected Integer doInBackground(Object... objects) {
        initializeObjects(objects);
        if(false){
            insertAppsAndPermissions(recordInstalledApps());
        }else{
            storeAppRecordsInSQL(recordInstalledApps());
        }

        SQLiteDatabase db = AppsSQL.getInstance(mContextApps).getReadableDatabase(appPrefs.getString("password", "not to be used"));
        String selectQuery = "SELECT * FROM " + AppsSQLCols.AppsSQLColsNames.TABLE_NAME;
        Cursor c = db.rawQuery(selectQuery, null);
        c.moveToLast();
        int length = c.getCount();
        c.close();
        Log.i(TAG, "table size: " + c.getCount());
        if(length >0){
            return 1;
        }else{
            return 2;
        }
    }

    private void storeAppRecordsInSQL(HashMap<String, ArrayList> appPermsList) {
        //initialize the SQL cipher
        SQLiteDatabase.loadLibs(mContextApps);
        SQLiteDatabase database = AppsSQL.getInstance(mainActivityContext).getWritableDatabase(appPrefs.getString("password", "not to be used"));
        //start loop that adds each app name, if it is installed, permission, approved or not, time

        final long time = System.currentTimeMillis();
        final int numApps = appPermsList.size();

        AtomicInteger progress = new AtomicInteger();
        for (Map.Entry<String, ArrayList> item : appPermsList.entrySet()) {


            ContentValues values = new ContentValues();
            String app = item.getKey();
            ArrayList permission= item.getValue();

            //if there are permissions
            if(permission.size() > 0){
                for (int i = 0; i < permission.size(); i++){
                    //do something with the permissions

                    String[] currentPermissionSplit = ((String) permission.get(i)).split("\\$");
                    Log.i(TAG, "split string: " + currentPermissionSplit[0]);
                    Log.i(TAG, "split string: " + currentPermissionSplit[1]);
                    values.put(AppsSQLCols.AppsSQLColsNames.APP, app);
                    values.put(AppsSQLCols.AppsSQLColsNames.INSTALLED, "installed");
                    values.put(AppsSQLCols.AppsSQLColsNames.PERMISSION, currentPermissionSplit[0]);
                    values.put(AppsSQLCols.AppsSQLColsNames.APPROVED, currentPermissionSplit[1]);
                    values.put(AppsSQLCols.AppsSQLColsNames.TIME, time);

                    database.insert(AppsSQLCols.AppsSQLColsNames.TABLE_NAME, null, values);
                    Cursor cursor = database.rawQuery("SELECT * FROM '" + AppsSQLCols.AppsSQLColsNames.TABLE_NAME + "';", null);

                    cursor.close();
                }
            }else{
                values.put(AppsSQLCols.AppsSQLColsNames.APP, app);
                values.put(AppsSQLCols.AppsSQLColsNames.INSTALLED, "installed");
                values.put(AppsSQLCols.AppsSQLColsNames.PERMISSION, "no permissions");
                values.put(AppsSQLCols.AppsSQLColsNames.APPROVED, "false");
                values.put(AppsSQLCols.AppsSQLColsNames.TIME, time);

                database.insert(AppsSQLCols.AppsSQLColsNames.TABLE_NAME, null, values);
                Cursor cursor = database.rawQuery("SELECT * FROM '" + AppsSQLCols.AppsSQLColsNames.TABLE_NAME + "';", null);

                cursor.close();
            }

            /**
             * Experiment with what can be taken out of the loop in from the below four lines of code
             */


            int currentProgress = (progress.incrementAndGet() * 100) / numApps;
            publishProgress(currentProgress);
        }
        database.close();

        Log.d(TAG, "SQL attempted to document apps");

        //stop looping
    }


    private void initializeObjects(Object[] objects) {
        mContextApps = (Context) objects[0];
        appPrefs = mContextApps.getSharedPreferences("app initialization prefs",Context.MODE_PRIVATE);
        appPrefsToDelete = mContextApps.getSharedPreferences("app prefs to update", Context.MODE_PRIVATE);
        mainActivityContext = (MainActivity) objects[1];
        levelOfAnalysis = (Integer) objects[2];
        appEditor = appPrefsToDelete.edit();
        appEditor.apply();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private HashMap<String, ArrayList> recordInstalledApps() {
        HashMap<String, ArrayList> appPermissions = new HashMap<>();

        pm = mContextApps.getPackageManager();
        final List<PackageInfo> appInstall= pm.getInstalledPackages(PackageManager.GET_PERMISSIONS|PackageManager.GET_RECEIVERS|
                PackageManager.GET_SERVICES|PackageManager.GET_PROVIDERS);

        for(PackageInfo pInfo:appInstall) {
            String[] reqPermission = pInfo.requestedPermissions;
            int[] reqPermissionFlag = pInfo.requestedPermissionsFlags;

            if(levelOfAnalysis > 2){
                ArrayList<String> permissions = new ArrayList<>();
                if (reqPermission != null){
                    for (int i = 0; i < reqPermission.length; i++){
                        String tempPermission = reqPermission[i];
                        int tempPermissionFlag = reqPermissionFlag[i];
                        boolean approved = tempPermissionFlag == 3;
                        permissions.add(tempPermission + " $ " + approved);
                    }
                }
                Log.i("app", (String) pInfo.applicationInfo.loadLabel(pm));
                appPermissions.put(""+pInfo.applicationInfo.loadLabel(pm), permissions);
            }else{
                ArrayList<String> permissions = new ArrayList<>();
                if (reqPermission != null){
                    for (int i = 0; i < reqPermission.length; i++){
                        String tempPermission = reqPermission[i];
                        permissions.add("*&^"+tempPermission);
                    }
                }
                Log.i("app", (String) pInfo.applicationInfo.loadLabel(pm));
                appPermissions.put(""+pInfo.applicationInfo.loadLabel(pm), permissions);
            }
        }
        return appPermissions;
    }

    private Boolean insertAppsAndPermissions(HashMap<String, ArrayList> stringArrayListHashMap) {
        //creates document
        Document document = new Document();
        //getting destination
        File path = mContextApps.getFilesDir();
        File file = new File(path, Constants.APPS_AND_PERMISSIONS_FILE);
        // Location to save
        PdfWriter writer = null;
        try {
            writer = PdfWriter.getInstance(document, new FileOutputStream(file));
        } catch (DocumentException e) {
            Log.e("Main", "document exception: " + e);
        } catch (FileNotFoundException e) {
            Log.e("Main", "File not found exception: " + e);
        }
        try {
            if (writer != null) {
                writer.setEncryption("concretepage".getBytes(), appPrefs.getString("pdfPassword", "sensorlab").getBytes(), PdfWriter.ALLOW_COPY, PdfWriter.ENCRYPTION_AES_128);
            }
        } catch (DocumentException e) {
            Log.e("Main", "document exception: " + e);
        }
        if (writer != null) {
            writer.createXmpMetadata();
        }
        // Open to write
        document.open();

        PdfPTable table = new PdfPTable(1);
        //attempts to add the columns
        try {
            int count = 0;
            switch(levelOfAnalysis){
                case 1:
                    for (Map.Entry<String, ArrayList> item : stringArrayListHashMap.entrySet()) {
                        String key = item.getKey();
                        pushAppIntoPrefs(key);
                        table.addCell("£$$"+key);
                        count++;
                        int currentProgress = (count * 100) / stringArrayListHashMap.size();
                        publishProgress(currentProgress);
                    }
                    break;
                case 2:
                    for (Map.Entry<String, ArrayList> item : stringArrayListHashMap.entrySet()) {
                        String key = item.getKey();
                        pushAppIntoPrefs(key);
                        table.addCell("£$$"+key);
                        ArrayList value = item.getValue();
                        for (int i = 0; i < value.size(); i++){
                            table.addCell("$££"+value.get(i));
                        }
                        count++;
                        int currentProgress = (count * 100) / stringArrayListHashMap.size();
                        publishProgress(currentProgress);
                    }
                    break;
                case 3:
                    for (Map.Entry<String, ArrayList> item : stringArrayListHashMap.entrySet()) {
                        String key = item.getKey();
                        pushAppIntoPrefs(key);
                        table.addCell("£$$"+key);
                        ArrayList value = item.getValue();
                        for (int i = 0; i < value.size(); i++){
                            table.addCell("$££"+value.get(i));
                        }
                        count++;
                        int currentProgress = (count * 100) / stringArrayListHashMap.size();
                        publishProgress(currentProgress);
                    }
                    break;
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
            Log.e("App reader", "Document exception: " + e);
        }
        document.addAuthor("Kris");
        document.close();

        String directory = (String.valueOf(mContextApps.getFilesDir()) + File.separator);
        File backgroundLogging = new File(directory + File.separator + Constants.APPS_AND_PERMISSIONS_FILE);
        Log.i("prefs", String.valueOf(appPrefs.getInt("number of apps",0)));
        return backgroundLogging.exists();
    }

    private void pushAppIntoPrefs(String key) {
        appEditor.putString("app" + appPrefsToDelete.getInt("number of apps", 0), key)
                .putInt("number of apps",(appPrefsToDelete.getInt("number of apps", 0)+1)).apply();
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        Log.i("Main", "Progress update: " + values[0]);
        //set up sending local signal to update main activity

        informMain(values[0]);
    }

    private void informMain(int progressBarValue) {
            Intent intent = new Intent("changeInService");
            intent.putExtra("progress bar update", true);
            intent.putExtra("progress bar progress", progressBarValue);
            LocalBroadcastManager.getInstance(mainActivityContext).sendBroadcast(intent);
            Log.i("service", "data sent to main");
    }

    @Override
    protected void onPostExecute(Integer integer) {
        super.onPostExecute(integer);
        delegate.processFinish(integer);
    }
}
