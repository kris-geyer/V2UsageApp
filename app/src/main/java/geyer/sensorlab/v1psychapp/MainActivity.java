package geyer.sensorlab.v1psychapp;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.content.FileProvider;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import net.sqlcipher.database.SQLiteDatabase;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, asyncResponse {

    PermissionRequestsAndCrossSectionalAnalysis prANDcsa;
    DirectApplicationInitialization dai;
    documentApps docApps;

    BroadcastReceiver localListener;
    ProgressBar progressBar;

    SharedPreferences prefs;
    SharedPreferences.Editor editor;

    private static final String TAG = "MAIN";

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeServiceStateListener();
        initializeInvisibleComponents();
        initializeClasses();
        initializeVisibleComponents();

        //direct action
        promptAction(dai.detectState());
    }

    private void initializeServiceStateListener() {

        localListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getBooleanExtra("dataFromService", false)){
                    Log.i("FROM service", "data collection on going");
                    //relay that service if functioning properly
                }
                if(intent.getBooleanExtra("errorFromService", false)){
                    final String msg = intent.getStringExtra("dataToRelay");
                    Log.i("FROM service", msg);
                    //change string value to msg
                }
                if(intent.getBooleanExtra("progress bar update", false)){
                    progressBar.setProgress(intent.getIntExtra("progress bar progress", 0));
                }
            }
        };
        LocalBroadcastManager.getInstance(MainActivity.this).registerReceiver(localListener, new IntentFilter("changeInService"));
    }

    private void initializeInvisibleComponents() {
        SQLiteDatabase.loadLibs(this);
        prefs = getSharedPreferences("app initialization prefs", MODE_PRIVATE);
        editor = prefs.edit();
        editor.putBoolean("main in foreground", true).apply();
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void initializeClasses() {
        //initializes the class tasked with detecting the stage of the application initialization.
        //Researcher at this point has to determine how the app will function. See mark up
        dai = new DirectApplicationInitialization(
                //is the app required to informed the user?
                true,
                //is the app required to prompt the user to provide password?
                /**
                 * If the researcher wishes to have the encryption of data then a password is required.
                 * Only edit this option if an alternative to the user providing the password is supplied.
                 *
                 * Please anticipate extensive problems with the functionality of the app if this option is not set to true
                 */
                true,
                //is the app required to prompt the participant to provides usage permission?
                true,
                //is the app required to request permission to listen to notifications?
                false,
                //is the app required to log previous events
                false,
                //is the app required that the app performs a cross sectional analysis?
                true,
                //is the app required to log data prospectively?
                true,
                /**
                 * Direction for the usage statics
                 */
                //is the app required to record usage statistics? Usage statistics documents the duration that an application was employed for a specific period.
                false,
                //how many days back should the usage statistics start to record the data?
                0,
                //what size of the bins should be developed for the duration of the apps?
                0,
                //should usage events be document? These are highly detailed accounts of what the smartphone was used for
                false,
                //How many days back should the app document usage events? The records for usage events is not held for a long period of time, so it is unlikely that the results will return more than a weeks worth of data.
                0,
                /*
                what level of the sophistication will the app engage with?
                0 - nothing
                1 - document apps
                2 - document apps and permissions
                3 - document apps and permissions and user's response to permissions
                (performCrossSectionalAnalysis required to be true for function)
                */
                3,
                /*
                What is the level of the prospective data required?
                1 - basic screen usage
                2 - foreground logging
                3 - notification listening
                4 - foreground and notification listening
                 */
                /**
                 * If the level is above 1 then the app requires the usage statistics permission, notification listening permission or both.
                 * This will need to be done manually!!!
                 */
                1,
                //requirements for the class to operate
                getSystemService(Context.APP_OPS_SERVICE),
                getSystemService(Context.ACTIVITY_SERVICE),
                getPackageName(),
                this
        );
        prANDcsa = new PermissionRequestsAndCrossSectionalAnalysis(this);
        docApps = new documentApps(this);
    }

    private void initializeVisibleComponents() {
        progressBar = findViewById(R.id.pb);
        Button request = findViewById(R.id.btnRequest);
        request.setOnClickListener(this);
        Button email = findViewById(R.id.btnEmail);
        email.setOnClickListener(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btnRequest:
                promptAction(dai.detectState());
                break;
            case R.id.btnEmail:
                packageSQLdata makePkg = new packageSQLdata(this);
                makePkg.execute(this);
                break;
        }
    }

    private void sendEmail() {
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("text/plain");

        //getting directory for internal files
        String directory = (String.valueOf(this.getFilesDir()) + File.separator);
        Log.i("Directory", directory);
        File directoryPath = new File(directory);
        File[] filesInDirectory = directoryPath.listFiles();
        Log.d("Files", "Size: "+ filesInDirectory.length);
        for (File file : filesInDirectory) {
            Log.d("Files", "FileName:" + file.getName());
        }

        //initializing files reference
        File appDocumented = new File(directory + File.separator + Constants.APPS_AND_PERMISSIONS_FILE),

                screenUsage = new File(directory + File.separator + Constants.SCREEN_USAGE_FILE);

        //list of files to be uploaded
        ArrayList<Uri> files = new ArrayList<>();

        //if target files are identified to exist then they are packages into the attachments of the email
        try {
            if(appDocumented.exists()){
                files.add(FileProvider.getUriForFile(this, "geyer.sensorlab.v1psychapp.fileprovider", appDocumented));
            }

            if(screenUsage.exists()){
                files.add(FileProvider.getUriForFile(this, "geyer.sensorlab.v1psychapp.fileprovider", screenUsage));
            }

            if(files.size()>0){
                //adds the file to the intent to send multiple data points
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            }else{
                Log.e("email", "no files to upload");
            }

        }
        catch (Exception e){
            Log.e("File upload error1", "Error:" + e);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void promptAction(int i) {
        Log.i(TAG, "result of detect state: " + i);
        switch (i){
            //inform user
            case 1:
                informUser();
                break;
            case 2:
                requestPassword();
                break;
            //document apps
            case 3:
                docApps.execute(getApplicationContext(), this, dai.returnAppDirectionValue("current"));
                break;
            case 4:
                prANDcsa.requestPermission(Constants.USAGE_STATISTIC_PERMISSION_REQUEST);
                break;
            case 7:
                Log.i(TAG, "call to start logging background data");
                startLoggingData();
                break;
            case 11:
                informServiceIsRunning();
                break;
            default:
                break;
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void informUser() {
        StringBuilder msg = dai.buildMessageToInformUser();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("usage app")
                .setMessage(msg)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        editor.putBoolean("instructions shown", true)
                                .apply();
                        promptAction(dai.detectState());
                    }
                }).setNegativeButton("View privacy policy", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Uri uri = Uri.parse("https://psychsensorlab.com/privacy-agreement-for-apps/");
                Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uri);
                startActivityForResult(launchBrowser, Constants.SHOW_PRIVACY_POLICY);
            }
        });
        builder.create()
                .show();
    }

    private void requestPassword() {
        LayoutInflater inflater = this.getLayoutInflater();
        //create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("password")
                .setMessage("Please specify a password that is 6 characters in length. This can include letters and/or numbers.")
                .setView(inflater.inflate(R.layout.password_alert_dialog, null))
                .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Dialog d = (Dialog) dialogInterface;
                        EditText password = d.findViewById(R.id.etPassword);
                        if (checkPassword(password.getText())) {
                            editor.putBoolean("password generated", true);
                            editor.putString("password", String.valueOf(password.getText()));
                            editor.putString("pdfPassword", String.valueOf(password.getText()));
                            editor.apply();
                            promptAction(dai.detectState());
                        } else {
                            requestPassword();
                            Toast.makeText(MainActivity.this, "The password entered was not long enough", Toast.LENGTH_SHORT).show();
                        }
                    }

                    private boolean checkPassword(Editable text) {
                        return text.length() > 5;
                    }
                });
        builder.create()
                .show();
    }


    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case Constants.USAGE_STATISTIC_PERMISSION_REQUEST:
                promptAction(dai.detectState());
                break;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void startLoggingData() {
        Intent startLogging = new Intent();
        Bundle bundle = new Bundle();
        int includeUsageData = dai.returnAppDirectionValue("prospective");
        //if includeUsageData is equal to 2 or 4 then document that you want usage data
        if(includeUsageData==1||includeUsageData==3){
            bundle.putBoolean("usage log", true);
        }else{
            bundle.putBoolean("usage log", false);
        }
        startLogging.putExtras(bundle);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            startService(new Intent(this, logger.class));
        }else{
            startForegroundService(new Intent(this, logger.class));
            }
        }

    private void informServiceIsRunning() {
        Toast.makeText(this, "data logging is underway", Toast.LENGTH_LONG).show();
    }

    /**
     * What happens when the asynch tasks are completed
     * 1 - 2: results regarding the documenting of apps & permissions
     * 1 - success
     * 2 - failure
     * 3 - 4: results regarding the packaging of the SQL cypher screen usage/app usage database
     * 3 - success
     * 4 - failure
     */


    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void processFinish(Integer output) {
        switch (output){
            //log finished documenting apps
            case 1:
                promptAction(dai.detectState());
                break;
            //problem logging apps
            case 2:

                break;
            //finished packaging screen logging successfully
            case 3:
                sendEmail();
                break;
            //failed to package screen logging data
            case 4:
                Toast.makeText(this, "Problem uploading screen logging data", Toast.LENGTH_LONG).show();
                Log.i(TAG, "Problem uploading screen logging data");
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "CLOSED");
        editor.putBoolean("main in foreground", false).apply();
    }
}
