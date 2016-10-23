package rho.reader;

import static rho.Panic.panic;

enum EOFBehaviour {
    RETURN {
        @Override
        boolean reactToEOF(Location location, Character endChar) {
            return true;
        }
    },

    THROW {
        @Override
        boolean reactToEOF(Location location, Character endChar) {
            throw panic(String.format("EOF while reading at %s%s", location,
                endChar != null ? String.format(", expecting '%s'", endChar) : ""));
        }
    };

    abstract boolean reactToEOF(Location location, Character endChar);
}
