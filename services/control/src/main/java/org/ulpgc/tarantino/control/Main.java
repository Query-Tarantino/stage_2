package org.ulpgc.tarantino.control;

import java.util.Arrays;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<Integer> candidates = ControlFactory.candidates(ControlConfig.fromEnvironment(), Arrays.stream(args).findFirst());
        System.out.println("[CONTROL] " + candidates.size() + " candidate books");
    }
}
