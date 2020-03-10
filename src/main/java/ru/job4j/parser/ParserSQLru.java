package ru.job4j.parser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParserSQLru implements ParserSite {
    private String url = "https://www.sql.ru/forum/job-offers";
    private Map<String, String> mapMonths = new HashMap<>();
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM yy, HH:mm");
    private DateTimeFormatter briefFormat = DateTimeFormatter.ofPattern("d MMM yy");
    private List<Vacancy> vacancies = new ArrayList<>();
    private static final Logger LOG = LogManager.getLogger(Parser.class.getName());

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

    public List<Vacancy> pagesToParse(LocalDateTime limitDay) {
        initMapMonths();
        Document doc;
        try {
            String address = "";
            int i = 0;
            Boolean exit = false;
            while (!exit) {
                i++;
                if (i == 1) {
                    address = url;
                } else {
                    address = String.format("%s/%s", url, String.valueOf(i));
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
        return vacancies;
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
