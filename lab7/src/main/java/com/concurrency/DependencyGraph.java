package com.concurrency;

import java.util.ArrayList;
import java.util.List;

public class DependencyGraph {
    private final String word;
    private final boolean[][] minimal;

    public DependencyGraph(String word, Relations rel) {
        this.word = word;
        int n = word.length();

        boolean[][] full = new boolean[n][n];

        // dla kazdej pary pozycji i<j jest krawedz
        // jeśli litery są zalezne (rozwazamy tylko pary w prawo - zalatwia sortowanie topologiczne)
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                full[i][j] = rel.isDependent(word.charAt(i), word.charAt(j));
            }
        }

        boolean[][] reach = new boolean[n][n];

        // liczymy od konca 
        // zeby policzyc dokad dochodzi i - musimy wiedziec dokad dochodza jego nastepniki k>i
        // idac od tylu mamy je policzone (program dynamiczne po porzadku topologicznym)
        for (int i = n - 1; i >= 0; i--) {
            for (int k = i + 1; k < n; k++) {
                // jesli jest krawedz i->k : do k na pewno dojdziemy
                if (full[i][k]) {
                    reach[i][k] = true;
                    for (int j = k + 1; j < n; j++) {
                        if (reach[k][j]) reach[i][j] = true;
                    }
                }
            }
        }

        minimal = new boolean[n][n];
        // i->k->j && i->j wiec i->j jest redundantne
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (!full[i][j]) continue;
                boolean redundant = false;
                for (int k = i + 1; k < j && !redundant; k++) {
                    if (reach[i][k] && reach[k][j]) redundant = true;
                }
                minimal[i][j] = !redundant; // krawedz zostaje <=> nie da sie jej obejsc 
            }
        }
    }

    public List<int[]> edges() {
        List<int[]> result = new ArrayList<>();
        int n = word.length();
        for (int j = 0; j < n; j++)
            for (int i = 0; i < j; i++)
                if (minimal[i][j])
                    result.add(new int[]{i + 1, j + 1});
        return result;    
    }

    public String toDot() {
        StringBuilder sb = new StringBuilder("digraph g { \n");
        for (int i = 0; i < word.length(); i++) {
            int node = i + 1;
            char letter = word.charAt(i);
            sb.append("  ").append(node).append(" [label=\"").append(letter).append("\"];\n");
        }
        for (int[] e : edges()) {
            sb.append("  ").append(e[0]).append(" -> ").append(e[1]).append(";\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
