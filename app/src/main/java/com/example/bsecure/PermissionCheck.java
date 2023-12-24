package com.example.bsecure;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;


import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PermissionCheck {

    public List<String> permissions = Arrays.asList(Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION);
    private Context con;
    private ActivityResultLauncher<String[]> requestPermLauncher;
    private ActivityResultLauncher<String> singlePermLauncher;
    public PermissionCheck(Context con, ActivityResultLauncher<String[]> requestPermLauncher ) {
        this(con, requestPermLauncher, null);
    }
    public PermissionCheck(Context con, ActivityResultLauncher<String[]> requestPermLauncher, ActivityResultLauncher<String> singlePermLauncher) {
        this.con = con;
        this.singlePermLauncher = singlePermLauncher;
        this.requestPermLauncher = requestPermLauncher;
    }

    public void checkPermissions() {
        requestPermLauncher.launch((String[]) permissions.toArray());
    }

    public void checkSingularPermission(String perm) {
        singlePermLauncher.launch(perm);
    }

}
