package geyer.sensorlab.v1psychapp;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.util.Log;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.spongycastle.util.Pack;

import java.io.File;

@RequiresApi(api = Build.VERSION_CODES.KITKAT)
class DirectApplicationInitialization {

    private static final String TAG = "DAI";

    private Boolean informUserRequired,
            passwordRequired,
            requestUsagePermission,
            requestNotificationPermission,
            performCrossSectionalAnalysis,
            prospectiveLoggingEmployed,
            retrospectiveLoggingEmployed,
            useUsageStatistics,
            useUsageEvents;


    private int levelOfCrossSectionalAnalysis,
            levelOfProspectiveLogging,
            numberOfDaysForUsageStats,
            intervalOfDaysGenerated,
            numberOfDaysForUsageEvents;

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    private AppOpsManager appOpsManager;
    private ActivityManager manager;

    private String pkg;
    private MainActivity mainActivityContext;

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    DirectApplicationInitialization(
            //basic initialization direction
            Boolean InformUserRequired, Boolean PasswordRequired,
            //permission requisition directions
            Boolean RequestUsagePermission, Boolean RequestNotificationPermission,
            //direction of temporal focus for behavioural logging
            Boolean RetrospectiveLoggingEmployed, Boolean PerformCrossSectionalAnalysis, Boolean ProspectiveLoggingEmployed,
            //direction for the retrospective logging
                //direction for usage statistics
                Boolean UseUsageStatics , int NumberOfDaysForUsageStats, int IntervalOfDaysGenerated,
                //directions for the usage events
                Boolean UseUsageEvents, int NumberOfDaysForUsageEvents,
            //direction for cross sectional logging
            int LevelOfCrossSectionalAnalysis,
            //direction for prospective logging
            int LevelOfProspectiveLogging,
            //components required from the activity
             Object AppOpsService, Object ActivityService, String PackageName, MainActivity MainActivityContext
        )
    {
        /**
         * initialization of the values
         */
        informUserRequired = InformUserRequired;
        passwordRequired = PasswordRequired;
        /**
         * If data being requested is beyond the capabilities of the data generated then it will be altered
         */
        if(ProspectiveLoggingEmployed){
            switch (LevelOfProspectiveLogging){
                case 1:
                    requestUsagePermission = RequestUsagePermission;
                    requestNotificationPermission = RequestNotificationPermission;
                    break;
                case 2:
                    requestUsagePermission = true;
                    requestNotificationPermission = RequestNotificationPermission;
                    break;
                case 3:
                    requestUsagePermission = RequestUsagePermission;
                    requestNotificationPermission = true;
                    break;
                case 4:
                    requestUsagePermission = true;
                    requestNotificationPermission = true;
                    break;
            }
        }else{
            requestUsagePermission = RequestUsagePermission;
            requestNotificationPermission = RequestNotificationPermission;
        }

        performCrossSectionalAnalysis = PerformCrossSectionalAnalysis;
        levelOfCrossSectionalAnalysis = LevelOfCrossSectionalAnalysis;
        prospectiveLoggingEmployed = ProspectiveLoggingEmployed;
        levelOfProspectiveLogging = LevelOfProspectiveLogging;
        retrospectiveLoggingEmployed = RetrospectiveLoggingEmployed;
        if(retrospectiveLoggingEmployed){
            requestUsagePermission = true;
        }

        useUsageStatistics = UseUsageStatics;
        numberOfDaysForUsageStats = NumberOfDaysForUsageStats;
        intervalOfDaysGenerated = IntervalOfDaysGenerated;

        useUsageEvents = UseUsageEvents;
        numberOfDaysForUsageEvents = NumberOfDaysForUsageEvents;

        appOpsManager= (AppOpsManager) AppOpsService;
        manager = (ActivityManager) ActivityService;
        pkg = PackageName;
        mainActivityContext = MainActivityContext;

        sharedPreferences = mainActivityContext.getSharedPreferences("app initialization prefs", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
        editor.apply();
    }

    /**
     * States:
     * 1 - inform user
     * 2 - request password
     * 3 - document apps & permissions
     * 4 - request usage permission
     * 5 - request notification permission
     * 6 - All permission provided
     * 7 - retrospectively log data
     * 8 - retrospective data generating complete
     * 9 - start Service
     * 10 - start NotificationListenerService
     * 11 - service running
     *
     */

     int detectState(){
        int state = 1;

        if(sharedPreferences.getBoolean("instructions shown", false) || !informUserRequired){
            state = 2;
            if(sharedPreferences.getBoolean("password generated", false) || !passwordRequired){

                    if(appDatabaseExists() || !performCrossSectionalAnalysis){
                        state = detectPermissionsState();
                        if(state == 6){
                            if(retrospectiveLoggingEmployed){
                                //change below line to: !fileExists(Constants.PAST_USAGE_FILE
                                if(true) {
                                    return 7;
                                }else{
                                     if(prospectiveLoggingEmployed) {
                                        return returnStateOfService();
                                     }
                                }
                            }else{
                                if(prospectiveLoggingEmployed) {
                                    return returnStateOfService();
                                }
                            }
                        }
                    }else{
                        state = 3;
                    }
            }
        }
        return state;
    }

    private int returnStateOfService() {
        if(requestNotificationPermission){
            if(serviceIsRunning(notificationLogger.class)){
                return 11;
            }else{
                return 10;
            }
        }else{
            if(serviceIsRunning(logger.class)){
                return 11;
            }else{
                return 9;
            }
        }
    }

    private boolean appDatabaseExists() {
        SQLiteDatabase db = AppsSQL.getInstance(mainActivityContext).getReadableDatabase(sharedPreferences.getString("password", "not to be used"));
        String selectQuery = "SELECT * FROM " + AppsSQLCols.AppsSQLColsNames.TABLE_NAME;
        Cursor c = db.rawQuery(selectQuery, null);
        c.moveToLast();
        Log.i(TAG, "table size: " + c.getCount());
        int length = c.getCount();
        c.close();
        if(db.isOpen()) {db.close();}
        return length > 0;
    }

    private boolean fileExists(String file) {
        String directory = (String.valueOf(mainActivityContext.getFilesDir()) + File.separator);
        File inspectedFile = new File(directory + File.separator + file);
        return inspectedFile.exists();
    }

    //This establishes what permission is required based on what the researchers have indicated that they wish to include in their study.
    private int detectPermissionsState() {

        Boolean permissionsStillRequired = requestUsagePermission || requestNotificationPermission;

        if(permissionsStillRequired){
            if(!requestNotificationPermission){
                if(establishStateOfUsageStatisticsPermission()){
                    Log.i(TAG, "usage statistics permission granted");
                    return 6;
                }else{
                    return 4;
                }
            }else if(!requestUsagePermission){
                if(establishStateOfNotificationListenerPermission()){
                    Log.i(TAG, "notification listener permission granted");
                    return 6;
                }else{
                    return 5;
                }
            }else{
                if(establishStateOfUsageStatisticsPermission()){
                    if(establishStateOfNotificationListenerPermission()){
                        Log.i(TAG, "all permissions permission granted");
                        return 6;
                    }else{
                        return 5;
                    }
                }else{
                    return 4;
                }
            }
        }else{
            return 6;
        }

    }

    //establishes if the usage statistics permissions are provided
    private Boolean establishStateOfUsageStatisticsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int mode = 2;
            if (appOpsManager != null) {
                mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), String.valueOf(pkg));
            }
            return (mode == AppOpsManager.MODE_ALLOWED);
        }else{
            return true;
        }
    }

    private boolean establishStateOfNotificationListenerPermission() {
        ComponentName cn = new ComponentName(mainActivityContext, notificationLogger.class);
        String flat = Settings.Secure.getString(mainActivityContext.getContentResolver(), "enabled_notification_listeners");
        return flat == null || flat.contains(cn.flattenToString());
    }

    //detects if the background logging behaviour is running, this will not detect if data is being collected.
    private boolean serviceIsRunning(Class<?> serviceClass) {
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * DIRECTING ACTION
     */


    /*
    WILL NEED TO IDENTIFY WHAT IS REQUIRED FOR DIFFERENT LEVELS OF ANALYSIS
     */
    public StringBuilder buildMessageToInformUser() {
        StringBuilder toRelay = new StringBuilder();
        toRelay.append("This app is intended to relay information about how much you use this smartphone" +"\n").
                append("\n").append("This app functions by ");
        if(prospectiveLoggingEmployed && !retrospectiveLoggingEmployed){
            toRelay.append("documenting how you use your phone by running a program in the background. ");
        }else if (!prospectiveLoggingEmployed && retrospectiveLoggingEmployed){
            toRelay.append("querying a database stored by android phones about what you have used your phone for previously. ");
        }else if(prospectiveLoggingEmployed && retrospectiveLoggingEmployed){
            toRelay.append("querying a database stored by android phones about what you have used your phone for previously and documenting how you use your phone by running a program in the background. ");
        }
        if(prospectiveLoggingEmployed){
            if(requestUsagePermission && !requestNotificationPermission){
                toRelay.append("What you use your smartphone for will be documented. ");
            }else if(!requestUsagePermission && requestNotificationPermission){
                toRelay.append("What notifications appear and which app called them will be documented as well as when you deleted them. ");
            }else if(requestUsagePermission && requestNotificationPermission){
                toRelay.append("What you use your smartphone for will be documented and ehat notifications appear and which app called them will be documented as well as when you deleted them. ");
            }

        }

        if(performCrossSectionalAnalysis){
            toRelay.append("The app will also seek to generate context by identify the current state of your phone. ");
                    switch (levelOfCrossSectionalAnalysis){
                        case 1:
                            toRelay.append("What apps are installed on your phone will be documented. ");
                            break;
                        case 2:
                            toRelay.append("What permissions are requested by the apps on your phone will be documented and your if you have granted this permission on not. ");
                            break;
                        case 3:
                            toRelay.append("What apps are installed on your phone will be documented as well as what permissions are requested and if you've granted access to the permission or not. ");
                            break;
                    }
        }

            toRelay.append("The phone has adopted a number of security measures to protect your privacy and data. ").append("\n").append("\n");
        if(passwordRequired){
            toRelay.append("Your data will be stored on at least a password protected 128-bit encryption.");
        }
        toRelay.append("You can delete all the data that hasn't been relayed to a researcher by simply uninstalling the app. The data will only be relayed once you press email and send the email that is generated with the data attached.");

        return toRelay;
    }



    public int returnAppDirectionValue(String temporalTarget){
        switch (temporalTarget){
            case "current":
                Log.i(TAG, "request received for level of cross sectional analysis");
                Log.i(TAG, "cross sectional analysis: " + levelOfCrossSectionalAnalysis);
                return levelOfCrossSectionalAnalysis;
            case "prospective":
                Log.i(TAG, "request received for level of prospective analysis");
                Log.i(TAG, "prospective analysis: " + levelOfProspectiveLogging);
                return levelOfProspectiveLogging;
            case "numberOfDaysForUsageStats":
                return numberOfDaysForUsageStats;
            case "intervalOfDaysGenerated":
                return intervalOfDaysGenerated;
            case "numberOfDaysForUsageEvents":
                return  numberOfDaysForUsageEvents;
            case "useUsageStatistics":
                if(useUsageStatistics){
                    return 1;
                }else{
                    return 0;
                }
            case "useUsageEvents":
                if(useUsageEvents){
                    return 1;
                }else{
                    return 0;
                }
            default:
                return 100;
        }
    }

    public Boolean returnIfAppDocumentingShouldOccur() {
        return performCrossSectionalAnalysis;
    }
}
