package edu.utas.travelbot.utils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Afinn {

    Map<String, Integer> wordList;

    public Afinn(String afinn_file_path) {

        wordList = new HashMap<String, Integer>();

        // Load the AFINN File
        try {
            BufferedReader r = new BufferedReader(new FileReader(afinn_file_path));
            String line;
            while ( (line = r.readLine()) != null) {

                String[] parts = line.split("\t");
                wordList.put(parts[0], Integer.valueOf(parts[1]));
            }
            r.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int score(String text) {
        int score = 0;
        String[] words = text.split("[^a-zA-Z0-9]");
        for (String word : words) {
            if (word.trim().length() < 1) continue;

            if (wordList.keySet().contains(word)) {
                score = score + wordList.get(word);
            }
        }
        return score;
    }

}
