package com.concurrency;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: java Main <path_to_input_file>...");
            System.exit(1);
        }
        for (String arg : args) {
            process(Path.of(arg));
        }
    }

    private static void process(Path input) throws IOException {
        InputData data = InputData.fromFile(input);
        Relations rel = new Relations(data);
        FoataNormalForm fnf = new FoataNormalForm(data.word(), rel);
        DependencyGraph graph = new DependencyGraph(data.word(), rel);

        System.out.println("====");
        System.out.println("Akcje: ");
        for (Action a : data.actions().values()) {
            System.out.println(a);
        }

        System.out.println(Relations.format("D", rel.dependencePairs()));
        System.out.println(Relations.format("I", rel.independentPairs()));
        System.out.println("FNF([w]) = " + fnf);
        System.out.println();
        System.out.println(graph.toDot());

        String name = input.getFileName().toString().replaceFirst("[.][^.]+$", "");
        Path dotFile = input.resolveSibling(name + ".dot");
        Files.writeString(dotFile, graph.toDot());
        System.out.println("Zapisano graf do: " + dotFile);
    }
}
