package ru.job4j.parser;

import java.time.LocalDateTime;
import java.util.List;

public interface ParserSite {
    List<Vacancy> pagesToParse(LocalDateTime limitDay);
}
