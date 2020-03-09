package ru.job4j.parser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        parser.pagesToParse(limitDay);
        parser.writeToDB(config.getString("url"), config.getString("username"), config.getString("password"));
    }
}