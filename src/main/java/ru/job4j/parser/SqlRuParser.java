package ru.job4j.parser;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import java.io.InputStream;
import java.util.Properties;
import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

public class SqlRuParser {
    public static void main(String[] args) {
        try (InputStream in = SqlRuParser.class.getClassLoader().getResourceAsStream(args[0])) {
            var conf = new Properties();
            conf.load(in);

            SchedulerFactory sf = new StdSchedulerFactory();
            Scheduler sched = sf.getScheduler();

            JobDataMap map = new JobDataMap();
            map.put("url", conf.getProperty("url"));
            map.put("username", conf.getProperty("username"));
            map.put("password", conf.getProperty("password"));

            JobDetail job = newJob(Parser.class)
                    .withIdentity("job1", "group1")
                    .usingJobData(map)
                    .build();

            CronTrigger trigger = newTrigger()
                    .withIdentity("trigger1", "group1")
                    .withSchedule(cronSchedule(conf.getProperty("cron.time")))
                    .build();

            sched.scheduleJob(job, trigger);
            sched.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
