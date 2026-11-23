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

//actions - mapa litera->akcja, alphabet - lista liter A (kolejność), word - słowo
public record InputData(Map<Character, Action> actions, List<Character> alphabet, String word) {
    private static final Pattern ACTION_LINE = Pattern.compile("^\\(\\s*([a-z])\\s*\\)\\s*([a-z])\\s*:=\\s*(.+)$");
    // np (b) y := y + 2z ( b ) y := y + 2z (dowolny znak, co naj, mniej jeden)

    private static final Pattern ALPHABET_LINE = Pattern.compile("^A\\s*=\\s*\\{(.*)\\}\\s*$");
    // A = {a, b, c}

    private static final Pattern WORD_LINE = Pattern.compile("^w\\s*=\\s*([a-z\\s]+)$");
    // w=acdcfbbe

    private static final Pattern VARIABLE = Pattern.compile("[a-z]");
    // jedna małą litera - do suzkania zmiennych po prawej str

    public static InputData fromFile(Path path) throws IOException {
        Map<Character, Action> actions = new LinkedHashMap<>();
        List<Character> alphabet = new ArrayList<>();
        String word = null;

        for (String raw : Files.readAllLines(path)) {
            String line = raw.strip().replace("-", "-");

            if (line.isEmpty())
                continue;

            Matcher m;
            if ((m = ACTION_LINE.matcher(line)).matches()) {
                char name = m.group(1).charAt(0);
                char written = m.group(2).charAt(0);
                Set<Character> read = new TreeSet<>(); // zbiór posortowany
                Matcher v = VARIABLE.matcher(m.group(3));
                while (v.find())
                    read.add(v.group().charAt(0));
                actions.put(name, new Action(name, written, read));
            } else if ((m = ALPHABET_LINE.matcher(line)).matches()) {
                for (String s : m.group(1).split(",")) {
                    if (!s.isBlank())
                        alphabet.add(s.strip().charAt(0));
                }
            } else if ((m = WORD_LINE.matcher(line)).matches()) {
                word = m.group(1).replaceAll("\\s", "");
            } else {
                throw new IllegalArgumentException("nie rozumiem linii: " + raw);
            }
        }

        if (word == null)
            throw new IllegalArgumentException("Brak słowa w ");
        if (alphabet.isEmpty())
            alphabet.addAll(actions.keySet());

        for (char c : alphabet)
            if (!actions.containsKey(c))
                throw new IllegalArgumentException("brak def akcji" + c);

        for (char c : word.toCharArray())
            if (!actions.containsKey(c))
                throw new IllegalArgumentException("litera " + c + " nie nalezy do A");

        return new InputData(actions, alphabet, word);

    }
}
