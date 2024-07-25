package org.voidzero.pgauditor;

import org.voidzero.influx.cli.TypeConverter;
import org.voidzero.influx.cli.exception.ParseException;

public class AuthenticationTypeConverter implements TypeConverter<Authentication> {

    @Override
    public Class<Authentication> getType() {
        return Authentication.class;
    }

    @Override
    public Authentication read(String value) throws ParseException {
        if (value == null) {
            return null;
        }
        return Authentication.valueOf(value.trim().toUpperCase());
    }

    @Override
    public String write(Authentication value) throws ParseException {
        if (value == null) {
            return null;
        }
        return value.name().toLowerCase();
    }
}
