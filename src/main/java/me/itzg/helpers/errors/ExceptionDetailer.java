package me.itzg.helpers.errors;

import java.util.ArrayList;
import java.util.Iterator;

public class ExceptionDetailer {

    private static class CauseIterator implements Iterator<Throwable> {
        private Throwable current;

        CauseIterator(Throwable initial) {
            this.current = initial;
        }

        @Override
        public boolean hasNext() {
            return current != null;
        }

        public Throwable next() {
            if (current == null) {
                return null;
            }
            Throwable result = current;
            current = current.getCause();
            return result;
        }
    }

    public static String buildCausalMessages(Throwable throwable) {
        final CauseIterator causeIterator = new CauseIterator(throwable);
        final ArrayList<String> parts = new ArrayList<>();
        while (causeIterator.hasNext()) {
            final Throwable cause = causeIterator.next();
            if (cause != null) {
                parts.add(cause.getClass().getSimpleName() + ": " + cause.getMessage());
            }
        }
        return String.join("; ", parts);
    }
}
