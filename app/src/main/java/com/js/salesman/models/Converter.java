package com.js.salesman.models;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

public class Converter {
    @TypeConverter
    public static List<AlternateUnit> fromString(String value) {
        Type listType = new TypeToken<List<AlternateUnit>>() {}.getType();
        return new Gson().fromJson(value, listType);
    }

    @TypeConverter
    public static String fromList(List<AlternateUnit> list) {
        Gson gson = new Gson();
        return gson.toJson(list);
    }
}
