package com.matejdro.pebblecommons.util;

import java.util.ArrayList;
import java.util.List;

import android.content.SharedPreferences;

public class ListSerialization {
	
	public static void saveList(SharedPreferences.Editor editor, List<String> list, String listKey)
	{
		editor.putInt(listKey, list.size());
		for (int i = 0; i < list.size(); i++)
		{
			editor.putString(listKey + i, list.get(i));
		}
		
		editor.apply();
	}
	
	public static List<String> loadList(SharedPreferences preferences, String listKey)
	{
		int size = preferences.getInt(listKey, 0);
		
		List<String> list = new ArrayList<String>(size);
		for (int i = 0; i < size; i++)
		{
			list.add(preferences.getString(listKey + i, null));
		}
		
		return list;
	}

    public static void saveIntegerList(SharedPreferences.Editor editor, List<Integer> list, String listKey)
    {
        editor.putInt(listKey, list.size());
        for (int i = 0; i < list.size(); i++)
        {
            editor.putInt(listKey + i, list.get(i));
        }

        editor.apply();
    }

    public static List<Integer> loadIntegerList(SharedPreferences preferences, String listKey)
    {
        int size = preferences.getInt(listKey, 0);

        List<Integer> list = new ArrayList<Integer>(size);
        for (int i = 0; i < size; i++)
        {
            list.add(preferences.getInt(listKey + i, -1));
        }

        return list;
    }

}
