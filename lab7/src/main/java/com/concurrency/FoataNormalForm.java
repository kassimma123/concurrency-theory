package com.concurrency;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;

public class FoataNormalForm {

    // level[i] - poziom litery na pozycji słowa
    private final int[] level;
    // steps to wynik - lista kroków
    private final List<List<Character>> steps = new ArrayList<>();

    public FoataNormalForm(String word, Relations rel){
        int n = word.length();
        level = new int[n];

        for (int i = 0; i<n ; i++){
            int maxPrev = 0;
            for (int j = 0; j<i; j++){
                if (rel.isDependent(word.charAt(j), word.charAt(i))){
                    maxPrev = Math.max(maxPrev, level[j]);
                }
            }
            level[i] = maxPrev + 1;
        }

        Map<Integer, List<Character>> byLevel = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            byLevel.computeIfAbsent(level[i], k -> new ArrayList<>()).add(word.charAt(i));
        }
        for (List<Character> step : byLevel.values()) {
            Collections.sort(step);
            steps.add(step);
        }
    }

    public int[] levels() {
        return level.clone();
    }

    public List<List<Character>> steps() {
        return steps;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (List<Character> step : steps) {
            sb.append('(');
            for (char c : step) sb.append(c);
            sb.append(')');
        }
        return sb.toString();
    }
}
