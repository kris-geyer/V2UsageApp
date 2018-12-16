package geyer.sensorlab.v1psychapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
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

public class packageSQLdata extends AsyncTask<Object, Integer, Integer> {

    Context mContext;
    SharedPreferences prefs;

    packageSQLdata(MainActivity delegate) {
        this.delegate = (asyncResponse) delegate;
    }

    private asyncResponse delegate = null;


    @Override
    protected Integer doInBackground(Object[] objects) {
        initializeComponents(objects);
        packageBackgroundLoggingSQL();
        if(fileExists(Constants.SCREEN_USAGE_FILE)){
            return 3;
        }else{
            return 4;
        }
    }


    private boolean fileExists(String file) {
        String directory = (String.valueOf(mContext.getFilesDir()) + File.separator);
        File inspectedFile = new File(directory + File.separator + file);
        return inspectedFile.exists();
    }

    private void initializeComponents(Object[] objects) {
        mContext = (Context) objects[0];
        prefs =  mContext.getSharedPreferences("app initialization prefs", Context.MODE_PRIVATE);
        SQLiteDatabase.loadLibs(mContext);
    }

    private void packageBackgroundLoggingSQL() {
        //creates document
        Document document = new Document();
        //getting destination
        File path = mContext.getFilesDir();
        File file = new File(path, Constants.SCREEN_USAGE_FILE);
        // Location to save
        PdfWriter writer = null;
        try {
            writer = PdfWriter.getInstance(document, new FileOutputStream(file));
        } catch (DocumentException e) {
            Log.e("MAIN", "document exception: " + e);
        } catch (FileNotFoundException e) {
            Log.e("MAIN", "File not found exception: " + e);
        }
        try {
            if (writer != null) {
                writer.setEncryption("concretepage".getBytes(), prefs.getString("pdfPassword", "sensorlab").getBytes(), PdfWriter.ALLOW_COPY, PdfWriter.ENCRYPTION_AES_128);
            }
        } catch (DocumentException e) {
            Log.e("MAIN", "document exception: " + e);
        }
        if (writer != null) {
            writer.createXmpMetadata();
        }
        // Open to write
        document.open();


        String selectQuery = "SELECT * FROM " + BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.TABLE_NAME;
        SQLiteDatabase db = BackgroundLoggingSQL.getInstance(mContext).getReadableDatabase(prefs.getString("password", "not to be used"));

        Cursor c = db.rawQuery(selectQuery, null);

        int event = c.getColumnIndex(BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.EVENT);
        int time = c.getColumnIndex(BackgroundLoggingSQLCols.BackgroundLoggingSQLColsNames.TIME);



        PdfPTable table = new PdfPTable(2);
        //attempts to add the columns
        c.moveToLast();
        int rowLength =  c.getCount();
        if(rowLength > 0){
            try {
                for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
                    Log.i("pkgSQL", "event: " + event);
                    table.addCell(c.getString(event));
                    table.addCell(String.valueOf(c.getLong(time)));
                    if (c.getCount() != 0) {
                        int currentProgress = (c.getCount() * 100) / rowLength;
                        publishProgress(currentProgress);
                    }
                }
            } catch (Exception e) {
                Log.e("file construct", "error " + e);
            }

            c.close();
            db.close();


            //add to document
            document.setPageSize(PageSize.A4);
            document.addCreationDate();
            try {
                document.add(table);
            } catch (DocumentException e) {
                Log.e("MAIN", "Document exception: " + e);
            }
            document.addAuthor("Kris");
            document.close();
        }
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        Log.i("Main", "Progress update: " + values[0]);
        informMain(values[0]);
    }

    private void informMain(int progressBarValue) {
        Intent intent = new Intent("changeInService");
        intent.putExtra("progress bar update", true);
        intent.putExtra("progress bar progress", progressBarValue);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        Log.i("service", "data sent to main");
    }

    @Override
    protected void onPostExecute(Integer integer) {
        super.onPostExecute(integer);
        delegate.processFinish(integer);
    }
}

