package org.ulpgc.tarantino.control;

import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<Integer> candidates = ControlFactory.candidates(TarantinoConfig.fromEnvironment(TarantinoService.CONTROL));
        System.out.println("[CONTROL] " + candidates.size() + " candidate books");
    }
}
