package geyer.sensorlab.v1psychapp;

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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class documentApps extends AsyncTask<Object, Integer, Integer> {
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected Integer doInBackground(Object... objects) {
        initializeObjects(objects);
        insertAppsAndPermissions(recordInstalledApps());
        if(fileExists(Constants.APPS_AND_PERMISSIONS_FILE)){
            return 1;
        }else{
            return 2;
        }
    }

    private Context mContextApps;
    private SharedPreferences appPrefs;
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

    private void initializeObjects(Object[] objects) {
        mContextApps = (Context) objects[0];
        appPrefs = mContextApps.getSharedPreferences("app initialization prefs",Context.MODE_PRIVATE);
        mainActivityContext = (MainActivity) objects[1];
        levelOfAnalysis = (Integer) objects[2];
        appEditor = appPrefs.edit();
        appEditor.apply();
    }

    private boolean fileExists(String file) {
        String directory = (String.valueOf(mainActivityContext.getFilesDir()) + File.separator);
        File inspectedFile = new File(directory + File.separator + file);
        return inspectedFile.exists();
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
                        permissions.add("*&^"+tempPermission + " - " + approved);
                    }
                }
                Log.i("app", (String) pInfo.applicationInfo.loadLabel(pm));
                appPermissions.put("!@€"+pInfo.applicationInfo.loadLabel(pm), permissions);
            }else{
                ArrayList<String> permissions = new ArrayList<>();
                if (reqPermission != null){
                    for (int i = 0; i < reqPermission.length; i++){
                        String tempPermission = reqPermission[i];
                        permissions.add("*&^"+tempPermission);
                    }
                }
                Log.i("app", (String) pInfo.applicationInfo.loadLabel(pm));
                appPermissions.put("!@€"+pInfo.applicationInfo.loadLabel(pm), permissions);
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
        appEditor.putString("app" + appPrefs.getInt("number of apps", 0), key)
                .putInt("number of apps",(appPrefs.getInt("number of apps", 0)+1)).apply();
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
