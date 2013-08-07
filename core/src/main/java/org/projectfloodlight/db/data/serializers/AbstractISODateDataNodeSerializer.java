package org.projectfloodlight.db.data.serializers;

import java.util.Date;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.projectfloodlight.db.data.DataNodeSerializer;

public abstract class AbstractISODateDataNodeSerializer<T> implements DataNodeSerializer<T> {

    public static String formatISO(Date d) {
        DateTimeFormatter fmt = ISODateTimeFormat.dateTime().withZoneUTC();
        return fmt.print(new DateTime(d));
    }

    public static String formatISO(long d) {
        DateTimeFormatter fmt = ISODateTimeFormat.dateTime().withZoneUTC();
        return fmt.print(new DateTime(d));
    }

    // used to parse the string back to date
    public static Date parse(String text) {
        DateTimeFormatter fmt = ISODateTimeFormat.dateTime().withZoneUTC();
        return fmt.parseDateTime(text).toDate();
    }
}
