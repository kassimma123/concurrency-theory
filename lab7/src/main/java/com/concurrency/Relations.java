package com.concurrency;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashMap;

public class Relations {
    private final List<Character> alphabet;
    private final Map<Character, Integer> index = new HashMap<>();
    private final boolean[][] dependent;

    public Relations(InputData data) {
        this.alphabet = data.alphabet();
        int n = alphabet.size();
        for (int i = 0; i < n; i++)
            index.put(alphabet.get(i), i);

        dependent = new boolean[n][n];

        // dwie zagniedzone funkcje przechodzą po parach (i,j) i spr czy zalezą od
        // siebie
        for (int i = 0; i < n; i++) {
            Action ai = data.actions().get(alphabet.get(i));
            for (int j = 0; j < n; j++) {
                Action aj = data.actions().get(alphabet.get(j));
                dependent[i][j] = ai.dependsOn(aj);
            }
        }
    }

    public boolean isDependent(char x, char y) {
        return dependent[index.get(x)][index.get(y)];
    }

    public List<String> dependencePairs() {
        return pairs(true);
    }

    public List<String> independentPairs() {
        return pairs(false);
    }

    private List<String> pairs(boolean wantDependent) {
        List<String> result = new ArrayList<>();
        for (char x : alphabet)
            for (char y : alphabet)
                if (isDependent(x, y) == wantDependent)
                    result.add("( " + x + " , " + y + " )");
        return result;  
    }

    public static String format(String name, List<String> pairs) {
        return name + " = { " + String.join(", ", pairs) + " }";
    }

}
