package org.ulpgc.tarantino.control.model;

import java.util.List;

public record NextStep(Action action, List<Integer> bookIds) {

    public enum Action {
        INDEX,
        DOWNLOAD,
        IDLE
    }

    public NextStep {
        bookIds = List.copyOf(bookIds);
    }

    public static NextStep index(List<Integer> bookIds) {
        return new NextStep(Action.INDEX, bookIds);
    }

    public static NextStep download(int bookId) {
        return new NextStep(Action.DOWNLOAD, List.of(bookId));
    }

    public static NextStep idle() {
        return new NextStep(Action.IDLE, List.of());
    }

    public String books() {
        return switch (bookIds.size()) {
            case 0 -> "";
            case 1 -> String.valueOf(bookIds.getFirst());
            default -> "%d books (%d…%d)".formatted(bookIds.size(), bookIds.getFirst(), bookIds.getLast());
        };
    }
}
