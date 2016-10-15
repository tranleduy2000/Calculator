package com.android2.calculator3.dao;

import android.content.Context;
import android.content.pm.PackageManager;

import com.xlythe.dao.RemoteModel;
import com.xlythe.dao.Unique;

public class App extends RemoteModel<App> {

    public static boolean doesPackageExists(Context context, String targetPackage) {
        try {
            context.getPackageManager().getApplicationInfo(targetPackage, 0);
            return true;
        } catch(PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static class Query extends RemoteModel.Query<App> {
        public Query(Context context) {
            super(App.class, context);
            url("http://xlythe.com/calculator/store/themes.json");
        }
    }

    private String name;
    private String packageName;
    private float price;
    private String imageUrl;
    private boolean onPlayStore;
    private boolean onAmazonStore;
    private String customUrl;

    public App(Context context) {
        super(context);
    }

    public String getName() {
        return name;
    }

    public String getPackageName() {
        return packageName;
    }

    public float getPrice() {
        return price;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
