package geyer.sensorlab.v1psychapp;

import android.provider.BaseColumns;

public class AppsSQLCols {
    public AppsSQLCols(){}

    public static abstract class AppsSQLColsNames implements BaseColumns {
        public static final String
                TABLE_NAME = "app_database",
                COLUMN_NAME_ENTRY = "column_id",
                APP = "app",
                INSTALLED = "installed",
                PERMISSION = "permission",
                APPROVED = "approved",
                TIME = "time";
    }
}