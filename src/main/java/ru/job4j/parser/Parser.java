package ru.job4j.parser;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.Date;

public class Parser implements Job {
    private LocalDateTime limitDay = LocalDateTime.of(LocalDate.now().getYear(), 1, 1, 0, 0, 0);

    @Override
    public void execute(JobExecutionContext context) {
        Date prevDateRunJob = context.getPreviousFireTime();
        JobDataMap config = context.getMergedJobDataMap();
        if (prevDateRunJob != null) {
            limitDay = prevDateRunJob.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        ParserSite parser = new ParserSQLru();
        List<Vacancy> vacancyList = parser.pagesToParse(limitDay);
        WriteToDB db = new WriteToDB();
        db.writeToDB(config.getString("url"), config.getString("username"), config.getString("password"), vacancyList);
    }
}