package com.studenthub.integration.jw;

import com.studenthub.modules.jwauth.config.JwProperties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KingosoftScheduleClientTest {

    @Test
    void parsesSourceTimeAndKeepsDifferentWeeksSeparate() throws Exception {
        Document document = Jsoup.parse(Files.readString(
                Path.of("src/test/resources/schedule/multi-week-grid.html")));
        KingosoftScheduleClient client = new KingosoftScheduleClient(new JwProperties());
        Method parse = KingosoftScheduleClient.class.getDeclaredMethod("parseGrid", Document.class);
        parse.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<KingosoftScheduleClient.JwCourseEntry> courses =
                (List<KingosoftScheduleClient.JwCourseEntry>) parse.invoke(client, document);

        assertEquals(5, courses.size());
        java.util.regex.Matcher weeks = java.util.regex.Pattern.compile("周次[：:]\\s*(\\d+)\\s*[-–~]\\s*(\\d+)")
                .matcher(document.text());
        assertTrue(weeks.find());
        assertEquals(25, Integer.parseInt(weeks.group(2)));
        assertEquals("16:05-16:50", courses.get(0).timeRange());
        assertEquals("1-3", courses.get(0).weeks());
        assertEquals("9", courses.get(1).weeks());
        assertEquals(12, courses.get(4).startSection());
        assertEquals("21:00-21:45", courses.get(4).timeRange());
    }

    @Test
    void sectionTimesIncludeSchoolTwelfthSection() {
        assertEquals("21:00-21:45",
                com.studenthub.modules.schedule.calendar.SectionTimes.rangeOf(12, 12));
    }
}
