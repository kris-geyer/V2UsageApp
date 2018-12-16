package geyer.sensorlab.v1psychapp;

import android.content.Intent;
import android.provider.Settings;

class PermissionRequestsAndCrossSectionalAnalysis {

    private MainActivity activityContext;

    PermissionRequestsAndCrossSectionalAnalysis(MainActivity mainActivity) {
        activityContext = mainActivity;
    }

    public void requestPermission(int permissionRequest){
        switch (permissionRequest){
            case Constants.USAGE_STATISTIC_PERMISSION_REQUEST:
                activityContext.startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), Constants.USAGE_STATISTIC_PERMISSION_REQUEST);
                break;
        }

    }



}
