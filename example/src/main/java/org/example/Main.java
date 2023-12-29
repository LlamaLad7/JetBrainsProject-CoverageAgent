package org.example;

import com.google.gson.GsonBuilder;

public class Main {
    public static void main(String[] args) {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        var mark = new Person("Mark", 43);
        if (mark.equals(gson.fromJson(gson.toJson(mark), Person.class))) {
            System.out.println("Hooray!");
        }
    }
}