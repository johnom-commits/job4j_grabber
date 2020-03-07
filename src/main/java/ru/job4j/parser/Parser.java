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
    private Map<String, String> mapMonths = new HashMap<>();
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM yy, HH:mm");
    private DateTimeFormatter briefFormat = DateTimeFormatter.ofPattern("d MMM yy");
    private LocalDateTime limitDay = LocalDateTime.of(LocalDate.now().getYear(), 1, 1, 0, 0, 0);
    private List<Vacancy> vacancies = new ArrayList<>();
    private static final Logger LOG = LogManager.getLogger(Parser.class.getName());

    @Override
    public void execute(JobExecutionContext context) {
        Date prevDateRunJob = context.getPreviousFireTime();
        JobDataMap config = context.getMergedJobDataMap();
        if (prevDateRunJob != null) {
            limitDay = prevDateRunJob.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        Parser parser = new Parser();
        parser.pagesToParse(config.getString("website"));
        parser.writeToDB(config.getString("url"), config.getString("username"), config.getString("password"));
    }

    private void initMapMonths() {
        mapMonths.put("янв", "янв.");
        mapMonths.put("фев", "февр.");
        mapMonths.put("мар", "мар.");
        mapMonths.put("апр", "апр.");
        mapMonths.put("май", "мая");
        mapMonths.put("июн", "июн.");
        mapMonths.put("июл", "июл.");
        mapMonths.put("авг", "авг.");
        mapMonths.put("сен", "сент.");
        mapMonths.put("окт", "окт.");
        mapMonths.put("ноя", "нояб.");
        mapMonths.put("дек", "дек.");
    }

    private void writeToDB(String urlBase, String username, String password) {
        try (Connection con = DriverManager.getConnection(urlBase, username, password)) {
            initTableDB(con);
            vacanciesToDB(con);
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.error(ex.getMessage());
        }
    }

    private void initTableDB(Connection con) {
        try (final Statement st = con.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS vacancies (id serial PRIMARY KEY, name varchar(400) NOT NULL UNIQUE, text text, link varchar(400))");
        } catch (SQLException e) {
            LOG.error(e.getMessage());
        }
    }

    public void vacanciesToDB(Connection con) throws SQLException {
        con.setAutoCommit(false);
        try (final PreparedStatement st = con.prepareStatement("INSERT INTO vacancies (name, text, link) VALUES (?, ?, ?)")) {
            for (Vacancy vacancy : vacancies) {
                st.setString(1, vacancy.getName());
                st.setString(2, vacancy.getText());
                st.setString(3, vacancy.getLink());
                st.addBatch();
            }
            st.executeBatch();
            con.commit();
        } catch (SQLException ex) {
            LOG.error(ex.getMessage());
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException exp) {
                    LOG.error(exp.getMessage());
                }
            }
        }
        con.setAutoCommit(true);
    }

    private void pagesToParse(String website) {
        initMapMonths();
        Document doc;
        try {
            String address = "";
            int i = 0;
            Boolean exit = false;
            while (!exit) {
                i++;
                if (i == 1) {
                    address = website;
                } else {
                    address = String.format("%s/%s", website, String.valueOf(i));
                }
                doc = Jsoup.connect(address).get();
                Element table = doc.selectFirst("table.forumTable");
                Element body = table.child(0);
                Elements tr = body.select("tr");
                for (Element row : tr) {
                    Element el = row.child(1);
                    Elements els = el.select("a");
                    String text = els.text();
                    Boolean exist = find(text);
                    if (!exist) {
                        continue;
                    }
                    Element cellDate = row.child(5);
                    LocalDateTime dateLastComment = getDate(cellDate.text());
                    if (dateLastComment.isBefore(limitDay)) {
                        exit = true;    // Если дата комментария меньше целевой даты, то загругляемся.
                        break;
                    }
                    String link = els.attr("href");
                    String name = els.text();

                    Document docItem = Jsoup.connect(link).get();  // открываем страницу конкретной вакансии
                    Element tableItem = docItem.selectFirst("table.msgTable");
                    Element bodyItem = tableItem.child(0);
                    Elements trItem = bodyItem.select("tr");
                    trItem.remove(0);               // пропускаем заголовок
                    LocalDateTime dataCreate = getDate(trItem.last().child(0).text());
                    if (dataCreate.isBefore(limitDay)) {
                        continue;
                    }
                    String textVacancy = trItem.first().child(1).text();
                    vacancies.add(new Vacancy(name, textVacancy, link));
                    LOG.info(String.format("Create: %s: %s; link: %s", dataCreate.toString(), name, link));
                }
            }
        } catch (IOException e) {
            LOG.error(e.getMessage());
        }
    }

    private LocalDateTime getDate(String data) {
        LocalDateTime dt;
        if (data.contains("сегодня")) {
            dt = parseToday(data);
        } else if (data.contains("вчера")) {
            dt = parseYestoday(data);
        } else {
            dt = parseDate(data);
        }
        return dt;
    }

    private LocalDateTime parseToday(String data) {
        String today = LocalDate.now().format(briefFormat);
        String dataToday = selectDate(data, "сегодня, \\d{2}:\\d{2}");
        String newData = dataToday.replace("сегодня", today);
        LocalDateTime dateValue = LocalDateTime.parse(newData, formatter);
        return dateValue;
    }

    private LocalDateTime parseYestoday(String data) {
        LocalDate today = LocalDate.now();
        String yestoday = today.minusDays(1).format(briefFormat);
        String dataYestoday = selectDate(data, "вчера, \\d{2}:\\d{2}");
        String newData = dataYestoday.replace("вчера", yestoday);
        LocalDateTime dateValue = LocalDateTime.parse(newData, formatter);
        return dateValue;
    }

    private LocalDateTime parseDate(String data) {
        String dataSelect = selectDate(data, "\\d{1,2} ... \\d{2}, \\d{2}:\\d{2}");
        String oldValue = findMonth(dataSelect);
        String newData = dataSelect.replace(oldValue, mapMonths.get(oldValue));
        LocalDateTime dateValue = LocalDateTime.parse(newData, formatter);
        return dateValue;
    }

    private String selectDate(String text, String pattern) {
        String newText = "";
        Pattern pat = Pattern.compile(pattern);
        Matcher matcher = pat.matcher(text);
        while (matcher.find()) {
            newText = text.substring(matcher.start(), matcher.end());
        }
        return newText;
    }

    private String findMonth(String data) {
        String result = data.substring(data.indexOf(" ") + 1);
        result = result.substring(0, result.indexOf(" "));
        return result;
    }

    private boolean find(String input) {
        Pattern pattern = Pattern.compile("\\bjava\\b(?!script)\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(input);
        return matcher.find();
    }
}