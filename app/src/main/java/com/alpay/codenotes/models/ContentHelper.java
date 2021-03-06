package com.alpay.codenotes.models;

import android.content.res.AssetManager;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.alpay.codenotes.utils.Utils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;

public class ContentHelper {

    static final String TAG = ContentHelper.class.getSimpleName();
    static Gson gson = new GsonBuilder().create();
    static Type contentListType = new TypeToken<ArrayList<Content>>(){}.getType();
    static String TR_FILE_NAME = "tr_content.json";
    static String EN_FILE_NAME = "en_content.json";

    public static ArrayList<Content> readFromAssets(AppCompatActivity context) {
        AssetManager am = context.getAssets();
        InputStream inputStream;
        BufferedReader bufferedReader = null;
        try {
            if (Utils.isTR(context)){
                inputStream = am.open(TR_FILE_NAME);
            }else{
                inputStream = am.open(EN_FILE_NAME);
            }
            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                bufferedReader = new BufferedReader(inputStreamReader);
            }
        } catch (IOException e) {
            Utils.sendAnalyticsData(TAG, "Group file cannot be read");
            Log.e("Content", "Can not read file: " + e.toString());
        }

        ArrayList<Content> data = new ArrayList<>();
        if(bufferedReader != null){
            JsonReader reader = new JsonReader(bufferedReader);
            data = gson.fromJson(reader, contentListType);
        }
        return data;
    }

}